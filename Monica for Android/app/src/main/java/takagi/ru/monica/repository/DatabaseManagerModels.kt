package takagi.ru.monica.repository

import takagi.ru.monica.credentialexchange.ImportDestination

internal data class DatabaseManagerLocation(val database: ImportDestination, val folderId: String? = null)
internal data class DatabaseManagerStore(val key: ImportDestination, val title: String, val available: Boolean = true)
internal data class DatabaseManagerSnapshot(val location: DatabaseManagerLocation, val title: String,
    val nodes: List<MdbxStructureNode>, val revision: String = "") {
    val index = MdbxBrowserIndex(nodes)
}
internal data class DatabaseManagerFailure(val title: String, val reason: String)
internal data class DatabaseManagerReport(val completed: Int, val failures: List<DatabaseManagerFailure>)
internal class DatabaseManagerTransferContext {
    val passwordGroups = mutableMapOf<String, String>()
    var portableSnapshot: takagi.ru.monica.transfer.DatabaseExportSnapshot? = null
    var portableSnapshotRevision: String? = null
}

/** Normalize a range without toggling rows repeatedly when the pointer crosses them twice. */
internal fun databaseManagerSelectionRange(initial: Set<String>, rows: List<String>, start: Int, end: Int,
    select: Boolean): Set<String> {
    if (start !in rows.indices || end !in rows.indices) return initial
    val range = rows.subList(minOf(start, end), maxOf(start, end) + 1).toSet()
    return if (select) initial + range else initial - range
}

internal fun databaseManagerSelectionRoots(nodes: List<MdbxStructureNode>, ids: Set<String>): List<MdbxStructureNode> {
    val index = MdbxBrowserIndex(nodes)
    require(ids.all { id -> nodes.any { it.id == id } }) { "The selection changed. Refresh and select again." }
    return nodes.filter { it.id in ids && index.ancestors(index.parent(it)).none { parent -> parent.id in ids } }
}

internal fun databaseManagerDescendants(nodes: List<MdbxStructureNode>, root: MdbxStructureNode): List<MdbxStructureNode> {
    val index = MdbxBrowserIndex(nodes)
    val result = mutableListOf<MdbxStructureNode>()
    val queue = ArrayDeque<MdbxStructureNode>(); queue.add(root)
    val seen = mutableSetOf<String>()
    while (queue.isNotEmpty()) {
        val row = queue.removeFirst()
        if (!seen.add(row.id)) continue
        result += row
        if (row.type == MdbxStructureNodeType.FOLDER) queue.addAll(index.children(row.id))
    }
    return result
}
