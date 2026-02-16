package takagi.ru.monica.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.LocalKeePassDatabaseDao
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.data.OperationLogItemType
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.OperationLogger
import takagi.ru.monica.utils.FieldChange
import takagi.ru.monica.utils.KeePassKdbxService
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.Date
import java.util.Locale
import takagi.ru.monica.utils.SavedCategoryFilterState
import takagi.ru.monica.utils.SettingsManager

/**
 * 验证器分类过滤器
 */
sealed class TotpCategoryFilter {
    object All : TotpCategoryFilter()
    object Local : TotpCategoryFilter()
    object Starred : TotpCategoryFilter()
    object Uncategorized : TotpCategoryFilter()
    object LocalStarred : TotpCategoryFilter()
    object LocalUncategorized : TotpCategoryFilter()
    data class Custom(val categoryId: Long) : TotpCategoryFilter()
    data class KeePassDatabase(val databaseId: Long) : TotpCategoryFilter()
    data class KeePassGroupFilter(val databaseId: Long, val groupPath: String) : TotpCategoryFilter()
    data class KeePassDatabaseStarred(val databaseId: Long) : TotpCategoryFilter()
    data class KeePassDatabaseUncategorized(val databaseId: Long) : TotpCategoryFilter()
    data class BitwardenVault(val vaultId: Long) : TotpCategoryFilter()
    data class BitwardenFolderFilter(val folderId: String, val vaultId: Long) : TotpCategoryFilter()
    data class BitwardenVaultStarred(val vaultId: Long) : TotpCategoryFilter()
    data class BitwardenVaultUncategorized(val vaultId: Long) : TotpCategoryFilter()
}

/**
 * TOTP验证器ViewModel
 */
class TotpViewModel(
    private val repository: SecureItemRepository,
    private val passwordRepository: PasswordRepository,
    context: Context? = null,
    localKeePassDatabaseDao: LocalKeePassDatabaseDao? = null,
    securityManager: SecurityManager? = null
) : ViewModel() {
    companion object {
        private const val FILTER_ALL = "all"
        private const val FILTER_LOCAL = "local"
        private const val FILTER_STARRED = "starred"
        private const val FILTER_UNCATEGORIZED = "uncategorized"
        private const val FILTER_LOCAL_STARRED = "local_starred"
        private const val FILTER_LOCAL_UNCATEGORIZED = "local_uncategorized"
        private const val FILTER_CUSTOM = "custom"
        private const val FILTER_KEEPASS_DATABASE = "keepass_database"
        private const val FILTER_KEEPASS_GROUP = "keepass_group"
        private const val FILTER_KEEPASS_DATABASE_STARRED = "keepass_database_starred"
        private const val FILTER_KEEPASS_DATABASE_UNCATEGORIZED = "keepass_database_uncategorized"
        private const val FILTER_BITWARDEN_VAULT = "bitwarden_vault"
        private const val FILTER_BITWARDEN_FOLDER = "bitwarden_folder"
        private const val FILTER_BITWARDEN_VAULT_STARRED = "bitwarden_vault_starred"
        private const val FILTER_BITWARDEN_VAULT_UNCATEGORIZED = "bitwarden_vault_uncategorized"
    }

    private val keepassService = if (context != null && localKeePassDatabaseDao != null && securityManager != null) {
        KeePassKdbxService(context.applicationContext, localKeePassDatabaseDao, securityManager)
    } else {
        null
    }
    private val settingsManager = context?.let { SettingsManager(it.applicationContext) }
    
    // 搜索查询
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // 分类过滤器
    private val _categoryFilter = MutableStateFlow<TotpCategoryFilter>(TotpCategoryFilter.All)
    val categoryFilter: StateFlow<TotpCategoryFilter> = _categoryFilter.asStateFlow()

    init {
        restoreLastCategoryFilter()
    }
    
    // 分类列表（使用 PasswordRepository 获取）
    val categories: StateFlow<List<Category>> = passwordRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 当前 Bitwarden Vault 筛选下的文件夹集合。
     * 兼容历史数据: 某些旧数据可能只有 folderId，没有写入 vaultId。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedBitwardenVaultFolderIds: StateFlow<Set<String>> = _categoryFilter
        .flatMapLatest { filter ->
            val folderFlow = when (filter) {
                is TotpCategoryFilter.BitwardenVault -> {
                    passwordRepository.getBitwardenFoldersByVaultId(filter.vaultId)
                }
                is TotpCategoryFilter.BitwardenVaultStarred -> {
                    passwordRepository.getBitwardenFoldersByVaultId(filter.vaultId)
                }
                else -> flowOf(emptyList())
            }
            folderFlow.map { folders -> folders.mapTo(linkedSetOf()) { it.bitwardenFolderId } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    // TOTP项目列表 - 合并实际存储的TOTP和从密码authenticatorKey生成的虚拟TOTP
    val totpItems: StateFlow<List<SecureItem>> = combine(
        _searchQuery,
        _categoryFilter,
        repository.getItemsByType(ItemType.TOTP),
        passwordRepository.getAllPasswordEntries(),
        selectedBitwardenVaultFolderIds
    ) { query, filter, storedTotps, allPasswords, selectedVaultFolderIds ->
        // 收集所有已存储的TOTP密钥（用于去重）
        val existingSecrets = storedTotps.mapNotNull { item ->
            try {
                Json.decodeFromString<TotpData>(item.itemData).secret
            } catch (e: Exception) {
                null
            }
        }.toSet()
        
        // 从密码的authenticatorKey生成虚拟TOTP项目（仅当密钥不存在于实际TOTP中时）
        val virtualTotps = allPasswords
            .filter { it.authenticatorKey.isNotBlank() && it.authenticatorKey !in existingSecrets }
            .distinctBy { it.authenticatorKey }  // 去重相同的authenticatorKey
            .map { password ->
                // 创建虚拟TOTP项目（使用负ID以区分实际存储的项目）
                val totpData = TotpData(
                    secret = password.authenticatorKey,
                    issuer = password.website.takeIf { it.isNotBlank() } ?: password.title,
                    accountName = password.username.takeIf { it.isNotBlank() } ?: password.title,
                    boundPasswordId = password.id,
                    categoryId = password.categoryId  // 继承密码的分类
                )
                SecureItem(
                    id = -password.id, // 使用负ID标识这是虚拟项目
                    itemType = ItemType.TOTP,
                    title = password.title,
                    notes = "来自密码: ${password.title}",
                    itemData = Json.encodeToString(totpData),
                    isFavorite = false,
                    createdAt = password.createdAt,
                    updatedAt = password.updatedAt,
                    imagePaths = "",
                    categoryId = password.categoryId,  // 继承密码的分类
                    keepassDatabaseId = password.keepassDatabaseId,
                    keepassGroupPath = password.keepassGroupPath,
                    bitwardenVaultId = password.bitwardenVaultId,
                    bitwardenFolderId = password.bitwardenFolderId
                )
            }
        
        // 合并实际TOTP和虚拟TOTP
        val allTotps = storedTotps + virtualTotps
        
        // 首先应用分类过滤
        val categoryFiltered = when (filter) {
            is TotpCategoryFilter.All -> allTotps
            is TotpCategoryFilter.Local -> allTotps.filter { it.bitwardenVaultId == null && it.keepassDatabaseId == null }
            is TotpCategoryFilter.Starred -> allTotps.filter { it.isFavorite }
            is TotpCategoryFilter.Uncategorized -> allTotps.filter { 
                it.categoryId == null && try {
                    Json.decodeFromString<TotpData>(it.itemData).categoryId == null
                } catch (e: Exception) { true }
            }
            is TotpCategoryFilter.LocalStarred -> allTotps.filter {
                it.bitwardenVaultId == null && it.keepassDatabaseId == null && it.isFavorite
            }
            is TotpCategoryFilter.LocalUncategorized -> allTotps.filter {
                it.bitwardenVaultId == null && it.keepassDatabaseId == null && (
                    it.categoryId == null && try {
                        Json.decodeFromString<TotpData>(it.itemData).categoryId == null
                    } catch (e: Exception) { true }
                )
            }
            is TotpCategoryFilter.Custom -> allTotps.filter { item ->
                item.categoryId == filter.categoryId || try {
                    Json.decodeFromString<TotpData>(item.itemData).categoryId == filter.categoryId
                } catch (e: Exception) { false }
            }
            is TotpCategoryFilter.KeePassDatabase -> allTotps.filter { it.keepassDatabaseId == filter.databaseId }
            is TotpCategoryFilter.KeePassGroupFilter -> allTotps.filter {
                it.keepassDatabaseId == filter.databaseId && it.keepassGroupPath == filter.groupPath
            }
            is TotpCategoryFilter.KeePassDatabaseStarred -> allTotps.filter {
                it.keepassDatabaseId == filter.databaseId && it.isFavorite
            }
            is TotpCategoryFilter.KeePassDatabaseUncategorized -> allTotps.filter {
                it.keepassDatabaseId == filter.databaseId && it.keepassGroupPath.isNullOrBlank()
            }
            is TotpCategoryFilter.BitwardenVault -> allTotps.filter {
                it.bitwardenVaultId == filter.vaultId ||
                    (
                        it.bitwardenVaultId == null &&
                            !it.bitwardenFolderId.isNullOrBlank() &&
                            it.bitwardenFolderId in selectedVaultFolderIds
                        )
            }
            is TotpCategoryFilter.BitwardenFolderFilter -> allTotps.filter { it.bitwardenFolderId == filter.folderId }
            is TotpCategoryFilter.BitwardenVaultStarred -> allTotps.filter {
                (
                    it.bitwardenVaultId == filter.vaultId ||
                        (
                            it.bitwardenVaultId == null &&
                                !it.bitwardenFolderId.isNullOrBlank() &&
                                it.bitwardenFolderId in selectedVaultFolderIds
                            )
                    ) && it.isFavorite
            }
            is TotpCategoryFilter.BitwardenVaultUncategorized -> allTotps.filter {
                it.bitwardenVaultId == filter.vaultId && it.bitwardenFolderId == null
            }
        }
        
        // 然后应用搜索过滤
        if (query.isBlank()) {
            categoryFiltered
        } else {
            categoryFiltered.filter { item ->
                item.title.contains(query, ignoreCase = true) ||
                item.notes.contains(query, ignoreCase = true) ||
                try {
                    val data = Json.decodeFromString<TotpData>(item.itemData)
                    data.issuer.contains(query, ignoreCase = true) ||
                    data.accountName.contains(query, ignoreCase = true)
                } catch (e: Exception) {
                    false
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    /**
     * 更新搜索查询
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    /**
     * 设置分类过滤器
     */
    fun setCategoryFilter(filter: TotpCategoryFilter) {
        _categoryFilter.value = filter
        persistCategoryFilter(filter)
        when (filter) {
            is TotpCategoryFilter.KeePassDatabase -> syncKeePassTotp(filter.databaseId)
            is TotpCategoryFilter.KeePassGroupFilter -> syncKeePassTotp(filter.databaseId)
            is TotpCategoryFilter.KeePassDatabaseStarred -> syncKeePassTotp(filter.databaseId)
            is TotpCategoryFilter.KeePassDatabaseUncategorized -> syncKeePassTotp(filter.databaseId)
            else -> Unit
        }
    }

    private fun persistCategoryFilter(filter: TotpCategoryFilter) {
        val manager = settingsManager ?: return
        viewModelScope.launch {
            manager.updateCategoryFilterState(
                scope = SettingsManager.CategoryFilterScope.TOTP,
                state = encodeCategoryFilter(filter)
            )
        }
    }

    private fun restoreLastCategoryFilter() {
        val manager = settingsManager ?: return
        viewModelScope.launch {
            runCatching {
                manager.categoryFilterStateFlow(SettingsManager.CategoryFilterScope.TOTP).first()
            }.onSuccess { state ->
                _categoryFilter.value = decodeCategoryFilter(state)
            }
        }
    }

    private fun encodeCategoryFilter(filter: TotpCategoryFilter): SavedCategoryFilterState = when (filter) {
        TotpCategoryFilter.All -> SavedCategoryFilterState(type = FILTER_ALL)
        TotpCategoryFilter.Local -> SavedCategoryFilterState(type = FILTER_LOCAL)
        TotpCategoryFilter.Starred -> SavedCategoryFilterState(type = FILTER_STARRED)
        TotpCategoryFilter.Uncategorized -> SavedCategoryFilterState(type = FILTER_UNCATEGORIZED)
        TotpCategoryFilter.LocalStarred -> SavedCategoryFilterState(type = FILTER_LOCAL_STARRED)
        TotpCategoryFilter.LocalUncategorized -> SavedCategoryFilterState(type = FILTER_LOCAL_UNCATEGORIZED)
        is TotpCategoryFilter.Custom -> SavedCategoryFilterState(type = FILTER_CUSTOM, primaryId = filter.categoryId)
        is TotpCategoryFilter.KeePassDatabase -> SavedCategoryFilterState(type = FILTER_KEEPASS_DATABASE, primaryId = filter.databaseId)
        is TotpCategoryFilter.KeePassGroupFilter -> SavedCategoryFilterState(type = FILTER_KEEPASS_GROUP, primaryId = filter.databaseId, text = filter.groupPath)
        is TotpCategoryFilter.KeePassDatabaseStarred -> SavedCategoryFilterState(type = FILTER_KEEPASS_DATABASE_STARRED, primaryId = filter.databaseId)
        is TotpCategoryFilter.KeePassDatabaseUncategorized -> SavedCategoryFilterState(type = FILTER_KEEPASS_DATABASE_UNCATEGORIZED, primaryId = filter.databaseId)
        is TotpCategoryFilter.BitwardenVault -> SavedCategoryFilterState(type = FILTER_BITWARDEN_VAULT, primaryId = filter.vaultId)
        is TotpCategoryFilter.BitwardenFolderFilter -> SavedCategoryFilterState(type = FILTER_BITWARDEN_FOLDER, primaryId = filter.vaultId, text = filter.folderId)
        is TotpCategoryFilter.BitwardenVaultStarred -> SavedCategoryFilterState(type = FILTER_BITWARDEN_VAULT_STARRED, primaryId = filter.vaultId)
        is TotpCategoryFilter.BitwardenVaultUncategorized -> SavedCategoryFilterState(type = FILTER_BITWARDEN_VAULT_UNCATEGORIZED, primaryId = filter.vaultId)
    }

    private fun decodeCategoryFilter(state: SavedCategoryFilterState): TotpCategoryFilter {
        return when (state.type.lowercase(Locale.ROOT)) {
            FILTER_ALL -> TotpCategoryFilter.All
            FILTER_LOCAL -> TotpCategoryFilter.Local
            FILTER_STARRED -> TotpCategoryFilter.Starred
            FILTER_UNCATEGORIZED -> TotpCategoryFilter.Uncategorized
            FILTER_LOCAL_STARRED -> TotpCategoryFilter.LocalStarred
            FILTER_LOCAL_UNCATEGORIZED -> TotpCategoryFilter.LocalUncategorized
            FILTER_CUSTOM -> state.primaryId?.let { TotpCategoryFilter.Custom(it) } ?: TotpCategoryFilter.All
            FILTER_KEEPASS_DATABASE -> state.primaryId?.let { TotpCategoryFilter.KeePassDatabase(it) } ?: TotpCategoryFilter.All
            FILTER_KEEPASS_GROUP -> {
                val databaseId = state.primaryId
                val groupPath = state.text
                if (databaseId != null && !groupPath.isNullOrBlank()) TotpCategoryFilter.KeePassGroupFilter(databaseId, groupPath) else TotpCategoryFilter.All
            }
            FILTER_KEEPASS_DATABASE_STARRED -> state.primaryId?.let { TotpCategoryFilter.KeePassDatabaseStarred(it) } ?: TotpCategoryFilter.All
            FILTER_KEEPASS_DATABASE_UNCATEGORIZED -> state.primaryId?.let { TotpCategoryFilter.KeePassDatabaseUncategorized(it) } ?: TotpCategoryFilter.All
            FILTER_BITWARDEN_VAULT -> state.primaryId?.let { TotpCategoryFilter.BitwardenVault(it) } ?: TotpCategoryFilter.All
            FILTER_BITWARDEN_FOLDER -> {
                val vaultId = state.primaryId
                val folderId = state.text
                if (vaultId != null && !folderId.isNullOrBlank()) TotpCategoryFilter.BitwardenFolderFilter(folderId, vaultId) else TotpCategoryFilter.All
            }
            FILTER_BITWARDEN_VAULT_STARRED -> state.primaryId?.let { TotpCategoryFilter.BitwardenVaultStarred(it) } ?: TotpCategoryFilter.All
            FILTER_BITWARDEN_VAULT_UNCATEGORIZED -> state.primaryId?.let { TotpCategoryFilter.BitwardenVaultUncategorized(it) } ?: TotpCategoryFilter.All
            else -> TotpCategoryFilter.All
        }
    }

    private fun syncKeePassTotp(databaseId: Long) {
        viewModelScope.launch {
            val snapshots = keepassService
                ?.readSecureItems(databaseId, setOf(ItemType.TOTP))
                ?.getOrNull()
                ?: return@launch

            val existingTotp = repository.getItemsByType(ItemType.TOTP).first()
            snapshots.forEach { snapshot ->
                val incoming = snapshot.item
                val existingBySource = snapshot.sourceMonicaId
                    ?.takeIf { it > 0 }
                    ?.let { sourceId -> repository.getItemById(sourceId) }
                    ?.takeIf { it.itemType == ItemType.TOTP }

                val existing = existingBySource ?: existingTotp.firstOrNull {
                    it.itemType == ItemType.TOTP &&
                        it.keepassDatabaseId == databaseId &&
                        it.keepassGroupPath == incoming.keepassGroupPath &&
                        it.title == incoming.title
                }

                if (existing == null) {
                    repository.insertItem(incoming)
                } else {
                    repository.updateItem(
                        existing.copy(
                            title = incoming.title,
                            notes = incoming.notes,
                            itemData = incoming.itemData,
                            isFavorite = incoming.isFavorite,
                            imagePaths = incoming.imagePaths,
                            keepassDatabaseId = incoming.keepassDatabaseId,
                            keepassGroupPath = incoming.keepassGroupPath,
                            isDeleted = false,
                            deletedAt = null,
                            updatedAt = Date()
                        )
                    )
                }
            }
        }
    }
    
    /**
     * 快速添加TOTP（从底部导航栏快速添加）
     */
    fun quickAddTotp(name: String, secret: String) {
        if (name.isBlank() || secret.isBlank()) return
        val totpData = TotpData(
            secret = secret.replace(" ", "").uppercase(),
            issuer = name,
            accountName = name
        )
        saveTotpItem(
            id = null,
            title = name,
            notes = "",
            totpData = totpData
        )
    }
    
    /**
     * 根据密钥查找现有的TOTP项目
     */
    fun findTotpBySecret(secret: String): SecureItem? {
        return totpItems.value.find { item ->
            try {
                val data = Json.decodeFromString<TotpData>(item.itemData)
                data.secret == secret
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * 根据ID获取TOTP项目
     */
    suspend fun getTotpItemById(id: Long): SecureItem? {
        return repository.getItemById(id)
    }
    
    /**
     * 保存TOTP项目
     */
    fun saveTotpItem(
        id: Long?,
        title: String,
        notes: String,
        totpData: TotpData,
        isFavorite: Boolean = false,
        categoryId: Long? = null,
        keepassDatabaseId: Long? = null,
        bitwardenVaultId: Long? = null,
        bitwardenFolderId: String? = null
    ) {
        viewModelScope.launch {
            try {
                val existingItem = if (id != null && id > 0) repository.getItemById(id) else null
                val previousTotpData = existingItem?.let { item ->
                    try {
                        Json.decodeFromString<TotpData>(item.itemData)
                    } catch (e: Exception) {
                        null
                    }
                }
                val previousBoundId = previousTotpData?.boundPasswordId
                val previousSecret = previousTotpData?.secret ?: ""

                // 将categoryId和keepassDatabaseId保存到TotpData中
                val updatedTotpData = totpData.copy(
                    categoryId = categoryId,
                    keepassDatabaseId = keepassDatabaseId
                )
                val itemDataJson = Json.encodeToString(updatedTotpData)
                
                val item = if (id != null && id > 0) {
                    // 更新现有项目
                    val existing = repository.getItemById(id)
                    existing?.copy(
                        title = title,
                        notes = notes,
                        itemData = itemDataJson,
                        categoryId = categoryId,
                        keepassDatabaseId = keepassDatabaseId,
                        bitwardenVaultId = bitwardenVaultId,
                        bitwardenFolderId = bitwardenFolderId,
                        bitwardenLocalModified = existing.bitwardenCipherId != null && bitwardenVaultId != null,
                        syncStatus = if (bitwardenVaultId != null) {
                            if (existing.bitwardenCipherId != null) "PENDING" else existing.syncStatus
                        } else {
                            "NONE"
                        },
                        updatedAt = Date()
                    ) ?: return@launch
                } else {
                    // 创建新项目
                    SecureItem(
                        itemType = ItemType.TOTP,
                        title = title,
                        notes = notes,
                        itemData = itemDataJson,
                        isFavorite = isFavorite,
                        categoryId = categoryId,
                        keepassDatabaseId = keepassDatabaseId,
                        bitwardenVaultId = bitwardenVaultId,
                        bitwardenFolderId = bitwardenFolderId,
                        syncStatus = if (bitwardenVaultId != null) "PENDING" else "NONE",
                        createdAt = Date(),
                        updatedAt = Date(),
                        imagePaths = ""
                    )
                }
                
                if (id != null && id > 0) {
                    // 更新操作 - 记录变更日志
                    val existing = repository.getItemById(id)
                    repository.updateItem(item)
                    if (existing != null) {
                        val changes = mutableListOf<FieldChange>()
                        if (existing.title != title) {
                            changes.add(FieldChange("标题", existing.title, title))
                        }
                        if (existing.notes != notes) {
                            changes.add(FieldChange("备注", existing.notes, notes))
                        }
                        // 始终记录更新操作，即使没有检测到字段变更
                        OperationLogger.logUpdate(
                            itemType = OperationLogItemType.TOTP,
                            itemId = id,
                            itemTitle = title,
                            changes = if (changes.isEmpty()) listOf(FieldChange("更新", "编辑于", java.text.SimpleDateFormat("HH:mm").format(Date()))) else changes
                        )
                    }
                } else {
                    val newId = repository.insertItem(item)
                    if (keepassDatabaseId != null) {
                        val syncResult = keepassService?.updateSecureItem(keepassDatabaseId, item.copy(id = newId))
                        if (syncResult?.isFailure == true) {
                            Log.e("TotpViewModel", "KeePass write failed: ${syncResult.exceptionOrNull()?.message}")
                        }
                    }
                    // 创建操作 - 记录日志
                    OperationLogger.logCreate(
                        itemType = OperationLogItemType.TOTP,
                        itemId = newId,
                        itemTitle = title
                    )
                }

                // 同步绑定到密码的验证器密钥
                val boundId = updatedTotpData.boundPasswordId
                val secret = updatedTotpData.secret
                if (boundId != null && secret.isNotBlank()) {
                    passwordRepository.updateAuthenticatorKey(boundId, secret)
                }

                // 如果解绑或更换绑定，清理旧绑定的密钥（仅当密钥一致时）
                if (previousBoundId != null && previousBoundId != boundId && previousSecret.isNotBlank()) {
                    val previousPassword = passwordRepository.getPasswordEntryById(previousBoundId)
                    if (previousPassword?.authenticatorKey == previousSecret) {
                        passwordRepository.updateAuthenticatorKey(previousBoundId, "")
                    }
                }

                if (id != null && id > 0) {
                    val current = repository.getItemById(id)
                    if (current != null) {
                        val oldKeepassId = existingItem?.keepassDatabaseId
                        val newKeepassId = current.keepassDatabaseId
                        if (oldKeepassId != null && oldKeepassId != newKeepassId) {
                            existingItem?.let { oldItem ->
                                val deleteResult = keepassService?.deleteSecureItems(oldKeepassId, listOf(oldItem))
                                if (deleteResult?.isFailure == true) {
                                    Log.e("TotpViewModel", "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
                                }
                            }
                        }
                        if (newKeepassId != null) {
                            val updateResult = keepassService?.updateSecureItem(newKeepassId, current)
                            if (updateResult?.isFailure == true) {
                                Log.e("TotpViewModel", "KeePass update failed: ${updateResult.exceptionOrNull()?.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // TODO: 处理错误
            }
        }
    }

    /**
     * 解绑指定密码条目对应的验证器（不删除验证器本身）
     * 当密码页清空密钥时，仅解除绑定
     */
    fun unbindTotpFromPassword(passwordId: Long, secret: String? = null) {
        viewModelScope.launch {
            try {
                val items = repository.getItemsByType(ItemType.TOTP).first()
                val target = items.firstOrNull { item ->
                    val data = try {
                        Json.decodeFromString<TotpData>(item.itemData)
                    } catch (e: Exception) {
                        null
                    }
                    data?.boundPasswordId == passwordId &&
                        (secret.isNullOrBlank() || data.secret == secret)
                }

                if (target != null) {
                    val data = Json.decodeFromString<TotpData>(target.itemData)
                    val updatedData = data.copy(boundPasswordId = null)
                    repository.updateItem(
                        target.copy(
                            itemData = Json.encodeToString(updatedData),
                            updatedAt = Date()
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 删除TOTP项目
     * @param item 要删除的项目
     * @param softDelete 是否软删除（移入回收站），默认为 true
     */
    fun deleteTotpItem(item: SecureItem, softDelete: Boolean = true) {
        viewModelScope.launch {
            if (item.keepassDatabaseId != null) {
                val deleteResult = keepassService?.deleteSecureItems(item.keepassDatabaseId, listOf(item))
                if (deleteResult?.isFailure == true) {
                    Log.e("TotpViewModel", "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
                }
            }
            val totpData = try {
                Json.decodeFromString<TotpData>(item.itemData)
            } catch (e: Exception) {
                null
            }

            if (totpData?.boundPasswordId != null && totpData.secret.isNotBlank()) {
                val boundId = totpData.boundPasswordId
                val password = boundId?.let { passwordRepository.getPasswordEntryById(it) }
                if (password?.authenticatorKey == totpData.secret) {
                    if (password.bitwardenVaultId != null && password.bitwardenCipherId != null) {
                        // For Bitwarden-linked passwords, mark as locally modified so sync can clear remote login.totp.
                        passwordRepository.updatePasswordEntry(
                            password.copy(
                                authenticatorKey = "",
                                bitwardenLocalModified = true,
                                updatedAt = Date()
                            )
                        )
                    } else {
                        passwordRepository.updateAuthenticatorKey(boundId, "")
                    }
                }
            }

            // Virtual TOTP items are derived from password.authenticatorKey and are not persisted in secure_items.
            if (item.id <= 0) {
                return@launch
            }

            if (softDelete) {
                // 软删除：移动到回收站
                repository.softDeleteItem(item)
                // 记录移入回收站操作
                OperationLogger.logDelete(
                    itemType = OperationLogItemType.TOTP,
                    itemId = item.id,
                    itemTitle = item.title,
                    detail = "移入回收站"
                )
            } else {
                // 永久删除
                repository.deleteItem(item)
                // 删除操作 - 记录日志
                OperationLogger.logDelete(
                    itemType = OperationLogItemType.TOTP,
                    itemId = item.id,
                    itemTitle = item.title
                )
            }
        }
    }
    
    /**
     * 切换收藏状态
     */
    fun toggleFavorite(id: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.updateFavoriteStatus(id, isFavorite)
        }
    }
    
    /**
     * 更新排序顺序
     */
    fun updateSortOrders(items: List<Pair<Long, Int>>) {
        viewModelScope.launch {
            repository.updateSortOrders(items)
        }
    }
    
    /**
     * HOTP专用: 增加计数器并重新生成验证码
     * @param itemId HOTP项目ID
     */
    fun incrementHotpCounter(itemId: Long) {
        viewModelScope.launch {
            try {
                val item = repository.getItemById(itemId) ?: return@launch
                
                // 解析TOTP数据
                val totpData = Json.decodeFromString<TotpData>(item.itemData)
                
                // 只处理HOTP类型
                if (totpData.otpType != takagi.ru.monica.data.model.OtpType.HOTP) {
                    return@launch
                }
                
                // 增加计数器
                val updatedTotpData = totpData.copy(counter = totpData.counter + 1)
                val updatedItemData = Json.encodeToString(updatedTotpData)
                
                // 更新数据库
                val updatedItem = item.copy(
                    itemData = updatedItemData,
                    updatedAt = Date()
                )
                
                repository.updateItem(updatedItem)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 移动TOTP到指定分类
     */
    fun moveToCategory(ids: List<Long>, categoryId: Long?) {
        viewModelScope.launch {
            ids.forEach { id ->
                try {
                    val item = repository.getItemById(id) ?: return@forEach
                    
                    // 更新TotpData中的categoryId
                    val totpData = try {
                        Json.decodeFromString<TotpData>(item.itemData)
                    } catch (e: Exception) {
                        return@forEach
                    }
                    val updatedTotpData = totpData.copy(categoryId = categoryId)
                    val updatedItemData = Json.encodeToString(updatedTotpData)
                    
                    // 更新数据库
                    val updatedItem = item.copy(
                        itemData = updatedItemData,
                        categoryId = categoryId,
                        updatedAt = Date()
                    )
                    repository.updateItem(updatedItem)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun moveToKeePassDatabase(ids: List<Long>, databaseId: Long?) {
        viewModelScope.launch {
            ids.forEach { id ->
                try {
                    val item = repository.getItemById(id) ?: return@forEach
                    val totpData = runCatching { Json.decodeFromString<TotpData>(item.itemData) }.getOrNull() ?: return@forEach
                    val updatedData = totpData.copy(keepassDatabaseId = databaseId)
                    val updatedItem = item.copy(
                        itemData = Json.encodeToString(updatedData),
                        keepassDatabaseId = databaseId,
                        keepassGroupPath = null,
                        bitwardenVaultId = null,
                        bitwardenFolderId = null,
                        bitwardenLocalModified = false,
                        syncStatus = "NONE",
                        updatedAt = Date()
                    )
                    repository.updateItem(updatedItem)
                    if (databaseId != null) {
                        keepassService?.updateSecureItem(databaseId, updatedItem)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun moveToKeePassGroup(ids: List<Long>, databaseId: Long, groupPath: String) {
        viewModelScope.launch {
            ids.forEach { id ->
                try {
                    val item = repository.getItemById(id) ?: return@forEach
                    val totpData = runCatching { Json.decodeFromString<TotpData>(item.itemData) }.getOrNull() ?: return@forEach
                    val updatedData = totpData.copy(keepassDatabaseId = databaseId)
                    val updatedItem = item.copy(
                        itemData = Json.encodeToString(updatedData),
                        keepassDatabaseId = databaseId,
                        keepassGroupPath = groupPath,
                        bitwardenVaultId = null,
                        bitwardenFolderId = null,
                        bitwardenLocalModified = false,
                        syncStatus = "NONE",
                        updatedAt = Date()
                    )
                    repository.updateItem(updatedItem)
                    keepassService?.updateSecureItem(databaseId, updatedItem)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun moveToBitwardenFolder(ids: List<Long>, vaultId: Long, folderId: String) {
        viewModelScope.launch {
            ids.forEach { id ->
                try {
                    val item = repository.getItemById(id) ?: return@forEach
                    val updatedItem = item.copy(
                        keepassDatabaseId = null,
                        keepassGroupPath = null,
                        bitwardenVaultId = vaultId,
                        bitwardenFolderId = folderId,
                        bitwardenLocalModified = item.bitwardenCipherId != null,
                        syncStatus = if (item.bitwardenCipherId != null) "PENDING" else item.syncStatus,
                        updatedAt = Date()
                    )
                    repository.updateItem(updatedItem)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    /**
     * 添加新分类
     */
    fun addCategory(name: String, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val category = Category(name = name)
            val id = passwordRepository.insertCategory(category)
            onResult(id)
        }
    }
    
    /**
     * 更新分类
     */
    fun updateCategory(category: Category) {
        viewModelScope.launch {
            passwordRepository.updateCategory(category)
        }
    }
    
    /**
     * 删除分类
     */
    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            passwordRepository.deleteCategory(category)
            // 如果当前过滤器是这个分类，重置为全部
            if (_categoryFilter.value is TotpCategoryFilter.Custom &&
                (_categoryFilter.value as TotpCategoryFilter.Custom).categoryId == category.id) {
                _categoryFilter.value = TotpCategoryFilter.All
            }
        }
    }
}
