package takagi.ru.monica.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.keepass.KeePassNativeGroupIdentity
import takagi.ru.monica.security.SecurityManager
import java.util.UUID
import takagi.ru.monica.credentialexchange.ImportDestination
import takagi.ru.monica.credentialexchange.ImportDestinationKind

/** An external folder never uses a Monica category ID for a write. */
sealed interface ExternalFolderTarget {
    val databaseId: Long
    val name: String
    data class KeePass(override val databaseId: Long, val path: String, val uuid: String?, override val name: String) : ExternalFolderTarget
    data class Mdbx(override val databaseId: Long, val folderId: String, override val name: String) : ExternalFolderTarget
    data class Bitwarden(override val databaseId: Long, val folderId: String, override val name: String) : ExternalFolderTarget
}

/** Uses the same mutation boundaries as the native database managers. */
internal class ExternalFolderRepository(context: Context, databaseOverride: PasswordDatabase? = null) {
    private val app = context.applicationContext
    private val database by lazy { databaseOverride ?: PasswordDatabase.getDatabase(app) }
    private val security by lazy { SecurityManager(app) }
    private val keepass by lazy { KeePassCompatibilityBridge(KeePassWorkspaceRepository(app, database.localKeePassDatabaseDao(), security)) }
    private val mdbx by lazy { MdbxRepositoryFactory.create(app, database, security) }
    private val bitwarden by lazy { BitwardenRepository.getInstance(app) }
    private val manager by lazy { DatabaseManagerRepository(app, database) }

    suspend fun transferStores(source: ExternalFolderTarget): List<DatabaseManagerStore> =
        if (source is ExternalFolderTarget.Bitwarden) listOf(DatabaseManagerStore(
            ImportDestination(ImportDestinationKind.BITWARDEN, source.databaseId),
            database.bitwardenVaultDao().getVaultById(source.databaseId)?.displayName ?: "Bitwarden"))
        else manager.stores().filter { it.available }

    suspend fun transferFolders(source: ExternalFolderTarget, store: DatabaseManagerStore): List<Pair<DatabaseManagerLocation, String>> =
        withContext(Dispatchers.IO) {
            if (source is ExternalFolderTarget.Bitwarden) {
                require(store.key == ImportDestination(ImportDestinationKind.BITWARDEN, source.databaseId))
                val folders = database.bitwardenFolderDao().getFoldersByVault(source.databaseId)
                val original = folders.single { it.bitwardenFolderId == source.folderId }
                folders.filterNot { it.name == original.name || it.name.startsWith(original.name + "/") }
                    .map { DatabaseManagerLocation(store.key, it.bitwardenFolderId) to it.name }
            } else {
                val snapshot = manager.browse(DatabaseManagerLocation(store.key))
                // The manager's local-category writer accepts an external tree at the root only.
                if (store.key.kind == ImportDestinationKind.LOCAL) emptyList()
                else {
                    val sourceId = if (source.destination() == store.key) resolveNativeFolder(source) else null
                    snapshot.nodes.filter { it.type == MdbxStructureNodeType.FOLDER &&
                        (sourceId == null || snapshot.index.canMoveFolder(sourceId, it.id)) }
                        .map { node -> DatabaseManagerLocation(store.key, node.id) to
                            (snapshot.index.ancestors(snapshot.index.parent(node)).map { it.name } + node.name).joinToString(" / ") }
                }
            }
        }

    suspend fun transfer(source: ExternalFolderTarget, destination: DatabaseManagerLocation, copy: Boolean) = withContext(Dispatchers.IO) {
        if (source is ExternalFolderTarget.Bitwarden) {
            require(destination.database == source.destination()) { "Bitwarden folders can be transferred within their own account" }
            bitwarden.transferFolder(source.databaseId, source.folderId, destination.folderId, copy).getOrThrow()
        } else {
            if (!copy && source is ExternalFolderTarget.KeePass) {
                check(!keepass.isDatabaseReadOnly(source.databaseId)) { "The source database is read-only" }
            }
            val id = resolveNativeFolder(source)
            val location = DatabaseManagerLocation(source.destination())
            val snapshot = manager.browse(location)
            require(snapshot.nodes.any { it.id == id && it.type == MdbxStructureNodeType.FOLDER }) { "Folder no longer exists" }
            val report = manager.transfer(location, destination, setOf(id), copy, snapshot.revision)
            check(report.completed == 1 && report.failures.isEmpty()) {
                report.failures.joinToString("\n") { it.reason }.ifBlank { "Folder transfer did not finish" }
            }
        }
        Unit
    }

    private suspend fun resolveNativeFolder(source: ExternalFolderTarget): String = when (source) {
        is ExternalFolderTarget.Mdbx -> source.folderId
        is ExternalFolderTarget.KeePass -> {
            val snapshot = keepass.openNativeBrowser(source.databaseId).getOrThrow()
            val groups = if (source.uuid != null) snapshot.groupsByIdentity[
                KeePassNativeGroupIdentity(source.databaseId, UUID.fromString(source.uuid))].orEmpty()
            else snapshot.groupsByLegacyPath[source.path].orEmpty()
            val group = groups.singleOrNull() ?: error("Folder is missing or ambiguous; reopen the database")
            require(group.identity != snapshot.rootGroup.identity) { "The root folder cannot be transferred" }
            group.identity.groupUuid.toString()
        }
        else -> error("Unsupported folder")
    }

    suspend fun rename(target: ExternalFolderTarget, name: String) = mutate(target, name.trim().also { require(it.isNotEmpty()) })
    suspend fun delete(target: ExternalFolderTarget) = mutate(target, null)

    private suspend fun mutate(target: ExternalFolderTarget, newName: String?): Unit = withContext(Dispatchers.IO) {
        when (target) {
            is ExternalFolderTarget.Bitwarden -> {
                // Check ownership again: stale menus must not address a folder from another account.
                val folders = database.bitwardenFolderDao().getFoldersByVault(target.databaseId)
                require(folders.any { it.bitwardenFolderId == target.folderId }) { "Folder no longer exists in this vault" }
                if (newName == null) bitwarden.deleteFolder(target.databaseId, target.folderId).getOrThrow()
                else bitwarden.renameFolder(target.databaseId, target.folderId, newName).getOrThrow()
            }
            is ExternalFolderTarget.Mdbx -> {
                require(mdbx.listFolders(target.databaseId).any { it.folderId == target.folderId }) { "Folder no longer exists in this database" }
                if (newName == null) mdbx.deleteFolder(target.databaseId, target.folderId)
                else mdbx.renameFolder(target.databaseId, target.folderId, newName)
            }
            is ExternalFolderTarget.KeePass -> {
                val snapshot = keepass.openNativeBrowser(target.databaseId).getOrThrow()
                val matches = if (target.uuid != null) snapshot.groupsByIdentity[
                    KeePassNativeGroupIdentity(target.databaseId, UUID.fromString(target.uuid))
                ].orEmpty() else snapshot.groupsByLegacyPath[target.path].orEmpty()
                val group = matches.singleOrNull() ?: error("Folder is missing or ambiguous; reopen the database")
                require(group.identity != snapshot.rootGroup.identity) { "The root folder cannot be edited" }
                if (newName == null) keepass.deleteNativeGroup(target.databaseId, group.identity.groupUuid, snapshot.sourceRevision.sha256).getOrThrow()
                else keepass.renameNativeGroup(target.databaseId, group.identity.groupUuid, newName, snapshot.sourceRevision.sha256).getOrThrow()
            }
        }
        Unit
    }
}

private fun ExternalFolderTarget.destination() = ImportDestination(when (this) {
    is ExternalFolderTarget.KeePass -> ImportDestinationKind.KEEPASS
    is ExternalFolderTarget.Mdbx -> ImportDestinationKind.MDBX
    is ExternalFolderTarget.Bitwarden -> ImportDestinationKind.BITWARDEN
}, databaseId)
