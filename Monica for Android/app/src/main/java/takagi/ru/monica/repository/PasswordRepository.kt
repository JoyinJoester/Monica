package takagi.ru.monica.repository

import kotlinx.coroutines.flow.Flow
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.CategoryDao
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordEntryDao
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenFolderDao
import java.util.Locale

/**
 * Repository for password entries
 */
class PasswordRepository(
    private val passwordEntryDao: PasswordEntryDao,
    private val categoryDao: CategoryDao? = null,
    private val bitwardenFolderDao: BitwardenFolderDao? = null
) {
    
    fun getAllPasswordEntries(): Flow<List<PasswordEntry>> {
        return passwordEntryDao.getAllPasswordEntries()
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

    fun getPasswordEntriesByBitwardenFolder(folderId: String): Flow<List<PasswordEntry>> {
        return passwordEntryDao.getByBitwardenFolderIdFlow(folderId)
    }
    
    fun getBitwardenFoldersByVaultId(vaultId: Long): Flow<List<BitwardenFolder>> {
        return bitwardenFolderDao?.getFoldersByVaultFlow(vaultId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    fun getFavoritePasswordEntries(): Flow<List<PasswordEntry>> {
        return passwordEntryDao.getFavoritePasswordEntries()
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
        // First remove category from passwords
        passwordEntryDao.removeCategoryFromPasswords(category.id)
        // Then delete category
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
    
    fun searchPasswordEntries(query: String): Flow<List<PasswordEntry>> {
        return passwordEntryDao.searchPasswordEntries(query)
    }
    
    suspend fun getPasswordEntryById(id: Long): PasswordEntry? {
        return passwordEntryDao.getPasswordEntryById(id)
    }
    
    suspend fun getPasswordsByIds(ids: List<Long>): List<PasswordEntry> {
        return passwordEntryDao.getPasswordsByIds(ids)
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

    suspend fun getDuplicateEntryInKeePass(databaseId: Long, title: String, username: String, website: String): PasswordEntry? {
        return passwordEntryDao.findDuplicateEntryInKeePass(databaseId, title, username, website)
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
        passwordEntryDao.updateAuthenticatorKey(id, authenticatorKey)
    }

    /**
     * 更新绑定的通行密钥元数据
     */
    suspend fun updatePasskeyBindings(id: Long, passkeyBindings: String) {
        passwordEntryDao.updatePasskeyBindings(id, passkeyBindings)
    }
}
