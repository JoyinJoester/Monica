package takagi.ru.monica.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Passkey 数据访问对象
 */
@Dao
interface PasskeyDao {
    
    // ==================== 查询操作 ====================
    
    /**
     * 获取所有 Passkey（按最后使用时间降序）
     */
    @Query("SELECT * FROM passkeys ORDER BY last_used_at DESC")
    fun getAllPasskeys(): Flow<List<PasskeyEntry>>
    
    /**
     * 获取所有 Passkey（同步版本）
     */
    @Query("SELECT * FROM passkeys ORDER BY last_used_at DESC")
    suspend fun getAllPasskeysSync(): List<PasskeyEntry>

    /**
     * 根据凭据 ID 获取 Passkey
     */
    @Query("SELECT * FROM passkeys WHERE credential_id = :credentialId")
    suspend fun getPasskeyById(credentialId: String): PasskeyEntry?
    
    /**
     * 根据依赖方 ID (域名) 获取 Passkeys
     */
    @Query("SELECT * FROM passkeys WHERE rp_id = :rpId ORDER BY last_used_at DESC")
    fun getPasskeysByRpId(rpId: String): Flow<List<PasskeyEntry>>
    
    /**
     * 根据依赖方 ID (域名) 获取 Passkeys（同步版本）
     */
    @Query("SELECT * FROM passkeys WHERE rp_id = :rpId ORDER BY last_used_at DESC")
    suspend fun getPasskeysByRpIdSync(rpId: String): List<PasskeyEntry>
    
    /**
     * 搜索 Passkey（按域名、用户名、显示名搜索）
     */
    @Query("""
        SELECT * FROM passkeys 
        WHERE rp_id LIKE '%' || :query || '%' 
           OR rp_name LIKE '%' || :query || '%'
           OR user_name LIKE '%' || :query || '%'
           OR user_display_name LIKE '%' || :query || '%'
        ORDER BY last_used_at DESC
    """)
    fun searchPasskeys(query: String): Flow<List<PasskeyEntry>>
    
    /**
     * 获取可发现的 Passkeys（用于 Credential Provider 展示）
     */
    @Query("SELECT * FROM passkeys WHERE is_discoverable = 1 ORDER BY last_used_at DESC")
    suspend fun getDiscoverablePasskeys(): List<PasskeyEntry>
    
    /**
     * 获取可发现的 Passkeys（同步版本，用于 Service）
     */
    @Query("SELECT * FROM passkeys WHERE is_discoverable = 1 ORDER BY last_used_at DESC")
    suspend fun getDiscoverablePasskeysSync(): List<PasskeyEntry>
    
    /**
     * 获取可发现的 Passkeys 按域名过滤
     */
    @Query("SELECT * FROM passkeys WHERE is_discoverable = 1 AND rp_id = :rpId ORDER BY last_used_at DESC")
    suspend fun getDiscoverablePasskeysByRpId(rpId: String): List<PasskeyEntry>
    
    /**
     * 获取 Passkey 总数
     */
    @Query("SELECT COUNT(*) FROM passkeys")
    fun getPasskeyCount(): Flow<Int>
    
    /**
     * 获取未备份的 Passkeys（用于 WebDAV 同步）
     */
    @Query("SELECT * FROM passkeys WHERE is_backed_up = 0")
    suspend fun getUnbackedPasskeys(): List<PasskeyEntry>
    
    // ==================== 插入操作 ====================
    
    /**
     * 插入 Passkey
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(passkey: PasskeyEntry)
    
    /**
     * 批量插入 Passkeys
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(passkeys: List<PasskeyEntry>)
    
    // ==================== 更新操作 ====================
    
    /**
     * 更新 Passkey
     */
    @Update
    suspend fun update(passkey: PasskeyEntry)
    
    /**
     * 更新最后使用时间和使用次数
     */
    @Query("""
        UPDATE passkeys 
        SET last_used_at = :timestamp, 
            use_count = use_count + 1,
            sign_count = :signCount
        WHERE credential_id = :credentialId
    """)
    suspend fun updateUsage(credentialId: String, timestamp: Long = System.currentTimeMillis(), signCount: Long)
    
    /**
     * 标记为已备份
     */
    @Query("UPDATE passkeys SET is_backed_up = 1 WHERE credential_id = :credentialId")
    suspend fun markAsBackedUp(credentialId: String)
    
    /**
     * 批量标记为已备份
     */
    @Query("UPDATE passkeys SET is_backed_up = 1 WHERE credential_id IN (:credentialIds)")
    suspend fun markAllAsBackedUp(credentialIds: List<String>)
    
    // ==================== 删除操作 ====================
    
    /**
     * 删除 Passkey
     */
    @Delete
    suspend fun delete(passkey: PasskeyEntry)

    /**
     * 删除所有 Passkey（用于覆盖恢复）
     */
    @Query("DELETE FROM passkeys")
    suspend fun deleteAllPasskeys()
    
    /**
     * 根据凭据 ID 删除 Passkey
     */
    @Query("DELETE FROM passkeys WHERE credential_id = :credentialId")
    suspend fun deleteById(credentialId: String)
    
    /**
     * 删除指定域名的所有 Passkeys
     */
    @Query("DELETE FROM passkeys WHERE rp_id = :rpId")
    suspend fun deleteByRpId(rpId: String)
    
    /**
     * 清空所有 Passkeys
     */
    @Query("DELETE FROM passkeys")
    suspend fun deleteAll()
    
    // =============== Bitwarden 同步相关方法 ===============
    
    /**
     * 根据 Bitwarden Cipher ID 获取 Passkey
     */
    @Query("SELECT * FROM passkeys WHERE bitwarden_cipher_id = :cipherId LIMIT 1")
    suspend fun getByBitwardenCipherId(cipherId: String): PasskeyEntry?
    
    /**
     * 获取指定 Vault 的所有 Passkeys
     */
    @Query("SELECT * FROM passkeys WHERE bitwarden_vault_id = :vaultId")
    suspend fun getByBitwardenVaultId(vaultId: Long): List<PasskeyEntry>

    /**
     * 获取绑定到指定密码的 Passkeys
     */
    @Query("SELECT * FROM passkeys WHERE bound_password_id = :passwordId ORDER BY last_used_at DESC")
    fun getByBoundPasswordId(passwordId: Long): Flow<List<PasskeyEntry>>

    /**
     * 更新绑定的密码 ID
     */
    @Query("UPDATE passkeys SET bound_password_id = :passwordId WHERE credential_id = :credentialId")
    suspend fun updateBoundPasswordId(credentialId: String, passwordId: Long?)
    
    /**
     * 获取待上传到 Bitwarden 的 Passkeys
     */
    @Query("SELECT * FROM passkeys WHERE bitwarden_vault_id = :vaultId AND bitwarden_cipher_id IS NULL")
    suspend fun getLocalEntriesPendingUpload(vaultId: Long): List<PasskeyEntry>

    /**
     * 标记所有未关联 Bitwarden 的 Passkeys 为指定 Vault
     */
    @Query("UPDATE passkeys SET bitwarden_vault_id = :vaultId WHERE bitwarden_vault_id IS NULL")
    suspend fun markAllForBitwarden(vaultId: Long)
    
    /**
     * 标记 Passkey 为已同步
     */
    @Query("UPDATE passkeys SET sync_status = 'SYNCED', bitwarden_cipher_id = :cipherId WHERE credential_id = :credentialId")
    suspend fun markSynced(credentialId: String, cipherId: String)
}
