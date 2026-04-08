package takagi.ru.monica.bitwarden.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.room.withTransaction
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import takagi.ru.monica.bitwarden.api.BitwardenApiFactory
import takagi.ru.monica.bitwarden.api.BitwardenApiManager
import takagi.ru.monica.bitwarden.api.BitwardenTlsConfig
import takagi.ru.monica.bitwarden.api.FolderCreateRequest
import takagi.ru.monica.bitwarden.api.FolderUpdateRequest
import takagi.ru.monica.bitwarden.BitwardenRestoreQueueOutcome
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey
import takagi.ru.monica.bitwarden.mapper.BitwardenSendMapper
import takagi.ru.monica.bitwarden.service.BitwardenAuthService
import takagi.ru.monica.bitwarden.service.BitwardenDiagLogger
import takagi.ru.monica.bitwarden.service.BitwardenHistoricalTotpRepairResult
import takagi.ru.monica.bitwarden.service.BitwardenHistoricalTotpRepairService
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
        private const val KEY_NEVER_LOCK_BITWARDEN = "never_lock_bitwarden"
        private const val PBKDF2_DEFAULT_ITERATIONS = 600000
        private const val ARGON2_DEFAULT_ITERATIONS = 3
        private const val ARGON2_DEFAULT_MEMORY_MB = 64
        private const val ARGON2_DEFAULT_PARALLELISM = 4
        private const val CACHE_KEEP_SENTINEL_CIPHER_ID = "__MONICA_CACHE_KEEP_SENTINEL__"
        private val syncMutex = Mutex()
        
        @Volatile
        private var instance: BitwardenRepository? = null
        
        fun getInstance(context: Context): BitwardenRepository {
            return instance ?: synchronized(this) {
                instance ?: BitwardenRepository(context.applicationContext).also { instance = it }
            }
        }
        
        /**
         * 将技术性错误消息转换为用户友好的中文提示
         */
        fun parseErrorMessage(rawError: String?): String {
            if (rawError.isNullOrBlank()) return "未知错误"
            
            return when {
                // 账号密码错误（也可能是新设备验证触发，Bitwarden 新版不区分这两种情况）
                rawError.contains("invalid_username_or_password", ignoreCase = true) ||
                rawError.contains("Username or password is incorrect", ignoreCase = true) ->
                    "登录失败：账号或密码错误。\n\n如果您确认密码正确，Bitwarden 可能要求验证新设备——请检查邮箱是否收到验证邮件，点击邮件中的链接完成授权后重试"
                
                // 验证码错误
                rawError.contains("Invalid New Device OTP", ignoreCase = true) ||
                rawError.contains("invalid new device otp", ignoreCase = true) ->
                    "验证码错误或已过期，请重新获取"
                
                rawError.contains("Two-step token is invalid", ignoreCase = true) ||
                rawError.contains("invalid two-step", ignoreCase = true) ->
                    "两步验证码错误，请检查后重试"
                
                // 需要新设备验证
                rawError.contains("New device verification required", ignoreCase = true) ||
                rawError.contains("new device verification", ignoreCase = true) ->
                    "需要验证新设备，请检查邮箱获取验证码"
                
                // Captcha 验证
                rawError.contains("captcha required", ignoreCase = true) &&
                rawError.contains("sitekey", ignoreCase = true).not() ->
                    "登录触发风控验证，但服务器未返回可用验证码配置，请稍后重试或先用官方客户端完成验证"

                rawError.contains("captcha", ignoreCase = true) ->
                    "需要 Captcha 验证，请稍后重试或使用官方客户端登录"
                
                // 账户锁定
                rawError.contains("locked", ignoreCase = true) ||
                rawError.contains("too many attempts", ignoreCase = true) ->
                    "登录尝试次数过多，账户已暂时锁定，请稍后重试"
                
                // 网络错误
                rawError.contains("timeout", ignoreCase = true) ||
                rawError.contains("connect", ignoreCase = true) ||
                rawError.contains("network", ignoreCase = true) ->
                    "网络连接失败，请检查网络后重试"
                
                // 服务器错误
                rawError.contains("500") || rawError.contains("502") || 
                rawError.contains("503") || rawError.contains("504") ->
                    "服务器暂时不可用，请稍后重试"
                
                // 其他 400 错误
                rawError.contains("400") && rawError.contains("invalid_grant") ->
                    "认证失败：可能是服务器区域或自建地址不匹配、SSO 账户限制、或验证流程未完成，请重试"
                
                // 默认返回原始错误（截断过长内容）
                else -> {
                    val shortError = if (rawError.length > 100) {
                        rawError.take(100) + "..."
                    } else {
                        rawError
                    }
                    "登录失败: $shortError"
                }
            }
        }
    }
    
    // Database DAOs
    private val database = PasswordDatabase.getDatabase(context)
    private val vaultDao = database.bitwardenVaultDao()
    private val folderDao = database.bitwardenFolderDao()
    private val sendDao = database.bitwardenSendDao()
    private val conflictDao = database.bitwardenConflictBackupDao()
    private val pendingOpDao = database.bitwardenPendingOperationDao()
    private val passwordEntryDao = database.passwordEntryDao()
    private val secureItemDao = database.secureItemDao()
    private val passkeyDao = database.passkeyDao()
    private val categoryDao = database.categoryDao()
    
    // Services
    private val apiManager = BitwardenApiManager()
    private val authService = BitwardenAuthService(context)
    private val syncService = BitwardenSyncService(context)
    private val historicalTotpRepairService = BitwardenHistoricalTotpRepairService(context)
    
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

    fun getAllVaultsFlow(): Flow<List<BitwardenVault>> = vaultDao.getAllVaultsFlow()
    
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

    fun getCachedSymmetricKey(vaultId: Long): SymmetricCryptoKey? {
        val key = symmetricKeyCache[vaultId] ?: return null
        return SymmetricCryptoKey(
            encKey = key.encKey.copyOf(),
            macKey = key.macKey.copyOf()
        )
    }
    
    /**
     * 登录 Bitwarden
     */
    suspend fun login(
        serverUrl: String?,
        email: String,
        masterPassword: String,
        captchaResponse: String? = null,
        tlsConfig: BitwardenTlsConfig? = null
    ): RepositoryLoginResult = withContext(Dispatchers.IO) {
        try {
            val normalizedEmail = email.trim()
            Log.d(TAG, "开始登录 Bitwarden: $normalizedEmail")
            
            val effectiveServerUrl = serverUrl?.takeIf { it.isNotBlank() } 
                ?: BitwardenApiFactory.OFFICIAL_VAULT_URL
            
            val loginResult = authService.login(
                email = normalizedEmail,
                password = masterPassword,
                serverUrl = effectiveServerUrl,
                captchaResponse = captchaResponse,
                tlsConfig = tlsConfig
            )
            
            loginResult.fold(
                onSuccess = { result ->
                    when (result) {
                        is LoginResult.Success -> {
                            handleSuccessfulLogin(result, normalizedEmail, tlsConfig)
                        }
                        is LoginResult.TwoFactorRequired -> {
                            RepositoryLoginResult.TwoFactorRequired(
                                providers = result.providers,
                                state = result
                            )
                        }
                        is LoginResult.CaptchaRequired -> {
                            RepositoryLoginResult.CaptchaRequired(
                                message = result.message,
                                siteKey = result.siteKey
                            )
                        }
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "登录失败", error)
                    RepositoryLoginResult.Error(parseErrorMessage(error.message))
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "登录异常", e)
            RepositoryLoginResult.Error(parseErrorMessage(e.message))
        }
    }
    
    /**
     * 使用两步验证登录
     */
    suspend fun loginWithTwoFactor(
        twoFactorState: LoginResult.TwoFactorRequired,
        twoFactorCode: String,
        twoFactorProvider: Int,
        serverUrl: String?,
        captchaResponse: String? = null,
        tlsConfig: BitwardenTlsConfig? = null
    ): RepositoryLoginResult = withContext(Dispatchers.IO) {
        try {
            val effectiveServerUrl = serverUrl?.takeIf { it.isNotBlank() }
                ?: BitwardenApiFactory.OFFICIAL_VAULT_URL
            
            val loginResult = if (twoFactorProvider == BitwardenAuthService.TWO_FACTOR_EMAIL_NEW_DEVICE) {
                authService.loginNewDeviceOtp(
                    twoFactorState = twoFactorState,
                    newDeviceOtp = twoFactorCode,
                    serverUrl = effectiveServerUrl,
                    captchaResponse = captchaResponse,
                    tlsConfig = tlsConfig
                )
            } else {
                authService.loginTwoFactor(
                    twoFactorState = twoFactorState,
                    twoFactorCode = twoFactorCode,
                    twoFactorProvider = twoFactorProvider,
                    remember = true,
                    serverUrl = effectiveServerUrl,
                    captchaResponse = captchaResponse,
                    tlsConfig = tlsConfig
                )
            }
            
            loginResult.fold(
                onSuccess = { result ->
                    when (result) {
                        is LoginResult.Success -> handleSuccessfulLogin(result, twoFactorState.email, tlsConfig)
                        is LoginResult.CaptchaRequired -> RepositoryLoginResult.CaptchaRequired(
                            message = result.message,
                            siteKey = result.siteKey
                        )
                        is LoginResult.TwoFactorRequired -> RepositoryLoginResult.TwoFactorRequired(
                            providers = result.providers,
                            state = result
                        )
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "两步验证登录失败", error)
                    RepositoryLoginResult.Error(parseErrorMessage(error.message))
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "两步验证异常", e)
            RepositoryLoginResult.Error(parseErrorMessage(e.message))
        }
    }
    
    /**
     * 处理成功登录
     */
    private suspend fun handleSuccessfulLogin(
        result: LoginResult.Success,
        email: String,
        tlsConfig: BitwardenTlsConfig?
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
                tlsCertificateAlias = tlsConfig?.certificateAlias ?: existingVault.tlsCertificateAlias,
                tlsCaCertificatePem = tlsConfig?.caCertificatePem ?: existingVault.tlsCaCertificatePem,
                tlsMtlsEnabled = tlsConfig?.mtlsEnabled ?: existingVault.tlsMtlsEnabled,
                tlsClientCertPkcs12Base64 = tlsConfig?.clientCertPkcs12Base64
                    ?: existingVault.tlsClientCertPkcs12Base64,
                tlsEncryptedClientCertPassword = tlsConfig?.clientCertPassword
                    ?: existingVault.tlsEncryptedClientCertPassword,
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
                tlsCertificateAlias = tlsConfig?.certificateAlias,
                tlsCaCertificatePem = tlsConfig?.caCertificatePem,
                tlsMtlsEnabled = tlsConfig?.mtlsEnabled == true,
                tlsClientCertPkcs12Base64 = tlsConfig?.clientCertPkcs12Base64,
                tlsEncryptedClientCertPassword = tlsConfig?.clientCertPassword,
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
            val normalizedIterations = when (vault.kdfType) {
                BitwardenVault.KDF_TYPE_PBKDF2 -> vault.kdfIterations.takeIf { it > 0 } ?: PBKDF2_DEFAULT_ITERATIONS
                BitwardenVault.KDF_TYPE_ARGON2ID -> vault.kdfIterations.takeIf { it > 0 } ?: ARGON2_DEFAULT_ITERATIONS
                else -> vault.kdfIterations
            }
            val normalizedMemory = vault.kdfMemory.takeIf { it != null && it > 0 } ?: ARGON2_DEFAULT_MEMORY_MB
            val normalizedParallelism = vault.kdfParallelism.takeIf { it != null && it > 0 } ?: ARGON2_DEFAULT_PARALLELISM
            
            // 派生主密钥
            val masterKey = when (vault.kdfType) {
                BitwardenVault.KDF_TYPE_ARGON2ID -> {
                    BitwardenCrypto.deriveMasterKeyArgon2(
                        password = masterPassword,
                        salt = vault.email,
                        iterations = normalizedIterations,
                        memory = normalizedMemory,
                        parallelism = normalizedParallelism
                    )
                }

                BitwardenVault.KDF_TYPE_PBKDF2 -> {
                    BitwardenCrypto.deriveMasterKeyPbkdf2(
                        password = masterPassword,
                        salt = vault.email,
                        iterations = normalizedIterations
                    )
                }

                else -> {
                    return@withContext UnlockResult.Error("不支持的 KDF 类型: ${vault.kdfType}，请重新登录")
                }
            }

            try {
                val storedMasterKey = vault.encryptedMasterKey?.let { decryptFromStorage(it) }
                    ?: return@withContext UnlockResult.Error("需要重新登录")
                val derivedMasterKey = Base64.encodeToString(masterKey, Base64.NO_WRAP)
                if (storedMasterKey != derivedMasterKey) {
                    return@withContext UnlockResult.Error("主密码错误")
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
            } finally {
                masterKey.fill(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "解锁异常", e)
            UnlockResult.Error(e.message ?: "解锁失败")
        }
    }
    
    /**
     * 锁定 Vault
     * 如果开启了"永不锁定"选项，则不执行锁定
     */
    suspend fun lock(vaultId: Long) = withContext(Dispatchers.IO) {
        if (isNeverLockEnabled) {
            Log.d(TAG, "永不锁定已开启，跳过锁定 Vault: $vaultId")
            return@withContext
        }
        symmetricKeyCache.remove(vaultId)
        accessTokenCache.remove(vaultId)
        vaultDao.setLocked(vaultId, true)
        Log.d(TAG, "Vault 已锁定: $vaultId")
    }
    
    /**
     * 尝试从存储中恢复解锁状态（无需主密码）
     * 用于"永不锁定"模式下 App 重启后恢复
     * 
     * @return true 如果成功恢复解锁状态
     */
    suspend fun tryRestoreUnlockState(vaultId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val vault = vaultDao.getVaultById(vaultId) ?: return@withContext false
            if (vault.isLocked) {
                return@withContext false
            }
            
            // 尝试从存储中恢复密钥
            val storedEncKey = vault.encryptedEncKey?.let { decryptFromStorage(it) }
            val storedMacKey = vault.encryptedMacKey?.let { decryptFromStorage(it) }
            
            if (storedEncKey != null && storedMacKey != null) {
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
                
                Log.d(TAG, "成功恢复 Vault 解锁状态: $vaultId")
                return@withContext true
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "恢复解锁状态失败", e)
            false
        }
    }

    suspend fun forceLock(vaultId: Long) = withContext(Dispatchers.IO) {
        symmetricKeyCache.remove(vaultId)
        accessTokenCache.remove(vaultId)
        vaultDao.setLocked(vaultId, true)
        Log.d(TAG, "Vault 已强制锁定: $vaultId")
    }
    
    /**
     * 锁定所有 Vault
     * 如果开启了"永不锁定"选项，则不执行锁定
     */
    suspend fun lockAll() = withContext(Dispatchers.IO) {
        if (isNeverLockEnabled) {
            Log.d(TAG, "永不锁定已开启，跳过锁定所有 Vault")
            return@withContext
        }
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

            database.withTransaction {
                // 先清理所有引用 vault_id 的表，避免外键约束导致登出失败。
                pendingOpDao.deleteByVault(vaultId)
                conflictDao.deleteByVault(vaultId)
                sendDao.deleteByVault(vaultId)
                folderDao.deleteByVault(vaultId)

                // 清理本地业务数据
                passwordEntryDao.deleteAllByBitwardenVaultId(vaultId)
                secureItemDao.deleteAllByBitwardenVaultId(vaultId)
                passkeyDao.deleteAllByBitwardenVaultId(vaultId)
                categoryDao.unlinkByVaultId(vaultId)

                // 最后删除 Vault 本体
                vaultDao.deleteById(vaultId)
            }

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
     * 
     * 同步流程：
     * 1. 先处理本地待删除操作（delete）
     * 2. 上传本地创建的条目到服务器（create）
     * 3. 上传本地修改的条目到服务器（update）
     * 4. 从服务器拉取最新数据（pull）
     */
    suspend fun sync(vaultId: Long): SyncResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            try {
                val vault = vaultDao.getVaultById(vaultId) ?: return@withLock SyncResult.Error("Vault 不存在")

                if (!isVaultUnlocked(vaultId)) {
                    return@withLock SyncResult.Error("Vault 未解锁")
                }

                val symmetricKey = symmetricKeyCache[vaultId] ?: return@withLock SyncResult.Error("密钥不可用")
                var accessToken = accessTokenCache[vaultId] ?: return@withLock SyncResult.Error("令牌不可用")

                // 检查 Token 是否需要刷新
                val expiresAt = vault.accessTokenExpiresAt ?: 0
                if (expiresAt <= System.currentTimeMillis() + 60000) {
                    val refreshResult = refreshToken(vault)
                    if (refreshResult != null) {
                        accessToken = refreshResult
                        accessTokenCache[vaultId] = accessToken
                    } else {
                        return@withLock SyncResult.Error("Token 刷新失败，请重新登录")
                    }
                }

                // 1. 先处理本地待删除操作（delete）
                val processedDeleteCount = syncService.processPendingOperations(vault, accessToken, symmetricKey)

                // 2. 再上传本地创建的条目到服务器（create）
                val uploadResult = syncService.uploadLocalEntries(vault, accessToken, symmetricKey)
                val uploadedCount = when (uploadResult) {
                    is takagi.ru.monica.bitwarden.service.UploadResult.Success -> uploadResult.uploaded
                    else -> 0
                }
                val uploadFailedCount = when (uploadResult) {
                    is takagi.ru.monica.bitwarden.service.UploadResult.Success -> uploadResult.failed
                    else -> 0
                }

                // 3. 上传本地已修改的条目到服务器（update）
                val modifiedUploadResult = syncService.uploadModifiedEntries(vault, accessToken, symmetricKey)
                val modifiedUploadedCount = when (modifiedUploadResult) {
                    is takagi.ru.monica.bitwarden.service.UploadResult.Success -> modifiedUploadResult.uploaded
                    else -> 0
                }
                val modifiedUploadFailedCount = when (modifiedUploadResult) {
                    is takagi.ru.monica.bitwarden.service.UploadResult.Success -> modifiedUploadResult.failed
                    else -> 0
                }

                // 4. 执行同步（pull）
                val result = syncService.fullSync(vault, accessToken, symmetricKey)

                // 更新最后同步时间
                securePrefs.edit().putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis()).apply()

                when (result) {
                    is ServiceSyncResult.Success -> {
                        val totalUploadFailedCount = uploadFailedCount + modifiedUploadFailedCount
                        val overallResult = if (totalUploadFailedCount > 0) {
                            "PARTIAL_SUCCESS"
                        } else {
                            "SUCCESS"
                        }
                        BitwardenDiagLogger.append(
                            "BitwardenRepository overallSyncResult: vaultId=$vaultId, result=$overallResult, " +
                                "uploaded=$uploadedCount, modifiedUploaded=$modifiedUploadedCount, " +
                                "uploadFailed=$totalUploadFailedCount, deletes=$processedDeleteCount, " +
                                "ciphersAdded=${result.ciphersAdded}, ciphersUpdated=${result.ciphersUpdated}, " +
                                "conflicts=${result.conflictsDetected}, skippedLocalDirty=${result.skippedDueToLocalDirty}"
                        )
                        SyncResult.Success(
                            syncedCount = result.ciphersAdded + result.ciphersUpdated + uploadedCount + modifiedUploadedCount + processedDeleteCount,
                            conflictCount = result.conflictsDetected,
                            uploadFailedCount = totalUploadFailedCount,
                            skippedDueToLocalDirtyCount = result.skippedDueToLocalDirty
                        )
                    }
                    is ServiceSyncResult.Error -> {
                        SyncResult.Error(result.message)
                    }
                    is ServiceSyncResult.EmptyVaultBlocked -> {
                        SyncResult.EmptyVaultBlocked(
                            vaultId = vaultId,
                            localCount = result.localCount,
                            serverCount = result.serverCount,
                            reason = result.reason
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "同步异常", e)
                SyncResult.Error(e.message ?: "同步失败")
            }
        }
    }

    suspend fun getVaultCacheRiskSummary(vaultId: Long): VaultCacheRiskSummary = withContext(Dispatchers.IO) {
        if (vaultDao.getVaultById(vaultId) == null) {
            throw IllegalStateException("Vault 不存在")
        }
        collectVaultCacheRiskSummary(vaultId)
    }

    suspend fun clearVaultLocalCache(
        vaultId: Long,
        mode: CacheClearMode
    ): CacheClearResult = withContext(Dispatchers.IO) {
        if (vaultDao.getVaultById(vaultId) == null) {
            throw IllegalStateException("Vault 不存在")
        }

        val riskSummary = collectVaultCacheRiskSummary(vaultId)
        val protectedCipherIds = collectProtectedCipherIds(vaultId)
        val keepIds = if (protectedCipherIds.isEmpty()) {
            listOf(CACHE_KEEP_SENTINEL_CIPHER_ID)
        } else {
            protectedCipherIds.toList()
        }

        val passwordBefore = passwordEntryDao.getBitwardenEntryCount(vaultId)
        val secureBefore = secureItemDao.getBitwardenEntriesCount(vaultId)
        val passkeyBefore = passkeyDao.getByBitwardenVaultId(vaultId).size
        val folderBefore = folderDao.getFoldersByVault(vaultId).size
        val sendBefore = sendDao.getSendsByVault(vaultId).size
        val conflictBefore = conflictDao.getUnresolvedConflictsByVault(vaultId).size
        val pendingBefore = pendingOpDao.getRunnableOperationsByVault(vaultId).size

        database.withTransaction {
            when (mode) {
                CacheClearMode.SAFE_ONLY_SYNCED -> {
                    passwordEntryDao.deleteBitwardenEntriesNotIn(vaultId, keepIds)
                    secureItemDao.deleteBitwardenEntriesNotIn(vaultId, keepIds)
                    passkeyDao.deleteBitwardenEntriesNotIn(vaultId, keepIds)
                    folderDao.deleteByVault(vaultId)
                    sendDao.deleteByVault(vaultId)
                }

                CacheClearMode.FULL_FORCE -> {
                    passwordEntryDao.deleteAllByBitwardenVaultId(vaultId)
                    secureItemDao.deleteAllByBitwardenVaultId(vaultId)
                    passkeyDao.deleteAllByBitwardenVaultId(vaultId)
                    folderDao.deleteByVault(vaultId)
                    sendDao.deleteByVault(vaultId)
                    conflictDao.deleteByVault(vaultId)
                    pendingOpDao.deleteByVault(vaultId)
                }
            }
        }

        val passwordAfter = passwordEntryDao.getBitwardenEntryCount(vaultId)
        val secureAfter = secureItemDao.getBitwardenEntriesCount(vaultId)
        val passkeyAfter = passkeyDao.getByBitwardenVaultId(vaultId).size
        val folderAfter = folderDao.getFoldersByVault(vaultId).size
        val sendAfter = sendDao.getSendsByVault(vaultId).size
        val conflictAfter = conflictDao.getUnresolvedConflictsByVault(vaultId).size
        val pendingAfter = pendingOpDao.getRunnableOperationsByVault(vaultId).size

        CacheClearResult(
            mode = mode,
            riskSummary = riskSummary,
            protectedCipherCount = protectedCipherIds.size,
            passwordClearedCount = (passwordBefore - passwordAfter).coerceAtLeast(0),
            secureItemClearedCount = (secureBefore - secureAfter).coerceAtLeast(0),
            passkeyClearedCount = (passkeyBefore - passkeyAfter).coerceAtLeast(0),
            folderClearedCount = (folderBefore - folderAfter).coerceAtLeast(0),
            sendClearedCount = (sendBefore - sendAfter).coerceAtLeast(0),
            unresolvedConflictClearedCount = (conflictBefore - conflictAfter).coerceAtLeast(0),
            pendingOperationClearedCount = (pendingBefore - pendingAfter).coerceAtLeast(0)
        )
    }

    private suspend fun collectVaultCacheRiskSummary(vaultId: Long): VaultCacheRiskSummary {
        val pendingOperations = pendingOpDao.getRunnableOperationsByVault(vaultId)
        val passwordLocalModified = passwordEntryDao.getEntriesWithPendingBitwardenSync(vaultId)
        val secureLocalModified = secureItemDao.getLocalModifiedEntries(vaultId)
        val unresolvedConflicts = conflictDao.getUnresolvedConflictsByVault(vaultId)

        return VaultCacheRiskSummary(
            vaultId = vaultId,
            pendingOperationCount = pendingOperations.size,
            passwordLocalModifiedCount = passwordLocalModified.size,
            secureItemLocalModifiedCount = secureLocalModified.size,
            unresolvedConflictCount = unresolvedConflicts.size
        )
    }

    private suspend fun collectProtectedCipherIds(vaultId: Long): Set<String> {
        val protected = linkedSetOf<String>()

        passwordEntryDao.getEntriesWithPendingBitwardenSync(vaultId).forEach { entry ->
            entry.bitwardenCipherId
                ?.takeIf { it.isNotBlank() }
                ?.let(protected::add)
        }

        secureItemDao.getLocalModifiedEntries(vaultId).forEach { item ->
            item.bitwardenCipherId
                ?.takeIf { it.isNotBlank() }
                ?.let(protected::add)
        }

        pendingOpDao.getRunnableOperationsByVault(vaultId).forEach { op ->
            op.bitwardenCipherId
                ?.takeIf { it.isNotBlank() }
                ?.let(protected::add)
        }

        conflictDao.getUnresolvedConflictsByVault(vaultId).forEach { conflict ->
            conflict.bitwardenCipherId
                ?.takeIf { it.isNotBlank() }
                ?.let(protected::add)
        }

        return protected
    }

    suspend fun repairHistoricalBitwardenTotp(vaultId: Long): Result<BitwardenHistoricalTotpRepairResult> =
        withContext(Dispatchers.IO) {
            try {
                val vault = vaultDao.getVaultById(vaultId)
                    ?: return@withContext Result.failure(IllegalStateException("Vault 不存在"))

                if (!isVaultUnlocked(vaultId)) {
                    return@withContext Result.failure(IllegalStateException("Vault 未解锁"))
                }

                val symmetricKey = symmetricKeyCache[vaultId]
                    ?: return@withContext Result.failure(IllegalStateException("密钥不可用"))
                var accessToken = accessTokenCache[vaultId]
                    ?: return@withContext Result.failure(IllegalStateException("令牌不可用"))

                val expiresAt = vault.accessTokenExpiresAt ?: 0
                if (expiresAt <= System.currentTimeMillis() + 60000) {
                    val refreshed = refreshToken(vault)
                    if (refreshed != null) {
                        accessToken = refreshed
                        accessTokenCache[vaultId] = refreshed
                    } else {
                        return@withContext Result.failure(IllegalStateException("Token 刷新失败，请重新登录"))
                    }
                }

                Result.success(
                    historicalTotpRepairService.repairHistoricalTotp(
                        vault = vault,
                        accessToken = accessToken,
                        symmetricKey = symmetricKey
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "修复历史 Bitwarden TOTP 失败", e)
                Result.failure(e)
            }
        }
    
    /**
     * 刷新访问令牌
     */
    private suspend fun refreshToken(vault: BitwardenVault): String? {
        val refreshToken = vault.encryptedRefreshToken?.let { decryptFromStorage(it) } ?: return null
        
        return try {
            val result = authService.refreshToken(
                refreshToken = refreshToken,
                identityUrl = vault.identityUrl,
                refererUrl = vault.serverUrl,
                tlsConfig = BitwardenTlsConfig(
                    certificateAlias = vault.tlsCertificateAlias,
                    caCertificatePem = vault.tlsCaCertificatePem,
                    mtlsEnabled = vault.tlsMtlsEnabled,
                    clientCertPkcs12Base64 = vault.tlsClientCertPkcs12Base64,
                    clientCertPassword = vault.tlsEncryptedClientCertPassword
                )
            )
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

    suspend fun createFolder(vaultId: Long, name: String): Result<BitwardenFolder> = withContext(Dispatchers.IO) {
        try {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@withContext Result.failure(IllegalArgumentException("文件夹名称不能为空"))

            val vault = vaultDao.getVaultById(vaultId)
                ?: return@withContext Result.failure(IllegalStateException("Vault 不存在"))

            if (!isVaultUnlocked(vaultId)) {
                return@withContext Result.failure(IllegalStateException("Vault 未解锁"))
            }

            val symmetricKey = symmetricKeyCache[vaultId]
                ?: return@withContext Result.failure(IllegalStateException("密钥不可用"))
            var accessToken = accessTokenCache[vaultId]
                ?: return@withContext Result.failure(IllegalStateException("令牌不可用"))

            val expiresAt = vault.accessTokenExpiresAt ?: 0
            if (expiresAt <= System.currentTimeMillis() + 60000) {
                val refreshed = refreshToken(vault)
                if (refreshed != null) {
                    accessToken = refreshed
                    accessTokenCache[vaultId] = refreshed
                } else {
                    return@withContext Result.failure(IllegalStateException("Token 刷新失败，请重新登录"))
                }
            }

            val encryptedName = BitwardenCrypto.encryptString(trimmed, symmetricKey)
            val vaultApi = apiManager.getVaultApi(vault)
            val response = vaultApi.createFolder(
                "Bearer $accessToken",
                FolderCreateRequest(name = encryptedName)
            )

            if (!response.isSuccessful || response.body() == null) {
                return@withContext Result.failure(
                    IllegalStateException("创建失败: ${response.code()} ${response.message()}")
                )
            }

            val created = response.body()!!
            val folder = BitwardenFolder(
                vaultId = vaultId,
                bitwardenFolderId = created.id,
                name = trimmed,
                encryptedName = encryptedName,
                revisionDate = created.revisionDate ?: "",
                lastSyncedAt = System.currentTimeMillis()
            )
            folderDao.upsert(folder)
            Result.success(folder)
        } catch (e: Exception) {
            Log.e(TAG, "创建 Bitwarden 文件夹失败", e)
            Result.failure(e)
        }
    }

    suspend fun renameFolder(vaultId: Long, folderId: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val trimmed = newName.trim()
            if (trimmed.isBlank()) return@withContext Result.failure(IllegalArgumentException("文件夹名称不能为空"))

            val vault = vaultDao.getVaultById(vaultId)
                ?: return@withContext Result.failure(IllegalStateException("Vault 不存在"))

            if (!isVaultUnlocked(vaultId)) {
                return@withContext Result.failure(IllegalStateException("Vault 未解锁"))
            }

            val symmetricKey = symmetricKeyCache[vaultId]
                ?: return@withContext Result.failure(IllegalStateException("密钥不可用"))
            var accessToken = accessTokenCache[vaultId]
                ?: return@withContext Result.failure(IllegalStateException("令牌不可用"))

            val expiresAt = vault.accessTokenExpiresAt ?: 0
            if (expiresAt <= System.currentTimeMillis() + 60000) {
                val refreshed = refreshToken(vault)
                if (refreshed != null) {
                    accessToken = refreshed
                    accessTokenCache[vaultId] = refreshed
                } else {
                    return@withContext Result.failure(IllegalStateException("Token 刷新失败，请重新登录"))
                }
            }

            val encryptedName = BitwardenCrypto.encryptString(trimmed, symmetricKey)
            val vaultApi = apiManager.getVaultApi(vault)
            val response = vaultApi.updateFolder(
                "Bearer $accessToken",
                folderId,
                FolderUpdateRequest(name = encryptedName)
            )
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("重命名失败: ${response.code()} ${response.message()}")
                )
            }

            folderDao.updateName(folderId, trimmed, encryptedName)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "重命名 Bitwarden 文件夹失败", e)
            Result.failure(e)
        }
    }

    suspend fun deleteFolder(vaultId: Long, folderId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val vault = vaultDao.getVaultById(vaultId)
                ?: return@withContext Result.failure(IllegalStateException("Vault 不存在"))

            if (!isVaultUnlocked(vaultId)) {
                return@withContext Result.failure(IllegalStateException("Vault 未解锁"))
            }

            var accessToken = accessTokenCache[vaultId]
                ?: return@withContext Result.failure(IllegalStateException("令牌不可用"))

            val expiresAt = vault.accessTokenExpiresAt ?: 0
            if (expiresAt <= System.currentTimeMillis() + 60000) {
                val refreshed = refreshToken(vault)
                if (refreshed != null) {
                    accessToken = refreshed
                    accessTokenCache[vaultId] = refreshed
                } else {
                    return@withContext Result.failure(IllegalStateException("Token 刷新失败，请重新登录"))
                }
            }

            val vaultApi = apiManager.getVaultApi(vault)
            val response = vaultApi.deleteFolder("Bearer $accessToken", folderId)
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("删除失败: ${response.code()} ${response.message()}")
                )
            }

            folderDao.deleteByBitwardenId(folderId)
            categoryDao.unlinkByFolderId(folderId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "删除 Bitwarden 文件夹失败", e)
            Result.failure(e)
        }
    }

    suspend fun queueCipherDelete(
        vaultId: Long,
        cipherId: String,
        entryId: Long? = null,
        itemType: String = BitwardenPendingOperation.ITEM_TYPE_PASSWORD
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val vault = vaultDao.getVaultById(vaultId)
                ?: return@withContext Result.failure(IllegalStateException("Vault 不存在"))

            // 已有待删除操作时直接复用，保证幂等。
            val existingDelete = pendingOpDao.findActiveDeleteByCipher(vaultId, cipherId)
            if (existingDelete != null) {
                return@withContext Result.success(Unit)
            }

            pendingOpDao.cancelActiveRestoreByCipher(vaultId, cipherId)

            entryId?.let { id ->
                pendingOpDao.deletePendingForEntryAndType(id, itemType)
            }

            pendingOpDao.insert(
                BitwardenPendingOperation(
                    vaultId = vault.id,
                    entryId = entryId,
                    bitwardenCipherId = cipherId,
                    itemType = itemType,
                    operationType = BitwardenPendingOperation.OP_DELETE,
                    targetType = BitwardenPendingOperation.TARGET_CIPHER,
                    payloadJson = "{}",
                    status = BitwardenPendingOperation.STATUS_PENDING
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "加入 Bitwarden 删除队列失败", e)
            Result.failure(e)
        }
    }

    suspend fun queueCipherRestore(
        vaultId: Long,
        cipherId: String,
        entryId: Long? = null,
        itemType: String = BitwardenPendingOperation.ITEM_TYPE_PASSWORD
    ): Result<BitwardenRestoreQueueOutcome> = withContext(Dispatchers.IO) {
        try {
            val vault = vaultDao.getVaultById(vaultId)
                ?: return@withContext Result.failure(IllegalStateException("Vault 不存在"))

            val existingRestore = pendingOpDao.findActiveRestoreByCipher(vaultId, cipherId)
            if (existingRestore != null) {
                return@withContext Result.success(BitwardenRestoreQueueOutcome.REMOTE_RESTORE_ALREADY_QUEUED)
            }

            val existingDelete = pendingOpDao.findActiveDeleteByCipher(vaultId, cipherId)
            if (existingDelete != null) {
                pendingOpDao.cancelActiveDeleteByCipher(vaultId, cipherId)
                return@withContext Result.success(BitwardenRestoreQueueOutcome.CANCELED_PENDING_DELETE)
            }

            entryId?.let { id ->
                pendingOpDao.deletePendingForEntryAndType(id, itemType)
            }

            pendingOpDao.insert(
                BitwardenPendingOperation(
                    vaultId = vault.id,
                    entryId = entryId,
                    bitwardenCipherId = cipherId,
                    itemType = itemType,
                    operationType = BitwardenPendingOperation.OP_RESTORE,
                    targetType = BitwardenPendingOperation.TARGET_CIPHER,
                    payloadJson = "{}",
                    status = BitwardenPendingOperation.STATUS_PENDING
                )
            )

            Result.success(BitwardenRestoreQueueOutcome.ENQUEUED_REMOTE_RESTORE)
        } catch (e: Exception) {
            Log.e(TAG, "加入 Bitwarden 恢复队列失败", e)
            Result.failure(e)
        }
    }

    suspend fun cancelPendingCipherDelete(vaultId: Long, cipherId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            pendingOpDao.cancelActiveDeleteByCipher(vaultId, cipherId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "取消 Bitwarden 删除队列失败", e)
            Result.failure(e)
        }
    }

    suspend fun deleteCipher(vaultId: Long, cipherId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val vault = vaultDao.getVaultById(vaultId)
                ?: return@withContext Result.failure(IllegalStateException("Vault 不存在"))

            if (!isVaultUnlocked(vaultId)) {
                return@withContext Result.failure(IllegalStateException("Vault 未解锁"))
            }

            var accessToken = accessTokenCache[vaultId]
                ?: return@withContext Result.failure(IllegalStateException("令牌不可用"))

            val expiresAt = vault.accessTokenExpiresAt ?: 0
            if (expiresAt <= System.currentTimeMillis() + 60000) {
                val refreshed = refreshToken(vault)
                if (refreshed != null) {
                    accessToken = refreshed
                    accessTokenCache[vaultId] = refreshed
                } else {
                    return@withContext Result.failure(IllegalStateException("Token 刷新失败，请重新登录"))
                }
            }

            val vaultApi = apiManager.getVaultApi(vault)
            val response = vaultApi.deleteCipher("Bearer $accessToken", cipherId)
            if (!response.isSuccessful && response.code() != 404) {
                return@withContext Result.failure(
                    IllegalStateException("删除失败: ${response.code()} ${response.message()}")
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "删除 Bitwarden Cipher 失败", e)
            Result.failure(e)
        }
    }

    suspend fun permanentDeleteCipher(vaultId: Long, cipherId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val vault = vaultDao.getVaultById(vaultId)
                ?: return@withContext Result.failure(IllegalStateException("Vault 不存在"))

            if (!isVaultUnlocked(vaultId)) {
                return@withContext Result.failure(IllegalStateException("Vault 未解锁"))
            }

            var accessToken = accessTokenCache[vaultId]
                ?: return@withContext Result.failure(IllegalStateException("令牌不可用"))

            val expiresAt = vault.accessTokenExpiresAt ?: 0
            if (expiresAt <= System.currentTimeMillis() + 60000) {
                val refreshed = refreshToken(vault)
                if (refreshed != null) {
                    accessToken = refreshed
                    accessTokenCache[vaultId] = refreshed
                } else {
                    return@withContext Result.failure(IllegalStateException("Token 刷新失败，请重新登录"))
                }
            }

            val vaultApi = apiManager.getVaultApi(vault)
            val response = vaultApi.permanentDeleteCipher("Bearer $accessToken", cipherId)
            if (!response.isSuccessful && response.code() != 404) {
                return@withContext Result.failure(
                    IllegalStateException("永久删除失败: ${response.code()} ${response.message()}")
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "永久删除 Bitwarden Cipher 失败", e)
            Result.failure(e)
        }
    }

    suspend fun getSends(vaultId: Long): List<BitwardenSend> = withContext(Dispatchers.IO) {
        sendDao.getSendsByVault(vaultId)
    }

    suspend fun refreshSends(vaultId: Long): SendSyncResult = withContext(Dispatchers.IO) {
        when (val result = sync(vaultId)) {
            is SyncResult.Success -> {
                SendSyncResult.Success(sendDao.getSendsByVault(vaultId))
            }
            is SyncResult.EmptyVaultBlocked -> {
                SendSyncResult.Warning(
                    sends = sendDao.getSendsByVault(vaultId),
                    message = result.reason
                )
            }
            is SyncResult.Error -> {
                SendSyncResult.Error(result.message)
            }
        }
    }

    suspend fun createTextSend(
        vaultId: Long,
        title: String,
        text: String,
        notes: String?,
        password: String?,
        maxAccessCount: Int?,
        hideEmail: Boolean,
        hiddenText: Boolean,
        deletionMillis: Long,
        expirationMillis: Long?
    ): SendMutationResult = withContext(Dispatchers.IO) {
        try {
            if (title.isBlank()) return@withContext SendMutationResult.Error("标题不能为空")
            if (text.isBlank()) return@withContext SendMutationResult.Error("发送内容不能为空")

            val vault = vaultDao.getVaultById(vaultId)
                ?: return@withContext SendMutationResult.Error("Vault 不存在")

            if (!isVaultUnlocked(vaultId)) {
                return@withContext SendMutationResult.Error("Vault 未解锁")
            }

            val symmetricKey = symmetricKeyCache[vaultId]
                ?: return@withContext SendMutationResult.Error("密钥不可用")
            var accessToken = accessTokenCache[vaultId]
                ?: return@withContext SendMutationResult.Error("令牌不可用")

            val expiresAt = vault.accessTokenExpiresAt ?: 0
            if (expiresAt <= System.currentTimeMillis() + 60000) {
                val refreshed = refreshToken(vault)
                if (refreshed != null) {
                    accessToken = refreshed
                    accessTokenCache[vaultId] = refreshed
                } else {
                    return@withContext SendMutationResult.Error("Token 刷新失败，请重新登录")
                }
            }

            val payload = BitwardenSendMapper.buildCreateTextSendPayload(
                serverUrl = vault.serverUrl,
                vaultKey = symmetricKey,
                title = title,
                text = text,
                notes = notes,
                password = password,
                maxAccessCount = maxAccessCount,
                hideEmail = hideEmail,
                hiddenText = hiddenText,
                deletionMillis = deletionMillis,
                expirationMillis = expirationMillis
            )

            val vaultApi = apiManager.getVaultApi(vault)
            val response = vaultApi.createSend(
                authorization = "Bearer $accessToken",
                send = payload.request
            )

            if (!response.isSuccessful) {
                return@withContext SendMutationResult.Error(
                    "创建 Send 失败: ${response.code()} ${response.message()}"
                )
            }

            val body = response.body()
                ?: return@withContext SendMutationResult.Error("服务器未返回 Send 数据")
            val mapped = BitwardenSendMapper.mapApiToEntity(
                vaultId = vault.id,
                serverUrl = vault.serverUrl,
                api = body,
                vaultKey = symmetricKey
            ) ?: return@withContext SendMutationResult.Error("Send 解密失败")

            val existing = sendDao.getBySendId(vault.id, mapped.bitwardenSendId)
            val now = System.currentTimeMillis()
            val entity = if (existing == null) {
                mapped.copy(createdAt = now, updatedAt = now, lastSyncedAt = now)
            } else {
                mapped.copy(
                    id = existing.id,
                    createdAt = existing.createdAt,
                    updatedAt = now,
                    lastSyncedAt = now
                )
            }
            sendDao.upsert(entity)

            SendMutationResult.Success(entity)
        } catch (e: Exception) {
            Log.e(TAG, "创建 Send 失败", e)
            SendMutationResult.Error(e.message ?: "创建 Send 失败")
        }
    }

    suspend fun deleteSend(vaultId: Long, sendId: String): SendMutationResult = withContext(Dispatchers.IO) {
        try {
            val vault = vaultDao.getVaultById(vaultId)
                ?: return@withContext SendMutationResult.Error("Vault 不存在")
            if (!isVaultUnlocked(vaultId)) {
                return@withContext SendMutationResult.Error("Vault 未解锁")
            }

            var accessToken = accessTokenCache[vaultId]
                ?: return@withContext SendMutationResult.Error("令牌不可用")
            val expiresAt = vault.accessTokenExpiresAt ?: 0
            if (expiresAt <= System.currentTimeMillis() + 60000) {
                val refreshed = refreshToken(vault)
                if (refreshed != null) {
                    accessToken = refreshed
                    accessTokenCache[vaultId] = refreshed
                } else {
                    return@withContext SendMutationResult.Error("Token 刷新失败，请重新登录")
                }
            }

            val vaultApi = apiManager.getVaultApi(vault)
            val response = vaultApi.deleteSend(
                authorization = "Bearer $accessToken",
                sendId = sendId
            )

            if (!response.isSuccessful && response.code() != 404) {
                return@withContext SendMutationResult.Error(
                    "删除 Send 失败: ${response.code()} ${response.message()}"
                )
            }

            sendDao.deleteBySendId(vaultId, sendId)
            SendMutationResult.Deleted(sendId)
        } catch (e: Exception) {
            Log.e(TAG, "删除 Send 失败", e)
            SendMutationResult.Error(e.message ?: "删除 Send 失败")
        }
    }
    
    suspend fun getPasswordEntriesByFolder(vaultId: Long, folderId: String): List<PasswordEntry> = withContext(Dispatchers.IO) {
        passwordEntryDao.getByBitwardenFolderId(vaultId, folderId)
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
            val entry = passwordEntryDao.getByBitwardenCipherIdInVault(conflict.vaultId, cipherId)
            
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
    
    /**
     * 是否永不锁定 Bitwarden
     * 
     * 开启后：
     * - Bitwarden Vault 将保持解锁状态
     * - 密钥会持久化保存在内存中
     * - 适合安全环境下使用
     */
    var isNeverLockEnabled: Boolean
        get() = securePrefs.getBoolean(KEY_NEVER_LOCK_BITWARDEN, false)
        set(value) = securePrefs.edit().putBoolean(KEY_NEVER_LOCK_BITWARDEN, value).apply()
    
    val lastSyncTime: Long
        get() = securePrefs.getLong(KEY_LAST_SYNC_TIME, 0)

    /**
     * 同步队列计数（实时）
     */
    fun getPendingSyncCountFlow(): Flow<Int> = pendingOpDao.getPendingCountFlow()

    fun getFailedSyncCountFlow(): Flow<Int> = pendingOpDao.getFailedCountFlow()
    
    // ==================== 加密辅助 ====================
    
    private fun encryptForStorage(data: String): String {
        return Base64.encodeToString(data.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
    
    private fun decryptFromStorage(data: String): String {
        return String(Base64.decode(data, Base64.NO_WRAP), Charsets.UTF_8)
    }
    
    // ==================== 结果类型 ====================

    sealed class SendSyncResult {
        data class Success(val sends: List<BitwardenSend>) : SendSyncResult()
        data class Warning(val sends: List<BitwardenSend>, val message: String) : SendSyncResult()
        data class Error(val message: String) : SendSyncResult()
    }

    sealed class SendMutationResult {
        data class Success(val send: BitwardenSend) : SendMutationResult()
        data class Deleted(val sendId: String) : SendMutationResult()
        data class Error(val message: String) : SendMutationResult()
    }
    
    sealed class RepositoryLoginResult {
        data class Success(val vault: BitwardenVault) : RepositoryLoginResult()
        data class TwoFactorRequired(
            val providers: List<Int>,
            val state: LoginResult.TwoFactorRequired
        ) : RepositoryLoginResult()
        data class CaptchaRequired(
            val message: String,
            val siteKey: String? = null
        ) : RepositoryLoginResult()
        data class Error(val message: String) : RepositoryLoginResult()
    }
    
    sealed class UnlockResult {
        object Success : UnlockResult()
        data class Error(val message: String) : UnlockResult()
    }
    
    sealed class SyncResult {
        data class Success(
            val syncedCount: Int,
            val conflictCount: Int,
            val uploadFailedCount: Int,
            val skippedDueToLocalDirtyCount: Int
        ) : SyncResult()
        data class Error(val message: String) : SyncResult()
        
        /**
         * 空 Vault 保护阻止了同步
         */
        data class EmptyVaultBlocked(
            val vaultId: Long,
            val localCount: Int,
            val serverCount: Int,
            val reason: String
        ) : SyncResult()
    }

    enum class CacheClearMode {
        SAFE_ONLY_SYNCED,
        FULL_FORCE
    }

    data class VaultCacheRiskSummary(
        val vaultId: Long,
        val pendingOperationCount: Int,
        val passwordLocalModifiedCount: Int,
        val secureItemLocalModifiedCount: Int,
        val unresolvedConflictCount: Int
    ) {
        val hasRisk: Boolean
            get() = pendingOperationCount > 0 ||
                passwordLocalModifiedCount > 0 ||
                secureItemLocalModifiedCount > 0 ||
                unresolvedConflictCount > 0
    }

    data class CacheClearResult(
        val mode: CacheClearMode,
        val riskSummary: VaultCacheRiskSummary,
        val protectedCipherCount: Int,
        val passwordClearedCount: Int,
        val secureItemClearedCount: Int,
        val passkeyClearedCount: Int,
        val folderClearedCount: Int,
        val sendClearedCount: Int,
        val unresolvedConflictClearedCount: Int,
        val pendingOperationClearedCount: Int
    ) {
        val totalClearedCount: Int
            get() = passwordClearedCount +
                secureItemClearedCount +
                passkeyClearedCount +
                folderClearedCount +
                sendClearedCount +
                unresolvedConflictClearedCount +
                pendingOperationClearedCount
    }
}
