package takagi.ru.monica.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import takagi.ru.monica.data.PasskeyDao
import takagi.ru.monica.data.PasskeyEntry
import java.security.KeyStore

/**
 * Passkey 数据仓库
 * 
 * 提供 Passkey 数据的访问接口，封装 DAO 操作
 * 负责在删除 Passkey 时同步清理 Android Keystore 中的私钥
 */
class PasskeyRepository(private val passkeyDao: PasskeyDao) {
    
    companion object {
        private const val TAG = "PasskeyRepository"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }
    
    // ==================== 查询操作 ====================
    
    /**
     * 获取所有 Passkey（响应式）
     */
    fun getAllPasskeys(): Flow<List<PasskeyEntry>> = passkeyDao.getAllPasskeys()
    
    /**
     * 获取所有 Passkey（同步）
     */
    suspend fun getAllPasskeysSync(): List<PasskeyEntry> = passkeyDao.getAllPasskeysSync()
    
    /**
     * 根据凭据 ID 获取 Passkey
     */
    suspend fun getPasskeyById(credentialId: String): PasskeyEntry? = 
        passkeyDao.getPasskeyById(credentialId)
    
    /**
     * 根据域名获取 Passkeys
     */
    fun getPasskeysByRpId(rpId: String): Flow<List<PasskeyEntry>> = 
        passkeyDao.getPasskeysByRpId(rpId)
    
    /**
     * 根据域名获取 Passkeys（同步）
     */
    suspend fun getPasskeysByRpIdSync(rpId: String): List<PasskeyEntry> = 
        passkeyDao.getPasskeysByRpIdSync(rpId)
    
    /**
     * 搜索 Passkey
     */
    fun searchPasskeys(query: String): Flow<List<PasskeyEntry>> = 
        passkeyDao.searchPasskeys(query)
    
    /**
     * 获取可发现的 Passkeys（用于 Credential Provider）
     */
    suspend fun getDiscoverablePasskeys(): List<PasskeyEntry> = 
        passkeyDao.getDiscoverablePasskeys()
    
    /**
     * 获取指定域名的可发现 Passkeys
     */
    suspend fun getDiscoverablePasskeysByRpId(rpId: String): List<PasskeyEntry> = 
        passkeyDao.getDiscoverablePasskeysByRpId(rpId)
    
    /**
     * 获取 Passkey 总数
     */
    fun getPasskeyCount(): Flow<Int> = passkeyDao.getPasskeyCount()
    
    /**
     * 获取未备份的 Passkeys
     */
    suspend fun getUnbackedPasskeys(): List<PasskeyEntry> = 
        passkeyDao.getUnbackedPasskeys()

    /**
     * 获取绑定到指定密码的 Passkeys
     */
    fun getPasskeysByBoundPasswordId(passwordId: Long): Flow<List<PasskeyEntry>> =
        passkeyDao.getByBoundPasswordId(passwordId)
    
    // ==================== 写入操作 ====================
    
    /**
     * 保存 Passkey（插入或更新）
     */
    suspend fun savePasskey(passkey: PasskeyEntry) = passkeyDao.insert(passkey)
    
    /**
     * 批量保存 Passkeys
     */
    suspend fun saveAllPasskeys(passkeys: List<PasskeyEntry>) = passkeyDao.insertAll(passkeys)
    
    /**
     * 更新 Passkey
     */
    suspend fun updatePasskey(passkey: PasskeyEntry) = passkeyDao.update(passkey)

    /**
     * 更新绑定的密码 ID
     */
    suspend fun updateBoundPasswordId(credentialId: String, passwordId: Long?) =
        passkeyDao.updateBoundPasswordId(credentialId, passwordId)
    
    /**
     * 更新使用记录
     */
    suspend fun updateUsage(credentialId: String, signCount: Long) = 
        passkeyDao.updateUsage(credentialId, System.currentTimeMillis(), signCount)
    
    /**
     * 标记为已备份
     */
    suspend fun markAsBackedUp(credentialId: String) = passkeyDao.markAsBackedUp(credentialId)
    
    /**
     * 批量标记为已备份
     */
    suspend fun markAllAsBackedUp(credentialIds: List<String>) = 
        passkeyDao.markAllAsBackedUp(credentialIds)
    
    // ==================== 删除操作 ====================
    
    /**
     * 删除 Passkey 并清理 Keystore 私钥
     */
    suspend fun deletePasskey(passkey: PasskeyEntry) {
        // 先清理 Keystore 中的私钥
        deletePrivateKey(passkey.privateKeyAlias)
        logAudit("PASSKEY_DELETED", "${passkey.credentialId}|rpId=${passkey.rpId}")
        // 再从数据库删除
        passkeyDao.delete(passkey)
    }
    
    /**
     * 根据凭据 ID 删除 Passkey 并清理 Keystore
     */
    suspend fun deletePasskeyById(credentialId: String) {
        // 先获取 Passkey 以获取私钥别名
        val passkey = passkeyDao.getPasskeyById(credentialId)
        if (passkey != null) {
            deletePrivateKey(passkey.privateKeyAlias)
            logAudit("PASSKEY_DELETED", "${credentialId}|rpId=${passkey.rpId}")
        }
        passkeyDao.deleteById(credentialId)
    }
    
    /**
     * 删除指定域名的所有 Passkeys 并清理 Keystore
     */
    suspend fun deletePasskeysByRpId(rpId: String) {
        // 获取所有匹配的 Passkey
        val passkeys = passkeyDao.getPasskeysByRpIdSync(rpId)
        for (passkey in passkeys) {
            deletePrivateKey(passkey.privateKeyAlias)
            logAudit("PASSKEY_DELETED", "${passkey.credentialId}|rpId=$rpId")
        }
        passkeyDao.deleteByRpId(rpId)
    }
    
    /**
     * 清空所有 Passkeys 并清理 Keystore
     */
    suspend fun deleteAllPasskeys() {
        // 获取所有 Passkey
        val passkeys = passkeyDao.getAllPasskeysSync()
        for (passkey in passkeys) {
            deletePrivateKey(passkey.privateKeyAlias)
        }
        logAudit("PASSKEY_CLEAR_ALL", "count=${passkeys.size}")
        passkeyDao.deleteAll()
    }
    
    // ==================== Keystore 管理 ====================
    
    /**
     * 从 Android Keystore 删除私钥
     */
    private fun deletePrivateKey(keyAlias: String) {
        if (keyAlias.isBlank()) {
            Log.w(TAG, "Empty key alias, skipping Keystore cleanup")
            return
        }
        
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
                Log.d(TAG, "Deleted private key from Keystore: $keyAlias")
            } else {
                Log.w(TAG, "Key alias not found in Keystore: $keyAlias")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete private key: $keyAlias", e)
        }
    }
    
    // ==================== 审计日志 ====================
    
    /**
     * 记录审计日志
     * TODO: 可扩展为写入文件或远程日志服务
     */
    fun logAudit(action: String, details: String) {
        Log.i("PasskeyAudit", "[$action] $details")
    }
}
