package takagi.ru.monica.passkey

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import takagi.ru.monica.R
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.repository.PasskeyRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.components.MasterPasswordDialog
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.utils.BiometricAuthHelper
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Passkey 认证 Activity
 * 
 * 当用户选择使用 Monica 中的 Passkey 登录时显示
 * 强制要求生物识别验证以确保安全性
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyAuthActivity : FragmentActivity() {
    
    companion object {
        private const val TAG = "PasskeyAuthActivity"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }
    
    private val database: PasswordDatabase by lazy {
        PasswordDatabase.getDatabase(applicationContext)
    }
    
    private val biometricHelper: BiometricAuthHelper by lazy {
        BiometricAuthHelper(this)
    }
    
    private val repository: PasskeyRepository by lazy {
        PasskeyRepository(database.passkeyDao())
    }

    private val showMasterPasswordDialog = mutableStateOf(false)
    private val masterPasswordError = mutableStateOf(false)
    
    private var passkey: PasskeyEntry? = null
    private var pendingRequestJson: String = ""
    private var pendingCallingAppInfo: CallingAppInfo? = null
    private var pendingClientDataHash: ByteArray? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "PasskeyAuthActivity onCreate called")
        Log.d(TAG, "Intent: $intent")
        Log.d(TAG, "Intent extras: ${intent.extras}")
        
        // 首先尝试从 PendingIntentHandler 获取请求（这是正确的方式）
        val providerRequest = try {
            PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve ProviderGetCredentialRequest", e)
            null
        }
        
        if (providerRequest == null) {
            Log.e(TAG, "providerRequest is null - this should not happen!")
            // 如果无法获取 providerRequest，尝试从 intent extras 获取（向后兼容）
        } else {
            Log.d(TAG, "providerRequest retrieved successfully")
            pendingCallingAppInfo = providerRequest.callingAppInfo
            Log.d(TAG, "CallingAppInfo: $pendingCallingAppInfo")
            Log.d(TAG, "CallingAppInfo origin: ${pendingCallingAppInfo?.origin}")
            Log.d(TAG, "CallingAppInfo packageName: ${pendingCallingAppInfo?.packageName}")
            
            // 获取 clientDataHash（如果提供）
            providerRequest.credentialOptions.firstOrNull()?.let { opt ->
                if (opt is GetPublicKeyCredentialOption) {
                    pendingClientDataHash = opt.clientDataHash
                    Log.d(TAG, "clientDataHash: ${pendingClientDataHash?.contentToString()}")
                }
            }
        }
        
        val requestJson = intent.getStringExtra(MonicaCredentialProviderService.EXTRA_REQUEST_JSON) ?: ""
        val credentialId = intent.getStringExtra(MonicaCredentialProviderService.EXTRA_CREDENTIAL_ID) ?: ""
        
        Log.d(TAG, "requestJson from extras: ${requestJson.take(100)}...")
        Log.d(TAG, "credentialId from extras: $credentialId")
        
        // 检查必要参数
        if (credentialId.isBlank()) {
            Log.e(TAG, "credentialId is empty!")
            val resultIntent = Intent()
            PendingIntentHandler.setGetCredentialException(
                resultIntent,
                GetCredentialUnknownException("Missing credential ID")
            )
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
            return
        }
        
        if (requestJson.isBlank()) {
            Log.e(TAG, "requestJson is empty!")
            val resultIntent = Intent()
            PendingIntentHandler.setGetCredentialException(
                resultIntent,
                GetCredentialUnknownException("Missing request JSON")
            )
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
            return
        }
        
        // 加载 Passkey 信息（优先按 credentialId，其次尝试规范化匹配）
        runBlocking {
            passkey = database.passkeyDao().getPasskeyById(credentialId)
            if (passkey == null) {
                val normalizedId = normalizeCredentialId(credentialId)
                if (normalizedId != null) {
                    val all = database.passkeyDao().getAllPasskeysSync()
                    passkey = all.firstOrNull { normalizeCredentialId(it.credentialId) == normalizedId }
                }
            }
        }
        
        val currentPasskey = passkey
        if (currentPasskey == null) {
            Log.e(TAG, "Passkey not found: $credentialId")
            // 必须使用 PendingIntentHandler 设置异常，否则系统会显示 Authentication failed
            val resultIntent = Intent()
            PendingIntentHandler.setGetCredentialException(
                resultIntent,
                GetCredentialUnknownException("Passkey not found")
            )
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
            return
        }
        
        // 保存请求数据用于生物识别验证后使用
        pendingRequestJson = requestJson
        
        setContent {
            MonicaTheme {
                PasskeyAuthScreen(
                    passkey = currentPasskey,
                    onConfirm = {
                        // 触发生物识别验证
                        requestBiometricAuth(currentPasskey)
                    },
                    onCancel = {
                        repository.logAudit("PASSKEY_AUTH_CANCELLED", currentPasskey.credentialId)
                        // 用户取消生物识别时，提供主密码验证回退
                        showMasterPasswordDialog.value = true
                    },
                    onUseMasterPassword = {
                        showMasterPasswordDialog.value = true
                    }
                )

                if (showMasterPasswordDialog.value) {
                    MasterPasswordDialog(
                        onDismiss = {
                            showMasterPasswordDialog.value = false
                            masterPasswordError.value = false
                        },
                        onConfirm = { password ->
                            val securityManager = SecurityManager(this)
                            if (securityManager.verifyMasterPassword(password)) {
                                masterPasswordError.value = false
                                showMasterPasswordDialog.value = false
                                repository.logAudit("PASSKEY_AUTH_MASTER_PASSWORD_SUCCESS", currentPasskey.credentialId)
                                authenticateWithPasskey(pendingRequestJson, currentPasskey)
                            } else {
                                masterPasswordError.value = true
                                repository.logAudit("PASSKEY_AUTH_MASTER_PASSWORD_FAILED", currentPasskey.credentialId)
                            }
                        },
                        isError = masterPasswordError.value
                    )
                }
            }
        }
    }
    
    /**
     * 请求生物识别验证
     * 只有通过生物识别后才能使用 Passkey 进行签名
     */
    private fun requestBiometricAuth(passkey: PasskeyEntry) {
        repository.logAudit("PASSKEY_AUTH_BIOMETRIC_REQUESTED", passkey.credentialId)

        if (!biometricHelper.isBiometricAvailable()) {
            repository.logAudit("PASSKEY_AUTH_BIOMETRIC_UNAVAILABLE", passkey.credentialId)
            showMasterPasswordDialog.value = true
            return
        }
        
        biometricHelper.authenticate(
            activity = this,
            title = getString(R.string.biometric_title_passkey_auth),
            subtitle = getString(R.string.biometric_subtitle_passkey_auth, passkey.rpId),
            negativeButtonText = getString(R.string.cancel),
            onSuccess = {
                repository.logAudit("PASSKEY_AUTH_BIOMETRIC_SUCCESS", passkey.credentialId)
                authenticateWithPasskey(pendingRequestJson, passkey)
            },
            onError = { errorCode, errString ->
                repository.logAudit("PASSKEY_AUTH_BIOMETRIC_FAILED", 
                    "${passkey.credentialId}|error=$errorCode|$errString")
                Log.e(TAG, "Biometric auth failed: $errorCode - $errString")
                // 生物识别失败时，提供主密码验证回退
                showMasterPasswordDialog.value = true
            },
            onCancel = {
                repository.logAudit("PASSKEY_AUTH_BIOMETRIC_CANCELLED", passkey.credentialId)
                Log.d(TAG, "Biometric auth cancelled by user")
                // 用户取消时，提供主密码验证回退
                showMasterPasswordDialog.value = true
            }
        )
    }
    
    private fun authenticateWithPasskey(
        requestJson: String,
        passkey: PasskeyEntry
    ) {
        try {
            val json = JSONObject(requestJson)
            
            // 解析 challenge
            val challengeB64 = json.optString("challenge")
            val challenge = Base64.decode(challengeB64, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            
            val rpId = json.optString("rpId", passkey.rpId)
            
            // 更新签名计数
            val newSignCount = passkey.signCount + 1L
            
            // 创建 authenticator data
            val authenticatorData = createAuthenticatorData(rpId, newSignCount.toInt())
            
            // 计算签名
            // 如果调用方提供了 clientDataHash，直接使用它（调用方自己构建了 clientDataJSON）
            // 否则需要自己构建 clientDataJSON 并计算哈希
            val clientDataJsonBytes: ByteArray?
            val clientDataHash: ByteArray
            
            if (pendingClientDataHash != null) {
                // 调用方已提供 clientDataHash，不需要构建 clientDataJSON
                clientDataJsonBytes = null
                clientDataHash = pendingClientDataHash!!
                Log.d(TAG, "Using provided clientDataHash")
            } else {
                // 需要自己构建 clientDataJSON
                val origin = resolveOrigin(requestJson, pendingCallingAppInfo)
                val androidPackageName = pendingCallingAppInfo?.packageName
                val clientDataJson = createClientDataJson(
                    type = "webauthn.get",
                    challenge = challengeB64,
                    origin = origin,
                    androidPackageName = androidPackageName
                )
                clientDataJsonBytes = clientDataJson.toByteArray()
                clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientDataJsonBytes)
                Log.d(TAG, "Built clientDataJSON with origin: $origin")
            }
            
            val signedData = authenticatorData + clientDataHash
            val signature = signWithPrivateKey(passkey.privateKeyAlias, signedData)
            
            // 更新数据库
            runBlocking {
                database.passkeyDao().updateUsage(
                    credentialId = passkey.credentialId,
                    timestamp = System.currentTimeMillis(),
                    signCount = newSignCount
                )
            }
            
            // 构建响应 JSON - 参照 KeePassDX 实现
            // 当调用方提供 clientDataHash 时，返回 placeholder 而不是省略 clientDataJSON
            val clientDataJsonB64 = if (clientDataJsonBytes != null) {
                Base64.encodeToString(
                    clientDataJsonBytes,
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                )
            } else {
                // 参照 KeePassDX ClientDataDefinedResponse: 使用 placeholder
                "<placeholder>"
            }
            
            val responseJson = JSONObject().apply {
                put("id", passkey.credentialId)
                put("rawId", passkey.credentialId)
                put("type", "public-key")
                put("authenticatorAttachment", "platform")
                put("response", JSONObject().apply {
                    put("clientDataJSON", clientDataJsonB64)
                    put("authenticatorData", Base64.encodeToString(
                        authenticatorData,
                        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                    ))
                    put("signature", Base64.encodeToString(
                        signature,
                        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                    ))
                    put("userHandle", passkey.userId)
                })
                put("clientExtensionResults", JSONObject())
            }
            
            Log.d(TAG, "Authentication successful for: ${passkey.credentialId}")
            repository.logAudit("PASSKEY_AUTH_SUCCESS", 
                "${passkey.credentialId}|rpId=${passkey.rpId}|signCount=$newSignCount")
            
            // 返回结果
            val credentialResponse = PublicKeyCredential(responseJson.toString())
            val resultIntent = Intent()
            PendingIntentHandler.setGetCredentialResponse(
                resultIntent, 
                GetCredentialResponse(credentialResponse)
            )
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to authenticate with passkey", e)
            repository.logAudit("PASSKEY_AUTH_ERROR", 
                "${passkey.credentialId}|error=${e.message}")
            val resultIntent = Intent()
            PendingIntentHandler.setGetCredentialException(
                resultIntent,
                GetCredentialUnknownException(e.message)
            )
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
    
    private fun createAuthenticatorData(rpId: String, signCount: Int): ByteArray {
        val rpIdHash = MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray())
        
        // 参照 KeePassDX AuthenticatorData.buildAuthenticatorData
        // Flags: UP (0x01) | UV (0x04) | BE (0x08) | BS (0x10) = 0x1D
        // UP = User Present
        // UV = User Verified  
        // BE = Backup Eligibility
        // BS = Backup State
        var flags = 0x01 // UP
        flags = flags or 0x04 // UV
        flags = flags or 0x08 // BE - Backup Eligibility
        flags = flags or 0x10 // BS - Backup State
        
        val signCountBytes = ByteArray(4)
        signCountBytes[0] = ((signCount shr 24) and 0xFF).toByte()
        signCountBytes[1] = ((signCount shr 16) and 0xFF).toByte()
        signCountBytes[2] = ((signCount shr 8) and 0xFF).toByte()
        signCountBytes[3] = (signCount and 0xFF).toByte()
        
        return rpIdHash + byteArrayOf(flags.toByte()) + signCountBytes
    }

    private fun normalizeCredentialId(credentialId: String): String? {
        return try {
            val decoded = Base64.decode(
                credentialId,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            Base64.encodeToString(
                decoded,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        } catch (e: Exception) {
            try {
                val decoded = Base64.decode(credentialId, Base64.DEFAULT)
                Base64.encodeToString(
                    decoded,
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                )
            } catch (ignored: Exception) {
                null
            }
        }
    }
    
    /**
     * 使用存储的私钥签名数据
     * @param privateKeyData Base64编码的私钥，或者AndroidKeyStore别名（向后兼容）
     */
    private fun signWithPrivateKey(privateKeyData: String, data: ByteArray): ByteArray {
        val privateKey: java.security.PrivateKey = try {
            // 尝试作为Base64私钥解码
            val keyBytes = Base64.decode(privateKeyData, Base64.NO_WRAP)
            val keySpec = java.security.spec.PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = java.security.KeyFactory.getInstance("EC")
            keyFactory.generatePrivate(keySpec)
        } catch (e: Exception) {
            // 回退到AndroidKeyStore（向后兼容旧数据）
            Log.d(TAG, "Falling back to AndroidKeyStore for key: $privateKeyData")
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            
            val privateKeyEntry = keyStore.getEntry(privateKeyData, null) as? KeyStore.PrivateKeyEntry
                ?: throw IllegalStateException("Private key not found: $privateKeyData")
            privateKeyEntry.privateKey
        }
        
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }
    
    private fun createClientDataJson(
        type: String,
        challenge: String,
        origin: String,
        androidPackageName: String? = null
    ): String {
        return JSONObject().apply {
            put("type", type)
            put("challenge", challenge)
            put("origin", origin)
            if (!androidPackageName.isNullOrBlank()) {
                put("androidPackageName", androidPackageName)
            }
            put("crossOrigin", false)
        }.toString()
    }

    private fun resolveOrigin(requestJson: String, callingAppInfo: CallingAppInfo?): String {
        return try {
            val json = JSONObject(requestJson)
            val originFromRequest = json.optString("origin")
            if (originFromRequest.isNotBlank()) {
                originFromRequest
            } else {
                getOriginFromCallingAppInfo(callingAppInfo)
                    ?: "android:apk-key-hash:${getAppSigningHash()}"
            }
        } catch (e: Exception) {
            getOriginFromCallingAppInfo(callingAppInfo)
                ?: "android:apk-key-hash:${getAppSigningHash()}"
        }
    }

    private fun getOriginFromCallingAppInfo(callingAppInfo: CallingAppInfo?): String? {
        if (callingAppInfo == null) return null
        return try {
            val origin = callingAppInfo.origin
            if (!origin.isNullOrBlank()) {
                origin
            } else {
                val hash = getCallingAppSigningHash(callingAppInfo)
                if (hash.isNullOrBlank()) null else "android:apk-key-hash:$hash"
            }
        } catch (e: Exception) {
            val hash = getCallingAppSigningHash(callingAppInfo)
            if (hash.isNullOrBlank()) null else "android:apk-key-hash:$hash"
        }
    }

    private fun getCallingAppSigningHash(callingAppInfo: CallingAppInfo): String? {
        return try {
            val signatures = callingAppInfo.signingInfo.apkContentsSigners
            if (signatures.isNotEmpty()) {
                val certFactory = CertificateFactory.getInstance("X509")
                val cert = certFactory.generateCertificate(
                    signatures[0].toByteArray().inputStream()
                ) as X509Certificate
                val md = MessageDigest.getInstance("SHA-256")
                val hash = md.digest(cert.encoded)
                Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get calling app signing hash", e)
            null
        }
    }
    
    private fun getAppSigningHash(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(
                packageName,
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signatures = packageInfo.signingInfo?.apkContentsSigners ?: return ""
            if (signatures.isNotEmpty()) {
                val md = MessageDigest.getInstance("SHA-256")
                val hash = md.digest(signatures[0].toByteArray())
                Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            } else ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get app signing hash", e)
            ""
        }
    }
}

/**
 * 更新签名计数
 */
private suspend fun takagi.ru.monica.data.PasskeyDao.updateSignCount(credentialId: String, signCount: Int) {
    // 这需要在 DAO 中添加对应方法
    // 暂时通过更新整个条目实现
}

@Composable
private fun PasskeyAuthScreen(
    passkey: PasskeyEntry,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onUseMasterPassword: () -> Unit
) {
    var isAuthenticating by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.primary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = stringResource(R.string.passkey_auth_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.passkey_auth_message, passkey.rpName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 账户信息卡片
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // 网站信息
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = passkey.rpName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = passkey.rpId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 用户信息
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = passkey.userDisplayName.ifBlank { passkey.userName },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (passkey.userName != passkey.userDisplayName && passkey.userDisplayName.isNotBlank()) {
                                Text(
                                    text = passkey.userName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 使用次数
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.passkey_use_count, passkey.useCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 按钮
            Button(
                onClick = {
                    isAuthenticating = true
                    onConfirm()
                },
                enabled = !isAuthenticating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isAuthenticating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Login,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.passkey_auth_confirm))
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedButton(
                onClick = onCancel,
                enabled = !isAuthenticating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.cancel))
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onUseMasterPassword,
                enabled = !isAuthenticating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.use_master_password))
            }
        }
    }
}
