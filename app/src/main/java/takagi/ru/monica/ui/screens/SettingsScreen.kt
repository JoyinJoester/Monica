package takagi.ru.monica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.BottomNavContentTab
import takagi.ru.monica.data.Language
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.utils.BiometricAuthHelper
import takagi.ru.monica.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onResetPassword: () -> Unit,
    onSecurityQuestions: () -> Unit,
    onSupportAuthor: () -> Unit,
    onExportData: () -> Unit = {},
    onImportData: () -> Unit = {},
    onClearAllData: (Boolean, Boolean, Boolean, Boolean) -> Unit = { _, _, _, _ -> },
    onNavigateToWebDav: () -> Unit = {},
    onNavigateToAutofill: () -> Unit = {},
    onNavigateToBottomNavSettings: () -> Unit = {},
    onNavigateToColorScheme: () -> Unit = {},
    onSecurityAnalysis: () -> Unit = {},
    onNavigateToDeveloperSettings: () -> Unit = {},
    showTopBar: Boolean = true  // 添加参数控制是否显示顶栏
) {
    val context = LocalContext.current
    
    // 直接使用 LocalContext.current as? ComponentActivity 获取 Activity
    val activity = context as? FragmentActivity
    android.util.Log.d("SettingsScreen", "Activity: $activity (type: ${context.javaClass.name})")
    
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var showClearDataDialog by remember { mutableStateOf(false) }
    var clearDataPasswordInput by remember { mutableStateOf("") }
    
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDeveloperVerifyDialog by remember { mutableStateOf(false) }
    var developerPasswordInput by remember { mutableStateOf("") }
    
    // 生物识别帮助类
    val biometricHelper = remember(context) { BiometricAuthHelper(context) }
    val isBiometricAvailable = remember(biometricHelper) { 
        val available = biometricHelper.isBiometricAvailable()
        android.util.Log.d("SettingsScreen", "Biometric available: $available")
        android.util.Log.d("SettingsScreen", "Activity: $activity")
        available
    }
    
    // 使用本地状态跟踪生物识别开关,避免验证失败时状态不一致
    var biometricSwitchState by remember(settings.biometricEnabled) { 
        mutableStateOf(settings.biometricEnabled) 
    }
    
    Scaffold(
        topBar = if (showTopBar) {
            {
                TopAppBar(
                    title = { Text(context.getString(R.string.settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = context.getString(R.string.back))
                        }
                    },
                    actions = {
                        // 安全分析图标
                        IconButton(onClick = onSecurityAnalysis) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = context.getString(R.string.security_analysis),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        } else {
            {}
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            // 安全分析入口卡片 - 置顶显示
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable { onSecurityAnalysis() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = context.getString(R.string.security_analysis),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = context.getString(R.string.security_analysis_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Security Settings
            SettingsSection(
                title = context.getString(R.string.security)
            ) {
                // 生物识别开关
                SettingsItemWithSwitch(
                    icon = Icons.Default.Fingerprint,
                    title = context.getString(R.string.biometric_unlock),
                    subtitle = if (isBiometricAvailable) {
                        if (biometricSwitchState)
                            context.getString(R.string.biometric_unlock_enabled)
                        else
                            context.getString(R.string.biometric_unlock_disabled)
                    } else {
                        biometricHelper.getBiometricStatusMessage()
                    },
                    checked = biometricSwitchState,
                    enabled = isBiometricAvailable,
                    onCheckedChange = { newState ->
                        android.util.Log.d("SettingsScreen", "Switch clicked: newState=$newState, activity=$activity")
                        if (newState) {
                            // 用户想启用指纹解锁
                            if (activity != null) {
                                android.util.Log.d("SettingsScreen", "Starting biometric authentication...")
                                // 需要先验证指纹
                                biometricHelper.authenticate(
                                    activity = activity,
                                    title = context.getString(R.string.biometric_login_title),
                                    subtitle = "验证指纹以启用指纹解锁",
                                    description = context.getString(R.string.biometric_login_description),
                                    negativeButtonText = context.getString(R.string.cancel),
                                    onSuccess = {
                                        // 验证成功,启用指纹解锁
                                        android.util.Log.d("SettingsScreen", "Biometric authentication SUCCESS")
                                        biometricSwitchState = true
                                        viewModel.updateBiometricEnabled(true)
                                        Toast.makeText(
                                            context,
                                            "指纹解锁已启用",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onError = { errorCode, errorMsg ->
                                        // 验证失败,保持关闭状态
                                        android.util.Log.e("SettingsScreen", "Biometric authentication ERROR: code=$errorCode, msg=$errorMsg")
                                        biometricSwitchState = false
                                        Toast.makeText(
                                            context,
                                            "指纹验证失败: $errorMsg",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onCancel = {
                                        // 用户取消,保持关闭状态
                                        android.util.Log.d("SettingsScreen", "Biometric authentication CANCELLED")
                                        biometricSwitchState = false
                                        Toast.makeText(
                                            context,
                                            "已取消",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            } else {
                                // activity 为空,无法验证,恢复开关状态
                                android.util.Log.e("SettingsScreen", "Activity is NULL! Cannot authenticate")
                                biometricSwitchState = false
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.biometric_cannot_enable),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            // 用户想禁用指纹解锁,直接禁用不需要验证
                            android.util.Log.d("SettingsScreen", "Disabling biometric unlock")
                            biometricSwitchState = false
                            viewModel.updateBiometricEnabled(false)
                            Toast.makeText(
                                context,
                                context.getString(R.string.biometric_unlock_disabled),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
                
                SettingsItem(
                    icon = Icons.Default.Security,
                    title = context.getString(R.string.screenshot_protection),
                    subtitle = if (settings.screenshotProtectionEnabled) 
                        context.getString(R.string.screenshot_protection_enabled)
                    else 
                        context.getString(R.string.screenshot_protection_disabled),
                    onClick = { 
                        viewModel.updateScreenshotProtectionEnabled(!settings.screenshotProtectionEnabled)
                    }
                )
                
                SettingsItem(
                    icon = Icons.Default.Security,
                    title = context.getString(R.string.security_questions),
                    subtitle = context.getString(R.string.security_questions_description),
                    onClick = onSecurityQuestions
                )
                
                SettingsItem(
                    icon = Icons.Default.VpnKey,
                    title = context.getString(R.string.reset_master_password),
                    subtitle = context.getString(R.string.reset_password_description),
                    onClick = onResetPassword
                )
            }
            
            // Data Management Settings
            SettingsSection(
                title = context.getString(R.string.data_management)
            ) {
                SettingsItem(
                    icon = Icons.Default.Download,
                    title = context.getString(R.string.export_data),
                    subtitle = context.getString(R.string.export_data_description),
                    onClick = onExportData
                )
                
                SettingsItem(
                    icon = Icons.Default.Upload,
                    title = context.getString(R.string.import_data),
                    subtitle = context.getString(R.string.import_data_description),
                    onClick = onImportData
                )
                
                SettingsItem(
                    icon = Icons.Default.Cloud,
                    title = context.getString(R.string.webdav_backup),
                    subtitle = context.getString(R.string.webdav_backup_description),
                    onClick = onNavigateToWebDav
                )
                
                SettingsItem(
                    icon = Icons.Default.VpnKey,
                    title = context.getString(R.string.autofill),
                    subtitle = context.getString(R.string.autofill_subtitle),
                    onClick = onNavigateToAutofill
                )
                
                SettingsItem(
                    icon = Icons.Default.DeleteForever,
                    title = context.getString(R.string.clear_all_data),
                    subtitle = context.getString(R.string.clear_all_data_subtitle),
                    onClick = { showClearDataDialog = true },
                    iconTint = MaterialTheme.colorScheme.error
                )
            }
            
            // Appearance Settings (原Theme Settings)
            SettingsSection(
                title = context.getString(R.string.theme)  // 现在显示为"外观"
            ) {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = context.getString(R.string.theme),
                    subtitle = getThemeDisplayName(settings.themeMode, context),
                    onClick = { showThemeDialog = true }
                )
                
                SettingsItem(
                    icon = Icons.Default.Colorize,
                    title = context.getString(R.string.color_scheme),
                    subtitle = getColorSchemeDisplayName(settings.colorScheme, context),
                    onClick = { onNavigateToColorScheme() }
                )
                
                // 移入的设置项：
                // 1. 语言设置
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = context.getString(R.string.language),
                    subtitle = getLanguageDisplayName(settings.language, context),
                    onClick = { showLanguageDialog = true }
                )
                
                // 2. 底部导航栏设置
                SettingsItem(
                    icon = Icons.Default.ViewWeek,
                    title = context.getString(R.string.bottom_nav_settings),
                    subtitle = context.getString(R.string.bottom_nav_settings_entry_subtitle),
                    onClick = onNavigateToBottomNavSettings
                )
                
                // 3. 关闭壁纸取色设置
                SettingsItemWithSwitch(
                    icon = Icons.Default.Colorize,
                    title = context.getString(R.string.disable_wallpaper_color_extraction),
                    subtitle = context.getString(R.string.disable_wallpaper_color_extraction_description),
                    checked = !settings.dynamicColorEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.updateDynamicColorEnabled(!enabled)
                    }
                )
            }
            
            // About Settings
            SettingsSection(
                title = context.getString(R.string.about)
            ) {
                SettingsItem(
                    icon = Icons.Default.Favorite,
                    title = context.getString(R.string.support_author),
                    subtitle = context.getString(R.string.support_author_subtitle),
                    onClick = onSupportAuthor
                )
                
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = context.getString(R.string.version),
                    subtitle = context.getString(R.string.settings_version_number),
                    onClick = {
                        // 打开 GitHub 仓库链接
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/JoyinJoester/Monica"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.cannot_open_browser),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
            
            // 开发者设置入口
            SettingsSection(
                title = "开发者选项"
            ) {
                SettingsItem(
                    icon = Icons.Default.Code,
                    title = "开发者设置",
                    subtitle = "日志查看、开发者调试工具",
                    onClick = {
                        val hasActivity = activity != null
                        val biometricAvailableNow = hasActivity && biometricHelper.isBiometricAvailable()
                        android.util.Log.d(
                            "SettingsScreen",
                            "Developer settings tapped. hasActivity=$hasActivity, biometricAvailable=$biometricAvailableNow"
                        )

                        developerPasswordInput = ""
                        showDeveloperVerifyDialog = false

                        when {
                            !hasActivity -> {
                                android.util.Log.w(
                                    "SettingsScreen",
                                    "Cannot start biometric auth: FragmentActivity context missing"
                                )
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.use_master_password),
                                    Toast.LENGTH_SHORT
                                ).show()
                                showDeveloperVerifyDialog = true
                            }
                            biometricAvailableNow -> {
                                biometricHelper.authenticate(
                                    activity = activity!!,
                                    title = context.getString(R.string.biometric_login_title),
                                    subtitle = context.getString(R.string.biometric_login_subtitle),
                                    description = context.getString(R.string.biometric_login_description),
                                    negativeButtonText = context.getString(R.string.use_master_password),
                                    onSuccess = {
                                        android.util.Log.d(
                                            "SettingsScreen",
                                            "Developer biometric authentication succeeded"
                                        )
                                        showDeveloperVerifyDialog = false
                                        developerPasswordInput = ""
                                        onNavigateToDeveloperSettings()
                                    },
                                    onError = { errorCode, errorMessage ->
                                        android.util.Log.w(
                                            "SettingsScreen",
                                            "Developer biometric error: code=$errorCode, message=$errorMessage"
                                        )
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.biometric_auth_error, errorMessage),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        showDeveloperVerifyDialog = true
                                    },
                                    onCancel = {
                                        android.util.Log.d(
                                            "SettingsScreen",
                                            "Developer biometric canceled by user"
                                        )
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.use_master_password),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        showDeveloperVerifyDialog = true
                                    }
                                )
                            }
                            else -> {
                                android.util.Log.d(
                                    "SettingsScreen",
                                    "Biometric unavailable, showing password dialog for developer settings"
                                )
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.biometric_not_available),
                                    Toast.LENGTH_SHORT
                                ).show()
                                showDeveloperVerifyDialog = true
                            }
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // Theme Selection Dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = settings.themeMode,
            onThemeSelected = { theme ->
                viewModel.updateThemeMode(theme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
    
    // Language Selection Dialog
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = settings.language,
            onLanguageSelected = { language ->
                coroutineScope.launch {
                    viewModel.updateLanguage(language)
                    showLanguageDialog = false
                    // 等待DataStore保存完成
                    delay(200)
                    // Restart activity to apply language change
                    if (context is Activity) {
                        context.recreate()
                    }
                }
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
    
    // Developer Settings Verification Dialog
    if (showDeveloperVerifyDialog) {
        AlertDialog(
            onDismissRequest = { 
                showDeveloperVerifyDialog = false
                developerPasswordInput = ""
            },
            icon = {
                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text("验证身份")
            },
            text = {
                Column {
                    Text(
                        "访问开发者设置需要验证主密码",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = developerPasswordInput,
                        onValueChange = { developerPasswordInput = it },
                        label = { Text("主密码") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            val securityManager = takagi.ru.monica.security.SecurityManager(context)
                            if (securityManager.verifyMasterPassword(developerPasswordInput)) {
                                showDeveloperVerifyDialog = false
                                developerPasswordInput = ""
                                onNavigateToDeveloperSettings()
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "密码错误",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                developerPasswordInput = ""
                            }
                        }
                    },
                    enabled = developerPasswordInput.isNotEmpty()
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showDeveloperVerifyDialog = false
                        developerPasswordInput = ""
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
    
    // Clear All Data Confirmation Dialog with Password and Options
    if (showClearDataDialog) {
        var clearPasswords by remember { mutableStateOf(true) }
        var clearTotp by remember { mutableStateOf(true) }
        var clearDocuments by remember { mutableStateOf(true) }
        var clearBankCards by remember { mutableStateOf(true) }
        
        AlertDialog(
            onDismissRequest = { 
                showClearDataDialog = false
                clearDataPasswordInput = ""
            },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(context.getString(R.string.clear_all_data_confirm))
            },
            text = {
                Column {
                    Text(
                        context.getString(R.string.clear_all_data_warning),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 数据类型选择复选框
                    Text(
                        context.getString(R.string.select_data_types_to_clear),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    CheckboxRow(
                        checked = clearPasswords,
                        onCheckedChange = { clearPasswords = it },
                        label = context.getString(R.string.data_type_passwords)
                    )
                    
                    CheckboxRow(
                        checked = clearTotp,
                        onCheckedChange = { clearTotp = it },
                        label = context.getString(R.string.data_type_totp)
                    )
                    
                    CheckboxRow(
                        checked = clearDocuments,
                        onCheckedChange = { clearDocuments = it },
                        label = context.getString(R.string.data_type_documents)
                    )
                    
                    CheckboxRow(
                        checked = clearBankCards,
                        onCheckedChange = { clearBankCards = it },
                        label = context.getString(R.string.data_type_bank_cards)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = clearDataPasswordInput,
                        onValueChange = { clearDataPasswordInput = it },
                        label = { Text(context.getString(R.string.enter_master_password_to_confirm)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        android.util.Log.d("SettingsScreen", "Confirm button clicked, starting verification")
                        coroutineScope.launch {
                            android.util.Log.d("SettingsScreen", "Verifying password: ${clearDataPasswordInput.isNotEmpty()}")
                            val securityManager = takagi.ru.monica.security.SecurityManager(context)
                            if (securityManager.verifyMasterPassword(clearDataPasswordInput)) {
                                android.util.Log.d("SettingsScreen", "Password verified, calling onClearAllData")
                                showClearDataDialog = false
                                clearDataPasswordInput = ""
                                onClearAllData(
                                    clearPasswords,
                                    clearTotp,
                                    clearDocuments,
                                    clearBankCards
                                )
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.clearing_data),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                // 密码错误
                                android.util.Log.d("SettingsScreen", "Password verification failed")
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.password_incorrect),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                clearDataPasswordInput = ""
                                // 保持对话框打开,让用户重试
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = clearDataPasswordInput.isNotEmpty()
                ) {
                    Text(context.getString(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showClearDataDialog = false
                        clearDataPasswordInput = ""
                    }
                ) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
        )
        content()
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 带开关的设置项组件
 */
@Composable
fun SettingsItemWithSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.38f),
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) {
                        LocalContentColor.current
                    } else {
                        LocalContentColor.current.copy(alpha = 0.38f)
                    }
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    }
                )
            }
            
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

/**
 * 带复选框的行组件
 */
@Composable
fun CheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun BottomNavConfigRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    switchEnabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = switchEnabled
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = stringResource(R.string.bottom_nav_move_up)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = stringResource(R.string.bottom_nav_move_down)
                    )
                }
            }
        }
    }
}

private fun BottomNavContentTab.toIcon(): ImageVector = when (this) {
    BottomNavContentTab.PASSWORDS -> Icons.Default.Lock
    BottomNavContentTab.AUTHENTICATOR -> Icons.Default.Security
    BottomNavContentTab.DOCUMENTS -> Icons.Default.Description
    BottomNavContentTab.BANK_CARDS -> Icons.Default.CreditCard
    BottomNavContentTab.GENERATOR -> Icons.Default.AutoAwesome  // 添加生成器图标
}

private fun BottomNavContentTab.toLabelRes(): Int = when (this) {
    BottomNavContentTab.PASSWORDS -> R.string.nav_passwords
    BottomNavContentTab.AUTHENTICATOR -> R.string.nav_authenticator
    BottomNavContentTab.DOCUMENTS -> R.string.nav_documents
    BottomNavContentTab.BANK_CARDS -> R.string.nav_bank_cards
    BottomNavContentTab.GENERATOR -> R.string.nav_generator  // 添加生成器标签
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.theme)) },
        text = {
            Column {
                ThemeMode.values().forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = theme == currentTheme,
                            onClick = { onThemeSelected(theme) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(getThemeDisplayName(theme, context))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.ok))
            }
        }
    )
}

@Composable
fun LanguageSelectionDialog(
    currentLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.language)) },
        text = {
            Column {
                Language.values().forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = language == currentLanguage,
                            onClick = { onLanguageSelected(language) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(getLanguageDisplayName(language, context))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.ok))
            }
        }
    )
}

@Composable
fun AutoLockSelectionDialog(
    currentMinutes: Int,
    onMinutesSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val options = listOf(1, 5, 15, 30, -1) // 删除了 0 (立即锁定), -1 represents "Never"
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.auto_lock)) },
        text = {
            Column {
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = minutes == currentMinutes,
                            onClick = { onMinutesSelected(minutes) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(getAutoLockDisplayName(minutes, context))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.ok))
            }
        }
    )
}

// Helper functions
private fun getThemeDisplayName(theme: ThemeMode, context: android.content.Context): String {
    return when (theme) {
        ThemeMode.SYSTEM -> context.getString(R.string.theme_system)
        ThemeMode.LIGHT -> context.getString(R.string.theme_light)
        ThemeMode.DARK -> context.getString(R.string.theme_dark)
    }
}

private fun getLanguageDisplayName(language: Language, context: android.content.Context): String {
    return when (language) {
        Language.SYSTEM -> context.getString(R.string.language_system)
        Language.ENGLISH -> context.getString(R.string.language_english)
        Language.CHINESE -> context.getString(R.string.language_chinese)
        Language.VIETNAMESE -> context.getString(R.string.language_vietnamese)
        Language.JAPANESE -> context.getString(R.string.language_japanese)
    }
}

private fun getAutoLockDisplayName(minutes: Int, context: android.content.Context): String {
    return when (minutes) {
        0 -> context.getString(R.string.auto_lock_immediately)
        1 -> context.getString(R.string.auto_lock_1_minute)
        5 -> context.getString(R.string.auto_lock_5_minutes)
        15 -> context.getString(R.string.auto_lock_15_minutes)
        30 -> context.getString(R.string.auto_lock_30_minutes)
        -1 -> context.getString(R.string.auto_lock_never)
        else -> "$minutes ${context.getString(R.string.auto_lock_5_minutes).substringAfter("5")}"
    }
}

private fun getColorSchemeDisplayName(colorScheme: takagi.ru.monica.data.ColorScheme, context: android.content.Context): String {
    return when (colorScheme) {
        takagi.ru.monica.data.ColorScheme.DEFAULT -> context.getString(R.string.default_color_scheme)
        takagi.ru.monica.data.ColorScheme.OCEAN_BLUE -> context.getString(R.string.ocean_blue_scheme)
        takagi.ru.monica.data.ColorScheme.SUNSET_ORANGE -> context.getString(R.string.sunset_orange_scheme)
        takagi.ru.monica.data.ColorScheme.FOREST_GREEN -> context.getString(R.string.forest_green_scheme)
        takagi.ru.monica.data.ColorScheme.TECH_PURPLE -> context.getString(R.string.tech_purple_scheme)
        takagi.ru.monica.data.ColorScheme.BLACK_MAMBA -> context.getString(R.string.black_mamba_scheme)
        takagi.ru.monica.data.ColorScheme.GREY_STYLE -> context.getString(R.string.grey_style_scheme)
        takagi.ru.monica.data.ColorScheme.CUSTOM -> context.getString(R.string.custom_color_scheme)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val bottomNavVisibility = settings.bottomNavVisibility
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = context.getString(R.string.bottom_nav_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = context.getString(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = context.getString(R.string.bottom_nav_reorder_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            )

            val bottomNavOrder = settings.bottomNavOrder
            val visibleCount = bottomNavVisibility.visibleCount()
            bottomNavOrder.forEachIndexed { index, tab ->
                val isVisible = bottomNavVisibility.isVisible(tab)
                val switchEnabled = !isVisible || visibleCount > 1
                BottomNavConfigRow(
                    icon = tab.toIcon(),
                    title = context.getString(tab.toLabelRes()),
                    subtitle = context.getString(R.string.bottom_nav_toggle_subtitle),
                    checked = isVisible,
                    switchEnabled = switchEnabled,
                    canMoveUp = index > 0,
                    canMoveDown = index < bottomNavOrder.lastIndex,
                    onCheckedChange = { checked ->
                        viewModel.updateBottomNavVisibility(tab, checked)
                    },
                    onMoveUp = {
                        if (index > 0) {
                            val newOrder = bottomNavOrder.toMutableList().apply {
                                add(index - 1, removeAt(index))
                            }
                            viewModel.updateBottomNavOrder(newOrder)
                        }
                    },
                    onMoveDown = {
                        if (index < bottomNavOrder.lastIndex) {
                            val newOrder = bottomNavOrder.toMutableList().apply {
                                add(index + 1, removeAt(index))
                            }
                            viewModel.updateBottomNavOrder(newOrder)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

