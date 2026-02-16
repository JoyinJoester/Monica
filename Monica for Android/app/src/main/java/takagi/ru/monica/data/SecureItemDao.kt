package takagi.ru.monica.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for secure items (TOTP, Bank Cards, Documents)
 */
@Dao
interface SecureItemDao {
    
    // 获取所有项目（排除已删除）
    @Query("SELECT * FROM secure_items WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllItems(): Flow<List<SecureItem>>
    
    // 根据类型获取项目（排除已删除）
    @Query("SELECT * FROM secure_items WHERE isDeleted = 0 AND itemType = :type ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    fun getItemsByType(type: ItemType): Flow<List<SecureItem>>
    
    // 搜索项目（排除已删除）
    @Query("SELECT * FROM secure_items WHERE isDeleted = 0 AND (title LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%') ORDER BY updatedAt DESC")
    fun searchItems(query: String): Flow<List<SecureItem>>
    
    // 根据类型搜索（排除已删除）
    @Query("SELECT * FROM secure_items WHERE isDeleted = 0 AND itemType = :type AND (title LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%') ORDER BY isFavorite DESC, updatedAt DESC")
    fun searchItemsByType(type: ItemType, query: String): Flow<List<SecureItem>>
    
    // 获取收藏项目（排除已删除）
    @Query("SELECT * FROM secure_items WHERE isDeleted = 0 AND isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteItems(): Flow<List<SecureItem>>
    
    // 根据ID获取项目
    @Query("SELECT * FROM secure_items WHERE id = :id")
    suspend fun getItemById(id: Long): SecureItem?

    // 监听指定ID的项目变化
    @Query("SELECT * FROM secure_items WHERE id = :id")
    fun observeItemById(id: Long): Flow<SecureItem?>
    
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
    
    // 更新排序顺序
    @Query("UPDATE secure_items SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)
    
    // 批量更新排序顺序
    @Transaction
    suspend fun updateSortOrders(items: List<Pair<Long, Int>>) {
        items.forEach { (id, sortOrder) ->
            updateSortOrder(id, sortOrder)
        }
    }
    
    /**
     * 检查是否存在相同的安全项(根据itemType和title匹配)
     */
    @Query("SELECT * FROM secure_items WHERE itemType = :itemType AND title = :title LIMIT 1")
    suspend fun findDuplicateItem(itemType: ItemType, title: String): SecureItem?
    
    /**
     * 获取指定类型的所有未删除项目（同步版本，用于智能重复检测）
     */
    @Query("SELECT * FROM secure_items WHERE itemType = :itemType AND isDeleted = 0")
    suspend fun getActiveItemsByTypeSync(itemType: ItemType): List<SecureItem>

    @Query("SELECT * FROM secure_items WHERE isDeleted = 0 AND keepass_database_id = :databaseId ORDER BY updatedAt DESC")
    suspend fun getByKeePassDatabaseIdSync(databaseId: Long): List<SecureItem>

    @Query("""
        SELECT * FROM secure_items
        WHERE itemType = :itemType
          AND isDeleted = 0
          AND bitwarden_vault_id IS NULL
          AND keepass_database_id IS NULL
    """)
    suspend fun getActiveLocalItemsByTypeSync(itemType: ItemType): List<SecureItem>
    
    /**
     * 删除指定类型的所有项目
     */
    @Query("DELETE FROM secure_items WHERE itemType = :type")
    suspend fun deleteAllItemsByType(type: ItemType)
    
    /**
     * 删除所有安全项目
     */
    @Query("DELETE FROM secure_items")
    suspend fun deleteAllItems()
    
    // =============== 回收站相关方法 ===============
    
    /**
     * 获取所有已删除的项目（回收站）
     */
    @Query("SELECT * FROM secure_items WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedItems(): Flow<List<SecureItem>>
    
    /**
     * 获取所有已删除的项目（同步版本，用于备份）
     */
    @Query("SELECT * FROM secure_items WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    suspend fun getDeletedItemsSync(): List<SecureItem>
    
    /**
     * 获取所有未删除的项目（正常项目）
     */
    @Query("SELECT * FROM secure_items WHERE isDeleted = 0 ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    fun getActiveItems(): Flow<List<SecureItem>>
    
    /**
     * 根据类型获取未删除的项目
     */
    @Query("SELECT * FROM secure_items WHERE itemType = :type AND isDeleted = 0 ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    fun getActiveItemsByType(type: ItemType): Flow<List<SecureItem>>
    
    /**
     * 软删除项目（移动到回收站）
     */
    @Query("UPDATE secure_items SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long = System.currentTimeMillis())
    
    /**
     * 恢复已删除的项目
     */
    @Query("UPDATE secure_items SET isDeleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)
    
    /**
     * 永久删除所有回收站中的项目
     */
    @Query("DELETE FROM secure_items WHERE isDeleted = 1")
    suspend fun permanentlyDeleteAll()
    
    /**
     * 删除过期的回收站项目（超过指定天数）
     */
    @Query("DELETE FROM secure_items WHERE isDeleted = 1 AND deletedAt < :cutoffDate")
    suspend fun deleteExpiredItems(cutoffDate: java.util.Date)
    
    /**
     * 获取回收站项目数量
     */
    @Query("SELECT COUNT(*) FROM secure_items WHERE isDeleted = 1")
    suspend fun getDeletedCount(): Int
    
    /**
     * 更新项目（用于恢复等操作）
     */
    @Update
    suspend fun update(item: SecureItem)
    
    /**
     * 删除项目
     */
    @Delete
    suspend fun delete(item: SecureItem)
    
    /**
     * 插入项目
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SecureItem): Long
    
    // =============== Bitwarden 同步相关方法 ===============
    
    /**
     * 根据 Bitwarden Cipher ID 获取项目
     */
    @Query("SELECT * FROM secure_items WHERE bitwarden_cipher_id = :cipherId LIMIT 1")
    suspend fun getByBitwardenCipherId(cipherId: String): SecureItem?
    
    /**
     * 获取指定 Vault 的所有项目
     */
    @Query("SELECT * FROM secure_items WHERE bitwarden_vault_id = :vaultId AND isDeleted = 0")
    suspend fun getByBitwardenVaultId(vaultId: Long): List<SecureItem>
    
    /**
     * 获取待上传到 Bitwarden 的项目（有 vaultId 但没有 cipherId）
     */
    @Query("SELECT * FROM secure_items WHERE bitwarden_vault_id = :vaultId AND bitwarden_cipher_id IS NULL AND isDeleted = 0")
    suspend fun getLocalEntriesPendingUpload(vaultId: Long): List<SecureItem>

    /**
     * 标记所有未关联 Bitwarden 的项目为指定 Vault
     */
    @Query("UPDATE secure_items SET bitwarden_vault_id = :vaultId WHERE bitwarden_vault_id IS NULL AND isDeleted = 0")
    suspend fun markAllForBitwarden(vaultId: Long)
    
    /**
     * 获取有本地修改需要同步的项目
     */
    @Query("SELECT * FROM secure_items WHERE bitwarden_vault_id = :vaultId AND bitwarden_local_modified = 1 AND isDeleted = 0")
    suspend fun getLocalModifiedEntries(vaultId: Long): List<SecureItem>
    
    /**
     * 获取指定 Vault 的项目数量
     */
    @Query("SELECT COUNT(*) FROM secure_items WHERE bitwarden_vault_id = :vaultId AND isDeleted = 0")
    suspend fun getBitwardenEntriesCount(vaultId: Long): Int

    /**
     * 删除指定 Vault 下所有已同步的 Bitwarden 安全项。
     * 仅删除未进入回收站的条目，避免覆盖本地删除墓碑。
     */
    @Query("""
        DELETE FROM secure_items
        WHERE bitwarden_vault_id = :vaultId
          AND bitwarden_cipher_id IS NOT NULL
          AND isDeleted = 0
    """)
    suspend fun deleteAllSyncedBitwardenEntries(vaultId: Long)

    /**
     * 清理服务器不存在的 Bitwarden 安全项（delete-wins）。
     */
    @Query("""
        DELETE FROM secure_items
        WHERE bitwarden_vault_id = :vaultId
          AND bitwarden_cipher_id IS NOT NULL
          AND isDeleted = 0
          AND bitwarden_cipher_id NOT IN (:keepIds)
          AND NOT EXISTS (
              SELECT 1
              FROM bitwarden_pending_operations op
              WHERE op.vault_id = :vaultId
                AND op.bitwarden_cipher_id = secure_items.bitwarden_cipher_id
                AND op.operation_type = 'RESTORE'
                AND op.status IN ('PENDING', 'IN_PROGRESS', 'FAILED')
          )
    """)
    suspend fun deleteBitwardenEntriesNotIn(vaultId: Long, keepIds: List<String>)
    
    /**
     * 标记项目为已同步
     */
    @Query("UPDATE secure_items SET sync_status = 'SYNCED', bitwarden_local_modified = 0, bitwarden_revision_date = :revisionDate WHERE id = :id")
    suspend fun markSynced(id: Long, revisionDate: String)
    
    /**
     * 标记项目有本地修改
     */
    @Query("UPDATE secure_items SET bitwarden_local_modified = 1, sync_status = 'PENDING' WHERE id = :id")
    suspend fun markLocalModified(id: Long)
}
