package takagi.ru.monica.utils

import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.SecureItemOwnership
import takagi.ru.monica.data.isLocalOnlyItem
import takagi.ru.monica.data.resolveOwnership

enum class BackupContentScope {
    MONICA_LOCAL_ONLY,
    ALL_OFFLINE
}

object BackupContentPolicy {
    fun shouldIncludePassword(entry: PasswordEntry, scope: BackupContentScope): Boolean {
        return when (scope) {
            BackupContentScope.ALL_OFFLINE -> true
            BackupContentScope.MONICA_LOCAL_ONLY ->
                entry.isLocalOnlyEntry() || isLikelyDetachedKeePassPassword(entry)
        }
    }

    fun shouldIncludeSecureItem(item: SecureItem, scope: BackupContentScope): Boolean {
        return when (scope) {
            BackupContentScope.ALL_OFFLINE -> true
            BackupContentScope.MONICA_LOCAL_ONLY ->
                item.isLocalOnlyItem() || isLikelyDetachedKeePassSecureItem(item)
        }
    }

    fun sanitizePasswordForMonicaBackup(entry: PasswordEntry): PasswordEntry {
        return entry.copy(
            keepassDatabaseId = null,
            keepassGroupPath = null,
            keepassEntryUuid = null,
            keepassGroupUuid = null,
            mdbxDatabaseId = null,
            mdbxFolderId = null,
            bitwardenVaultId = null,
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false
        )
    }

    fun sanitizeSecureItemForMonicaBackup(item: SecureItem): SecureItem {
        return item.copy(
            keepassDatabaseId = null,
            keepassGroupPath = null,
            keepassEntryUuid = null,
            keepassGroupUuid = null,
            mdbxDatabaseId = null,
            mdbxFolderId = null,
            bitwardenVaultId = null,
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            syncStatus = "NONE"
        )
    }

    fun isLikelyDetachedKeePassPassword(entry: PasswordEntry): Boolean {
        if (entry.keepassDatabaseId == null) return false
        if (entry.bitwardenVaultId != null || !entry.bitwardenCipherId.isNullOrBlank()) return false
        val hasLocalCategory = entry.categoryId != null
        val missingKeePassIdentity =
            entry.keepassEntryUuid.isNullOrBlank() && entry.keepassGroupUuid.isNullOrBlank()
        return hasLocalCategory || missingKeePassIdentity
    }

    fun isLikelyDetachedKeePassSecureItem(item: SecureItem): Boolean {
        if (item.resolveOwnership() !is SecureItemOwnership.KeePass) return false
        val hasLocalCategory = item.categoryId != null
        val missingKeePassIdentity =
            item.keepassEntryUuid.isNullOrBlank() && item.keepassGroupUuid.isNullOrBlank()
        return hasLocalCategory || missingKeePassIdentity
    }
}
