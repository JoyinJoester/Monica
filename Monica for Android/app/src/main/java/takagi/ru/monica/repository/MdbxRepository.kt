package takagi.ru.monica.repository

import takagi.ru.monica.attachments.model.Attachment
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem

/**
 * Boundary for user-visible MDBX operations.
 *
 * Implementations must commit the local .mdbx working copy and then publish
 * that working copy to the configured SAF/WebDAV source before reporting
 * success. This keeps a second client able to see new MDBX items with its
 * next manual sync instead of requiring a manual sync on the writer first.
 *
 * Keep schema/version/sync/conflict/snapshot logic behind this facade so UI
 * and ViewModel code do not grow their own MDBX table behavior.
 */
interface MdbxRepository {
    suspend fun createFolder(
        databaseId: Long,
        name: String,
        parentFolderId: String? = "root"
    ): MdbxStoredFolderEntry

    suspend fun listFolders(databaseId: Long): List<MdbxStoredFolderEntry>

    suspend fun upsertPassword(entry: PasswordEntry)
    suspend fun deletePassword(entry: PasswordEntry)
    suspend fun upsertPasswords(entries: List<PasswordEntry>) {
        entries.forEach { upsertPassword(it) }
    }
    suspend fun deletePasswords(entries: List<PasswordEntry>) {
        entries.forEach { deletePassword(it) }
    }

    suspend fun upsertSecureItem(item: SecureItem)
    suspend fun deleteSecureItem(item: SecureItem)
    suspend fun upsertSecureItems(items: List<SecureItem>) {
        items.forEach { upsertSecureItem(it) }
    }
    suspend fun deleteSecureItems(items: List<SecureItem>) {
        items.forEach { deleteSecureItem(it) }
    }

    suspend fun upsertPasskey(passkey: PasskeyEntry)
    suspend fun deletePasskey(passkey: PasskeyEntry)
    suspend fun upsertPasskeys(passkeys: List<PasskeyEntry>) {
        passkeys.forEach { upsertPasskey(it) }
    }
    suspend fun deletePasskeys(passkeys: List<PasskeyEntry>) {
        passkeys.forEach { deletePasskey(it) }
    }

    suspend fun getVaultDiagnostics(databaseId: Long): MdbxVaultDiagnostics
    suspend fun getPendingSyncCount(databaseId: Long): Int

    suspend fun setProjectTags(databaseId: Long, projectId: String, tags: List<String>)
    suspend fun listProjectTags(databaseId: Long, projectId: String): List<String>
    suspend fun listAllProjectTags(databaseId: Long): List<MdbxProjectTagSummary>
    suspend fun searchProjects(
        databaseId: Long,
        query: String,
        requiredTags: List<String> = emptyList()
    ): List<MdbxProjectSearchResult>

    suspend fun listDeltaHistory(databaseId: Long): List<MdbxDeltaSummary>
    suspend fun listCommitDiff(databaseId: Long, commitId: String): List<MdbxCommitDiff>
    suspend fun revertCommit(databaseId: Long, commitId: String): Int

    suspend fun listSnapshots(databaseId: Long): List<MdbxSnapshotSummary>
    suspend fun createSnapshot(
        databaseId: Long,
        name: String,
        fullSnapshot: Boolean = false,
        autoPrune: Boolean = false
    ): MdbxSnapshotSummary
    suspend fun deleteSnapshot(databaseId: Long, snapshotId: String)
    suspend fun revertToSnapshot(databaseId: Long, snapshotId: String): Int
    suspend fun getSnapshotStructurePreview(
        databaseId: Long,
        snapshotId: String
    ): MdbxStructurePreview

    suspend fun exportSyncBundle(
        databaseId: Long,
        baseCommitId: String? = null
    ): MdbxSyncBundle
    suspend fun importSyncBundle(databaseId: Long, bundle: MdbxSyncBundle): MdbxApplyResult
    suspend fun flushPendingWorkingCopy(databaseId: Long)
    suspend fun flushWorkingCopy(databaseId: Long)

    suspend fun listConflicts(databaseId: Long): List<MdbxConflictSummary>
    suspend fun resolveConflict(
        databaseId: Long,
        conflictId: String,
        resolution: MdbxConflictResolution
    )

    suspend fun upsertAttachment(
        databaseId: Long,
        parentEntryId: String,
        attachment: Attachment
    )
    suspend fun upsertExternalAttachmentRef(
        databaseId: Long,
        parentEntryId: String,
        attachment: Attachment,
        externalUri: String
    )
    suspend fun deleteAttachment(
        databaseId: Long,
        parentEntryId: String,
        attachment: Attachment
    )
}
