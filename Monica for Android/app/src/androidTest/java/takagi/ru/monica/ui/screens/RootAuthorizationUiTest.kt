package takagi.ru.monica.ui.screens

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import takagi.ru.monica.autofill_ng.privilege.PrivilegeAccess
import takagi.ru.monica.autofill_ng.privilege.PrivilegeBackend
import takagi.ru.monica.autofill_ng.privilege.RootStatus
import takagi.ru.monica.autofill_ng.privilege.RootResult
import takagi.ru.monica.autofill_ng.privilege.RootCommand
import takagi.ru.monica.autofill_ng.protection.AutofillProtection
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RootAuthorizationUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var saved: Map<String, Map<String, *>>
    private val prefNames = listOf("system_privilege", "autofill_protection")

    @Before fun before() {
        saved = prefNames.associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all }
        PrivilegeAccess.disable(context)
        PrivilegeAccess.select(context, PrivilegeBackend.SHIZUKU)
    }

    @After fun after() {
        PrivilegeAccess.disable(context)
        saved.forEach { (name, values) ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply {
                values.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is Long -> putLong(key, value)
                        is Int -> putInt(key, value)
                        is String -> putString(key, value)
                        is Float -> putFloat(key, value)
                    }
                }
            }.commit()
        }
    }

    private fun show(scale: Float = 1f, page: String = "selector") {
        compose.setContent {
            val activityResultOwner = requireNotNull(LocalActivityResultRegistryOwner.current)
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.SIMPLIFIED_CHINESE) }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalContext provides LocalContext.current.createConfigurationContext(configuration),
                LocalActivityResultRegistryOwner provides activityResultOwner,
                LocalDensity provides Density(LocalDensity.current.density, scale)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        when (page) {
                            "manager" -> DefaultPasswordManagerSheet {}
                            "protection" -> AutofillProtectionScreen {}
                            else -> Column(Modifier.width(360.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                                PrivilegeBackendSelector()
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun choosingRootDoesNotRequestPermissionAndCancelDoesNotEnableIt() {
        show()
        compose.onNodeWithTag("privilege-ROOT").performClick()
        assertFalse(PrivilegeAccess.rootAllowed(context))
        assertEquals(RootStatus.IDLE, PrivilegeAccess.status.value)
        compose.onNodeWithTag("root-authorize").performScrollTo().performClick()
        compose.onNodeWithTag("root-consent-cancel").performClick()
        assertFalse(PrivilegeAccess.rootAllowed(context))
        assertEquals(RootStatus.IDLE, PrivilegeAccess.status.value)
    }

    @Test fun missingSuIsReportedAndCannotEnableRootOrChangeSystemSettings() {
        val descriptor = instrumentation.uiAutomation.executeShellCommand("/system/bin/toybox which su")
        val su = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        Assume.assumeTrue("This case requires an emulator without su", su.isBlank())
        val before = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_accessibility_services")
        show()
        compose.onNodeWithTag("privilege-ROOT").performClick()
        compose.onNodeWithTag("root-authorize").performScrollTo().performClick()
        compose.onNodeWithTag("root-consent-confirm").performClick()
        compose.waitUntil(35000) { PrivilegeAccess.status.value == RootStatus.UNAVAILABLE }
        compose.onNodeWithTag("root-status").performScrollTo().assertTextContains("su", substring = true)
        capture("root-unavailable.png")
        assertFalse(PrivilegeAccess.rootAllowed(context))
        assertEquals(before, android.provider.Settings.Secure.getString(context.contentResolver, "enabled_accessibility_services"))
    }

    @Test fun rootChoiceAndActionsRemainReachableWithLargeText() {
        show(1.5f)
        compose.onNodeWithTag("privilege-ROOT").performScrollTo().performClick()
        compose.onNodeWithTag("root-authorize").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("root-consent-confirm").assertIsDisplayed()
        capture("root-consent-large.png")
        compose.onNodeWithTag("root-consent-cancel").performClick()
        compose.onNodeWithTag("privilege-SHIZUKU").performScrollTo().performClick()
        assertEquals(PrivilegeBackend.SHIZUKU, PrivilegeAccess.backend(context))
    }

    @Test @SdkSuppress(minSdkVersion = 31)
    fun rootHelperLoadsAndRejectsTheUnprivilegedShellWithoutWriting() {
        val before = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_accessibility_services")
        // UiAutomation executes argv, not shell assignments or redirections.
        val command = "/system/bin/env CLASSPATH=${context.applicationInfo.sourceDir}" +
            " /system/bin/app_process /system/bin takagi.ru.monica.autofill_ng.privilege.RootAccessibilityMain 10"
        val descriptors = instrumentation.uiAutomation.executeShellCommandRwe(command)
        descriptors[1].close()
        val output = listOf(descriptors[0], descriptors[2]).joinToString("\n") { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        }
        assertTrue(output, output.contains("MONICA_ROOT_REQUIRED"))
        assertFalse(output, output.contains("MONICA_RECOVERY:"))
        assertEquals(before, android.provider.Settings.Secure.getString(context.contentResolver, "enabled_accessibility_services"))

        // Exercise quoting in Android's real shell without root, including command substitutions.
        val sentinel = File(context.cacheDir, "root-quote-${java.util.UUID.randomUUID()}")
        val payload = "provider/.Service:'; touch ${RootCommand.quote(sentinel.path)}; # \$(id) `id`"
        try {
            val echoed = RootCommand.runProcess(listOf("/system/bin/sh", "-c", "printf '%s' " + RootCommand.quote(payload)), 5)
            assertEquals(RootResult(0, payload), echoed)
            assertFalse("Quoted data executed as shell commands", sentinel.exists())
        } finally { sentinel.delete() }
    }

    @Test fun rootIsAvailableOnTheProtectionPageWithoutEnablingRecovery() {
        context.getSharedPreferences("autofill_protection", Context.MODE_PRIVATE).edit()
            .putBoolean("background_enabled", false).putBoolean("shizuku_recovery_enabled", false).commit()
        show(page = "protection")
        compose.onNodeWithTag("privilege-ROOT").performScrollTo().performClick()
        compose.onNodeWithTag("root-authorize").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("enhanced-recovery-toggle").performScrollTo().assertIsNotEnabled()
        assertFalse(AutofillProtection.isEnhancedRecoveryEnabled(context))
        capture("protection-root.png")
    }

    @Test fun defaultManagerRootActionsStayDisabledOnUnsupportedAndroid() {
        Assume.assumeTrue(Build.VERSION.SDK_INT < 34)
        show(scale = 1.5f, page = "manager")
        compose.onNodeWithTag("privilege-ROOT").performScrollTo().performClick()
        compose.onNodeWithTag("root-authorize").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("default_manager_apply").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("default_manager_restore").assertIsDisplayed().assertIsNotEnabled()
        capture("default-manager-root-large.png")
    }

    @Test fun switchingAndDisablingRootClearAutomaticRecoveryConsent() {
        val protection = context.getSharedPreferences("autofill_protection", Context.MODE_PRIVATE)
        protection.edit().putBoolean("shizuku_recovery_enabled", true).commit()
        PrivilegeAccess.select(context, PrivilegeBackend.ROOT)
        assertFalse(AutofillProtection.isEnhancedRecoveryEnabled(context))
        protection.edit().putBoolean("shizuku_recovery_enabled", true).commit()
        PrivilegeAccess.disable(context)
        assertFalse(AutofillProtection.isEnhancedRecoveryEnabled(context))
        assertFalse(PrivilegeAccess.rootAllowed(context))
    }

    @Test fun rootCanStillBeDisabledAfterACommandFails() {
        val descriptor = instrumentation.uiAutomation.executeShellCommand("/system/bin/toybox which su")
        val su = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        Assume.assumeTrue("This case requires an emulator without su", su.isBlank())
        PrivilegeAccess.select(context, PrivilegeBackend.ROOT)
        context.getSharedPreferences("system_privilege", Context.MODE_PRIVATE).edit()
            .putBoolean("root_consent", true).commit()
        assertThrows(java.io.IOException::class.java) {
            PrivilegeAccess.settings(listOf("/system/bin/settings", "--user", "0", "get", "secure", "enabled_accessibility_services"))
        }
        assertFalse(PrivilegeAccess.rootAllowed(context))
        assertTrue(PrivilegeAccess.hasRootConsent(context))
        show()
        compose.onNodeWithTag("root-disable").performScrollTo().assertIsDisplayed().performClick()
        assertFalse(PrivilegeAccess.hasRootConsent(context))
    }

    @Test fun delayedRootGrantCannotUndoDisableOrBackendSwitch() = runBlocking {
        for (switchBackend in listOf(false, true)) {
            PrivilegeAccess.select(context, PrivilegeBackend.ROOT)
            val started = CountDownLatch(1)
            val finish = CountDownLatch(1)
            val request = async(start = CoroutineStart.UNDISPATCHED) {
                PrivilegeAccess.authorize(context) {
                    started.countDown()
                    check(finish.await(5, TimeUnit.SECONDS))
                    RootResult(0, "0\n")
                }
            }
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS))
                if (switchBackend) PrivilegeAccess.select(context, PrivilegeBackend.SHIZUKU)
                else PrivilegeAccess.disable(context)
            } finally { finish.countDown() }
            assertFalse(request.await())
            assertFalse(PrivilegeAccess.hasRootConsent(context))
            assertFalse(AutofillProtection.isEnhancedRecoveryEnabled(context))
        }
    }

    @Test fun queuedAuthorizationDoesNotInvokeSuAfterRevocation() = runBlocking {
        PrivilegeAccess.select(context, PrivilegeBackend.ROOT)
        PrivilegeAccess.transaction.lock()
        var invoked = false
        try {
            val request = async(start = CoroutineStart.UNDISPATCHED) {
                PrivilegeAccess.authorize(context) { invoked = true; RootResult(0, "0\n") }
            }
            PrivilegeAccess.disable(context)
            PrivilegeAccess.transaction.unlock()
            assertFalse(request.await())
            assertFalse(invoked)
            assertFalse(PrivilegeAccess.hasRootConsent(context))
        } finally {
            if (PrivilegeAccess.transaction.isLocked) PrivilegeAccess.transaction.unlock()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Android window fades run outside Compose's test clock.
        Thread.sleep(400)
        val folder = File(context.getExternalFilesDir(null), "root-access-test").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
