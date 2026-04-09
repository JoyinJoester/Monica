package takagi.ru.monica.autofill_ng

import android.app.Activity
import android.app.Application
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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.Parcelize
import takagi.ru.monica.R
import takagi.ru.monica.autofill_ng.core.AutofillLogger
import takagi.ru.monica.autofill_ng.ui.*
import takagi.ru.monica.autofill_ng.utils.SmartCopyNotificationHelper
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.isLocalPasswordOwnership
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.repository.CustomFieldRepository
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.theme.MonicaTheme
import androidx.compose.foundation.isSystemInDarkTheme
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.ui.components.PasswordVerificationContent
import takagi.ru.monica.ui.base.BaseMonicaActivity
import takagi.ru.monica.ui.screens.AddEditBankCardScreen
import takagi.ru.monica.ui.screens.AddEditDocumentScreen
import takagi.ru.monica.ui.screens.AddEditPasswordInitialDraft
import takagi.ru.monica.ui.screens.AddEditPasswordScreen
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.util.TotpDataResolver
import takagi.ru.monica.util.TotpGenerator
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.viewmodel.LocalKeePassViewModel
import takagi.ru.monica.viewmodel.PasswordViewModel

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
        val responseAuthMode: Boolean = false,
        val rememberLastFilled: Boolean = true,
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
    
    private val explicitManualMode by lazy {
        intent.getBooleanExtra("extra_manual_mode", false)
    }

    private val manualModeReason by lazy {
        when {
            explicitManualMode -> "explicit_manual_extra"
            !args.fieldSignatureKey.isNullOrBlank() -> "framework_context_field_signature"
            !args.applicationId.isNullOrBlank() -> "framework_context_application_id"
            !args.webDomain.isNullOrBlank() -> "framework_context_web_domain"
            args.responseAuthMode -> "framework_context_response_auth"
            args.isSaveMode -> "framework_context_save_mode"
            args.autofillIds.isNullOrEmpty() -> "no_framework_context_and_no_ids"
            else -> "autofill_ids_present"
        }
    }

    // 手动模式：仅在明确的手动入口中启用。
    // 某些系统/应用链路会丢失 AutofillId，但仍然属于真实的自动填充请求，
    // 这类场景不能退化成手动模式，否则会导致来源显示错误且无法标记非自动填充。
    private val isManualMode by lazy {
        if (explicitManualMode) {
            true
        } else {
            val hasFrameworkAutofillContext =
                !args.fieldSignatureKey.isNullOrBlank() ||
                    !args.applicationId.isNullOrBlank() ||
                    !args.webDomain.isNullOrBlank() ||
                    args.responseAuthMode ||
                    args.isSaveMode
            !hasFrameworkAutofillContext && args.autofillIds.isNullOrEmpty()
        }
    }
    

    
    // attachBaseContext 已由 BaseMonicaActivity 统一处理
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // BaseMonicaActivity 已调用 enableEdgeToEdge()
        AutofillLogger.initialize(applicationContext)
        val launchedFromAutofillFramework = !args.autofillIds.isNullOrEmpty()
        if (!launchedFromAutofillFramework && shouldSuppressDuplicateLaunch(args)) {
            AutofillLogger.w("PICKER", "Suppress duplicate picker launch within ${DUPLICATE_LAUNCH_WINDOW_MS}ms")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val idCount = args.autofillIds?.size ?: 0
        val hintCount = args.autofillHints?.size ?: 0
        val suggestedCount = args.suggestedPasswordIds?.size ?: 0
        AutofillLogger.i(
            "PICKER",
            "Picker opened: saveMode=${args.isSaveMode}, responseAuth=${args.responseAuthMode}, ids=$idCount, hints=$hintCount",
            metadata = mapOf(
                "sdk" to Build.VERSION.SDK_INT,
                "device" to "${Build.MANUFACTURER}/${Build.MODEL}",
                "manualMode" to isManualMode,
                "manualReason" to manualModeReason,
                "manualExtra" to explicitManualMode,
                "launchedFromFramework" to launchedFromAutofillFramework,
                "idCount" to idCount,
                "hintCount" to hintCount,
                "suggestedCount" to suggestedCount,
                "hintPreview" to (args.autofillHints?.take(6)?.joinToString(",") ?: "none"),
                "applicationId" to (args.applicationId ?: "none"),
                "webDomain" to (args.webDomain ?: "none"),
                "responseAuth" to args.responseAuthMode,
                "saveMode" to args.isSaveMode,
                "interactionIdPresent" to !args.interactionIdentifier.isNullOrBlank(),
                "fieldSignaturePresent" to !args.fieldSignatureKey.isNullOrBlank()
            )
        )
        if (!explicitManualMode && idCount == 0) {
            AutofillLogger.w(
                "PICKER",
                "Opened without AutofillId in non-manual entry path",
                metadata = mapOf(
                    "hintCount" to hintCount,
                    "responseAuth" to args.responseAuthMode,
                    "applicationId" to (args.applicationId ?: "none"),
                    "webDomain" to (args.webDomain ?: "none"),
                    "fieldSignaturePresent" to !args.fieldSignatureKey.isNullOrBlank(),
                    "manualReason" to manualModeReason,
                )
            )
        }
        if (hintCount > 0 && idCount != hintCount) {
            AutofillLogger.w(
                "PICKER",
                "Autofill IDs and hints size mismatch",
                metadata = mapOf("idCount" to idCount, "hintCount" to hintCount)
            )
        }
        
        val database = PasswordDatabase.getDatabase(applicationContext)
        val repository = PasswordRepository(database.passwordEntryDao())
        val localKeePassDao = database.localKeePassDatabaseDao()
        val securityManager = SecurityManager(applicationContext)
        // settingsManager 已由 BaseMonicaActivity 初始化
        val localSettingsManager = settingsManager

        runCatching {
            val autoLockMinutes = runBlocking {
                localSettingsManager.settingsFlow.first().autoLockMinutes
            }
            SessionManager.updateAutoLockTimeout(autoLockMinutes)
        }.onFailure { error ->
            AutofillLogger.w(
                "PICKER",
                "Failed to sync auto-lock timeout before verification: ${error.message}"
            )
        }
        
        // 主应用已解锁时可直接进入；否则在自动填充页内复用同款验证界面，
        // 但验证结果仅用于本次自动填充，不回写主应用共享会话。
        val canOpenPicker = securityManager.canRestoreMainAppSession(
            applicationContext,
            cachedSettings?.autoLockMinutes ?: 5
        )
        
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
                    canSkipVerification = canOpenPicker,
                    biometricEnabled = settings.biometricEnabled,
                    iconCardsEnabled = settings.iconCardsEnabled,
                    isManualMode = isManualMode,
                    manualModeReason = manualModeReason,
                    onAutofill = { password, forceAddUri ->
                        handleAutofill(password, forceAddUri)
                    },
                    onAutofillBankCard = ::handleBankCardAutofill,
                    onAutofillDocument = ::handleDocumentAutofill,
                    onCopy = ::copyToClipboard,
                    onClose = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                    onMarkAsNonAutofill = { markCurrentFieldAsNonAutofill() },
                    onSmartCopy = { password, usernameFirst ->
                        handleSmartCopy(password, usernameFirst)
                    }
                )
            }
        }
    }

    override fun shouldEnforceSharedSessionLock(): Boolean {
        return false
    }

    private fun markCurrentFieldAsNonAutofill() {
        val signatureKey = args.fieldSignatureKey?.trim()?.lowercase().orEmpty()
        if (signatureKey.isBlank()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        lifecycleScope.launch {
            runCatching {
                AutofillPreferences(applicationContext).markFieldSignatureBlocked(
                    signatureKey = signatureKey,
                    packageName = args.applicationId,
                    webDomain = args.webDomain,
                    hints = args.autofillHints?.toList().orEmpty(),
                )
            }.onFailure { error ->
                AutofillLogger.e("PICKER", "Failed to block field signature", error)
            }
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_ARGS, Args::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_ARGS)
        }
        AutofillLogger.w(
            "PICKER",
            "onNewIntent received while picker is active; reusing current instance",
            metadata = mapOf(
                "newIdCount" to (newArgs?.autofillIds?.size ?: 0),
                "newHintCount" to (newArgs?.autofillHints?.size ?: 0),
                "newSuggestedCount" to (newArgs?.suggestedPasswordIds?.size ?: 0),
                "newSaveMode" to (newArgs?.isSaveMode ?: false),
                "newResponseAuth" to (newArgs?.responseAuthMode ?: false)
            )
        )
    }
    
    private fun handleSmartCopy(password: PasswordEntry, usernameFirst: Boolean) {
        val securityManager = SecurityManager(applicationContext)
        val accountValue = AccountFillPolicy.resolveAccountIdentifier(password, securityManager)
        val decryptedPassword = AutofillSecretResolver.decryptPasswordOrNull(
            securityManager = securityManager,
            encryptedOrPlain = password.password,
            logTag = "AutofillPickerV2",
        )
        if (decryptedPassword.isNullOrBlank()) {
            android.util.Log.w("AutofillPickerV2", "Smart copy skipped: password decryption unavailable")
            android.widget.Toast.makeText(
                this,
                R.string.autofill_copy_password_unavailable,
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (usernameFirst && accountValue.isBlank()) {
            android.util.Log.w("AutofillPickerV2", "Smart copy skipped: account identifier unavailable")
            android.widget.Toast.makeText(
                this,
                R.string.autofill_copy_account_unavailable,
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!usernameFirst && accountValue.isBlank()) {
            copyToClipboard(getString(R.string.autofill_password), decryptedPassword, true)
            android.widget.Toast.makeText(
                this,
                R.string.autofill_copy_account_unavailable,
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        val queued = if (usernameFirst) {
            // Copy username first, queue password for notification
            val result = SmartCopyNotificationHelper.copyAndQueueNext(
                context = this,
                firstValue = accountValue,
                firstLabel = getString(R.string.autofill_username),
                secondValue = decryptedPassword,
                secondLabel = getString(R.string.autofill_password)
            )
            android.widget.Toast.makeText(this, R.string.username_copied, android.widget.Toast.LENGTH_SHORT).show()
            result
        } else {
            // Copy password first, queue username for notification
            val result = SmartCopyNotificationHelper.copyAndQueueNext(
                context = this,
                firstValue = decryptedPassword.orEmpty(),
                firstLabel = getString(R.string.autofill_password),
                secondValue = accountValue,
                secondLabel = getString(R.string.autofill_username)
            )
            android.widget.Toast.makeText(this, R.string.password_copied, android.widget.Toast.LENGTH_SHORT).show()
            result
        }
        
        if (queued) {
            // Close the picker only when queued action is available.
            setResult(Activity.RESULT_CANCELED)
            finish()
        } else {
            android.widget.Toast.makeText(
                this,
                R.string.smart_copy_notification_unavailable,
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
        

    
    private fun handleAutofill(password: PasswordEntry, forceAddUri: Boolean) {
        val securityManager = SecurityManager(applicationContext)
        val accountValue = AccountFillPolicy.resolveAccountIdentifier(password, securityManager)
        val fillEmailWithAccount = AccountFillPolicy.shouldFillEmailWithAccount(applicationContext)
        val decryptedPassword = AutofillSecretResolver.decryptPasswordOrNull(
            securityManager = securityManager,
            encryptedOrPlain = password.password,
            logTag = "AutofillPickerV2",
        )
        val hints = args.autofillHints
        val hasPasswordTarget = hints?.any {
            it == EnhancedAutofillStructureParserV2.FieldHint.PASSWORD.name ||
                it == EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD.name
        } == true
        if ((isManualMode || hasPasswordTarget) && decryptedPassword.isNullOrBlank()) {
            android.util.Log.w("AutofillPickerV2", "Autofill canceled: password decryption unavailable")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        
        // 手动模式：复制密码到剪贴板
        if (isManualMode) {
            // 使用智能复制：先复制密码，通知栏提供用户名
            val queued = SmartCopyNotificationHelper.copyAndQueueNext(
                context = this,
                firstValue = decryptedPassword.orEmpty(),
                firstLabel = getString(R.string.autofill_password),
                secondValue = accountValue,
                secondLabel = getString(R.string.autofill_username)
            )
            android.widget.Toast.makeText(this, R.string.password_copied, android.widget.Toast.LENGTH_SHORT).show()
            if (queued) {
                finish()
            } else {
                android.widget.Toast.makeText(
                    this,
                    R.string.smart_copy_notification_unavailable,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            return
        }
        
        // 正常自动填充模式：构建 Dataset
        val autofillIds = args.autofillIds
        if (autofillIds.isNullOrEmpty()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val datasetBuilder = Dataset.Builder()

        val normalizedHints = hints.orEmpty().map { it.trim().lowercase() }
        val hasUsernameHint = normalizedHints.any {
            it == EnhancedAutofillStructureParserV2.FieldHint.USERNAME.name.lowercase() || it.contains("username")
        }
        val hasPhoneHint = normalizedHints.any {
            it == EnhancedAutofillStructureParserV2.FieldHint.PHONE_NUMBER.name.lowercase() ||
                it.contains("phone") ||
                it.contains("mobile") ||
                it.contains("tel")
        }
        val hasEmailHint = normalizedHints.any {
            it == EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS.name.lowercase() || it.contains("email")
        }
        val hasAccountHint = hasUsernameHint || hasPhoneHint
        val allowAccountInEmailField =
            fillEmailWithAccount || accountValue.contains("@") || (!hasAccountHint && hasEmailHint)
        var filledCount = 0
        var strictFilledCount = 0
        var fallbackFilledCount = 0
        val unmatchedHintPreview = mutableListOf<String>()
        autofillIds.forEachIndexed { index, autofillId ->
            val normalizedHint = hints?.getOrNull(index)?.trim()?.lowercase().orEmpty()
            val value = when {
                normalizedHint == EnhancedAutofillStructureParserV2.FieldHint.USERNAME.name.lowercase() ||
                    normalizedHint.contains("username") -> accountValue
                normalizedHint == EnhancedAutofillStructureParserV2.FieldHint.PHONE_NUMBER.name.lowercase() ||
                    normalizedHint.contains("phone") ||
                    normalizedHint.contains("mobile") ||
                    normalizedHint.contains("tel") -> accountValue
                normalizedHint == EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS.name.lowercase() ||
                    normalizedHint.contains("email") -> if (allowAccountInEmailField) accountValue else null
                normalizedHint == EnhancedAutofillStructureParserV2.FieldHint.PASSWORD.name.lowercase() ||
                    normalizedHint == EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD.name.lowercase() ||
                    normalizedHint.contains("password") ||
                    normalizedHint.contains("pass") -> decryptedPassword
                else -> null
            }
            if (value != null) {
                datasetBuilder.setValue(autofillId, AutofillValue.forText(value))
                filledCount++
                strictFilledCount++
            } else if (unmatchedHintPreview.size < 6) {
                unmatchedHintPreview += if (normalizedHint.isBlank()) "(blank)" else normalizedHint
            }
        }

        AutofillLogger.i(
            "PICKER",
            "Auth strict mapping diagnostics",
            metadata = mapOf(
                "idCount" to autofillIds.size,
                "hintCount" to (hints?.size ?: 0),
                "strictFilledCount" to strictFilledCount,
                "allowAccountInEmailField" to allowAccountInEmailField,
                "unmatchedHintPreview" to if (unmatchedHintPreview.isEmpty()) "none" else unmatchedHintPreview.joinToString(","),
            )
        )

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
                    datasetBuilder.setValue(autofillId, AutofillValue.forText(fallbackValue))
                    filledCount++
                    fallbackFilledCount++
                }
            }

            AutofillLogger.i(
                "PICKER",
                "Auth fallback mapping diagnostics",
                metadata = mapOf(
                    "idCount" to autofillIds.size,
                    "hintCount" to (hints?.size ?: 0),
                    "fallbackFilledCount" to fallbackFilledCount,
                    "strictFilledCount" to strictFilledCount,
                )
            )
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

        if (args.rememberLastFilled) {
            rememberLastFilledCredential(password.id)
        }
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

    private fun handleBankCardAutofill(item: SecureItem) {
        val (_, data) = parseBankCardCandidate(item) ?: run {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        if (isManualMode) {
            val manualValue = data.cardNumber.ifBlank { data.cardholderName }
            if (manualValue.isNotBlank()) {
                copyToClipboard(getString(R.string.item_type_bank_card), manualValue, true)
            }
            finish()
            return
        }

        val autofillIds = args.autofillIds
        if (autofillIds.isNullOrEmpty()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val datasetBuilder = Dataset.Builder()
        val hints = args.autofillHints
        var filledCount = 0
        autofillIds.forEachIndexed { index, autofillId ->
            val value = mapBankCardAutofillValue(hints?.getOrNull(index), data)
            if (!value.isNullOrBlank()) {
                datasetBuilder.setValue(autofillId, AutofillValue.forText(value))
                filledCount++
            }
        }

        if (filledCount == 0 && autofillIds.size == 1) {
            val fallbackValue = data.cardNumber.ifBlank { data.cardholderName }
            if (fallbackValue.isNotBlank()) {
                datasetBuilder.setValue(autofillIds.first(), AutofillValue.forText(fallbackValue))
                filledCount = 1
            }
        }

        if (filledCount == 0) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val dataset = datasetBuilder.build()
        val authResult: Parcelable = if (args.responseAuthMode) {
            FillResponse.Builder().addDataset(dataset).build()
        } else {
            dataset
        }
        val resultIntent = Intent().apply {
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, authResult)
        }
        AutofillLogger.i(
            "PICKER",
            "Returning bank card autofill: itemId=${item.id}, filled=$filledCount, hints=${hints?.joinToString(",") ?: "none"}"
        )
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun handleDocumentAutofill(item: SecureItem) {
        val (_, data) = parseDocumentCandidate(item) ?: run {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        if (isManualMode) {
            val displayName = listOf(data.firstName, data.middleName, data.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { data.fullName }
            val manualValue = data.documentNumber.ifBlank { displayName }
            if (manualValue.isNotBlank()) {
                copyToClipboard(getString(R.string.item_type_document), manualValue, true)
            }
            finish()
            return
        }

        val autofillIds = args.autofillIds
        if (autofillIds.isNullOrEmpty()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val datasetBuilder = Dataset.Builder()
        val hints = args.autofillHints
        var filledCount = 0
        autofillIds.forEachIndexed { index, autofillId ->
            val value = mapDocumentAutofillValue(hints?.getOrNull(index), data)
            if (!value.isNullOrBlank()) {
                datasetBuilder.setValue(autofillId, AutofillValue.forText(value))
                filledCount++
            }
        }

        if (filledCount == 0 && autofillIds.size == 1) {
            val displayName = listOf(data.firstName, data.middleName, data.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { data.fullName }
            val fallbackValue = data.documentNumber
                .ifBlank { displayName }
            if (fallbackValue.isNotBlank()) {
                datasetBuilder.setValue(autofillIds.first(), AutofillValue.forText(fallbackValue))
                filledCount = 1
            }
        }

        if (filledCount == 0) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val dataset = datasetBuilder.build()
        val authResult: Parcelable = if (args.responseAuthMode) {
            FillResponse.Builder().addDataset(dataset).build()
        } else {
            dataset
        }
        val resultIntent = Intent().apply {
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, authResult)
        }
        AutofillLogger.i(
            "PICKER",
            "Returning document autofill: itemId=${item.id}, filled=$filledCount, hints=${hints?.joinToString(",") ?: "none"}"
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
        return TotpDataResolver.fromAuthenticatorKey(authenticatorKey)
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

        val identityKey = buildTotpIdentityKey(passwordTotpData)
        if (identityKey.isNotEmpty()) {
            validatorTotpList.firstOrNull { buildTotpIdentityKey(it) == identityKey }?.let { return it }
        }

        return null
    }

    private fun buildTotpIdentityKey(data: TotpData?): String {
        val normalized = data?.let { TotpDataResolver.normalizeTotpData(it) } ?: return ""
        val normalizedSecret = TotpDataResolver.normalizeBase32Secret(normalized.secret)
        return listOf(
            normalized.otpType.name,
            normalizedSecret,
            normalized.digits.toString(),
            normalized.period.toString(),
            normalized.algorithm.uppercase(),
            normalized.counter.toString()
        ).joinToString("|")
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
    manualModeReason: String = "unknown",
    onAutofill: (PasswordEntry, Boolean) -> Unit,
    onAutofillBankCard: (SecureItem) -> Unit,
    onAutofillDocument: (SecureItem) -> Unit,
    onCopy: (String, String, Boolean) -> Unit,
    onClose: () -> Unit,
    onMarkAsNonAutofill: () -> Unit,
    onSmartCopy: (PasswordEntry, Boolean) -> Unit
) {
    // 导航状态: "list", "detail", "add"
    var currentScreen by remember { mutableStateOf("list") }
    var selectedPassword by remember { mutableStateOf<PasswordEntry?>(null) }
    
    var allPasswords by remember { mutableStateOf<List<PasswordEntry>>(emptyList()) }
    var suggestedPasswords by remember { mutableStateOf<List<PasswordEntry>>(emptyList()) }
    var searchedPasswords by remember { mutableStateOf<List<PasswordEntry>>(emptyList()) }
    var allBankCards by remember { mutableStateOf<List<SecureItem>>(emptyList()) }
    var allDocuments by remember { mutableStateOf<List<SecureItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchLoading by remember { mutableStateOf(false) }
    var showMarkAsNonAutofillDialog by remember { mutableStateOf(false) }
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
    val autofillPreferences = remember(context) { AutofillPreferences(context) }
    val requestProfile = remember(args.autofillHints) {
        buildAutofillPickerRequestProfile(args.autofillHints)
    }
    val loginHintCount = remember(args.autofillHints) {
        args.autofillHints.orEmpty().count(::isLoginAutofillHint)
    }
    val bankCardHintCount = remember(args.autofillHints) {
        args.autofillHints.orEmpty().count(::isBankCardAutofillHint)
    }
    val documentHintCount = remember(args.autofillHints) {
        args.autofillHints.orEmpty().count(::isDocumentAutofillHint)
    }
    val addTargets = remember(requestProfile, loginHintCount, bankCardHintCount, documentHintCount) {
        resolveAutofillAddTargets(
            requestProfile = requestProfile,
            loginHintCount = loginHintCount,
            bankCardHintCount = bankCardHintCount,
            documentHintCount = documentHintCount,
        )
    }
    val defaultAddTarget = addTargets.lastOrNull()
    var pendingAddTarget by rememberSaveable { mutableStateOf<AutofillAddTarget?>(null) }
    val appDb = remember(context) { PasswordDatabase.getDatabase(context.applicationContext) }
    val secureItemRepository = remember(appDb) { SecureItemRepository(appDb.secureItemDao()) }
    val customFieldRepository = remember(appDb) { CustomFieldRepository(appDb.customFieldDao()) }
    val hasNotificationPermission = remember {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val canMarkAsNonAutofill = !isManualMode && !args.isSaveMode && !args.fieldSignatureKey.isNullOrBlank()
    
    val autofillUsernameLabel = stringResource(R.string.autofill_username)
    val autofillPasswordLabel = stringResource(R.string.autofill_password)
    val autofillCopyPasswordUnavailable = stringResource(R.string.autofill_copy_password_unavailable)
    val autofillCopyAccountUnavailable = stringResource(R.string.autofill_copy_account_unavailable)

    val handlePasswordAction: (PasswordItemAction) -> Unit = { action ->
        when (action) {
            is PasswordItemAction.Autofill -> onAutofill(action.password, false)
            is PasswordItemAction.AutofillAndSaveUri -> onAutofill(action.password, true)
            is PasswordItemAction.ViewDetails -> {
                selectedPassword = action.password
                currentScreen = "detail"
            }
            is PasswordItemAction.CopyUsername -> {
                val accountValue = AccountFillPolicy.resolveAccountIdentifier(action.password, securityManager)
                if (accountValue.isNotBlank()) {
                    onCopy(autofillUsernameLabel, accountValue, false)
                } else {
                    android.widget.Toast.makeText(
                        context,
                        autofillCopyAccountUnavailable,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            is PasswordItemAction.CopyPassword -> {
                val decryptedPassword = AutofillSecretResolver.decryptPasswordOrNull(
                    securityManager = securityManager,
                    encryptedOrPlain = action.password.password,
                    logTag = "AutofillPickerV2",
                )
                if (!decryptedPassword.isNullOrBlank()) {
                    onCopy(autofillPasswordLabel, decryptedPassword, true)
                } else {
                    android.widget.Toast.makeText(
                        context,
                        autofillCopyPasswordUnavailable,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            is PasswordItemAction.SmartCopyUsernameFirst -> onSmartCopy(action.password, true)
            is PasswordItemAction.SmartCopyPasswordFirst -> onSmartCopy(action.password, false)
        }
    }

    var isAuthenticated by remember { mutableStateOf(canSkipVerification) }

    if (!isAuthenticated) {
        Surface(modifier = Modifier.fillMaxSize()) {
            PasswordVerificationContent(
                modifier = Modifier.fillMaxSize(),
                isFirstTime = false,
                disablePasswordVerification = false,
                biometricEnabled = biometricEnabled,
                persistVaultUnlockToSession = false,
                onVerifyPassword = { input -> securityManager.unlockVaultWithPassword(input) },
                onSuccess = {
                    isAuthenticated = true
                }
            )
        }
        return
    }

    LaunchedEffect(Unit) {
        runCatching {
            val savedSource = autofillPreferences.v2DefaultSourceFilter.first()
            sourceFilter = savedSource.toUiFilter()
            selectedKeePassDatabaseId = autofillPreferences.v2DefaultKeepassDatabaseId.first()
            selectedVaultId = autofillPreferences.v2DefaultBitwardenVaultId.first()
        }.onFailure {
            android.util.Log.w("AutofillPickerV2", "Failed to restore picker defaults: ${it.message}")
        }
    }

    fun persistPickerDefaults() {
        coroutineScope.launch {
            runCatching {
                autofillPreferences.setV2DefaultSourceFilter(sourceFilter.toPreferenceFilter())
                autofillPreferences.setV2DefaultKeepassDatabaseId(selectedKeePassDatabaseId)
                autofillPreferences.setV2DefaultBitwardenVaultId(selectedVaultId)
            }.onFailure {
                android.util.Log.w("AutofillPickerV2", "Failed to persist picker defaults: ${it.message}")
            }
        }
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
    val markNonAutofillTargetLabel = remember(args.webDomain, appName, args.applicationId) {
        args.webDomain?.takeIf { it.isNotBlank() }
            ?: appName?.takeIf { it.isNotBlank() }
            ?: args.applicationId?.takeIf { it.isNotBlank() }
            ?: ""
    }
    
    val bitwardenRepository = remember(context) { BitwardenRepository.getInstance(context) }

    LaunchedEffect(Unit) {
        AutofillLogger.i(
            "PICKER_UI",
            "Picker content mounted",
            metadata = mapOf(
                "manualMode" to isManualMode,
                "manualReason" to manualModeReason,
                "mainAppAuthenticated" to canSkipVerification,
                "iconCardsEnabled" to iconCardsEnabled,
                "responseAuth" to args.responseAuthMode,
                "idCount" to (args.autofillIds?.size ?: 0),
                "hintCount" to (args.autofillHints?.size ?: 0),
                "loginHintCount" to loginHintCount,
                "bankCardHintCount" to bankCardHintCount,
                "documentHintCount" to documentHintCount,
                "wantsPasswords" to requestProfile.wantsPasswords,
                "wantsBankCards" to requestProfile.wantsBankCards,
                "wantsDocuments" to requestProfile.wantsDocuments,
            )
        )
    }

    LaunchedEffect(
        isManualMode,
        canMarkAsNonAutofill,
        args.isSaveMode,
        args.fieldSignatureKey,
        args.applicationId,
        args.webDomain,
        appName,
        markNonAutofillTargetLabel,
    ) {
        AutofillLogger.i(
            "PICKER_UI",
            "Picker source diagnostics",
            metadata = mapOf(
                "manualMode" to isManualMode,
                "manualReason" to manualModeReason,
                "canMarkAsNonAutofill" to canMarkAsNonAutofill,
                "isSaveMode" to args.isSaveMode,
                "fieldSignaturePresent" to !args.fieldSignatureKey.isNullOrBlank(),
                "applicationId" to (args.applicationId ?: "none"),
                "webDomain" to (args.webDomain ?: "none"),
                "resolvedAppName" to (appName ?: "none"),
                "markTargetLabel" to if (markNonAutofillTargetLabel.isBlank()) "none" else markNonAutofillTargetLabel,
                "autofillIdCount" to (args.autofillIds?.size ?: 0),
            )
        )
    }

    // 加载密码
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        try {
            // 筛选建议密码
            val suggestedIds = args.suggestedPasswordIds?.toList() ?: emptyList()
            if (requestProfile.wantsPasswords && suggestedIds.isNotEmpty()) {
                suggestedPasswords = repository.getPasswordsByIds(suggestedIds)
            }
            if (requestProfile.wantsPasswords) {
                // 先完成全量数据读取，再切换到列表视图，避免首屏阶段性结构突变。
                allPasswords = repository.getAllPasswordEntries().first()
            }
            if (requestProfile.wantsBankCards) {
                allBankCards = secureItemRepository.getActiveItemsByType(ItemType.BANK_CARD).first()
            }
            if (requestProfile.wantsDocuments) {
                allDocuments = secureItemRepository.getActiveItemsByType(ItemType.DOCUMENT).first()
            }
            AutofillLogger.i(
                "PICKER_UI",
                "Picker data load complete",
                metadata = mapOf(
                    "suggestedRequested" to suggestedIds.size,
                    "suggestedLoaded" to suggestedPasswords.size,
                    "allLoaded" to allPasswords.size,
                    "bankCardsLoaded" to allBankCards.size,
                    "documentsLoaded" to allDocuments.size,
                    "elapsedMs" to (System.currentTimeMillis() - start)
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("AutofillPickerV2", "Error loading passwords", e)
            AutofillLogger.e(
                "PICKER_UI",
                "Picker data load failed",
                e,
                metadata = mapOf("elapsedMs" to (System.currentTimeMillis() - start))
            )
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(searchQuery) {
        if (!requestProfile.wantsPasswords) {
            searchedPasswords = emptyList()
            isSearchLoading = false
            return@LaunchedEffect
        }
        val query = searchQuery.trim()
        if (query.isBlank()) {
            searchedPasswords = emptyList()
            isSearchLoading = false
            return@LaunchedEffect
        }
        searchedPasswords = emptyList()
        isSearchLoading = true
        try {
            // 防抖，避免每次按键都触发数据库搜索。
            delay(260)
            searchedPasswords = withContext(Dispatchers.IO) {
                repository.searchPasswordEntries(query).first()
            }
        } catch (e: Exception) {
            android.util.Log.e("AutofillPickerV2", "Error searching passwords", e)
            searchedPasswords = emptyList()
        } finally {
            isSearchLoading = false
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
    val keepassGroupsForSelectedDb = remember(allPasswords, allBankCards, allDocuments, selectedKeePassDatabaseId) {
        sequenceOf(
            allPasswords.asSequence().map { Triple(it.keepassDatabaseId, it.keepassGroupPath, Unit) },
            allBankCards.asSequence().map { Triple(it.keepassDatabaseId, it.keepassGroupPath, Unit) },
            allDocuments.asSequence().map { Triple(it.keepassDatabaseId, it.keepassGroupPath, Unit) }
        )
            .flatten()
            .filter { it.first == selectedKeePassDatabaseId }
            .mapNotNull { it.second?.trim()?.takeIf { path -> path.isNotBlank() } }
            .distinct()
            .sorted()
            .toList()
    }
    val hasUncategorizedKeepassEntries = remember(allPasswords, allBankCards, allDocuments, selectedKeePassDatabaseId) {
        allPasswords.any { entry ->
            entry.keepassDatabaseId == selectedKeePassDatabaseId && entry.keepassGroupPath.isNullOrBlank()
        } || allBankCards.any { entry ->
            entry.keepassDatabaseId == selectedKeePassDatabaseId && entry.keepassGroupPath.isNullOrBlank()
        } || allDocuments.any { entry ->
            entry.keepassDatabaseId == selectedKeePassDatabaseId && entry.keepassGroupPath.isNullOrBlank()
        }
    }
    val hasUncategorizedBitwardenEntries = remember(allPasswords, allBankCards, allDocuments, selectedVaultId) {
        selectedVaultId != null && (
            allPasswords.any { entry ->
                entry.bitwardenVaultId == selectedVaultId && entry.bitwardenFolderId.isNullOrBlank()
            } || allBankCards.any { entry ->
                entry.bitwardenVaultId == selectedVaultId && entry.bitwardenFolderId.isNullOrBlank()
            } || allDocuments.any { entry ->
                entry.bitwardenVaultId == selectedVaultId && entry.bitwardenFolderId.isNullOrBlank()
            }
        )
    }

    val parsedBankCards = remember(allBankCards) { allBankCards.mapNotNull(::parseBankCardCandidate) }
    val parsedDocuments = remember(allDocuments) { allDocuments.mapNotNull(::parseDocumentCandidate) }

    val basePasswords = if (searchQuery.isBlank() || !requestProfile.wantsPasswords) allPasswords else searchedPasswords

    val sourceFilteredPasswords by remember(
        basePasswords,
        sourceFilter,
        selectedKeePassDatabaseId,
        selectedKeePassGroupPath,
        selectedVaultId,
        selectedFolderId
    ) {
        derivedStateOf {
            basePasswords.filter { entry ->
                when (sourceFilter) {
                    AutofillStorageSourceFilter.ALL -> true
                    AutofillStorageSourceFilter.LOCAL ->
                        entry.isLocalOnlyEntry()
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

    val filteredBankCards by remember(
        parsedBankCards,
        sourceFilter,
        selectedKeePassDatabaseId,
        selectedKeePassGroupPath,
        selectedVaultId,
        selectedFolderId,
        searchQuery
    ) {
        derivedStateOf {
            parsedBankCards.filter { (item, data) ->
                item.matchesAutofillSourceFilter(
                    sourceFilter = sourceFilter,
                    selectedKeePassDatabaseId = selectedKeePassDatabaseId,
                    selectedKeePassGroupPath = selectedKeePassGroupPath,
                    selectedVaultId = selectedVaultId,
                    selectedFolderId = selectedFolderId
                ) && (searchQuery.isBlank() || data.matchesAutofillSearch(searchQuery))
            }
        }
    }

    val filteredDocuments by remember(
        parsedDocuments,
        sourceFilter,
        selectedKeePassDatabaseId,
        selectedKeePassGroupPath,
        selectedVaultId,
        selectedFolderId,
        searchQuery
    ) {
        derivedStateOf {
            parsedDocuments.filter { (item, data) ->
                item.matchesAutofillSourceFilter(
                    sourceFilter = sourceFilter,
                    selectedKeePassDatabaseId = selectedKeePassDatabaseId,
                    selectedKeePassGroupPath = selectedKeePassGroupPath,
                    selectedVaultId = selectedVaultId,
                    selectedFolderId = selectedFolderId
                ) && (searchQuery.isBlank() || data.matchesAutofillSearch(searchQuery))
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

    LaunchedEffect(
        isLoading,
        isSearchLoading,
        sourceFilter,
        selectedKeePassDatabaseId,
        selectedKeePassGroupPath,
        selectedVaultId,
        selectedFolderId,
        searchQuery.length,
        allPasswords.size,
        allBankCards.size,
        allDocuments.size,
        suggestedPasswords.size,
        filteredPasswords.size,
        filteredSuggestedPasswords.size,
        filteredBankCards.size,
        filteredDocuments.size
    ) {
        AutofillLogger.d(
            "PICKER_UI",
            "UI state snapshot",
            metadata = mapOf(
                "isLoading" to isLoading,
                "isSearchLoading" to isSearchLoading,
                "queryLen" to searchQuery.length,
                "sourceFilter" to sourceFilter.name,
                "allCount" to allPasswords.size,
                "bankCardCount" to allBankCards.size,
                "documentCount" to allDocuments.size,
                "suggestedCount" to suggestedPasswords.size,
                "filteredCount" to filteredPasswords.size,
                "filteredSuggestedCount" to filteredSuggestedPasswords.size,
                "filteredBankCards" to filteredBankCards.size,
                "filteredDocuments" to filteredDocuments.size,
                "selectedKeePassDb" to (selectedKeePassDatabaseId ?: -1L),
                "selectedKeePassGroupSet" to (selectedKeePassGroupPath != null),
                "selectedVaultId" to (selectedVaultId ?: -1L),
                "selectedFolderSet" to (selectedFolderId != null)
            )
        )
    }

    val application = context.applicationContext as Application
    val autofillPasswordViewModel: PasswordViewModel = viewModel(
        factory = remember(repository, securityManager, secureItemRepository, customFieldRepository, appDb, context) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(PasswordViewModel::class.java)) {
                        return PasswordViewModel(
                            repository = repository,
                            securityManager = securityManager,
                            secureItemRepository = secureItemRepository,
                            customFieldRepository = customFieldRepository,
                            context = context.applicationContext,
                            localKeePassDatabaseDao = appDb.localKeePassDatabaseDao(),
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    )
    val autofillLocalKeePassViewModel: LocalKeePassViewModel = viewModel(
        factory = remember(appDb, securityManager, application) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(LocalKeePassViewModel::class.java)) {
                        return LocalKeePassViewModel(
                            application = application,
                            dao = appDb.localKeePassDatabaseDao(),
                            securityManager = securityManager,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    )
    val autofillBankCardViewModel: BankCardViewModel = viewModel(
        factory = remember(secureItemRepository, appDb, securityManager, context) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(BankCardViewModel::class.java)) {
                        return BankCardViewModel(
                            repository = secureItemRepository,
                            context = context.applicationContext,
                            localKeePassDatabaseDao = appDb.localKeePassDatabaseDao(),
                            securityManager = securityManager,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    )
    val autofillDocumentViewModel: DocumentViewModel = viewModel(
        factory = remember(secureItemRepository, appDb, securityManager, context) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(DocumentViewModel::class.java)) {
                        return DocumentViewModel(
                            repository = secureItemRepository,
                            context = context.applicationContext,
                            localKeePassDatabaseDao = appDb.localKeePassDatabaseDao(),
                            securityManager = securityManager,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    )
    val addMenuActions = remember(addTargets) {
        addTargets.map { target ->
            when (target) {
                AutofillAddTarget.PASSWORD -> AutofillFabMenuAction(
                    icon = Icons.Default.Lock,
                    labelRes = R.string.item_type_password,
                    onClick = { pendingAddTarget = AutofillAddTarget.PASSWORD; currentScreen = "add" }
                )
                AutofillAddTarget.DOCUMENT -> AutofillFabMenuAction(
                    icon = Icons.Default.Badge,
                    labelRes = R.string.item_type_document,
                    onClick = { pendingAddTarget = AutofillAddTarget.DOCUMENT; currentScreen = "add" }
                )
                AutofillAddTarget.BANK_CARD -> AutofillFabMenuAction(
                    icon = Icons.Default.CreditCard,
                    labelRes = R.string.item_type_bank_card,
                    onClick = { pendingAddTarget = AutofillAddTarget.BANK_CARD; currentScreen = "add" }
                )
            }
        }
    }
    val navigateBackToList: () -> Unit = {
        pendingAddTarget = null
        currentScreen = "list"
    }

    if (showMarkAsNonAutofillDialog && canMarkAsNonAutofill) {
        AlertDialog(
            onDismissRequest = { showMarkAsNonAutofillDialog = false },
            title = {
                Text(text = stringResource(R.string.autofill_mark_not_field_dialog_title))
            },
            text = {
                Text(
                    text = if (markNonAutofillTargetLabel.isNotBlank()) {
                        stringResource(
                            R.string.autofill_mark_not_field_dialog_message_with_target,
                            markNonAutofillTargetLabel,
                        )
                    } else {
                        stringResource(R.string.autofill_mark_not_field_dialog_message)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMarkAsNonAutofillDialog = false
                        onMarkAsNonAutofill()
                    }
                ) {
                    Text(text = stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMarkAsNonAutofillDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
    
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
                            showMarkButton = canMarkAsNonAutofill,
                            onMarkClick = { showMarkAsNonAutofillDialog = true },
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
                                        persistPickerDefaults()
                                    },
                                    onSelectKeePassDatabase = { databaseId ->
                                        selectedKeePassDatabaseId = databaseId
                                        selectedKeePassGroupPath = null
                                        persistPickerDefaults()
                                    },
                                    onSelectKeePassGroup = { groupPath ->
                                        selectedKeePassGroupPath = groupPath
                                    },
                                    onSelectVault = { vaultId ->
                                        selectedVaultId = vaultId
                                        selectedFolderId = null
                                        persistPickerDefaults()
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
                                        persistPickerDefaults()
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
                            if (requestProfile.wantsPasswords) {
                                val showSuggestedSection = searchQuery.isBlank() &&
                                    filteredSuggestedPasswords.isNotEmpty()
                                val suggestedIds = filteredSuggestedPasswords.map { it.id }.toSet()
                                val mainPasswords = if (showSuggestedSection) {
                                    filteredPasswords.filter { it.id !in suggestedIds }
                                } else {
                                    filteredPasswords
                                }
                                val displayedMainPasswords = mainPasswords
                                val showNoSuggestionsHint = sourceFilter == AutofillStorageSourceFilter.ALL &&
                                    selectedKeePassDatabaseId == null &&
                                    selectedKeePassGroupPath == null &&
                                    selectedVaultId == null &&
                                    selectedFolderId == null &&
                                    searchQuery.isBlank() &&
                                    filteredSuggestedPasswords.isEmpty()

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .weight(1f)
                                ) {
                                    if (isSearchLoading) {
                                        item(key = "search_loading") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                    }

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
                                                onAction = handlePasswordAction
                                            )
                                        }

                                        item {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant
                                            )
                                        }
                                    }

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
                                        items = displayedMainPasswords,
                                        key = { it.id }
                                    ) { password ->
                                        PasswordListItem(
                                            password = password,
                                            showDropdownMenu = true,
                                            iconCardsEnabled = iconCardsEnabled,
                                            showSmartCopyOptions = hasNotificationPermission,
                                            onAction = handlePasswordAction
                                        )
                                    }

                                    item {
                                        Spacer(modifier = Modifier.height(80.dp))
                                    }
                                }
                            } else {
                                val hasStructuredResults = filteredBankCards.isNotEmpty() || filteredDocuments.isNotEmpty()
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .weight(1f)
                                ) {
                                    if (requestProfile.wantsBankCards && filteredBankCards.isNotEmpty()) {
                                        item(key = "cards_header") {
                                            Text(
                                                text = stringResource(R.string.item_type_bank_card),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                            )
                                        }
                                        items(
                                            items = filteredBankCards,
                                            key = { (item, _) -> "card_${item.id}" }
                                        ) { (item, data) ->
                                            AutofillStructuredItemCard(
                                                title = bankCardDisplayTitle(item, data),
                                                subtitle = bankCardDisplaySubtitle(data),
                                                onClick = { onAutofillBankCard(item) }
                                            )
                                        }
                                    }

                                    if (requestProfile.wantsDocuments && filteredDocuments.isNotEmpty()) {
                                        item(key = "documents_header") {
                                            Text(
                                                text = stringResource(R.string.item_type_document),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                            )
                                        }
                                        items(
                                            items = filteredDocuments,
                                            key = { (item, _) -> "document_${item.id}" }
                                        ) { (item, data) ->
                                            AutofillStructuredItemCard(
                                                title = documentDisplayTitle(item, data),
                                                subtitle = documentDisplaySubtitle(data),
                                                onClick = { onAutofillDocument(item) }
                                            )
                                        }
                                    }

                                    if (!hasStructuredResults) {
                                        item {
                                            EmptyPasswordState(
                                                modifier = Modifier
                                                    .fillParentMaxSize()
                                                    .padding(top = 32.dp)
                                            )
                                        }
                                    }

                                    item {
                                        Spacer(modifier = Modifier.height(24.dp))
                                    }
                                }
                            }
                        }
                    }

                    if (addMenuActions.isNotEmpty()) {
                        AutofillFabMenu(
                            menuActions = addMenuActions,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        )
                    }
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
            val isWebFlow = !args.webDomain.isNullOrBlank()
            val initialTitle = when {
                isWebFlow -> args.webDomain.orEmpty()
                !appName.isNullOrBlank() -> appName
                else -> args.applicationId.orEmpty()
            }.orEmpty()
            when (pendingAddTarget ?: defaultAddTarget) {
                AutofillAddTarget.PASSWORD -> {
                    AddEditPasswordScreen(
                        viewModel = autofillPasswordViewModel,
                        localKeePassViewModel = autofillLocalKeePassViewModel,
                        passwordId = null,
                        initialDraft = AddEditPasswordInitialDraft(
                            title = initialTitle,
                            website = args.webDomain.orEmpty(),
                            username = args.capturedUsername.orEmpty(),
                            password = args.capturedPassword.orEmpty(),
                            appPackageName = if (isWebFlow) "" else args.applicationId.orEmpty(),
                            appName = if (isWebFlow) "" else appName.orEmpty(),
                        ),
                        forceShowAppBinding = true,
                        onSaveCompleted = { firstPasswordId ->
                            if (firstPasswordId == null) {
                                navigateBackToList()
                                return@AddEditPasswordScreen
                            }
                            coroutineScope.launch {
                                val savedEntry = repository.getPasswordEntryById(firstPasswordId)
                                if (savedEntry != null) {
                                    onAutofill(savedEntry, true)
                                } else {
                                    navigateBackToList()
                                }
                            }
                        },
                        onNavigateBack = navigateBackToList
                    )
                }
                AutofillAddTarget.DOCUMENT -> {
                    AddEditDocumentScreen(
                        viewModel = autofillDocumentViewModel,
                        documentId = null,
                        onNavigateBack = navigateBackToList
                    )
                }
                AutofillAddTarget.BANK_CARD -> {
                    AddEditBankCardScreen(
                        viewModel = autofillBankCardViewModel,
                        cardId = null,
                        onNavigateBack = navigateBackToList
                    )
                }
                null -> {
                    LaunchedEffect(Unit) {
                        navigateBackToList()
                    }
                }
            }
        }
        }
    }



}

@Composable
private fun AutofillStructuredItemCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title.ifBlank { "-" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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

private fun AutofillStorageSourceFilter.toPreferenceFilter(): AutofillPreferences.AutofillDefaultSourceFilter {
    return when (this) {
        AutofillStorageSourceFilter.ALL -> AutofillPreferences.AutofillDefaultSourceFilter.ALL
        AutofillStorageSourceFilter.LOCAL -> AutofillPreferences.AutofillDefaultSourceFilter.LOCAL
        AutofillStorageSourceFilter.KEEPASS -> AutofillPreferences.AutofillDefaultSourceFilter.KEEPASS
        AutofillStorageSourceFilter.BITWARDEN -> AutofillPreferences.AutofillDefaultSourceFilter.BITWARDEN
    }
}

private fun AutofillPreferences.AutofillDefaultSourceFilter.toUiFilter(): AutofillStorageSourceFilter {
    return when (this) {
        AutofillPreferences.AutofillDefaultSourceFilter.ALL -> AutofillStorageSourceFilter.ALL
        AutofillPreferences.AutofillDefaultSourceFilter.LOCAL -> AutofillStorageSourceFilter.LOCAL
        AutofillPreferences.AutofillDefaultSourceFilter.KEEPASS -> AutofillStorageSourceFilter.KEEPASS
        AutofillPreferences.AutofillDefaultSourceFilter.BITWARDEN -> AutofillStorageSourceFilter.BITWARDEN
    }
}

private data class AutofillFabMenuAction(
    val icon: ImageVector,
    val labelRes: Int,
    val onClick: () -> Unit,
)

@Composable
private fun AutofillFabMenu(
    menuActions: List<AutofillFabMenuAction>,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 28.dp else 16.dp,
        animationSpec = spring(),
        label = "autofill_fab_corner"
    )

    fun updateExpanded(next: Boolean) {
        isExpanded = next
    }

    BackHandler(enabled = isExpanded) {
        updateExpanded(false)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = tween(durationMillis = 120)),
            exit = fadeOut(animationSpec = tween(durationMillis = 90)),
            modifier = Modifier.matchParentSize()
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        updateExpanded(false)
                    }
            )
        }

        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            menuActions.forEachIndexed { index, action ->
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn(
                        animationSpec = tween(durationMillis = 160, delayMillis = index * 28)
                    ) + slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = tween(
                            durationMillis = 220,
                            delayMillis = index * 28,
                            easing = LinearEasing
                        )
                    ),
                    exit = fadeOut(animationSpec = tween(durationMillis = 90)) + slideOutVertically(
                        targetOffsetY = { it / 4 },
                        animationSpec = tween(durationMillis = 120)
                    )
                ) {
                    Surface(
                        onClick = {
                            updateExpanded(false)
                            action.onClick()
                        },
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        tonalElevation = 4.dp,
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(action.labelRes),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(animatedCornerRadius),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                onClick = { updateExpanded(!isExpanded) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = stringResource(R.string.add)
                    )
                }
            }
        }
    }
}

private enum class AutofillAddTarget {
    PASSWORD,
    DOCUMENT,
    BANK_CARD
}

private fun resolveAutofillAddTargets(
    requestProfile: AutofillPickerRequestProfile,
    loginHintCount: Int,
    bankCardHintCount: Int,
    documentHintCount: Int,
): List<AutofillAddTarget> {
    val requestedTargets = buildList {
        if (requestProfile.wantsPasswords) add(AutofillAddTarget.PASSWORD to loginHintCount)
        if (requestProfile.wantsDocuments) add(AutofillAddTarget.DOCUMENT to documentHintCount)
        if (requestProfile.wantsBankCards) add(AutofillAddTarget.BANK_CARD to bankCardHintCount)
    }
    if (requestedTargets.isEmpty()) return emptyList()

    fun priorityOf(target: AutofillAddTarget): Int = when (target) {
        AutofillAddTarget.PASSWORD -> 3
        AutofillAddTarget.DOCUMENT -> 2
        AutofillAddTarget.BANK_CARD -> 1
    }

    return requestedTargets.sortedWith(
        compareBy<Pair<AutofillAddTarget, Int>> { it.second }
            .thenBy { priorityOf(it.first) }
    ).map { it.first }
}

private const val KEEPASS_GROUP_UNCATEGORIZED = "__keepass_uncategorized__"
private const val BITWARDEN_FOLDER_UNCATEGORIZED = "__bitwarden_uncategorized__"

private fun SecureItem.matchesAutofillSourceFilter(
    sourceFilter: AutofillStorageSourceFilter,
    selectedKeePassDatabaseId: Long?,
    selectedKeePassGroupPath: String?,
    selectedVaultId: Long?,
    selectedFolderId: String?,
): Boolean {
    return when (sourceFilter) {
        AutofillStorageSourceFilter.ALL -> true
        AutofillStorageSourceFilter.LOCAL ->
            isLocalPasswordOwnership(keepassDatabaseId, bitwardenVaultId)
        AutofillStorageSourceFilter.KEEPASS -> {
            val keepassId = keepassDatabaseId
            keepassId != null &&
                (selectedKeePassDatabaseId == null || keepassId == selectedKeePassDatabaseId) &&
                when (selectedKeePassGroupPath) {
                    null -> true
                    KEEPASS_GROUP_UNCATEGORIZED -> keepassGroupPath.isNullOrBlank()
                    else -> keepassGroupPath == selectedKeePassGroupPath
                }
        }
        AutofillStorageSourceFilter.BITWARDEN -> {
            val vaultId = bitwardenVaultId
            vaultId != null &&
                (selectedVaultId == null || vaultId == selectedVaultId) &&
                when (selectedFolderId) {
                    null -> true
                    BITWARDEN_FOLDER_UNCATEGORIZED -> bitwardenFolderId.isNullOrBlank()
                    else -> bitwardenFolderId == selectedFolderId
                }
        }
    }
}

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
    showMarkButton: Boolean,
    onMarkClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
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
        if (showMarkButton) {
            FilledTonalIconButton(
                onClick = onMarkClick,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Block,
                    contentDescription = stringResource(R.string.autofill_mark_not_field_button)
                )
            }
        }
    }
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
    val triggerShape = RoundedCornerShape(22.dp)
    val panelShape = RoundedCornerShape(20.dp)
    val containerMotion = tween<IntSize>(
        durationMillis = 140,
        easing = LinearEasing
    )
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "filter_arrow_rotation"
    )
    val visibleChips = if (activeFilterCount > 0) chips.take(2) else emptyList()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = containerMotion),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .clip(triggerShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            shape = triggerShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = if (expanded) 2.dp else 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                    ) {
                        Icon(
                            imageVector = sourceFilter.icon(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(30.dp)
                                .padding(6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.filter_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
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
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(5.dp)
                                .graphicsLayer { rotationZ = arrowRotation }
                        )
                    }
                }

                if (visibleChips.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        visibleChips.forEach { chip ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = chip,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                        if (chips.size > visibleChips.size) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "+${chips.size - visibleChips.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (panelContent != null) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 150, easing = LinearEasing),
                    expandFrom = Alignment.Top
                ) + fadeIn(animationSpec = tween(durationMillis = 110, easing = LinearEasing)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 130, easing = LinearEasing),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(animationSpec = tween(durationMillis = 90, easing = LinearEasing))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = containerMotion),
                    shape = panelShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        shape = RoundedCornerShape(16.dp),
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
                            shape = RoundedCornerShape(16.dp),
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
                        shape = RoundedCornerShape(16.dp),
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
                            shape = RoundedCornerShape(16.dp),
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



