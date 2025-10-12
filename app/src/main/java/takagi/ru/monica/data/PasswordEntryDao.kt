package takagi.ru.monica.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for password entries
 */
@Dao
interface PasswordEntryDao {
    
    @Query("SELECT * FROM password_entries ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    fun getAllPasswordEntries(): Flow<List<PasswordEntry>>
    
    @Query("SELECT * FROM password_entries WHERE title LIKE '%' || :query || '%' OR website LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' OR appName LIKE '%' || :query || '%' OR appPackageName LIKE '%' || :query || '%' ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    fun searchPasswordEntries(query: String): Flow<List<PasswordEntry>>
    
    @Query("SELECT * FROM password_entries WHERE id = :id")
    suspend fun getPasswordEntryById(id: Long): PasswordEntry?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasswordEntry(entry: PasswordEntry): Long
    
    @Update
    suspend fun updatePasswordEntry(entry: PasswordEntry)
    
    @Delete
    suspend fun deletePasswordEntry(entry: PasswordEntry)
    
    @Query("DELETE FROM password_entries WHERE id = :id")
    suspend fun deletePasswordEntryById(id: Long)
    
    @Query("UPDATE password_entries SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean)
    
    @Query("UPDATE password_entries SET isGroupCover = :isGroupCover WHERE id = :id")
    suspend fun updateGroupCoverStatus(id: Long, isGroupCover: Boolean)
    
    @Query("UPDATE password_entries SET isGroupCover = 0 WHERE website = :website")
    suspend fun clearGroupCover(website: String)
    
    @Transaction
    suspend fun setGroupCover(id: Long, website: String) {
        // 先清除该分组的所有封面标记
        clearGroupCover(website)
        // 再设置新的封面
        updateGroupCoverStatus(id, true)
    }
    
    @Query("UPDATE password_entries SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)
    
    @Transaction
    suspend fun updateSortOrders(items: List<Pair<Long, Int>>) {
        items.forEach { (id, sortOrder) ->
            updateSortOrder(id, sortOrder)
        }
    }
    
    @Query("SELECT COUNT(*) FROM password_entries")
    suspend fun getPasswordEntriesCount(): Int
    
    @Query("DELETE FROM password_entries")
    suspend fun deleteAllPasswordEntries()
    
    /**
     * 检查是否存在相同的密码条目(根据title、username、website匹配)
     */
    @Query("SELECT * FROM password_entries WHERE title = :title AND username = :username AND website = :website LIMIT 1")
    suspend fun findDuplicateEntry(title: String, username: String, website: String): PasswordEntry?
}