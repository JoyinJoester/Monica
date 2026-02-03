package takagi.ru.monica.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)
    
    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)
    
    // =============== Bitwarden 文件夹关联相关方法 ===============
    
    /**
     * 获取关联了 Bitwarden 的分类
     */
    @Query("SELECT * FROM categories WHERE bitwarden_vault_id = :vaultId ORDER BY sortOrder ASC")
    fun getCategoriesByVault(vaultId: Long): Flow<List<Category>>
    
    /**
     * 获取关联了特定 Bitwarden Folder 的分类
     */
    @Query("SELECT * FROM categories WHERE bitwarden_folder_id = :folderId LIMIT 1")
    suspend fun getCategoryByBitwardenFolderId(folderId: String): Category?
    
    /**
     * 获取所有关联了 Bitwarden 的分类（同步版本）
     */
    @Query("SELECT * FROM categories WHERE bitwarden_vault_id IS NOT NULL ORDER BY sortOrder ASC")
    suspend fun getBitwardenLinkedCategoriesSync(): List<Category>
    
    /**
     * 关联分类到 Bitwarden Folder
     */
    @Query("UPDATE categories SET bitwarden_vault_id = :vaultId, bitwarden_folder_id = :folderId, sync_item_types = :syncTypes WHERE id = :categoryId")
    suspend fun linkToBitwarden(categoryId: Long, vaultId: Long, folderId: String, syncTypes: String?)
    
    /**
     * 解除 Bitwarden 关联
     */
    @Query("UPDATE categories SET bitwarden_vault_id = NULL, bitwarden_folder_id = NULL, sync_item_types = NULL WHERE id = :categoryId")
    suspend fun unlinkFromBitwarden(categoryId: Long)
    
    /**
     * 更新同步类型
     */
    @Query("UPDATE categories SET sync_item_types = :syncTypes WHERE id = :categoryId")
    suspend fun updateSyncTypes(categoryId: Long, syncTypes: String?)
    
    /**
     * 根据 ID 获取分类
     */
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): Category?
}
