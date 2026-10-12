package takagi.ru.monica.lansync

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.ui.screens.NearbySyncActions
import takagi.ru.monica.ui.screens.NearbySyncContent
import takagi.ru.monica.ui.theme.MonicaTheme

class NearbyManualControlsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun lock() { SessionManager.markLocked() }
    private fun text(id: Int) = compose.activity.getString(id)
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("capture") != "true") return
        val runner = InstrumentationRegistry.getInstrumentation()
        compose.waitForIdle()
        // This activity contains synthetic controls only; allow local design evidence.
        compose.runOnIdle { compose.activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
        var bitmap = runner.uiAutomation.takeScreenshot()
        repeat(10) { if (bitmap == null) { Thread.sleep(200); bitmap = runner.uiAutomation.takeScreenshot() } }
        java.io.File(runner.targetContext.cacheDir, "nearby-$name.png").outputStream().use {
            requireNotNull(bitmap).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun manual_sync_stays_available_and_database_selection_defaults_to_local() {
        SessionManager.markUnlocked()
        val state = mutableStateOf(DeviceSyncState(enabled = false,
            peers = listOf(DeviceSyncPeerState("fixture", "Android fixture", "manual", 0))))
        var requested = 0
        val database = LocalMdbxDatabase(id = 42L, name = "Fixture MDBX", filePath = "fixture-only")
        compose.setContent { MonicaTheme { NearbySyncContent(state.value, NearbySyncActions(
            sync = { requested++; state.value = state.value.copy(manualSync = true) },
            database = { state.value = state.value.copy(databaseId = it) },
            cancelSync = { state.value = state.value.copy(manualSync = false) }), listOf(database)) } }
        compose.onNodeWithText(text(R.string.nearby_local), useUnmergedTree = true).assertExists()
        compose.onNodeWithText(text(R.string.device_sync_now)).performScrollTo().assertIsEnabled()
        capture("manual")
        compose.onNodeWithText(text(R.string.nearby_database)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.nearby_database_hint)).assertIsDisplayed()
        capture("database")
        compose.onNodeWithText("Fixture MDBX").performClick()
        compose.runOnIdle { assertEquals(42L, state.value.databaseId) }
        compose.onNodeWithText(text(R.string.nearby_database)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.nearby_local)).performClick()
        compose.runOnIdle { assertNull(state.value.databaseId) }
        compose.onNodeWithText(text(R.string.device_sync_now)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.nearby_cancel_sync)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, requested); assertFalse(state.value.enabled) }
        compose.onNodeWithText(text(R.string.device_sync_now)).assertIsEnabled()
    }
}
