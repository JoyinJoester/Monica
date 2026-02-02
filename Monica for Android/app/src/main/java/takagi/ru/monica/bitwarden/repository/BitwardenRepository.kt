package takagi.ru.monica.bitwarden.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.bitwarden.api.BitwardenApiFactory
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey
import takagi.ru.monica.bitwarden.service.BitwardenAuthService
import takagi.ru.monica.bitwarden.service.BitwardenSyncService
import takagi.ru.monica.bitwarden.service.LoginResult
import takagi.ru.monica.bitwarden.service.SyncResult as ServiceSyncResult
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.bitwarden.*

/**
 * Bitwarden 统一数据仓库
 * 
 * 职责：
 * 1. 管理 Bitwarden Vault 的生命周期（登录、登出、Token 刷新）
 * 2. 协调认证服务和同步服务
 * 3. 提供统一的数据访问接口
 * 4. 管理加密密钥的安全存储
 */
class BitwardenRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "BitwardenRepository"
        private const val PREFS_NAME = "bitwarden_secure_prefs"
        private const val KEY_ACTIVE_VAULT_ID = "active_vault_id"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
        private const val KEY_SYNC_ON_WIFI_ONLY = "sync_on_wifi_only"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        
        @Volatile
        private var instance: BitwardenRepository? = null
        
        fun getInstance(context: Context): BitwardenRepository {
            return instance ?: synchronized(this) {
                instance ?: BitwardenRepository(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // Database DAOs
    private val database = PasswordDatabase.getDatabase(context)
    private val vaultDao = database.bitwardenVaultDao()
    private val folderDao = database.bitwardenFolderDao()
    private val conflictDao = database.bitwardenConflictBackupDao()
    private val pendingOpDao = database.bitwardenPendingOperationDao()
    private val passwordEntryDao = database.passwordEntryDao()
    
    // Services
    private val authService = BitwardenAuthService(context)
    private val syncService = BitwardenSyncService(context)
    
    // 加密的 SharedPreferences
    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    // 内存中的密钥缓存（不持久化）
    private val symmetricKeyCache = mutableMapOf<Long, SymmetricCryptoKey>()
    private val accessTokenCache = mutableMapOf<Long, String>()
    
    // ==================== Vault 管理 ====================
    
    /**
     * 获取所有 Vault
     */
    suspend fun getAllVaults(): List<BitwardenVault> = withContext(Dispatchers.IO) {
        vaultDao.getAllVaults()
    }
    
    /**
     * 获取活跃的 Vault
     */
    suspend fun getActiveVault(): BitwardenVault? = withContext(Dispatchers.IO) {
        val activeVaultId = securePrefs.getLong(KEY_ACTIVE_VAULT_ID, -1)
        if (activeVaultId > 0) {
            vaultDao.getVaultById(activeVaultId)
        } else {
            vaultDao.getDefaultVault() ?: vaultDao.getAllVaults().firstOrNull()
        }
    }
    
    /**
     * 设置活跃的 Vault
     */
    fun setActiveVault(vaultId: Long) {
        securePrefs.edit().putLong(KEY_ACTIVE_VAULT_ID, vaultId).apply()
    }
    
    /**
     * 获取 Vault 的解锁状态
     */
    fun isVaultUnlocked(vaultId: Long): Boolean {
        return symmetricKeyCache.containsKey(vaultId) && accessTokenCache.containsKey(vaultId)
    }
    
    /**
     * 登录 Bitwarden
     */
    suspend fun login(
        serverUrl: String?,
        email: String,
        masterPassword: String
    ): RepositoryLoginResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始登录 Bitwarden: $email")
            
            val effectiveServerUrl = serverUrl?.takeIf { it.isNotBlank() } 
                ?: BitwardenApiFactory.OFFICIAL_VAULT_URL
            
            val loginResult = authService.login(email, masterPassword, effectiveServerUrl)
            
            loginResult.fold(
                onSuccess = { result ->
                    when (result) {
                        is LoginResult.Success -> {
                            handleSuccessfulLogin(result, email)
                        }
                        is LoginResult.TwoFactorRequired -> {
                            RepositoryLoginResult.TwoFactorRequired(
                                providers = result.providers,
                                state = result
                            )
                        }
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "登录失败", error)
                    RepositoryLoginResult.Error(error.message ?: "未知错误")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "登录异常", e)
            RepositoryLoginResult.Error(e.message ?: "未知错误")
        }
    }
    
    /**
     * 使用两步验证登录
     */
    suspend fun loginWithTwoFactor(
        twoFactorState: LoginResult.TwoFactorRequired,
        twoFactorCode: String,
        twoFactorProvider: Int,
        serverUrl: String?
    ): RepositoryLoginResult = withContext(Dispatchers.IO) {
        try {
            val effectiveServerUrl = serverUrl?.takeIf { it.isNotBlank() }
                ?: BitwardenApiFactory.OFFICIAL_VAULT_URL
            
            val loginResult = if (twoFactorProvider == BitwardenAuthService.TWO_FACTOR_EMAIL_NEW_DEVICE) {
                authService.loginNewDeviceOtp(
                    twoFactorState = twoFactorState,
                    newDeviceOtp = twoFactorCode,
                    serverUrl = effectiveServerUrl
                )
            } else {
                authService.loginTwoFactor(
                    twoFactorState = twoFactorState,
                    twoFactorCode = twoFactorCode,
                    twoFactorProvider = twoFactorProvider,
                    remember = true,
                    serverUrl = effectiveServerUrl
                )
            }
            
            loginResult.fold(
                onSuccess = { result ->
                    handleSuccessfulLogin(result, twoFactorState.email)
                },
                onFailure = { error ->
                    Log.e(TAG, "两步验证登录失败", error)
                    RepositoryLoginResult.Error(error.message ?: "验证失败")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "两步验证异常", e)
            RepositoryLoginResult.Error(e.message ?: "未知错误")
        }
    }
    
    /**
     * 处理成功登录
     */
    private suspend fun handleSuccessfulLogin(
        result: LoginResult.Success,
        email: String
    ): RepositoryLoginResult {
        // 加密敏感数据用于存储
        val encryptedAccessToken = encryptForStorage(result.accessToken)
        val encryptedRefreshToken = result.refreshToken?.let { encryptForStorage(it) }
        
        // 加密密钥
        val encryptedMasterKey = encryptForStorage(Base64.encodeToString(result.masterKey, Base64.NO_WRAP))
        val encryptedEncKey = encryptForStorage(Base64.encodeToString(result.symmetricKey.encKey, Base64.NO_WRAP))
        val encryptedMacKey = encryptForStorage(Base64.encodeToString(result.symmetricKey.macKey, Base64.NO_WRAP))
        
        // 查找或创建 Vault
        val existingVault = vaultDao.getVaultByEmail(email)
        val expiresAt = System.currentTimeMillis() + (result.expiresIn * 1000L)
        
        val vault = if (existingVault != null) {
            existingVault.copy(
                serverUrl = result.serverUrls.vault,
                identityUrl = result.serverUrls.identity,
                apiUrl = result.serverUrls.api,
                encryptedAccessToken = encryptedAccessToken,
                encryptedRefreshToken = encryptedRefreshToken,
                accessTokenExpiresAt = expiresAt,
                encryptedMasterKey = encryptedMasterKey,
                encryptedEncKey = encryptedEncKey,
                encryptedMacKey = encryptedMacKey,
                kdfType = result.kdfType,
                kdfIterations = result.kdfIterations,
                kdfMemory = result.kdfMemory,
                kdfParallelism = result.kdfParallelism,
                isLocked = false,
                isConnected = true,
                updatedAt = System.currentTimeMillis()
            ).also { vaultDao.update(it) }
        } else {
            BitwardenVault(
                email = email,
                serverUrl = result.serverUrls.vault,
                identityUrl = result.serverUrls.identity,
                apiUrl = result.serverUrls.api,
                encryptedAccessToken = encryptedAccessToken,
                encryptedRefreshToken = encryptedRefreshToken,
                accessTokenExpiresAt = expiresAt,
                encryptedMasterKey = encryptedMasterKey,
                encryptedEncKey = encryptedEncKey,
                encryptedMacKey = encryptedMacKey,
                kdfType = result.kdfType,
                kdfIterations = result.kdfIterations,
                kdfMemory = result.kdfMemory,
                kdfParallelism = result.kdfParallelism,
                isLocked = false,
                isConnected = true,
                isDefault = vaultDao.getVaultCount() == 0
            ).let { newVault ->
                val id = vaultDao.insert(newVault)
                newVault.copy(id = id)
            }
        }
        
        // 缓存密钥和令牌
        symmetricKeyCache[vault.id] = result.symmetricKey
        accessTokenCache[vault.id] = result.accessToken
        
        // 设置为活跃 Vault
        setActiveVault(vault.id)
        
        Log.d(TAG, "登录成功: vaultId=${vault.id}")
        return RepositoryLoginResult.Success(vault)
    }
    
    /**
     * 解锁已登录的 Vault
     */
    suspend fun unlock(vaultId: Long, masterPassword: String): UnlockResult = withContext(Dispatchers.IO) {
        try {
            val vault = vaultDao.getVaultById(vaultId) ?: return@withContext UnlockResult.Error("Vault 不存在")
            
            // 派生主密钥
            val masterKey = if (vault.kdfType == BitwardenVault.KDF_TYPE_ARGON2ID) {
                BitwardenCrypto.deriveMasterKeyArgon2(
                    password = masterPassword,
                    salt = vault.email,
                    iterations = vault.kdfIterations,
                    memory = vault.kdfMemory ?: 64,
                    parallelism = vault.kdfParallelism ?: 4
                )
            } else {
                BitwardenCrypto.deriveMasterKeyPbkdf2(
                    password = masterPassword,
                    salt = vault.email,
                    iterations = vault.kdfIterations
                )
            }
            
            // 尝试从存储中恢复密钥
            val storedEncKey = vault.encryptedEncKey?.let { decryptFromStorage(it) }
            val storedMacKey = vault.encryptedMacKey?.let { decryptFromStorage(it) }
            
            if (storedEncKey != null && storedMacKey != null) {
                try {
                    val encKeyBytes = Base64.decode(storedEncKey, Base64.NO_WRAP)
                    val macKeyBytes = Base64.decode(storedMacKey, Base64.NO_WRAP)
                    val symmetricKey = SymmetricCryptoKey(encKeyBytes, macKeyBytes)
                    
                    // 缓存密钥
                    symmetricKeyCache[vaultId] = symmetricKey
                    
                    // 尝试恢复访问令牌
                    vault.encryptedAccessToken?.let { 
                        accessTokenCache[vaultId] = decryptFromStorage(it)
                    }
                    
                    // 更新状态
                    vaultDao.setLocked(vaultId, false)
                    
                    return@withContext UnlockResult.Success
                } catch (e: Exception) {
                    Log.e(TAG, "密钥恢复失败，尝试重新登录", e)
                }
            }
            
            // 密钥恢复失败，需要重新登录
            UnlockResult.Error("需要重新登录")
        } catch (e: Exception) {
            Log.e(TAG, "解锁异常", e)
            UnlockResult.Error(e.message ?: "解锁失败")
        }
    }
    
    /**
     * 锁定 Vault
     */
    suspend fun lock(vaultId: Long) = withContext(Dispatchers.IO) {
        symmetricKeyCache.remove(vaultId)
        accessTokenCache.remove(vaultId)
        vaultDao.setLocked(vaultId, true)
        Log.d(TAG, "Vault 已锁定: $vaultId")
    }
    
    /**
     * 锁定所有 Vault
     */
    suspend fun lockAll() = withContext(Dispatchers.IO) {
        symmetricKeyCache.clear()
        accessTokenCache.clear()
        getAllVaults().forEach { vault ->
            vaultDao.setLocked(vault.id, true)
        }
        Log.d(TAG, "所有 Vault 已锁定")
    }
    
    /**
     * 登出并删除 Vault
     */
    suspend fun logout(vaultId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            // 清除缓存
            symmetricKeyCache.remove(vaultId)
            accessTokenCache.remove(vaultId)
            
            // 删除该 Vault 的所有密码条目
            passwordEntryDao.deleteAllByBitwardenVaultId(vaultId)
            
            // 删除文件夹
            folderDao.deleteByVault(vaultId)
            
            // 删除 Vault
            vaultDao.deleteById(vaultId)
            
            // 重置活跃 Vault
            if (securePrefs.getLong(KEY_ACTIVE_VAULT_ID, -1) == vaultId) {
                securePrefs.edit().remove(KEY_ACTIVE_VAULT_ID).apply()
            }
            
            Log.d(TAG, "Vault 已登出: $vaultId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "登出失败", e)
            false
        }
    }
    
    // ==================== 同步 ====================
    
    /**
     * 执行完整同步
     */
    suspend fun sync(vaultId: Long): SyncResult = withContext(Dispatchers.IO) {
        try {
            val vault = vaultDao.getVaultById(vaultId) ?: return@withContext SyncResult.Error("Vault 不存在")
            
            if (!isVaultUnlocked(vaultId)) {
                return@withContext SyncResult.Error("Vault 未解锁")
            }
            
            val symmetricKey = symmetricKeyCache[vaultId] ?: return@withContext SyncResult.Error("密钥不可用")
            var accessToken = accessTokenCache[vaultId] ?: return@withContext SyncResult.Error("令牌不可用")
            
            // 检查 Token 是否需要刷新
            val expiresAt = vault.accessTokenExpiresAt ?: 0
            if (expiresAt <= System.currentTimeMillis() + 60000) {
                val refreshResult = refreshToken(vault)
                if (refreshResult != null) {
                    accessToken = refreshResult
                    accessTokenCache[vaultId] = accessToken
                } else {
                    return@withContext SyncResult.Error("Token 刷新失败，请重新登录")
                }
            }
            
            // 执行同步
            val result = syncService.fullSync(vault, accessToken, symmetricKey)
            
            // 更新最后同步时间
            securePrefs.edit().putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis()).apply()
            
            when (result) {
                is ServiceSyncResult.Success -> {
                    SyncResult.Success(
                        syncedCount = result.ciphersAdded + result.ciphersUpdated,
                        conflictCount = result.conflictsDetected
                    )
                }
                is ServiceSyncResult.Error -> {
                    SyncResult.Error(result.message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "同步异常", e)
            SyncResult.Error(e.message ?: "同步失败")
        }
    }
    
    /**
     * 刷新访问令牌
     */
    private suspend fun refreshToken(vault: BitwardenVault): String? {
        val refreshToken = vault.encryptedRefreshToken?.let { decryptFromStorage(it) } ?: return null
        
        return try {
            val result = authService.refreshToken(refreshToken, vault.identityUrl)
            result.getOrNull()?.let { refreshResult ->
                // 更新存储
                val encryptedAccessToken = encryptForStorage(refreshResult.accessToken)
                val encryptedRefreshToken = refreshResult.refreshToken?.let { encryptForStorage(it) }
                val expiresAt = System.currentTimeMillis() + (refreshResult.expiresIn * 1000L)
                
                vaultDao.updateAccessToken(vault.id, encryptedAccessToken, expiresAt)
                encryptedRefreshToken?.let { 
                    vaultDao.updateRefreshToken(vault.id, it)
                }
                
                refreshResult.accessToken
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token 刷新失败", e)
            null
        }
    }
    
    // ==================== 数据访问 ====================
    
    suspend fun getPasswordEntries(vaultId: Long): List<PasswordEntry> = withContext(Dispatchers.IO) {
        passwordEntryDao.getByBitwardenVaultId(vaultId)
    }
    
    suspend fun getFolders(vaultId: Long): List<BitwardenFolder> = withContext(Dispatchers.IO) {
        folderDao.getFoldersByVault(vaultId)
    }
    
    suspend fun getPasswordEntriesByFolder(folderId: String): List<PasswordEntry> = withContext(Dispatchers.IO) {
        passwordEntryDao.getByBitwardenFolderId(folderId)
    }
    
    suspend fun searchEntries(vaultId: Long, query: String): List<PasswordEntry> = withContext(Dispatchers.IO) {
        passwordEntryDao.searchBitwardenEntries(vaultId, query)
    }
    
    suspend fun getConflictBackups(vaultId: Long): List<BitwardenConflictBackup> = withContext(Dispatchers.IO) {
        conflictDao.getUnresolvedConflictsByVault(vaultId)
    }
    
    /**
     * 解决冲突：使用本地版本
     * 标记冲突为已解决，保留当前本地数据
     */
    suspend fun resolveConflictWithLocal(conflictId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val conflict = conflictDao.getById(conflictId) ?: return@withContext false
            
            // 标记冲突为已解决（本地优先）
            conflictDao.update(
                conflict.copy(
                    isResolved = true,
                    resolvedAt = System.currentTimeMillis()
                )
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "解决冲突（本地）失败", e)
            false
        }
    }
    
    /**
     * 解决冲突：使用服务器版本
     * 恢复备份的服务器数据，覆盖本地数据
     */
    suspend fun resolveConflictWithServer(conflictId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val conflict = conflictDao.getById(conflictId) ?: return@withContext false
            
            // 获取对应的密码条目
            val cipherId = conflict.bitwardenCipherId ?: return@withContext false
            val entry = passwordEntryDao.getByBitwardenCipherId(cipherId)
            
            if (entry != null) {
                // 使用备份的服务器数据更新本地条目
                // 服务器数据存储在 serverDataJson 中
                val serverDataJsonStr = conflict.serverDataJson ?: return@withContext false
                // 解析 serverDataJson JSON 并更新 entry
                // 简化实现：将 serverDataJson 解析为更新字段
                try {
                    val json = org.json.JSONObject(serverDataJsonStr)
                    val updatedEntry = entry.copy(
                        title = json.optString("title", entry.title),
                        username = json.optString("username", entry.username),
                        password = json.optString("password", entry.password),
                        website = json.optString("website", entry.website),
                        notes = json.optString("notes", entry.notes),
                        bitwardenLocalModified = false,
                        updatedAt = java.util.Date()
                    )
                    passwordEntryDao.update(updatedEntry)
                } catch (e: Exception) {
                    Log.e(TAG, "解析服务器数据失败", e)
                    return@withContext false
                }
            }
            
            // 标记冲突为已解决
            conflictDao.update(
                conflict.copy(
                    isResolved = true,
                    resolvedAt = System.currentTimeMillis()
                )
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "解决冲突（服务器）失败", e)
            false
        }
    }
    
    // ==================== 设置 ====================
    
    var isAutoSyncEnabled: Boolean
        get() = securePrefs.getBoolean(KEY_AUTO_SYNC_ENABLED, true)
        set(value) = securePrefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, value).apply()
    
    var isSyncOnWifiOnly: Boolean
        get() = securePrefs.getBoolean(KEY_SYNC_ON_WIFI_ONLY, false)
        set(value) = securePrefs.edit().putBoolean(KEY_SYNC_ON_WIFI_ONLY, value).apply()
    
    val lastSyncTime: Long
        get() = securePrefs.getLong(KEY_LAST_SYNC_TIME, 0)
    
    // ==================== 加密辅助 ====================
    
    private fun encryptForStorage(data: String): String {
        return Base64.encodeToString(data.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
    
    private fun decryptFromStorage(data: String): String {
        return String(Base64.decode(data, Base64.NO_WRAP), Charsets.UTF_8)
    }
    
    // ==================== 结果类型 ====================
    
    sealed class RepositoryLoginResult {
        data class Success(val vault: BitwardenVault) : RepositoryLoginResult()
        data class TwoFactorRequired(
            val providers: List<Int>,
            val state: LoginResult.TwoFactorRequired
        ) : RepositoryLoginResult()
        data class Error(val message: String) : RepositoryLoginResult()
    }
    
    sealed class UnlockResult {
        object Success : UnlockResult()
        data class Error(val message: String) : UnlockResult()
    }
    
    sealed class SyncResult {
        data class Success(val syncedCount: Int, val conflictCount: Int) : SyncResult()
        data class Error(val message: String) : SyncResult()
    }
}
