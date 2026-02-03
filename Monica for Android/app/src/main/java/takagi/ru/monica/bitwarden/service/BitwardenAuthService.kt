package takagi.ru.monica.bitwarden.service

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import takagi.ru.monica.bitwarden.api.*
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey
import takagi.ru.monica.data.bitwarden.BitwardenVault
import java.util.UUID

/**
 * Bitwarden 认证服务
 * 
 * 负责处理:
 * 1. 预登录 (获取 KDF 参数)
 * 2. 登录 (获取访问令牌)
 * 3. 令牌刷新
 * 4. 两步验证
 * 
 * 安全注意:
 * - 密码不会存储，只存储加密的令牌和密钥
 * - 敏感操作完成后立即清除内存中的密钥材料
 */
class BitwardenAuthService(
    private val context: Context,
    private val apiManager: BitwardenApiManager = BitwardenApiManager()
) {
    
    companion object {
        private const val TAG = "BitwardenAuthService"
        
        // 两步验证类型
        const val TWO_FACTOR_AUTHENTICATOR = 0
        const val TWO_FACTOR_EMAIL = 1
        const val TWO_FACTOR_DUO = 2
        const val TWO_FACTOR_YUBIKEY = 3
        const val TWO_FACTOR_U2F = 4
        const val TWO_FACTOR_REMEMBER = 5
        const val TWO_FACTOR_WEBAUTHN = 7
        const val TWO_FACTOR_EMAIL_NEW_DEVICE = -100
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    
    /**
     * 预登录 - 获取 KDF 参数
     */
    suspend fun preLogin(
        email: String,
        serverUrl: String = BitwardenApiFactory.OFFICIAL_VAULT_URL
    ): Result<PreLoginResult> = withContext(Dispatchers.IO) {
        try {
            val urls = BitwardenApiFactory.inferServerUrls(serverUrl)
            val identityApi = apiManager.getIdentityApi(urls.identity)
            
            val response = identityApi.preLogin(PreLoginRequest(email))
            
            if (response.isSuccessful) {
                val body = response.body() ?: return@withContext Result.failure(
                    Exception("Empty response from server")
                )
                
                Result.success(
                    PreLoginResult(
                        kdfType = body.kdf,
                        kdfIterations = body.kdfIterations,
                        kdfMemory = body.kdfMemory,
                        kdfParallelism = body.kdfParallelism
                    )
                )
            } else {
                Result.failure(
                    Exception("PreLogin failed: ${response.code()} ${response.message()}")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 登录 - 完整流程
     * 
     * @param email 用户邮箱
     * @param password 用户主密码
     * @param serverUrl Vault 服务 URL
     * @return 登录结果，包含令牌和加密密钥
     */
    suspend fun login(
        email: String,
        password: String,
        serverUrl: String = BitwardenApiFactory.OFFICIAL_VAULT_URL
    ): Result<LoginResult> = withContext(Dispatchers.IO) {
        var masterKey: ByteArray? = null
        var stretchedKey: SymmetricCryptoKey? = null
        
        try {
            val urls = BitwardenApiFactory.inferServerUrls(serverUrl)
            
            // 调试: 打印密码信息（不显示完整密码）
            Log.d(TAG, "login: password.length=${password.length}, first=${password.firstOrNull()}, last=${password.lastOrNull()}")
            Log.d(TAG, "login: password bytes (hex first 4)=${password.toByteArray(Charsets.UTF_8).take(4).joinToString("") { "%02x".format(it) }}")
            
            // 1. 预登录获取 KDF 参数
            val preLoginResult = preLogin(email, serverUrl).getOrElse {
                return@withContext Result.failure(it)
            }
            
            // 邮箱必须小写化 (使用英文 locale，与 Keyguard 保持一致)
            val emailLower = email.lowercase(java.util.Locale.ENGLISH)
            
            // 调试: 对比两种 PBKDF2 实现
            Log.d(TAG, "========== PBKDF2 Implementation Comparison ==========")
            val comparison = BitwardenCrypto.comparePbkdf2Implementations(
                password = password,
                salt = emailLower,
                iterations = preLoginResult.kdfIterations
            )
            Log.d(TAG, comparison)
            Log.d(TAG, "========== End Comparison ==========")
            
            // 2. 派生 Master Key
            masterKey = if (preLoginResult.kdfType == BitwardenVault.KDF_TYPE_ARGON2ID) {
                BitwardenCrypto.deriveMasterKeyArgon2(
                    password = password,
                    salt = emailLower,  // 使用小写邮箱作为盐
                    iterations = preLoginResult.kdfIterations,
                    memory = preLoginResult.kdfMemory ?: 64,
                    parallelism = preLoginResult.kdfParallelism ?: 4
                )
            } else {
                BitwardenCrypto.deriveMasterKeyPbkdf2(
                    password = password,
                    salt = emailLower,  // 使用小写邮箱作为盐
                    iterations = preLoginResult.kdfIterations
                )
            }
            
            // 3. 派生 Master Password Hash (用于服务器认证)
            val passwordHash = BitwardenCrypto.deriveMasterPasswordHash(masterKey, password)
            
            // 4. 扩展 Master Key 为 Stretched Key
            stretchedKey = BitwardenCrypto.stretchMasterKey(masterKey)
            
            // 5. 准备 Auth-Email header (Base64 编码的邮箱，URL-safe)
            // 注意: Auth-Email 和 username 使用原始邮箱，不是小写！
            // 只有密钥派生使用小写邮箱作为盐
            val authEmail = toBase64UrlNoPadding(email)
            
            // 调试日志 - 用于验证登录参数 (使用 Log.e 确保一定显示)
            Log.e(TAG, "========== LOGIN REQUEST PARAMETERS ==========")
            Log.e(TAG, "login: email=$email")
            Log.e(TAG, "login: emailLower (used for KDF salt)=$emailLower")
            Log.e(TAG, "login: KDF type=${preLoginResult.kdfType}, iterations=${preLoginResult.kdfIterations}")
            Log.e(TAG, "login: authEmail (Base64)=$authEmail")
            Log.e(TAG, "login: passwordHash=$passwordHash")
            Log.e(TAG, "login: masterKey (hex)=${masterKey.joinToString("") { "%02x".format(it) }}")
            Log.e(TAG, "login: deviceId=${getDeviceId()}")
            Log.e(TAG, "================================================")
            
            // 6. 发送登录请求 (模拟 Keyguard Linux Desktop 模式)
            val identityApi = apiManager.getIdentityApi(urls.identity)
            val response = identityApi.login(
                authEmail = authEmail,
                username = email,
                passwordHash = passwordHash,
                deviceIdentifier = getDeviceId()
                // deviceName 使用默认值 "linux"
            )
            
            if (response.isSuccessful) {
                val body = response.body() ?: return@withContext Result.failure(
                    Exception("Empty login response")
                )
                
                // 检查是否需要两步验证
                if (body.twoFactorProviders != null && body.twoFactorProviders.isNotEmpty()) {
                    return@withContext Result.success(
                        LoginResult.TwoFactorRequired(
                            providers = body.twoFactorProviders,
                            providersData = body.twoFactorProviders2,
                            // 保存中间状态用于后续两步验证
                            tempMasterKey = masterKey,
                            tempStretchedKey = stretchedKey,
                            email = email,
                            passwordHash = passwordHash,
                            kdfType = preLoginResult.kdfType,
                            kdfIterations = preLoginResult.kdfIterations,
                            kdfMemory = preLoginResult.kdfMemory,
                            kdfParallelism = preLoginResult.kdfParallelism
                        )
                    )
                }
                
                // 解密 Protected Symmetric Key
                val encryptedKey = body.key ?: return@withContext Result.failure(
                    Exception("No encryption key in response")
                )
                
                val symmetricKey = BitwardenCrypto.decryptSymmetricKey(encryptedKey, stretchedKey)
                
                Result.success(
                    LoginResult.Success(
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken,
                        expiresIn = body.expiresIn,
                        masterKey = masterKey,
                        stretchedKey = stretchedKey,
                        symmetricKey = symmetricKey,
                        kdfType = preLoginResult.kdfType,
                        kdfIterations = preLoginResult.kdfIterations,
                        kdfMemory = preLoginResult.kdfMemory,
                        kdfParallelism = preLoginResult.kdfParallelism,
                        serverUrls = urls
                    )
                )
            } else {
                val errorBody = response.errorBody()?.string()
                val errorResponse = parseTokenError(errorBody)

                // 两步验证 (标准 2FA)
                val providers = errorResponse?.twoFactorProviders
                if (!providers.isNullOrEmpty()) {
                    return@withContext Result.success(
                        LoginResult.TwoFactorRequired(
                            providers = providers,
                            providersData = errorResponse.twoFactorProviders2,
                            tempMasterKey = masterKey,
                            tempStretchedKey = stretchedKey,
                            email = email,
                            passwordHash = passwordHash,
                            kdfType = preLoginResult.kdfType,
                            kdfIterations = preLoginResult.kdfIterations,
                            kdfMemory = preLoginResult.kdfMemory,
                            kdfParallelism = preLoginResult.kdfParallelism
                        )
                    )
                }

                // 新设备验证 (Email New Device OTP)
                if (isNewDeviceVerificationRequired(errorResponse)) {
                    return@withContext Result.success(
                        LoginResult.TwoFactorRequired(
                            providers = listOf(TWO_FACTOR_EMAIL_NEW_DEVICE),
                            providersData = null,
                            tempMasterKey = masterKey,
                            tempStretchedKey = stretchedKey,
                            email = email,
                            passwordHash = passwordHash,
                            kdfType = preLoginResult.kdfType,
                            kdfIterations = preLoginResult.kdfIterations,
                            kdfMemory = preLoginResult.kdfMemory,
                            kdfParallelism = preLoginResult.kdfParallelism
                        )
                    )
                }
                Log.e(TAG, "Login failed: ${response.code()} - $errorBody")
                
                // 如果是密码错误，运行加密测试来验证实现
                if (errorBody?.contains("invalid_username_or_password") == true || 
                    errorBody?.contains("invalid_grant") == true) {
                    Log.e(TAG, "========== Running Crypto Tests ==========")
                    try {
                        val testReport = takagi.ru.monica.bitwarden.crypto.BitwardenCryptoTest.runAllTests()
                        Log.e(TAG, testReport)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to run crypto tests: ${e.message}")
                    }
                    Log.e(TAG, "========== End Crypto Tests ==========")
                }
                
                Result.failure(
                    Exception("Login failed: ${response.code()} - $errorBody")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            // 安全清除敏感数据 (如果登录失败)
            // 成功时密钥需要传递给调用者，由调用者负责清理
        }
    }
    
    /**
     * 两步验证登录
     */
    suspend fun loginTwoFactor(
        twoFactorState: LoginResult.TwoFactorRequired,
        twoFactorCode: String,
        twoFactorProvider: Int,
        remember: Boolean = false,
        serverUrl: String = BitwardenApiFactory.OFFICIAL_VAULT_URL
    ): Result<LoginResult.Success> = withContext(Dispatchers.IO) {
        try {
            val urls = BitwardenApiFactory.inferServerUrls(serverUrl)
            val identityApi = apiManager.getIdentityApi(urls.identity)
            
            // Auth-Email header - 使用原始邮箱，不是小写！
            val authEmail = toBase64UrlNoPadding(twoFactorState.email)
            
            val response = identityApi.loginTwoFactor(
                authEmail = authEmail,
                username = twoFactorState.email,
                passwordHash = twoFactorState.passwordHash,
                deviceIdentifier = getDeviceId(),
                // deviceName 使用默认值 "linux"
                twoFactorToken = twoFactorCode.trim(),  // keyguard 也会 trim
                twoFactorProvider = twoFactorProvider,
                twoFactorRemember = if (remember) 1 else 0
            )
            
            if (response.isSuccessful) {
                val body = response.body() ?: return@withContext Result.failure(
                    Exception("Empty two-factor response")
                )
                
                val encryptedKey = body.key ?: return@withContext Result.failure(
                    Exception("No encryption key in response")
                )
                
                val symmetricKey = BitwardenCrypto.decryptSymmetricKey(
                    encryptedKey, 
                    twoFactorState.tempStretchedKey
                )
                
                Result.success(
                    LoginResult.Success(
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken,
                        expiresIn = body.expiresIn,
                        masterKey = twoFactorState.tempMasterKey,
                        stretchedKey = twoFactorState.tempStretchedKey,
                        symmetricKey = symmetricKey,
                        kdfType = twoFactorState.kdfType,
                        kdfIterations = twoFactorState.kdfIterations,
                        kdfMemory = twoFactorState.kdfMemory,
                        kdfParallelism = twoFactorState.kdfParallelism,
                        serverUrls = urls,
                        twoFactorToken = body.twoFactorToken
                    )
                )
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(
                    Exception("Two-factor login failed: ${response.code()} - $errorBody")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 新设备验证登录 (Email New Device OTP)
     */
    suspend fun loginNewDeviceOtp(
        twoFactorState: LoginResult.TwoFactorRequired,
        newDeviceOtp: String,
        serverUrl: String = BitwardenApiFactory.OFFICIAL_VAULT_URL
    ): Result<LoginResult.Success> = withContext(Dispatchers.IO) {
        try {
            val urls = BitwardenApiFactory.inferServerUrls(serverUrl)
            val identityApi = apiManager.getIdentityApi(urls.identity)

            val authEmail = toBase64UrlNoPadding(twoFactorState.email)

            val response = identityApi.loginNewDeviceOtp(
                authEmail = authEmail,
                username = twoFactorState.email,
                passwordHash = twoFactorState.passwordHash,
                deviceIdentifier = getDeviceId(),
                newDeviceOtp = newDeviceOtp.trim()
            )

            if (response.isSuccessful) {
                val body = response.body() ?: return@withContext Result.failure(
                    Exception("Empty new device response")
                )

                val encryptedKey = body.key ?: return@withContext Result.failure(
                    Exception("No encryption key in response")
                )

                val symmetricKey = BitwardenCrypto.decryptSymmetricKey(
                    encryptedKey,
                    twoFactorState.tempStretchedKey
                )

                Result.success(
                    LoginResult.Success(
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken,
                        expiresIn = body.expiresIn,
                        masterKey = twoFactorState.tempMasterKey,
                        stretchedKey = twoFactorState.tempStretchedKey,
                        symmetricKey = symmetricKey,
                        kdfType = twoFactorState.kdfType,
                        kdfIterations = twoFactorState.kdfIterations,
                        kdfMemory = twoFactorState.kdfMemory,
                        kdfParallelism = twoFactorState.kdfParallelism,
                        serverUrls = urls
                    )
                )
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "New device login failed: ${response.code()} - $errorBody")
                Result.failure(
                    Exception("New device login failed: ${response.code()} - $errorBody")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 刷新访问令牌
     */
    suspend fun refreshToken(
        refreshToken: String,
        identityUrl: String = BitwardenApiFactory.OFFICIAL_IDENTITY_URL
    ): Result<RefreshResult> = withContext(Dispatchers.IO) {
        try {
            val identityApi = apiManager.getIdentityApi(identityUrl)
            
            val response = identityApi.refreshToken(
                refreshToken = refreshToken
            )
            
            if (response.isSuccessful) {
                val body = response.body() ?: return@withContext Result.failure(
                    Exception("Empty refresh response")
                )
                
                Result.success(
                    RefreshResult(
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken,
                        expiresIn = body.expiresIn
                    )
                )
            } else {
                Result.failure(
                    Exception("Token refresh failed: ${response.code()}")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取设备 ID
     */
    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: UUID.randomUUID().toString()
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }
    
    /**
     * 获取设备名称
     * 
     * 使用真实设备名称，因为现在使用 mobile 客户端类型
     */
    private fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    private fun toBase64UrlNoPadding(value: String): String {
        val base64 = Base64.encodeToString(
            value.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        return base64
            .replace('+', '-')
            .replace('/', '_')
            .replace("=", "")
    }

    private fun parseTokenError(errorBody: String?): TokenResponse? {
        if (errorBody.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<TokenResponse>(errorBody) }
            .getOrNull()
    }

    private fun isNewDeviceVerificationRequired(errorResponse: TokenResponse?): Boolean {
        val message = errorResponse?.errorModel?.message ?: return false
        return message.equals("new device verification required", ignoreCase = true)
    }
}

// ========== 结果数据类 ==========

data class PreLoginResult(
    val kdfType: Int,
    val kdfIterations: Int,
    val kdfMemory: Int?,
    val kdfParallelism: Int?
)

sealed class LoginResult {
    
    data class Success(
        val accessToken: String,
        val refreshToken: String?,
        val expiresIn: Int,
        val masterKey: ByteArray,
        val stretchedKey: SymmetricCryptoKey,
        val symmetricKey: SymmetricCryptoKey,
        val kdfType: Int,
        val kdfIterations: Int,
        val kdfMemory: Int?,
        val kdfParallelism: Int?,
        val serverUrls: BitwardenApiFactory.ServerUrls,
        val twoFactorToken: String? = null
    ) : LoginResult() {
        
        /**
         * 清除敏感密钥材料
         * 调用者在保存必要数据后应调用此方法
         */
        fun clearKeys() {
            masterKey.fill(0)
            stretchedKey.clear()
            symmetricKey.clear()
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return accessToken == other.accessToken
        }
        
        override fun hashCode(): Int = accessToken.hashCode()
    }
    
    data class TwoFactorRequired(
        val providers: List<Int>,
        val providersData: Map<String, Map<String, String>>?,
        val tempMasterKey: ByteArray,
        val tempStretchedKey: SymmetricCryptoKey,
        val email: String,
        val passwordHash: String,
        val kdfType: Int,
        val kdfIterations: Int,
        val kdfMemory: Int?,
        val kdfParallelism: Int?
    ) : LoginResult() {
        
        /**
         * 获取支持的两步验证方式名称
         */
        fun getProviderNames(): List<String> {
            return providers.map { provider ->
                when (provider) {
                    BitwardenAuthService.TWO_FACTOR_AUTHENTICATOR -> "Authenticator App"
                    BitwardenAuthService.TWO_FACTOR_EMAIL -> "Email"
                    BitwardenAuthService.TWO_FACTOR_DUO -> "Duo"
                    BitwardenAuthService.TWO_FACTOR_YUBIKEY -> "YubiKey"
                    BitwardenAuthService.TWO_FACTOR_WEBAUTHN -> "WebAuthn"
                    else -> "Unknown"
                }
            }
        }
        
        fun clear() {
            tempMasterKey.fill(0)
            tempStretchedKey.clear()
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TwoFactorRequired) return false
            return email == other.email
        }
        
        override fun hashCode(): Int = email.hashCode()
    }
}

data class RefreshResult(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Int
)
