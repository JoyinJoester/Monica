package takagi.ru.monica.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for password entries
 */
@Dao
interface PasswordEntryDao {
    
    @Query("SELECT * FROM password_entries ORDER BY isFavorite DESC, updatedAt DESC")
    fun getAllPasswordEntries(): Flow<List<PasswordEntry>>
    
    @Query("SELECT * FROM password_entries WHERE title LIKE '%' || :query || '%' OR website LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' ORDER BY isFavorite DESC, updatedAt DESC")
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
    
    @Query("SELECT COUNT(*) FROM password_entries")
    suspend fun getPasswordEntriesCount(): Int
    
    @Query("DELETE FROM password_entries")
    suspend fun deleteAllPasswordEntries()
}