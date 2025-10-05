package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.content.Intent
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.Language
import takagi.ru.monica.data.ThemeMode
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
    onClearAllData: () -> Unit = {},
    showTopBar: Boolean = true  // 添加参数控制是否显示顶栏
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var showClearDataDialog by remember { mutableStateOf(false) }
    var clearDataPasswordInput by remember { mutableStateOf("") }
    
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = if (showTopBar) {
            {
                TopAppBar(
                    title = { Text(context.getString(R.string.settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = context.getString(R.string.back))
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
            // Theme Settings
            SettingsSection(
                title = context.getString(R.string.theme)
            ) {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = context.getString(R.string.theme),
                    subtitle = getThemeDisplayName(settings.themeMode, context),
                    onClick = { showThemeDialog = true }
                )
            }
            
            // Language Settings
            SettingsSection(
                title = context.getString(R.string.language)
            ) {
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = context.getString(R.string.language),
                    subtitle = getLanguageDisplayName(settings.language, context),
                    onClick = { showLanguageDialog = true }
                )
            }
            
            // Security Settings
            SettingsSection(
                title = context.getString(R.string.security)
            ) {
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
                    title = context.getString(R.string.export_passwords),
                    subtitle = context.getString(R.string.export_passwords_description),
                    onClick = onExportData
                )
                
                SettingsItem(
                    icon = Icons.Default.Upload,
                    title = context.getString(R.string.import_passwords),
                    subtitle = context.getString(R.string.import_passwords_description),
                    onClick = onImportData
                )
                
                SettingsItem(
                    icon = Icons.Default.DeleteForever,
                    title = "清空所有数据",
                    subtitle = "删除所有密码、验证器、银行卡和证件数据",
                    onClick = { showClearDataDialog = true },
                    iconTint = MaterialTheme.colorScheme.error
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
                    subtitle = "1.0.0",
                    onClick = { /* Show version info */ }
                )
            }
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
    
    // Clear All Data Confirmation Dialog with Password
    if (showClearDataDialog) {
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
                Text("确认清空所有数据?")
            },
            text = {
                Column {
                    Text(
                        "此操作将永久删除所有密码、验证器、银行卡和证件数据,且无法恢复。\n\n建议在清空前先导出备份!",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = clearDataPasswordInput,
                        onValueChange = { clearDataPasswordInput = it },
                        label = { Text("请输入主密码确认") },
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
                                onClearAllData()
                                android.widget.Toast.makeText(
                                    context,
                                    "正在清空数据...",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                // 密码错误
                                android.util.Log.d("SettingsScreen", "Password verification failed")
                                android.widget.Toast.makeText(
                                    context,
                                    "主密码错误",
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
                    Text("确认清空")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showClearDataDialog = false
                        clearDataPasswordInput = ""
                    }
                ) {
                    Text("取消")
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