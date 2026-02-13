package takagi.ru.monica.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.CustomField
import takagi.ru.monica.data.CustomFieldDraft
import takagi.ru.monica.data.LocalKeePassDatabaseDao
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordHistoryManager
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.repository.CustomFieldRepository
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.utils.KeePassEntryData
import takagi.ru.monica.utils.KeePassKdbxService
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Date
import java.net.URI
import java.util.Locale

import takagi.ru.monica.data.bitwarden.BitwardenFolder

sealed class CategoryFilter {
    object All : CategoryFilter()
    object Local : CategoryFilter() // Pure local view (Monica)
    object Starred : CategoryFilter()
    object Uncategorized : CategoryFilter()
    data class Custom(val categoryId: Long) : CategoryFilter()
    data class KeePassDatabase(val databaseId: Long) : CategoryFilter()
    data class KeePassGroupFilter(val databaseId: Long, val groupPath: String) : CategoryFilter()
    data class BitwardenVault(val vaultId: Long) : CategoryFilter()
    data class BitwardenFolderFilter(val folderId: String, val vaultId: Long) : CategoryFilter()
}

/**
 * ViewModel for password management
 */
class PasswordViewModel(
    private val repository: PasswordRepository,
    private val securityManager: SecurityManager,
    private val secureItemRepository: SecureItemRepository? = null,
    private val customFieldRepository: CustomFieldRepository? = null,
    context: Context? = null,
    private val localKeePassDatabaseDao: LocalKeePassDatabaseDao? = null
) : ViewModel() {
    
    private val passwordHistoryManager: PasswordHistoryManager? = context?.let { PasswordHistoryManager(it) }
    private val settingsManager: takagi.ru.monica.utils.SettingsManager? = context?.let { takagi.ru.monica.utils.SettingsManager(it) }
    private val keepassService = if (context != null && localKeePassDatabaseDao != null) {
        KeePassKdbxService(context.applicationContext, localKeePassDatabaseDao, securityManager)
    } else {
        null
    }
    
    // Trash settings
    private val trashSettings = settingsManager?.settingsFlow?.map { 
        it.trashEnabled to it.trashAutoDeleteDays 
    }?.stateIn(viewModelScope, SharingStarted.Eagerly, true to 30)

    // Smart Deduplication setting
    private val smartDeduplicationEnabled = settingsManager?.settingsFlow?.map { 
        it.smartDeduplicationEnabled 
    }?.stateIn(viewModelScope, SharingStarted.Eagerly, true) ?: kotlinx.coroutines.flow.MutableStateFlow(true)
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _categoryFilter = MutableStateFlow<CategoryFilter>(CategoryFilter.All)
    val categoryFilter = _categoryFilter.asStateFlow()

    val categories = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    
    fun getBitwardenFolders(vaultId: Long): Flow<List<BitwardenFolder>> {
        return repository.getBitwardenFoldersByVaultId(vaultId)
    }
    
    val passwordEntries: StateFlow<List<PasswordEntry>> = combine(
        searchQuery,
        _categoryFilter
    ) { query, filter ->
        Pair(query, filter)
    }
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { (query, filter) ->
            val baseFlow = if (query.isNotBlank()) {
                // Extended search
                repository.searchPasswordEntries(query).map { baseResults ->
                    val customFieldMatchIds = try {
                        customFieldRepository?.searchEntryIdsByFieldContent(query) ?: emptyList()
                    } catch (e: Exception) {
                        Log.w("PasswordViewModel", "Custom field search failed", e)
                        emptyList()
                    }
                    
                    if (customFieldMatchIds.isEmpty()) {
                        baseResults
                    } else {
                        val baseIds = baseResults.map { it.id }.toSet()
                        val additionalIds = customFieldMatchIds.filter { it !in baseIds }
                        
                        if (additionalIds.isEmpty()) {
                            baseResults
                        } else {
                            val additionalEntries = try {
                                repository.getPasswordsByIds(additionalIds)
                            } catch (e: Exception) {
                                Log.w("PasswordViewModel", "Failed to fetch custom field matched entries", e)
                                emptyList()
                            }
                            baseResults + additionalEntries
                        }
                    }
                }
            } else {

                when (filter) {
                    is CategoryFilter.All -> repository.getAllPasswordEntries()
                    is CategoryFilter.Local -> repository.getAllPasswordEntries().map { list ->
                        list.filter { it.keepassDatabaseId == null && it.bitwardenVaultId == null }
                    }
                    is CategoryFilter.Starred -> repository.getFavoritePasswordEntries()
                    is CategoryFilter.Uncategorized -> repository.getUncategorizedPasswordEntries()
                    is CategoryFilter.Custom -> repository.getPasswordEntriesByCategory(filter.categoryId)
                    is CategoryFilter.KeePassDatabase -> repository.getPasswordEntriesByKeePassDatabase(filter.databaseId)
                    is CategoryFilter.KeePassGroupFilter -> repository.getPasswordEntriesByKeePassGroup(filter.databaseId, filter.groupPath)
                    is CategoryFilter.BitwardenVault -> repository.getPasswordEntriesByBitwardenVault(filter.vaultId)
                    is CategoryFilter.BitwardenFolderFilter -> repository.getPasswordEntriesByBitwardenFolder(filter.folderId)
                }
            }
            // Combine with settings for smart deduplication logic
            combine(baseFlow, smartDeduplicationEnabled) { entries, smartDedupe ->
                // Dedupe logic:
                // 1. If searching, or explicit Local/KeePass/Bitwarden filter -> NO dedupe (show raw data).
                // 2. If "All" or other categories -> Apply Smart Dedupe if enabled.
                val isExplicitSourceView = when (filter) {
                    is CategoryFilter.BitwardenVault -> true
                    is CategoryFilter.BitwardenFolderFilter -> true // Explicit folder view
                    is CategoryFilter.KeePassDatabase -> true
                    is CategoryFilter.KeePassGroupFilter -> true
                    is CategoryFilter.Local -> true // Local view shows all local entries
                    else -> false
                }
                
                // Smart dedupe is only for the "All" view and does not mutate source data.
                val shouldDedupe = !isExplicitSourceView && smartDedupe && filter is CategoryFilter.All
                
                val filtered = if (shouldDedupe) {
                    dedupeSmart(entries)
                } else {
                    entries
                }
                filtered.map { entry ->
                    entry.copy(password = decryptForDisplay(entry.password))
                }
            }
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allPasswords: StateFlow<List<PasswordEntry>> = repository.getAllPasswordEntries()
        .map { entries ->
            entries.map { entry ->
                entry.copy(password = decryptForDisplay(entry.password))
            }
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Smart Deduplication Logic
     * Display-layer dedupe for "All" view:
     * 1) merge same account across sources
     * 2) then keep one entry per unique password value within that account
     */
    private fun dedupeSmart(entries: List<PasswordEntry>): List<PasswordEntry> {
        if (entries.size <= 1) return entries

        val indexById = entries.mapIndexed { index, entry -> entry.id to index }.toMap()
        val accountGroups = entries.groupBy { buildDedupeKey(it) }
        val deduped = mutableListOf<PasswordEntry>()

        for ((_, groupEntries) in accountGroups) {
            if (groupEntries.size <= 1) {
                deduped.addAll(groupEntries)
                continue
            }

            val decrypted = groupEntries.map { entry ->
                entry to runCatching { securityManager.decryptData(entry.password) }.getOrNull()
            }

            val hasAnyDecrypted = decrypted.any { (_, password) -> password != null }
            if (!hasAnyDecrypted) {
                // When auth/MDK is unavailable, still collapse source-duplicates by account key.
                pickBestEntry(groupEntries)?.let { deduped.add(it) }
                continue
            }

            val knownPasswordBuckets = decrypted
                .filter { (_, password) -> password != null }
                .groupBy({ (_, password) -> password!! }, { (entry, _) -> entry })

            for ((_, candidates) in knownPasswordBuckets) {
                pickBestEntry(candidates)?.let { deduped.add(it) }
            }
        }

        return deduped.sortedBy { indexById[it.id] ?: Int.MAX_VALUE }
    }

    private fun pickBestEntry(candidates: List<PasswordEntry>): PasswordEntry? {
        return candidates.maxWithOrNull(
            compareBy<PasswordEntry> { it.notes.length }
                .thenBy { it.website.length }
                .thenBy { it.username.length }
                .thenBy { if (it.isFavorite) 1 else 0 }
                .thenBy { if (it.keepassDatabaseId != null || it.bitwardenVaultId != null) 1 else 0 }
                .thenBy { it.updatedAt.time }
        )
    }

    private fun buildDedupeKey(entry: PasswordEntry): String {
        val title = normalizeDedupeText(entry.title)
        val username = normalizeDedupeText(entry.username)
        val website = normalizeWebsiteForDedupe(entry.website)
        return "$title|$username|$website"
    }

    private fun normalizeDedupeText(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
    }

    private fun normalizeWebsiteForDedupe(value: String): String {
        val raw = value.trim()
        if (raw.isEmpty()) return ""

        return runCatching {
            val withScheme = if (raw.contains("://")) raw else "https://$raw"
            val uri = URI(withScheme)
            val host = (uri.host ?: "").lowercase(Locale.ROOT).removePrefix("www.")
            if (host.isEmpty()) return@runCatching raw.lowercase(Locale.ROOT).trimEnd('/')

            val port = uri.port
            val hostWithPort = if (port == -1 || port == 80 || port == 443) host else "$host:$port"
            val path = (uri.path ?: "").trim().trimEnd('/').lowercase(Locale.ROOT)
            if (path.isBlank()) hostWithPort else "$hostWithPort$path"
        }.getOrElse {
            raw.lowercase(Locale.ROOT)
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("www.")
                .trimEnd('/')
        }
    }

    private fun decryptForDisplay(encryptedPassword: String): String {
        if (encryptedPassword.isEmpty()) return ""
        return runCatching {
            securityManager.decryptData(encryptedPassword)
        }.getOrElse { error ->
            Log.w("PasswordViewModel", "Skip decrypt due to auth/key state: ${error.message}")
            ""
        }
    }

    private fun syncKeePassDatabase(databaseId: Long) {
        val service = keepassService ?: return
        viewModelScope.launch {
            val result = service.readPasswordEntries(databaseId)
            val data = result.getOrNull() ?: return@launch
            upsertKeePassEntries(databaseId, data)
        }
    }

    private suspend fun upsertKeePassEntries(databaseId: Long, entries: List<KeePassEntryData>) {
        entries.forEach { item ->
            val existingById = item.monicaLocalId?.let { repository.getPasswordEntryById(it) }
            val existing = if (existingById != null && existingById.keepassDatabaseId == databaseId) {
                existingById
            } else {
                repository.getDuplicateEntryInKeePass(databaseId, item.title, item.username, item.url)
            }
            val encryptedPassword = securityManager.encryptData(item.password)
            if (existing != null) {
                val updated = existing.copy(
                    title = item.title,
                    username = item.username,
                    password = encryptedPassword,
                    website = item.url,
                    notes = item.notes,
                    keepassDatabaseId = databaseId,
                    keepassGroupPath = item.groupPath,
                    isDeleted = false,
                    deletedAt = null,
                    updatedAt = Date()
                )
                repository.updatePasswordEntry(updated)
            } else {
                val newEntry = PasswordEntry(
                    title = item.title,
                    username = item.username,
                    password = encryptedPassword,
                    website = item.url,
                    notes = item.notes,
                    createdAt = Date(),
                    updatedAt = Date(),
                    keepassDatabaseId = databaseId,
                    keepassGroupPath = item.groupPath
                )
                repository.insertPasswordEntry(newEntry)
            }
        }
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(filter: CategoryFilter) {
        _categoryFilter.value = filter
        when (filter) {
            is CategoryFilter.KeePassDatabase -> syncKeePassDatabase(filter.databaseId)
            is CategoryFilter.KeePassGroupFilter -> syncKeePassDatabase(filter.databaseId)
            else -> Unit
        }
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
            val targetCategory = categoryId?.let { repository.getCategoryById(it) }
            val targetVaultId = targetCategory?.bitwardenVaultId
            val targetFolderId = targetCategory?.bitwardenFolderId

            if (targetVaultId != null && !targetFolderId.isNullOrBlank()) {
                repository.bindPasswordsToBitwardenFolder(
                    ids = ids,
                    vaultId = targetVaultId,
                    folderId = targetFolderId
                )
            } else {
                // 仅清理尚未上传（无 cipherId）的待绑定条目，避免误改已同步条目
                repository.clearPendingBitwardenBinding(ids)
            }
        }
    }
    
    fun movePasswordsToKeePassDatabase(ids: List<Long>, databaseId: Long?) {
        viewModelScope.launch {
            repository.updateKeePassDatabaseForPasswords(ids, databaseId)
        }
    }

    fun movePasswordsToKeePassGroup(ids: List<Long>, databaseId: Long, groupPath: String) {
        viewModelScope.launch {
            repository.updateKeePassGroupForPasswords(ids, databaseId, groupPath)
        }
    }

    fun movePasswordsToBitwardenFolder(ids: List<Long>, vaultId: Long, folderId: String) {
        viewModelScope.launch {
            // Clear KeePass binding first so the same entry can switch storage target.
            repository.updateKeePassDatabaseForPasswords(ids, null)
            repository.bindPasswordsToBitwardenFolder(ids, vaultId, folderId)
        }
    }
    
    fun authenticate(password: String): Boolean {
        val isValid = securityManager.verifyMasterPassword(password)
        _isAuthenticated.value = isValid
        if (isValid) {
            SessionManager.markUnlocked()
        }
        return isValid
    }
    
    fun setMasterPassword(password: String) {
        securityManager.setMasterPassword(password)
        _isAuthenticated.value = true
        SessionManager.markUnlocked()
    }
    
    fun isMasterPasswordSet(): Boolean {
        return securityManager.isMasterPasswordSet()
    }
    
    fun logout() {
        _isAuthenticated.value = false
        SessionManager.markLocked()
    }
    
    fun addPasswordEntry(entry: PasswordEntry, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val boundEntry = applyCategoryBinding(entry)
            val encryptedEntry = boundEntry.copy(
                password = securityManager.encryptData(entry.password),
                createdAt = Date(),
                updatedAt = Date()
            )
            val id = repository.insertPasswordEntry(encryptedEntry)
            if (entry.keepassDatabaseId != null) {
                val service = keepassService
                if (service != null) {
                    val syncResult = service.updatePasswordEntry(
                        databaseId = entry.keepassDatabaseId,
                        entry = entry.copy(id = id),
                        resolvePassword = { it.password }
                    )
                    if (syncResult.isFailure) {
                        repository.deletePasswordEntryById(id)
                        Log.e("PasswordViewModel", "KeePass write failed: ${syncResult.exceptionOrNull()?.message}")
                        return@launch
                    }
                }
            }
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
            updatePasswordEntryInternal(entry)
        }
    }

    private suspend fun updatePasswordEntryInternal(entry: PasswordEntry): Boolean {
        // 获取旧数据用于对比
        val oldEntry = repository.getPasswordEntryById(entry.id)
        
        // 应用分类绑定
        val boundEntry = applyCategoryBinding(entry)
        val entryToUpdate = if (boundEntry.bitwardenVaultId != null) {
            boundEntry.copy(bitwardenLocalModified = true)
        } else {
            boundEntry
        }
        
        val oldPassword = oldEntry?.let { decryptForDisplay(it.password) } ?: ""
        repository.updatePasswordEntry(
            entryToUpdate.copy(
                password = securityManager.encryptData(entryToUpdate.password),
                updatedAt = Date()
            )
        )

        val service = keepassService
        val oldKeepassId = oldEntry?.keepassDatabaseId
        val newKeepassId = entryToUpdate.keepassDatabaseId
        if (service != null) {
            if (oldKeepassId != null && oldKeepassId != newKeepassId) {
                val deleteResult = service.deletePasswordEntries(oldKeepassId, listOf(entryToUpdate.copy(keepassDatabaseId = oldKeepassId)))
                if (deleteResult.isFailure) {
                    Log.e("PasswordViewModel", "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
                }
            }
            if (newKeepassId != null) {
                val updateResult = service.updatePasswordEntry(
                    databaseId = newKeepassId,
                    entry = entryToUpdate,
                    resolvePassword = { it.password }
                )
                if (updateResult.isFailure) {
                    Log.e("PasswordViewModel", "KeePass update failed: ${updateResult.exceptionOrNull()?.message}")
                }
            }
        }
        
        // 记录更新操作
        val changes = takagi.ru.monica.utils.OperationLogger.compareAndGetChanges(
            old = oldEntry,
            new = entryToUpdate,
            fields = listOf(
                "用户名" to { it.username },
                "网站" to { it.website },
                "备注" to { it.notes }
            )
        )

        // 捕获密码变化（记录真实密码，在UI层隐藏显示）
        if (oldEntry != null && oldPassword != entryToUpdate.password) {
            val updatedChanges = changes.toMutableList()
            updatedChanges.add(
                takagi.ru.monica.utils.FieldChange(
                    fieldName = "密码",
                    oldValue = oldPassword,
                    newValue = entryToUpdate.password
                )
            )
            takagi.ru.monica.utils.OperationLogger.logUpdate(
                itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
                itemId = entryToUpdate.id,
                itemTitle = entryToUpdate.title,
                changes = updatedChanges
            )
            return true
        }
        takagi.ru.monica.utils.OperationLogger.logUpdate(
            itemType = takagi.ru.monica.data.OperationLogItemType.PASSWORD,
            itemId = entryToUpdate.id,
            itemTitle = entryToUpdate.title,
            changes = changes
        )
        return true
    }
    
    fun deletePasswordEntry(entry: PasswordEntry) {
        viewModelScope.launch {
            val trashEnabled = trashSettings?.value?.first ?: true
            val service = keepassService
            val keepassId = entry.keepassDatabaseId
            
            if (trashEnabled) {
                if (service != null && keepassId != null) {
                    val deleteResult = service.deletePasswordEntries(keepassId, listOf(entry))
                    if (deleteResult.isFailure) {
                        Log.e("PasswordViewModel", "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
                        return@launch
                    }
                }
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
                if (service != null && keepassId != null) {
                    val deleteResult = service.deletePasswordEntries(keepassId, listOf(entry))
                    if (deleteResult.isFailure) {
                        Log.e("PasswordViewModel", "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
                        return@launch
                    }
                }
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

    /**
     * 更新绑定的验证器密钥
     */
    fun updateAuthenticatorKey(id: Long, authenticatorKey: String) {
        viewModelScope.launch {
            repository.updateAuthenticatorKey(id, authenticatorKey)
        }
    }

    /**
     * 更新绑定的通行密钥元数据
     */
    fun updatePasskeyBindings(id: Long, passkeyBindings: String) {
        viewModelScope.launch {
            repository.updatePasskeyBindings(id, passkeyBindings)
        }
    }
    
    suspend fun getPasswordEntryById(id: Long): PasswordEntry? {
        return repository.getPasswordEntryById(id)?.let { entry ->
            entry.copy(password = decryptForDisplay(entry.password))
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
                entry.copy(password = decryptForDisplay(entry.password))
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

    private fun applyCategoryBinding(entry: PasswordEntry): PasswordEntry {
        // 如果条目已指派到 Bitwarden Vault，且没有指定文件夹，尝试从分类继承
        // 或者，如果条目是在本地创建（无 Vault），但分类绑定了 Bitwarden，则自动指派
        
        val categoryId = entry.categoryId ?: return entry
        val category = categories.value.find { it.id == categoryId } ?: return entry

        // KeePass 条目保持独立，不参与 Bitwarden 自动绑定
        if (entry.keepassDatabaseId != null) return entry

        // 分类未绑定 Bitwarden：清理“待上传”绑定（已同步条目保持映射不动）
        if (category.bitwardenVaultId == null || category.bitwardenFolderId == null) {
            return if (entry.bitwardenCipherId == null) {
                entry.copy(
                    bitwardenVaultId = null,
                    bitwardenFolderId = null,
                    bitwardenLocalModified = false
                )
            } else {
                entry
            }
        }
        
        // 自动绑定到分类关联的 Bitwarden 文件夹
        return entry.copy(
            bitwardenVaultId = category.bitwardenVaultId,
            bitwardenFolderId = category.bitwardenFolderId,
            // 如果是已同步的条目，且文件夹改变了，标记为本地修改
            bitwardenLocalModified = if (entry.bitwardenCipherId != null && entry.bitwardenFolderId != category.bitwardenFolderId) true else entry.bitwardenLocalModified
        )
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
        customFields: List<CustomFieldDraft> = emptyList(), // 自定义字段
        onComplete: (firstPasswordId: Long?) -> Unit = {}
    ) {
        viewModelScope.launch {
            var firstId: Long? = null
            
            // 应用分类绑定规则
            val boundCommonEntry = applyCategoryBinding(commonEntry)
            
            // 1. Process each password
            passwords.forEachIndexed { index, password ->
                if (index < originalIds.size) {
                    // Update existing
                    val id = originalIds[index]
                    if (index == 0) firstId = id
                    val draftEntry = boundCommonEntry.copy(
                        id = id,
                        password = password
                    )
                    val existingEntry = repository.getPasswordEntryById(id)
                    val updatedEntry = existingEntry?.copy(
                        title = draftEntry.title,
                        website = draftEntry.website,
                        username = draftEntry.username,
                        password = draftEntry.password,
                        notes = draftEntry.notes,
                        isFavorite = draftEntry.isFavorite,
                        appPackageName = draftEntry.appPackageName,
                        appName = draftEntry.appName,
                        email = draftEntry.email,
                        phone = draftEntry.phone,
                        addressLine = draftEntry.addressLine,
                        city = draftEntry.city,
                        state = draftEntry.state,
                        zipCode = draftEntry.zipCode,
                        country = draftEntry.country,
                        creditCardNumber = draftEntry.creditCardNumber,
                        creditCardHolder = draftEntry.creditCardHolder,
                        creditCardExpiry = draftEntry.creditCardExpiry,
                        creditCardCVV = draftEntry.creditCardCVV,
                        categoryId = draftEntry.categoryId,
                        keepassDatabaseId = draftEntry.keepassDatabaseId,
                        authenticatorKey = draftEntry.authenticatorKey,
                        passkeyBindings = draftEntry.passkeyBindings,
                        loginType = draftEntry.loginType,
                        ssoProvider = draftEntry.ssoProvider,
                        ssoRefEntryId = draftEntry.ssoRefEntryId,
                        bitwardenVaultId = draftEntry.bitwardenVaultId
                    ) ?: draftEntry
                    updatePasswordEntryInternal(updatedEntry)
                } else {
                    // Create new
                    val newEntry = boundCommonEntry.copy(
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
            
            // 3. 保存自定义字段（只针对第一个密码条目）
            firstId?.let { entryId ->
                saveCustomFieldsForEntry(entryId, customFields)
            }
            
            onComplete(firstId)
        }
    }
    
    // =============== 自定义字段相关方法 ===============
    
    /**
     * 获取指定密码条目的自定义字段（Flow）
     */
    fun getCustomFieldsByEntryId(entryId: Long): Flow<List<CustomField>> {
        return customFieldRepository?.getFieldsByEntryId(entryId) ?: flowOf(emptyList())
    }
    
    /**
     * 获取指定密码条目的自定义字段（同步版本）
     */
    suspend fun getCustomFieldsByEntryIdSync(entryId: Long): List<CustomField> {
        return customFieldRepository?.getFieldsByEntryIdSync(entryId) ?: emptyList()
    }
    
    /**
     * 保存密码条目的自定义字段
     * 同时更新密码条目的 updatedAt 以触发同步
     */
    suspend fun saveCustomFieldsForEntry(entryId: Long, fields: List<CustomFieldDraft>) {
        customFieldRepository?.saveFieldsForEntry(entryId, fields)
        
        // 更新密码条目的 updatedAt 以确保 WebDAV 同步能检测到自定义字段的变化
        repository.updatePasswordUpdatedAt(entryId, java.util.Date())
    }
    
    /**
     * 批量获取多个条目的自定义字段（用于列表显示优化）
     */
    suspend fun getCustomFieldsByEntryIds(entryIds: List<Long>): Map<Long, List<CustomField>> {
        return customFieldRepository?.getFieldsByEntryIds(entryIds) ?: emptyMap()
    }
    
    /**
     * 搜索包含指定关键词的条目ID（通过自定义字段搜索）
     */
    suspend fun searchEntryIdsByCustomFieldContent(query: String): List<Long> {
        return customFieldRepository?.searchEntryIdsByFieldContent(query) ?: emptyList()
    }
}
