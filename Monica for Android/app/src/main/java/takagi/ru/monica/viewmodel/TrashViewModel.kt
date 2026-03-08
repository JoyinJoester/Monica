package takagi.ru.monica.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.bitwarden.BitwardenPendingOperation
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.KeePassKdbxService
import takagi.ru.monica.utils.SettingsManager
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * 回收站中的条目数据类
 */
data class TrashItem(
    val id: Long,
    val title: String,
    val itemType: ItemType,
    val deletedAt: Date,
    val daysRemaining: Int,  // 剩余天数（-1表示不自动清空）
    val originalData: Any  // PasswordEntry 或 SecureItem
)

/**
 * 回收站分类数据类
 */
data class TrashCategory(
    val type: ItemType,
    val displayName: String,
    val count: Int,
    val items: List<TrashItem>
)

/**
 * 回收站 ViewModel
 */
class TrashViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = PasswordDatabase.getDatabase(application)
    private val passwordRepository = PasswordRepository(database.passwordEntryDao())
    private val secureItemRepository = SecureItemRepository(database.secureItemDao())
    private val bitwardenRepository = BitwardenRepository.getInstance(application)
    private val securityManager = SecurityManager(application)
    private val keepassService = KeePassKdbxService(application, database.localKeePassDatabaseDao(), securityManager)
    private val settingsManager = SettingsManager(application)
    
    // 回收站设置
    val trashSettings = settingsManager.settingsFlow.map { settings ->
        TrashSettings(
            enabled = settings.trashEnabled,
            autoDeleteDays = settings.trashAutoDeleteDays
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TrashSettings()
    )
    
    init {
        // 应用启动时自动清理过期的回收站条目
        viewModelScope.launch {
            // 等待设置加载完成
            trashSettings.first { it.autoDeleteDays >= 0 }
            cleanupExpiredItems()
        }
    }
    
    // 已删除的密码条目
    private val deletedPasswords: Flow<List<PasswordEntry>> = 
        database.passwordEntryDao().getDeletedEntries()
    
    // 已删除的安全项目
    private val deletedSecureItems: Flow<List<SecureItem>> = 
        database.secureItemDao().getDeletedItems()
    
    // 合并所有已删除项目并按类型分组
    val trashCategories: StateFlow<List<TrashCategory>> = combine(
        deletedPasswords,
        deletedSecureItems,
        trashSettings
    ) { passwords, secureItems, settings ->
        val now = Date()
        val categories = mutableListOf<TrashCategory>()
        
        // 密码类别
        if (passwords.isNotEmpty()) {
            val passwordItems = passwords.map { entry ->
                val daysRemaining = if (settings.autoDeleteDays > 0 && entry.deletedAt != null) {
                    val daysSinceDelete = TimeUnit.MILLISECONDS.toDays(now.time - entry.deletedAt.time).toInt()
                    maxOf(0, settings.autoDeleteDays - daysSinceDelete)
                } else -1
                
                TrashItem(
                    id = entry.id,
                    title = entry.title,
                    itemType = ItemType.PASSWORD,
                    deletedAt = entry.deletedAt ?: now,
                    daysRemaining = daysRemaining,
                    originalData = entry
                )
            }
            categories.add(TrashCategory(
                type = ItemType.PASSWORD,
                displayName = "密码",
                count = passwordItems.size,
                items = passwordItems
            ))
        }
        
        // 按 SecureItem 类型分组
        val groupedSecureItems = secureItems.groupBy { it.itemType }
        
        // 验证器类别
        groupedSecureItems[ItemType.TOTP]?.let { totpItems ->
            val items = totpItems.map { item ->
                val daysRemaining = if (settings.autoDeleteDays > 0 && item.deletedAt != null) {
                    val daysSinceDelete = TimeUnit.MILLISECONDS.toDays(now.time - item.deletedAt.time).toInt()
                    maxOf(0, settings.autoDeleteDays - daysSinceDelete)
                } else -1
                
                TrashItem(
                    id = item.id,
                    title = item.title,
                    itemType = ItemType.TOTP,
                    deletedAt = item.deletedAt ?: now,
                    daysRemaining = daysRemaining,
                    originalData = item
                )
            }
            categories.add(TrashCategory(
                type = ItemType.TOTP,
                displayName = "验证器",
                count = items.size,
                items = items
            ))
        }
        
        // 银行卡类别
        groupedSecureItems[ItemType.BANK_CARD]?.let { cardItems ->
            val items = cardItems.map { item ->
                val daysRemaining = if (settings.autoDeleteDays > 0 && item.deletedAt != null) {
                    val daysSinceDelete = TimeUnit.MILLISECONDS.toDays(now.time - item.deletedAt.time).toInt()
                    maxOf(0, settings.autoDeleteDays - daysSinceDelete)
                } else -1
                
                TrashItem(
                    id = item.id,
                    title = item.title,
                    itemType = ItemType.BANK_CARD,
                    deletedAt = item.deletedAt ?: now,
                    daysRemaining = daysRemaining,
                    originalData = item
                )
            }
            categories.add(TrashCategory(
                type = ItemType.BANK_CARD,
                displayName = "银行卡",
                count = items.size,
                items = items
            ))
        }
        
        // 证件类别
        groupedSecureItems[ItemType.DOCUMENT]?.let { docItems ->
            val items = docItems.map { item ->
                val daysRemaining = if (settings.autoDeleteDays > 0 && item.deletedAt != null) {
                    val daysSinceDelete = TimeUnit.MILLISECONDS.toDays(now.time - item.deletedAt.time).toInt()
                    maxOf(0, settings.autoDeleteDays - daysSinceDelete)
                } else -1
                
                TrashItem(
                    id = item.id,
                    title = item.title,
                    itemType = ItemType.DOCUMENT,
                    deletedAt = item.deletedAt ?: now,
                    daysRemaining = daysRemaining,
                    originalData = item
                )
            }
            categories.add(TrashCategory(
                type = ItemType.DOCUMENT,
                displayName = "证件",
                count = items.size,
                items = items
            ))
        }
        
        // 笔记类别
        groupedSecureItems[ItemType.NOTE]?.let { noteItems ->
            val items = noteItems.map { item ->
                val daysRemaining = if (settings.autoDeleteDays > 0 && item.deletedAt != null) {
                    val daysSinceDelete = TimeUnit.MILLISECONDS.toDays(now.time - item.deletedAt.time).toInt()
                    maxOf(0, settings.autoDeleteDays - daysSinceDelete)
                } else -1
                
                TrashItem(
                    id = item.id,
                    title = item.title,
                    itemType = ItemType.NOTE,
                    deletedAt = item.deletedAt ?: now,
                    daysRemaining = daysRemaining,
                    originalData = item
                )
            }
            categories.add(TrashCategory(
                type = ItemType.NOTE,
                displayName = "笔记",
                count = items.size,
                items = items
            ))
        }
        
        categories
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    // 回收站总条目数
    val totalTrashCount: StateFlow<Int> = trashCategories.map { categories ->
        categories.sumOf { it.count }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )
    
    /**
     * 恢复已删除的条目
     */
    fun restoreItem(item: TrashItem, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                if (!queueRemoteRestoreIfNeeded(item.originalData)) {
                    onResult(false)
                    return@launch
                }
                val keepassRestorePath = restoreKeepassIfNeeded(item.originalData).getOrElse {
                    onResult(false)
                    return@launch
                }
                when (item.originalData) {
                    is PasswordEntry -> {
                        val restoredPath = if (item.originalData.keepassDatabaseId != null) {
                            keepassRestorePath
                        } else {
                            item.originalData.keepassGroupPath
                        }
                        val restored = buildRestoredPasswordEntry(item.originalData, restoredPath)
                        database.passwordEntryDao().update(restored)
                    }
                    is SecureItem -> {
                        val restoredPath = if (item.originalData.keepassDatabaseId != null) {
                            keepassRestorePath
                        } else {
                            item.originalData.keepassGroupPath
                        }
                        val restored = buildRestoredSecureItem(item.originalData, restoredPath)
                        database.secureItemDao().update(restored)
                    }
                }
                onResult(true)
            } catch (e: Exception) {
                android.util.Log.e("TrashViewModel", "Failed to restore item", e)
                onResult(false)
            }
        }
    }
    
    /**
     * 永久删除条目
     */
    fun permanentlyDeleteItem(item: TrashItem, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                if (!permanentlyDeleteWithSources(item.originalData)) {
                    onResult(false)
                    return@launch
                }
                onResult(true)
            } catch (e: Exception) {
                android.util.Log.e("TrashViewModel", "Failed to permanently delete item", e)
                onResult(false)
            }
        }
    }
    
    /**
     * 恢复某个类别的所有条目
     */
    fun restoreCategory(category: TrashCategory, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val queuedPasswords = mutableListOf<PasswordEntry>()
                val queuedSecureItems = mutableListOf<SecureItem>()
                val failedItemIds = mutableSetOf<Long>()

                category.items.forEach { item ->
                    if (!queueRemoteRestoreIfNeeded(item.originalData)) {
                        failedItemIds += item.id
                        return@forEach
                    }
                    when (val data = item.originalData) {
                        is PasswordEntry -> queuedPasswords += data
                        is SecureItem -> queuedSecureItems += data
                    }
                }

                val keepassBatchResult = restoreKeepassBatchIfNeeded(
                    passwords = queuedPasswords,
                    secureItems = queuedSecureItems
                )
                failedItemIds += keepassBatchResult.failedIds

                val restoredPasswordUpdates = mutableListOf<PasswordEntry>()
                queuedPasswords.forEach { entry ->
                    if (entry.id in failedItemIds) return@forEach
                    val restoredPath = if (entry.keepassDatabaseId != null) {
                        keepassBatchResult.passwordGroupPaths[entry.id]
                    } else {
                        entry.keepassGroupPath
                    }
                    if (entry.keepassDatabaseId != null && !keepassBatchResult.passwordGroupPaths.containsKey(entry.id)) {
                        failedItemIds += entry.id
                        return@forEach
                    }
                    restoredPasswordUpdates += buildRestoredPasswordEntry(entry, restoredPath)
                }

                if (restoredPasswordUpdates.isNotEmpty()) {
                    runCatching {
                        database.passwordEntryDao().updateAll(restoredPasswordUpdates)
                    }.onFailure { batchError ->
                        android.util.Log.e(
                            "TrashViewModel",
                            "Batch update restored passwords failed, fallback to per-item update",
                            batchError
                        )
                        restoredPasswordUpdates.forEach { restored ->
                            runCatching {
                                database.passwordEntryDao().update(restored)
                            }.onFailure {
                                android.util.Log.e("TrashViewModel", "Failed to update restored password id=${restored.id}", it)
                                failedItemIds += restored.id
                            }
                        }
                    }
                }

                val restoredSecureItemUpdates = mutableListOf<SecureItem>()
                queuedSecureItems.forEach { item ->
                    if (item.id in failedItemIds) return@forEach
                    val restoredPath = if (item.keepassDatabaseId != null) {
                        keepassBatchResult.secureItemGroupPaths[item.id]
                    } else {
                        item.keepassGroupPath
                    }
                    if (item.keepassDatabaseId != null && !keepassBatchResult.secureItemGroupPaths.containsKey(item.id)) {
                        failedItemIds += item.id
                        return@forEach
                    }
                    restoredSecureItemUpdates += buildRestoredSecureItem(item, restoredPath)
                }

                if (restoredSecureItemUpdates.isNotEmpty()) {
                    runCatching {
                        database.secureItemDao().updateAll(restoredSecureItemUpdates)
                    }.onFailure { batchError ->
                        android.util.Log.e(
                            "TrashViewModel",
                            "Batch update restored secure items failed, fallback to per-item update",
                            batchError
                        )
                        restoredSecureItemUpdates.forEach { restored ->
                            runCatching {
                                database.secureItemDao().update(restored)
                            }.onFailure {
                                android.util.Log.e("TrashViewModel", "Failed to update restored secure item id=${restored.id}", it)
                                failedItemIds += restored.id
                            }
                        }
                    }
                }

                onResult(failedItemIds.isEmpty())
            } catch (e: Exception) {
                android.util.Log.e("TrashViewModel", "Failed to restore category", e)
                onResult(false)
            }
        }
    }
    
    /**
     * 清空回收站
     */
    fun emptyTrash(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                var hasFailure = false

                val deletedPasswords = database.passwordEntryDao().getDeletedEntriesSync()
                deletedPasswords.forEach { entry ->
                    if (!permanentlyDeleteWithSources(entry)) {
                        hasFailure = true
                    }
                }

                val deletedSecureItems = database.secureItemDao().getDeletedItemsSync()
                deletedSecureItems.forEach { item ->
                    if (!permanentlyDeleteWithSources(item)) {
                        hasFailure = true
                    }
                }

                onResult(!hasFailure)
            } catch (e: Exception) {
                android.util.Log.e("TrashViewModel", "Failed to empty trash", e)
                onResult(false)
            }
        }
    }
    
    /**
     * 清理过期的回收站条目（根据设置的自动清空天数）
     */
    fun cleanupExpiredItems() {
        viewModelScope.launch {
            val settings = trashSettings.value
            if (settings.autoDeleteDays <= 0) return@launch
            
            val cutoffDate = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(settings.autoDeleteDays.toLong()))
            
            try {
                val expiredPasswords = database.passwordEntryDao()
                    .getDeletedEntriesSync()
                    .filter { it.deletedAt != null && it.deletedAt < cutoffDate }

                expiredPasswords.forEach { entry ->
                    permanentlyDeleteWithSources(entry)
                }

                val expiredSecureItems = database.secureItemDao()
                    .getDeletedItemsSync()
                    .filter { it.deletedAt != null && it.deletedAt < cutoffDate }

                expiredSecureItems.forEach { item ->
                    permanentlyDeleteWithSources(item)
                }
            } catch (e: Exception) {
                android.util.Log.e("TrashViewModel", "Failed to cleanup expired items", e)
            }
        }
    }

    private fun buildRestoredPasswordEntry(entry: PasswordEntry, restoredKeepassGroupPath: String?): PasswordEntry {
        return entry.copy(
            isDeleted = false,
            deletedAt = null,
            updatedAt = Date(),
            bitwardenLocalModified = false,
            keepassGroupPath = restoredKeepassGroupPath
        )
    }

    private fun buildRestoredSecureItem(item: SecureItem, restoredKeepassGroupPath: String?): SecureItem {
        return item.copy(
            isDeleted = false,
            deletedAt = null,
            updatedAt = Date(),
            bitwardenLocalModified = false,
            keepassGroupPath = restoredKeepassGroupPath
        )
    }

    private suspend fun restoreKeepassIfNeeded(data: Any): Result<String?> {
        return when (data) {
            is PasswordEntry -> {
                val keepassId = data.keepassDatabaseId ?: return Result.success(data.keepassGroupPath)
                val restoredGroupPath = keepassService.resolveRestoreGroupPathForPassword(
                    databaseId = keepassId,
                    target = data.copy(keepassDatabaseId = keepassId)
                ).getOrElse { return Result.failure(it) }
                val restoredForKeepass = buildRestoredPasswordEntry(data, restoredGroupPath).copy(
                    keepassDatabaseId = keepassId,
                    keepassGroupPath = restoredGroupPath
                )
                val addResult = keepassService.addOrUpdatePasswordEntries(
                    databaseId = keepassId,
                    entries = listOf(restoredForKeepass),
                    resolvePassword = { entry ->
                        runCatching { securityManager.decryptData(entry.password) }
                            .getOrElse { entry.password }
                    }
                )
                if (addResult.isFailure) return Result.failure(addResult.exceptionOrNull() ?: IllegalStateException("KeePass add restore failed"))
                Result.success(restoredGroupPath)
            }
            is SecureItem -> {
                val keepassId = data.keepassDatabaseId ?: return Result.success(data.keepassGroupPath)
                val restoredGroupPath = keepassService.resolveRestoreGroupPathForSecureItem(
                    databaseId = keepassId,
                    target = data.copy(keepassDatabaseId = keepassId)
                ).getOrElse { return Result.failure(it) }
                val restoredForKeepass = buildRestoredSecureItem(data, restoredGroupPath).copy(
                    keepassDatabaseId = keepassId,
                    keepassGroupPath = restoredGroupPath
                )
                val addResult = keepassService.addOrUpdateSecureItems(
                    keepassId,
                    listOf(restoredForKeepass)
                )
                if (addResult.isFailure) return Result.failure(addResult.exceptionOrNull() ?: IllegalStateException("KeePass add restore failed"))
                Result.success(restoredGroupPath)
            }
            else -> Result.success(null)
        }
    }

    private data class KeepassBatchRestoreResult(
        val passwordGroupPaths: Map<Long, String?>,
        val secureItemGroupPaths: Map<Long, String?>,
        val failedIds: Set<Long>
    )

    private suspend fun restoreKeepassBatchIfNeeded(
        passwords: List<PasswordEntry>,
        secureItems: List<SecureItem>
    ): KeepassBatchRestoreResult {
        val restoredPasswordPaths = mutableMapOf<Long, String?>()
        val restoredSecurePaths = mutableMapOf<Long, String?>()
        val failedIds = mutableSetOf<Long>()

        // Local-only items are considered restored in place.
        passwords.filter { it.keepassDatabaseId == null }.forEach { restoredPasswordPaths[it.id] = it.keepassGroupPath }
        secureItems.filter { it.keepassDatabaseId == null }.forEach { restoredSecurePaths[it.id] = it.keepassGroupPath }

        val groupedPasswords = passwords
            .filter { it.keepassDatabaseId != null }
            .groupBy { it.keepassDatabaseId!! }
        groupedPasswords.forEach { (databaseId, entries) ->
            val restoredEntries = mutableListOf<PasswordEntry>()
            entries.forEach entryLoop@{ entry ->
                val restorePath = keepassService.resolveRestoreGroupPathForPassword(
                    databaseId = databaseId,
                    target = entry.copy(keepassDatabaseId = databaseId)
                ).getOrElse {
                    android.util.Log.e(
                        "TrashViewModel",
                        "Resolve KeePass restore path failed for password id=${entry.id}, db=$databaseId",
                        it
                    )
                    failedIds += entry.id
                    return@entryLoop
                }
                restoredEntries += buildRestoredPasswordEntry(entry, restorePath).copy(
                    keepassDatabaseId = databaseId,
                    keepassGroupPath = restorePath
                )
            }

            if (restoredEntries.isEmpty()) return@forEach

            val addResult = keepassService.addOrUpdatePasswordEntries(
                databaseId = databaseId,
                entries = restoredEntries,
                resolvePassword = { entry ->
                    runCatching { securityManager.decryptData(entry.password) }
                        .getOrElse { entry.password }
                }
            )
            if (addResult.isFailure) {
                android.util.Log.e(
                    "TrashViewModel",
                    "KeePass batch restore failed for passwords db=$databaseId",
                    addResult.exceptionOrNull()
                )
                failedIds += entries.map { it.id }
                return@forEach
            }

            restoredEntries.forEach { restored ->
                restoredPasswordPaths[restored.id] = restored.keepassGroupPath
            }
        }

        val groupedSecureItems = secureItems
            .filter { it.keepassDatabaseId != null }
            .groupBy { it.keepassDatabaseId!! }
        groupedSecureItems.forEach { (databaseId, items) ->
            val restoredItems = mutableListOf<SecureItem>()
            items.forEach itemLoop@{ item ->
                val restorePath = keepassService.resolveRestoreGroupPathForSecureItem(
                    databaseId = databaseId,
                    target = item.copy(keepassDatabaseId = databaseId)
                ).getOrElse {
                    android.util.Log.e(
                        "TrashViewModel",
                        "Resolve KeePass restore path failed for secure item id=${item.id}, db=$databaseId",
                        it
                    )
                    failedIds += item.id
                    return@itemLoop
                }
                restoredItems += buildRestoredSecureItem(item, restorePath).copy(
                    keepassDatabaseId = databaseId,
                    keepassGroupPath = restorePath
                )
            }

            if (restoredItems.isEmpty()) return@forEach

            val addResult = keepassService.addOrUpdateSecureItems(
                databaseId = databaseId,
                items = restoredItems
            )
            if (addResult.isFailure) {
                android.util.Log.e(
                    "TrashViewModel",
                    "KeePass batch restore failed for secure items db=$databaseId",
                    addResult.exceptionOrNull()
                )
                failedIds += items.map { it.id }
                return@forEach
            }

            restoredItems.forEach { restored ->
                restoredSecurePaths[restored.id] = restored.keepassGroupPath
            }
        }

        return KeepassBatchRestoreResult(
            passwordGroupPaths = restoredPasswordPaths,
            secureItemGroupPaths = restoredSecurePaths,
            failedIds = failedIds
        )
    }

    private suspend fun deleteKeepassEntryIfNeeded(data: Any): Boolean {
        return when (data) {
            is PasswordEntry -> {
                val keepassId = data.keepassDatabaseId ?: return true
                keepassService.deletePasswordEntries(
                    keepassId,
                    listOf(data.copy(keepassDatabaseId = keepassId))
                ).isSuccess
            }
            is SecureItem -> {
                val keepassId = data.keepassDatabaseId ?: return true
                keepassService.deleteSecureItems(
                    keepassId,
                    listOf(data.copy(keepassDatabaseId = keepassId))
                ).isSuccess
            }
            else -> true
        }
    }

    private suspend fun permanentlyDeleteWithSources(data: Any): Boolean {
        if (!deleteRemoteCipherIfNeeded(data)) return false
        if (!deleteKeepassEntryIfNeeded(data)) return false
        when (data) {
            is PasswordEntry -> database.passwordEntryDao().delete(data)
            is SecureItem -> database.secureItemDao().delete(data)
        }
        return true
    }

    private suspend fun deleteRemoteCipherIfNeeded(data: Any): Boolean {
        return when (data) {
            is PasswordEntry -> {
                val vaultId = data.bitwardenVaultId
                val cipherId = data.bitwardenCipherId
                if (vaultId != null && !cipherId.isNullOrBlank()) {
                    bitwardenRepository.permanentDeleteCipher(vaultId, cipherId).isSuccess
                } else {
                    true
                }
            }
            is SecureItem -> {
                val vaultId = data.bitwardenVaultId
                val cipherId = data.bitwardenCipherId
                if (vaultId != null && !cipherId.isNullOrBlank()) {
                    bitwardenRepository.permanentDeleteCipher(vaultId, cipherId).isSuccess
                } else {
                    true
                }
            }
            else -> true
        }
    }

    private suspend fun queueRemoteRestoreIfNeeded(data: Any): Boolean {
        return when (data) {
            is PasswordEntry -> {
                val vaultId = data.bitwardenVaultId
                val cipherId = data.bitwardenCipherId
                if (vaultId != null && !cipherId.isNullOrBlank()) {
                    bitwardenRepository.queueCipherRestore(
                        vaultId = vaultId,
                        cipherId = cipherId,
                        entryId = data.id,
                        itemType = BitwardenPendingOperation.ITEM_TYPE_PASSWORD
                    ).isSuccess
                } else {
                    true
                }
            }
            is SecureItem -> {
                val vaultId = data.bitwardenVaultId
                val cipherId = data.bitwardenCipherId
                if (vaultId != null && !cipherId.isNullOrBlank()) {
                    bitwardenRepository.queueCipherRestore(
                        vaultId = vaultId,
                        cipherId = cipherId,
                        entryId = data.id,
                        itemType = data.itemType.toPendingItemType()
                    ).isSuccess
                } else {
                    true
                }
            }
            else -> true
        }
    }

    private fun ItemType.toPendingItemType(): String = when (this) {
        ItemType.PASSWORD -> BitwardenPendingOperation.ITEM_TYPE_PASSWORD
        ItemType.TOTP -> BitwardenPendingOperation.ITEM_TYPE_TOTP
        ItemType.BANK_CARD -> BitwardenPendingOperation.ITEM_TYPE_CARD
        ItemType.DOCUMENT -> BitwardenPendingOperation.ITEM_TYPE_DOCUMENT
        ItemType.NOTE -> BitwardenPendingOperation.ITEM_TYPE_NOTE
    }
    
    /**
     * 更新回收站设置
     */
    fun updateTrashSettings(enabled: Boolean, autoDeleteDays: Int) {
        viewModelScope.launch {
            settingsManager.updateTrashEnabled(enabled)
            settingsManager.updateTrashAutoDeleteDays(autoDeleteDays)
        }
    }
}

/**
 * 回收站设置数据类
 */
data class TrashSettings(
    val enabled: Boolean = true,
    val autoDeleteDays: Int = 30  // 0 = 不自动清空, -1 = 禁用回收站
)
