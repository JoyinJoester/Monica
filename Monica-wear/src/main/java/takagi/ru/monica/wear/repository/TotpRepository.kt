package takagi.ru.monica.wear.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import takagi.ru.monica.wear.data.ItemType
import takagi.ru.monica.wear.data.SecureItem
import takagi.ru.monica.wear.data.SecureItemDao

/**
 * TOTP Repository接口
 */
interface TotpRepository {
    fun getAllTotpItems(): Flow<List<SecureItem>>
    fun getTotpById(id: Long): Flow<SecureItem?>
    fun searchTotpItems(query: String): Flow<List<SecureItem>>
    suspend fun importTotpItems(items: List<SecureItem>): Pair<Int, Int>
    suspend fun getTotpCount(): Int
    suspend fun deleteItem(item: SecureItem)
    suspend fun updateItem(item: SecureItem)
}

/**
 * TOTP Repository实现
 */
class TotpRepositoryImpl(
    private val dao: SecureItemDao
) : TotpRepository {
    
    override fun getAllTotpItems(): Flow<List<SecureItem>> {
        return dao.getItemsByType(ItemType.TOTP)
    }
    
    override fun getTotpById(id: Long): Flow<SecureItem?> {
        // 由于DAO中没有Flow版本的getItemById，我们需要使用其他方式
        // 这里暂时使用map来过滤
        // TODO: 在后续任务中优化
        return getAllTotpItems().map { items ->
            items.firstOrNull { it.id == id }
        }
    }
    
    override fun searchTotpItems(query: String): Flow<List<SecureItem>> {
        return dao.searchItemsByType(ItemType.TOTP, query)
    }
    
    override suspend fun importTotpItems(items: List<SecureItem>): Pair<Int, Int> {
        // 仅导入TOTP类型的项目
        val totpItems = items.filter { it.itemType == ItemType.TOTP }
        
        // 获取现有的TOTP项目
        val existingItems = dao.getItemsByTypeSync(ItemType.TOTP)
        
        // 构建去重键集合
        val existingKeys = existingItems.mapNotNull { item ->
            generateDeduplicationKey(item)
        }.toMutableSet()
        
        // 过滤重复项
        val itemsToImport = mutableListOf<SecureItem>()
        var skippedCount = 0
        
        for (item in totpItems) {
            val key = generateDeduplicationKey(item)
            if (key == null) {
                // 两个字段都为空 - 无条件导入
                itemsToImport.add(item.copy(id = 0))
            } else if (key in existingKeys) {
                // 发现重复项
                skippedCount++
                Log.d(TAG, "Skipping duplicate: title='${item.title}', key='$key'")
            } else {
                // 新项目
                itemsToImport.add(item.copy(id = 0))
                existingKeys.add(key)
            }
        }
        
        // 批量插入
        if (itemsToImport.isNotEmpty()) {
            dao.insertAll(itemsToImport)
        }
        
        val importedCount = itemsToImport.size
        Log.d(TAG, "Import complete: imported=$importedCount, skipped=$skippedCount")
        
        return Pair(importedCount, skippedCount)
    }
    
    /**
     * 生成去重键
     * @return 去重键，如果两个字段都为空则返回null
     */
    private fun generateDeduplicationKey(item: SecureItem): String? {
        val trimmedTitle = item.title.trim()
        val trimmedData = item.itemData.trim()
        
        return when {
            trimmedTitle.isEmpty() && trimmedData.isEmpty() -> null
            trimmedTitle.isEmpty() -> trimmedData
            trimmedData.isEmpty() -> trimmedTitle
            else -> "$trimmedTitle|$trimmedData"
        }
    }
    
    override suspend fun getTotpCount(): Int {
        return dao.getItemCount(ItemType.TOTP)
    }
    
    override suspend fun deleteItem(item: SecureItem) {
        dao.deleteItem(item)
    }
    
    override suspend fun updateItem(item: SecureItem) {
        dao.updateItem(item)
    }
    
    companion object {
        private const val TAG = "TotpRepository"
    }
}
