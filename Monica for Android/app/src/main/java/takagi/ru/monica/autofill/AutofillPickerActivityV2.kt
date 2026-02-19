package takagi.ru.monica.autofill

import android.app.Activity
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
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.Parcelize
import takagi.ru.monica.R
import takagi.ru.monica.autofill.ui.*
import takagi.ru.monica.autofill.utils.SmartCopyNotificationHelper
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.utils.SettingsManager
import androidx.compose.foundation.isSystemInDarkTheme
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.ui.components.PasswordVerificationContent
import takagi.ru.monica.ui.base.BaseMonicaActivity
import takagi.ru.monica.security.SessionManager

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
    }
    
    @Parcelize
    data class Args(
        val applicationId: String? = null,
        val webDomain: String? = null,
        val webScheme: String? = null,
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
                dynamicColor = settings.dynamicColorEnabled,
                colorScheme = settings.colorScheme,
                customPrimaryColor = settings.customPrimaryColor,
                customSecondaryColor = settings.customSecondaryColor,
                customTertiaryColor = settings.customTertiaryColor
            ) {
                AutofillPickerContent(
                    args = args,
                    repository = repository,
                    securityManager = securityManager,
                    settingsManager = localSettingsManager,
                    keepassDatabases = keepassDatabases,
                    canSkipVerification = canSkipAuth,
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
        
        val datasetBuilder = Dataset.Builder()

        val hints = args.autofillHints
        val hasUsernameHint = hints?.contains(EnhancedAutofillStructureParserV2.FieldHint.USERNAME.name) == true
        val hasEmailHint = hints?.contains(EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS.name) == true
        val allowAccountInEmailField = fillEmailWithAccount || accountValue.contains("@") || (!hasUsernameHint && hasEmailHint)
        var filledCount = 0
        autofillIds.forEachIndexed { index, autofillId ->
            val hint = hints?.getOrNull(index)
            val value = when (hint) {
                EnhancedAutofillStructureParserV2.FieldHint.USERNAME.name -> accountValue
                EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS.name ->
                    if (allowAccountInEmailField) accountValue else null
                EnhancedAutofillStructureParserV2.FieldHint.PASSWORD.name,
                EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD.name -> decryptedPassword
                else -> null
            }
            if (value != null) {
                datasetBuilder.setValue(autofillId, AutofillValue.forText(value))
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
                }
            }
        }

        if (filledCount == 0) {
            android.util.Log.w("AutofillPickerV2", "No credential value resolved after controlled fallback")
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
        
        // 处理 URI 绑定
        if (forceAddUri) {
            saveUriBinding(password)
        }

        rememberLearnedFieldSignature()
        
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
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
    settingsManager: SettingsManager,
    keepassDatabases: List<LocalKeePassDatabase>,
    canSkipVerification: Boolean = false,
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
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    
    // 检查通知权限 - 用于决定是否显示智能复制选项
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasNotificationPermission = remember {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    
    // 读取自动填充验证设置
    val appSettingsState = settingsManager.settingsFlow.collectAsState(initial = null)
    val appSettings = appSettingsState.value
    val autofillUsernameLabel = stringResource(R.string.autofill_username)
    val autofillPasswordLabel = stringResource(R.string.autofill_password)
    
    if (appSettings == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    
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
                biometricEnabled = appSettings.biometricEnabled,
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
    
    // 加载密码
    LaunchedEffect(Unit) {
        try {
            allPasswords = repository.getAllPasswordEntries().first()
            
            // 筛选建议密码
            val suggestedIds = args.suggestedPasswordIds?.toList() ?: emptyList()
            if (suggestedIds.isNotEmpty()) {
                suggestedPasswords = repository.getPasswordsByIds(suggestedIds)
            }
        } catch (e: Exception) {
            android.util.Log.e("AutofillPickerV2", "Error loading passwords", e)
        } finally {
            isLoading = false
        }
    }
    
    // 过滤密码
    val filteredPasswords by remember(allPasswords, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                allPasswords
            } else {
                allPasswords.filter { password ->
                    password.title.contains(searchQuery, ignoreCase = true) ||
                    password.username.contains(searchQuery, ignoreCase = true) ||
                    password.website.contains(searchQuery, ignoreCase = true)
                }
            }
        }
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
                        // 搜索栏
                        AutofillSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        
                        if (isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            // 密码列表
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f)
                            ) {
                                // 建议密码区域（高亮）
                                if (suggestedPasswords.isNotEmpty() && searchQuery.isBlank()) {
                                    item {
                                        Text(
                                            text = stringResource(R.string.autofill_suggested_fill),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                    
                                    items(
                                        items = suggestedPasswords,
                                        key = { "suggested_${it.id}" }
                                    ) { password ->
                                        SuggestedPasswordListItem(
                                            password = password,
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
                                if (suggestedPasswords.isEmpty() && searchQuery.isBlank()) {
                                    item {
                                        NoSuggestionsHint()
                                    }
                                }
                                
                                // 所有条目标题
                                if (filteredPasswords.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = if (suggestedPasswords.isNotEmpty()) {
                                                stringResource(R.string.autofill_other_entries)
                                            } else {
                                                stringResource(R.string.autofill_all_entries)
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                                
                                // 过滤掉已在建议中显示的密码
                                val suggestedIds = suggestedPasswords.map { it.id }.toSet()
                                val otherPasswords = filteredPasswords.filter { it.id !in suggestedIds }
                                
                                items(
                                    items = otherPasswords,
                                    key = { it.id }
                                ) { password ->
                                    PasswordListItem(
                                        password = password,
                                        showDropdownMenu = true,
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

