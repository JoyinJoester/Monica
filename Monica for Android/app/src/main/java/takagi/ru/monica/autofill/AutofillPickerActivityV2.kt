package takagi.ru.monica.autofill

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.autofill.Dataset
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.Parcelize
import takagi.ru.monica.R
import takagi.ru.monica.autofill.ui.*
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.ui.theme.MonicaTheme

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
class AutofillPickerActivityV2 : ComponentActivity() {
    
    companion object {
        private const val EXTRA_ARGS = "extra_args"
        
        fun getIntent(context: Context, args: Args): Intent {
            return Intent(context, AutofillPickerActivityV2::class.java).apply {
                putExtra(EXTRA_ARGS, args)
            }
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
        val suggestedPasswordIds: LongArray? = null,
        val isSaveMode: Boolean = false
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        val database = PasswordDatabase.getDatabase(applicationContext)
        val repository = PasswordRepository(database.passwordEntryDao())
        val localKeePassDao = database.localKeePassDatabaseDao()
        val securityManager = SecurityManager(applicationContext)
        val settingsManager = takagi.ru.monica.utils.SettingsManager(applicationContext)
        
        setContent {
            // 读取截图保护设置
            val settings by settingsManager.settingsFlow.collectAsState(
                initial = takagi.ru.monica.data.AppSettings()
            )
            
            // 根据用户设置应用截图保护
            takagi.ru.monica.utils.ScreenshotProtection(enabled = settings.screenshotProtectionEnabled)
            
            // 获取 KeePass 数据库列表
            val keepassDatabases by localKeePassDao.getAllDatabases().collectAsState(initial = emptyList())
            
            MonicaTheme {
                AutofillPickerContent(
                    args = args,
                    repository = repository,
                    securityManager = securityManager,
                    keepassDatabases = keepassDatabases,
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
                    }
                )
            }
        }
    }
        

    
    private fun handleAutofill(password: PasswordEntry, forceAddUri: Boolean) {
        val securityManager = SecurityManager(applicationContext)
        
        // 解密
        val decryptedUsername = try {
            if (password.username.contains("==") && password.username.length > 20) {
                securityManager.decryptData(password.username)
            } else {
                password.username
            }
        } catch (e: Exception) {
            password.username
        }
        
        val decryptedPassword = try {
            securityManager.decryptData(password.password)
        } catch (e: Exception) {
            ""
        }
        
        // 构建 Dataset
        val autofillIds = args.autofillIds
        if (autofillIds.isNullOrEmpty()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        
        val presentation = RemoteViews(packageName, R.layout.autofill_dataset_card).apply {
            setTextViewText(R.id.text_title, password.title.ifEmpty { decryptedUsername })
            setTextViewText(R.id.text_username, decryptedUsername)
            setImageViewResource(R.id.icon_app, R.drawable.ic_key)
        }
        
        val datasetBuilder = Dataset.Builder(presentation)
        
        autofillIds.forEachIndexed { index, autofillId ->
            val value = if (index % 2 == 0) decryptedUsername else decryptedPassword
            datasetBuilder.setValue(autofillId, AutofillValue.forText(value))
        }
        
        val resultIntent = Intent().apply {
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, datasetBuilder.build())
        }
        
        // 处理 URI 绑定
        if (forceAddUri) {
            saveUriBinding(password)
        }
        
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
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
            
            val message = if (label == "Password") "密码已复制" else "用户名已复制"
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
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
    onAutofill: (PasswordEntry, Boolean) -> Unit,
    onSaveNewPassword: suspend (PasswordEntry) -> Long,
    onCopy: (String, String, Boolean) -> Unit,
    onClose: () -> Unit
) {
    // 导航状态: "list", "detail", "add"
    var currentScreen by remember { mutableStateOf("list") }
    var selectedPassword by remember { mutableStateOf<PasswordEntry?>(null) }
    
    var allPasswords by remember { mutableStateOf<List<PasswordEntry>>(emptyList()) }
    var suggestedPasswords by remember { mutableStateOf<List<PasswordEntry>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    
    // 获取应用图标和名称
    val context = androidx.compose.ui.platform.LocalContext.current
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
            val title = if (args.isSaveMode) "Save form data" else "Autofill with Monica"
            
            AutofillScaffold(
                topBar = {
                    AutofillHeader(
                        title = title,
                        username = args.capturedUsername,
                        password = args.capturedPassword,
                        applicationId = args.applicationId,
                        webDomain = args.webDomain,
                        appIcon = appIcon,
                        appName = appName,
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
                                            text = "建议填充",
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
                                                        onCopy("Username", action.password.username, false)
                                                    }
                                                    is PasswordItemAction.CopyPassword -> {
                                                        val decryptedPassword = securityManager.decryptData(action.password.password)
                                                        onCopy("Password", decryptedPassword, true)
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
                                            text = if (suggestedPasswords.isNotEmpty()) "其他条目" else "所有条目",
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
                                                    onCopy("Username", action.password.username, false)
                                                }
                                                is PasswordItemAction.CopyPassword -> {
                                                    val decryptedPassword = securityManager.decryptData(action.password.password)
                                                    onCopy("Password", decryptedPassword, true)
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
                        text = { Text("新建") }
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
            text = "在此上下文中没有建议的项目",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
