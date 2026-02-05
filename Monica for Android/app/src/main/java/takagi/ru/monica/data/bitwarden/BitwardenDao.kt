package takagi.ru.monica.data.bitwarden

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Bitwarden Vault DAO
 * 
 * 安全规则:
 * - 所有删除操作都是软删除，除非明确需要硬删除
 * - 敏感数据（令牌、密钥）的更新必须使用事务
 */
@Dao
interface BitwardenVaultDao {
    
    // === 查询操作 ===
    
    @Query("SELECT * FROM bitwarden_vaults ORDER BY is_default DESC, created_at DESC")
    fun getAllVaultsFlow(): Flow<List<BitwardenVault>>
    
    @Query("SELECT * FROM bitwarden_vaults ORDER BY is_default DESC, created_at DESC")
    suspend fun getAllVaults(): List<BitwardenVault>
    
    @Query("SELECT * FROM bitwarden_vaults WHERE id = :id")
    suspend fun getVaultById(id: Long): BitwardenVault?
    
    @Query("SELECT * FROM bitwarden_vaults WHERE id = :id")
    fun getVaultByIdFlow(id: Long): Flow<BitwardenVault?>
    
    @Query("SELECT * FROM bitwarden_vaults WHERE email = :email LIMIT 1")
    suspend fun getVaultByEmail(email: String): BitwardenVault?
    
    @Query("SELECT * FROM bitwarden_vaults WHERE is_default = 1 LIMIT 1")
    suspend fun getDefaultVault(): BitwardenVault?
    
    @Query("SELECT * FROM bitwarden_vaults WHERE is_default = 1 LIMIT 1")
    fun getDefaultVaultFlow(): Flow<BitwardenVault?>
    
    @Query("SELECT COUNT(*) FROM bitwarden_vaults")
    suspend fun getVaultCount(): Int
    
    // === 插入操作 ===
    
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(vault: BitwardenVault): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vault: BitwardenVault): Long
    
    // === 更新操作 ===
    
    @Update
    suspend fun update(vault: BitwardenVault)
    
    /**
     * 更新访问令牌 - 原子操作
     */
    @Query("""
        UPDATE bitwarden_vaults 
        SET encrypted_access_token = :encryptedAccessToken,
            access_token_expires_at = :expiresAt,
            updated_at = :updatedAt
        WHERE id = :vaultId
    """)
    suspend fun updateAccessToken(
        vaultId: Long,
        encryptedAccessToken: String,
        expiresAt: Long,
        updatedAt: Long = System.currentTimeMillis()
    )
    
    /**
     * 更新刷新令牌 - 原子操作
     */
    @Query("""
        UPDATE bitwarden_vaults 
        SET encrypted_refresh_token = :encryptedRefreshToken,
            updated_at = :updatedAt
        WHERE id = :vaultId
    """)
    suspend fun updateRefreshToken(
        vaultId: Long,
        encryptedRefreshToken: String,
        updatedAt: Long = System.currentTimeMillis()
    )
    
    /**
     * 更新加密密钥 - 原子操作
     */
    @Query("""
        UPDATE bitwarden_vaults 
        SET encrypted_master_key = :encryptedMasterKey,
            encrypted_enc_key = :encryptedEncKey,
            encrypted_mac_key = :encryptedMacKey,
            updated_at = :updatedAt
        WHERE id = :vaultId
    """)
    suspend fun updateEncryptionKeys(
        vaultId: Long,
        encryptedMasterKey: String,
        encryptedEncKey: String,
        encryptedMacKey: String,
        updatedAt: Long = System.currentTimeMillis()
    )
    
    /**
     * 更新同步状态
     */
    @Query("""
        UPDATE bitwarden_vaults 
        SET last_sync_at = :lastSyncAt,
            revision_date = :revisionDate,
            updated_at = :updatedAt
        WHERE id = :vaultId
    """)
    suspend fun updateSyncStatus(
        vaultId: Long,
        lastSyncAt: Long,
        revisionDate: String?,
        updatedAt: Long = System.currentTimeMillis()
    )
    
    /**
     * 设置锁定状态
     */
    @Query("UPDATE bitwarden_vaults SET is_locked = :isLocked, updated_at = :updatedAt WHERE id = :vaultId")
    suspend fun setLocked(vaultId: Long, isLocked: Boolean, updatedAt: Long = System.currentTimeMillis())
    
    /**
     * 设置连接状态
     */
    @Query("UPDATE bitwarden_vaults SET is_connected = :isConnected, updated_at = :updatedAt WHERE id = :vaultId")
    suspend fun setConnected(vaultId: Long, isConnected: Boolean, updatedAt: Long = System.currentTimeMillis())
    
    /**
     * 设置默认 Vault (清除其他默认)
     */
    @Transaction
    suspend fun setAsDefault(vaultId: Long) {
        clearDefaultFlag()
        setDefaultFlag(vaultId)
    }
    
    @Query("UPDATE bitwarden_vaults SET is_default = 0")
    suspend fun clearDefaultFlag()
    
    @Query("UPDATE bitwarden_vaults SET is_default = 1, updated_at = :updatedAt WHERE id = :vaultId")
    suspend fun setDefaultFlag(vaultId: Long, updatedAt: Long = System.currentTimeMillis())
    
    // === 删除操作 ===
    
    /**
     * 删除 Vault - 需谨慎！
     * 建议先备份或确认用户意图
     */
    @Delete
    suspend fun delete(vault: BitwardenVault)
    
    @Query("DELETE FROM bitwarden_vaults WHERE id = :vaultId")
    suspend fun deleteById(vaultId: Long)
    
    /**
     * 清除敏感数据（登出时使用）
     */
    @Query("""
        UPDATE bitwarden_vaults 
        SET encrypted_access_token = NULL,
            encrypted_refresh_token = NULL,
            access_token_expires_at = NULL,
            encrypted_master_key = NULL,
            encrypted_enc_key = NULL,
            encrypted_mac_key = NULL,
            is_locked = 1,
            is_connected = 0,
            updated_at = :updatedAt
        WHERE id = :vaultId
    """)
    suspend fun clearSensitiveData(vaultId: Long, updatedAt: Long = System.currentTimeMillis())
}

/**
 * Bitwarden Folder DAO
 */
@Dao
interface BitwardenFolderDao {
    
    @Query("SELECT * FROM bitwarden_folders WHERE vault_id = :vaultId ORDER BY name ASC")
    fun getFoldersByVaultFlow(vaultId: Long): Flow<List<BitwardenFolder>>
    
    @Query("SELECT * FROM bitwarden_folders WHERE vault_id = :vaultId ORDER BY name ASC")
    suspend fun getFoldersByVault(vaultId: Long): List<BitwardenFolder>
    
    @Query("SELECT * FROM bitwarden_folders WHERE id = :id")
    suspend fun getFolderById(id: Long): BitwardenFolder?
    
    @Query("SELECT * FROM bitwarden_folders WHERE bitwarden_folder_id = :bitwardenId")
    suspend fun getFolderByBitwardenId(bitwardenId: String): BitwardenFolder?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: BitwardenFolder): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: BitwardenFolder): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(folders: List<BitwardenFolder>)
    
    @Update
    suspend fun update(folder: BitwardenFolder)
    
    @Delete
    suspend fun delete(folder: BitwardenFolder)
    
    @Query("DELETE FROM bitwarden_folders WHERE vault_id = :vaultId")
    suspend fun deleteByVault(vaultId: Long)
    
    @Query("DELETE FROM bitwarden_folders WHERE bitwarden_folder_id = :bitwardenId")
    suspend fun deleteByBitwardenId(bitwardenId: String)
    
    @Query("UPDATE bitwarden_folders SET name = :newName, encrypted_name = :encryptedName WHERE bitwarden_folder_id = :folderId")
    suspend fun updateName(folderId: String, newName: String, encryptedName: String)
    
    @Query("DELETE FROM bitwarden_folders WHERE bitwarden_folder_id NOT IN (:keepIds) AND vault_id = :vaultId")
    suspend fun deleteNotIn(vaultId: Long, keepIds: List<String>)
}

/**
 * Bitwarden 冲突备份 DAO
 */
@Dao
interface BitwardenConflictBackupDao {
    
    @Query("SELECT * FROM bitwarden_conflict_backups WHERE is_resolved = 0 ORDER BY created_at DESC")
    fun getUnresolvedConflictsFlow(): Flow<List<BitwardenConflictBackup>>
    
    @Query("SELECT * FROM bitwarden_conflict_backups WHERE is_resolved = 0 ORDER BY created_at DESC")
    suspend fun getUnresolvedConflicts(): List<BitwardenConflictBackup>
    
    @Query("SELECT * FROM bitwarden_conflict_backups WHERE vault_id = :vaultId AND is_resolved = 0")
    suspend fun getUnresolvedConflictsByVault(vaultId: Long): List<BitwardenConflictBackup>
    
    @Query("SELECT COUNT(*) FROM bitwarden_conflict_backups WHERE is_resolved = 0")
    fun getUnresolvedCountFlow(): Flow<Int>
    
    @Query("SELECT * FROM bitwarden_conflict_backups WHERE id = :id")
    suspend fun getById(id: Long): BitwardenConflictBackup?
    
    @Insert
    suspend fun insert(conflict: BitwardenConflictBackup): Long
    
    @Update
    suspend fun update(conflict: BitwardenConflictBackup)
    
    /**
     * 标记冲突已解决
     */
    @Query("""
        UPDATE bitwarden_conflict_backups 
        SET is_resolved = 1, 
            resolution = :resolution,
            resolved_at = :resolvedAt
        WHERE id = :id
    """)
    suspend fun markResolved(id: Long, resolution: String, resolvedAt: Long = System.currentTimeMillis())
    
    /**
     * 删除已解决的冲突 (超过保留期限)
     */
    @Query("DELETE FROM bitwarden_conflict_backups WHERE is_resolved = 1 AND resolved_at < :beforeTimestamp")
    suspend fun deleteResolvedBefore(beforeTimestamp: Long)
}

/**
 * Bitwarden 待处理操作 DAO
 */
@Dao
interface BitwardenPendingOperationDao {
    
    @Query("SELECT * FROM bitwarden_pending_operations WHERE status = 'PENDING' ORDER BY created_at ASC")
    fun getPendingOperationsFlow(): Flow<List<BitwardenPendingOperation>>
    
    @Query("SELECT * FROM bitwarden_pending_operations WHERE status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPendingOperations(): List<BitwardenPendingOperation>
    
    @Query("SELECT * FROM bitwarden_pending_operations WHERE vault_id = :vaultId AND status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPendingOperationsByVault(vaultId: Long): List<BitwardenPendingOperation>
    
    @Query("SELECT COUNT(*) FROM bitwarden_pending_operations WHERE status = 'PENDING'")
    fun getPendingCountFlow(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM bitwarden_pending_operations WHERE status = 'FAILED'")
    fun getFailedCountFlow(): Flow<Int>
    
    @Query("SELECT * FROM bitwarden_pending_operations WHERE status = 'FAILED' ORDER BY created_at DESC")
    fun getFailedOperationsFlow(): Flow<List<BitwardenPendingOperation>>
    
    @Query("SELECT * FROM bitwarden_pending_operations WHERE status = 'FAILED' ORDER BY created_at DESC")
    suspend fun getFailedOperations(): List<BitwardenPendingOperation>
    
    @Query("SELECT * FROM bitwarden_pending_operations WHERE status IN ('PENDING', 'FAILED') ORDER BY created_at ASC")
    fun getAllPendingAndFailedFlow(): Flow<List<BitwardenPendingOperation>>
    
    @Query("SELECT * FROM bitwarden_pending_operations WHERE id = :id")
    suspend fun getById(id: Long): BitwardenPendingOperation?
    
    @Query("SELECT * FROM bitwarden_pending_operations WHERE entry_id = :entryId AND item_type = :itemType AND status IN ('PENDING', 'IN_PROGRESS')")
    suspend fun findPendingByEntryAndType(entryId: Long, itemType: String): BitwardenPendingOperation?
    
    @Insert
    suspend fun insert(operation: BitwardenPendingOperation): Long
    
    @Update
    suspend fun update(operation: BitwardenPendingOperation)
    
    /**
     * 更新操作状态
     */
    @Query("""
        UPDATE bitwarden_pending_operations 
        SET status = :status,
            last_attempt_at = :lastAttemptAt,
            retry_count = retry_count + 1,
            last_error = :lastError
        WHERE id = :id
    """)
    suspend fun updateStatus(
        id: Long,
        status: String,
        lastAttemptAt: Long = System.currentTimeMillis(),
        lastError: String? = null
    )
    
    /**
     * 标记操作完成
     */
    @Query("""
        UPDATE bitwarden_pending_operations 
        SET status = 'COMPLETED',
            completed_at = :completedAt
        WHERE id = :id
    """)
    suspend fun markCompleted(id: Long, completedAt: Long = System.currentTimeMillis())
    
    /**
     * 更新 Bitwarden Cipher ID（创建成功后）
     */
    @Query("""
        UPDATE bitwarden_pending_operations 
        SET bitwarden_cipher_id = :cipherId
        WHERE id = :id
    """)
    suspend fun updateCipherId(id: Long, cipherId: String)
    
    /**
     * 取消操作
     */
    @Query("UPDATE bitwarden_pending_operations SET status = 'CANCELLED' WHERE id = :id")
    suspend fun cancel(id: Long)
    
    /**
     * 重置失败操作为待处理状态（手动重试）
     */
    @Query("""
        UPDATE bitwarden_pending_operations 
        SET status = 'PENDING',
            retry_count = 0,
            last_error = NULL,
            last_attempt_at = NULL
        WHERE id = :id AND status = 'FAILED'
    """)
    suspend fun resetForRetry(id: Long)
    
    /**
     * 批量重置所有失败操作
     */
    @Query("""
        UPDATE bitwarden_pending_operations 
        SET status = 'PENDING',
            retry_count = 0,
            last_error = NULL,
            last_attempt_at = NULL
        WHERE status = 'FAILED'
    """)
    suspend fun resetAllFailedForRetry()
    
    /**
     * 删除已完成的操作 (超过保留期限)
     */
    @Query("DELETE FROM bitwarden_pending_operations WHERE status = 'COMPLETED' AND completed_at < :beforeTimestamp")
    suspend fun deleteCompletedBefore(beforeTimestamp: Long)
    
    /**
     * 删除条目相关的待处理操作
     */
    @Query("DELETE FROM bitwarden_pending_operations WHERE entry_id = :entryId AND status = 'PENDING'")
    suspend fun deletePendingForEntry(entryId: Long)
    
    /**
     * 删除条目相关的所有待处理操作（指定类型）
     */
    @Query("DELETE FROM bitwarden_pending_operations WHERE entry_id = :entryId AND item_type = :itemType AND status IN ('PENDING', 'FAILED')")
    suspend fun deletePendingForEntryAndType(entryId: Long, itemType: String)
    
    /**
     * 获取指定 Vault 的待同步数量统计
     */
    @Query("""
        SELECT item_type, COUNT(*) as count 
        FROM bitwarden_pending_operations 
        WHERE vault_id = :vaultId AND status = 'PENDING' 
        GROUP BY item_type
    """)
    suspend fun getPendingCountByType(vaultId: Long): List<ItemTypeCount>
}

/**
 * 用于统计各类型待同步数量的数据类
 */
data class ItemTypeCount(
    @ColumnInfo(name = "item_type") val itemType: String,
    @ColumnInfo(name = "count") val count: Int
)
