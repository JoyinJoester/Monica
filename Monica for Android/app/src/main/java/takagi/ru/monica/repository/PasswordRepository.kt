package takagi.ru.monica.repository

import kotlinx.coroutines.flow.Flow
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.CategoryDao
import takagi.ru.monica.data.PasskeyDao
import takagi.ru.monica.data.PasswordArchiveSyncMeta
import takagi.ru.monica.data.PasswordArchiveSyncMetaDao
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordEntryDao
import takagi.ru.monica.data.PasswordHistoryDao
import takagi.ru.monica.data.PasswordHistoryEntry
import takagi.ru.monica.data.SecureItemDao
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenFolderDao
import takagi.ru.monica.data.bitwarden.BitwardenSyncRawEntryRecord
import takagi.ru.monica.data.bitwarden.BitwardenSyncRawEntryRecordDao
import java.util.Date
import java.util.Locale

/**
 * Repository for password entries
 */
class PasswordRepository(
    private val passwordEntryDao: PasswordEntryDao,
    private val categoryDao: CategoryDao? = null,
    private val bitwardenFolderDao: BitwardenFolderDao? = null,
    private val secureItemDao: SecureItemDao? = null,
    private val passkeyDao: PasskeyDao? = null,
    private val passwordArchiveSyncMetaDao: PasswordArchiveSyncMetaDao? = null,
    private val passwordHistoryDao: PasswordHistoryDao? = null,
    private val bitwardenSyncRawEntryRecordDao: BitwardenSyncRawEntryRecordDao? = null
) {
    
    fun getAllPasswordEntries(): Flow<List<PasswordEntry>> {
        // Keep semantic behavior the same but avoid the legacy generated callable index path.
        return passwordEntryDao.getActiveEntries()
    }
    
    fun getPasswordEntriesByCategory(categoryId: Long): Flow<List<PasswordEntry>> {
        return passwordEntryDao.getPasswordEntriesByCategory(categoryId)
    }

    fun getUncategorizedPasswordEntries(): Flow<List<PasswordEntry>> {
        return passwordEntryDao.getUncategorizedPasswordEntries()
    }

    fun getPasswordEntriesByKeePassDatabase(databaseId: Long): Flow<List<PasswordEntry>> {
        return passwordEntryDao.getPasswordEntriesByKeePassDatabase(databaseId)
    }

    fun getPasswordEntriesByKeePassGroup(databaseId: Long, groupPath: String): Flow<List<PasswordEntry>> {
        return passwordEntryDao.getPasswordEntriesByKeePassGroup(databaseId, groupPath)
    }
    
    fun getPasswordEntriesByBitwardenVault(vaultId: Long): Flow<List<PasswordEntry>> {
        return passwordEntryDao.getByBitwardenVaultIdFlow(vaultId)
    }

    fun getPasswordEntriesByBitwardenFolder(vaultId: Long, folderId: String): Flow<List<PasswordEntry>> {
        return passwordEntryDao.getByBitwardenFolderIdFlow(vaultId, folderId)
    }
    
    fun getBitwardenFoldersByVaultId(vaultId: Long): Flow<List<BitwardenFolder>> {
        return bitwardenFolderDao?.getFoldersByVaultFlow(vaultId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    fun getFavoritePasswordEntries(): Flow<List<PasswordEntry>> {
        return passwordEntryDao.getFavoritePasswordEntries()
    }

    fun getArchivedEntries(): Flow<List<PasswordEntry>> {
        return passwordEntryDao.getArchivedEntries()
    }

    // Category operations
    fun getAllCategories(): Flow<List<Category>> {
        return categoryDao?.getAllCategories() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    suspend fun insertCategory(category: Category): Long {
        return categoryDao?.insert(category) ?: -1
    }

    suspend fun updateCategory(category: Category) {
        categoryDao?.update(category)
    }

    suspend fun deleteCategory(category: Category) {
        // Clear category references across all local data types first.
        passwordEntryDao.removeCategoryFromPasswords(category.id)
        secureItemDao?.removeCategoryFromItems(category.id)
        passkeyDao?.removeCategoryFromPasskeys(category.id)
        categoryDao?.delete(category)
    }
    
    suspend fun updateCategorySortOrder(id: Long, sortOrder: Int) {
        categoryDao?.updateSortOrder(id, sortOrder)
    }

    suspend fun getCategoryById(id: Long): Category? {
        return categoryDao?.getCategoryById(id)
    }

    suspend fun updateCategoryForPasswords(ids: List<Long>, categoryId: Long?) {
        passwordEntryDao.updateCategoryForPasswords(ids, categoryId)
    }

    suspend fun updateBoundNoteId(id: Long, noteId: Long?) {
        passwordEntryDao.updateBoundNoteId(id, noteId)
    }

    suspend fun clearBoundNoteReferences(noteId: Long) {
        passwordEntryDao.clearBoundNoteReferences(noteId)
    }

    suspend fun bindPasswordsToBitwardenFolder(ids: List<Long>, vaultId: Long, folderId: String) {
        passwordEntryDao.bindPasswordsToBitwardenFolder(ids, vaultId, folderId)
    }

    suspend fun clearPendingBitwardenBinding(ids: List<Long>) {
        passwordEntryDao.clearPendingBitwardenBinding(ids)
    }

    suspend fun bindCategoryToBitwarden(categoryId: Long, vaultId: Long, folderId: String) {
        passwordEntryDao.bindCategoryToBitwarden(categoryId, vaultId, folderId)
    }
    
    suspend fun updateKeePassDatabaseForPasswords(ids: List<Long>, databaseId: Long?) {
        passwordEntryDao.updateKeePassDatabaseForPasswords(ids, databaseId)
    }

    suspend fun updateKeePassGroupForPasswords(ids: List<Long>, databaseId: Long, groupPath: String) {
        passwordEntryDao.updateKeePassGroupForPasswords(ids, databaseId, groupPath)
    }

    suspend fun clearBitwardenBindingForPasswords(ids: List<Long>) {
        passwordEntryDao.clearBitwardenBindingForPasswords(ids)
    }
    
    fun searchPasswordEntries(query: String): Flow<List<PasswordEntry>> {
        return passwordEntryDao.searchPasswordEntries(query)
    }
    
    suspend fun getPasswordEntryById(id: Long): PasswordEntry? {
        return passwordEntryDao.getPasswordEntryById(id)
    }

    suspend fun getPasswordEntryByKeePassUuid(databaseId: Long, entryUuid: String): PasswordEntry? {
        return passwordEntryDao.findByKeePassEntryUuid(databaseId, entryUuid)
    }
    
    suspend fun getPasswordsByIds(ids: List<Long>): List<PasswordEntry> {
        return passwordEntryDao.getPasswordsByIds(ids)
    }

    suspend fun getActivePasswordsByIds(ids: List<Long>): List<PasswordEntry> {
        return passwordEntryDao.getActivePasswordsByIds(ids)
    }
    
    suspend fun insertPasswordEntry(entry: PasswordEntry): Long {
        return passwordEntryDao.insertPasswordEntry(entry)
    }
    
    suspend fun updatePasswordEntry(entry: PasswordEntry) {
        passwordEntryDao.updatePasswordEntry(entry)
    }

    suspend fun updatePasswordUpdatedAt(id: Long, updatedAt: java.util.Date) {
        passwordEntryDao.updateUpdatedAt(id, updatedAt)
    }
    
    suspend fun deletePasswordEntry(entry: PasswordEntry) {
        passwordEntryDao.deletePasswordEntry(entry)
    }
    
    suspend fun deletePasswordEntryById(id: Long) {
        passwordEntryDao.deletePasswordEntryById(id)
    }

    suspend fun archivePasswordById(id: Long) {
        passwordEntryDao.archiveById(id)
    }

    suspend fun archivePasswordsByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        passwordEntryDao.archiveByIds(ids)
    }

    suspend fun unarchivePasswordById(id: Long) {
        passwordEntryDao.unarchiveById(id)
    }

    suspend fun unarchivePasswordsByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        passwordEntryDao.unarchiveByIds(ids)
    }

    suspend fun getArchiveSyncMeta(entryId: Long): PasswordArchiveSyncMeta? {
        return passwordArchiveSyncMetaDao?.getByEntryId(entryId)
    }

    suspend fun upsertArchiveSyncMeta(meta: PasswordArchiveSyncMeta) {
        passwordArchiveSyncMetaDao?.upsert(meta)
    }

    suspend fun deleteArchiveSyncMeta(entryId: Long) {
        passwordArchiveSyncMetaDao?.deleteByEntryId(entryId)
    }

    fun getPasswordHistoryByEntryId(entryId: Long): Flow<List<PasswordHistoryEntry>> {
        return passwordHistoryDao?.getHistoryByEntryId(entryId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    fun getBitwardenSyncRawRecords(
        vaultId: Long,
        cipherId: String
    ): Flow<List<BitwardenSyncRawEntryRecord>> {
        if (cipherId.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return bitwardenSyncRawEntryRecordDao?.getByCipherFlow(vaultId, cipherId)
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    suspend fun getPasswordHistoryByEntryIdSync(entryId: Long): List<PasswordHistoryEntry> {
        return passwordHistoryDao?.getHistoryByEntryIdSync(entryId) ?: emptyList()
    }

    suspend fun insertPasswordHistory(entry: PasswordHistoryEntry): Long {
        return passwordHistoryDao?.insert(entry) ?: -1L
    }

    suspend fun updatePasswordHistoryPassword(historyId: Long, password: String) {
        passwordHistoryDao?.updatePasswordById(historyId, password)
    }

    suspend fun trimPasswordHistory(entryId: Long, limit: Int) {
        passwordHistoryDao?.trimToLimit(entryId, limit)
    }

    suspend fun deletePasswordHistoryById(id: Long) {
        passwordHistoryDao?.deleteById(id)
    }

    suspend fun clearPasswordHistory(entryId: Long) {
        passwordHistoryDao?.deleteByEntryId(entryId)
    }
    
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        passwordEntryDao.updateFavoriteStatus(id, isFavorite)
    }
    
    suspend fun updateGroupCoverStatus(id: Long, isGroupCover: Boolean) {
        passwordEntryDao.updateGroupCoverStatus(id, isGroupCover)
    }
    
    suspend fun setGroupCover(id: Long, website: String) {
        passwordEntryDao.setGroupCover(id, website)
    }
    
    suspend fun updateSortOrders(items: List<Pair<Long, Int>>) {
        passwordEntryDao.updateSortOrders(items)
    }
    
    suspend fun getPasswordEntriesCount(): Int {
        return passwordEntryDao.getPasswordEntriesCount()
    }

    /**
     * 获取本地密码条目数量（排除 KeePass 和 Bitwarden 的数据）
     */
    suspend fun getLocalEntriesCount(): Int {
        return passwordEntryDao.getLocalEntriesCount()
    }

    /**
     * 获取本地已删除密码条目数量（排除 KeePass 和 Bitwarden 的数据）
     */
    suspend fun getLocalDeletedEntriesCount(): Int {
        return passwordEntryDao.getLocalDeletedEntriesCount()
    }
    
    suspend fun deleteAllPasswordEntries() {
        passwordEntryDao.deleteAllPasswordEntries()
    }
    
    /**
     * 检查是否存在重复的密码条目
     */
    suspend fun isDuplicateEntry(title: String, username: String, website: String): Boolean {
        return passwordEntryDao.findDuplicateEntry(title, username, website) != null
    }

    /**
     * 获取重复的密码条目
     */
    suspend fun getDuplicateEntry(title: String, username: String, website: String): PasswordEntry? {
        return passwordEntryDao.findDuplicateEntry(title, username, website)
    }

    /**
     * 仅在 Monica 本地库范围内查重（排除 KeePass / Bitwarden）
     */
    suspend fun getLocalDuplicateEntry(title: String, username: String, website: String): PasswordEntry? {
        return passwordEntryDao.findLocalDuplicateByKey(
            title.lowercase(Locale.ROOT),
            username.lowercase(Locale.ROOT),
            website.lowercase(Locale.ROOT)
        )
    }

    suspend fun getDuplicateEntryInKeePass(
        databaseId: Long,
        title: String,
        username: String,
        website: String,
        groupPath: String?
    ): PasswordEntry? {
        return passwordEntryDao.findDuplicateEntryInKeePass(databaseId, title, username, website, groupPath)
    }

    suspend fun getPasswordEntriesByKeePassDatabaseSync(databaseId: Long): List<PasswordEntry> {
        return passwordEntryDao.getPasswordEntriesByKeePassDatabaseSync(databaseId)
    }
    
    /**
     * 按包名和用户名查询密码(自动填充保存功能)
     */
    suspend fun findByPackageAndUsername(packageName: String, username: String): PasswordEntry? {
        return passwordEntryDao.findByPackageAndUsername(packageName, username)
    }
    
    /**
     * 按网站和用户名查询密码(自动填充保存功能)
     */
    suspend fun findByDomainAndUsername(domain: String, username: String): PasswordEntry? {
        return passwordEntryDao.findByDomainAndUsername(domain, username)
    }
    
    /**
     * 按包名查询所有密码
     */
    suspend fun findByPackageName(packageName: String): List<PasswordEntry> {
        return passwordEntryDao.findByPackageName(packageName)
    }
    
    /**
     * 按网站域名查询所有密码
     */
    suspend fun findByDomain(domain: String): List<PasswordEntry> {
        return passwordEntryDao.findByDomain(domain)
    }
    
    /**
     * 检查是否存在完全相同的密码
     */
    suspend fun findExactMatch(packageName: String, username: String, encryptedPassword: String): PasswordEntry? {
        return passwordEntryDao.findExactMatch(packageName, username, encryptedPassword)
    }

    suspend fun updateAppAssociationByWebsite(website: String, packageName: String, appName: String) {
        passwordEntryDao.updateAppAssociationByWebsite(website, packageName, appName)
    }

    suspend fun updateAppAssociationByTitle(title: String, packageName: String, appName: String) {
        passwordEntryDao.updateAppAssociationByTitle(title, packageName, appName)
    }

    /**
     * 更新绑定的验证器密钥
     */
    suspend fun updateAuthenticatorKey(id: Long, authenticatorKey: String) {
        val existing = passwordEntryDao.getPasswordEntryById(id) ?: return
        val isBitwardenCipher = existing.bitwardenVaultId != null && !existing.bitwardenCipherId.isNullOrBlank()
        passwordEntryDao.updatePasswordEntry(
            existing.copy(
                authenticatorKey = authenticatorKey,
                updatedAt = Date(),
                bitwardenLocalModified = if (isBitwardenCipher) true else existing.bitwardenLocalModified
            )
        )
    }

    /**
     * 更新绑定的通行密钥元数据
     */
    suspend fun updatePasskeyBindings(id: Long, passkeyBindings: String) {
        val existing = passwordEntryDao.getPasswordEntryById(id) ?: return
        val isBitwardenCipher = existing.bitwardenVaultId != null && !existing.bitwardenCipherId.isNullOrBlank()
        passwordEntryDao.updatePasswordEntry(
            existing.copy(
                passkeyBindings = passkeyBindings,
                updatedAt = Date(),
                bitwardenLocalModified = if (isBitwardenCipher) true else existing.bitwardenLocalModified
            )
        )
    }
}
