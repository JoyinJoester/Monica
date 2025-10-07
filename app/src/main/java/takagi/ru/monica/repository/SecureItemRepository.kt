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
     * 检查是否存在重复的安全项
     */
    suspend fun isDuplicateItem(itemType: ItemType, title: String): Boolean {
        return secureItemDao.findDuplicateItem(itemType, title) != null
    }
}
