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
    
    @Query("SELECT * FROM password_entries WHERE categoryId = :categoryId ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    fun getPasswordEntriesByCategory(categoryId: Long): Flow<List<PasswordEntry>>

    @Query("SELECT * FROM password_entries WHERE isFavorite = 1 ORDER BY sortOrder ASC, updatedAt DESC")
    fun getFavoritePasswordEntries(): Flow<List<PasswordEntry>>
    
    @Query("UPDATE password_entries SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun removeCategoryFromPasswords(categoryId: Long)
    
    @Query("SELECT * FROM password_entries WHERE title LIKE '%' || :query || '%' OR website LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' OR appName LIKE '%' || :query || '%' OR appPackageName LIKE '%' || :query || '%' ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    fun searchPasswordEntries(query: String): Flow<List<PasswordEntry>>
    
    @Query("SELECT * FROM password_entries WHERE id = :id")
    suspend fun getPasswordEntryById(id: Long): PasswordEntry?
    
    @Query("SELECT * FROM password_entries WHERE id IN (:ids)")
    suspend fun getPasswordsByIds(ids: List<Long>): List<PasswordEntry>
    
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
    
    @Query("UPDATE password_entries SET categoryId = :categoryId WHERE id IN (:ids)")
    suspend fun updateCategoryForPasswords(ids: List<Long>, categoryId: Long?)

    @Transaction
    suspend fun setGroupCover(id: Long, website: String) {
        // 先清除该分组的所有封面标记
        clearGroupCover(website)
        // 再设置新的封面
        updateGroupCoverStatus(id, true)
    }

    @Query("UPDATE password_entries SET appPackageName = :packageName, appName = :appName WHERE website = :website AND website != ''")
    suspend fun updateAppAssociationByWebsite(website: String, packageName: String, appName: String)

    @Query("UPDATE password_entries SET appPackageName = :packageName, appName = :appName WHERE title = :title AND title != ''")
    suspend fun updateAppAssociationByTitle(title: String, packageName: String, appName: String)

    
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
    
    /**
     * 按包名和用户名查询密码
     * 用于自动填充保存时检测重复
     */
    @Query("SELECT * FROM password_entries WHERE appPackageName = :packageName AND LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun findByPackageAndUsername(packageName: String, username: String): PasswordEntry?
    
    /**
     * 按网站和用户名查询密码
     * 用于自动填充保存时检测重复
     */
    @Query("SELECT * FROM password_entries WHERE LOWER(website) LIKE '%' || LOWER(:domain) || '%' AND LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun findByDomainAndUsername(domain: String, username: String): PasswordEntry?
    
    /**
     * 按包名查询所有密码
     * 用于检测同一应用的多个账号
     */
    @Query("SELECT * FROM password_entries WHERE appPackageName = :packageName ORDER BY updatedAt DESC")
    suspend fun findByPackageName(packageName: String): List<PasswordEntry>
    
    /**
     * 按网站域名查询所有密码
     * 用于检测同一网站的多个账号
     */
    @Query("SELECT * FROM password_entries WHERE LOWER(website) LIKE '%' || LOWER(:domain) || '%' ORDER BY updatedAt DESC")
    suspend fun findByDomain(domain: String): List<PasswordEntry>
    
    /**
     * 检查是否存在完全相同的密码(包名+用户名+密码)
     * 用于避免重复保存
     */
    @Query("SELECT * FROM password_entries WHERE appPackageName = :packageName AND LOWER(username) = LOWER(:username) AND password = :encryptedPassword LIMIT 1")
    suspend fun findExactMatch(packageName: String, username: String, encryptedPassword: String): PasswordEntry?
}