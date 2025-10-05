package takagi.ru.monica.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for secure items (TOTP, Bank Cards, Documents)
 */
@Dao
interface SecureItemDao {
    
    // 获取所有项目
    @Query("SELECT * FROM secure_items ORDER BY updatedAt DESC")
    fun getAllItems(): Flow<List<SecureItem>>
    
    // 根据类型获取项目
    @Query("SELECT * FROM secure_items WHERE itemType = :type ORDER BY updatedAt DESC")
    fun getItemsByType(type: ItemType): Flow<List<SecureItem>>
    
    // 搜索项目
    @Query("SELECT * FROM secure_items WHERE title LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchItems(query: String): Flow<List<SecureItem>>
    
    // 根据类型搜索
    @Query("SELECT * FROM secure_items WHERE itemType = :type AND (title LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%') ORDER BY updatedAt DESC")
    fun searchItemsByType(type: ItemType, query: String): Flow<List<SecureItem>>
    
    // 获取收藏项目
    @Query("SELECT * FROM secure_items WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteItems(): Flow<List<SecureItem>>
    
    // 根据ID获取项目
    @Query("SELECT * FROM secure_items WHERE id = :id")
    suspend fun getItemById(id: Long): SecureItem?
    
    // 插入项目
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: SecureItem): Long
    
    // 更新项目
    @Update
    suspend fun updateItem(item: SecureItem)
    
    // 删除项目
    @Delete
    suspend fun deleteItem(item: SecureItem)
    
    // 根据ID删除
    @Query("DELETE FROM secure_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)
    
    // 切换收藏状态
    @Query("UPDATE secure_items SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean)
}
