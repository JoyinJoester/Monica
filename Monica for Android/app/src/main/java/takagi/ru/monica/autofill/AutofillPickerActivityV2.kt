package takagi.ru.monica.autofill

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.Parcelize
import takagi.ru.monica.R
import takagi.ru.monica.autofill.core.AutofillLogger
import takagi.ru.monica.autofill.ui.*
import takagi.ru.monica.autofill.utils.SmartCopyNotificationHelper
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.theme.MonicaTheme
import androidx.compose.foundation.isSystemInDarkTheme
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.ui.components.PasswordVerificationContent
import takagi.ru.monica.ui.base.BaseMonicaActivity
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.util.TotpGenerator
import takagi.ru.monica.util.TotpUriParser

/**
 * Keyguard 风格的全屏自动填充选择器
 * 
 * 特点:
 * - 全屏界面（非 BottomSheet）
 * - 顶部显示捕获的表单数据
 * - 支持搜索和筛选
 * - 支持新建密码
 * - Dropdown 菜单选择操作
 */
class AutofillPickerActivityV2 : BaseMonicaActivity() {
    
    companion object {
        private const val EXTRA_ARGS = "extra_args"
        private const val DUPLICATE_LAUNCH_WINDOW_MS = 1500L
        private const val AUTOFILL_OTP_NOTIFICATION_ID = 12001
        @Volatile
        private var lastLaunchSignature: String? = null
        @Volatile
        private var lastLaunchAtMs: Long = 0L
        
        /**
         * 创建启动 Intent（契约保持不变，确保 Service 兼容）
         */
        fun getIntent(context: Context, args: Args): Intent {
            return Intent(context, AutofillPickerActivityV2::class.java).apply {
                putExtra(EXTRA_ARGS, args)
            }
        }
        
        /**
         * 创建测试 Intent（用于开发者调试入口）
         */
        fun getTestIntent(context: Context): Intent {
            val testArgs = Args(
                applicationId = "com.test.autofill",
                webDomain = "example.com",
                capturedUsername = "test_user",
                capturedPassword = null,
                isSaveMode = false
            )
            return getIntent(context, testArgs)
        }

        private fun buildLaunchSignature(args: Args): String {
            val idCount = args.autofillIds?.size ?: 0
            val hintCount = args.autofillHints?.size ?: 0
            val suggestedCount = args.suggestedPasswordIds?.size ?: 0
            return buildString {
                append(args.applicationId.orEmpty())
                append('|')
                append(args.webDomain.orEmpty())
                append('|')
                append(args.interactionIdentifier.orEmpty())
                append('|')
                append(args.fieldSignatureKey.orEmpty())
                append('|')
                append(idCount)
                append('|')
                append(hintCount)
                append('|')
                append(suggestedCount)
                append('|')
                append(args.responseAuthMode)
                append('|')
                append(args.isSaveMode)
            }
        }

        @Synchronized
        private fun shouldSuppressDuplicateLaunch(args: Args): Boolean {
            val now = System.currentTimeMillis()
            val signature = buildLaunchSignature(args)
            val isDuplicate = lastLaunchSignature == signature &&
                now - lastLaunchAtMs <= DUPLICATE_LAUNCH_WINDOW_MS
            lastLaunchSignature = signature
            lastLaunchAtMs = now
            return isDuplicate
        }
    }
    
    @Parcelize
    data class Args(
        val applicationId: String? = null,
        val webDomain: String? = null,
        val webScheme: String? = null,
        val interactionIdentifier: String? = null,
        val interactionIdentifierAliases: ArrayList<String>? = null,
        val capturedUsername: String? = null,
        val capturedPassword: String? = null,
        val autofillIds: ArrayList<AutofillId>? = null,
        val autofillHints: ArrayList<String>? = null,
        val suggestedPasswordIds: LongArray? = null,
        val isSaveMode: Boolean = false,
        val fieldSignatureKey: String? = null,
        val responseAuthMode: Boolean = false
    ) : Parcelable {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Args
            return applicationId == other.applicationId &&
                   webDomain == other.webDomain &&
                   isSaveMode == other.isSaveMode
        }
        override fun hashCode(): Int {
            var result = applicationId?.hashCode() ?: 0
            result = 31 * result + (webDomain?.hashCode() ?: 0)
            result = 31 * result + isSaveMode.hashCode()
            return result
        }
    }
    
    private val args by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_ARGS, Args::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_ARGS)
        } ?: Args()
    }
    
    // 手动模式：从磁贴启动，没有 AutofillId，选择密码后复制而不是回填
    private val isManualMode by lazy {
        intent.getBooleanExtra("extra_manual_mode", false) || args.autofillIds.isNullOrEmpty()
    }
    

    
    // attachBaseContext 已由 BaseMonicaActivity 统一处理
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // BaseMonicaActivity 已调用 enableEdgeToEdge()
        AutofillLogger.initialize(applicationContext)
        if (shouldSuppressDuplicateLaunch(args)) {
            AutofillLogger.w("PICKER", "Suppress duplicate picker launch within ${DUPLICATE_LAUNCH_WINDOW_MS}ms")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        AutofillLogger.i(
            "PICKER",
            "Picker opened: saveMode=${args.isSaveMode}, responseAuth=${args.responseAuthMode}, ids=${args.autofillIds?.size ?: 0}, hints=${args.autofillHints?.size ?: 0}"
        )
        
        val database = PasswordDatabase.getDatabase(applicationContext)
        val repository = PasswordRepository(database.passwordEntryDao())
        val localKeePassDao = database.localKeePassDatabaseDao()
        val securityManager = SecurityManager(applicationContext)
        // settingsManager 已由 BaseMonicaActivity 初始化
        val localSettingsManager = settingsManager
        
        // 检查是否可以跳过验证（基于会话管理器的安全窗规则）
        val canSkipAuth = canSkipVerification()
        
        setContent {
            // 读取截图保护设置（截图保护已由 BaseMonicaActivity 统一处理）
            val settings by localSettingsManager.settingsFlow.collectAsState(
                initial = takagi.ru.monica.data.AppSettings()
            )
            
            // 获取 KeePass 数据库列表
            val keepassDatabases by localKeePassDao.getAllDatabases().collectAsState(initial = emptyList())
            
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            MonicaTheme(
                darkTheme = darkTheme,
                colorScheme = settings.colorScheme,
                customPrimaryColor = settings.customPrimaryColor,
                customSecondaryColor = settings.customSecondaryColor,
                customTertiaryColor = settings.customTertiaryColor,
                customNeutralColor = settings.customNeutralColor,
                customNeutralVariantColor = settings.customNeutralVariantColor
            ) {
                AutofillPickerContent(
                    args = args,
                    repository = repository,
                    securityManager = securityManager,
                    keepassDatabases = keepassDatabases,
                    canSkipVerification = canSkipAuth,
                    biometricEnabled = settings.biometricEnabled,
                    iconCardsEnabled = settings.iconCardsEnabled,
                    isManualMode = isManualMode,
                    onAuthenticationSuccess = { markAuthenticationSuccess() },
                    onAutofill = { password, forceAddUri ->
                        handleAutofill(password, forceAddUri)
                    },
                    onSaveNewPassword = { entry ->
                        repository.insertPasswordEntry(entry)
                    },
                    onCopy = ::copyToClipboard,
                    onClose = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                    onSmartCopy = { password, usernameFirst ->
                        handleSmartCopy(password, usernameFirst)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AutofillLogger.w("PICKER", "onNewIntent received while picker is active; reusing current instance")
    }
    
    private fun handleSmartCopy(password: PasswordEntry, usernameFirst: Boolean) {
        val securityManager = SecurityManager(applicationContext)
        val accountValue = AccountFillPolicy.resolveAccountIdentifier(password, securityManager)
        
        val decryptedPassword = try {
            securityManager.decryptData(password.password)
        } catch (e: Exception) {
            password.password
        }
        
        if (usernameFirst) {
            // Copy username first, queue password for notification
            SmartCopyNotificationHelper.copyAndQueueNext(
                context = this,
                firstValue = accountValue,
                firstLabel = getString(R.string.autofill_username),
                secondValue = decryptedPassword,
                secondLabel = getString(R.string.autofill_password)
            )
            android.widget.Toast.makeText(this, R.string.username_copied, android.widget.Toast.LENGTH_SHORT).show()
        } else {
            // Copy password first, queue username for notification
            SmartCopyNotificationHelper.copyAndQueueNext(
                context = this,
                firstValue = decryptedPassword,
                firstLabel = getString(R.string.autofill_password),
                secondValue = accountValue,
                secondLabel = getString(R.string.autofill_username)
            )
            android.widget.Toast.makeText(this, R.string.password_copied, android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // Close the picker
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
        

    
    private fun handleAutofill(password: PasswordEntry, forceAddUri: Boolean) {
        val securityManager = SecurityManager(applicationContext)
        val accountValue = AccountFillPolicy.resolveAccountIdentifier(password, securityManager)
        val fillEmailWithAccount = AccountFillPolicy.shouldFillEmailWithAccount(applicationContext)
        
        val decryptedPassword = try {
            securityManager.decryptData(password.password)
        } catch (e: Exception) {
            password.password
        }
        
        // 手动模式：复制密码到剪贴板
        if (isManualMode) {
            // 使用智能复制：先复制密码，通知栏提供用户名
            SmartCopyNotificationHelper.copyAndQueueNext(
                context = this,
                firstValue = decryptedPassword,
                firstLabel = getString(R.string.autofill_password),
                secondValue = accountValue,
                secondLabel = getString(R.string.autofill_username)
            )
            android.widget.Toast.makeText(this, R.string.password_copied, android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // 正常自动填充模式：构建 Dataset
        val autofillIds = args.autofillIds
        if (autofillIds.isNullOrEmpty()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val cardTitle = password.title.ifBlank {
            args.webDomain?.takeIf { it.isNotBlank() }
                ?: args.applicationId?.takeIf { it.isNotBlank() }
                ?: getString(R.string.tile_autofill_label)
        }
        val cardSubtitle = accountValue
        val selectedPresentation = RemoteViews(packageName, R.layout.autofill_last_filled_card_compact).apply {
            setTextViewText(R.id.text_title, cardTitle)
            setTextViewText(R.id.text_username, cardSubtitle)
            setImageViewResource(R.id.icon_app, R.drawable.ic_passkey)
        }
        android.util.Log.d(
            "AutofillPickerV2",
            "Using distinct selectedPresentation for auth result dataset"
        )
        val datasetBuilder = Dataset.Builder(selectedPresentation)

        val hints = args.autofillHints
        val hasUsernameHint = hints?.contains(EnhancedAutofillStructureParserV2.FieldHint.USERNAME.name) == true
        val hasPhoneHint = hints?.contains(EnhancedAutofillStructureParserV2.FieldHint.PHONE_NUMBER.name) == true
        val hasEmailHint = hints?.contains(EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS.name) == true
        val hasAccountHint = hasUsernameHint || hasPhoneHint
        val allowAccountInEmailField =
            fillEmailWithAccount || accountValue.contains("@") || (!hasAccountHint && hasEmailHint)
        var filledCount = 0
        autofillIds.forEachIndexed { index, autofillId ->
            val hint = hints?.getOrNull(index)
            val value = when (hint) {
                EnhancedAutofillStructureParserV2.FieldHint.USERNAME.name -> accountValue
                EnhancedAutofillStructureParserV2.FieldHint.PHONE_NUMBER.name -> accountValue
                EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS.name ->
                    if (allowAccountInEmailField) accountValue else null
                EnhancedAutofillStructureParserV2.FieldHint.PASSWORD.name,
                EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD.name -> decryptedPassword
                else -> null
            }
            if (value != null) {
                datasetBuilder.setValue(autofillId, AutofillValue.forText(value), selectedPresentation)
                filledCount++
            }
        }

        if (filledCount == 0) {
            android.util.Log.w("AutofillPickerV2", "No strict hint matched, trying controlled fallback")
            autofillIds.forEachIndexed { index, autofillId ->
                val normalizedHint = hints?.getOrNull(index)?.lowercase().orEmpty()
                val fallbackValue = when {
                    normalizedHint.contains("pass") -> decryptedPassword
                    normalizedHint.contains("user") ||
                        normalizedHint.contains("email") ||
                        normalizedHint.contains("phone") ||
                        normalizedHint.contains("mobile") ||
                        normalizedHint.contains("tel") ||
                        normalizedHint.contains("号码") ||
                        normalizedHint.contains("手机号") ||
                        normalizedHint.contains("account") ||
                        normalizedHint.contains("login") -> accountValue
                    autofillIds.size == 1 -> if (accountValue.isNotBlank()) accountValue else decryptedPassword
                    index == 0 -> accountValue
                    index == 1 -> decryptedPassword
                    else -> null
                }
                if (!fallbackValue.isNullOrBlank()) {
                    datasetBuilder.setValue(autofillId, AutofillValue.forText(fallbackValue), selectedPresentation)
                    filledCount++
                }
            }
        }

        if (filledCount == 0) {
            android.util.Log.w("AutofillPickerV2", "No credential value resolved after controlled fallback")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        android.util.Log.i(
            "AutofillPickerV2",
            "Autofill auth result prepared: filledCount=$filledCount, ids=${autofillIds.size}, " +
                "hints=${hints?.joinToString(",") ?: "none"}, passwordId=${password.id}"
        )
        AutofillLogger.i(
            "PICKER",
            "Auth result prepared: filled=$filledCount, ids=${autofillIds.size}, passwordId=${password.id}, hints=${hints?.joinToString(",") ?: "none"}"
        )
        
        val dataset = datasetBuilder.build()
        val authResult: Parcelable = if (args.responseAuthMode) {
            FillResponse.Builder().addDataset(dataset).build()
        } else {
            dataset
        }
        val resultIntent = Intent().apply {
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, authResult)
        }
        
        // 处理 URI 绑定
        if (forceAddUri) {
            saveUriBinding(password)
        }

        rememberLastFilledCredential(password.id)
        rememberLearnedFieldSignature()
        processSelectedOtpActions(password)
        
        android.util.Log.i(
            "AutofillPickerV2",
            "Returning authentication result to framework: mode=${if (args.responseAuthMode) "fill_response" else "dataset"}"
        )
        AutofillLogger.i(
            "PICKER",
            "Returning auth result: mode=${if (args.responseAuthMode) "fill_response" else "dataset"}, passwordId=${password.id}"
        )
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun processSelectedOtpActions(password: PasswordEntry) {
        val authenticatorKey = password.authenticatorKey.trim()
        if (authenticatorKey.isBlank()) return

        val isOtpTarget = args.autofillHints
            ?.contains(EnhancedAutofillStructureParserV2.FieldHint.OTP_CODE.name) == true
        if (isOtpTarget) {
            AutofillLogger.d("OTP", "Skip OTP auto action for OTP-target fill request")
            return
        }

        runCatching {
            val preferences = AutofillPreferences(applicationContext)
            val showNotification = runBlocking(Dispatchers.IO) {
                preferences.isOtpNotificationEnabled.first()
            }
            val autoCopy = runBlocking(Dispatchers.IO) {
                preferences.isAutoCopyOtpEnabled.first()
            }
            if (!showNotification && !autoCopy) return

            val passwordTotpData = parsePasswordAuthenticatorTotpData(authenticatorKey)
            val totpData = resolveOtpFromExistingValidators(password, passwordTotpData)
            if (totpData == null) {
                AutofillLogger.w(
                    "OTP",
                    "Skip OTP notify/copy: no matching validator entry found for passwordId=${password.id}"
                )
                return
            }
            AutofillLogger.i(
                "OTP",
                "Resolved OTP source: passwordId=${password.id}, otpType=${totpData.otpType}, secretLen=${totpData.secret.length}, boundPasswordId=${totpData.boundPasswordId}"
            )
            val resolvedTotpData = resolveTotpDataForGeneration(totpData)
            val code = TotpGenerator.generateOtp(resolvedTotpData)
            AutofillLogger.i(
                "OTP",
                "Selected OTP generated: passwordId=${password.id}, type=${resolvedTotpData.otpType}, codeLen=${code.length}"
            )
            if (autoCopy) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("OTP Code", code))
                AutofillLogger.d("OTP", "Auto-copied selected credential OTP")
            }
            if (showNotification) {
                val durationSeconds = runBlocking(Dispatchers.IO) {
                    preferences.otpNotificationDuration.first()
                }
                showSelectedOtpNotification(code = code, label = password.title, durationSeconds = durationSeconds)
            }
        }.onFailure { e ->
            AutofillLogger.e("OTP", "Failed selected OTP action", e)
        }
    }

    private fun showSelectedOtpNotification(code: String, label: String, durationSeconds: Int) {
        val channelId = "autofill_otp"
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.autofill_otp_notification_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows 2FA codes during autofill"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val copyIntent = Intent(this, AutofillNotificationReceiver::class.java).apply {
            action = AutofillNotificationReceiver.ACTION_COPY_OTP
            putExtra(AutofillNotificationReceiver.EXTRA_OTP_CODE, code)
            putExtra("notification_id", AUTOFILL_OTP_NOTIFICATION_ID)
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val copyPendingIntent = PendingIntent.getBroadcast(this, 0, copyIntent, pendingIntentFlags)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val timeoutMs = durationSeconds.coerceAtLeast(1) * 1000L
        val notification = builder
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Code: $code")
            .setContentText(label)
            .setAutoCancel(true)
            .setTimeoutAfter(timeoutMs)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.autofill_otp_copy_action, code),
                    copyPendingIntent
                ).build()
            )
            .build()

        notificationManager.notify(AUTOFILL_OTP_NOTIFICATION_ID, notification)
    }

    private fun parsePasswordAuthenticatorTotpData(authenticatorKey: String): TotpData? {
        val normalized = authenticatorKey.trim()
        if (normalized.isBlank()) return null
        return if (normalized.contains("://")) {
            TotpUriParser.parseUri(normalized)?.totpData
        } else {
            TotpData(secret = normalized)
        }
    }

    private fun resolveOtpFromExistingValidators(
        password: PasswordEntry,
        passwordTotpData: TotpData?
    ): TotpData? {
        val validatorTotpList = runBlocking(Dispatchers.IO) {
            val dao = PasswordDatabase.getDatabase(applicationContext).secureItemDao()
            dao.getActiveItemsByTypeSync(ItemType.TOTP)
                .mapNotNull { item ->
                    runCatching {
                        Json { ignoreUnknownKeys = true }.decodeFromString(TotpData.serializer(), item.itemData)
                    }.getOrNull()
                }
        }

        if (validatorTotpList.isEmpty()) return null

        validatorTotpList.firstOrNull { it.boundPasswordId == password.id }?.let { return it }

        val normalizedSecret = normalizeOtpSecret(passwordTotpData?.secret)
        if (normalizedSecret.isNotEmpty()) {
            validatorTotpList.firstOrNull { normalizeOtpSecret(it.secret) == normalizedSecret }?.let { return it }
        }

        return null
    }

    private fun normalizeOtpSecret(secret: String?): String {
        return secret
            ?.trim()
            ?.replace(" ", "")
            ?.replace("-", "")
            ?.uppercase()
            .orEmpty()
    }

    private fun resolveTotpDataForGeneration(totpData: TotpData): TotpData {
        val securityManager = SecurityManager(applicationContext)
        val decryptResult = runCatching { securityManager.decryptData(totpData.secret) }
        val decryptedSecret = decryptResult.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        AutofillLogger.i(
            "OTP",
            "OTP secret resolve: otpType=${totpData.otpType}, rawLen=${totpData.secret.length}, decryptSuccess=${decryptResult.isSuccess && !decryptedSecret.isNullOrEmpty()}, resolvedLen=${decryptedSecret?.length ?: totpData.secret.length}"
        )
        return if (!decryptedSecret.isNullOrEmpty()) {
            totpData.copy(secret = decryptedSecret)
        } else {
            totpData
        }
    }

    private fun rememberLastFilledCredential(passwordId: Long) {
        val primaryIdentifier = args.interactionIdentifier
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: args.interactionIdentifierAliases
                ?.asSequence()
                ?.map { it.trim().lowercase() }
                ?.firstOrNull { it.isNotBlank() }
            ?: return
        try {
            android.util.Log.i(
                "AutofillPickerV2",
                "Persisting last-filled credential: passwordId=$passwordId, primaryId=$primaryIdentifier"
            )
            AutofillLogger.i(
                "PICKER",
                "Persisting last-filled credential: passwordId=$passwordId, primaryId=$primaryIdentifier"
            )
            runBlocking(Dispatchers.IO) {
                val preferences = AutofillPreferences(applicationContext)
                preferences.completeAutofillInteraction(primaryIdentifier, passwordId)
            }
        } catch (e: Exception) {
            android.util.Log.e("AutofillPickerV2", "Failed to persist last filled credential", e)
            AutofillLogger.e("PICKER", "Failed to persist last-filled credential", e)
        }
    }

    private fun rememberLearnedFieldSignature() {
        val signatureKey = args.fieldSignatureKey?.trim()?.lowercase().orEmpty()
        if (signatureKey.isBlank()) return
        try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                AutofillPreferences(applicationContext).markFieldSignatureLearned(signatureKey)
            }
        } catch (e: Exception) {
            android.util.Log.e("AutofillPickerV2", "Failed to persist learned field signature", e)
        }
    }
    
    private fun saveUriBinding(password: PasswordEntry) {
        // TODO: 保存 URI 绑定到数据库
        val applicationId = args.applicationId
        val webDomain = args.webDomain
        
        android.util.Log.d("AutofillPickerV2", "Saving URI binding: app=$applicationId, web=$webDomain for password=${password.id}")
        
        // 后台保存
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val database = PasswordDatabase.getDatabase(applicationContext)
                val repository = PasswordRepository(database.passwordEntryDao())
                
                val updatedEntry = password.copy(
                    appPackageName = applicationId ?: password.appPackageName,
                    website = if (!webDomain.isNullOrEmpty() && !password.website.contains(webDomain)) {
                        if (password.website.isNotEmpty()) "${password.website}, $webDomain"
                        else webDomain
                    } else password.website
                )
                
                repository.updatePasswordEntry(updatedEntry)
                android.util.Log.d("AutofillPickerV2", "URI binding saved successfully")
            } catch (e: Exception) {
                android.util.Log.e("AutofillPickerV2", "Failed to save URI binding", e)
            }
        }
    }


    /**
     * 复制内容到剪贴板
     */
    private fun copyToClipboard(label: String, text: String, isSensitive: Boolean = false) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText(label, text).apply {
                if (isSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    description.extras = android.os.PersistableBundle().apply {
                        putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                }
            }
            clipboard.setPrimaryClip(clip)
            
            val messageRes = if (isSensitive) R.string.password_copied else R.string.username_copied
            android.widget.Toast.makeText(this, messageRes, android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("AutofillPickerV2", "Clipboard copy failed", e)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutofillPickerContent(
    args: AutofillPickerActivityV2.Args,
    repository: PasswordRepository,
    securityManager: SecurityManager,
    keepassDatabases: List<LocalKeePassDatabase>,
    canSkipVerification: Boolean = false,
    biometricEnabled: Boolean = false,
    iconCardsEnabled: Boolean = false,
    isManualMode: Boolean = false,
    onAuthenticationSuccess: () -> Unit = {},
    onAutofill: (PasswordEntry, Boolean) -> Unit,
    onSaveNewPassword: suspend (PasswordEntry) -> Long,
    onCopy: (String, String, Boolean) -> Unit,
    onClose: () -> Unit,
    onSmartCopy: (PasswordEntry, Boolean) -> Unit
) {
    // 导航状态: "list", "detail", "add"
    var currentScreen by remember { mutableStateOf("list") }
    var selectedPassword by remember { mutableStateOf<PasswordEntry?>(null) }
    
    var allPasswords by remember { mutableStateOf<List<PasswordEntry>>(emptyList()) }
    var suggestedPasswords by remember { mutableStateOf<List<PasswordEntry>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var sourceFilter by remember { mutableStateOf(AutofillStorageSourceFilter.ALL) }
    var selectedKeePassDatabaseId by remember { mutableStateOf<Long?>(null) }
    var selectedKeePassGroupPath by remember { mutableStateOf<String?>(null) }
    var selectedVaultId by remember { mutableStateOf<Long?>(null) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var isFilterExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var bitwardenVaults by remember { mutableStateOf<List<BitwardenVault>>(emptyList()) }
    var foldersByVault by remember { mutableStateOf<Map<Long, List<BitwardenFolder>>>(emptyMap()) }
    val coroutineScope = rememberCoroutineScope()
    
    // 检查通知权限 - 用于决定是否显示智能复制选项
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasNotificationPermission = remember {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    
    val autofillUsernameLabel = stringResource(R.string.autofill_username)
    val autofillPasswordLabel = stringResource(R.string.autofill_password)
    
    // 验证状态：如果可以跳过验证（会话有效），直接标记为已认证
    var isAuthenticated by remember { mutableStateOf(canSkipVerification) }
    
    // 如果会话有效，通知外部
    LaunchedEffect(canSkipVerification) {
        if (canSkipVerification) {
            onAuthenticationSuccess()
        }
    }
    
    // 显示验证界面（完全复用 PasswordVerificationContent，与主程序一致）
    if (!isAuthenticated) {
        Surface(modifier = Modifier.fillMaxSize()) {
            PasswordVerificationContent(
                isFirstTime = false,
                disablePasswordVerification = false, // 自动填充始终需要验证
                biometricEnabled = biometricEnabled,
                onVerifyPassword = { input -> securityManager.verifyMasterPassword(input) },
                onSuccess = { 
                    isAuthenticated = true
                    onAuthenticationSuccess()
                }
            )
        }
        return
    }
    
    val (appIcon, appName) = remember(args.applicationId) {
        args.applicationId?.let { packageName ->
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val icon = pm.getApplicationIcon(appInfo)
                val name = pm.getApplicationLabel(appInfo).toString()
                icon to name
            } catch (e: Exception) {
                null to null
            }
        } ?: (null to null)
    }
    
    val bitwardenRepository = remember(context) { BitwardenRepository.getInstance(context) }

    // 加载密码
    LaunchedEffect(Unit) {
        try {
            // 筛选建议密码
            val suggestedIds = args.suggestedPasswordIds?.toList() ?: emptyList()
            if (suggestedIds.isNotEmpty()) {
                suggestedPasswords = repository.getPasswordsByIds(suggestedIds)
            }
            // 先展示推荐项，随后异步补齐全量列表，降低首屏等待感
            isLoading = false
            allPasswords = repository.getAllPasswordEntries().first()
        } catch (e: Exception) {
            android.util.Log.e("AutofillPickerV2", "Error loading passwords", e)
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        bitwardenVaults = withContext(Dispatchers.IO) {
            bitwardenRepository.getAllVaults()
        }
    }

    LaunchedEffect(bitwardenVaults) {
        foldersByVault = withContext(Dispatchers.IO) {
            bitwardenVaults.associate { vault ->
                vault.id to bitwardenRepository.getFolders(vault.id)
            }
        }
    }

    val keepassNameById = remember(keepassDatabases) {
        keepassDatabases.associate { it.id to it.name }
    }
    val vaultLabelById = remember(bitwardenVaults) {
        bitwardenVaults.associate { vault ->
            vault.id to (vault.displayName?.takeIf { it.isNotBlank() } ?: vault.email)
        }
    }
    val selectedVaultFolders = remember(selectedVaultId, foldersByVault) {
        selectedVaultId?.let { foldersByVault[it] }.orEmpty()
    }
    val folderNameById = remember(selectedVaultFolders) {
        selectedVaultFolders.associate { it.bitwardenFolderId to it.name }
    }
    val keepassGroupsForSelectedDb = remember(allPasswords, selectedKeePassDatabaseId) {
        allPasswords
            .asSequence()
            .filter { it.keepassDatabaseId == selectedKeePassDatabaseId }
            .mapNotNull { it.keepassGroupPath?.trim()?.takeIf { path -> path.isNotBlank() } }
            .distinct()
            .sorted()
            .toList()
    }
    val hasUncategorizedKeepassEntries = remember(allPasswords, selectedKeePassDatabaseId) {
        allPasswords.any { entry ->
            entry.keepassDatabaseId == selectedKeePassDatabaseId && entry.keepassGroupPath.isNullOrBlank()
        }
    }
    val hasUncategorizedBitwardenEntries = remember(allPasswords, selectedVaultId) {
        selectedVaultId != null && allPasswords.any { entry ->
            entry.bitwardenVaultId == selectedVaultId && entry.bitwardenFolderId.isNullOrBlank()
        }
    }

    val sourceFilteredPasswords by remember(
        allPasswords,
        sourceFilter,
        selectedKeePassDatabaseId,
        selectedKeePassGroupPath,
        selectedVaultId,
        selectedFolderId
    ) {
        derivedStateOf {
            allPasswords.filter { entry ->
                when (sourceFilter) {
                    AutofillStorageSourceFilter.ALL -> true
                    AutofillStorageSourceFilter.LOCAL ->
                        entry.keepassDatabaseId == null && entry.bitwardenVaultId == null
                    AutofillStorageSourceFilter.KEEPASS -> {
                        val keepassId = entry.keepassDatabaseId
                        keepassId != null &&
                            (selectedKeePassDatabaseId == null || keepassId == selectedKeePassDatabaseId) &&
                            when (selectedKeePassGroupPath) {
                                null -> true
                                KEEPASS_GROUP_UNCATEGORIZED -> entry.keepassGroupPath.isNullOrBlank()
                                else -> entry.keepassGroupPath == selectedKeePassGroupPath
                            }
                    }
                    AutofillStorageSourceFilter.BITWARDEN -> {
                        val vaultId = entry.bitwardenVaultId
                        vaultId != null &&
                            (selectedVaultId == null || vaultId == selectedVaultId) &&
                            when (selectedFolderId) {
                                null -> true
                                BITWARDEN_FOLDER_UNCATEGORIZED -> entry.bitwardenFolderId.isNullOrBlank()
                                else -> entry.bitwardenFolderId == selectedFolderId
                            }
                    }
                }
            }
        }
    }

    val filteredPasswords by remember(sourceFilteredPasswords, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                sourceFilteredPasswords
            } else {
                sourceFilteredPasswords.filter { it.matchesSearchQuery(searchQuery) }
            }
        }
    }

    val filteredPasswordIds = remember(filteredPasswords) { filteredPasswords.map { it.id }.toHashSet() }
    val filteredSuggestedPasswords by remember(suggestedPasswords, filteredPasswordIds) {
        derivedStateOf {
            suggestedPasswords.filter { it.id in filteredPasswordIds }
        }
    }

    val filterSummary = when (sourceFilter) {
        AutofillStorageSourceFilter.ALL -> stringResource(R.string.filter_all)
        AutofillStorageSourceFilter.LOCAL -> stringResource(R.string.filter_local_only)
        AutofillStorageSourceFilter.KEEPASS -> {
            val databaseLabel = selectedKeePassDatabaseId?.let { keepassNameById[it] }
                ?: stringResource(R.string.password_picker_all_databases)
            val groupLabel = when (selectedKeePassGroupPath) {
                null -> null
                KEEPASS_GROUP_UNCATEGORIZED -> stringResource(R.string.category_none)
                else -> selectedKeePassGroupPath
            }
            if (groupLabel.isNullOrBlank()) {
                "${stringResource(R.string.filter_keepass)} · $databaseLabel"
            } else {
                "${stringResource(R.string.filter_keepass)} · $databaseLabel · $groupLabel"
            }
        }
        AutofillStorageSourceFilter.BITWARDEN -> {
            val vaultLabel = selectedVaultId?.let { vaultLabelById[it] }
                ?: stringResource(R.string.password_picker_all_vaults)
            val folderLabel = when (selectedFolderId) {
                null -> null
                BITWARDEN_FOLDER_UNCATEGORIZED -> stringResource(R.string.category_none)
                else -> selectedFolderId?.let { folderNameById[it] }
            }
            if (folderLabel.isNullOrBlank()) {
                "${stringResource(R.string.filter_bitwarden)} · $vaultLabel"
            } else {
                "${stringResource(R.string.filter_bitwarden)} · $vaultLabel · $folderLabel"
            }
        }
    }

    val activeFilterCount = when (sourceFilter) {
        AutofillStorageSourceFilter.ALL -> 0
        AutofillStorageSourceFilter.LOCAL -> 1
        AutofillStorageSourceFilter.KEEPASS -> 1 +
            (if (selectedKeePassDatabaseId != null) 1 else 0) +
            (if (selectedKeePassGroupPath != null) 1 else 0)
        AutofillStorageSourceFilter.BITWARDEN -> 1 +
            (if (selectedVaultId != null) 1 else 0) +
            (if (selectedFolderId != null) 1 else 0)
    }

    val filterTokens = when (sourceFilter) {
        AutofillStorageSourceFilter.ALL -> listOf(stringResource(R.string.filter_all))
        AutofillStorageSourceFilter.LOCAL -> listOf(stringResource(R.string.filter_local_only))
        AutofillStorageSourceFilter.KEEPASS -> buildList {
            add(stringResource(sourceFilter.labelResId()))
            selectedKeePassDatabaseId?.let { keepassNameById[it] }?.let(::add)
            when (selectedKeePassGroupPath) {
                null -> Unit
                KEEPASS_GROUP_UNCATEGORIZED -> add(stringResource(R.string.category_none))
                else -> selectedKeePassGroupPath?.let { add(it) }
            }
        }
        AutofillStorageSourceFilter.BITWARDEN -> buildList {
            add(stringResource(sourceFilter.labelResId()))
            selectedVaultId?.let { vaultLabelById[it] }?.let(::add)
            when (selectedFolderId) {
                null -> Unit
                BITWARDEN_FOLDER_UNCATEGORIZED -> add(stringResource(R.string.category_none))
                else -> {
                    val folderLabel = folderNameById[selectedFolderId]
                        .orEmpty()
                        .ifBlank { selectedFolderId ?: "" }
                    if (folderLabel.isNotBlank()) add(folderLabel)
                }
            }
        }
    }.filter { it.isNotBlank() }
    
    // 根据当前屏幕显示内容 - 带动画过渡
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState == "list") {
                // 返回列表：从左滑入
                (slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn(tween(300)))
                    .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut(tween(300)))
            } else {
                // 进入子页面：从右滑入
                (slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn(tween(300)))
                    .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut(tween(300)))
            }
        },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
        "list" -> {
            // 根据模式显示不同标题
            val title = when {
                args.isSaveMode -> stringResource(R.string.autofill_save_form_data)
                isManualMode -> stringResource(R.string.autofill_manual_quick_copy)
                else -> stringResource(R.string.autofill_with_monica)
            }
            
            AutofillScaffold(
                topBar = {
                    AutofillHeader(
                        title = title,
                        username = if (isManualMode) null else args.capturedUsername,
                        password = if (isManualMode) null else args.capturedPassword,
                        applicationId = if (isManualMode) null else args.applicationId,
                        webDomain = if (isManualMode) null else args.webDomain,
                        appIcon = if (isManualMode) null else appIcon,
                        appName = if (isManualMode) stringResource(R.string.autofill_select_password_and_copy) else appName,
                        onClose = onClose
                    )
                }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        AutofillExpressiveSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        AutofillFilterTrigger(
                            sourceFilter = sourceFilter,
                            summary = filterSummary,
                            chips = filterTokens,
                            activeFilterCount = activeFilterCount,
                            expanded = isFilterExpanded,
                            onClick = { isFilterExpanded = !isFilterExpanded },
                            panelContent = {
                                AutofillFilterPanelBody(
                                    sourceFilter = sourceFilter,
                                    activeFilterCount = activeFilterCount,
                                    keepassDatabases = keepassDatabases,
                                    keepassNameById = keepassNameById,
                                    selectedKeePassDatabaseId = selectedKeePassDatabaseId,
                                    selectedKeePassGroupPath = selectedKeePassGroupPath,
                                    keepassGroupsForSelectedDb = keepassGroupsForSelectedDb,
                                    hasUncategorizedKeepassEntries = hasUncategorizedKeepassEntries,
                                    bitwardenVaults = bitwardenVaults,
                                    vaultLabelById = vaultLabelById,
                                    selectedVaultId = selectedVaultId,
                                    selectedFolderId = selectedFolderId,
                                    selectedVaultFolders = selectedVaultFolders,
                                    folderNameById = folderNameById,
                                    hasUncategorizedBitwardenEntries = hasUncategorizedBitwardenEntries,
                                    onSourceFilterChange = { newSource ->
                                        sourceFilter = newSource
                                        when (newSource) {
                                            AutofillStorageSourceFilter.ALL,
                                            AutofillStorageSourceFilter.LOCAL -> {
                                                selectedKeePassDatabaseId = null
                                                selectedKeePassGroupPath = null
                                                selectedVaultId = null
                                                selectedFolderId = null
                                            }
                                            AutofillStorageSourceFilter.KEEPASS -> {
                                                selectedVaultId = null
                                                selectedFolderId = null
                                            }
                                            AutofillStorageSourceFilter.BITWARDEN -> {
                                                selectedKeePassDatabaseId = null
                                                selectedKeePassGroupPath = null
                                            }
                                        }
                                    },
                                    onSelectKeePassDatabase = { databaseId ->
                                        selectedKeePassDatabaseId = databaseId
                                        selectedKeePassGroupPath = null
                                    },
                                    onSelectKeePassGroup = { groupPath ->
                                        selectedKeePassGroupPath = groupPath
                                    },
                                    onSelectVault = { vaultId ->
                                        selectedVaultId = vaultId
                                        selectedFolderId = null
                                    },
                                    onSelectFolder = { folderId ->
                                        selectedFolderId = folderId
                                    },
                                    onResetAllFilters = {
                                        sourceFilter = AutofillStorageSourceFilter.ALL
                                        selectedKeePassDatabaseId = null
                                        selectedKeePassGroupPath = null
                                        selectedVaultId = null
                                        selectedFolderId = null
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                        
                        if (isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            val showSuggestedSection = searchQuery.isBlank() &&
                                filteredSuggestedPasswords.isNotEmpty()
                            val suggestedIds = filteredSuggestedPasswords.map { it.id }.toSet()
                            val mainPasswords = if (showSuggestedSection) {
                                filteredPasswords.filter { it.id !in suggestedIds }
                            } else {
                                filteredPasswords
                            }
                            val showNoSuggestionsHint = sourceFilter == AutofillStorageSourceFilter.ALL &&
                                selectedKeePassDatabaseId == null &&
                                selectedKeePassGroupPath == null &&
                                selectedVaultId == null &&
                                selectedFolderId == null &&
                                searchQuery.isBlank() &&
                                filteredSuggestedPasswords.isEmpty()

                            // 密码列表
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f)
                            ) {
                                // 建议密码区域（高亮）
                                if (showSuggestedSection) {
                                    item {
                                        Text(
                                            text = stringResource(R.string.autofill_suggested_fill),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                    
                                    items(
                                        items = filteredSuggestedPasswords,
                                        key = { "suggested_${it.id}" }
                                    ) { password ->
                                        SuggestedPasswordListItem(
                                            password = password,
                                            iconCardsEnabled = iconCardsEnabled,
                                            showSmartCopyOptions = hasNotificationPermission,
                                            onAction = { action ->
                                                when (action) {
                                                    is PasswordItemAction.Autofill -> {
                                                        onAutofill(action.password, false)
                                                    }
                                                    is PasswordItemAction.AutofillAndSaveUri -> {
                                                        onAutofill(action.password, true)
                                                    }
                                                    is PasswordItemAction.ViewDetails -> {
                                                        selectedPassword = action.password
                                                        currentScreen = "detail"
                                                    }
                                                    is PasswordItemAction.CopyUsername -> {
                                                        onCopy(autofillUsernameLabel, action.password.username, false)
                                                    }
                                                    is PasswordItemAction.CopyPassword -> {
                                                        val decryptedPassword = securityManager.decryptData(action.password.password)
                                                        onCopy(autofillPasswordLabel, decryptedPassword, true)
                                                    }
                                                    is PasswordItemAction.SmartCopyUsernameFirst -> {
                                                        onSmartCopy(action.password, true)
                                                    }
                                                    is PasswordItemAction.SmartCopyPasswordFirst -> {
                                                        onSmartCopy(action.password, false)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    
                                    item {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                }
                                
                                // 无建议提示
                                if (showNoSuggestionsHint) {
                                    item {
                                        NoSuggestionsHint()
                                    }
                                }
                                
                                if (mainPasswords.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = if (showSuggestedSection) {
                                                stringResource(R.string.autofill_other_entries)
                                            } else {
                                                when (sourceFilter) {
                                                    AutofillStorageSourceFilter.ALL -> stringResource(R.string.autofill_all_entries)
                                                    AutofillStorageSourceFilter.LOCAL -> stringResource(R.string.filter_local_only)
                                                    AutofillStorageSourceFilter.KEEPASS -> stringResource(R.string.filter_keepass)
                                                    AutofillStorageSourceFilter.BITWARDEN -> stringResource(R.string.filter_bitwarden)
                                                }
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                } else if (!showNoSuggestionsHint) {
                                    item {
                                        EmptyPasswordState(
                                            modifier = Modifier
                                                .fillParentMaxSize()
                                                .padding(top = 32.dp)
                                        )
                                    }
                                }
                                
                                items(
                                    items = mainPasswords,
                                    key = { it.id }
                                ) { password ->
                                    PasswordListItem(
                                        password = password,
                                        showDropdownMenu = true,
                                        iconCardsEnabled = iconCardsEnabled,
                                        showSmartCopyOptions = hasNotificationPermission,
                                        onAction = { action ->
                                            when (action) {
                                                is PasswordItemAction.Autofill -> {
                                                    onAutofill(action.password, false)
                                                }
                                                is PasswordItemAction.AutofillAndSaveUri -> {
                                                    onAutofill(action.password, true)
                                                }
                                                is PasswordItemAction.ViewDetails -> {
                                                    selectedPassword = action.password
                                                    currentScreen = "detail"
                                                }
                                                is PasswordItemAction.CopyUsername -> {
                                                    onCopy(autofillUsernameLabel, action.password.username, false)
                                                }
                                                is PasswordItemAction.CopyPassword -> {
                                                    val decryptedPassword = securityManager.decryptData(action.password.password)
                                                    onCopy(autofillPasswordLabel, decryptedPassword, true)
                                                }
                                                is PasswordItemAction.SmartCopyUsernameFirst -> {
                                                    onSmartCopy(action.password, true)
                                                }
                                                is PasswordItemAction.SmartCopyPasswordFirst -> {
                                                    onSmartCopy(action.password, false)
                                                }
                                            }
                                        }
                                    )
                                }
                                
                                item {
                                    Spacer(modifier = Modifier.height(80.dp))
                                }
                            }
                        }
                    }

                    // FAB: 新建
                    ExtendedFloatingActionButton(
                        onClick = { currentScreen = "add" },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.create_new)) }
                    )
                }
            }
        }
        
        "detail" -> {
            selectedPassword?.let { password ->
                InlinePasswordDetailContent(
                    password = password,
                    securityManager = securityManager,
                    onAutofill = { onAutofill(password, false) },
                    onAutofillAndSaveUri = { onAutofill(password, true) },
                    onBack = { currentScreen = "list" }
                )
            }
        }
        
        "add" -> {
            InlineAddPasswordContent(
                initialTitle = "",
                initialUsername = args.capturedUsername ?: "",
                initialPassword = args.capturedPassword ?: "",
                initialWebsite = args.webDomain ?: "",
                initialAppPackageName = args.applicationId,
                initialAppName = appName,
                appIcon = appIcon,
                keepassDatabases = keepassDatabases,
                onSave = { newEntry ->
                    coroutineScope.launch {
                        try {
                            // 加密并保存
                            val encryptedEntry = newEntry.copy(
                                password = securityManager.encryptData(newEntry.password)
                            )
                            val id = onSaveNewPassword(encryptedEntry)
                            
                            // 获取保存后的完整条目
                            val savedEntry = repository.getPasswordEntryById(id)
                            if (savedEntry != null) {
                                // 自动填充并保存 URI
                                onAutofill(savedEntry, true)
                            } else {
                                currentScreen = "list"
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AutofillPickerV2", "Error saving password", e)
                            currentScreen = "list"
                        }
                    }
                },
                onCancel = { currentScreen = "list" }
            )
        }
        }
    }
    


}

private enum class AutofillStorageSourceFilter {
    ALL,
    LOCAL,
    KEEPASS,
    BITWARDEN
}

private const val KEEPASS_GROUP_UNCATEGORIZED = "__keepass_uncategorized__"
private const val BITWARDEN_FOLDER_UNCATEGORIZED = "__bitwarden_uncategorized__"

private fun AutofillStorageSourceFilter.labelResId(): Int = when (this) {
    AutofillStorageSourceFilter.ALL -> R.string.filter_all
    AutofillStorageSourceFilter.LOCAL -> R.string.filter_monica
    AutofillStorageSourceFilter.KEEPASS -> R.string.filter_keepass
    AutofillStorageSourceFilter.BITWARDEN -> R.string.filter_bitwarden
}

private fun AutofillStorageSourceFilter.icon(): ImageVector = when (this) {
    AutofillStorageSourceFilter.ALL -> Icons.Default.FilterList
    AutofillStorageSourceFilter.LOCAL -> Icons.Default.Smartphone
    AutofillStorageSourceFilter.KEEPASS -> Icons.Default.Storage
    AutofillStorageSourceFilter.BITWARDEN -> Icons.Default.CloudSync
}

private fun PasswordEntry.matchesSearchQuery(query: String): Boolean {
    if (query.isBlank()) return true
    return title.contains(query, ignoreCase = true) ||
        appName.contains(query, ignoreCase = true) ||
        username.contains(query, ignoreCase = true) ||
        website.contains(query, ignoreCase = true)
}

@Composable
private fun NoSuggestionsHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.autofill_no_suggestions_in_context),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AutofillExpressiveSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(22.dp),
        placeholder = {
            Text(
                text = stringResource(R.string.search_passwords),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.clear_search)
                    )
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun AutofillFilterTrigger(
    sourceFilter: AutofillStorageSourceFilter,
    summary: String,
    chips: List<String>,
    activeFilterCount: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    panelContent: (@Composable ColumnScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 170, easing = LinearEasing),
        label = "filter_arrow_rotation"
    )

    Surface(
        modifier = modifier
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = sourceFilter.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .size(34.dp)
                            .padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.filter_title),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (activeFilterCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = activeFilterCount.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
                )
            }

            val visibleChips = chips.take(3)
            if (visibleChips.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    visibleChips.forEach { chip ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = chip,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    if (chips.size > visibleChips.size) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "+${chips.size - visibleChips.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            if (panelContent != null) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(
                        animationSpec = tween(durationMillis = 180, easing = LinearEasing),
                        expandFrom = Alignment.Top
                    ) + fadeIn(animationSpec = tween(durationMillis = 120, easing = LinearEasing)),
                    exit = shrinkVertically(
                        animationSpec = tween(durationMillis = 160, easing = LinearEasing),
                        shrinkTowards = Alignment.Top
                    ) + fadeOut(animationSpec = tween(durationMillis = 90, easing = LinearEasing))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        panelContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterScopeCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutofillFilterPanelBody(
    sourceFilter: AutofillStorageSourceFilter,
    activeFilterCount: Int,
    keepassDatabases: List<LocalKeePassDatabase>,
    keepassNameById: Map<Long, String>,
    selectedKeePassDatabaseId: Long?,
    selectedKeePassGroupPath: String?,
    keepassGroupsForSelectedDb: List<String>,
    hasUncategorizedKeepassEntries: Boolean,
    bitwardenVaults: List<BitwardenVault>,
    vaultLabelById: Map<Long, String>,
    selectedVaultId: Long?,
    selectedFolderId: String?,
    selectedVaultFolders: List<BitwardenFolder>,
    folderNameById: Map<String, String>,
    hasUncategorizedBitwardenEntries: Boolean,
    onSourceFilterChange: (AutofillStorageSourceFilter) -> Unit,
    onSelectKeePassDatabase: (Long?) -> Unit,
    onSelectKeePassGroup: (String?) -> Unit,
    onSelectVault: (Long?) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onResetAllFilters: () -> Unit
) {
    var keepassMenuExpanded by remember { mutableStateOf(false) }
    var keepassGroupMenuExpanded by remember { mutableStateOf(false) }
    var vaultMenuExpanded by remember { mutableStateOf(false) }
    var folderMenuExpanded by remember { mutableStateOf(false) }
    val sourceOptions = AutofillStorageSourceFilter.values().toList()
    val hasActiveFilters = activeFilterCount > 0
    val dropdownFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sourceOptions.forEach { option ->
                FilterChip(
                    selected = sourceFilter == option,
                    onClick = { onSourceFilterChange(option) },
                    label = { Text(stringResource(option.labelResId())) },
                    leadingIcon = {
                        Icon(
                            imageVector = option.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }

        if (sourceFilter == AutofillStorageSourceFilter.KEEPASS) {
            FilterScopeCard(title = stringResource(R.string.filter_keepass)) {
                Text(
                    text = stringResource(R.string.password_picker_filter_database),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ExposedDropdownMenuBox(
                    expanded = keepassMenuExpanded,
                    onExpandedChange = { keepassMenuExpanded = !keepassMenuExpanded }
                ) {
                    TextField(
                        readOnly = true,
                        value = selectedKeePassDatabaseId?.let { keepassNameById[it] }
                            ?: stringResource(R.string.password_picker_all_databases),
                        onValueChange = {},
                        leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = keepassMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = dropdownFieldColors
                    )
                    ExposedDropdownMenu(
                        expanded = keepassMenuExpanded,
                        onDismissRequest = { keepassMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.password_picker_all_databases)) },
                            onClick = {
                                onSelectKeePassDatabase(null)
                                keepassMenuExpanded = false
                            }
                        )
                        keepassDatabases.forEach { databaseItem ->
                            DropdownMenuItem(
                                text = { Text(databaseItem.name) },
                                onClick = {
                                    onSelectKeePassDatabase(databaseItem.id)
                                    keepassMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                if (selectedKeePassDatabaseId != null) {
                    Text(
                        text = stringResource(R.string.password_picker_filter_folder),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ExposedDropdownMenuBox(
                        expanded = keepassGroupMenuExpanded,
                        onExpandedChange = { keepassGroupMenuExpanded = !keepassGroupMenuExpanded }
                    ) {
                        TextField(
                            readOnly = true,
                            value = when (selectedKeePassGroupPath) {
                                null -> stringResource(R.string.password_picker_all_folders)
                                KEEPASS_GROUP_UNCATEGORIZED -> stringResource(R.string.category_none)
                                else -> selectedKeePassGroupPath
                            },
                            onValueChange = {},
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = keepassGroupMenuExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = dropdownFieldColors
                        )
                        ExposedDropdownMenu(
                            expanded = keepassGroupMenuExpanded,
                            onDismissRequest = { keepassGroupMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.password_picker_all_folders)) },
                                onClick = {
                                    onSelectKeePassGroup(null)
                                    keepassGroupMenuExpanded = false
                                }
                            )
                            if (hasUncategorizedKeepassEntries) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.category_none)) },
                                    onClick = {
                                        onSelectKeePassGroup(KEEPASS_GROUP_UNCATEGORIZED)
                                        keepassGroupMenuExpanded = false
                                    }
                                )
                            }
                            keepassGroupsForSelectedDb.forEach { groupPath ->
                                DropdownMenuItem(
                                    text = { Text(groupPath) },
                                    onClick = {
                                        onSelectKeePassGroup(groupPath)
                                        keepassGroupMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (sourceFilter == AutofillStorageSourceFilter.BITWARDEN) {
            FilterScopeCard(title = stringResource(R.string.filter_bitwarden)) {
                Text(
                    text = stringResource(R.string.password_picker_filter_vault),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ExposedDropdownMenuBox(
                    expanded = vaultMenuExpanded,
                    onExpandedChange = { vaultMenuExpanded = !vaultMenuExpanded }
                ) {
                    TextField(
                        readOnly = true,
                        value = selectedVaultId?.let { vaultLabelById[it] }
                            ?: stringResource(R.string.password_picker_all_vaults),
                        onValueChange = {},
                        leadingIcon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vaultMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = dropdownFieldColors
                    )
                    ExposedDropdownMenu(
                        expanded = vaultMenuExpanded,
                        onDismissRequest = { vaultMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.password_picker_all_vaults)) },
                            onClick = {
                                onSelectVault(null)
                                vaultMenuExpanded = false
                            }
                        )
                        bitwardenVaults.forEach { vault ->
                            val label = vaultLabelById[vault.id].orEmpty()
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onSelectVault(vault.id)
                                    vaultMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                if (selectedVaultId != null) {
                    Text(
                        text = stringResource(R.string.password_picker_filter_folder),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ExposedDropdownMenuBox(
                        expanded = folderMenuExpanded,
                        onExpandedChange = { folderMenuExpanded = !folderMenuExpanded }
                    ) {
                        TextField(
                            readOnly = true,
                            value = when (selectedFolderId) {
                                null -> stringResource(R.string.password_picker_all_folders)
                                BITWARDEN_FOLDER_UNCATEGORIZED -> stringResource(R.string.category_none)
                                else -> folderNameById[selectedFolderId].orEmpty()
                            },
                            onValueChange = {},
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = folderMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = dropdownFieldColors
                        )
                        ExposedDropdownMenu(
                            expanded = folderMenuExpanded,
                            onDismissRequest = { folderMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.password_picker_all_folders)) },
                                onClick = {
                                    onSelectFolder(null)
                                    folderMenuExpanded = false
                                }
                            )
                            if (hasUncategorizedBitwardenEntries) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.category_none)) },
                                    onClick = {
                                        onSelectFolder(BITWARDEN_FOLDER_UNCATEGORIZED)
                                        folderMenuExpanded = false
                                    }
                                )
                            }
                            selectedVaultFolders.forEach { folder ->
                                DropdownMenuItem(
                                    text = { Text(folder.name) },
                                    onClick = {
                                        onSelectFolder(folder.bitwardenFolderId)
                                        folderMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (hasActiveFilters) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onResetAllFilters) {
                    Text(text = stringResource(R.string.clear))
                }
            }
        }
    }
}

