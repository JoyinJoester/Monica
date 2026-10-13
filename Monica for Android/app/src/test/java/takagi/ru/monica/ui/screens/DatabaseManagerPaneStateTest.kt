package takagi.ru.monica.ui.screens

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.credentialexchange.ImportDestination
import takagi.ru.monica.credentialexchange.ImportDestinationKind
import takagi.ru.monica.repository.DatabaseManagerLocation
import takagi.ru.monica.repository.DatabaseManagerSnapshot

class DatabaseManagerPaneStateTest {
    @Test fun tapTogglesOneItemAndSwipeSelectsRangeFromTheLastAnchor() {
        val pane = DatabaseManagerPane()
        val rows = listOf("a", "b", "c", "d", "e")
        pane.swipeSelect("b", rows)
        assertEquals(setOf("b"), pane.selected)
        assertTrue(pane.isSelecting)
        pane.swipeSelect("e", rows)
        assertEquals(setOf("b", "c", "d", "e"), pane.selected)
        pane.swipeSelect("c", rows)
        assertEquals(setOf("b", "c"), pane.selected)
        pane.toggleSelection("e")
        assertEquals(setOf("b", "c", "e"), pane.selected)
        pane.swipeSelect("d", rows)
        assertEquals(setOf("d", "e"), pane.selected)
    }

    @Test fun deselectingLastItemStaysInSelectionModeUntilExplicitExit() {
        val pane = DatabaseManagerPane()
        pane.toggleSelection("one"); pane.toggleSelection("one")
        assertTrue(pane.selected.isEmpty()); assertTrue(pane.isSelecting)
        pane.clearSelection()
        assertFalse(pane.isSelecting); assertNull(pane.selectionAnchor)
    }

    @Test fun panesKeepIndependentHistoryAndDestinationNavigationDoesNotTouchSourceSelection() {
        val left = DatabaseManagerPane("LOCAL:0")
        val right = DatabaseManagerPane()
        left.toggleSelection("password:1")
        right.navigate("KEEPASS:4"); right.navigate("KEEPASS:4", "folder")
        right.goBack()
        assertNull(right.folderId); assertTrue(right.canGoForward)
        right.goForward()
        assertEquals("folder", right.folderId)
        right.goBack(); right.navigate("MDBX:2")
        assertFalse(right.canGoForward)
        assertEquals(setOf("password:1"), left.selected)
        assertFalse(left.canGoBack)
    }

    @Test fun navigationClearsSelectionAnchorAndReturningToSameLocationDoesNotAddHistory() {
        val pane = DatabaseManagerPane("LOCAL:0")
        pane.navigate("LOCAL:0"); assertFalse(pane.canGoBack)
        pane.toggleSelection("one"); pane.navigate("LOCAL:0", "folder:2")
        assertTrue(pane.selected.isEmpty()); assertNull(pane.selectionAnchor)
        pane.goBack(); assertNull(pane.folderId); assertFalse(pane.isSelecting)
        pane.goForward(); assertEquals("folder:2", pane.folderId)
    }

    @Test fun staleSnapshotFromPreviousDatabaseCannotEnableActions() {
        val local = ImportDestination.Local
        val pane = DatabaseManagerPane(local.key)
        pane.snapshot = DatabaseManagerSnapshot(DatabaseManagerLocation(local), "Local", emptyList())
        assertTrue(pane.isReady)
        pane.navigate(ImportDestination(ImportDestinationKind.MDBX, 2).key)
        assertFalse(pane.isReady)
    }
}
