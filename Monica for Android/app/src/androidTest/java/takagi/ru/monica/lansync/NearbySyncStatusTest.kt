package takagi.ru.monica.lansync

import androidx.activity.ComponentActivity
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asAndroidBitmap
import takagi.ru.monica.data.LocalMdbxDatabase
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.ui.screens.*
import takagi.ru.monica.ui.theme.MonicaTheme

class NearbySyncStatusTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun lock() { SessionManager.markLocked() }
    private fun text(id: Int) = compose.activity.getString(id)
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("capture") != "true") return
        compose.runOnIdle { compose.activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
        val runner = InstrumentationRegistry.getInstrumentation()
        compose.waitForIdle()
        // Capture the composed status itself: UiAutomation can return a stale
        // blank foreground window during a fixture locale override.
        if (name.startsWith("transfer-") || name.startsWith("failure-")) {
            val bitmap = compose.onNodeWithTag("nearby-status").captureToImage().asAndroidBitmap()
            java.io.File(runner.targetContext.cacheDir, "nearby-status-$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            return
        }
        var bitmap = runner.uiAutomation.takeScreenshot()
        repeat(10) { if (bitmap == null) { Thread.sleep(200); bitmap = runner.uiAutomation.takeScreenshot() } }
        java.io.File(runner.targetContext.cacheDir, "nearby-status-$name.png").outputStream().use {
            requireNotNull(bitmap).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun stableLayout(scale: Float) {
        SessionManager.markUnlocked()
        val initial = DeviceSyncState(enabled = false,
            peers = listOf(DeviceSyncPeerState("fixture", "Pixel fixture", "manual", 0)))
        val state = mutableStateOf(initial)
        val configuration = Configuration(compose.activity.resources.configuration).apply {
            if (scale > 1f) setLocale(Locale.SIMPLIFIED_CHINESE)
        }
        val localized = compose.activity.createConfigurationContext(configuration)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale),
                LocalContext provides localized, LocalConfiguration provides configuration) {
                MonicaTheme(darkTheme = true) { NearbySyncContent(state.value, NearbySyncActions()) }
            }
        }
        val devices = compose.onNodeWithTag("nearby-devices")
        val top = devices.getUnclippedBoundsInRoot().top.value
        fun statusHeight() = compose.onNodeWithTag("nearby-status").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        val height = statusHeight()
        fun indicatorSize() = compose.onNodeWithTag("nearby-sync-indicator").getUnclippedBoundsInRoot().let { (it.right - it.left) to (it.bottom - it.top) }
        val indicatorSize = indicatorSize()
        assertTrue("Status should precede settings", compose.onNodeWithTag("nearby-status").getUnclippedBoundsInRoot().top.value <
            compose.onNodeWithText(localized.getString(R.string.nearby_settings)).getUnclippedBoundsInRoot().top.value)
        val transitions = listOf(
            initial.copy(phase = "preparing", manualSync = true, progress = DeviceSyncProgress("preparing")),
            initial.copy(phase = "preparing", manualSync = true, progress = DeviceSyncProgress("preparing", 64, 300)),
            initial.copy(phase = "transferring", manualSync = true, activeConnections = 1, progress = DeviceSyncProgress("transferring", bytes = 250000, totalBytes = 1000000)),
            initial.copy(phase = "received", manualSync = true, activeConnections = 1),
            initial.copy(phase = "failed", error = "invalid_request", failureStage = "preparing", failureOperation = "replica_resolve"),
            initial.copy(phase = "completed"), initial.copy(phase = "waiting", pairing = true),
            initial.copy(phase = "cancelling", manualSync = true), initial.copy(phase = "cancelled"), initial
        )
        transitions.forEach { next ->
            compose.runOnIdle { state.value = next }
            compose.waitForIdle()
            assertEquals("Device group moved at ${next.phase}", top, devices.getUnclippedBoundsInRoot().top.value, .5f)
            assertEquals("Status height changed at ${next.phase}", height, statusHeight(), .5f)
            assertEquals(indicatorSize, indicatorSize())
            val cardBounds = compose.onNodeWithTag("nearby-status").getUnclippedBoundsInRoot()
            val diagnosticBounds = compose.onNodeWithTag("nearby-diagnostics-button").getUnclippedBoundsInRoot()
            assertEquals("Diagnostics must be horizontally centered at ${next.phase}",
                (cardBounds.left.value + cardBounds.right.value) / 2,
                (diagnosticBounds.left.value + diagnosticBounds.right.value) / 2, .5f)
            if (next.phase == "transferring") {
                compose.onNodeWithTag("nearby-status-title").assertTextEquals(localized.getString(R.string.nearby_status_transferring))
                compose.onNodeWithTag("nearby-sync-percentage").assertTextEquals("25%")
                capture("transfer-$scale")
            }
            if (next.error != null) capture("failure-$scale")
        }
    }

    @Test fun progress_failure_and_completion_keep_device_cards_in_place() = stableLayout(1f)
    @Test fun large_text_keeps_device_cards_in_place() = stableLayout(1.5f)

    @Test fun diagnostics_show_live_state_and_copy_actionable_failure_without_secrets() {
        SessionManager.markUnlocked()
        val initial = DeviceSyncState(enabled = false, phase = "transferring", manualSync = true,
            peers = listOf(DeviceSyncPeerState("fixture", "Pixel fixture", "syncing", 1700000000000)),
            databaseId = 42, activeConnections = 1, connectionAttempts = 2, lastAttemptAt = 1700000001000,
            lastDiscoveryAt = 1700000000500, discoveredPeers = 1, code = "secret-pairing-code",
            invitations = listOf("secret-pairing-link"), failureRecordName = "private-entry-name",
            progress = DeviceSyncProgress("transferring", bytes = 1000, totalBytes = 5000))
        val state = mutableStateOf(initial)
        val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        fun copied() = clipboard.primaryClip!!.getItemAt(0).text.toString()
        compose.setContent { MonicaTheme { NearbySyncContent(state.value, NearbySyncActions(),
            listOf(LocalMdbxDatabase(id = 42, name = "Fixture MDBX", filePath = "fixture-only"))) } }
        compose.onNodeWithText(text(R.string.nearby_view_diagnostics)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.nearby_diagnostic_network)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.nearby_diagnostic_success)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.device_sync_copy_diagnostics)).performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(copied().contains("Fixture MDBX"))
            assertTrue(copied().contains(text(R.string.nearby_status_transferring)))
            assertTrue(copied().contains(text(R.string.nearby_diagnostic_network)))
            assertTrue(copied().contains(text(R.string.nearby_diagnostic_success)))
            assertFalse(copied().contains("secret-pairing")); assertFalse(copied().contains("private-entry-name"))
            state.value = initial.copy(phase = "failed", manualSync = false, progress = null, activeConnections = 0,
                error = "invalid_request", failureStage = "preparing", failureOperation = "replica_resolve", failureKind = "LanSyncException", connectionAttempts = 0)
        }
        compose.onNodeWithText(text(R.string.device_sync_copy_diagnostics)).performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(copied().contains("replica_resolve")); assertTrue(copied().contains("invalid_request"))
            assertTrue(copied().contains(text(R.string.nearby_hint_prepare_failed)))
            state.value = initial.copy(phase="failed",error="peer_merge_failed",failureStage="confirming",failureOperation="peer_apply",progress=null)
        }
        compose.onNodeWithText(text(R.string.device_sync_copy_diagnostics)).performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(copied().contains(text(R.string.nearby_hint_peer_apply)))
            assertFalse(copied().contains("secret-pairing")); assertFalse(copied().contains("private-entry-name"))
            state.value = initial.copy(phase="failed",error="invalid_password_payload",failureStage="applying",failureOperation="password_password_plain_type",progress=null)
        }
        compose.onNodeWithText(text(R.string.device_sync_copy_diagnostics)).performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(copied().contains(text(R.string.nearby_hint_payload)))
            assertTrue(copied().contains("password_password_plain_type"))
        }
        capture("diagnostics")
    }
}
