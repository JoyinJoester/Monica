package takagi.ru.monica.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.bitwarden.BitwardenRestoreQueueOutcome
import takagi.ru.monica.bitwarden.BitwardenTrashRestoreStateHelper
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.bitwarden.BitwardenPendingOperation
import takagi.ru.monica.repository.KeePassCompatibilityBridge
import takagi.ru.monica.repository.KeePassWorkspaceRepository
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.data.OperationLogItemType
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.FieldChange
import takagi.ru.monica.utils.KeePassRestoreTarget
import takagi.ru.monica.utils.OperationLogger
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
    private val keepassBridge = KeePassCompatibilityBridge(
        KeePassWorkspaceRepository(application, database.localKeePassDatabaseDao(), securityManager)
    )
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
            var restoreOutcome = BitwardenRestoreQueueOutcome.NO_REMOTE_ACTION
            try {
                restoreOutcome = queueRemoteRestoreIfNeeded(item.originalData).getOrElse {
                    android.util.Log.e("TrashViewModel", "Queue remote restore failed for item id=${item.id}", it)
                    onResult(false)
                    return@launch
                }
                applyLocalRestore(
                    item.originalData,
                    restoreOutcome = restoreOutcome
                )
                onResult(true)

                if (!needsKeepassRestore(item.originalData)) {
                    logTrashRestore(item)
                    return@launch
                }
            } catch (e: Exception) {
                android.util.Log.e("TrashViewModel", "Failed to restore item", e)
                onResult(false)
                return@launch
            }

            viewModelScope.launch keepassRestoreSync@{
                val keepassRestoreTarget = restoreKeepassIfNeeded(item.originalData).getOrElse {
                    android.util.Log.e("TrashViewModel", "KeePass restore failed for item id=${item.id}, rolling back local restore", it)
                    rollbackLocalRestore(item.originalData)
                    return@keepassRestoreSync
                }
                applyLocalRestore(
                    item.originalData,
                    restoreTarget = keepassRestoreTarget,
                    restoreOutcome = restoreOutcome
                )
                logTrashRestore(item)
                android.util.Log.i("TrashViewModel", "KeePass restore synced: id=${item.id}, type=${item.itemType}")
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
                logTrashPermanentDelete(item)
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
            val queuedPasswords = mutableListOf<PasswordEntry>()
            val queuedSecureItems = mutableListOf<SecureItem>()
            val restoreOutcomes = mutableMapOf<Long, BitwardenRestoreQueueOutcome>()
            val failedItemIds = mutableSetOf<Long>()
            var keepassPasswords: List<PasswordEntry> = emptyList()
            var keepassSecureItems: List<SecureItem> = emptyList()

            try {
                category.items.forEach { item ->
                    val restoreOutcome = queueRemoteRestoreIfNeeded(item.originalData).getOrNull()
                    if (restoreOutcome == null) {
                        failedItemIds += item.id
                        return@forEach
                    }
                    restoreOutcomes[item.id] = restoreOutcome
                    when (val data = item.originalData) {
                        is PasswordEntry -> queuedPasswords += data
                        is SecureItem -> queuedSecureItems += data
                    }
                }

                queuedPasswords
                    .filterNot { it.id in failedItemIds }
                    .forEach { entry ->
                        applyLocalRestore(
                            entry,
                            restoreOutcome = restoreOutcomes[entry.id]
                                ?: BitwardenRestoreQueueOutcome.NO_REMOTE_ACTION
                        )
                    }
                queuedSecureItems
                    .filterNot { it.id in failedItemIds }
                    .forEach { item ->
                        applyLocalRestore(
                            item,
                            restoreOutcome = restoreOutcomes[item.id]
                                ?: BitwardenRestoreQueueOutcome.NO_REMOTE_ACTION
                        )
                    }

                onResult(failedItemIds.isEmpty())

                keepassPasswords = queuedPasswords.filterNot { it.id in failedItemIds || it.keepassDatabaseId == null }
                keepassSecureItems = queuedSecureItems.filterNot { it.id in failedItemIds || it.keepassDatabaseId == null }
                if (keepassPasswords.isEmpty() && keepassSecureItems.isEmpty()) {
                    return@launch
                }
            } catch (e: Exception) {
                android.util.Log.e("TrashViewModel", "Failed to restore category", e)
                onResult(false)
                return@launch
            }

            viewModelScope.launch keepassBatchRestoreSync@{
                val keepassBatchResult = restoreKeepassBatchIfNeeded(
                    passwords = keepassPasswords,
                    secureItems = keepassSecureItems
                )
                keepassBatchResult.failedIds.forEach { failedId ->
                    queuedPasswords.firstOrNull { it.id == failedId }?.let { failedEntry ->
                        android.util.Log.e("TrashViewModel", "KeePass restore failed for password id=$failedId, rolling back local restore")
                        rollbackLocalRestore(failedEntry)
                    }
                    queuedSecureItems.firstOrNull { it.id == failedId }?.let { failedItem ->
                        android.util.Log.e("TrashViewModel", "KeePass restore failed for secure item id=$failedId, rolling back local restore")
                        rollbackLocalRestore(failedItem)
                    }
                }

                keepassPasswords.forEach { entry ->
                    if (entry.id in keepassBatchResult.failedIds) return@forEach
                    val restoreTarget = keepassBatchResult.passwordTargets[entry.id]
                    applyLocalRestore(
                        entry,
                        restoreTarget = restoreTarget,
                        restoreOutcome = restoreOutcomes[entry.id]
                            ?: BitwardenRestoreQueueOutcome.NO_REMOTE_ACTION
                    )
                    android.util.Log.i("TrashViewModel", "KeePass restore synced: id=${entry.id}, type=${ItemType.PASSWORD}")
                }

                keepassSecureItems.forEach { item ->
                    if (item.id in keepassBatchResult.failedIds) return@forEach
                    val restoreTarget = keepassBatchResult.secureItemTargets[item.id]
                    applyLocalRestore(
                        item,
                        restoreTarget = restoreTarget,
                        restoreOutcome = restoreOutcomes[item.id]
                            ?: BitwardenRestoreQueueOutcome.NO_REMOTE_ACTION
                    )
                    android.util.Log.i("TrashViewModel", "KeePass restore synced: id=${item.id}, type=${item.itemType}")
                }
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
                var deletedCount = 0

                val deletedPasswords = database.passwordEntryDao().getDeletedEntriesSync()
                deletedPasswords.forEach { entry ->
                    if (!permanentlyDeleteWithSources(entry)) {
                        hasFailure = true
                    } else {
                        deletedCount += 1
                    }
                }

                val deletedSecureItems = database.secureItemDao().getDeletedItemsSync()
                deletedSecureItems.forEach { item ->
                    if (!permanentlyDeleteWithSources(item)) {
                        hasFailure = true
                    } else {
                        deletedCount += 1
                    }
                }

                if (deletedCount > 0) {
                    logTrashSummaryDelete(
                        title = getApplication<Application>().getString(R.string.timeline_empty_trash_title),
                        detail = getApplication<Application>().getString(R.string.timeline_deleted_items_count, deletedCount)
                    )
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
                var deletedCount = 0

                val expiredPasswords = database.passwordEntryDao()
                    .getDeletedEntriesSync()
                    .filter { it.deletedAt != null && it.deletedAt < cutoffDate }

                expiredPasswords.forEach { entry ->
                    if (permanentlyDeleteWithSources(entry)) {
                        deletedCount += 1
                    }
                }

                val expiredSecureItems = database.secureItemDao()
                    .getDeletedItemsSync()
                    .filter { it.deletedAt != null && it.deletedAt < cutoffDate }

                expiredSecureItems.forEach { item ->
                    if (permanentlyDeleteWithSources(item)) {
                        deletedCount += 1
                    }
                }

                if (deletedCount > 0) {
                    logTrashSummaryDelete(
                        title = getApplication<Application>().getString(R.string.timeline_trash_title),
                        detail = getApplication<Application>().getString(R.string.timeline_auto_clear_in_days, settings.autoDeleteDays)
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("TrashViewModel", "Failed to cleanup expired items", e)
            }
        }
    }

    private fun buildRestoredPasswordEntry(
        entry: PasswordEntry,
        restoreTarget: KeePassRestoreTarget?,
        restoreOutcome: BitwardenRestoreQueueOutcome
    ): PasswordEntry {
        return BitwardenTrashRestoreStateHelper.applyToPasswordEntry(
            candidate = entry.copy(
                isDeleted = false,
                deletedAt = null,
                updatedAt = Date(),
                keepassGroupPath = restoreTarget?.groupPath,
                keepassGroupUuid = restoreTarget?.groupUuid
            ),
            restoreOutcome = restoreOutcome
        )
    }

    private fun buildRestoredSecureItem(
        item: SecureItem,
        restoreTarget: KeePassRestoreTarget?,
        restoreOutcome: BitwardenRestoreQueueOutcome
    ): SecureItem {
        return BitwardenTrashRestoreStateHelper.applyToSecureItem(
            candidate = item.copy(
                isDeleted = false,
                deletedAt = null,
                updatedAt = Date(),
                keepassGroupPath = restoreTarget?.groupPath,
                keepassGroupUuid = restoreTarget?.groupUuid
            ),
            restoreOutcome = restoreOutcome
        )
    }

    private suspend fun applyLocalRestore(
        data: Any,
        restoreTarget: KeePassRestoreTarget? = null,
        restoreOutcome: BitwardenRestoreQueueOutcome = BitwardenRestoreQueueOutcome.NO_REMOTE_ACTION
    ) {
        when (data) {
            is PasswordEntry -> {
                val resolvedTarget = restoreTarget ?: KeePassRestoreTarget(data.keepassGroupPath, data.keepassGroupUuid)
                database.passwordEntryDao().update(
                    buildRestoredPasswordEntry(
                        entry = data,
                        restoreTarget = resolvedTarget,
                        restoreOutcome = restoreOutcome
                    )
                )
            }
            is SecureItem -> {
                val resolvedTarget = restoreTarget ?: KeePassRestoreTarget(data.keepassGroupPath, data.keepassGroupUuid)
                database.secureItemDao().update(
                    buildRestoredSecureItem(
                        item = data,
                        restoreTarget = resolvedTarget,
                        restoreOutcome = restoreOutcome
                    )
                )
            }
        }
    }

    private suspend fun rollbackLocalRestore(data: Any) {
        when (data) {
            is PasswordEntry -> database.passwordEntryDao().update(data.copy(updatedAt = Date()))
            is SecureItem -> database.secureItemDao().update(data.copy(updatedAt = Date()))
        }
    }

    private fun needsKeepassRestore(data: Any): Boolean = when (data) {
        is PasswordEntry -> data.keepassDatabaseId != null
        is SecureItem -> data.keepassDatabaseId != null
        else -> false
    }

    private suspend fun restoreKeepassIfNeeded(data: Any): Result<KeePassRestoreTarget?> {
        return when (data) {
            is PasswordEntry -> {
                val keepassId = data.keepassDatabaseId
                if (keepassId == null) {
                    val localTarget = KeePassRestoreTarget(data.keepassGroupPath, data.keepassGroupUuid)
                    return Result.success(localTarget)
                }
                val restoreTarget = keepassBridge.resolveLegacyRestoreTargetForPassword(
                    databaseId = keepassId,
                    entry = data.copy(keepassDatabaseId = keepassId)
                ).getOrElse { return Result.failure(it) }
                val restoredForKeepass = buildRestoredPasswordEntry(
                    entry = data,
                    restoreTarget = restoreTarget,
                    restoreOutcome = BitwardenRestoreQueueOutcome.NO_REMOTE_ACTION
                ).copy(
                    keepassDatabaseId = keepassId,
                    keepassGroupPath = restoreTarget.groupPath,
                    keepassGroupUuid = restoreTarget.groupUuid
                )
                val addResult = keepassBridge.upsertLegacyPasswordEntries(
                    databaseId = keepassId,
                    entries = listOf(restoredForKeepass),
                    resolvePassword = { entry ->
                        runCatching { securityManager.decryptData(entry.password) }
                            .getOrElse { entry.password }
                    }
                )
                if (addResult.isFailure) return Result.failure(addResult.exceptionOrNull() ?: IllegalStateException("KeePass add restore failed"))
                Result.success(restoreTarget)
            }
            is SecureItem -> {
                val keepassId = data.keepassDatabaseId
                if (keepassId == null) {
                    val localTarget = KeePassRestoreTarget(data.keepassGroupPath, data.keepassGroupUuid)
                    return Result.success(localTarget)
                }
                val restoreTarget = keepassBridge.resolveLegacyRestoreTargetForSecureItem(
                    databaseId = keepassId,
                    item = data.copy(keepassDatabaseId = keepassId)
                ).getOrElse { return Result.failure(it) }
                val restoredForKeepass = buildRestoredSecureItem(
                    item = data,
                    restoreTarget = restoreTarget,
                    restoreOutcome = BitwardenRestoreQueueOutcome.NO_REMOTE_ACTION
                ).copy(
                    keepassDatabaseId = keepassId,
                    keepassGroupPath = restoreTarget.groupPath,
                    keepassGroupUuid = restoreTarget.groupUuid
                )
                val addResult = keepassBridge.upsertLegacySecureItems(
                    databaseId = keepassId,
                    items = listOf(restoredForKeepass)
                )
                if (addResult.isFailure) return Result.failure(addResult.exceptionOrNull() ?: IllegalStateException("KeePass add restore failed"))
                Result.success(restoreTarget)
            }
            else -> Result.success(null)
        }
    }

    private data class KeepassBatchRestoreResult(
        val passwordTargets: Map<Long, KeePassRestoreTarget?>,
        val secureItemTargets: Map<Long, KeePassRestoreTarget?>,
        val failedIds: Set<Long>
    )

    private suspend fun restoreKeepassBatchIfNeeded(
        passwords: List<PasswordEntry>,
        secureItems: List<SecureItem>
    ): KeepassBatchRestoreResult {
        val restoredPasswordTargets = mutableMapOf<Long, KeePassRestoreTarget?>()
        val restoredSecureTargets = mutableMapOf<Long, KeePassRestoreTarget?>()
        val failedIds = mutableSetOf<Long>()

        // Local-only items are considered restored in place.
        passwords.filter { it.keepassDatabaseId == null }.forEach {
            restoredPasswordTargets[it.id] = KeePassRestoreTarget(it.keepassGroupPath, it.keepassGroupUuid)
        }
        secureItems.filter { it.keepassDatabaseId == null }.forEach {
            restoredSecureTargets[it.id] = KeePassRestoreTarget(it.keepassGroupPath, it.keepassGroupUuid)
        }

        val groupedPasswords = passwords
            .filter { it.keepassDatabaseId != null }
            .groupBy { it.keepassDatabaseId!! }
        groupedPasswords.forEach { (databaseId, entries) ->
            val restoredEntries = mutableListOf<PasswordEntry>()
            entries.forEach entryLoop@{ entry ->
                val restoreTarget = keepassBridge.resolveLegacyRestoreTargetForPassword(
                    databaseId = databaseId,
                    entry = entry.copy(keepassDatabaseId = databaseId)
                ).getOrElse {
                    android.util.Log.e(
                        "TrashViewModel",
                        "Resolve KeePass restore path failed for password id=${entry.id}, db=$databaseId",
                        it
                    )
                    failedIds += entry.id
                    return@entryLoop
                }
                restoredEntries += buildRestoredPasswordEntry(
                    entry = entry,
                    restoreTarget = restoreTarget,
                    restoreOutcome = BitwardenRestoreQueueOutcome.NO_REMOTE_ACTION
                ).copy(
                    keepassDatabaseId = databaseId,
                    keepassGroupPath = restoreTarget.groupPath,
                    keepassGroupUuid = restoreTarget.groupUuid
                )
            }

            if (restoredEntries.isEmpty()) return@forEach

            val addResult = keepassBridge.upsertLegacyPasswordEntries(
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
                restoredPasswordTargets[restored.id] = KeePassRestoreTarget(
                    restored.keepassGroupPath,
                    restored.keepassGroupUuid
                )
            }
        }

        val groupedSecureItems = secureItems
            .filter { it.keepassDatabaseId != null }
            .groupBy { it.keepassDatabaseId!! }
        groupedSecureItems.forEach { (databaseId, items) ->
            val restoredItems = mutableListOf<SecureItem>()
            items.forEach itemLoop@{ item ->
                val restoreTarget = keepassBridge.resolveLegacyRestoreTargetForSecureItem(
                    databaseId = databaseId,
                    item = item.copy(keepassDatabaseId = databaseId)
                ).getOrElse {
                    android.util.Log.e(
                        "TrashViewModel",
                        "Resolve KeePass restore path failed for secure item id=${item.id}, db=$databaseId",
                        it
                    )
                    failedIds += item.id
                    return@itemLoop
                }
                restoredItems += buildRestoredSecureItem(
                    item = item,
                    restoreTarget = restoreTarget,
                    restoreOutcome = BitwardenRestoreQueueOutcome.NO_REMOTE_ACTION
                ).copy(
                    keepassDatabaseId = databaseId,
                    keepassGroupPath = restoreTarget.groupPath,
                    keepassGroupUuid = restoreTarget.groupUuid
                )
            }

            if (restoredItems.isEmpty()) return@forEach

            val addResult = keepassBridge.upsertLegacySecureItems(
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
                restoredSecureTargets[restored.id] = KeePassRestoreTarget(
                    restored.keepassGroupPath,
                    restored.keepassGroupUuid
                )
            }
        }

        return KeepassBatchRestoreResult(
            passwordTargets = restoredPasswordTargets,
            secureItemTargets = restoredSecureTargets,
            failedIds = failedIds
        )
    }

    private suspend fun deleteKeepassEntryIfNeeded(data: Any): Boolean {
        return when (data) {
            is PasswordEntry -> {
                val keepassId = data.keepassDatabaseId ?: return true
                val result = keepassBridge.deleteLegacyPasswordEntries(
                    databaseId = keepassId,
                    entries = listOf(data.copy(keepassDatabaseId = keepassId))
                )
                result.getOrNull()?.let { it > 0 } ?: false
            }
            is SecureItem -> {
                val keepassId = data.keepassDatabaseId ?: return true
                val result = keepassBridge.deleteLegacySecureItems(
                    databaseId = keepassId,
                    items = listOf(data.copy(keepassDatabaseId = keepassId))
                )
                result.getOrNull()?.let { it > 0 } ?: false
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

    private suspend fun queueRemoteRestoreIfNeeded(data: Any): Result<BitwardenRestoreQueueOutcome> {
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
                    )
                } else {
                    Result.success(BitwardenRestoreQueueOutcome.NO_REMOTE_ACTION)
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
                    )
                } else {
                    Result.success(BitwardenRestoreQueueOutcome.NO_REMOTE_ACTION)
                }
            }
            else -> Result.success(BitwardenRestoreQueueOutcome.NO_REMOTE_ACTION)
        }
    }

    private fun ItemType.toPendingItemType(): String = when (this) {
        ItemType.PASSWORD -> BitwardenPendingOperation.ITEM_TYPE_PASSWORD
        ItemType.TOTP -> BitwardenPendingOperation.ITEM_TYPE_TOTP
        ItemType.BANK_CARD -> BitwardenPendingOperation.ITEM_TYPE_CARD
        ItemType.DOCUMENT -> BitwardenPendingOperation.ITEM_TYPE_DOCUMENT
        ItemType.NOTE -> BitwardenPendingOperation.ITEM_TYPE_NOTE
    }

    private fun ItemType.toOperationLogItemType(): OperationLogItemType = when (this) {
        ItemType.PASSWORD -> OperationLogItemType.PASSWORD
        ItemType.TOTP -> OperationLogItemType.TOTP
        ItemType.BANK_CARD -> OperationLogItemType.BANK_CARD
        ItemType.DOCUMENT -> OperationLogItemType.DOCUMENT
        ItemType.NOTE -> OperationLogItemType.NOTE
    }

    private fun logTrashRestore(item: TrashItem) {
        OperationLogger.logUpdate(
            itemType = item.itemType.toOperationLogItemType(),
            itemId = item.id,
            itemTitle = item.title,
            changes = listOf(
                FieldChange(
                    fieldName = getApplication<Application>().getString(R.string.timeline_trash_title),
                    oldValue = getApplication<Application>().getString(R.string.timeline_op_delete),
                    newValue = getApplication<Application>().getString(R.string.timeline_reverted)
                )
            )
        )
    }

    private fun logTrashPermanentDelete(item: TrashItem) {
        OperationLogger.logDelete(
            itemType = item.itemType.toOperationLogItemType(),
            itemId = item.id,
            itemTitle = item.title,
            detail = getApplication<Application>().getString(R.string.timeline_permanent_delete_title)
        )
    }

    private fun logTrashSummaryDelete(title: String, detail: String) {
        OperationLogger.logDelete(
            itemType = OperationLogItemType.CATEGORY,
            itemId = System.currentTimeMillis(),
            itemTitle = title,
            detail = detail
        )
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
