package takagi.ru.monica.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordHistoryManager
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.data.ItemType
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date

sealed class CategoryFilter {
    object All : CategoryFilter()
    object Starred : CategoryFilter()
    object Uncategorized : CategoryFilter()
    data class Custom(val categoryId: Long) : CategoryFilter()
    data class KeePassDatabase(val databaseId: Long) : CategoryFilter()
}

/**
 * ViewModel for password management
 */
class PasswordViewModel(
    private val repository: PasswordRepository,
    private val securityManager: SecurityManager,
    private val secureItemRepository: SecureItemRepository? = null,
    context: Context? = null
) : ViewModel() {
    
    private val passwordHistoryManager: PasswordHistoryManager? = context?.let { PasswordHistoryManager(it) }
    private val settingsManager: takagi.ru.monica.utils.SettingsManager? = context?.let { takagi.ru.monica.utils.SettingsManager(it) }
    
    // 回收站设置
    private val trashSettings = settingsManager?.settingsFlow?.map { 
        it.trashEnabled to it.trashAutoDeleteDays 
    }?.stateIn(viewModelScope, SharingStarted.Eagerly, true to 30)
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _categoryFilter = MutableStateFlow<CategoryFilter>(CategoryFilter.All)
    val categoryFilter = _categoryFilter.asStateFlow()

    val categories = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    
    val passwordEntries: StateFlow<List<PasswordEntry>> = combine(
        searchQuery,
        _categoryFilter
    ) { query, filter ->
        Pair(query, filter)
    }
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { (query, filter) ->
            if (query.isNotBlank()) {
                repository.searchPasswordEntries(query)
            } else {
                when (filter) {
                    is CategoryFilter.All -> repository.getAllPasswordEntries()
                    is CategoryFilter.Starred -> repository.getFavoritePasswordEntries()
                    is CategoryFilter.Uncategorized -> repository.getUncategorizedPasswordEntries()
                    is CategoryFilter.Custom -> repository.getPasswordEntriesByCategory(filter.categoryId)
                    is CategoryFilter.KeePassDatabase -> repository.getPasswordEntriesByKeePassDatabase(filter.databaseId)
                }
            }
        }
        .map { entries ->
            // 解密所有密码
            entries.map { entry ->
                entry.copy(password = securityManager.decryptData(entry.password))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allPasswords: StateFlow<List<PasswordEntry>> = repository.getAllPasswordEntries()
        .map { entries ->
            entries.map { entry ->
                entry.copy(password = securityManager.decryptData(entry.password))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(filter: CategoryFilter) {
        _categoryFilter.value = filter
    }

    fun addCategory(name: String, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertCategory(Category(name = name))
            onResult(id)
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            if (_categoryFilter.value is CategoryFilter.Custom && (_categoryFilter.value as CategoryFilter.Custom).categoryId == category.id) {
                _categoryFilter.value = CategoryFilter.All
            }
        }
    }
    
    fun updateCategorySortOrder(categories: List<Category>) {
        viewModelScope.launch {
            categories.forEachIndexed { index, category ->
                repository.updateCategorySortOrder(category.id, index)
            }
        }
    }

    fun movePasswordsToCategory(ids: List<Long>, categoryId: Long?) {
        viewModelScope.launch {
            repository.updateCategoryForPasswords(ids, categoryId)
        }
    }
    
    fun authenticate(password: String): Boolean {
        val isValid = securityManager.verifyMasterPassword(password)
        _isAuthenticated.value = isValid
        return isValid
    }
    
    fun setMasterPassword(password: String) {
        securityManager.setMasterPassword(password)
        _isAuthenticated.value = true
    }
    
    fun isMasterPasswordSet(): Boolean {
        return securityManager.isMasterPasswordSet()
    }
    
    fun logout() {
        _isAuthenticated.value = false
    }
    
    fun addPasswordEntry(entry: PasswordEntry, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertPasswordEntry(entry.copy(
                password = securityManager.encryptData(entry.password),
                createdAt = Date(),
                updatedAt = Date()
            ))
            // 记录创建操作（包含详细字段信息）
            val createDetails = mutableListOf<takagi.ru.monica.utils.FieldChange>()
            if (entry.username.isNotBlank()) {
                createDetails.add(takagi.ru.monica.utils.FieldChange("用户名", "", entry.username))
            }
            if (entry.website.isNotBlank()) {
                createDetails.add(takagi.ru.monica.utils.FieldChange("网站", "", entry.website))
            }
            if (entry.password.isNotBlank()) {
                // 记录真实密码，在UI层隐藏显示
                createDetails.add(takagi.ru.monica.utils.FieldChange("密码", "", entry.password))
            }
            if (entry.notes.isNotBlank()) {
                createDetails.add(takagi.ru.monica.utils.FieldChange("备注", "", entry.notes.take(50)))
            }
            takagi.ru.monica.utils.OperationLogger.logCreate(
                itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
                itemId = id,
                itemTitle = entry.title,
                details = createDetails
            )
            onResult(id)
        }
    }

    fun addSecureItem(item: SecureItem) {
        viewModelScope.launch {
            secureItemRepository?.insertItem(item)
        }
    }
    
    /**
     * 快速添加密码（从底部导航栏快速添加）
     */
    fun quickAddPassword(title: String, username: String, password: String) {
        if (title.isBlank()) return
        val entry = PasswordEntry(
            title = title,
            username = username,
            password = password,
            website = "",
            notes = "",
            isFavorite = false
        )
        addPasswordEntry(entry)
    }
    
    fun updatePasswordEntry(entry: PasswordEntry) {
        viewModelScope.launch {
            // 获取旧数据用于对比
            val oldEntry = repository.getPasswordEntryById(entry.id)
            val oldPassword = oldEntry?.let { securityManager.decryptData(it.password) } ?: ""

            repository.updatePasswordEntry(entry.copy(
                password = securityManager.encryptData(entry.password),
                updatedAt = Date()
            ))
            
            // 记录更新操作
            val changes = takagi.ru.monica.utils.OperationLogger.compareAndGetChanges(
                old = oldEntry,
                new = entry,
                fields = listOf(
                    "用户名" to { it.username },
                    "网站" to { it.website },
                    "备注" to { it.notes }
                )
            )

            // 捕获密码变化（记录真实密码，在UI层隐藏显示）
            if (oldEntry != null && oldPassword != entry.password) {
                val updatedChanges = changes.toMutableList()
                updatedChanges.add(
                    takagi.ru.monica.utils.FieldChange(
                        fieldName = "密码",
                        oldValue = oldPassword,
                        newValue = entry.password
                    )
                )
                takagi.ru.monica.utils.OperationLogger.logUpdate(
                    itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
                    itemId = entry.id,
                    itemTitle = entry.title,
                    changes = updatedChanges
                )
                return@launch
            }
            takagi.ru.monica.utils.OperationLogger.logUpdate(
                itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
                itemId = entry.id,
                itemTitle = entry.title,
                changes = changes
            )
        }
    }
    
    fun deletePasswordEntry(entry: PasswordEntry) {
        viewModelScope.launch {
            val trashEnabled = trashSettings?.value?.first ?: true
            
            if (trashEnabled) {
                // 软删除：移动到回收站
                val softDeletedEntry = entry.copy(
                    isDeleted = true,
                    deletedAt = Date(),
                    updatedAt = Date()
                )
                repository.updatePasswordEntry(softDeletedEntry)
                // 记录移入回收站操作
                takagi.ru.monica.utils.OperationLogger.logDelete(
                    itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
                    itemId = entry.id,
                    itemTitle = entry.title,
                    detail = "移入回收站"
                )
            } else {
                // 直接永久删除
                repository.deletePasswordEntry(entry)
                // 记录删除操作
                takagi.ru.monica.utils.OperationLogger.logDelete(
                    itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
                    itemId = entry.id,
                    itemTitle = entry.title
                )
            }
        }
    }
    
    fun toggleFavorite(id: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(id, isFavorite)
        }
    }
    
    fun toggleGroupCover(id: Long, website: String, isGroupCover: Boolean) {
        viewModelScope.launch {
            if (isGroupCover) {
                // 设置为封面,会自动清除该分组的其他封面
                repository.setGroupCover(id, website)
            } else {
                // 取消封面
                repository.updateGroupCoverStatus(id, false)
            }
        }
    }
    
    fun updateSortOrders(items: List<Pair<Long, Int>>) {
        viewModelScope.launch {
            repository.updateSortOrders(items)
        }
    }
    
    suspend fun getPasswordEntryById(id: Long): PasswordEntry? {
        return repository.getPasswordEntryById(id)?.let { entry ->
            entry.copy(password = securityManager.decryptData(entry.password))
        }
    }

    /**
     * Get linked TOTP data for a password entry
     */
    fun getLinkedTotpFlow(passwordId: Long): Flow<TotpData?> {
        return secureItemRepository?.getItemsByType(ItemType.TOTP)
            ?.map { items ->
                items.mapNotNull { item ->
                    try {
                        Json.decodeFromString<TotpData>(item.itemData)
                    } catch (e: Exception) {
                        null
                    }
                }.find { it.boundPasswordId == passwordId }
            } ?: flowOf(null)
    }
    
    /**
     * Verify master password
     */
    fun verifyMasterPassword(password: String): Boolean {
        return securityManager.verifyMasterPassword(password)
    }
    
    /**
     * Reset all application data - used for forgot password scenario
     * Supports selective clearing of different data categories
     */
    fun resetAllData(
        clearPasswords: Boolean = true,
        clearTotp: Boolean = true,
        clearDocuments: Boolean = true,
        clearBankCards: Boolean = true,
        clearGeneratorHistory: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                // Clear selected data categories
                if (clearPasswords) {
                    repository.deleteAllPasswordEntries()
                }
                
                if (secureItemRepository != null) {
                    if (clearTotp) {
                        secureItemRepository.deleteAllTotpEntries()
                    }
                    
                    if (clearDocuments) {
                        secureItemRepository.deleteAllDocuments()
                    }
                    
                    if (clearBankCards) {
                        secureItemRepository.deleteAllBankCards()
                    }
                }
                
                if (clearGeneratorHistory && passwordHistoryManager != null) {
                    passwordHistoryManager.clearHistory()
                }
                
                // Always clear security data when resetting
                securityManager.clearSecurityData()
                
                // Reset authentication state
                _isAuthenticated.value = false
            } catch (e: Exception) {
                // Handle error - log it
                Log.e("PasswordViewModel", "Error clearing data", e)
            }
        }
    }
    
    /**
     * Change master password
     * 修改主密码并重新加密所有数据
     */
    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            // 1. 验证当前密码
            if (!securityManager.verifyMasterPassword(currentPassword)) {
                // TODO: 通知UI密码错误
                return@launch
            }
            
            // 2. 获取所有加密数据
            val allPasswords = repository.getAllPasswordEntries().first()
            
            // 3. 使用当前密码解密所有数据
            val decryptedPasswords = allPasswords.map { entry ->
                entry.copy(password = securityManager.decryptData(entry.password))
            }
            
            // 4. 设置新密码
            securityManager.setMasterPassword(newPassword)
            
            // 5. 使用新密码重新加密所有数据
            decryptedPasswords.forEach { entry ->
                repository.updatePasswordEntry(entry.copy(
                    password = securityManager.encryptData(entry.password),
                    updatedAt = Date()
                ))
            }
            
            // 6. 重新认证
            _isAuthenticated.value = true
        }
    }
    
    /**
     * Save security questions
     * 保存密保问题
     */
    fun saveSecurityQuestions(questions: List<Pair<String, String>>) {
        viewModelScope.launch {
            // TODO: 保存到DataStore或数据库
            // 答案应该加密存储
            questions.forEach { (question, answer) ->
                val encryptedAnswer = securityManager.encryptData(answer.lowercase())
                // 存储 question 和 encryptedAnswer
            }
        }
    }

    fun updateAppAssociationByWebsite(website: String, packageName: String, appName: String) {
        viewModelScope.launch {
            repository.updateAppAssociationByWebsite(website, packageName, appName)
        }
    }

    fun updateAppAssociationByTitle(title: String, packageName: String, appName: String) {
        viewModelScope.launch {
            repository.updateAppAssociationByTitle(title, packageName, appName)
        }
    }

    // ==========================================
    // Grouping Helpers
    // ==========================================

    private fun getPasswordInfoKey(entry: PasswordEntry): String {
        return "${entry.title}|${entry.website}|${entry.username}|${entry.notes}|${entry.appPackageName}|${entry.appName}"
    }

    private fun isSameGroup(entry1: PasswordEntry, entry2: PasswordEntry): Boolean {
        return getPasswordInfoKey(entry1) == getPasswordInfoKey(entry2)
    }

    /**
     * Save a group of passwords.
     * Updates existing entries to preserve IDs (and TOTP links), creates new ones if needed,
     * and deletes removed ones.
     * The callback receives the ID of the first password (for TOTP binding).
     */
    fun saveGroupedPasswords(
        originalIds: List<Long>,
        commonEntry: PasswordEntry, // Contains common info and ONE password (ignored)
        passwords: List<String>,
        onComplete: (firstPasswordId: Long?) -> Unit = {}
    ) {
        viewModelScope.launch {
            var firstId: Long? = null
            
            // 1. Process each password
            passwords.forEachIndexed { index, password ->
                if (index < originalIds.size) {
                    // Update existing
                    val id = originalIds[index]
                    if (index == 0) firstId = id
                    val updatedEntry = commonEntry.copy(
                        id = id,
                        password = password
                    )
                    updatePasswordEntry(updatedEntry)
                } else {
                    // Create new
                    val newEntry = commonEntry.copy(
                        id = 0, // Reset ID for new entry
                        password = password
                    )
                    val newId = repository.insertPasswordEntry(newEntry.copy(
                        password = securityManager.encryptData(newEntry.password),
                        createdAt = java.util.Date(),
                        updatedAt = java.util.Date()
                    ))
                    if (index == 0) firstId = newId
                    
                    // 记录创建操作
                    takagi.ru.monica.utils.OperationLogger.logCreate(
                        itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
                        itemId = newId,
                        itemTitle = newEntry.title
                    )
                }
            }

            // 2. Delete leftovers
            if (originalIds.size > passwords.size) {
                val toDelete = originalIds.subList(passwords.size, originalIds.size)
                toDelete.forEach { id ->
                    repository.getPasswordEntryById(id)?.let { deletePasswordEntry(it) }
                }
            }
            
            onComplete(firstId)
        }
    }
}