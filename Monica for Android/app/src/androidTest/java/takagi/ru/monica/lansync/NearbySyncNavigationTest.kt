package takagi.ru.monica.lansync

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.navigation.Screen
import takagi.ru.monica.navigation.nearbySyncDestinations
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.ui.theme.MonicaTheme

class NearbySyncNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var nav: NavHostController
    private fun text(id: Int) = compose.activity.getString(id)
    @After fun lock() { SessionManager.markLocked() }
    private fun capture(name: String) {
        val runner = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("capture") != "true") return
        compose.waitForIdle()
        var bitmap = runner.uiAutomation.takeScreenshot()
        repeat(10) { if (bitmap == null) { Thread.sleep(200); bitmap = runner.uiAutomation.takeScreenshot() } }
        java.io.File(runner.targetContext.cacheDir, "nearby-$name.png").outputStream().use {
            requireNotNull(bitmap) { "Screenshot unavailable after display configuration change" }
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun open() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(compose.activity.packageName, Manifest.permission.CAMERA)
        SessionManager.markUnlocked()
        compose.setContent { MonicaTheme {
            nav = rememberNavController()
            NavHost(nav, startDestination = "backup-fixture") {
                composable("backup-fixture") { Button({ nav.navigate(Screen.LanSync.route) }) { Text("Open nearby fixture") } }
                nearbySyncDestinations(nav)
            }
        } }
        compose.onNodeWithText("Open nearby fixture").performClick()
        compose.waitForIdle()
        capture("main")
    }
    @Test fun scannerToolbarAndSystemBackBothReturnToNearbyPage() {
        open()
        repeat(2) { index ->
            compose.onNodeWithText(text(R.string.lan_scan)).performScrollTo().performClick()
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(Screen.LanSyncScanner.route, nav.currentDestination?.route) }
            capture("scanner")
            if (index == 0) compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            else compose.onNodeWithContentDescription(text(R.string.go_back)).performClick()
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(Screen.LanSync.route, nav.currentDestination?.route) }
            compose.onNodeWithText(text(R.string.device_sync_show_qr)).assertIsDisplayed()
        }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Open nearby fixture").assertIsDisplayed()
    }
    @Test fun packagedNativeGeneratesQrAndCopyableLinkAndSurvivesCancellation() {
        open()
        val coordinator = DeviceSyncCoordinator.get(compose.activity)
        repeat(3) {
            compose.onNodeWithText(text(R.string.device_sync_show_qr)).performClick()
            compose.waitUntil(30_000) { coordinator.state.value.invitations.isNotEmpty() || coordinator.state.value.error != null }
            compose.runOnIdle { assertNull(coordinator.state.value.error) }
            compose.onNodeWithText(text(R.string.lan_copy_link)).performScrollTo()
            capture("qr")
            compose.onNodeWithText(text(R.string.lan_copy_link)).performClick()
            compose.runOnIdle {
                val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val value = clipboard.primaryClip?.getItemAt(0)?.text.toString()
                assertEquals(2, LanPairingInvitation.parse(value)?.version)
                clipboard.clearPrimaryClip()
            }
            compose.onNodeWithText(text(R.string.nearby_cancel_pairing)).performScrollTo().performClick()
            compose.waitUntil(10_000) { !coordinator.state.value.pairing }
        }
        // Cross Android's foreground-service start deadline after repeated start/cancel cycles.
        Thread.sleep(7_000)
        compose.onNodeWithText(text(R.string.device_sync_show_qr)).assertIsDisplayed()
    }
}
