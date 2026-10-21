package takagi.ru.monica.bitwarden.service

/** Bitwarden has flat folder IDs; slash-separated names provide the visible hierarchy. */
internal fun planBitwardenFolderTransfer(
    folders: Map<String, String>, sourceId: String, parentId: String?, copy: Boolean, copyLabel: String,
): Map<String, String> {
    val source = requireNotNull(folders[sourceId]) { "Source folder missing" }
    val parent = parentId?.let { requireNotNull(folders[it]) { "Destination folder missing" } }
    require(parent == null || parent != source && !parent.startsWith("$source/")) {
        "A folder cannot be transferred into itself or its descendants"
    }
    val base = listOfNotNull(parent, source.substringAfterLast('/')).joinToString("/")
    fun exists(name: String) = folders.values.any { it == name || it.startsWith("$name/") }
    var destination = base
    if (copy && exists(destination)) {
        var suffix = 1
        do { destination = "$base ($copyLabel${if (suffix == 1) "" else " $suffix"})"; suffix++ } while (exists(destination))
    }
    require(destination != source && !exists(destination)) { "A folder already exists at this location" }
    val tree = folders.filterValues { it == source || it.startsWith("$source/") }.toList().sortedBy { it.second.length }
    require(tree.map { it.second }.distinct().size == tree.size) { "Duplicate folder paths must be renamed before transfer" }
    return tree.associate { (id, name) -> id to destination + name.removePrefix(source) }
}
