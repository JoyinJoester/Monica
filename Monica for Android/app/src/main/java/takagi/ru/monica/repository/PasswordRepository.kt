package takagi.ru.monica.repository

import takagi.ru.monica.data.Category
import takagi.ru.monica.data.CategoryDao
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordEntryDao
import kotlinx.coroutines.flow.Flow

/**
 * Repository for password entries
 */
class PasswordRepository(
    private val passwordEntryDao: PasswordEntryDao,
    private val categoryDao: CategoryDao? = null
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

    suspend fun updateCategoryForPasswords(ids: List<Long>, categoryId: Long?) {
        passwordEntryDao.updateCategoryForPasswords(ids, categoryId)
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
}