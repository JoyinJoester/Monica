package takagi.ru.monica.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.credentialexchange.*
import takagi.ru.monica.data.PasswordEntry

class DatabaseManagerDeletionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun wake() { compose.runOnUiThread {
        compose.activity.setShowWhenLocked(true); compose.activity.setTurnScreenOn(true)
        compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } }

    @Test fun actualLocalScreenRequiresConfirmationAndDeletesOnlySelectedItems() = runBlocking<Unit> {
        val f = TransferFixture()
        val app = f.context.applicationContext as android.app.Application
        val db = f.db
        val mdbx = takagi.ru.monica.viewmodel.MdbxViewModel(app, db.localMdbxDatabaseDao(), db.mdbxRemoteSourceDao(),
            db.passwordEntryDao(), db.secureItemDao(), db.passkeyDao(), db.attachmentDao(), db.customFieldDao(), f.security)
        val keepass = takagi.ru.monica.viewmodel.LocalKeePassViewModel(app, db.localKeePassDatabaseDao(), f.security)
        var show by mutableStateOf(true)
        try {
            val ids = (1..3).map { n -> db.passwordEntryDao().insertPasswordEntry(PasswordEntry(title = "${f.prefix}-$n",
                website = "", username = "", password = f.security.encryptData("synthetic secret"))) }
            compose.setContent { if (show) MaterialTheme { DatabaseManagerScreen(ImportDestination.Local.key, mdbx, keepass, {}) } }
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("manager-header-0").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("manager-list-0").fetchSemanticsNodes().isNotEmpty() }
            fun open(id: Long) {
                compose.onNodeWithTag("manager-list-0").performScrollToKey("password:$id")
                compose.onNodeWithTag("manager-row-0-password:$id").performTouchInput { longClick() }
            }
            open(ids[0])
            compose.onNodeWithTag("manager-menu-delete").performScrollTo().performClick()
            compose.onNodeWithTag("manager-delete-dialog").assertIsDisplayed()
            compose.onNodeWithTag("manager-cancel-delete").performClick()
            assertFalse(db.passwordEntryDao().getPasswordEntryById(ids[0])!!.isDeleted)
            for (id in ids.take(2)) {
                open(id)
                compose.onNodeWithTag("manager-menu-select").performScrollTo().performClick()
            }
            compose.onNodeWithTag("manager-action-more").performClick()
            compose.onNodeWithTag("manager-menu-delete").performScrollTo().performClick()
            compose.onNodeWithText(f.context.getString(R.string.manager_delete_source,
                f.context.getString(R.string.manager_left_pane), "Monica", 2)).assertIsDisplayed()
            capture("local-delete-confirmation.png")
            compose.onNodeWithTag("manager-confirm-delete").performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("manager-delete-dialog").fetchSemanticsNodes().isEmpty() }
            assertTrue(db.passwordEntryDao().getPasswordEntryById(ids[0])!!.isDeleted)
            assertTrue(db.passwordEntryDao().getPasswordEntryById(ids[1])!!.isDeleted)
            assertFalse(db.passwordEntryDao().getPasswordEntryById(ids[2])!!.isDeleted)
        } finally {
            compose.runOnIdle { show = false }
            mdbx.viewModelScope.cancel(); keepass.viewModelScope.cancel(); f.close()
        }
    }

    @Test fun confirmationKeepsRightPaneSourceAndVisibleErrorAndBlocksBusyClicks() {
        var busy by mutableStateOf(false)
        var confirmed = 0
        var dismissed = 0
        compose.setContent { MaterialTheme {
            DatabaseManagerDeleteDialog("Synthetic KeePass", 1, 6, false, busy, "Synthetic read-only failure",
                { dismissed++ }, { confirmed++ })
        } }
        compose.onNodeWithText(compose.activity.getString(R.string.manager_delete_source,
            compose.activity.getString(R.string.manager_right_pane), "Synthetic KeePass", 6)).assertIsDisplayed()
        compose.onNodeWithText("Synthetic read-only failure").assertIsDisplayed()
        capture("native-delete-error.png")
        compose.runOnIdle { busy = true }
        compose.onNodeWithTag("manager-confirm-delete").assertIsNotEnabled()
        compose.onNodeWithTag("manager-cancel-delete").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, confirmed); assertEquals(0, dismissed); busy = false }
        compose.onNodeWithTag("manager-cancel-delete").performClick()
        compose.runOnIdle { assertEquals(0, confirmed); assertEquals(1, dismissed) }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(instrumentation.targetContext.cacheDir, name).outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        } } finally { bitmap.recycle() }
    }
}
