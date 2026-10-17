package takagi.ru.monica.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.keepass.KeePassNativeGroupIdentity
import takagi.ru.monica.security.SecurityManager
import java.util.UUID

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
