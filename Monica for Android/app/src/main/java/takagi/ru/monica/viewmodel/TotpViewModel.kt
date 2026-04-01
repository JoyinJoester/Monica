package takagi.ru.monica.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import takagi.ru.monica.keepass.KeePassSecureItemCreateExecutor
import takagi.ru.monica.keepass.KeePassSecureItemDeleteExecutor
import takagi.ru.monica.keepass.KeePassSecureItemUpdateExecutor
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.LocalKeePassDatabaseDao
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.SecureItemOwnership
import takagi.ru.monica.data.asMonicaLocalCopy
import takagi.ru.monica.data.hasBitwardenBinding
import takagi.ru.monica.data.hasOwnershipConflict
import takagi.ru.monica.data.isLocalOnlyItem
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.data.OperationLogItemType
import takagi.ru.monica.data.bitwarden.BitwardenPendingOperation
import takagi.ru.monica.data.resolveOwnership
import takagi.ru.monica.repository.KeePassCompatibilityBridge
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.KeePassWorkspaceRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.OperationLogger
import takagi.ru.monica.utils.FieldChange
import takagi.ru.monica.utils.KeePassKdbxService
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.Date
import java.util.Locale
import java.util.UUID
import takagi.ru.monica.utils.SavedCategoryFilterState
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.util.TotpDataResolver

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
    private val localKeePassDatabaseDao: LocalKeePassDatabaseDao? = null,
    securityManager: SecurityManager? = null
) : ViewModel() {
    private data class KeePassMutationIdentity(
        val groupPath: String?,
        val entryUuid: String?,
        val groupUuid: String?
    )

    data class BitwardenTotpRepairResult(
        val normalizedTotpItems: Int,
        val queuedTotpItemsForSync: Int,
        val normalizedPasswords: Int,
        val queuedPasswordsForSync: Int,
        val skippedItems: Int
    ) {
        val normalizedCount: Int
            get() = normalizedTotpItems + normalizedPasswords

        val queuedForSyncCount: Int
            get() = queuedTotpItemsForSync + queuedPasswordsForSync
    }

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

    private val keepassBridge = if (context != null && localKeePassDatabaseDao != null && securityManager != null) {
        KeePassCompatibilityBridge(
            KeePassWorkspaceRepository(
                context = context.applicationContext,
                dao = localKeePassDatabaseDao,
                securityManager = securityManager
            )
        )
    } else {
        null
    }
    private val keepassSecureItemCreateExecutor = KeePassSecureItemCreateExecutor(keepassBridge)
    private val keepassSecureItemDeleteExecutor = KeePassSecureItemDeleteExecutor(keepassBridge)
    private val keepassSecureItemUpdateExecutor = KeePassSecureItemUpdateExecutor(keepassBridge)
    private val bitwardenRepository = context?.let { BitwardenRepository.getInstance(it.applicationContext) }
    private val settingsManager = context?.let { SettingsManager(it.applicationContext) }
    
    // 搜索查询
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // 分类过滤器
    private val _categoryFilter = MutableStateFlow<TotpCategoryFilter>(TotpCategoryFilter.All)
    val categoryFilter: StateFlow<TotpCategoryFilter> = _categoryFilter.asStateFlow()

    init {
        restoreLastCategoryFilter()
        viewModelScope.launch {
            repairLegacyDetachedKeePassItems()
        }
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
        // 收集所有已存储的 TOTP 标识（归一化后去重，兼容 otpauth URI）
        val existingKeys = storedTotps.mapNotNull { item ->
            runCatching { Json.decodeFromString<TotpData>(item.itemData) }
                .getOrNull()
                ?.let(TotpDataResolver::normalizeTotpData)
                ?.let(::buildTotpIdentityKey)
        }.toSet()
        
        // 从密码的 authenticatorKey 生成虚拟 TOTP 项目
        val seenVirtualKeys = mutableSetOf<String>()
        val virtualTotps = allPasswords.mapNotNull { password ->
            val resolvedTotpData = resolvePasswordAuthenticatorTotp(password) ?: return@mapNotNull null
            val identityKey = buildTotpIdentityKey(resolvedTotpData)
            if (identityKey in existingKeys || !seenVirtualKeys.add(identityKey)) {
                return@mapNotNull null
            }

            SecureItem(
                id = -password.id, // 使用负ID标识这是虚拟项目
                itemType = ItemType.TOTP,
                title = password.title,
                notes = "来自密码: ${password.title}",
                itemData = Json.encodeToString(resolvedTotpData),
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
            is TotpCategoryFilter.Local -> allTotps.filter { it.isLocalOnlyItem() }
            is TotpCategoryFilter.Starred -> allTotps.filter { it.isFavorite }
            is TotpCategoryFilter.Uncategorized -> allTotps.filter { 
                it.categoryId == null && try {
                    Json.decodeFromString<TotpData>(it.itemData).categoryId == null
                } catch (e: Exception) { true }
            }
            is TotpCategoryFilter.LocalStarred -> allTotps.filter {
                it.isLocalOnlyItem() && it.isFavorite
            }
            is TotpCategoryFilter.LocalUncategorized -> allTotps.filter {
                it.isLocalOnlyItem() && (
                    it.categoryId == null && try {
                        Json.decodeFromString<TotpData>(it.itemData).categoryId == null
                    } catch (e: Exception) { true }
                )
            }
            is TotpCategoryFilter.Custom -> allTotps.filter { item ->
                item.isLocalOnlyItem() && (
                    item.categoryId == filter.categoryId || try {
                        Json.decodeFromString<TotpData>(item.itemData).categoryId == filter.categoryId
                    } catch (e: Exception) { false }
                )
            }
            is TotpCategoryFilter.KeePassDatabase -> allTotps.filter {
                (it.resolveOwnership() as? SecureItemOwnership.KeePass)?.databaseId == filter.databaseId
            }
            is TotpCategoryFilter.KeePassGroupFilter -> allTotps.filter {
                (it.resolveOwnership() as? SecureItemOwnership.KeePass)?.databaseId == filter.databaseId &&
                    it.keepassGroupPath == filter.groupPath
            }
            is TotpCategoryFilter.KeePassDatabaseStarred -> allTotps.filter {
                (it.resolveOwnership() as? SecureItemOwnership.KeePass)?.databaseId == filter.databaseId &&
                    it.isFavorite
            }
            is TotpCategoryFilter.KeePassDatabaseUncategorized -> allTotps.filter {
                (it.resolveOwnership() as? SecureItemOwnership.KeePass)?.databaseId == filter.databaseId &&
                    it.keepassGroupPath.isNullOrBlank()
            }
            is TotpCategoryFilter.BitwardenVault -> allTotps.filter {
                ((it.resolveOwnership() as? SecureItemOwnership.Bitwarden)?.vaultId == filter.vaultId) ||
                    (
                        it.bitwardenVaultId == null &&
                            !it.bitwardenFolderId.isNullOrBlank() &&
                            it.bitwardenFolderId in selectedVaultFolderIds
                        )
            }
            is TotpCategoryFilter.BitwardenFolderFilter -> allTotps.filter {
                it.hasBitwardenBinding() && it.bitwardenFolderId == filter.folderId
            }
            is TotpCategoryFilter.BitwardenVaultStarred -> allTotps.filter {
                (
                    (it.resolveOwnership() as? SecureItemOwnership.Bitwarden)?.vaultId == filter.vaultId ||
                        (
                            it.bitwardenVaultId == null &&
                                !it.bitwardenFolderId.isNullOrBlank() &&
                                it.bitwardenFolderId in selectedVaultFolderIds
                            )
                    ) && it.isFavorite
            }
            is TotpCategoryFilter.BitwardenVaultUncategorized -> allTotps.filter {
                (it.resolveOwnership() as? SecureItemOwnership.Bitwarden)?.vaultId == filter.vaultId &&
                    it.bitwardenFolderId == null
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

    private fun resolvePasswordAuthenticatorTotp(password: PasswordEntry): TotpData? {
        return TotpDataResolver.fromAuthenticatorKey(
            rawKey = password.authenticatorKey,
            fallbackIssuer = password.website.takeIf { it.isNotBlank() } ?: password.title,
            fallbackAccountName = password.username.takeIf { it.isNotBlank() } ?: password.title
        )?.copy(
            boundPasswordId = password.id,
            categoryId = password.categoryId
        )
    }

    private fun buildTotpIdentityKey(data: TotpData): String {
        val normalized = TotpDataResolver.normalizeTotpData(data)
        return listOf(
            normalized.otpType.name,
            normalized.secret,
            normalized.algorithm.uppercase(Locale.ROOT),
            normalized.digits.toString(),
            normalized.period.toString(),
            normalized.counter.toString()
        ).joinToString("|")
    }

    private fun buildTotpIdentityKeyFromRawKey(rawKey: String): String? {
        return TotpDataResolver.fromAuthenticatorKey(rawKey)?.let(::buildTotpIdentityKey)
    }

    suspend fun repairHistoricalBitwardenTotp(vaultId: Long): BitwardenTotpRepairResult {
        bitwardenRepository?.let { repo ->
            val result = repo.repairHistoricalBitwardenTotp(vaultId).getOrThrow()
            return BitwardenTotpRepairResult(
                normalizedTotpItems = result.normalizedTotpItems,
                queuedTotpItemsForSync = result.queuedTotpItemsForSync,
                normalizedPasswords = result.normalizedPasswords,
                queuedPasswordsForSync = result.queuedPasswordsForSync,
                skippedItems = result.skippedItems
            )
        }

        return repairHistoricalBitwardenTotpLocally(vaultId)
    }

    private suspend fun repairHistoricalBitwardenTotpLocally(vaultId: Long): BitwardenTotpRepairResult {
        val now = Date()
        var normalizedTotpItems = 0
        var queuedTotpItemsForSync = 0
        var normalizedPasswords = 0
        var queuedPasswordsForSync = 0
        var skippedItems = 0

        val secureItems = repository.getItemsByType(ItemType.TOTP).first()
        secureItems
            .asSequence()
            .filter { it.bitwardenVaultId == vaultId && !it.isDeleted }
            .forEach { item ->
                val normalizedData = TotpDataResolver.parseStoredItemData(
                    itemData = item.itemData,
                    fallbackIssuer = item.title
                )
                if (normalizedData == null) {
                    skippedItems += 1
                    return@forEach
                }

                val normalizedItemData = Json.encodeToString(normalizedData)
                val canSafelyQueueRemoteRepair =
                    item.itemData.contains("://", ignoreCase = true) ||
                        TotpDataResolver.hasNonDefaultOtpSettings(normalizedData)

                val updatedItem = item.copy(
                    itemData = normalizedItemData,
                    updatedAt = if (normalizedItemData != item.itemData || canSafelyQueueRemoteRepair) now else item.updatedAt,
                    bitwardenLocalModified = if (canSafelyQueueRemoteRepair && item.bitwardenCipherId != null) {
                        true
                    } else {
                        item.bitwardenLocalModified
                    },
                    syncStatus = if (canSafelyQueueRemoteRepair && item.bitwardenCipherId != null) {
                        "PENDING"
                    } else {
                        item.syncStatus
                    }
                )

                if (updatedItem != item) {
                    repository.updateItem(updatedItem)
                    if (normalizedItemData != item.itemData) {
                        normalizedTotpItems += 1
                    }
                    if (canSafelyQueueRemoteRepair && item.bitwardenCipherId != null) {
                        queuedTotpItemsForSync += 1
                    }
                }
            }

        val passwordEntries = passwordRepository.getAllPasswordEntries().first()
        passwordEntries
            .asSequence()
            .filter { it.bitwardenVaultId == vaultId && !it.isDeleted && it.authenticatorKey.isNotBlank() }
            .forEach { entry ->
                val normalizedTotp = TotpDataResolver.fromAuthenticatorKey(
                    rawKey = entry.authenticatorKey,
                    fallbackIssuer = entry.website.takeIf { it.isNotBlank() } ?: entry.title,
                    fallbackAccountName = entry.username.takeIf { it.isNotBlank() } ?: entry.title
                )
                if (normalizedTotp == null) {
                    skippedItems += 1
                    return@forEach
                }

                val normalizedPayload = TotpDataResolver.toBitwardenPayload(entry.title, normalizedTotp)
                val canSafelyQueueRemoteRepair =
                    entry.authenticatorKey.contains("://", ignoreCase = true) ||
                        TotpDataResolver.hasNonDefaultOtpSettings(normalizedTotp)

                val updatedEntry = entry.copy(
                    authenticatorKey = normalizedPayload,
                    updatedAt = if (normalizedPayload != entry.authenticatorKey || canSafelyQueueRemoteRepair) now else entry.updatedAt,
                    bitwardenLocalModified = if (canSafelyQueueRemoteRepair && entry.bitwardenCipherId != null) {
                        true
                    } else {
                        entry.bitwardenLocalModified
                    }
                )

                if (updatedEntry != entry) {
                    passwordRepository.updatePasswordEntry(updatedEntry)
                    if (normalizedPayload != entry.authenticatorKey) {
                        normalizedPasswords += 1
                    }
                    if (canSafelyQueueRemoteRepair && entry.bitwardenCipherId != null) {
                        queuedPasswordsForSync += 1
                    }
                }
            }

        return BitwardenTotpRepairResult(
            normalizedTotpItems = normalizedTotpItems,
            queuedTotpItemsForSync = queuedTotpItemsForSync,
            normalizedPasswords = normalizedPasswords,
            queuedPasswordsForSync = queuedPasswordsForSync,
            skippedItems = skippedItems
        )
    }

    fun syncKeePassByDatabaseId(databaseId: Long) {
        syncKeePassTotp(databaseId)
    }

    suspend fun getTotpById(id: Long): SecureItem? {
        val item = repository.getItemById(id) ?: return null
        return repository.normalizeLegacyDetachedKeePassItem(item, ::hasKeePassDatabase)
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

    private suspend fun repairLegacyDetachedKeePassItems() {
        repository.repairLegacyDetachedKeePassItems(::hasKeePassDatabase)
    }

    private suspend fun hasKeePassDatabase(databaseId: Long): Boolean {
        return localKeePassDatabaseDao?.getDatabaseById(databaseId) != null
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
            val snapshots = keepassBridge
                ?.readLegacySecureItems(databaseId, setOf(ItemType.TOTP))
                ?.getOrNull()
                ?: return@launch

            val existingTotp = repository.getItemsByType(ItemType.TOTP).first()
            snapshots.forEach { snapshot ->
                val incoming = snapshot.item
                val existingByUuid = incoming.keepassEntryUuid
                    ?.takeIf { it.isNotBlank() }
                    ?.let { repository.getItemByKeePassUuid(databaseId, it) }
                val existingBySource = snapshot.sourceMonicaId
                    ?.takeIf { it > 0 }
                    ?.let { sourceId -> repository.getItemById(sourceId) }
                    ?.takeIf { it.itemType == ItemType.TOTP }

                val existing = existingByUuid ?: existingBySource ?: existingTotp.firstOrNull {
                    it.itemType == ItemType.TOTP &&
                        it.keepassDatabaseId == databaseId &&
                        it.keepassGroupPath == incoming.keepassGroupPath &&
                        it.title == incoming.title
                }

                if (existing == null) {
                    repository.insertItem(incoming)
                } else {
                    val isInRecycleBin = snapshot.isInRecycleBin
                    repository.updateItem(
                        existing.copy(
                            title = incoming.title,
                            notes = incoming.notes,
                            itemData = incoming.itemData,
                            isFavorite = incoming.isFavorite,
                            imagePaths = incoming.imagePaths,
                            keepassDatabaseId = incoming.keepassDatabaseId,
                            keepassGroupPath = incoming.keepassGroupPath,
                            keepassEntryUuid = incoming.keepassEntryUuid,
                            keepassGroupUuid = incoming.keepassGroupUuid,
                            isDeleted = isInRecycleBin,
                            deletedAt = if (isInRecycleBin) (existing.deletedAt ?: Date()) else null,
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
        val targetKey = buildTotpIdentityKeyFromRawKey(secret) ?: return null
        return totpItems.value.find { item ->
            try {
                val data = Json.decodeFromString<TotpData>(item.itemData)
                buildTotpIdentityKey(data) == targetKey
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * 根据ID获取TOTP项目
     */
    suspend fun getTotpItemById(id: Long): SecureItem? {
        val item = repository.getItemById(id) ?: return null
        return repository.normalizeLegacyDetachedKeePassItem(item, ::hasKeePassDatabase)
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
        keepassGroupPath: String? = null,
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

                val shouldFollowBoundPassword = totpData.boundPasswordId != null
                val boundPassword = if (shouldFollowBoundPassword) {
                    totpData.boundPasswordId?.let { passwordRepository.getPasswordEntryById(it) }
                } else {
                    null
                }
                val resolvedKeepassDatabaseId = if (shouldFollowBoundPassword) {
                    boundPassword?.keepassDatabaseId ?: keepassDatabaseId
                } else {
                    keepassDatabaseId
                }
                val resolvedKeepassGroupPath = when {
                    resolvedKeepassDatabaseId == null -> null
                    shouldFollowBoundPassword -> boundPassword?.keepassGroupPath
                    else -> keepassGroupPath ?: existingItem?.keepassGroupPath
                }
                val keepassIdentity = resolveKeePassMutationIdentity(
                    existingItem = existingItem,
                    targetDatabaseId = resolvedKeepassDatabaseId,
                    requestedGroupPath = resolvedKeepassGroupPath
                )
                // TotpData 与 SecureItem 列字段保持同源，避免后续编辑把 KeePass 归属回写成“本地”。
                val updatedTotpData = totpData.copy(
                    categoryId = categoryId,
                    keepassDatabaseId = resolvedKeepassDatabaseId
                )
                val itemDataJson = Json.encodeToString(updatedTotpData)

                if (shouldFollowBoundPassword &&
                    existingItem != null &&
                    existingItem.bitwardenVaultId != null &&
                    !existingItem.bitwardenCipherId.isNullOrBlank()
                ) {
                    val queueResult = bitwardenRepository?.queueCipherDelete(
                        vaultId = existingItem.bitwardenVaultId,
                        cipherId = existingItem.bitwardenCipherId,
                        entryId = existingItem.id,
                        itemType = BitwardenPendingOperation.ITEM_TYPE_TOTP
                    )
                    if (queueResult?.isSuccess != true) {
                        Log.e(
                            "TotpViewModel",
                            "Queue Bitwarden delete failed: ${queueResult?.exceptionOrNull()?.message ?: "Bitwarden repository unavailable"}"
                        )
                        return@launch
                    }
                }

                val resolvedBitwardenVaultId = if (shouldFollowBoundPassword) null else bitwardenVaultId
                val resolvedBitwardenFolderId = if (shouldFollowBoundPassword) null else bitwardenFolderId
                 
                val item = if (id != null && id > 0) {
                    // 更新现有项目
                    existingItem?.copy(
                        title = title,
                        notes = notes,
                        itemData = itemDataJson,
                        categoryId = categoryId,
                        keepassDatabaseId = resolvedKeepassDatabaseId,
                        keepassGroupPath = keepassIdentity.groupPath,
                        keepassEntryUuid = keepassIdentity.entryUuid,
                        keepassGroupUuid = keepassIdentity.groupUuid,
                        bitwardenVaultId = resolvedBitwardenVaultId,
                        bitwardenFolderId = resolvedBitwardenFolderId,
                        bitwardenCipherId = if (shouldFollowBoundPassword) null else existingItem.bitwardenCipherId,
                        bitwardenRevisionDate = if (shouldFollowBoundPassword) null else existingItem.bitwardenRevisionDate,
                        bitwardenLocalModified = if (shouldFollowBoundPassword) {
                            false
                        } else {
                            existingItem.bitwardenCipherId != null && resolvedBitwardenVaultId != null
                        },
                        syncStatus = if (shouldFollowBoundPassword) {
                            "NONE"
                        } else if (resolvedBitwardenVaultId != null) {
                            if (existingItem.bitwardenCipherId != null) "PENDING" else existingItem.syncStatus
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
                        keepassDatabaseId = resolvedKeepassDatabaseId,
                        keepassGroupPath = keepassIdentity.groupPath,
                        keepassEntryUuid = keepassIdentity.entryUuid,
                        keepassGroupUuid = keepassIdentity.groupUuid,
                        bitwardenVaultId = resolvedBitwardenVaultId,
                        bitwardenFolderId = resolvedBitwardenFolderId,
                        syncStatus = if (resolvedBitwardenVaultId != null) "PENDING" else "NONE",
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
                    val newId = keepassSecureItemCreateExecutor.create(
                        item = item,
                        insertItem = repository::insertItem,
                        rollbackItem = repository::deleteItemById
                    ) ?: return@launch
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
                    val previousPasswordKey = previousPassword
                        ?.authenticatorKey
                        ?.let(::buildTotpIdentityKeyFromRawKey)
                    val previousTotpKey = buildTotpIdentityKeyFromRawKey(previousSecret)
                    if (previousPasswordKey != null && previousPasswordKey == previousTotpKey) {
                        passwordRepository.updateAuthenticatorKey(previousBoundId, "")
                    }
                }

                if (id != null && id > 0) {
                    val current = repository.getItemById(id)
                    if (current != null) {
                        keepassSecureItemUpdateExecutor.syncUpdatedItem(existingItem = existingItem, updatedItem = current)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // TODO: 处理错误
            }
        }
    }

    private fun resolveKeePassMutationIdentity(
        existingItem: SecureItem?,
        targetDatabaseId: Long?,
        requestedGroupPath: String?
    ): KeePassMutationIdentity {
        if (targetDatabaseId == null) {
            return KeePassMutationIdentity(
                groupPath = null,
                entryUuid = null,
                groupUuid = null
            )
        }

        val sameDatabase = existingItem?.keepassDatabaseId == targetDatabaseId
        val resolvedGroupPath = requestedGroupPath ?: if (sameDatabase) existingItem?.keepassGroupPath else null
        val groupUnchanged = sameDatabase && resolvedGroupPath == existingItem?.keepassGroupPath

        return KeePassMutationIdentity(
            groupPath = resolvedGroupPath,
            entryUuid = if (sameDatabase) {
                existingItem?.keepassEntryUuid ?: UUID.randomUUID().toString()
            } else {
                UUID.randomUUID().toString()
            },
            groupUuid = if (groupUnchanged) existingItem?.keepassGroupUuid else null
        )
    }

    /**
     * 解绑指定密码条目对应的验证器（不删除验证器本身）
     * 当密码页清空密钥时，仅解除绑定
     */
    fun unbindTotpFromPassword(passwordId: Long, secret: String? = null) {
        viewModelScope.launch {
            try {
                val items = repository.getItemsByType(ItemType.TOTP).first()
                val targetKey = secret
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::buildTotpIdentityKeyFromRawKey)
                val target = items.firstOrNull { item ->
                    val data = try {
                        Json.decodeFromString<TotpData>(item.itemData)
                    } catch (e: Exception) {
                        null
                    }
                    data?.let { parsed ->
                        parsed.boundPasswordId == passwordId &&
                            (targetKey == null || buildTotpIdentityKey(parsed) == targetKey)
                    } ?: false
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
            val totpData = try {
                Json.decodeFromString<TotpData>(item.itemData)
            } catch (e: Exception) {
                null
            }

            if (totpData?.boundPasswordId != null && totpData.secret.isNotBlank()) {
                val boundId = totpData.boundPasswordId
                val password = passwordRepository.getPasswordEntryById(boundId) ?: return@launch
                val passwordKey = buildTotpIdentityKeyFromRawKey(password.authenticatorKey)
                val itemKey = buildTotpIdentityKey(totpData)
                if (passwordKey != null && passwordKey == itemKey) {
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

            val vaultId = item.bitwardenVaultId
            val cipherId = item.bitwardenCipherId
            val isBitwardenCipher = vaultId != null && !cipherId.isNullOrBlank()

            if (isBitwardenCipher) {
                val queueResult = bitwardenRepository?.queueCipherDelete(
                    vaultId = vaultId!!,
                    cipherId = cipherId!!,
                    entryId = item.id,
                    itemType = BitwardenPendingOperation.ITEM_TYPE_TOTP
                )
                if (queueResult?.isFailure == true) {
                    Log.e("TotpViewModel", "Queue Bitwarden delete failed: ${queueResult.exceptionOrNull()?.message}")
                    return@launch
                }
            }

            if (!softDelete || isBitwardenCipher) {
                if (!keepassSecureItemDeleteExecutor.delete(item, useRecycleBin = softDelete || isBitwardenCipher)) {
                    Log.e("TotpViewModel", "KeePass delete failed for totp id=${item.id}")
                    return@launch
                }
            }

            if (isBitwardenCipher) {
                repository.updateItem(
                    item.copy(
                        isDeleted = true,
                        deletedAt = Date(),
                        updatedAt = Date(),
                        bitwardenLocalModified = true
                    )
                )
                OperationLogger.logDelete(
                    itemType = OperationLogItemType.TOTP,
                    itemId = item.id,
                    itemTitle = item.title,
                    detail = "移入回收站（待同步删除）"
                )
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

                if (!isBitwardenCipher && item.keepassDatabaseId != null) {
                    viewModelScope.launch keepassDeleteSync@{
                        if (keepassSecureItemDeleteExecutor.delete(item, useRecycleBin = true)) {
                            Log.i("TotpViewModel", "KeePass trash delete synced for totp id=${item.id}")
                            return@keepassDeleteSync
                        }

                        Log.e("TotpViewModel", "KeePass trash delete failed, reverting local trash state for totp id=${item.id}")
                        repository.updateItem(item.copy(updatedAt = Date()))
                    }
                }
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

    suspend fun copyTotpToMonicaLocal(
        item: SecureItem,
        categoryId: Long?
    ): Long? {
        if (item.itemType != ItemType.TOTP || item.hasOwnershipConflict()) return null
        val totpData = runCatching { Json.decodeFromString<TotpData>(item.itemData) }.getOrNull() ?: return null
        val detachedTotpData = totpData.copy(
            boundPasswordId = null,
            categoryId = categoryId,
            keepassDatabaseId = null
        )
        val localCopy = item.asMonicaLocalCopy(categoryId).copy(
            itemData = Json.encodeToString(detachedTotpData),
            createdAt = Date(),
            updatedAt = Date()
        )
        return repository.insertItem(localCopy)
    }

    suspend fun moveTotpToMonicaLocal(
        item: SecureItem,
        categoryId: Long?
    ): Result<Long> {
        if (item.itemType != ItemType.TOTP) {
            return Result.failure(IllegalArgumentException("仅支持验证器项目"))
        }
        if (item.hasOwnershipConflict()) {
            return Result.failure(IllegalStateException("验证器来源冲突，无法移动到 Monica 本地"))
        }

        val newId = copyTotpToMonicaLocal(item, categoryId)
            ?: return Result.failure(IllegalStateException("创建 Monica 本地验证器副本失败"))

        val sourceDelete = when (val ownership = item.resolveOwnership()) {
            is SecureItemOwnership.Bitwarden -> {
                val vaultId = ownership.vaultId
                val cipherId = ownership.cipherId
                if (vaultId == null || cipherId.isNullOrBlank()) {
                    Result.failure(IllegalStateException("Bitwarden 验证器缺少同步标识"))
                } else {
                    bitwardenRepository?.queueCipherDelete(
                        vaultId = vaultId,
                        cipherId = cipherId,
                        entryId = item.id,
                        itemType = BitwardenPendingOperation.ITEM_TYPE_TOTP
                    ) ?: Result.failure(IllegalStateException("Bitwarden 仓库不可用"))
                }
            }
            is SecureItemOwnership.KeePass -> {
                if (keepassSecureItemDeleteExecutor.delete(item, useRecycleBin = false)) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("KeePass 验证器源删除失败"))
                }
            }
            is SecureItemOwnership.MonicaLocal -> Result.success(Unit)
            is SecureItemOwnership.Conflict -> Result.failure(IllegalStateException("验证器来源冲突，无法移动到 Monica 本地"))
        }

        if (sourceDelete.isFailure) {
            repository.deleteItemById(newId)
            return Result.failure(
                sourceDelete.exceptionOrNull() ?: IllegalStateException("删除验证器源失败")
            )
        }

        repository.deleteItem(item)
        return Result.success(newId)
    }

    fun moveToKeePassDatabase(ids: List<Long>, databaseId: Long?) {
        viewModelScope.launch {
            ids.forEach { id ->
                try {
                    val item = repository.getItemById(id) ?: return@forEach
                    val totpData = runCatching { Json.decodeFromString<TotpData>(item.itemData) }.getOrNull() ?: return@forEach
                    val updatedData = totpData.copy(keepassDatabaseId = databaseId)
                    val keepassIdentity = resolveKeePassMutationIdentity(
                        existingItem = item,
                        targetDatabaseId = databaseId,
                        requestedGroupPath = null
                    )
                    val updatedItem = item.copy(
                        itemData = Json.encodeToString(updatedData),
                        keepassDatabaseId = databaseId,
                        keepassGroupPath = keepassIdentity.groupPath,
                        keepassEntryUuid = keepassIdentity.entryUuid,
                        keepassGroupUuid = keepassIdentity.groupUuid,
                        bitwardenVaultId = null,
                        bitwardenFolderId = null,
                        bitwardenLocalModified = false,
                        syncStatus = "NONE",
                        updatedAt = Date()
                    )
                    repository.updateItem(updatedItem)
                    keepassSecureItemUpdateExecutor.syncUpdatedItem(existingItem = item, updatedItem = updatedItem)
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
                    val keepassIdentity = resolveKeePassMutationIdentity(
                        existingItem = item,
                        targetDatabaseId = databaseId,
                        requestedGroupPath = groupPath
                    )
                    val updatedItem = item.copy(
                        itemData = Json.encodeToString(updatedData),
                        keepassDatabaseId = databaseId,
                        keepassGroupPath = keepassIdentity.groupPath,
                        keepassEntryUuid = keepassIdentity.entryUuid,
                        keepassGroupUuid = keepassIdentity.groupUuid,
                        bitwardenVaultId = null,
                        bitwardenFolderId = null,
                        bitwardenLocalModified = false,
                        syncStatus = "NONE",
                        updatedAt = Date()
                    )
                    repository.updateItem(updatedItem)
                    keepassSecureItemUpdateExecutor.syncUpdatedItem(existingItem = item, updatedItem = updatedItem)
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
                    val totpData = runCatching { Json.decodeFromString<TotpData>(item.itemData) }.getOrNull() ?: return@forEach
                    val updatedData = totpData.copy(keepassDatabaseId = null)
                    val keepassIdentity = resolveKeePassMutationIdentity(
                        existingItem = item,
                        targetDatabaseId = null,
                        requestedGroupPath = null
                    )
                    val updatedItem = item.copy(
                        itemData = Json.encodeToString(updatedData),
                        keepassDatabaseId = null,
                        keepassGroupPath = keepassIdentity.groupPath,
                        keepassEntryUuid = keepassIdentity.entryUuid,
                        keepassGroupUuid = keepassIdentity.groupUuid,
                        bitwardenVaultId = vaultId,
                        bitwardenFolderId = folderId,
                        bitwardenLocalModified = item.bitwardenCipherId != null,
                        syncStatus = if (item.bitwardenCipherId != null) "PENDING" else item.syncStatus,
                        updatedAt = Date()
                    )
                    repository.updateItem(updatedItem)
                    keepassSecureItemUpdateExecutor.syncUpdatedItem(existingItem = item, updatedItem = updatedItem)
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
