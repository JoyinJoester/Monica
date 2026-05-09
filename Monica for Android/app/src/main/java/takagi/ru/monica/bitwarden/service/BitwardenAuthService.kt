package takagi.ru.monica.bitwarden.service

import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import takagi.ru.monica.bitwarden.api.*
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey
import takagi.ru.monica.data.bitwarden.BitwardenVault
import java.util.Locale
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
    init {
        BitwardenDiagLogger.initialize(context.applicationContext)
    }
    
    companion object {
        private const val TAG = "BitwardenAuthService"
        private const val DIAG_PREFIX = "[BW_DIAG]"
        private const val ERROR_BODY_SNIPPET_LIMIT = 240
        private const val PBKDF2_DEFAULT_ITERATIONS = 600000
        private const val ARGON2_DEFAULT_ITERATIONS = 3
        private const val ARGON2_DEFAULT_MEMORY_MB = 64
        private const val ARGON2_DEFAULT_PARALLELISM = 4
        
        // 两步验证类型
        const val TWO_FACTOR_AUTHENTICATOR = 0
        const val TWO_FACTOR_EMAIL = 1
        const val TWO_FACTOR_DUO = 2
        const val TWO_FACTOR_YUBIKEY = 3
        const val TWO_FACTOR_U2F = 4
        const val TWO_FACTOR_REMEMBER = 5
        const val TWO_FACTOR_ORGANIZATION_DUO = 6
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
        serverUrl: String = BitwardenApiFactory.OFFICIAL_VAULT_URL,
        diagnosticAttemptId: String? = null,
        tlsConfig: BitwardenTlsConfig? = null
    ): Result<PreLoginResult> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val normalizedEmail = email.trim()
            val urls = BitwardenApiFactory.inferServerUrls(serverUrl)
            diagnosticAttemptId?.let { attemptId ->
                logDiag(
                    flow = "primary",
                    attemptId = attemptId,
                    stage = "prelogin_start",
                    message =
                        "serverClass=${classifyServer(urls.vault)}, identity=${urls.identity}, " +
                            "emailDomain=${emailDomain(normalizedEmail)}"
                )
            }
            val identityApi = apiManager.getIdentityApi(
                identityUrl = urls.identity,
                refererUrl = urls.vault,
                tlsConfig = tlsConfig
            )
            
            val response = identityApi.preLogin(PreLoginRequest(normalizedEmail))
            
            if (response.isSuccessful) {
                val body = response.body() ?: return@withContext Result.failure(
                    Exception("Empty response from server")
                )
                diagnosticAttemptId?.let { attemptId ->
                    logDiag(
                        flow = "primary",
                        attemptId = attemptId,
                        stage = "prelogin_ok",
                        message =
                            "kdf=${body.kdf}, iter=${body.kdfIterations}, mem=${body.kdfMemory}, " +
                                "parallelism=${body.kdfParallelism}, latencyMs=${System.currentTimeMillis() - startMs}"
                    )
                }
                
                Result.success(
                    normalizePreLoginResult(
                        kdfType = body.kdf,
                        kdfIterations = body.kdfIterations,
                        kdfMemory = body.kdfMemory,
                        kdfParallelism = body.kdfParallelism,
                        diagnosticAttemptId = diagnosticAttemptId
                    )
                )
            } else {
                val errorBody = response.errorBody()?.string()
                diagnosticAttemptId?.let { attemptId ->
                    logDiag(
                        flow = "primary",
                        attemptId = attemptId,
                        stage = "prelogin_fail",
                        message =
                            "code=${response.code()}, message=${response.message()}, " +
                                "latencyMs=${System.currentTimeMillis() - startMs}, " +
                                "body=${oneLine(errorBody, ERROR_BODY_SNIPPET_LIMIT)}"
                    )
                }
                Result.failure(
                    Exception(
                        "PreLogin failed: ${response.code()} ${response.message()} " +
                            "[attemptId=${diagnosticAttemptId ?: "n/a"}]"
                    )
                )
            }
        } catch (e: Exception) {
            diagnosticAttemptId?.let { attemptId ->
                logDiag(
                    flow = "primary",
                    attemptId = attemptId,
                    stage = "prelogin_exception",
                    message =
                        "type=${e.javaClass.simpleName}, msg=${oneLine(e.message, 120)}, " +
                            "latencyMs=${System.currentTimeMillis() - startMs}"
                )
            }
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
        serverUrl: String = BitwardenApiFactory.OFFICIAL_VAULT_URL,
        captchaResponse: String? = null,
        tlsConfig: BitwardenTlsConfig? = null
    ): Result<LoginResult> = withContext(Dispatchers.IO) {
        var masterKey: ByteArray? = null
        var stretchedKey: SymmetricCryptoKey? = null
        val startMs = System.currentTimeMillis()
        val attemptId = newAttemptId()
        
        try {
            val normalizedEmail = email.trim()
            val urls = BitwardenApiFactory.inferServerUrls(serverUrl)
            val normalizedCaptcha = captchaResponse?.trim()?.takeIf { it.isNotBlank() }
            val emailTrimmed = normalizedEmail != email
            val emailContainsUpper = normalizedEmail.any { it.isUpperCase() }
            val primaryHeaderProfile = BitwardenApiFactory.HeaderProfile.MONICA_DEFAULT
            val primaryHeaderProfileName = BitwardenApiFactory.headerProfileName(primaryHeaderProfile)
            val primaryUaVersion = BitwardenApiFactory.headerProfileUserAgentVersion(primaryHeaderProfile)
            val primaryRefererApplied = BitwardenApiFactory.isRefererApplied(primaryHeaderProfile, urls.vault)

            logDiag(
                flow = "primary",
                attemptId = attemptId,
                stage = "start",
                message =
                    "serverClass=${classifyServer(urls.vault)}, vault=${urls.vault}, identity=${urls.identity}, api=${urls.api}, " +
                        "emailDomain=${emailDomain(normalizedEmail)}, emailTrimmed=$emailTrimmed, " +
                        "emailContainsUpper=$emailContainsUpper, passwordLen=${password.length}, " +
                        "captchaProvided=${!normalizedCaptcha.isNullOrBlank()}, androidApi=${Build.VERSION.SDK_INT}, " +
                        "headerProfile=$primaryHeaderProfileName, uaVersion=$primaryUaVersion, refererApplied=$primaryRefererApplied"
            )
            
            // 1. 预登录获取 KDF 参数
            val preLoginResult = preLogin(
                email = normalizedEmail,
                serverUrl = serverUrl,
                diagnosticAttemptId = attemptId,
                tlsConfig = tlsConfig
            ).getOrElse {
                logDiag(
                    flow = "primary",
                    attemptId = attemptId,
                    stage = "stop_prelogin_error",
                    message =
                        "reason=${oneLine(it.message, 140)}, latencyMs=${System.currentTimeMillis() - startMs}"
                )
                return@withContext Result.failure(it)
            }
            
            // 邮箱必须小写化 (使用英文 locale，与 Keyguard 保持一致)
            val emailLower = normalizedEmail.lowercase(Locale.ENGLISH)
            
            // 2. 派生 Master Key
            masterKey = when (preLoginResult.kdfType) {
                BitwardenVault.KDF_TYPE_ARGON2ID -> {
                    BitwardenCrypto.deriveMasterKeyArgon2(
                        password = password,
                        salt = emailLower,  // 使用小写邮箱作为盐
                        iterations = preLoginResult.kdfIterations,
                        memory = preLoginResult.kdfMemory ?: ARGON2_DEFAULT_MEMORY_MB,
                        parallelism = preLoginResult.kdfParallelism ?: ARGON2_DEFAULT_PARALLELISM
                    )
                }

                BitwardenVault.KDF_TYPE_PBKDF2 -> {
                    BitwardenCrypto.deriveMasterKeyPbkdf2(
                        password = password,
                        salt = emailLower,  // 使用小写邮箱作为盐
                        iterations = preLoginResult.kdfIterations
                    )
                }

                else -> {
                    logDiag(
                        flow = "primary",
                        attemptId = attemptId,
                        stage = "stop_unsupported_kdf",
                        message =
                            "kdf=${preLoginResult.kdfType}, iter=${preLoginResult.kdfIterations}, " +
                                "mem=${preLoginResult.kdfMemory}, parallelism=${preLoginResult.kdfParallelism}"
                    )
                    return@withContext Result.failure(
                        IllegalArgumentException("Unsupported KDF type: ${preLoginResult.kdfType}")
                    )
                }
            }
            
            // 3. 派生 Master Password Hash (用于服务器认证)
            val passwordHash = BitwardenCrypto.deriveMasterPasswordHash(masterKey, password)
            
            // 4. 扩展 Master Key 为 Stretched Key
            stretchedKey = BitwardenCrypto.stretchMasterKey(masterKey)
            
            // 5. 准备 Auth-Email header (Base64 编码的邮箱，URL-safe)
            // 注意: Auth-Email 和 username 使用原始邮箱，不是小写！
            // 只有密钥派生使用小写邮箱作为盐
            val authEmail = toBase64UrlNoPadding(normalizedEmail)
            val deviceId = getDeviceId()
            val pwBytes = password.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            val pwCharCount = password.length
            val pwByteCount = pwBytes.size
            val pwHasNonAscii = password.any { it.code > 127 }
            val pwSpecialCount = password.count { !it.isLetterOrDigit() }
            val pwUpperCount = password.count { it.isUpperCase() }
            val pwLowerCount = password.count { it.isLowerCase() }
            val pwDigitCount = password.count { it.isDigit() }
            val pwLeadingSpace = password.first() == ' '
            val pwTrailingSpace = password.last() == ' '
            val emailCharCount = normalizedEmail.length
            val emailByteCount = normalizedEmail.toByteArray(java.nio.charset.StandardCharsets.UTF_8).size
            logDiag(
                flow = "primary",
                attemptId = attemptId,
                stage = "token_request",
                message =
                    "kdf=${preLoginResult.kdfType}, iter=${preLoginResult.kdfIterations}, mem=${preLoginResult.kdfMemory}, " +
                        "parallelism=${preLoginResult.kdfParallelism}, authEmailLen=${authEmail.length}, " +
                        "deviceId=$deviceId, headerProfile=$primaryHeaderProfileName, " +
                        "uaVersion=$primaryUaVersion, refererApplied=$primaryRefererApplied, " +
                        "pw_chars=$pwCharCount, pw_bytes=$pwByteCount, pw_non_ascii=$pwHasNonAscii, " +
                        "pw_special=$pwSpecialCount, pw_upper=$pwUpperCount, pw_lower=$pwLowerCount, " +
                        "pw_digit=$pwDigitCount, pw_leading_space=$pwLeadingSpace, pw_trailing_space=$pwTrailingSpace, " +
                        "email_chars=$emailCharCount, email_bytes=$emailByteCount"
            )
            
            // 6. 发送登录请求 (模拟 Keyguard Linux Desktop 模式)
            val identityApi = apiManager.getIdentityApi(
                identityUrl = urls.identity,
                refererUrl = urls.vault,
                headerProfile = primaryHeaderProfile,
                tlsConfig = tlsConfig
            )
            val response = identityApi.login(
                authEmail = authEmail,
                username = normalizedEmail,
                passwordHash = passwordHash,
                captchaResponse = normalizedCaptcha,
                deviceIdentifier = deviceId
                // deviceName 使用默认值 "linux"
            )
            
            if (response.isSuccessful) {
                val body = response.body() ?: return@withContext Result.failure(
                    Exception("Empty login response")
                )
                
                // 检查是否需要两步验证
                if (body.twoFactorProviders != null && body.twoFactorProviders.isNotEmpty()) {
                    logDiag(
                        flow = "primary",
                        attemptId = attemptId,
                        stage = "result_two_factor",
                        message =
                            "code=${response.code()}, providers=${body.twoFactorProviders.joinToString(",")}, " +
                                "latencyMs=${System.currentTimeMillis() - startMs}"
                    )
                    return@withContext Result.success(
                        LoginResult.TwoFactorRequired(
                            providers = body.twoFactorProviders.mapNotNull { it.toIntOrNull() },
                            providersData = body.twoFactorProviders2,
                            // 保存中间状态用于后续两步验证
                            tempMasterKey = masterKey,
                            tempStretchedKey = stretchedKey,
                            email = normalizedEmail,
                            passwordHash = passwordHash,
                            kdfType = preLoginResult.kdfType,
                            kdfIterations = preLoginResult.kdfIterations,
                            kdfMemory = preLoginResult.kdfMemory,
                            kdfParallelism = preLoginResult.kdfParallelism,
                            authHeaderProfile = primaryHeaderProfile,
                            diagnosticAttemptId = attemptId
                        )
                    )
                }

                // 解密 Protected Symmetric Key
                val encryptedKey = body.key ?: return@withContext Result.failure(
                    Exception("No encryption key in response")
                )
                
                val symmetricKey = BitwardenCrypto.decryptSymmetricKey(encryptedKey, stretchedKey)
                logDiag(
                    flow = "primary",
                    attemptId = attemptId,
                    stage = "result_success",
                    message =
                        "code=${response.code()}, expiresIn=${body.expiresIn}, hasRefresh=${!body.refreshToken.isNullOrBlank()}, " +
                            "latencyMs=${System.currentTimeMillis() - startMs}"
                )
                
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
                val providers = errorResponse?.twoFactorProviders?.mapNotNull { it.toIntOrNull() }
                if (!providers.isNullOrEmpty()) {
                    val tokenError = errorResponse
                    logDiag(
                        flow = "primary",
                        attemptId = attemptId,
                        stage = "result_two_factor_from_error",
                        message =
                            "code=${response.code()}, error=${tokenError.error}, desc=${tokenError.errorDescription}, " +
                                "providers=${providers.joinToString(",")}, latencyMs=${System.currentTimeMillis() - startMs}, " +
                                "body=${oneLine(errorBody, ERROR_BODY_SNIPPET_LIMIT)}"
                    )
                    return@withContext Result.success(
                        LoginResult.TwoFactorRequired(
                            providers = providers,
                            providersData = errorResponse.twoFactorProviders2,
                            tempMasterKey = masterKey,
                            tempStretchedKey = stretchedKey,
                            email = normalizedEmail,
                            passwordHash = passwordHash,
                            kdfType = preLoginResult.kdfType,
                            kdfIterations = preLoginResult.kdfIterations,
                            kdfMemory = preLoginResult.kdfMemory,
                            kdfParallelism = preLoginResult.kdfParallelism,
                            authHeaderProfile = primaryHeaderProfile,
                            diagnosticAttemptId = attemptId
                        )
                    )
                }

                // 新设备验证 (Email New Device OTP)
                if (isNewDeviceVerificationRequired(errorResponse)) {
                    logDiag(
                        flow = "primary",
                        attemptId = attemptId,
                        stage = "result_new_device_required",
                        message =
                            "code=${response.code()}, error=${errorResponse?.error}, desc=${errorResponse?.errorDescription}, " +
                                "latencyMs=${System.currentTimeMillis() - startMs}, body=${oneLine(errorBody, ERROR_BODY_SNIPPET_LIMIT)}"
                    )
                    return@withContext Result.success(
                        LoginResult.TwoFactorRequired(
                            providers = listOf(TWO_FACTOR_EMAIL_NEW_DEVICE),
                            providersData = null,
                            tempMasterKey = masterKey,
                            tempStretchedKey = stretchedKey,
                            email = normalizedEmail,
                            passwordHash = passwordHash,
                            kdfType = preLoginResult.kdfType,
                            kdfIterations = preLoginResult.kdfIterations,
                            kdfMemory = preLoginResult.kdfMemory,
                            kdfParallelism = preLoginResult.kdfParallelism,
                            authHeaderProfile = primaryHeaderProfile,
                            diagnosticAttemptId = attemptId
                        )
                    )
                }

                if (isCaptchaRequired(errorResponse, errorBody)) {
                    val message = errorResponse?.errorDescription
                        ?: errorResponse?.errorModel?.message
                        ?: "需要验证码，请输入 Captcha response 后重试"
                    logDiag(
                        flow = "primary",
                        attemptId = attemptId,
                        stage = "result_captcha_required",
                        message =
                            "code=${response.code()}, error=${errorResponse?.error}, desc=${errorResponse?.errorDescription}, " +
                                "hasSiteKey=${!errorResponse?.hCaptchaSiteKey.isNullOrBlank()}, latencyMs=${System.currentTimeMillis() - startMs}, " +
                                "body=${oneLine(errorBody, ERROR_BODY_SNIPPET_LIMIT)}"
                    )
                    return@withContext Result.success(
                        LoginResult.CaptchaRequired(
                            message = message,
                            siteKey = errorResponse?.hCaptchaSiteKey
                        )
                    )
                }
                var retrySummary = "not_attempted"
                if (shouldRetryWithKeyguardFallback(
                        responseCode = response.code(),
                        errorResponse = errorResponse,
                        errorBody = errorBody,
                        captchaProvided = !normalizedCaptcha.isNullOrBlank()
                    )
                ) {
                    val retryHeaderProfile = BitwardenApiFactory.HeaderProfile.KEYGUARD_FALLBACK
                    val retryHeaderProfileName = BitwardenApiFactory.headerProfileName(retryHeaderProfile)
                    val retryUaVersion = BitwardenApiFactory.headerProfileUserAgentVersion(retryHeaderProfile)
                    val retryRefererApplied = BitwardenApiFactory.isRefererApplied(retryHeaderProfile, urls.vault)
                    logDiag(
                        flow = "primary",
                        attemptId = attemptId,
                        stage = "retry_keyguard_start",
                        message =
                            "reason=invalid_grant, headerProfile=$retryHeaderProfileName, uaVersion=$retryUaVersion, " +
                                "refererApplied=$retryRefererApplied, firstCode=${response.code()}, " +
                                "firstError=${errorResponse?.error}, firstDesc=${errorResponse?.errorDescription}"
                    )

                    try {
                        val retryIdentityApi = apiManager.getIdentityApi(
                            identityUrl = urls.identity,
                            refererUrl = urls.vault,
                            headerProfile = retryHeaderProfile,
                            tlsConfig = tlsConfig
                        )
                        val retryResponse = retryIdentityApi.login(
                            authEmail = authEmail,
                            username = normalizedEmail,
                            passwordHash = passwordHash,
                            captchaResponse = normalizedCaptcha,
                            deviceIdentifier = deviceId
                        )
                        if (retryResponse.isSuccessful) {
                            val retryBody = retryResponse.body() ?: return@withContext Result.failure(
                                Exception("Empty login response on retry")
                            )
                            if (retryBody.twoFactorProviders != null && retryBody.twoFactorProviders.isNotEmpty()) {
                                logDiag(
                                    flow = "primary",
                                    attemptId = attemptId,
                                    stage = "retry_keyguard_two_factor",
                                    message =
                                        "code=${retryResponse.code()}, providers=${retryBody.twoFactorProviders.joinToString(",")}, " +
                                            "latencyMs=${System.currentTimeMillis() - startMs}"
                                )
                                return@withContext Result.success(
                                    LoginResult.TwoFactorRequired(
                                        providers = retryBody.twoFactorProviders.mapNotNull { it.toIntOrNull() },
                                        providersData = retryBody.twoFactorProviders2,
                                        tempMasterKey = masterKey,
                                        tempStretchedKey = stretchedKey,
                                        email = normalizedEmail,
                                        passwordHash = passwordHash,
                                        kdfType = preLoginResult.kdfType,
                                        kdfIterations = preLoginResult.kdfIterations,
                                        kdfMemory = preLoginResult.kdfMemory,
                                        kdfParallelism = preLoginResult.kdfParallelism,
                                        authHeaderProfile = retryHeaderProfile,
                                        diagnosticAttemptId = attemptId
                                    )
                                )
                            }

                            val retryEncryptedKey = retryBody.key ?: return@withContext Result.failure(
                                Exception("No encryption key in retry response")
                            )
                            val retrySymmetricKey = BitwardenCrypto.decryptSymmetricKey(
                                retryEncryptedKey,
                                stretchedKey
                            )
                            logDiag(
                                flow = "primary",
                                attemptId = attemptId,
                                stage = "retry_keyguard_success",
                                message =
                                    "code=${retryResponse.code()}, expiresIn=${retryBody.expiresIn}, hasRefresh=${!retryBody.refreshToken.isNullOrBlank()}, " +
                                        "latencyMs=${System.currentTimeMillis() - startMs}"
                            )
                            return@withContext Result.success(
                                LoginResult.Success(
                                    accessToken = retryBody.accessToken,
                                    refreshToken = retryBody.refreshToken,
                                    expiresIn = retryBody.expiresIn,
                                    masterKey = masterKey,
                                    stretchedKey = stretchedKey,
                                    symmetricKey = retrySymmetricKey,
                                    kdfType = preLoginResult.kdfType,
                                    kdfIterations = preLoginResult.kdfIterations,
                                    kdfMemory = preLoginResult.kdfMemory,
                                    kdfParallelism = preLoginResult.kdfParallelism,
                                    serverUrls = urls
                                )
                            )
                        } else {
                            val retryErrorBody = retryResponse.errorBody()?.string()
                            val retryErrorResponse = parseTokenError(retryErrorBody)
                            retrySummary =
                                "code=${retryResponse.code()},error=${retryErrorResponse?.error},desc=${retryErrorResponse?.errorDescription}"

                            val retryProviders = retryErrorResponse?.twoFactorProviders?.mapNotNull { it.toIntOrNull() }
                            if (!retryProviders.isNullOrEmpty()) {
                                logDiag(
                                    flow = "primary",
                                    attemptId = attemptId,
                                    stage = "retry_keyguard_two_factor_from_error",
                                    message =
                                        "code=${retryResponse.code()}, providers=${retryProviders.joinToString(",")}, " +
                                            "latencyMs=${System.currentTimeMillis() - startMs}, body=${oneLine(retryErrorBody, ERROR_BODY_SNIPPET_LIMIT)}"
                                )
                                return@withContext Result.success(
                                    LoginResult.TwoFactorRequired(
                                        providers = retryProviders,
                                        providersData = retryErrorResponse.twoFactorProviders2,
                                        tempMasterKey = masterKey,
                                        tempStretchedKey = stretchedKey,
                                        email = normalizedEmail,
                                        passwordHash = passwordHash,
                                        kdfType = preLoginResult.kdfType,
                                        kdfIterations = preLoginResult.kdfIterations,
                                        kdfMemory = preLoginResult.kdfMemory,
                                        kdfParallelism = preLoginResult.kdfParallelism,
                                        authHeaderProfile = retryHeaderProfile,
                                        diagnosticAttemptId = attemptId
                                    )
                                )
                            }

                            if (isNewDeviceVerificationRequired(retryErrorResponse)) {
                                logDiag(
                                    flow = "primary",
                                    attemptId = attemptId,
                                    stage = "retry_keyguard_new_device_required",
                                    message =
                                        "code=${retryResponse.code()}, error=${retryErrorResponse?.error}, desc=${retryErrorResponse?.errorDescription}, " +
                                            "latencyMs=${System.currentTimeMillis() - startMs}, body=${oneLine(retryErrorBody, ERROR_BODY_SNIPPET_LIMIT)}"
                                )
                                return@withContext Result.success(
                                    LoginResult.TwoFactorRequired(
                                        providers = listOf(TWO_FACTOR_EMAIL_NEW_DEVICE),
                                        providersData = null,
                                        tempMasterKey = masterKey,
                                        tempStretchedKey = stretchedKey,
                                        email = normalizedEmail,
                                        passwordHash = passwordHash,
                                        kdfType = preLoginResult.kdfType,
                                        kdfIterations = preLoginResult.kdfIterations,
                                        kdfMemory = preLoginResult.kdfMemory,
                                        kdfParallelism = preLoginResult.kdfParallelism,
                                        authHeaderProfile = retryHeaderProfile,
                                        diagnosticAttemptId = attemptId
                                    )
                                )
                            }

                            if (isCaptchaRequired(retryErrorResponse, retryErrorBody)) {
                                val retryMessage = retryErrorResponse?.errorDescription
                                    ?: retryErrorResponse?.errorModel?.message
                                    ?: "需要验证码，请输入 Captcha response 后重试"
                                logDiag(
                                    flow = "primary",
                                    attemptId = attemptId,
                                    stage = "retry_keyguard_captcha_required",
                                    message =
                                        "code=${retryResponse.code()}, error=${retryErrorResponse?.error}, desc=${retryErrorResponse?.errorDescription}, " +
                                            "hasSiteKey=${!retryErrorResponse?.hCaptchaSiteKey.isNullOrBlank()}, latencyMs=${System.currentTimeMillis() - startMs}, " +
                                            "body=${oneLine(retryErrorBody, ERROR_BODY_SNIPPET_LIMIT)}"
                                )
                                return@withContext Result.success(
                                    LoginResult.CaptchaRequired(
                                        message = retryMessage,
                                        siteKey = retryErrorResponse?.hCaptchaSiteKey
                                    )
                                )
                            }

                            logDiag(
                                flow = "primary",
                                attemptId = attemptId,
                                stage = "retry_keyguard_error",
                                message =
                                    "code=${retryResponse.code()}, error=${retryErrorResponse?.error}, desc=${retryErrorResponse?.errorDescription}, " +
                                        "latencyMs=${System.currentTimeMillis() - startMs}, body=${oneLine(retryErrorBody, ERROR_BODY_SNIPPET_LIMIT)}"
                            )
                        }
                    } catch (retryError: Exception) {
                        retrySummary = "exception:${retryError.javaClass.simpleName}"
                        logDiag(
                            flow = "primary",
                            attemptId = attemptId,
                            stage = "retry_keyguard_exception",
                            message =
                                "type=${retryError.javaClass.simpleName}, msg=${oneLine(retryError.message, 120)}, " +
                                    "latencyMs=${System.currentTimeMillis() - startMs}"
                        )
                    }
                }
                Log.e(
                    TAG,
                    "Login failed: code=${response.code()}, error=${errorResponse?.error}, desc=${errorResponse?.errorDescription}"
                )
                logDiag(
                    flow = "primary",
                    attemptId = attemptId,
                    stage = "result_error",
                    message =
                        "code=${response.code()}, error=${errorResponse?.error}, desc=${errorResponse?.errorDescription}, " +
                            "modelMsg=${oneLine(errorResponse?.errorModel?.message, 100)}, latencyMs=${System.currentTimeMillis() - startMs}, " +
                            "body=${oneLine(errorBody, ERROR_BODY_SNIPPET_LIMIT)}, retry=$retrySummary"
                )
                
                Result.failure(
                    Exception("Login failed [attemptId=$attemptId]: ${response.code()} - $errorBody")
                )
            }
        } catch (e: Exception) {
            logDiag(
                flow = "primary",
                attemptId = attemptId,
                stage = "exception",
                message =
                    "type=${e.javaClass.simpleName}, msg=${oneLine(e.message, 140)}, " +
                        "latencyMs=${System.currentTimeMillis() - startMs}"
            )
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
        serverUrl: String = BitwardenApiFactory.OFFICIAL_VAULT_URL,
        captchaResponse: String? = null,
        tlsConfig: BitwardenTlsConfig? = null
    ): Result<LoginResult> = withContext(Dispatchers.IO) {
        val attemptId = twoFactorState.diagnosticAttemptId ?: newAttemptId()
        val startMs = System.currentTimeMillis()
        try {
            val urls = BitwardenApiFactory.inferServerUrls(serverUrl)
            val normalizedCaptcha = captchaResponse?.trim()?.takeIf { it.isNotBlank() }
            val headerProfile = twoFactorState.authHeaderProfile
            logDiag(
                flow = "two_factor",
                attemptId = attemptId,
                stage = "start",
                message =
                    "provider=$twoFactorProvider, remember=$remember, codeLen=${twoFactorCode.trim().length}, " +
                        "serverClass=${classifyServer(urls.vault)}, identity=${urls.identity}, " +
                        "captchaProvided=${!normalizedCaptcha.isNullOrBlank()}, " +
                        "headerProfile=${BitwardenApiFactory.headerProfileName(headerProfile)}, " +
                        "uaVersion=${BitwardenApiFactory.headerProfileUserAgentVersion(headerProfile)}, " +
                        "refererApplied=${BitwardenApiFactory.isRefererApplied(headerProfile, urls.vault)}"
            )
            val identityApi = apiManager.getIdentityApi(
                identityUrl = urls.identity,
                refererUrl = urls.vault,
                headerProfile = headerProfile,
                tlsConfig = tlsConfig
            )
            
            // Auth-Email header - 使用原始邮箱，不是小写！
            val authEmail = toBase64UrlNoPadding(twoFactorState.email)
            
            val response = identityApi.loginTwoFactor(
                authEmail = authEmail,
                username = twoFactorState.email,
                passwordHash = twoFactorState.passwordHash,
                captchaResponse = normalizedCaptcha,
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
                logDiag(
                    flow = "two_factor",
                    attemptId = attemptId,
                    stage = "result_success",
                    message =
                        "code=${response.code()}, hasRefresh=${!body.refreshToken.isNullOrBlank()}, " +
                            "latencyMs=${System.currentTimeMillis() - startMs}"
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
                val errorResponse = parseTokenError(errorBody)
                if (isCaptchaRequired(errorResponse, errorBody)) {
                    val message = errorResponse?.errorDescription
                        ?: errorResponse?.errorModel?.message
                        ?: "需要验证码，请输入 Captcha response 后重试"
                    logDiag(
                        flow = "two_factor",
                        attemptId = attemptId,
                        stage = "result_captcha_required",
                        message =
                            "code=${response.code()}, error=${errorResponse?.error}, desc=${errorResponse?.errorDescription}, " +
                                "hasSiteKey=${!errorResponse?.hCaptchaSiteKey.isNullOrBlank()}, latencyMs=${System.currentTimeMillis() - startMs}, " +
                                "body=${oneLine(errorBody, ERROR_BODY_SNIPPET_LIMIT)}"
                    )
                    return@withContext Result.success(
                        LoginResult.CaptchaRequired(
                            message = message,
                            siteKey = errorResponse?.hCaptchaSiteKey
                        )
                    )
                }
                logDiag(
                    flow = "two_factor",
                    attemptId = attemptId,
                    stage = "result_error",
                    message =
                        "code=${response.code()}, error=${errorResponse?.error}, desc=${errorResponse?.errorDescription}, " +
                            "latencyMs=${System.currentTimeMillis() - startMs}, body=${oneLine(errorBody, ERROR_BODY_SNIPPET_LIMIT)}"
                )
                Result.failure(
                    Exception("Two-factor login failed [attemptId=$attemptId]: ${response.code()} - $errorBody")
                )
            }
        } catch (e: Exception) {
            logDiag(
                flow = "two_factor",
                attemptId = attemptId,
                stage = "exception",
                message =
                    "type=${e.javaClass.simpleName}, msg=${oneLine(e.message, 140)}, " +
                        "latencyMs=${System.currentTimeMillis() - startMs}"
            )
            Result.failure(e)
        }
    }

    /**
     * 新设备验证登录 (Email New Device OTP)
     */
    suspend fun loginNewDeviceOtp(
        twoFactorState: LoginResult.TwoFactorRequired,
        newDeviceOtp: String,
        serverUrl: String = BitwardenApiFactory.OFFICIAL_VAULT_URL,
        captchaResponse: String? = null,
        tlsConfig: BitwardenTlsConfig? = null
    ): Result<LoginResult> = withContext(Dispatchers.IO) {
        val attemptId = twoFactorState.diagnosticAttemptId ?: newAttemptId()
        val startMs = System.currentTimeMillis()
        try {
            val urls = BitwardenApiFactory.inferServerUrls(serverUrl)
            val normalizedCaptcha = captchaResponse?.trim()?.takeIf { it.isNotBlank() }
            val headerProfile = twoFactorState.authHeaderProfile
            logDiag(
                flow = "new_device",
                attemptId = attemptId,
                stage = "start",
                message =
                    "otpLen=${newDeviceOtp.trim().length}, serverClass=${classifyServer(urls.vault)}, " +
                        "identity=${urls.identity}, captchaProvided=${!normalizedCaptcha.isNullOrBlank()}, " +
                        "headerProfile=${BitwardenApiFactory.headerProfileName(headerProfile)}, " +
                        "uaVersion=${BitwardenApiFactory.headerProfileUserAgentVersion(headerProfile)}, " +
                        "refererApplied=${BitwardenApiFactory.isRefererApplied(headerProfile, urls.vault)}"
            )
            val identityApi = apiManager.getIdentityApi(
                identityUrl = urls.identity,
                refererUrl = urls.vault,
                headerProfile = headerProfile,
                tlsConfig = tlsConfig
            )

            val authEmail = toBase64UrlNoPadding(twoFactorState.email)

            val response = identityApi.loginNewDeviceOtp(
                authEmail = authEmail,
                username = twoFactorState.email,
                passwordHash = twoFactorState.passwordHash,
                captchaResponse = normalizedCaptcha,
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
                logDiag(
                    flow = "new_device",
                    attemptId = attemptId,
                    stage = "result_success",
                    message =
                        "code=${response.code()}, hasRefresh=${!body.refreshToken.isNullOrBlank()}, " +
                            "latencyMs=${System.currentTimeMillis() - startMs}"
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
                val errorResponse = parseTokenError(errorBody)
                if (isCaptchaRequired(errorResponse, errorBody)) {
                    val message = errorResponse?.errorDescription
                        ?: errorResponse?.errorModel?.message
                        ?: "需要验证码，请输入 Captcha response 后重试"
                    logDiag(
                        flow = "new_device",
                        attemptId = attemptId,
                        stage = "result_captcha_required",
                        message =
                            "code=${response.code()}, error=${errorResponse?.error}, desc=${errorResponse?.errorDescription}, " +
                                "hasSiteKey=${!errorResponse?.hCaptchaSiteKey.isNullOrBlank()}, latencyMs=${System.currentTimeMillis() - startMs}, " +
                                "body=${oneLine(errorBody, ERROR_BODY_SNIPPET_LIMIT)}"
                    )
                    return@withContext Result.success(
                        LoginResult.CaptchaRequired(
                            message = message,
                            siteKey = errorResponse?.hCaptchaSiteKey
                        )
                    )
                }
                Log.e(TAG, "New device login failed: ${response.code()} - $errorBody")
                logDiag(
                    flow = "new_device",
                    attemptId = attemptId,
                    stage = "result_error",
                    message =
                        "code=${response.code()}, error=${errorResponse?.error}, desc=${errorResponse?.errorDescription}, " +
                            "latencyMs=${System.currentTimeMillis() - startMs}, body=${oneLine(errorBody, ERROR_BODY_SNIPPET_LIMIT)}"
                )
                Result.failure(
                    Exception("New device login failed [attemptId=$attemptId]: ${response.code()} - $errorBody")
                )
            }
        } catch (e: Exception) {
            logDiag(
                flow = "new_device",
                attemptId = attemptId,
                stage = "exception",
                message =
                    "type=${e.javaClass.simpleName}, msg=${oneLine(e.message, 140)}, " +
                        "latencyMs=${System.currentTimeMillis() - startMs}"
            )
            Result.failure(e)
        }
    }

    suspend fun sendTwoFactorEmailLogin(
        twoFactorState: LoginResult.TwoFactorRequired,
        serverUrl: String = BitwardenApiFactory.OFFICIAL_VAULT_URL,
        tlsConfig: BitwardenTlsConfig? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val attemptId = twoFactorState.diagnosticAttemptId ?: newAttemptId()
        val startMs = System.currentTimeMillis()
        try {
            val urls = BitwardenApiFactory.inferServerUrls(serverUrl)
            val headerProfile = twoFactorState.authHeaderProfile
            logDiag(
                flow = "two_factor_email",
                attemptId = attemptId,
                stage = "send_start",
                message =
                    "serverClass=${classifyServer(urls.vault)}, api=${urls.api}, " +
                        "emailDomain=${emailDomain(twoFactorState.email)}, " +
                        "headerProfile=${BitwardenApiFactory.headerProfileName(headerProfile)}, " +
                        "uaVersion=${BitwardenApiFactory.headerProfileUserAgentVersion(headerProfile)}, " +
                        "refererApplied=${BitwardenApiFactory.isRefererApplied(headerProfile, urls.vault)}"
            )
            val vaultApi = apiManager.getVaultApi(
                apiUrl = urls.api,
                refererUrl = urls.vault,
                headerProfile = headerProfile,
                tlsConfig = tlsConfig
            )
            val response = vaultApi.sendTwoFactorEmailLogin(
                SendEmailLoginRequest(
                    deviceIdentifier = getDeviceId(),
                    email = twoFactorState.email,
                    masterPasswordHash = twoFactorState.passwordHash
                )
            )
            if (response.isSuccessful) {
                logDiag(
                    flow = "two_factor_email",
                    attemptId = attemptId,
                    stage = "send_success",
                    message = "code=${response.code()}, latencyMs=${System.currentTimeMillis() - startMs}"
                )
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                logDiag(
                    flow = "two_factor_email",
                    attemptId = attemptId,
                    stage = "send_error",
                    message =
                        "code=${response.code()}, message=${response.message()}, " +
                            "latencyMs=${System.currentTimeMillis() - startMs}, " +
                            "body=${oneLine(errorBody, ERROR_BODY_SNIPPET_LIMIT)}"
                )
                Result.failure(
                    Exception("Send email two-factor code failed [attemptId=$attemptId]: ${response.code()} - $errorBody")
                )
            }
        } catch (e: Exception) {
            logDiag(
                flow = "two_factor_email",
                attemptId = attemptId,
                stage = "send_exception",
                message =
                    "type=${e.javaClass.simpleName}, msg=${oneLine(e.message, 140)}, " +
                        "latencyMs=${System.currentTimeMillis() - startMs}"
            )
            Result.failure(e)
        }
    }
    
    /**
     * 刷新访问令牌
     */
    suspend fun refreshToken(
        refreshToken: String,
        identityUrl: String = BitwardenApiFactory.OFFICIAL_IDENTITY_URL,
        refererUrl: String? = null,
        tlsConfig: BitwardenTlsConfig? = null
    ): Result<RefreshResult> = withContext(Dispatchers.IO) {
        try {
            val identityApi = apiManager.getIdentityApi(
                identityUrl = identityUrl,
                refererUrl = refererUrl,
                tlsConfig = tlsConfig
            )
            
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
        val prefs = context.getSharedPreferences("bitwarden_device", Context.MODE_PRIVATE)
        val existing = prefs.getString("device_id", null)
        if (existing != null) return existing
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString("device_id", newId).apply()
        return newId
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
        val modelMessage = errorResponse?.errorModel?.message.orEmpty()
        val errorDescription = errorResponse?.errorDescription.orEmpty()
        return modelMessage.contains("new device verification required", ignoreCase = true) ||
            errorDescription.contains("new device verification required", ignoreCase = true)
    }

    private fun isCaptchaRequired(errorResponse: TokenResponse?, errorBody: String?): Boolean {
        if (!errorResponse?.hCaptchaSiteKey.isNullOrBlank()) return true
        return errorBody?.contains("captcha", ignoreCase = true) == true
    }

    private fun shouldRetryWithKeyguardFallback(
        responseCode: Int,
        errorResponse: TokenResponse?,
        errorBody: String?,
        captchaProvided: Boolean
    ): Boolean {
        if (captchaProvided) return false
        if (responseCode != 400) return false

        val error = errorResponse?.error
        val description = errorResponse?.errorDescription
        val isInvalidGrant = error.equals("invalid_grant", ignoreCase = true)
        val isInvalidCredDescription = description.equals("invalid_username_or_password", ignoreCase = true)
        val isInvalidCredBody = errorBody?.contains("invalid_username_or_password", ignoreCase = true) == true

        return isInvalidGrant && (isInvalidCredDescription || isInvalidCredBody)
    }

    private fun normalizePreLoginResult(
        kdfType: Int,
        kdfIterations: Int,
        kdfMemory: Int?,
        kdfParallelism: Int?,
        diagnosticAttemptId: String?
    ): PreLoginResult {
        val normalized = when (kdfType) {
            BitwardenVault.KDF_TYPE_PBKDF2 -> PreLoginResult(
                kdfType = kdfType,
                kdfIterations = kdfIterations.takePositiveOrDefault(PBKDF2_DEFAULT_ITERATIONS),
                kdfMemory = null,
                kdfParallelism = null
            )

            BitwardenVault.KDF_TYPE_ARGON2ID -> PreLoginResult(
                kdfType = kdfType,
                kdfIterations = kdfIterations.takePositiveOrDefault(ARGON2_DEFAULT_ITERATIONS),
                kdfMemory = kdfMemory.takePositiveOrDefault(ARGON2_DEFAULT_MEMORY_MB),
                kdfParallelism = kdfParallelism.takePositiveOrDefault(ARGON2_DEFAULT_PARALLELISM)
            )

            else -> PreLoginResult(
                kdfType = kdfType,
                kdfIterations = kdfIterations,
                kdfMemory = kdfMemory,
                kdfParallelism = kdfParallelism
            )
        }

        if (
            normalized.kdfIterations != kdfIterations ||
            normalized.kdfMemory != kdfMemory ||
            normalized.kdfParallelism != kdfParallelism
        ) {
            diagnosticAttemptId?.let { attemptId ->
                logDiag(
                    flow = "primary",
                    attemptId = attemptId,
                    stage = "prelogin_normalized",
                    message =
                        "kdf=$kdfType, iter:$kdfIterations->${normalized.kdfIterations}, " +
                            "mem:$kdfMemory->${normalized.kdfMemory}, " +
                            "parallelism:$kdfParallelism->${normalized.kdfParallelism}"
                )
            }
        }

        return normalized
    }

    private fun Int?.takePositiveOrDefault(default: Int): Int {
        return this?.takeIf { it > 0 } ?: default
    }

    private fun newAttemptId(): String = UUID.randomUUID().toString().substring(0, 8)

    private fun classifyServer(vaultUrl: String): String {
        return when {
            BitwardenApiFactory.isOfficialEuServer(vaultUrl) -> "official_eu"
            BitwardenApiFactory.isOfficialServer(vaultUrl) -> "official_us"
            else -> "self_hosted"
        }
    }

    private fun emailDomain(email: String): String {
        val domain = email.substringAfter('@', "unknown")
        return domain.ifBlank { "unknown" }.lowercase(Locale.ENGLISH)
    }

    private fun oneLine(raw: String?, maxLen: Int): String {
        val value = raw?.replace('\n', ' ')?.replace('\r', ' ')?.trim().orEmpty()
        if (value.isEmpty()) return "-"
        return if (value.length <= maxLen) value else value.take(maxLen) + "..."
    }

    private fun logDiag(flow: String, attemptId: String, stage: String, message: String) {
        val line = "$DIAG_PREFIX flow=$flow attempt=$attemptId stage=$stage $message"
        Log.e(TAG, line)
        BitwardenDiagLogger.append("$TAG: $line")
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
        val providersData: Map<String, JsonElement>?,
        val tempMasterKey: ByteArray,
        val tempStretchedKey: SymmetricCryptoKey,
        val email: String,
        val passwordHash: String,
        val kdfType: Int,
        val kdfIterations: Int,
        val kdfMemory: Int?,
        val kdfParallelism: Int?,
        val authHeaderProfile: BitwardenApiFactory.HeaderProfile = BitwardenApiFactory.HeaderProfile.MONICA_DEFAULT,
        val diagnosticAttemptId: String? = null
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
                    BitwardenAuthService.TWO_FACTOR_ORGANIZATION_DUO -> "Organization Duo"
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

    data class CaptchaRequired(
        val message: String,
        val siteKey: String? = null
    ) : LoginResult()
}

data class RefreshResult(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Int
)
