package takagi.ru.monica.repository

import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.utils.KeePassRestoreTarget

class KeePassCompatibilityBridge(
    private val workspaceRepository: KeePassWorkspaceRepository
) {

    suspend fun upsertLegacyPasswordEntries(
        databaseId: Long,
        entries: List<PasswordEntry>,
        resolvePassword: (PasswordEntry) -> String,
        forceSyncWrite: Boolean = false
    ) = workspaceRepository.addOrUpdatePasswordEntries(
        databaseId = databaseId,
        entries = entries,
        resolvePassword = resolvePassword,
        forceSyncWrite = forceSyncWrite
    )

    suspend fun upsertLegacySecureItems(
        databaseId: Long,
        items: List<SecureItem>,
        forceSyncWrite: Boolean = false
    ) = workspaceRepository.addOrUpdateSecureItems(
        databaseId = databaseId,
        items = items,
        forceSyncWrite = forceSyncWrite
    )

    suspend fun loadLegacyWorkspace(databaseId: Long) = workspaceRepository.loadWorkspace(databaseId)

    suspend fun readLegacyPasswordEntries(databaseId: Long) = workspaceRepository.readPasswordEntries(databaseId)

    suspend fun readLegacySecureItems(
        databaseId: Long,
        allowedTypes: Set<ItemType>? = null
    ) = workspaceRepository.readSecureItems(databaseId, allowedTypes)

    suspend fun listLegacyGroups(
        databaseId: Long,
        includeRecycleBin: Boolean = false
    ) = workspaceRepository.listGroups(databaseId, includeRecycleBin)

    suspend fun createLegacyGroup(
        databaseId: Long,
        groupName: String,
        parentPath: String? = null
    ) = workspaceRepository.createGroup(databaseId, groupName, parentPath)

    suspend fun renameLegacyGroup(
        databaseId: Long,
        groupPath: String,
        newName: String
    ) = workspaceRepository.renameGroup(databaseId, groupPath, newName)

    suspend fun deleteLegacyGroup(
        databaseId: Long,
        groupPath: String
    ) = workspaceRepository.deleteGroup(databaseId, groupPath)

    suspend fun updateLegacyPasswordEntry(
        databaseId: Long,
        entry: PasswordEntry,
        resolvePassword: (PasswordEntry) -> String
    ) = workspaceRepository.updatePasswordEntry(databaseId, entry, resolvePassword)

    suspend fun deleteLegacyPasswordEntries(
        databaseId: Long,
        entries: List<PasswordEntry>
    ) = workspaceRepository.deletePasswordEntries(databaseId, entries)

    suspend fun moveLegacyPasswordEntriesToRecycleBin(
        databaseId: Long,
        entries: List<PasswordEntry>,
        forceSyncWrite: Boolean = false
    ) = workspaceRepository.movePasswordEntriesToRecycleBin(databaseId, entries, forceSyncWrite)

    suspend fun resolveLegacyRestoreGroupPathForPassword(
        databaseId: Long,
        entry: PasswordEntry
    ) = workspaceRepository.resolveRestoreGroupPathForPassword(databaseId, entry)

    suspend fun resolveLegacyRestoreTargetForPassword(
        databaseId: Long,
        entry: PasswordEntry
    ): Result<KeePassRestoreTarget> = workspaceRepository.resolveRestoreTargetForPassword(databaseId, entry)

    suspend fun updateLegacySecureItem(
        databaseId: Long,
        item: SecureItem
    ) = workspaceRepository.updateSecureItem(databaseId, item)

    suspend fun deleteLegacySecureItems(
        databaseId: Long,
        items: List<SecureItem>
    ) = workspaceRepository.deleteSecureItems(databaseId, items)

    suspend fun moveLegacySecureItemsToRecycleBin(
        databaseId: Long,
        items: List<SecureItem>,
        forceSyncWrite: Boolean = false
    ) = workspaceRepository.moveSecureItemsToRecycleBin(databaseId, items, forceSyncWrite)

    suspend fun resolveLegacyRestoreGroupPathForSecureItem(
        databaseId: Long,
        item: SecureItem
    ) = workspaceRepository.resolveRestoreGroupPathForSecureItem(databaseId, item)

    suspend fun resolveLegacyRestoreTargetForSecureItem(
        databaseId: Long,
        item: SecureItem
    ): Result<KeePassRestoreTarget> = workspaceRepository.resolveRestoreTargetForSecureItem(databaseId, item)
}