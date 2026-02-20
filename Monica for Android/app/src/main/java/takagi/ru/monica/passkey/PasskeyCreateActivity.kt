package takagi.ru.monica.passkey

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import takagi.ru.monica.R
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.data.model.PasskeyBinding
import takagi.ru.monica.data.model.PasskeyBindingCodec
import takagi.ru.monica.repository.PasskeyRepository
import takagi.ru.monica.ui.components.StorageTargetSelectorCard
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.utils.BiometricAuthHelper
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Passkey 创建确认 Activity
 * 
 * 当用户在网站/应用中选择使用 Monica 创建 Passkey 时显示
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyCreateActivity : FragmentActivity() {
    
    companion object {
        private const val TAG = "PasskeyCreateActivity"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        
        // Monica AAGUID - 使用自定义 UUID: 6d6f6e69-6361-7061-7373-6b6579617070
        // "monicapasskeyapp" 的十六进制表示
        val MONICA_AAGUID = byteArrayOf(
            0x6d.toByte(), 0x6f.toByte(), 0x6e.toByte(), 0x69.toByte(),
            0x63.toByte(), 0x61.toByte(), 0x70.toByte(), 0x61.toByte(),
            0x73.toByte(), 0x73.toByte(), 0x6b.toByte(), 0x65.toByte(),
            0x79.toByte(), 0x61.toByte(), 0x70.toByte(), 0x70.toByte()
        )
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
    
    // 存储待处理的请求数据
    private var pendingRequestJson: String = ""
    private var pendingRpId: String = ""
    private var pendingUserName: String = ""
    private var pendingUserDisplayName: String = ""
    private var pendingCallingAppInfo: CallingAppInfo? = null
    private var pendingClientDataHash: ByteArray? = null
    private var pendingBoundPasswordId: Long? = null
    private var pendingCategoryId: Long? = null
    private var pendingKeepassDatabaseId: Long? = null
    private var pendingBitwardenVaultId: Long? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "PasskeyCreateActivity onCreate called")
        Log.d(TAG, "Intent: $intent")
        Log.d(TAG, "Intent extras: ${intent.extras}")
        
        // 首先尝试从 PendingIntentHandler 获取请求（这是正确的方式）
        val providerRequest = try {
            PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve ProviderCreateCredentialRequest", e)
            null
        }
        
        if (providerRequest == null) {
            Log.e(TAG, "providerRequest is null - this should not happen!")
        } else {
            Log.d(TAG, "providerRequest retrieved successfully")
            pendingCallingAppInfo = providerRequest.callingAppInfo
            Log.d(TAG, "CallingAppInfo: $pendingCallingAppInfo")
            Log.d(TAG, "CallingAppInfo origin: ${pendingCallingAppInfo?.origin}")
            Log.d(TAG, "CallingAppInfo packageName: ${pendingCallingAppInfo?.packageName}")
            
            val callingRequest = providerRequest.callingRequest
            if (callingRequest is CreatePublicKeyCredentialRequest) {
                pendingClientDataHash = callingRequest.clientDataHash
                Log.d(TAG, "clientDataHash: ${pendingClientDataHash?.contentToString()}")
            }
        }
        
        val requestJson = intent.getStringExtra(MonicaCredentialProviderService.EXTRA_REQUEST_JSON) ?: ""
        val rpId = intent.getStringExtra(MonicaCredentialProviderService.EXTRA_RP_ID) ?: ""
        val userName = intent.getStringExtra(MonicaCredentialProviderService.EXTRA_USER_NAME) ?: ""
        val userDisplayName = intent.getStringExtra(MonicaCredentialProviderService.EXTRA_USER_DISPLAY_NAME) ?: userName
        
        Log.d(TAG, "requestJson from extras: ${requestJson.take(100)}...")
        Log.d(TAG, "rpId: $rpId, userName: $userName")
        
        // 检查必要参数
        if (requestJson.isBlank()) {
            Log.e(TAG, "requestJson is empty!")
            val resultIntent = Intent()
            PendingIntentHandler.setCreateCredentialException(
                resultIntent,
                CreateCredentialUnknownException("Missing request JSON")
            )
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
            return
        }
        
        if (rpId.isBlank()) {
            Log.e(TAG, "rpId is empty!")
            val resultIntent = Intent()
            PendingIntentHandler.setCreateCredentialException(
                resultIntent,
                CreateCredentialUnknownException("Missing RP ID")
            )
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
            return
        }
        
        Log.d(TAG, "Creating passkey for $rpId / $userName")
        
        // 保存待处理的数据
        pendingRequestJson = requestJson
        pendingRpId = rpId
        pendingUserName = userName
        pendingUserDisplayName = userDisplayName
        
        setContent {
            MonicaTheme {
                var showPasswordPicker by remember { mutableStateOf(false) }
                var showCategoryPicker by remember { mutableStateOf(false) }
                val passwords by database.passwordEntryDao().getAllPasswordEntries()
                    .collectAsState(initial = emptyList())
                val categories by database.categoryDao().getAllCategories()
                    .collectAsState(initial = emptyList())
                val keepassDatabases by database.localKeePassDatabaseDao().getAllDatabases()
                    .collectAsState(initial = emptyList())
                var selectedCategoryId by remember { mutableStateOf<Long?>(pendingCategoryId) }
                var selectedKeePassDatabaseId by remember { mutableStateOf<Long?>(pendingKeepassDatabaseId) }
                var selectedBitwardenVaultId by remember { mutableStateOf<Long?>(pendingBitwardenVaultId) }
                val context = LocalContext.current
                val bitwardenRepository = remember { BitwardenRepository.getInstance(context) }
                var bitwardenVaults by remember { mutableStateOf<List<BitwardenVault>>(emptyList()) }
                val selectedCategoryName = remember(selectedCategoryId, categories) {
                    selectedCategoryId?.let { id -> categories.find { it.id == id }?.name }
                }
                LaunchedEffect(Unit) {
                    bitwardenVaults = bitwardenRepository.getAllVaults()
                }

                PasskeyCreateScreen(
                    rpId = rpId,
                    rpName = parseRpName(requestJson, rpId),
                    userName = userName,
                    userDisplayName = userDisplayName,
                    keepassDatabases = keepassDatabases,
                    selectedKeePassDatabaseId = selectedKeePassDatabaseId,
                    onKeePassDatabaseSelected = {
                        selectedKeePassDatabaseId = it
                        if (it != null) selectedBitwardenVaultId = null
                    },
                    bitwardenVaults = bitwardenVaults,
                    selectedBitwardenVaultId = selectedBitwardenVaultId,
                    onBitwardenVaultSelected = {
                        selectedBitwardenVaultId = it
                        if (it != null) selectedKeePassDatabaseId = null
                    },
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    onCategorySelected = { selectedCategoryId = it },
                    selectedCategoryName = selectedCategoryName,
                    onCreateDirect = {
                        pendingBoundPasswordId = null
                        pendingCategoryId = selectedCategoryId
                        pendingKeepassDatabaseId = selectedKeePassDatabaseId
                        pendingBitwardenVaultId = selectedBitwardenVaultId
                        // 触发生物识别验证
                        requestBiometricAuth()
                    },
                    onBindToPassword = {
                        showPasswordPicker = true
                    },
                    onPickCategory = {
                        showCategoryPicker = true
                    },
                    onCancel = {
                        repository.logAudit("PASSKEY_CREATE_CANCELLED", rpId)
                        // 用户取消，使用 PendingIntentHandler 设置取消异常
                        val resultIntent = Intent()
                        PendingIntentHandler.setCreateCredentialException(
                            resultIntent,
                            CreateCredentialCancellationException("User cancelled")
                        )
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    }
                )

                if (showPasswordPicker) {
                    PasswordPickerDialog(
                        passwords = passwords,
                        onDismiss = {
                            pendingBoundPasswordId = null
                            showPasswordPicker = false
                        },
                        onPasswordSelected = { password ->
                            pendingBoundPasswordId = password.id
                            selectedCategoryId = password.categoryId
                            selectedKeePassDatabaseId = password.keepassDatabaseId
                            selectedBitwardenVaultId = if (password.keepassDatabaseId != null) {
                                null
                            } else {
                                password.bitwardenVaultId
                            }
                            pendingCategoryId = selectedCategoryId
                            pendingKeepassDatabaseId = selectedKeePassDatabaseId
                            pendingBitwardenVaultId = selectedBitwardenVaultId
                            showPasswordPicker = false
                            requestBiometricAuth()
                        }
                    )
                }

                if (showCategoryPicker) {
                    CategoryPickerDialog(
                        categories = categories,
                        selectedCategoryId = selectedCategoryId,
                        onDismiss = { showCategoryPicker = false },
                        onCategorySelected = { categoryId ->
                            selectedCategoryId = categoryId
                            pendingCategoryId = categoryId
                            showCategoryPicker = false
                        }
                    )
                }
            }
        }
    }
    
    /**
     * 请求生物识别验证
     * 只有通过生物识别后才能创建 Passkey
     */
    private fun requestBiometricAuth() {
        repository.logAudit("PASSKEY_CREATE_BIOMETRIC_REQUESTED", pendingRpId)
        
        biometricHelper.authenticate(
            activity = this,
            title = getString(R.string.biometric_title_passkey_create),
            subtitle = getString(R.string.biometric_subtitle_passkey_create, pendingRpId),
            negativeButtonText = getString(R.string.cancel),
            onSuccess = {
                repository.logAudit("PASSKEY_CREATE_BIOMETRIC_SUCCESS", pendingRpId)
                createPasskey(pendingRequestJson, pendingRpId, pendingUserName, pendingUserDisplayName)
            },
            onError = { errorCode, errString ->
                repository.logAudit("PASSKEY_CREATE_BIOMETRIC_FAILED", 
                    "$pendingRpId|error=$errorCode|$errString")
                Log.e(TAG, "Biometric auth failed: $errorCode - $errString")
                // 生物识别失败，必须使用 PendingIntentHandler 设置异常响应
                val resultIntent = Intent()
                PendingIntentHandler.setCreateCredentialException(
                    resultIntent,
                    CreateCredentialUnknownException("Biometric authentication failed: $errString")
                )
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            },
            onCancel = {
                repository.logAudit("PASSKEY_CREATE_BIOMETRIC_CANCELLED", pendingRpId)
                Log.d(TAG, "Biometric auth cancelled by user")
                // 用户取消，使用 PendingIntentHandler 设置取消异常
                val resultIntent = Intent()
                PendingIntentHandler.setCreateCredentialException(
                    resultIntent,
                    CreateCredentialCancellationException("User cancelled")
                )
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        )
    }
    
    private fun parseRpName(requestJson: String, defaultRpId: String): String {
        return try {
            val json = JSONObject(requestJson)
            val rpJson = json.optJSONObject("rp")
            rpJson?.optString("name") ?: defaultRpId
        } catch (e: Exception) {
            defaultRpId
        }
    }
    
    private fun createPasskey(
        requestJson: String,
        rpId: String,
        userName: String,
        userDisplayName: String
    ) {
        try {
            val json = JSONObject(requestJson)
            
            // 解析用户 ID
            val userJson = json.optJSONObject("user")
            val userIdB64 = userJson?.optString("id") ?: ""
            
            // 解析 challenge
            val challengeB64 = json.optString("challenge")
            
            // 解析算法偏好
            val pubKeyCredParams = json.optJSONArray("pubKeyCredParams")
            val algorithm = getPreferredAlgorithm(pubKeyCredParams)
            
            // 生成凭据 ID
            val credentialId = generateCredentialId()
            val credentialIdB64 = Base64.encodeToString(
                credentialId, 
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            
            // 生成密钥对（使用普通 EC 密钥生成器，不使用 AndroidKeyStore）
            val keyPair = generateEcKeyPair()
            
            // 创建 COSE 公钥（直接从 KeyPair 提取坐标）
            val cosePublicKey = createCosePublicKeyFromKeyPair(keyPair)
            
            // 创建 attestation object
            val authenticatorData = createAuthenticatorData(
                rpId = rpId,
                credentialId = credentialId,
                cosePublicKey = cosePublicKey,
                signCount = 0
            )
            
            // 创建 attestation object (使用 "none" attestation)
            val attestationObject = createAttestationObject(authenticatorData)
            
            // 构建 clientDataJSON
            val clientDataJsonValue: String
            if (pendingClientDataHash != null) {
                // 调用方已提供 clientDataHash，按照平台约定返回占位符
                clientDataJsonValue = "<placeholder>"
                Log.d(TAG, "Using placeholder clientDataJSON (clientDataHash provided)")
            } else {
                val origin = resolveOrigin(requestJson, pendingCallingAppInfo)
                val androidPackageName = pendingCallingAppInfo?.packageName
                val clientDataJson = createClientDataJson(
                    type = "webauthn.create",
                    challenge = challengeB64,
                    origin = origin,
                    androidPackageName = androidPackageName
                )
                val clientDataJsonBytes = clientDataJson.toByteArray()
                clientDataJsonValue = Base64.encodeToString(
                    clientDataJsonBytes,
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                )
                Log.d(TAG, "Built clientDataJSON with origin: $origin")
            }
            
            // 将私钥编码为 Base64 存储
            val privateKeyB64 = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
            val publicKeyB64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)

            val discoverable = parseDiscoverable(requestJson)
            
            // 保存到数据库
            val passkeyEntry = PasskeyEntry(
                credentialId = credentialIdB64,
                rpId = rpId,
                rpName = parseRpName(requestJson, rpId),
                userId = userIdB64,
                userName = userName,
                userDisplayName = userDisplayName,
                publicKey = publicKeyB64,
                privateKeyAlias = privateKeyB64,  // 存储私钥而不是 keyAlias
                publicKeyAlgorithm = algorithm,
                createdAt = System.currentTimeMillis(),
                lastUsedAt = System.currentTimeMillis(),
                useCount = 0,
                isDiscoverable = discoverable,
                signCount = 0L,
                boundPasswordId = pendingBoundPasswordId,
                categoryId = pendingCategoryId,
                keepassDatabaseId = pendingKeepassDatabaseId,
                bitwardenVaultId = pendingBitwardenVaultId,
                syncStatus = if (pendingBitwardenVaultId != null) "PENDING" else "NONE"
            )
            
            // 在协程中保存
            kotlinx.coroutines.runBlocking {
                database.passkeyDao().insert(passkeyEntry)

                // 同步写入密码条目的通行密钥绑定（用于备份/恢复）
                val boundPasswordId = pendingBoundPasswordId
                if (boundPasswordId != null) {
                    val passwordDao = database.passwordEntryDao()
                    val passwordEntry = passwordDao.getPasswordEntryById(boundPasswordId)
                    if (passwordEntry != null) {
                        val binding = PasskeyBinding(
                            credentialId = credentialIdB64,
                            rpId = rpId,
                            rpName = parseRpName(requestJson, rpId),
                            userName = userName,
                            userDisplayName = userDisplayName
                        )
                        val updatedBindings = PasskeyBindingCodec.addBinding(passwordEntry.passkeyBindings, binding)
                        passwordDao.updatePasskeyBindings(boundPasswordId, updatedBindings)
                    }
                }
            }
            
            // 构建响应 JSON
            val responseJson = JSONObject().apply {
                put("id", credentialIdB64)
                put("rawId", credentialIdB64)
                put("type", "public-key")
                put("authenticatorAttachment", "platform")
                put("response", JSONObject().apply {
                    put("clientDataJSON", clientDataJsonValue)
                    put("attestationObject", Base64.encodeToString(
                        attestationObject, 
                        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                    ))
                    put("publicKeyAlgorithm", algorithm)
                    put("publicKey", Base64.encodeToString(
                        keyPair.public.encoded,
                        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                    ))
                    put("authenticatorData", Base64.encodeToString(
                        authenticatorData,
                        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                    ))
                    put("transports", JSONArray().apply {
                        put("internal")
                        put("hybrid")
                    })
                })
                put("clientExtensionResults", JSONObject())
            }
            
            Log.d(TAG, "Passkey created successfully: $credentialIdB64")
            repository.logAudit("PASSKEY_CREATE_SUCCESS", 
                "$credentialIdB64|rpId=$rpId|userName=$userName")
            
            // 返回结果
            val credentialResponse = CreatePublicKeyCredentialResponse(responseJson.toString())
            val resultIntent = Intent()
            PendingIntentHandler.setCreateCredentialResponse(resultIntent, credentialResponse)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create passkey", e)
            repository.logAudit("PASSKEY_CREATE_ERROR", 
                "rpId=$rpId|error=${e.message}")
            val resultIntent = Intent()
            PendingIntentHandler.setCreateCredentialException(
                resultIntent,
                CreateCredentialUnknownException(e.message)
            )
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
    
    private fun getPreferredAlgorithm(pubKeyCredParams: JSONArray?): Int {
        // 默认使用 ES256 (-7)
        if (pubKeyCredParams == null) return -7
        
        for (i in 0 until pubKeyCredParams.length()) {
            val param = pubKeyCredParams.optJSONObject(i)
            val alg = param?.optInt("alg", 0) ?: 0
            // 只支持 ES256 (-7)
            if (alg == -7) {
                return alg
            }
        }
        return -7 // 默认 ES256
    }
    
    private fun generateCredentialId(): ByteArray {
        val uuid = UUID.randomUUID()
        val bytes = ByteArray(16)
        val msb = uuid.mostSignificantBits
        val lsb = uuid.leastSignificantBits
        for (i in 0..7) {
            bytes[i] = ((msb shr (8 * (7 - i))) and 0xFF).toByte()
            bytes[8 + i] = ((lsb shr (8 * (7 - i))) and 0xFF).toByte()
        }
        return bytes
    }
    
    /**
     * 生成 EC 密钥对（使用普通密钥生成器，不使用 AndroidKeyStore）
     * 这样可以正确提取公钥的 X/Y 坐标用于 COSE 编码
     */
    private fun generateEcKeyPair(): java.security.KeyPair {
        val generator = KeyPairGenerator.getInstance("EC").apply {
            val spec = ECGenParameterSpec("secp256r1")
            initialize(spec)
        }
        return generator.genKeyPair()
    }
    
    /**
     * 从 KeyPair 创建 COSE 公钥
     * 参考 RFC 9052 Section 7 和 Keyguard 实现
     */
    private fun createCosePublicKeyFromKeyPair(keyPair: java.security.KeyPair): ByteArray {
        val ecPubKey = keyPair.public as java.security.interfaces.ECPublicKey
        val ecPoint = ecPubKey.w
        
        // 验证坐标长度
        if (ecPoint.affineX.bitLength() > 256 || ecPoint.affineY.bitLength() > 256) {
            throw IllegalStateException("EC point coordinates exceed 256 bits")
        }
        
        val byteX = bigIntToByteArray32(ecPoint.affineX)
        val byteY = bigIntToByteArray32(ecPoint.affineY)
        
        // CBOR 编码的 COSE_Key (ES256):
        // A5 01 02 03 26 20 01 21 58 20 <x> 22 58 20 <y>
        // 参考: https://www.iana.org/assignments/cose/cose.xhtml
        val header = "A5010203262001215820".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val yHeader = "225820".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        
        return header + byteX + yHeader + byteY
    }
    
    private fun bigIntToByteArray32(bigInteger: java.math.BigInteger): ByteArray {
        var ba = bigInteger.toByteArray()
        // 如果长度不足 32，前面补零
        if (ba.size < 32) {
            val padded = ByteArray(32)
            System.arraycopy(ba, 0, padded, 32 - ba.size, ba.size)
            ba = padded
        }
        // 如果长度超过 32（由于符号位），取最后 32 字节
        return ba.copyOfRange(ba.size - 32, ba.size)
    }
    
    private fun createAuthenticatorData(
        rpId: String,
        credentialId: ByteArray,
        cosePublicKey: ByteArray,
        signCount: Int
    ): ByteArray {
        val rpIdHash = MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray())
        
        // 参照 KeePassDX AuthenticatorData.buildAuthenticatorData
        // Flags: UP (0x01) | UV (0x04) | BE (0x08) | BS (0x10) | AT (0x40)
        // UP = User Present
        // UV = User Verified  
        // BE = Backup Eligibility
        // BS = Backup State
        // AT = Attested Credential Data included
        var flags = 0x01 // UP
        flags = flags or 0x04 // UV
        flags = flags or 0x08 // BE - Backup Eligibility
        flags = flags or 0x10 // BS - Backup State
        flags = flags or 0x40 // AT - Attested Credential Data
        
        val signCountBytes = ByteArray(4)
        signCountBytes[0] = ((signCount shr 24) and 0xFF).toByte()
        signCountBytes[1] = ((signCount shr 16) and 0xFF).toByte()
        signCountBytes[2] = ((signCount shr 8) and 0xFF).toByte()
        signCountBytes[3] = (signCount and 0xFF).toByte()
        
        // Monica AAGUID
        val aaguid = MONICA_AAGUID
        
        // Credential ID length (2 bytes big-endian)
        val credIdLen = ByteArray(2)
        credIdLen[0] = ((credentialId.size shr 8) and 0xFF).toByte()
        credIdLen[1] = (credentialId.size and 0xFF).toByte()
        
        return rpIdHash + byteArrayOf(flags.toByte()) + signCountBytes + aaguid + credIdLen + credentialId + cosePublicKey
    }
    
    private fun createAttestationObject(authenticatorData: ByteArray): ByteArray {
        // 手动 CBOR 编码 attestation object (none attestation)
        // 结构: { "fmt": "none", "attStmt": {}, "authData": <bytes> }
        // 
        // CBOR 编码:
        // A3                           # map(3)
        //    63                        # text(3)
        //       666D74                 # "fmt"
        //    64                        # text(4)
        //       6E6F6E65               # "none"
        //    67                        # text(7)
        //       61747453746D74         # "attStmt"
        //    A0                        # map(0) - empty map
        //    68                        # text(8)
        //       6175746844617461       # "authData"
        //    58/59 XX                  # bytes(XX)
        //       <authenticatorData>
        
        val fmtKey = byteArrayOf(0x63.toByte(), 0x66, 0x6D, 0x74) // text(3) "fmt"
        val fmtValue = byteArrayOf(0x64.toByte(), 0x6E, 0x6F, 0x6E, 0x65) // text(4) "none"
        val attStmtKey = byteArrayOf(0x67.toByte(), 0x61, 0x74, 0x74, 0x53, 0x74, 0x6D, 0x74) // text(7) "attStmt"
        val attStmtValue = byteArrayOf(0xA0.toByte()) // map(0) - empty
        val authDataKey = byteArrayOf(0x68.toByte(), 0x61, 0x75, 0x74, 0x68, 0x44, 0x61, 0x74, 0x61) // text(8) "authData"
        
        // CBOR bytes header: 0x58 = 1-byte length, 0x59 = 2-byte length
        val authDataHeader = if (authenticatorData.size < 256) {
            byteArrayOf(0x58.toByte(), authenticatorData.size.toByte())
        } else {
            byteArrayOf(
                0x59.toByte(),
                ((authenticatorData.size shr 8) and 0xFF).toByte(),
                (authenticatorData.size and 0xFF).toByte()
            )
        }
        
        return byteArrayOf(0xA3.toByte()) + // map(3)
            fmtKey + fmtValue +
            attStmtKey + attStmtValue +
            authDataKey + authDataHeader + authenticatorData
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

    private fun parseDiscoverable(requestJson: String): Boolean {
        return try {
            val json = JSONObject(requestJson)
            val authSel = json.optJSONObject("authenticatorSelection")
            val requireResidentKey = authSel?.optBoolean("requireResidentKey", false) ?: false
            val residentKey = authSel?.optString("residentKey", "discouraged") ?: "discouraged"
            requireResidentKey || residentKey == "required" || residentKey == "preferred"
        } catch (e: Exception) {
            false
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

@Composable
private fun PasskeyCreateScreen(
    rpId: String,
    rpName: String,
    userName: String,
    userDisplayName: String,
    keepassDatabases: List<LocalKeePassDatabase>,
    selectedKeePassDatabaseId: Long?,
    onKeePassDatabaseSelected: (Long?) -> Unit,
    bitwardenVaults: List<BitwardenVault>,
    selectedBitwardenVaultId: Long?,
    onBitwardenVaultSelected: (Long?) -> Unit,
    categories: List<Category>,
    selectedCategoryId: Long?,
    onCategorySelected: (Long?) -> Unit,
    selectedCategoryName: String?,
    onCreateDirect: () -> Unit,
    onBindToPassword: () -> Unit,
    onPickCategory: () -> Unit,
    onCancel: () -> Unit
) {
    var isCreating by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = stringResource(R.string.passkey_create_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.passkey_create_message, rpName),
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
                                text = rpName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = rpId,
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
                                text = userDisplayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (userName != userDisplayName) {
                                Text(
                                    text = userName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            StorageTargetSelectorCard(
                keepassDatabases = keepassDatabases,
                selectedKeePassDatabaseId = selectedKeePassDatabaseId,
                onKeePassDatabaseSelected = onKeePassDatabaseSelected,
                bitwardenVaults = bitwardenVaults,
                selectedBitwardenVaultId = selectedBitwardenVaultId,
                onBitwardenVaultSelected = onBitwardenVaultSelected,
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                onCategorySelected = onCategorySelected
            )

            OutlinedButton(
                onClick = onPickCategory,
                enabled = !isCreating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = selectedCategoryName?.let { "文件夹: $it" } ?: "选择文件夹（可选）"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            // 绑定密码按钮（先选择密码）
            Button(
                onClick = {
                    onBindToPassword()
                },
                enabled = !isCreating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.bind_password))
            }
            
            Spacer(modifier = Modifier.height(12.dp))
 
            // 直接创建按钮（不绑定）
            OutlinedButton(
                onClick = {
                    isCreating = true
                    onCreateDirect()
                },
                enabled = !isCreating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.passkey_create_confirm))
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedButton(
                onClick = onCancel,
                enabled = !isCreating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun CategoryPickerDialog(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onDismiss: () -> Unit,
    onCategorySelected: (Long?) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "选择文件夹",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    item {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.category_none)) },
                            leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                            trailingContent = {
                                if (selectedCategoryId == null) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                            modifier = Modifier
                                .clickable { onCategorySelected(null) }
                                .fillMaxWidth()
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    items(categories) { category ->
                        ListItem(
                            headlineContent = { Text(category.name) },
                            leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                            trailingContent = {
                                if (selectedCategoryId == category.id) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                            modifier = Modifier
                                .clickable { onCategorySelected(category.id) }
                                .fillMaxWidth()
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun PasswordPickerDialog(
    passwords: List<PasswordEntry>,
    onDismiss: () -> Unit,
    onPasswordSelected: (PasswordEntry) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredPasswords = remember(passwords, searchQuery) {
        if (searchQuery.isBlank()) {
            passwords
        } else {
            passwords.filter { entry ->
                entry.title.contains(searchQuery, ignoreCase = true) ||
                    entry.username.contains(searchQuery, ignoreCase = true) ||
                    entry.website.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.select_password_to_bind),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(filteredPasswords) { password ->
                        ListItem(
                            headlineContent = { Text(password.title) },
                            supportingContent = {
                                val parts = listOf(password.username, password.website).filter { it.isNotBlank() }
                                if (parts.isNotEmpty()) {
                                    Text(parts.joinToString(" · "))
                                }
                            },
                            leadingContent = {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = password.title.firstOrNull()?.toString()?.uppercase() ?: "?",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .clickable { onPasswordSelected(password) }
                                .fillMaxWidth()
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}
