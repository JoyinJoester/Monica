package takagi.ru.monica.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for password entries
 */
@Dao
interface PasswordEntryDao {
    
    @Query("SELECT * FROM password_entries WHERE isDeleted = 0 ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    fun getAllPasswordEntries(): Flow<List<PasswordEntry>>
    
    @Query("SELECT * FROM password_entries WHERE isDeleted = 0 AND categoryId = :categoryId ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    fun getPasswordEntriesByCategory(categoryId: Long): Flow<List<PasswordEntry>>

    @Query("SELECT * FROM password_entries WHERE isDeleted = 0 AND categoryId IS NULL ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    fun getUncategorizedPasswordEntries(): Flow<List<PasswordEntry>>

    @Query("SELECT * FROM password_entries WHERE isDeleted = 0 AND keepassDatabaseId = :databaseId ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    fun getPasswordEntriesByKeePassDatabase(databaseId: Long): Flow<List<PasswordEntry>>

    @Query("SELECT * FROM password_entries WHERE isDeleted = 0 AND keepassDatabaseId = :databaseId AND keepassGroupPath = :groupPath ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    fun getPasswordEntriesByKeePassGroup(databaseId: Long, groupPath: String): Flow<List<PasswordEntry>>

    @Query("SELECT * FROM password_entries WHERE isDeleted = 0 AND keepassDatabaseId IS NULL ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    fun getPasswordEntriesWithoutKeePassDatabase(): Flow<List<PasswordEntry>>

    @Query("SELECT COUNT(*) FROM password_entries WHERE isDeleted = 0 AND keepassDatabaseId = :databaseId")
    fun getPasswordCountByKeePassDatabase(databaseId: Long): Flow<Int>

    @Query("SELECT * FROM password_entries WHERE isDeleted = 0 AND isFavorite = 1 ORDER BY sortOrder ASC, updatedAt DESC")
    fun getFavoritePasswordEntries(): Flow<List<PasswordEntry>>
    
    @Query("UPDATE password_entries SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun removeCategoryFromPasswords(categoryId: Long)
    
    @Query("SELECT * FROM password_entries WHERE isDeleted = 0 AND (title LIKE '%' || :query || '%' OR website LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' OR appName LIKE '%' || :query || '%' OR appPackageName LIKE '%' || :query || '%') ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    fun searchPasswordEntries(query: String): Flow<List<PasswordEntry>>
    
    @Query("SELECT * FROM password_entries WHERE id = :id")
    suspend fun getPasswordEntryById(id: Long): PasswordEntry?
    
    @Query("SELECT * FROM password_entries WHERE id IN (:ids)")
    suspend fun getPasswordsByIds(ids: List<Long>): List<PasswordEntry>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasswordEntry(entry: PasswordEntry): Long
    
    /**
     * 插入密码条目（别名方法，供 Bitwarden 同步服务使用）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PasswordEntry): Long
    
    @Update
    suspend fun updatePasswordEntry(entry: PasswordEntry)
    
    @Delete
    suspend fun deletePasswordEntry(entry: PasswordEntry)
    
    @Query("DELETE FROM password_entries WHERE id = :id")
    suspend fun deletePasswordEntryById(id: Long)
    
    @Query("UPDATE password_entries SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean)

    @Query("UPDATE password_entries SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateUpdatedAt(id: Long, updatedAt: java.util.Date)
    
    @Query("UPDATE password_entries SET isGroupCover = :isGroupCover WHERE id = :id")
    suspend fun updateGroupCoverStatus(id: Long, isGroupCover: Boolean)
    
    @Query("UPDATE password_entries SET isGroupCover = 0 WHERE website = :website")
    suspend fun clearGroupCover(website: String)
    
    @Query("UPDATE password_entries SET categoryId = :categoryId WHERE id IN (:ids)")
    suspend fun updateCategoryForPasswords(ids: List<Long>, categoryId: Long?)

    /**
     * 将指定条目绑定到 Bitwarden 文件夹
     * 仅作用于非 KeePass 条目，避免跨数据源污染。
     */
    @Query("""
        UPDATE password_entries
        SET bitwarden_vault_id = :vaultId,
            bitwarden_folder_id = :folderId,
            bitwarden_local_modified = CASE
                WHEN bitwarden_cipher_id IS NOT NULL AND COALESCE(bitwarden_folder_id, '') != :folderId THEN 1
                ELSE bitwarden_local_modified
            END
        WHERE id IN (:ids)
          AND keepassDatabaseId IS NULL
          AND isDeleted = 0
    """)
    suspend fun bindPasswordsToBitwardenFolder(ids: List<Long>, vaultId: Long, folderId: String)

    /**
     * 清理未上传条目的 Bitwarden 绑定
     * 仅清理还没有 cipherId 的本地待上传条目，避免破坏已同步映射。
     */
    @Query("""
        UPDATE password_entries
        SET bitwarden_vault_id = NULL,
            bitwarden_folder_id = NULL,
            bitwarden_local_modified = 0
        WHERE id IN (:ids)
          AND bitwarden_cipher_id IS NULL
          AND isDeleted = 0
    """)
    suspend fun clearPendingBitwardenBinding(ids: List<Long>)

    /**
     * 按分类批量绑定到 Bitwarden（用于“立即应用”）
     */
    @Query("""
        UPDATE password_entries
        SET bitwarden_vault_id = :vaultId,
            bitwarden_folder_id = :folderId,
            bitwarden_local_modified = CASE
                WHEN bitwarden_cipher_id IS NOT NULL AND COALESCE(bitwarden_folder_id, '') != :folderId THEN 1
                ELSE bitwarden_local_modified
            END
        WHERE categoryId = :categoryId
          AND keepassDatabaseId IS NULL
          AND isDeleted = 0
    """)
    suspend fun bindCategoryToBitwarden(categoryId: Long, vaultId: Long, folderId: String)
    
    @Query("UPDATE password_entries SET keepassDatabaseId = :databaseId, keepassGroupPath = NULL WHERE id IN (:ids)")
    suspend fun updateKeePassDatabaseForPasswords(ids: List<Long>, databaseId: Long?)

    @Query("UPDATE password_entries SET keepassDatabaseId = :databaseId, keepassGroupPath = :groupPath WHERE id IN (:ids)")
    suspend fun updateKeePassGroupForPasswords(ids: List<Long>, databaseId: Long, groupPath: String)

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

    /**
     * 更新绑定的验证器密钥
     */
    @Query("UPDATE password_entries SET authenticatorKey = :authenticatorKey WHERE id = :id")
    suspend fun updateAuthenticatorKey(id: Long, authenticatorKey: String)

    /**
     * 更新绑定的通行密钥元数据
     */
    @Query("UPDATE password_entries SET passkey_bindings = :passkeyBindings WHERE id = :id")
    suspend fun updatePasskeyBindings(id: Long, passkeyBindings: String)

    
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

    @Query("SELECT * FROM password_entries WHERE keepassDatabaseId = :databaseId AND title = :title AND username = :username AND website = :website LIMIT 1")
    suspend fun findDuplicateEntryInKeePass(databaseId: Long, title: String, username: String, website: String): PasswordEntry?
    
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
    
    // =============== 回收站相关方法 ===============
    
    /**
     * 获取所有已删除的条目（回收站）
     */
    @Query("SELECT * FROM password_entries WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedEntries(): Flow<List<PasswordEntry>>
    
    /**
     * 获取所有已删除的条目（同步版本，用于备份）
     */
    @Query("SELECT * FROM password_entries WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    suspend fun getDeletedEntriesSync(): List<PasswordEntry>
    
    /**
     * 获取所有未删除的密码条目（同步版本，用于KeePass导出）
     */
    @Query("SELECT * FROM password_entries WHERE isDeleted = 0 ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    suspend fun getAllPasswordEntriesSync(): List<PasswordEntry>
    
    /**
     * 检查是否存在相同的条目（用于KeePass导入去重）
     */
    @Query("SELECT COUNT(*) FROM password_entries WHERE title = :title AND username = :username AND website = :website")
    suspend fun countByTitleUsernameWebsite(title: String, username: String, website: String): Int
    
    /**
     * 获取所有未删除的条目（正常条目）
     */
    @Query("SELECT * FROM password_entries WHERE isDeleted = 0 ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    fun getActiveEntries(): Flow<List<PasswordEntry>>
    
    /**
     * 软删除条目（移动到回收站）
     */
    @Query("UPDATE password_entries SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long = System.currentTimeMillis())
    
    /**
     * 恢复已删除的条目
     */
    @Query("UPDATE password_entries SET isDeleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)
    
    /**
     * 永久删除所有回收站中的条目
     */
    @Query("DELETE FROM password_entries WHERE isDeleted = 1")
    suspend fun permanentlyDeleteAll()
    
    /**
     * 删除过期的回收站条目（超过指定天数）
     */
    @Query("DELETE FROM password_entries WHERE isDeleted = 1 AND deletedAt < :cutoffDate")
    suspend fun deleteExpiredItems(cutoffDate: java.util.Date)
    
    /**
     * 获取回收站条目数量
     */
    @Query("SELECT COUNT(*) FROM password_entries WHERE isDeleted = 1")
    suspend fun getDeletedCount(): Int
    
    /**
     * 更新条目（用于恢复等操作）
     */
    @Update
    suspend fun update(entry: PasswordEntry)
    
    /**
     * 删除条目
     */
    @Delete
    suspend fun delete(entry: PasswordEntry)
    
    // ==================== Bitwarden 集成相关方法 ====================
    
    /**
     * 根据 Bitwarden Cipher ID 获取条目
     * 用于同步时查找本地是否已存在对应条目
     */
    @Query("SELECT * FROM password_entries WHERE bitwarden_cipher_id = :cipherId LIMIT 1")
    suspend fun getByBitwardenCipherId(cipherId: String): PasswordEntry?

        /**
         * 查找本地重复条目（仅本地库）
         * 用于 Bitwarden 同步时合并本地条目，避免重复
         */
        @Query("""
                SELECT * FROM password_entries
                WHERE bitwarden_vault_id IS NULL
                    AND keepassDatabaseId IS NULL
                    AND isDeleted = 0
                    AND LOWER(title) = :title
                    AND LOWER(username) = :username
                    AND LOWER(website) = :website
                LIMIT 1
        """)
        suspend fun findLocalDuplicateByKey(title: String, username: String, website: String): PasswordEntry?
    
    /**
     * 根据 Bitwarden Vault ID 获取所有条目
     * 用于获取某个 Vault 的所有密码
     */
    @Query("SELECT * FROM password_entries WHERE bitwarden_vault_id = :vaultId AND isDeleted = 0 ORDER BY title ASC")
    suspend fun getByBitwardenVaultId(vaultId: Long): List<PasswordEntry>
    
    /**
     * 根据 Bitwarden Vault ID 获取所有条目 (Flow 版本)
     * 用于实时观察 Vault 的密码列表变化
     */
    @Query("SELECT * FROM password_entries WHERE bitwarden_vault_id = :vaultId AND isDeleted = 0 ORDER BY title ASC")
    fun getByBitwardenVaultIdFlow(vaultId: Long): kotlinx.coroutines.flow.Flow<List<PasswordEntry>>
    
    /**
     * 获取所有 Bitwarden 条目
     */
    @Query("SELECT * FROM password_entries WHERE bitwarden_vault_id IS NOT NULL AND isDeleted = 0 ORDER BY title ASC")
    suspend fun getAllBitwardenEntries(): List<PasswordEntry>
    
    /**
     * 获取待同步到 Bitwarden 的条目（本地有修改）
     */
    @Query("SELECT * FROM password_entries WHERE bitwarden_vault_id = :vaultId AND bitwarden_local_modified = 1 AND isDeleted = 0")
    suspend fun getEntriesWithPendingBitwardenSync(vaultId: Long): List<PasswordEntry>
    
    /**
     * 获取所有待同步的 Bitwarden 条目（跨所有 Vault）
     */
    @Query("SELECT * FROM password_entries WHERE bitwarden_vault_id IS NOT NULL AND bitwarden_local_modified = 1 AND isDeleted = 0")
    suspend fun getAllEntriesWithPendingBitwardenSync(): List<PasswordEntry>
    
    /**
     * 清除指定 Vault 的所有条目的本地修改标记
     */
    @Query("UPDATE password_entries SET bitwarden_local_modified = 0 WHERE bitwarden_vault_id = :vaultId")
    suspend fun clearBitwardenLocalModifiedFlag(vaultId: Long)
    
    /**
     * 更新条目的 Bitwarden 同步信息
     */
    @Query("""
        UPDATE password_entries 
        SET bitwarden_revision_date = :revisionDate, 
            bitwarden_local_modified = 0 
        WHERE id = :entryId
    """)
    suspend fun updateBitwardenSyncInfo(entryId: Long, revisionDate: Long)
    
    @Query("SELECT * FROM password_entries WHERE bitwarden_folder_id = :folderId AND isDeleted = 0 ORDER BY title ASC")
    fun getByBitwardenFolderIdFlow(folderId: String): kotlinx.coroutines.flow.Flow<List<PasswordEntry>>

    /**
     * 根据 Bitwarden Folder ID 获取条目
     */
    @Query("SELECT * FROM password_entries WHERE bitwarden_folder_id = :folderId AND isDeleted = 0 ORDER BY title ASC")
    suspend fun getByBitwardenFolderId(folderId: String): List<PasswordEntry>
    
    /**
     * 获取 Bitwarden 条目按类型分组
     */
    @Query("SELECT * FROM password_entries WHERE bitwarden_vault_id = :vaultId AND bitwarden_cipher_type = :cipherType AND isDeleted = 0 ORDER BY title ASC")
    suspend fun getBitwardenEntriesByCipherType(vaultId: Long, cipherType: Int): List<PasswordEntry>
    
    /**
     * 搜索 Bitwarden 条目
     */
    @Query("""
        SELECT * FROM password_entries 
        WHERE bitwarden_vault_id = :vaultId 
        AND isDeleted = 0 
        AND (title LIKE '%' || :query || '%' 
             OR username LIKE '%' || :query || '%' 
             OR notes LIKE '%' || :query || '%')
        ORDER BY title ASC
    """)
    suspend fun searchBitwardenEntries(vaultId: Long, query: String): List<PasswordEntry>
    
    /**
     * 删除指定 Vault 的所有条目（用于注销时清理）
     */
    @Query("DELETE FROM password_entries WHERE bitwarden_vault_id = :vaultId")
    suspend fun deleteAllByBitwardenVaultId(vaultId: Long)
    
    /**
     * 获取 Bitwarden 条目数量
     */
    @Query("SELECT COUNT(*) FROM password_entries WHERE bitwarden_vault_id = :vaultId AND isDeleted = 0")
    suspend fun getBitwardenEntryCount(vaultId: Long): Int
    
    /**
     * 获取指定 Vault 的最后修改时间
     */
    @Query("SELECT MAX(bitwarden_revision_date) FROM password_entries WHERE bitwarden_vault_id = :vaultId")
    suspend fun getLastBitwardenRevisionDate(vaultId: Long): Long?
    
    // ==================== V2 多源密码库相关方法 ====================
    
    /**
     * 获取纯本地条目数量（非 Bitwarden、非 KeePass）
     * 用于 V2 多源密码库统计
     */
    @Query("SELECT COUNT(*) FROM password_entries WHERE bitwarden_vault_id IS NULL AND keepassDatabaseId IS NULL AND isDeleted = 0")
    suspend fun getLocalEntriesCount(): Int

    /**
     * 获取指定 Bitwarden Vault 的条目数量
     * 用于 V2 多源密码库统计
     */
    @Query("SELECT COUNT(*) FROM password_entries WHERE bitwarden_vault_id = :vaultId AND isDeleted = 0")
    suspend fun getBitwardenEntriesCount(vaultId: Long): Int
    
    /**
     * 获取 KeePass 条目数量
     * 用于 V2 多源密码库统计
     */
    @Query("SELECT COUNT(*) FROM password_entries WHERE keepassDatabaseId IS NOT NULL AND isDeleted = 0")
    suspend fun getKeePassEntriesCount(): Int

    /**
     * 获取所有纯本地条目（非 Bitwarden、非 KeePass）
     * 用于 V2 多源密码库显示
     */
    @Query("SELECT * FROM password_entries WHERE bitwarden_vault_id IS NULL AND keepassDatabaseId IS NULL AND isDeleted = 0 ORDER BY isFavorite DESC, updatedAt DESC")
    suspend fun getAllLocalEntries(): List<PasswordEntry>
    
    /**
     * 获取所有 KeePass 条目
     * 用于 V2 多源密码库显示
     */
    @Query("SELECT * FROM password_entries WHERE keepassDatabaseId IS NOT NULL AND isDeleted = 0 ORDER BY isFavorite DESC, updatedAt DESC")
    suspend fun getAllKeePassEntries(): List<PasswordEntry>
    /**
     * 获取指定 Bitwarden Vault 的所有条目
     * 用于 V2 多源密码库显示
     */
    @Query("SELECT * FROM password_entries WHERE bitwarden_vault_id = :vaultId AND isDeleted = 0 ORDER BY isFavorite DESC, updatedAt DESC")
    suspend fun getEntriesByVaultId(vaultId: Long): List<PasswordEntry>
    
    /**
     * 获取待上传到 Bitwarden 的本地条目
     * 这些条目有 bitwardenVaultId（表示属于某个 Bitwarden vault）
     * 但没有 bitwardenCipherId（表示尚未上传到服务器）
     */
    @Query("SELECT * FROM password_entries WHERE bitwarden_vault_id = :vaultId AND bitwarden_cipher_id IS NULL AND isDeleted = 0")
    suspend fun getLocalEntriesPendingUpload(vaultId: Long): List<PasswordEntry>

    /**
     * 标记所有未关联 Bitwarden 的条目为指定 Vault
     */
    @Query("UPDATE password_entries SET bitwarden_vault_id = :vaultId WHERE bitwarden_vault_id IS NULL AND isDeleted = 0")
    suspend fun markAllForBitwarden(vaultId: Long)
    
    /**
     * 获取本地已修改但未同步的 Bitwarden 条目
     */
    @Query("SELECT * FROM password_entries WHERE bitwarden_vault_id = :vaultId AND bitwarden_local_modified = 1 AND isDeleted = 0")
    suspend fun getLocalModifiedBitwardenEntries(vaultId: Long): List<PasswordEntry>
}
