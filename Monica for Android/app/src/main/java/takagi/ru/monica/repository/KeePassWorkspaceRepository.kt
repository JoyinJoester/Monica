package takagi.ru.monica.repository

import android.content.Context
import android.net.Uri
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.LocalKeePassDatabaseDao
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.KeePassDatabaseDiagnostics
import takagi.ru.monica.utils.KeePassEntryData
import takagi.ru.monica.utils.KeePassGroupInfo
import takagi.ru.monica.utils.KeePassKdbxService
import takagi.ru.monica.utils.KeePassRestoreTarget
import takagi.ru.monica.utils.KeePassSecureItemData

data class KeePassWorkspaceSnapshot(
    val passwords: List<KeePassEntryData>,
    val secureItems: List<KeePassSecureItemData>,
    val groups: List<KeePassGroupInfo>
)

class KeePassWorkspaceRepository(
    private val service: KeePassKdbxService
) {

    constructor(
        context: Context,
        dao: LocalKeePassDatabaseDao,
        securityManager: SecurityManager
    ) : this(KeePassKdbxService(context, dao, securityManager))

    suspend fun loadWorkspace(
        databaseId: Long,
        includeRecycleBinGroups: Boolean = false,
        allowedSecureItemTypes: Set<ItemType>? = null
    ): Result<KeePassWorkspaceSnapshot> {
        val passwords = service.readPasswordEntries(databaseId).getOrElse {
            return Result.failure(it)
        }
        val secureItems = service.readSecureItems(databaseId, allowedSecureItemTypes).getOrElse {
            return Result.failure(it)
        }
        val groups = service.listGroups(databaseId, includeRecycleBinGroups).getOrElse {
            return Result.failure(it)
        }
        return Result.success(
            KeePassWorkspaceSnapshot(
                passwords = passwords,
                secureItems = secureItems,
                groups = groups
            )
        )
    }

    suspend fun listGroups(
        databaseId: Long,
        includeRecycleBin: Boolean = false
    ): Result<List<KeePassGroupInfo>> {
        return service.listGroups(databaseId, includeRecycleBin)
    }

    suspend fun readPasswordEntries(databaseId: Long): Result<List<KeePassEntryData>> {
        return service.readPasswordEntries(databaseId)
    }

    suspend fun readSecureItems(
        databaseId: Long,
        allowedTypes: Set<ItemType>? = null
    ): Result<List<KeePassSecureItemData>> {
        return service.readSecureItems(databaseId, allowedTypes)
    }

    suspend fun readPasskeyEntries(databaseId: Long): Result<List<PasskeyEntry>> {
        return service.readPasskeyEntries(databaseId)
    }

    suspend fun verifyDatabase(databaseId: Long): Result<Int> {
        return service.verifyDatabase(databaseId)
    }

    suspend fun inspectDatabase(
        databaseId: Long,
        passwordOverride: String? = null,
        keyFileUriOverride: Uri? = null
    ): Result<KeePassDatabaseDiagnostics> {
        return service.inspectDatabase(
            databaseId = databaseId,
            passwordOverride = passwordOverride,
            keyFileUriOverride = keyFileUriOverride
        )
    }

    suspend fun inspectExternalDatabase(
        fileUri: Uri,
        password: String,
        keyFileUri: Uri? = null
    ): Result<KeePassDatabaseDiagnostics> {
        return service.inspectExternalDatabase(
            fileUri = fileUri,
            password = password,
            keyFileUri = keyFileUri
        )
    }

    suspend fun createGroup(
        databaseId: Long,
        groupName: String,
        parentPath: String? = null
    ): Result<KeePassGroupInfo> {
        return service.createGroup(databaseId, groupName, parentPath)
    }

    suspend fun renameGroup(
        databaseId: Long,
        groupPath: String,
        newName: String
    ): Result<KeePassGroupInfo> {
        return service.renameGroup(databaseId, groupPath, newName)
    }

    suspend fun deleteGroup(
        databaseId: Long,
        groupPath: String
    ): Result<Unit> {
        return service.deleteGroup(databaseId, groupPath)
    }

    suspend fun moveGroup(
        sourceDatabaseId: Long,
        groupPath: String,
        targetDatabaseId: Long,
        targetParentPath: String? = null
    ): Result<KeePassGroupInfo> {
        return service.moveGroup(
            sourceDatabaseId = sourceDatabaseId,
            groupPath = groupPath,
            targetDatabaseId = targetDatabaseId,
            targetParentPath = targetParentPath
        )
    }

    suspend fun addOrUpdatePasswordEntries(
        databaseId: Long,
        entries: List<PasswordEntry>,
        resolvePassword: (PasswordEntry) -> String,
        forceSyncWrite: Boolean = false
    ): Result<Int> {
        return service.addOrUpdatePasswordEntries(
            databaseId = databaseId,
            entries = entries,
            resolvePassword = resolvePassword,
            forceSyncWrite = forceSyncWrite
        )
    }

    suspend fun updatePasswordEntry(
        databaseId: Long,
        entry: PasswordEntry,
        resolvePassword: (PasswordEntry) -> String
    ): Result<Unit> {
        return service.updatePasswordEntry(
            databaseId = databaseId,
            entry = entry,
            resolvePassword = resolvePassword
        )
    }

    suspend fun deletePasswordEntries(
        databaseId: Long,
        entries: List<PasswordEntry>
    ): Result<Int> {
        return service.deletePasswordEntries(databaseId, entries)
    }

    suspend fun movePasswordEntriesToRecycleBin(
        databaseId: Long,
        entries: List<PasswordEntry>,
        forceSyncWrite: Boolean = false
    ): Result<Int> {
        return service.movePasswordEntriesToRecycleBin(
            databaseId = databaseId,
            entries = entries,
            forceSyncWrite = forceSyncWrite
        )
    }

    suspend fun resolveRestoreGroupPathForPassword(
        databaseId: Long,
        entry: PasswordEntry
    ): Result<String?> {
        return service.resolveRestoreGroupPathForPassword(
            databaseId = databaseId,
            target = entry
        )
    }

    suspend fun resolveRestoreTargetForPassword(
        databaseId: Long,
        entry: PasswordEntry
    ): Result<KeePassRestoreTarget> {
        return service.resolveRestoreTargetForPassword(
            databaseId = databaseId,
            target = entry
        )
    }

    suspend fun addOrUpdateSecureItems(
        databaseId: Long,
        items: List<SecureItem>,
        forceSyncWrite: Boolean = false
    ): Result<Int> {
        return service.addOrUpdateSecureItems(
            databaseId = databaseId,
            items = items,
            forceSyncWrite = forceSyncWrite
        )
    }

    suspend fun addOrUpdatePasskeys(
        databaseId: Long,
        passkeys: List<PasskeyEntry>
    ): Result<Int> {
        return service.addOrUpdatePasskeys(
            databaseId = databaseId,
            passkeys = passkeys
        )
    }

    suspend fun updateSecureItem(
        databaseId: Long,
        item: SecureItem
    ): Result<Unit> {
        return service.updateSecureItem(databaseId, item)
    }

    suspend fun updatePasskey(
        databaseId: Long,
        passkey: PasskeyEntry
    ): Result<Unit> {
        return service.updatePasskey(databaseId, passkey)
    }

    suspend fun deleteSecureItems(
        databaseId: Long,
        items: List<SecureItem>
    ): Result<Int> {
        return service.deleteSecureItems(databaseId, items)
    }

    suspend fun deletePasskeys(
        databaseId: Long,
        passkeys: List<PasskeyEntry>
    ): Result<Int> {
        return service.deletePasskeys(databaseId, passkeys)
    }

    suspend fun moveSecureItemsToRecycleBin(
        databaseId: Long,
        items: List<SecureItem>,
        forceSyncWrite: Boolean = false
    ): Result<Int> {
        return service.moveSecureItemsToRecycleBin(
            databaseId = databaseId,
            items = items,
            forceSyncWrite = forceSyncWrite
        )
    }

    suspend fun resolveRestoreGroupPathForSecureItem(
        databaseId: Long,
        item: SecureItem
    ): Result<String?> {
        return service.resolveRestoreGroupPathForSecureItem(
            databaseId = databaseId,
            target = item
        )
    }

    suspend fun resolveRestoreTargetForSecureItem(
        databaseId: Long,
        item: SecureItem
    ): Result<KeePassRestoreTarget> {
        return service.resolveRestoreTargetForSecureItem(
            databaseId = databaseId,
            target = item
        )
    }
}
