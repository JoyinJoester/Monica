package takagi.ru.monica.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.R
import takagi.ru.monica.data.*
import takagi.ru.monica.data.bitwarden.*
import takagi.ru.monica.repository.MdbxStoredFolderEntry
import takagi.ru.monica.repository.ExternalFolderTarget
import takagi.ru.monica.ui.category.ExternalFolderActionDialogs
import takagi.ru.monica.ui.components.*
import takagi.ru.monica.utils.KeePassGroupInfo
import takagi.ru.monica.viewmodel.CategoryFilter

@RunWith(AndroidJUnit4::class)
class FilterFolderActionsTest {
    @Test fun externalFolderCopySelectsNativeIdentityAndKeepsErrorsVisible() {
        val destination = takagi.ru.monica.credentialexchange.ImportDestination(
            takagi.ru.monica.credentialexchange.ImportDestinationKind.KEEPASS, 11)
        val store = takagi.ru.monica.repository.DatabaseManagerStore(destination, "Test KeePass")
        val folder = takagi.ru.monica.repository.DatabaseManagerLocation(destination, "native-uuid")
        var dismissed = false
        var received: takagi.ru.monica.repository.DatabaseManagerLocation? = null
        var attempts = 0
        compose.setContent { MaterialTheme {
            ExternalFolderActionDialogs(ExternalFolderTarget.KeePass(11, "Work", "uuid", "Work"),
                { dismissed = true }, {}, {},
                onTransfer = { location, copy ->
                    org.junit.Assert.assertTrue(copy)
                    received = location
                    attempts++
                    if (attempts == 1) error("Synthetic save failure")
                },
                loadStores = { listOf(store) }, loadFolders = { listOf(folder to "Parent / Destination") })
        } }
        compose.onNodeWithText(compose.activity.getString(R.string.copy)).performClick()
        compose.onNodeWithTag("external-folder-confirm").assertIsNotEnabled()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("external-folder-store-KEEPASS:11").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("external-folder-store-KEEPASS:11").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("external-folder-location-native-uuid").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("external-folder-location-native-uuid").performClick()
        captureTransferDialog()
        compose.onNodeWithTag("external-folder-confirm").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Synthetic save failure", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertFalse(dismissed); assertEquals(folder, received) }
        compose.onNodeWithTag("external-folder-confirm").performClick()
        compose.waitUntil(5_000) { dismissed }
    }

    private fun captureTransferDialog() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { java.io.File(instrumentation.targetContext.cacheDir, "external-folder-transfer.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        } } finally { bitmap.recycle() }
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun awake() { compose.runOnUiThread {
        compose.activity.setTurnScreenOn(true)
        compose.activity.setShowWhenLocked(true)
        compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } }

    @Test fun passwordKeePassEditOpensActions() = password(CategoryFilter.KeePassGroupFilter(11, "Work", "11111111-1111-1111-1111-111111111111"))
    @Test fun passwordMdbxEditOpensActions() = password(CategoryFilter.MdbxFolderFilter(22, "folder"))
    @Test fun passwordBitwardenEditOpensActions() = password(CategoryFilter.BitwardenFolderFilter(vaultId = 33, folderId = "folder"))
    @Test fun sharedKeePassEditOpensActions() = shared(UnifiedCategoryFilterSelection.KeePassDatabaseFilter(11))
    @Test fun sharedMdbxEditOpensActions() = shared(UnifiedCategoryFilterSelection.MdbxDatabaseFilter(22))
    @Test fun sharedBitwardenEditOpensActions() = shared(UnifiedCategoryFilterSelection.BitwardenVaultFilter(33))

    @Test fun renameFailureKeepsInputAndCanRetry() {
        var attempts = 0
        var dismissed = false
        var renamed = ""
        compose.setContent { MaterialTheme {
            ExternalFolderActionDialogs(ExternalFolderTarget.Mdbx(22, "folder", "Work"),
                { dismissed = true }, onRename = {
                    attempts++
                    if (attempts == 1) error("Synthetic read-only failure")
                    renamed = it
                }, onDelete = { error("Unexpected delete") })
        } }
        compose.onNodeWithText(compose.activity.getString(R.string.rename_category)).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Renamed")
        compose.onNodeWithText(compose.activity.getString(R.string.confirm)).performClick()
        compose.waitUntil(5_000) { attempts == 1 }
        compose.onNodeWithText("Synthetic read-only failure", substring = true).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("Renamed")
        compose.runOnIdle { assertFalse(dismissed) }
        compose.onNodeWithText(compose.activity.getString(R.string.confirm)).performClick()
        compose.waitUntil(5_000) { dismissed }
        compose.runOnIdle { assertEquals("Renamed", renamed); assertEquals(2, attempts) }
    }

    @Test fun deleteRequiresConfirmationAndCancelDoesNotWrite() {
        var deleted = 0
        var dismissed = false
        compose.setContent { MaterialTheme {
            ExternalFolderActionDialogs(ExternalFolderTarget.Bitwarden(33, "folder", "Work"),
                { dismissed = true }, onRename = {}, onDelete = { deleted++ })
        } }
        compose.onNodeWithText(compose.activity.getString(R.string.delete)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.external_folder_delete_keep_items, "Work")).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, deleted) }
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
        compose.runOnIdle { assertEquals(0, deleted); assertEquals(true, dismissed) }
    }

    @Test fun localEditingAndBackNavigationRemainAvailable() {
        var edited: Category? = null
        var selected: CategoryFilter? = null
        val category = Category(id = 7, name = "Local")
        compose.setContent { MaterialTheme {
            PasswordQuickFolderFlow(PasswordQuickFolderFlowParams(CategoryFilter.Custom(7), listOf(
                PasswordQuickFolderShortcut("local", "Local", "", false, CategoryFilter.Custom(7), null, category),
                PasswordQuickFolderShortcut("back", "Back", "", true, CategoryFilter.Local, null)),
                true, listOf(category), { edited = it }, { selected = it }))
        } }
        compose.onNodeWithText("Local").performClick()
        compose.runOnIdle { assertEquals(category, edited); assertEquals(null, selected) }
        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle { assertEquals(CategoryFilter.Local, selected) }
    }

    private fun password(target: CategoryFilter) {
        var selected: CategoryFilter? = null
        compose.setContent { MaterialTheme {
            var editing by remember { mutableStateOf(false) }
            Column {
                TextButton(onClick = { editing = !editing }) { Text("Toggle edit") }
                PasswordQuickFolderFlow(PasswordQuickFolderFlowParams(
                    CategoryFilter.All, listOf(PasswordQuickFolderShortcut("folder", "Work", "", false, target, null)),
                    editing, emptyList(), {}, { selected = it }))
            }
        } }
        verifyActions(scroll = false)
        compose.runOnIdle { assertEquals(null, selected) }
    }

    private fun shared(initial: UnifiedCategoryFilterSelection) {
        var selected by mutableStateOf(initial)
        compose.setContent { MaterialTheme {
            var editing by remember { mutableStateOf(false) }
            UnifiedCategoryFilterChipMenu(true, {}, selected, { selected = it },
                categories = emptyList(),
                keepassDatabases = listOf(LocalKeePassDatabase(11, "KeePass", "synthetic")),
                mdbxDatabases = listOf(LocalMdbxDatabase(22, "MDBX", "synthetic")),
                bitwardenVaults = listOf(BitwardenVault(id = 33, email = "fixture@example.invalid")),
                getBitwardenFolders = { flowOf(listOf(BitwardenFolder(vaultId = 33, bitwardenFolderId = "folder", name = "Work", revisionDate = "test"))) },
                getMdbxFolders = { flowOf(listOf(MdbxStoredFolderEntry("folder", "root", "Work", "Work", 1))) },
                getKeePassGroups = { flowOf(listOf(KeePassGroupInfo("Work", "Work", "11111111-1111-1111-1111-111111111111"))) },
                categoryEditMode = editing, onRequestCategoryAction = {}, showQuickFilters = false,
                trailingContent = { TextButton(onClick = { editing = !editing }) { Text("Toggle edit") } })
        } }
        verifyActions()
        compose.runOnIdle { assertEquals(initial, selected) }
    }

    private fun verifyActions(scroll: Boolean = true) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Work").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Toggle edit").performClick()
        val folder = compose.onNodeWithText("Work")
        if (scroll) folder.performScrollTo()
        folder.performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.edit_category)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.rename_category)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.move)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.copy)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.delete)).assertIsDisplayed()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments()
                .getString("captureFolderActions") == "true") {
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            try {
                java.io.File(instrumentation.targetContext.cacheDir,
                    "folder-actions-dialog.png").outputStream().use {
                    check(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { screenshot.recycle() }
        }
    }
}
