package takagi.ru.monica.wear.repository

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
    suspend fun importTotpItems(items: List<SecureItem>)
    suspend fun getTotpCount(): Int
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
    
    override suspend fun importTotpItems(items: List<SecureItem>) {
        // 清空现有数据
        dao.deleteAll()
        // 仅导入TOTP类型的项目
        val totpItems = items.filter { it.itemType == ItemType.TOTP }
        dao.insertAll(totpItems)
    }
    
    override suspend fun getTotpCount(): Int {
        return dao.getItemCount(ItemType.TOTP)
    }
}
