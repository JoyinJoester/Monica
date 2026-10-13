package takagi.ru.monica.repository

import org.junit.Assert.*
import org.junit.Test

class DatabaseManagerSelectionTest {
    private fun row(id: String, parent: String? = null, folder: Boolean = false) = MdbxStructureNode(id, parent, id,
        if (folder) MdbxStructureNodeType.FOLDER else MdbxStructureNodeType.ENTRY, id, MdbxStructureNodeStatus.UNCHANGED, 0, "")
    @Test fun swipeRangeCanReverseWithoutTogglingAlreadySelectedRows() {
        val rows = listOf("a", "b", "c", "d", "e")
        val original = setOf("a")
        assertEquals(setOf("a", "b", "c", "d"), databaseManagerSelectionRange(original, rows, 1, 3, true))
        assertEquals(setOf("a", "b", "c"), databaseManagerSelectionRange(original, rows, 1, 2, true))
        assertEquals(setOf("a", "e"), databaseManagerSelectionRange(rows.toSet(), rows, 3, 1, false))
        assertEquals(original, databaseManagerSelectionRange(original, rows, -1, 4, true))
    }
    @Test fun selectedParentAndDescendantsAreTransferredOnce() {
        val nodes = listOf(row("parent", folder = true), row("child", "parent", true), row("one", "child"), row("two"))
        assertEquals(listOf("parent", "two"), databaseManagerSelectionRoots(nodes, setOf("parent", "child", "one", "two")).map { it.id })
        assertEquals(listOf("parent", "child", "one"), databaseManagerDescendants(nodes, nodes.first()).map { it.id })
        assertFalse(MdbxBrowserIndex(nodes).canMoveFolder("parent", "child"))
    }
    @Test fun staleSelectionsFailInsteadOfOperatingOnOtherRows() {
        assertThrows(IllegalArgumentException::class.java) { databaseManagerSelectionRoots(listOf(row("actual")), setOf("gone")) }
    }
}
