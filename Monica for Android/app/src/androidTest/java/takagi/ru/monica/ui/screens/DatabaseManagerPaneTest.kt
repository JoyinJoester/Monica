package takagi.ru.monica.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import androidx.lifecycle.viewModelScope
import takagi.ru.monica.credentialexchange.*
import takagi.ru.monica.repository.*

class DatabaseManagerPaneTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun wake() {
        compose.activity.setShowWhenLocked(true); compose.activity.setTurnScreenOn(true)
        compose.runOnUiThread { compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
    }
    private val database = ImportDestination(ImportDestinationKind.MDBX, 123)
    private val nodes = listOf(row("folder", null, true)) + (0..120).map { row("entry%03d".format(it), "folder") }
    private fun row(id: String, parent: String? = null, folder: Boolean = false) = MdbxStructureNode(id, parent, id,
        if (folder) MdbxStructureNodeType.FOLDER else MdbxStructureNodeType.ENTRY, id, MdbxStructureNodeStatus.UNCHANGED, 0, "login")
    private fun pane() = DatabaseManagerPane(database.key).apply { snapshot = DatabaseManagerSnapshot(DatabaseManagerLocation(database), "Synthetic vault", nodes) }
    @Test fun actualScreenCopiesSelectionRightAndMovesOneItemLeftIntoFocusedFolders() = runBlocking<Unit> {
        val fixture = TransferFixture()
        val app = fixture.context.applicationContext as android.app.Application
        val db = fixture.db
        val mdbxVm = takagi.ru.monica.viewmodel.MdbxViewModel(app, db.localMdbxDatabaseDao(), db.mdbxRemoteSourceDao(),
            db.passwordEntryDao(), db.secureItemDao(), db.passkeyDao(), db.attachmentDao(), db.customFieldDao(), fixture.security)
        val keepassVm = takagi.ru.monica.viewmodel.LocalKeePassViewModel(app, db.localKeePassDatabaseDao(), fixture.security)
        var show by mutableStateOf(true)
        try {
            val repo = DatabaseManagerRepository(fixture.context)
            val source = fixture.mdbx(); val target = fixture.keepass()
            repeat(2) { index -> fixture.passwords.insertPasswordEntry(takagi.ru.monica.data.PasswordEntry(title = "${fixture.prefix}-$index",
                website = "", username = "synthetic", password = fixture.security.encryptData("never show this secret"), mdbxDatabaseId = source.databaseId)) }
            val ids = repo.mdbx.nativeBrowser(source.databaseId).objects.keys.toList()
            val id = ids.first()
            val folder = repo.createFolder(DatabaseManagerLocation(target), "Target folder")
            compose.setContent { if (show) MaterialTheme { DatabaseManagerScreen(source.key, mdbxVm, keepassVm, {}) } }
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("manager-row-0-$id").fetchSemanticsNodes().isNotEmpty() }
            if (compose.onAllNodesWithTag("manager-pane-1").fetchSemanticsNodes().isEmpty()) {
                compose.onNodeWithContentDescription(fixture.context.getString(takagi.ru.monica.R.string.manager_dual)).performClick()
            }
            compose.onNodeWithTag("manager-row-0-${ids.first()}").performTouchInput { swipeRight() }
            compose.onNodeWithTag("manager-row-0-${ids.last()}").performTouchInput { swipeRight() }
            compose.onNodeWithTag("manager-action-destination").assertIsEnabled()
            compose.onNodeWithTag("manager-list-1").performScrollToKey(target.key)
            compose.onNodeWithTag("manager-store-1-${target.key}").performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("manager-row-1-$folder").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("manager-row-1-$folder").performClick()
            compose.onNodeWithText("never show this secret").assertDoesNotExist()
            screenshot("screen-transfer")
            compose.onNodeWithTag("manager-action-back").performClick()
            compose.onNodeWithTag("manager-row-1-$folder").assertIsDisplayed()
            compose.onNodeWithTag("manager-action-forward").performClick()
            compose.onNodeWithTag("manager-row-1-$folder").assertDoesNotExist()
            // Browsing the destination activates it, without making the left selection its source.
            compose.onNodeWithTag("manager-action-copy").assertDoesNotExist()
            compose.onNodeWithTag("manager-header-0").performClick()
            screenshot("focused-selection")
            compose.onNodeWithTag("manager-action-copy").assertIsEnabled().performClick()
            compose.onNodeWithTag("manager-confirm-transfer").performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithText(fixture.context.getString(takagi.ru.monica.R.string.manager_result)).fetchSemanticsNodes().isNotEmpty() }
            val targetEntries = repo.keepass.openNativeBrowser(target.databaseId).getOrThrow().entries
            assertEquals(compose.onNode(isDialog()).printToString(), setOf("${fixture.prefix}-0", "${fixture.prefix}-1"), targetEntries.map { it.title }.toSet())
            assertTrue(targetEntries.all { it.parentGroup.groupUuid.toString() == folder })
            assertTrue(repo.mdbx.nativeBrowser(source.databaseId).objects.keys.containsAll(ids))
            compose.onNode(hasText(fixture.context.getString(takagi.ru.monica.R.string.close)) and hasAnyAncestor(isDialog())).performClick()
            // A right-pane single-item menu transfers in the opposite direction, independent of left history.
            val rightId = targetEntries.first().identity.entryUuid.toString()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("manager-row-1-$rightId").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("manager-row-1-$rightId").performTouchInput { longClick() }
            compose.onNodeWithTag("manager-menu-rename").assertIsDisplayed()
            screenshot("single-menu")
            compose.onNodeWithTag("manager-menu-move").assertIsEnabled().performClick()
            compose.onNodeWithTag("manager-confirm-transfer").performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithText(fixture.context.getString(takagi.ru.monica.R.string.manager_result)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(fixture.context.getString(takagi.ru.monica.R.string.manager_result_count, 1, 0)).assertIsDisplayed()
            assertEquals(3, repo.mdbx.nativeBrowser(source.databaseId).objects.size)
            val remaining = repo.keepass.openNativeBrowser(target.databaseId).getOrThrow().entries
            assertEquals(1, remaining.count { it.parentGroup.groupUuid.toString() == folder })
            assertTrue(remaining.single { it.identity.entryUuid.toString() == rightId }.isInRecycleBin)
        } finally {
            compose.runOnIdle { show = false }
            mdbxVm.viewModelScope.cancel(); keepassVm.viewModelScope.cancel()
            fixture.close()
        }
    }
    @Test fun longTitlesWrapWithinPaneAndKeepMenuVisibleWithoutHorizontalScrolling() {
        val left = pane(); val right = pane()
        left.snapshot = DatabaseManagerSnapshot(DatabaseManagerLocation(database), "Long names",
            listOf(row("long").copy(name = "A long original entry title that must wrap into at most two lines within its pane")))
        compose.setContent { MaterialTheme { Surface { Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DatabaseManagerPaneContent(left, emptyList(), "left", true, true, true, {}, {}, Modifier.weight(1f))
            DatabaseManagerPaneContent(right, emptyList(), "right", true, false, true, {}, {}, Modifier.weight(1f))
        } } } }
        val paneBounds = compose.onNodeWithTag("manager-pane-left").getUnclippedBoundsInRoot()
        val rowBounds = compose.onNodeWithTag("manager-row-left-long").getUnclippedBoundsInRoot()
        assertEquals(paneBounds.right - paneBounds.left, rowBounds.right - rowBounds.left)
        compose.onNodeWithTag("manager-menu-left-long").assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("manager-title-left-long", useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(2, layouts.single().lineCount)
        assertTrue(rowBounds.bottom - rowBounds.top < 100.dp)
        val initialRight = compose.onNodeWithTag("manager-row-right-folder").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("manager-list-left").performTouchInput { swipeLeft() }
        assertEquals(rowBounds.left, compose.onNodeWithTag("manager-row-left-long").getUnclippedBoundsInRoot().left)
        assertEquals(initialRight, compose.onNodeWithTag("manager-row-right-folder").getUnclippedBoundsInRoot())
        screenshot("bounded-titles")
    }

    @Test fun landscapeDarkLargeTextKeepsBothPathsIndependent() {
        compose.runOnUiThread { compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        val left = pane().apply { selected = setOf("folder") }; val right = pane()
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density, 1.4f)) {
                MaterialTheme(colorScheme = darkColorScheme()) { Surface { Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatabaseManagerPaneContent(left, emptyList(), "left", true, false, true, {}, {}, Modifier.weight(1f))
                    DatabaseManagerPaneContent(right, emptyList(), "right", true, true, true, {}, {}, Modifier.weight(1f))
                } } }
            }
        }
        compose.onNodeWithTag("manager-row-right-folder").performClick()
        compose.onNodeWithTag("manager-list-right").performScrollToKey("entry060")
        compose.onNodeWithTag("manager-row-right-entry060").assertIsDisplayed()
        compose.onNodeWithTag("manager-row-left-folder").assertIsDisplayed()
        compose.runOnIdle { assertEquals(setOf("folder"), left.selected); assertNull(left.folderId) }
        screenshot("landscape-dark-large")
    }
    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = java.io.File(context.filesDir, "database-manager-test").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            java.io.File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
    @Test fun dualPanesNavigateAndScrollIndependently() {
        val left = pane(); val right = pane()
        compose.setContent { MaterialTheme { Surface { Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DatabaseManagerPaneContent(left, emptyList(), "left", true, true, true, {}, {}, Modifier.weight(1f))
            DatabaseManagerPaneContent(right, emptyList(), "right", true, false, true, {}, {}, Modifier.weight(1f))
        } } } }
        compose.onNodeWithTag("manager-row-left-folder").performClick()
        compose.onNodeWithTag("manager-list-left").performScrollToKey("entry100")
        compose.onNodeWithTag("manager-row-left-entry100").assertIsDisplayed()
        compose.onNodeWithTag("manager-row-right-folder").assertIsDisplayed()
        compose.runOnIdle { assertEquals("folder", left.folderId); assertNull(right.folderId) }
        screenshot("independent")
    }
    @Test fun swipeEntersSelectionTapTogglesOnlyOneAndAnotherSwipeSelectsRange() {
        val left = pane().apply { folderId = "folder" }; val right = pane()
        var opens = 0
        compose.setContent { MaterialTheme { Surface { Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DatabaseManagerPaneContent(left, emptyList(), "left", true, true, true, {}, { opens++ }, Modifier.weight(1f))
            DatabaseManagerPaneContent(right, emptyList(), "right", true, false, true, {}, {}, Modifier.weight(1f))
        } } } }
        compose.onNodeWithTag("manager-row-left-entry000").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(setOf("entry000"), left.selected) }
        compose.onNodeWithTag("manager-row-left-entry003").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals((0..3).map { "entry%03d".format(it) }.toSet(), left.selected) }
        compose.onNodeWithTag("manager-row-left-entry001").performClick()
        compose.runOnIdle { assertEquals(setOf("entry000", "entry002", "entry003"), left.selected); assertEquals(0, opens) }
        compose.onNodeWithTag("manager-row-left-entry002").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(setOf("entry001", "entry002"), left.selected); assertTrue(right.selected.isEmpty()) }
        screenshot("swipe-selection")
    }

    @Test fun longPressUsesTheTouchedPanesSingleOrMultipleItemsAndNeverOpensThem() {
        val left = pane().apply { folderId = "folder"; selected = setOf("entry000", "entry001") }
        val right = pane().apply { folderId = "folder" }
        var active = -1; var menuIds = emptySet<String>(); var opens = 0
        compose.setContent { MaterialTheme { Surface { Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DatabaseManagerPaneContent(left, emptyList(), "left", true, true, true, { active = 0 }, { opens++ }, Modifier.weight(1f), onActions = { menuIds = it })
            DatabaseManagerPaneContent(right, emptyList(), "right", true, false, true, { active = 1 }, { opens++ }, Modifier.weight(1f), onActions = { menuIds = it })
        } } } }
        compose.onNodeWithTag("manager-row-left-entry000").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(0, active); assertEquals(setOf("entry000", "entry001"), menuIds) }
        compose.onNodeWithTag("manager-row-right-entry002").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, active); assertEquals(setOf("entry002"), menuIds); assertEquals(0, opens); assertTrue(right.selected.isEmpty()) }
    }

    @Test fun paneRestorationRetainsItsOwnFolderSearchAndSelection() {
        lateinit var restored: DatabaseManagerPane
        val restorer = StateRestorationTester(compose)
        restorer.setContent {
            val state = rememberSaveable(saver = DatabaseManagerPane.Saver) { pane() }
            restored = state
            LaunchedEffect(state) { state.snapshot = DatabaseManagerSnapshot(DatabaseManagerLocation(database), "Synthetic vault", nodes) }
            MaterialTheme { Surface { DatabaseManagerPaneContent(state, emptyList(), "left", true, true, true, {}, {}, Modifier.fillMaxSize()) } }
        }
        compose.runOnIdle { restored.folderId = "folder"; restored.query = "entry"; restored.search = true; restored.selected = setOf("entry001", "entry002") }
        restorer.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals("folder", restored.folderId); assertEquals("entry", restored.query); assertEquals(2, restored.selected.size) }
        compose.onNodeWithTag("manager-search-left").assertIsDisplayed()
    }
}
