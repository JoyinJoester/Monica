package takagi.ru.monica.lansync

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
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

    @Test fun waiting_for_closed_peer_explains_discovery_and_keeps_manual_sync_available() {
        SessionManager.markUnlocked()
        val state = DeviceSyncState(phase = "waiting_for_app", nearbyAppCount = 0,
            peers = listOf(DeviceSyncPeerState("fixture", "Android fixture", "app_closed", 0)))
        compose.setContent { MonicaTheme { NearbySyncContent(state, NearbySyncActions()) } }
        compose.onNodeWithText(text(R.string.nearby_hint_waiting_apps)).performScrollTo().assertIsDisplayed()
        capture("presence")
        compose.onNodeWithText(text(R.string.device_sync_now)).performScrollTo().assertIsEnabled()
        compose.onNodeWithText(text(R.string.nearby_details)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.nearby_diagnostic_presence)).performScrollTo().assertIsDisplayed()
    }

    @Test fun hidden_passkey_notice_remains_viewable_and_can_be_reenabled_in_diagnostics() {
        SessionManager.markUnlocked()
        val issue = DevicePasskeySyncIssue("fixture", "Synthetic account", "example.test",
            takagi.ru.monica.passkey.PasskeyPortability.COUNTER_HISTORY, skipForCounter = true)
        val state = mutableStateOf(DeviceSyncState(enabled = false, passkeyNoticeSuppressed = true,
            passkeyDiagnosticIssues = listOf(issue)))
        var checks = 0
        compose.setContent { MonicaTheme { NearbySyncContent(state.value, NearbySyncActions(
            suppressPasskeyNotice = { state.value = state.value.copy(passkeyNoticeSuppressed = it) },
            refreshPasskeys = { checks++ })) } }
        compose.onNodeWithText(text(R.string.nearby_details)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.nearby_passkey_view)).performScrollTo().performClick()
        compose.onNodeWithTag("nearby_passkey_details_preflight").assertIsDisplayed()
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Synthetic account"))
        compose.onNodeWithText("Synthetic account").assertIsDisplayed()
        compose.onNodeWithTag("nearby_passkey_remember").assertDoesNotExist()
        compose.onNodeWithTag("nearby_passkey_details_skip").performClick()
        compose.onNodeWithText(text(R.string.nearby_passkey_prompt)).performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText(text(R.string.nearby_passkey_refresh)).performScrollTo().performClick()
        compose.runOnIdle { assertFalse(state.value.passkeyNoticeSuppressed); assertEquals(2, checks) }
    }

    @Test fun automatic_switch_changes_while_the_coordinator_is_busy() {
        SessionManager.markUnlocked()
        val context = compose.activity
        val name = "automatic-ui-${java.util.UUID.randomUUID()}"
        val store = DeviceSyncPreferenceStore(context, takagi.ru.monica.security.SecurityManager(context), name)
        store.save(DeviceSyncPreferences())
        val coordinator = DeviceSyncCoordinator::class.java.getDeclaredConstructor(android.content.Context::class.java)
            .apply { isAccessible = true }.newInstance(context)
        fun set(field: String, value: Any?) { DeviceSyncCoordinator::class.java.getDeclaredField(field).apply { isAccessible = true }.set(coordinator, value) }
        set("store\$delegate", lazyOf(store)); set("running", kotlinx.coroutines.Job())
        val gate = DeviceSyncCoordinator::class.java.getDeclaredField("gate").apply { isAccessible = true }.get(coordinator) as kotlinx.coroutines.sync.Mutex
        assertTrue(gate.tryLock())
        try {
            compose.setContent { MonicaTheme { NearbySyncContent(coordinator.state.collectAsState().value,
                NearbySyncActions(enabled = coordinator::setEnabled), emptyList()) } }
            for (phase in listOf("preparing", "sending", "applying", "confirming", "failed", "waiting")) {
                compose.runOnIdle {
                    set("phase", phase); set("pairing", phase == "waiting")
                    DeviceSyncCoordinator::class.java.getDeclaredMethod("publish").apply { isAccessible = true }.invoke(coordinator)
                }
                val toggle = compose.onNodeWithText(text(R.string.device_sync_automatic))
                toggle.performScrollTo().assertIsEnabled().assertIsOn().performClick().assertIsOff()
                compose.runOnIdle { assertFalse(store.load().enabled) }
                toggle.performClick().assertIsOn()
                compose.runOnIdle { assertTrue(store.load().enabled) }
            }
        } finally {
            val scope = DeviceSyncCoordinator::class.java.getDeclaredField("scope").apply { isAccessible = true }.get(coordinator) as kotlinx.coroutines.CoroutineScope
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            gate.unlock(); context.deleteSharedPreferences(name)
        }
    }
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
