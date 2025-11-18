package takagi.ru.monica.wear.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for secure items (Wear版本 - 仅TOTP)
 */
@Dao
interface SecureItemDao {
    
    // 根据类型获取项目 (Wear版本主要用于获取TOTP)
    @Query("SELECT * FROM secure_items WHERE itemType = :type ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    fun getItemsByType(type: ItemType): Flow<List<SecureItem>>
    
    // 根据类型搜索 (用于搜索功能)
    @Query("SELECT * FROM secure_items WHERE itemType = :type AND (title LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' OR itemData LIKE '%' || :query || '%') ORDER BY isFavorite DESC, updatedAt DESC")
    fun searchItemsByType(type: ItemType, query: String): Flow<List<SecureItem>>
    
    // 根据ID获取项目
    @Query("SELECT * FROM secure_items WHERE id = :id")
    suspend fun getItemById(id: Long): SecureItem?
    
    // 插入项目
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: SecureItem): Long
    
    // 批量插入 (用于同步导入)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SecureItem>)
    
    // 删除所有项目 (用于同步前清空)
    @Query("DELETE FROM secure_items")
    suspend fun deleteAll()
    
    // 获取项目总数
    @Query("SELECT COUNT(*) FROM secure_items WHERE itemType = :type")
    suspend fun getItemCount(type: ItemType): Int
}
