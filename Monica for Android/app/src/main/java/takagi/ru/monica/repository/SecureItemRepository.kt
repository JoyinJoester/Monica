package takagi.ru.monica.repository

import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.SecureItemDao
import takagi.ru.monica.data.ItemType
import kotlinx.coroutines.flow.Flow

/**
 * Repository for secure items (TOTP, Bank Cards, Documents)
 */
class SecureItemRepository(
    private val secureItemDao: SecureItemDao
) {
    
    fun getAllItems(): Flow<List<SecureItem>> {
        return secureItemDao.getAllItems()
    }
    
    fun getItemsByType(type: ItemType): Flow<List<SecureItem>> {
        return secureItemDao.getItemsByType(type)
    }
    
    fun searchItems(query: String): Flow<List<SecureItem>> {
        return secureItemDao.searchItems(query)
    }
    
    fun searchItemsByType(type: ItemType, query: String): Flow<List<SecureItem>> {
        return secureItemDao.searchItemsByType(type, query)
    }
    
    fun getFavoriteItems(): Flow<List<SecureItem>> {
        return secureItemDao.getFavoriteItems()
    }
    
    suspend fun getItemById(id: Long): SecureItem? {
        return secureItemDao.getItemById(id)
    }

    fun observeItemById(id: Long): Flow<SecureItem?> {
        return secureItemDao.observeItemById(id)
    }
    
    suspend fun insertItem(item: SecureItem): Long {
        return secureItemDao.insertItem(item)
    }
    
    suspend fun updateItem(item: SecureItem) {
        secureItemDao.updateItem(item)
    }
    
    suspend fun deleteItem(item: SecureItem) {
        secureItemDao.deleteItem(item)
    }
    
    suspend fun deleteItemById(id: Long) {
        secureItemDao.deleteItemById(id)
    }
    
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean) {
        secureItemDao.updateFavoriteStatus(id, isFavorite)
    }
    
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        secureItemDao.updateFavoriteStatus(id, isFavorite)
    }
    
    suspend fun updateSortOrder(id: Long, sortOrder: Int) {
        secureItemDao.updateSortOrder(id, sortOrder)
    }
    
    suspend fun updateSortOrders(items: List<Pair<Long, Int>>) {
        secureItemDao.updateSortOrders(items)
    }
    
    /**
     * 检查是否存在重复的安全项（基于 title 匹配，旧方法保留兼容）
     */
    suspend fun isDuplicateItem(itemType: ItemType, title: String): Boolean {
        return secureItemDao.findDuplicateItem(itemType, title) != null
    }
    
    /**
     * 智能检测重复项（根据类型使用不同的比较策略）
     * - DOCUMENT: 比较 documentNumber
     * - BANK_CARD: 比较 cardNumber  
     * - TOTP: 比较 issuer + accountName
     * - NOTE/PASSWORD: 比较 title
     * @return 找到的重复项，或 null
     */
    suspend fun findDuplicateSecureItem(
        itemType: ItemType,
        itemData: String,
        title: String,
        localOnly: Boolean = false
    ): takagi.ru.monica.data.SecureItem? {
        val existingItems = if (localOnly) {
            secureItemDao.getActiveLocalItemsByTypeSync(itemType)
        } else {
            secureItemDao.getActiveItemsByTypeSync(itemType)
        }
        
        return when (itemType) {
            ItemType.DOCUMENT -> {
                // 解析新项目的证件号码
                val newDocNumber = try {
                    kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                        .decodeFromString<takagi.ru.monica.data.model.DocumentData>(itemData).documentNumber
                } catch (e: Exception) { null }
                
                if (newDocNumber.isNullOrBlank()) {
                    // 无法解析证件号，退回到 title 匹配
                    existingItems.find { it.title == title }
                } else {
                    existingItems.find { existing ->
                        try {
                            val existingDocNumber = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                                .decodeFromString<takagi.ru.monica.data.model.DocumentData>(existing.itemData).documentNumber
                            existingDocNumber == newDocNumber
                        } catch (e: Exception) { false }
                    }
                }
            }
            ItemType.BANK_CARD -> {
                // 解析新项目的卡号
                val newCardNumber = try {
                    kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                        .decodeFromString<takagi.ru.monica.data.model.BankCardData>(itemData).cardNumber
                } catch (e: Exception) { null }
                
                if (newCardNumber.isNullOrBlank()) {
                    existingItems.find { it.title == title }
                } else {
                    existingItems.find { existing ->
                        try {
                            val existingCardNumber = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                                .decodeFromString<takagi.ru.monica.data.model.BankCardData>(existing.itemData).cardNumber
                            existingCardNumber == newCardNumber
                        } catch (e: Exception) { false }
                    }
                }
            }
            ItemType.TOTP -> {
                // 解析新项目的 issuer + accountName
                val newTotpData = try {
                    kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                        .decodeFromString<takagi.ru.monica.data.model.TotpData>(itemData)
                } catch (e: Exception) { null }
                
                if (newTotpData == null) {
                    existingItems.find { it.title == title }
                } else {
                    existingItems.find { existing ->
                        try {
                            val existingTotpData = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                                .decodeFromString<takagi.ru.monica.data.model.TotpData>(existing.itemData)
                            existingTotpData.issuer == newTotpData.issuer && 
                                existingTotpData.accountName == newTotpData.accountName
                        } catch (e: Exception) { false }
                    }
                }
            }
            else -> {
                // NOTE 和其他类型：只比较 title
                existingItems.find { it.title == title }
            }
        }
    }
    
    /**
     * 删除指定类型的所有项目
     */
    suspend fun deleteAllItemsByType(type: ItemType) {
        secureItemDao.deleteAllItemsByType(type)
    }
    
    /**
     * 删除所有TOTP认证器
     */
    suspend fun deleteAllTotpEntries() {
        secureItemDao.deleteAllItemsByType(ItemType.TOTP)
    }
    
    /**
     * 删除所有文档
     */
    suspend fun deleteAllDocuments() {
        secureItemDao.deleteAllItemsByType(ItemType.DOCUMENT)
    }
    
    /**
     * 删除所有银行卡
     */
    suspend fun deleteAllBankCards() {
        secureItemDao.deleteAllItemsByType(ItemType.BANK_CARD)
    }
    
    // =============== 回收站相关方法 ===============
    
    /**
     * 软删除项目（移动到回收站）
     */
    suspend fun softDeleteItem(item: SecureItem): SecureItem {
        val deletedItem = item.copy(
            isDeleted = true,
            deletedAt = java.util.Date(),
            updatedAt = java.util.Date()
        )
        secureItemDao.updateItem(deletedItem)
        return deletedItem
    }
    
    /**
     * 恢复已删除的项目
     */
    suspend fun restoreItem(item: SecureItem): SecureItem {
        val restoredItem = item.copy(
            isDeleted = false,
            deletedAt = null,
            updatedAt = java.util.Date()
        )
        secureItemDao.updateItem(restoredItem)
        return restoredItem
    }
    
    /**
     * 获取已删除的项目
     */
    fun getDeletedItems(): kotlinx.coroutines.flow.Flow<List<SecureItem>> {
        return secureItemDao.getDeletedItems()
    }
    
    /**
     * 获取未删除的项目（按类型）
     */
    fun getActiveItemsByType(type: ItemType): kotlinx.coroutines.flow.Flow<List<SecureItem>> {
        return secureItemDao.getActiveItemsByType(type)
    }
}
