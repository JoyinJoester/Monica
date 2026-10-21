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
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.ui.screens.NearbySyncActions
import takagi.ru.monica.ui.screens.NearbySyncContent
import takagi.ru.monica.ui.theme.MonicaTheme

class NearbyDeviceManagementTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun lock() { SessionManager.markLocked() }
    private val peers = listOf(DeviceSyncPeerState("browser","Browser fixture","manual",0),
        DeviceSyncPeerState("phone","Phone fixture","manual",0), DeviceSyncPeerState("tablet","Tablet fixture","manual",0))
    private fun text(id: Int) = compose.activity.getString(id)
    private fun awaitContent() {
        // First launch on the shared emulator may still be verifying dex classes.
        compose.waitUntil(timeoutMillis = 60_000) {
            runCatching { compose.onAllNodesWithTag("nearby-devices").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("capture") != "true") return
        val runner=InstrumentationRegistry.getInstrumentation()
        compose.runOnIdle { compose.activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
        compose.waitForIdle()
        var captured: android.graphics.Bitmap? = null
        // SurfaceFlinger can return null while the first dialog surface is drawing.
        compose.waitUntil(timeoutMillis = 10_000) {
            runner.uiAutomation.takeScreenshot()?.let { captured = it; true } ?: false
        }
        val bitmap=requireNotNull(captured)
        java.io.File(runner.targetContext.cacheDir,"nearby-management-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun long_press_multiselect_and_batch_remove_require_confirmation() {
        SessionManager.markUnlocked(); val state=mutableStateOf(DeviceSyncState(enabled=false,peers=peers)); val removed=mutableListOf<Set<String>>()
        compose.setContent { MonicaTheme { NearbySyncContent(state.value,NearbySyncActions(forgetDevices={ ids ->
            removed+=ids; state.value=state.value.copy(peers=state.value.peers.filterNot { it.deviceId in ids }) })) } }
        awaitContent()
        compose.onNodeWithTag("nearby-peer-browser").performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithTag("nearby-peer-browser").assertIsSelected()
        compose.onNodeWithTag("nearby-peer-phone").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("nearby-forget-selected").performScrollTo().performClick()
        compose.onNodeWithTag("nearby-forget-confirm").assertIsDisplayed(); capture("batch-confirm")
        compose.runOnIdle { assertTrue(removed.isEmpty()) }
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        compose.onNodeWithTag("nearby-peer-browser").assertIsSelected()
        compose.onNodeWithTag("nearby-forget-selected").performScrollTo().performClick()
        compose.onNodeWithTag("nearby-forget-accept").performClick()
        compose.runOnIdle { assertEquals(listOf(setOf("browser","phone")),removed); assertEquals(listOf("tablet"),state.value.peers.map { it.deviceId }) }
        compose.onNodeWithTag("nearby-peer-tablet").performScrollTo().assertExists(); capture("retained-device")
    }
    @Test fun swipe_delete_can_be_cancelled_and_swipe_right_selects_the_same_row() {
        SessionManager.markUnlocked(); var forgotten=0
        compose.setContent { MonicaTheme { NearbySyncContent(DeviceSyncState(enabled=false,peers=peers),NearbySyncActions(forget={ forgotten++ })) } }
        awaitContent()
        val row=compose.onNodeWithTag("nearby-peer-browser")
        row.performScrollTo().performTouchInput { swipeLeft() }
        compose.onNodeWithTag("nearby-forget-confirm").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0,forgotten) }
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        row.performScrollTo().assertIsDisplayed().performTouchInput { swipeRight() }
        row.assertIsSelected()
        row.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("nearby-forget-accept").performClick()
        compose.runOnIdle { assertEquals(1,forgotten) }
    }
    @Test fun incoming_deletion_dialog_offers_keep_include_and_cancel_as_distinct_actions() {
        SessionManager.markUnlocked()
        val review=DeviceDeletionReview("synthetic","browser","Browser fixture",3)
        val state=mutableStateOf(DeviceSyncState(enabled=false,peers=peers,deletionReview=review))
        val decisions=mutableListOf<Boolean>(); var cancellations=0
        compose.setContent { MonicaTheme(darkTheme=true) { NearbySyncContent(state.value,NearbySyncActions(
            receivedDeletions={ actual,include -> assertEquals(review,actual); decisions+=include; state.value=state.value.copy(deletionReview=null) },
            cancelSync={ cancellations++; state.value=state.value.copy(deletionReview=null) })) } }
        compose.onNodeWithTag("nearby-deletion-review").assertIsDisplayed(); capture("received-deletions")
        compose.onNodeWithTag("nearby-deletions-keep").performClick()
        compose.runOnIdle { assertEquals(listOf(false),decisions); state.value=state.value.copy(deletionReview=review) }
        compose.onNodeWithTag("nearby-deletions-include").performClick()
        compose.runOnIdle { assertEquals(listOf(false,true),decisions); state.value=state.value.copy(deletionReview=review) }
        compose.onNodeWithText(text(R.string.nearby_cancel_sync)).performClick()
        compose.runOnIdle { assertEquals(1,cancellations); assertEquals(2,decisions.size) }
    }
}
