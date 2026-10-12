package takagi.ru.monica.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.data.AppLauncherIcon
import takagi.ru.monica.data.AppLauncherLabel
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.Language
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.utils.AppLauncherIconManager
import takagi.ru.monica.utils.PageAdjustmentSettingsSnapshot
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.viewmodel.SettingsViewModel

class AppLauncherIconSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = SettingsManager(context)
    private val store = ViewModelStore()
    private lateinit var original: AppSettings
    private lateinit var snapshot: PageAdjustmentSettingsSnapshot

    @Before fun prepare() = runBlocking {
        original = manager.settingsFlow.first()
        snapshot = manager.exportPageAdjustmentSettings()
        manager.updateLanguage(Language.CHINESE)
        manager.updateAppLauncherLabel(AppLauncherLabel.MONICA_PASS)
        manager.updateAppLauncherIcon(AppLauncherIcon.MODERN)
        manager.updateIconCardsEnabled(false)
    }

    @After fun restore() {
        compose.runOnIdle { store.clear() }
        runBlocking {
            manager.importPageAdjustmentSettings(snapshot)
            manager.updateLanguage(original.language)
        }
    }

    private fun show(dark: Boolean = false, fontScale: Float = 1f, narrow: Boolean = false): SettingsViewModel {
        val model = SettingsViewModel(manager)
        store.put("settings", model)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
            this.fontScale = fontScale
        }
        val localized = context.createConfigurationContext(configuration)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density, fontScale),
            ) {
                MonicaTheme(darkTheme = dark) {
                    Box(if (narrow) Modifier.width(320.dp).fillMaxHeight() else Modifier) {
                        IconSettingsScreen(viewModel = model, onNavigateBack = {})
                    }
                }
            }
        }
        compose.waitUntil(10_000) { !model.settings.value.iconCardsEnabled }
        return model
    }

    @Test fun selectionWorksWithEntryIconsOffAndPersistsAndCanReturnToDefault() {
        val model = show()
        compose.onNodeWithText("主应用图标").assertIsDisplayed()
        compose.onNodeWithTag("launcher_icon_MODERN").assertIsSelected()
        compose.onNodeWithTag("launcher_icon_BLUE_STAR").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { model.settings.value.appLauncherIcon == AppLauncherIcon.BLUE_STAR }
        compose.onNodeWithTag("launcher_icon_BLUE_STAR").assertIsSelected()
        assertFalse(model.settings.value.iconCardsEnabled)
        runBlocking {
            assertEquals(AppLauncherIcon.BLUE_STAR, SettingsManager(context).settingsFlow.first().appLauncherIcon)
        }
        compose.waitForIdle()
        assertLauncher("BlueStarVisibleLauncherAlias", R.string.app_label)
        capture("launcher-icons-light.png")
        compose.onNodeWithTag("launcher_icon_MODERN").performClick()
        compose.waitUntil(10_000) { model.settings.value.appLauncherIcon == AppLauncherIcon.MODERN }
        compose.onNodeWithTag("launcher_icon_MODERN").assertIsSelected()
        compose.waitForIdle()
        assertLauncher("ModernVisibleLauncherAlias", R.string.app_label)
    }

    @Test fun darkNarrowLargeTextKeepsBothChoicesReachable() {
        runBlocking { manager.updateAppLauncherIcon(AppLauncherIcon.BLUE_STAR) }
        val model = show(dark = true, fontScale = 1.5f, narrow = true)
        compose.waitUntil(10_000) { model.settings.value.appLauncherIcon == AppLauncherIcon.BLUE_STAR }
        compose.onNodeWithTag("launcher_icon_BLUE_STAR").performScrollTo().assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("Grok bot").assertIsDisplayed()
        capture("launcher-icons-dark-large.png")
        compose.onNodeWithTag("launcher_icon_MODERN").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { model.settings.value.appLauncherIcon == AppLauncherIcon.MODERN }
        compose.onNodeWithTag("launcher_icon_MODERN").assertIsSelected()
    }

    @Test fun labelsLanguagesAndUpgradeRepairRetainOneWorkingLauncher() = runBlocking {
        for (label in AppLauncherLabel.entries) {
            manager.updateAppLauncherLabel(label)
            manager.updateAppLauncherIcon(AppLauncherIcon.BLUE_STAR)
            val alias = if (label == AppLauncherLabel.MONICA_PASS) "BlueStarVisibleLauncherAlias" else "BlueStarVisibleLauncherAliasMonica"
            val labelRes = if (label == AppLauncherLabel.MONICA_PASS) R.string.app_label else R.string.app_name
            for (language in listOf(Language.CHINESE, Language.ENGLISH)) {
                manager.updateLanguage(language)
                assertLauncher(alias, labelRes)
                assertEquals(AppLauncherIcon.BLUE_STAR, AppLauncherIconManager.getCurrentSelection(context))
            }
            // Simulate a stale upgrade state with an additional legacy launcher.
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, "takagi.ru.monica.ClassicVisibleLauncherAlias"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP,
            )
            AppLauncherIconManager.repairLaunchEntryPointsAfterUpgrade(context, AppLauncherIcon.BLUE_STAR, label)
            assertLauncher(alias, labelRes)
            manager.updateAppLauncherIcon(AppLauncherIcon.MODERN)
            manager.updateLanguage(Language.CHINESE)
            assertLauncher(if (label == AppLauncherLabel.MONICA_PASS) "ModernVisibleLauncherAlias" else "ModernVisibleLauncherAliasMonica", labelRes)
        }
    }

    @Test fun pageSettingsRoundTripPreservesIconAndUnknownValuesFallBack() = runBlocking {
        manager.updateAppLauncherIcon(AppLauncherIcon.BLUE_STAR)
        val selected = manager.exportPageAdjustmentSettings()
        assertEquals("BLUE_STAR", selected.appLauncherIcon)
        manager.updateAppLauncherIcon(AppLauncherIcon.MODERN)
        manager.importPageAdjustmentSettings(selected)
        withTimeout(10_000) { manager.settingsFlow.first { it.appLauncherIcon == AppLauncherIcon.BLUE_STAR } }
        assertLauncher("BlueStarVisibleLauncherAlias", R.string.app_label)
        manager.importPageAdjustmentSettings(selected.copy(appLauncherIcon = "UNKNOWN_FUTURE_ICON"))
        assertEquals(AppLauncherIcon.MODERN, manager.settingsFlow.first().appLauncherIcon)
        assertLauncher("ModernVisibleLauncherAlias", R.string.app_label)
    }

    private fun assertLauncher(alias: String, labelRes: Int) {
        val pm = context.packageManager
        val launchers = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
        assertEquals("Exactly one visible launcher", 1, launchers.size)
        val info = launchers.single().activityInfo
        assertEquals("takagi.ru.monica.$alias", info.name)
        assertEquals("takagi.ru.monica.MainActivity", info.targetActivity)
        assertTrue(info.exported)
        // ActivityInfo.enabled retains the manifest default on API 32; the
        // runtime override is authoritative for launcher aliases.
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            pm.getComponentEnabledSetting(ComponentName(context, info.name)))
        assertEquals(context.getString(labelRes), info.loadLabel(pm).toString())
        if (alias.startsWith("BlueStar")) {
            assertTrue(info.icon == R.mipmap.ic_launcher_blue_star || info.icon == R.mipmap.ic_launcher_blue_star_round)
            val bitmap = info.loadIcon(pm).toBitmap(256, 256)
            File(context.getExternalFilesDir(null), "launcher-blue-star-system.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        requireNotNull(bitmap)
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
