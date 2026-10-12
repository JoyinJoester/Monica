package takagi.ru.monica.lansync

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.navigation.Screen
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.ui.password.LocalNearbySyncMenu
import takagi.ru.monica.ui.password.NearbySyncMenuActions
import takagi.ru.monica.ui.password.PasswordTopActionsDropdownMenu
import takagi.ru.monica.ui.screens.NearbySyncActions
import takagi.ru.monica.ui.screens.NearbySyncContent
import takagi.ru.monica.ui.theme.MonicaTheme

class NearbySyncMenuTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val paired = DeviceSyncState(enabled = false,
        peers = listOf(DeviceSyncPeerState("synthetic-peer", "Test Android", "manual", 0)))
    private val state = MutableStateFlow(paired)
    private lateinit var actions: NearbySyncMenuActions
    private lateinit var nav: NavHostController
    private var starts = 0
    private var dismissed = false

    @After fun lock() { SessionManager.markLocked() }

    private fun open() {
        compose.setContent { MonicaTheme {
            nav = rememberNavController()
            actions = remember { NearbySyncMenuActions(state, syncNow = {
                assertTrue(dismissed)
                assertEquals(Screen.LanSync.route, nav.currentDestination?.route)
                starts++
            }, openStatus = { nav.navigate(Screen.LanSync.route) { launchSingleTop = true } }) }
            CompositionLocalProvider(LocalNearbySyncMenu provides actions) {
                NavHost(nav, startDestination = "vault-menu-fixture") {
                    composable("vault-menu-fixture") {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            Button({ expanded = true; dismissed = false }) { Text("Open menu") }
                            PasswordTopActionsDropdownMenu(expanded, { expanded = false; dismissed = true }) {
                                DropdownMenuItem(text = { Text("Existing action") }, onClick = {})
                            }
                        }
                    }
                    // Keep menu fixtures isolated from the application singleton. The real
                    // scanner/pairing destination is covered by NearbySyncNavigationTest.
                    composable(Screen.LanSync.route) {
                        NearbySyncContent(state.collectAsState().value,
                            NearbySyncActions(back = { nav.popBackStack() }))
                    }
                }
            }
        } }
        compose.onNodeWithText("Open menu").performClick()
    }

    @Test fun noPairingHidesShortcutButKeepsExistingActions() {
        state.value = DeviceSyncState()
        open()
        compose.onNodeWithTag("nearby_sync_menu").assertDoesNotExist()
        compose.onNodeWithText("Existing action").assertIsDisplayed()
    }

    @Test fun manualShortcutWorksWithAutomaticSyncOffAndBackReturnsToVault() {
        state.value = paired.copy(databaseId = 42, mode = DeviceSyncMode.SEND_ONLY)
        open()
        compose.onNodeWithText(compose.activity.getString(R.string.nearby_menu_sync)).assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("capture") == "true") {
            compose.runOnIdle { compose.activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
            compose.waitForIdle()
            val runner = InstrumentationRegistry.getInstrumentation()
            java.io.File(runner.targetContext.cacheDir, "nearby-menu.png").outputStream().use {
                requireNotNull(runner.uiAutomation.takeScreenshot()).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithTag("nearby_sync_menu").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, starts)
            assertFalse(state.value.enabled)
            assertEquals(42L, state.value.databaseId)
            assertEquals(DeviceSyncMode.SEND_ONLY, state.value.mode)
            assertEquals(Screen.LanSync.route, nav.currentDestination?.route)
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.go_back)).performClick()
        compose.onNodeWithText("Open menu").assertIsDisplayed()
        compose.onNodeWithText("Existing action").assertDoesNotExist()
    }

    @Test fun runningSyncOpensProgressWithoutStartingAnotherRound() {
        state.value = paired.copy(manualSync = true, phase = "sending", activeConnections = 1)
        open()
        compose.onNodeWithText(compose.activity.getString(R.string.nearby_menu_progress)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(0, starts); assertEquals(Screen.LanSync.route, nav.currentDestination?.route) }
    }

    @Test fun pairingAndLockDisableEntryAndForgottenPeersRemoveIt() {
        open()
        compose.runOnIdle { state.value = paired.copy(pairing = true) }
        compose.onNodeWithTag("nearby_sync_menu").assertIsNotEnabled()
        compose.runOnIdle { state.value = paired.copy(phase = "locked") }
        compose.onNodeWithTag("nearby_sync_menu").assertIsNotEnabled()
        compose.runOnIdle { state.value = paired.copy(peers = emptyList()) }
        compose.onNodeWithTag("nearby_sync_menu").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, starts) }
    }

    @Test fun latestStateProtectsStaleClicksAndPreparationDoesNotRestart() {
        var starts = 0
        var opens = 0
        val actions = NearbySyncMenuActions(state, { starts++ }, { opens++ })
        listOf(paired.copy(pairing = true), paired.copy(phase = "locked"), paired.copy(peers = emptyList())).forEach {
            state.value = it; actions.open()
        }
        assertEquals(0, starts); assertEquals(0, opens)
        listOf(paired.copy(phase = "preparing"), paired.copy(activeConnections = 1), paired.copy(manualSync = true)).forEach {
            state.value = it; actions.open()
        }
        assertEquals(0, starts); assertEquals(3, opens)
        state.value = paired.copy(phase = "failed", error = "connection_closed")
        actions.open()
        assertEquals(1, starts); assertEquals(4, opens)
    }
}
