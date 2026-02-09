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
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
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
                when (item.originalData) {
                    is PasswordEntry -> {
                        val restored = item.originalData.copy(
                            isDeleted = false,
                            deletedAt = null,
                            updatedAt = Date()
                        )
                        database.passwordEntryDao().update(restored)
                    }
                    is SecureItem -> {
                        val restored = item.originalData.copy(
                            isDeleted = false,
                            deletedAt = null,
                            updatedAt = Date()
                        )
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
                if (!deleteRemoteCipherIfNeeded(item.originalData)) {
                    onResult(false)
                    return@launch
                }
                when (item.originalData) {
                    is PasswordEntry -> {
                        database.passwordEntryDao().delete(item.originalData)
                    }
                    is SecureItem -> {
                        database.secureItemDao().delete(item.originalData)
                    }
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
                category.items.forEach { item ->
                    when (item.originalData) {
                        is PasswordEntry -> {
                            val restored = item.originalData.copy(
                                isDeleted = false,
                                deletedAt = null,
                                updatedAt = Date()
                            )
                            database.passwordEntryDao().update(restored)
                        }
                        is SecureItem -> {
                            val restored = item.originalData.copy(
                                isDeleted = false,
                                deletedAt = null,
                                updatedAt = Date()
                            )
                            database.secureItemDao().update(restored)
                        }
                    }
                }
                onResult(true)
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
                    if (deleteRemoteCipherIfNeeded(entry)) {
                        database.passwordEntryDao().delete(entry)
                    } else {
                        hasFailure = true
                    }
                }

                val deletedSecureItems = database.secureItemDao().getDeletedItemsSync()
                deletedSecureItems.forEach { item ->
                    if (deleteRemoteCipherIfNeeded(item)) {
                        database.secureItemDao().delete(item)
                    } else {
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
                    if (deleteRemoteCipherIfNeeded(entry)) {
                        database.passwordEntryDao().delete(entry)
                    }
                }

                val expiredSecureItems = database.secureItemDao()
                    .getDeletedItemsSync()
                    .filter { it.deletedAt != null && it.deletedAt < cutoffDate }

                expiredSecureItems.forEach { item ->
                    if (deleteRemoteCipherIfNeeded(item)) {
                        database.secureItemDao().delete(item)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TrashViewModel", "Failed to cleanup expired items", e)
            }
        }
    }

    private suspend fun deleteRemoteCipherIfNeeded(data: Any): Boolean {
        return when (data) {
            is PasswordEntry -> {
                val vaultId = data.bitwardenVaultId
                val cipherId = data.bitwardenCipherId
                if (vaultId != null && !cipherId.isNullOrBlank()) {
                    bitwardenRepository.deleteCipher(vaultId, cipherId).isSuccess
                } else {
                    true
                }
            }
            is SecureItem -> {
                val vaultId = data.bitwardenVaultId
                val cipherId = data.bitwardenCipherId
                if (vaultId != null && !cipherId.isNullOrBlank()) {
                    bitwardenRepository.deleteCipher(vaultId, cipherId).isSuccess
                } else {
                    true
                }
            }
            else -> true
        }
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
