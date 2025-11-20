package takagi.ru.monica.wear.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import takagi.ru.monica.wear.R
import takagi.ru.monica.wear.security.WearSecurityManager
import takagi.ru.monica.wear.ui.components.ChangeLockDialog
import takagi.ru.monica.wear.viewmodel.ColorScheme as WearColorScheme
import takagi.ru.monica.wear.viewmodel.SettingsViewModel
import takagi.ru.monica.wear.viewmodel.SyncState
import java.text.SimpleDateFormat
import java.util.*

// Theme colors are now defined in ui/theme/Color.kt and applied in MainActivity via MonicaWearTheme

/**
 * 设置界面 - Material 3 设计（深色主题）
 * 包含WebDAV配置、同步、安全设置等
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    securityManager: takagi.ru.monica.wear.security.WearSecurityManager? = null,
    onBack: () -> Unit,
    onLanguageChanged: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val syncState by viewModel.syncState.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val isWebDavConfigured by viewModel.isWebDavConfigured.collectAsState()
    val currentColorScheme: WearColorScheme by viewModel.currentColorScheme.collectAsState()
    val useOledBlack by viewModel.useOledBlack.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    
    var showWebDavDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showChangeLockDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAddTotpDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    
    SettingsScreenContent(
        scrollState = scrollState,
        syncState = syncState,
        lastSyncTime = lastSyncTime,
        isWebDavConfigured = isWebDavConfigured,
        currentColorScheme = currentColorScheme,
        useOledBlack = useOledBlack,
        currentLanguage = currentLanguage,
        securityManager = securityManager,
        onSyncClick = { viewModel.syncNow() },
        onWebDavClick = { showWebDavDialog = true },
        onAddTotpClick = { showAddTotpDialog = true },
        onThemeClick = { showThemeDialog = true },
        onOledBlackToggle = { viewModel.setOledBlack(it) },
        onLanguageClick = { showLanguageDialog = true },
        onClearDataClick = { showClearDataDialog = true },
        onChangeLockClick = { showChangeLockDialog = true },
        modifier = modifier
    )
    
    // WebDAV配置对话框
    if (showWebDavDialog) {
        WebDavConfigDialog(
            viewModel = viewModel,
            onDismiss = { showWebDavDialog = false }
        )
    }

    // 配色方案选择对话框
    if (showThemeDialog) {
        ColorSchemeSelectionDialog(
            currentColorScheme = currentColorScheme,
            onColorSchemeSelected = { scheme ->
                viewModel.setColorScheme(scheme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
    
    // 语言选择对话框
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = currentLanguage,
            onLanguageSelected = { language ->
                viewModel.setLanguage(language)
                showLanguageDialog = false
                // 触发语言变更回调，重新创建 Activity
                onLanguageChanged()
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    // 添加验证器对话框
    if (showAddTotpDialog) {
        AddTotpDialog(
            onDismiss = { showAddTotpDialog = false },
            onConfirm = { secret, issuer, accountName, onResult ->
                viewModel.addTotpItem(
                    secret = secret,
                    issuer = issuer,
                    accountName = accountName
                ) { success, error ->
                    onResult(success, error)
                    if (success) {
                        showAddTotpDialog = false
                    }
                }
            }
        )
    }
    
    // 清除数据确认对话框
    if (showClearDataDialog) {
        ClearDataConfirmDialog(
            onConfirm = {
                viewModel.clearAllData()
                showClearDataDialog = false
            },
            onDismiss = { showClearDataDialog = false }
        )
    }
    
    // 修改锁定方式对话框
    if (showChangeLockDialog && securityManager != null) {
        ChangeLockDialog(
            securityManager = securityManager,
            onDismiss = { showChangeLockDialog = false }
        )
    }
}

/**
 * 设置界面内容组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    scrollState: androidx.compose.foundation.ScrollState,
    syncState: SyncState,
    lastSyncTime: String,
    isWebDavConfigured: Boolean,
    currentColorScheme: WearColorScheme,
    useOledBlack: Boolean,
    currentLanguage: takagi.ru.monica.wear.viewmodel.AppLanguage,
    securityManager: takagi.ru.monica.wear.security.WearSecurityManager?,
    onSyncClick: () -> Unit,
    onWebDavClick: () -> Unit,
    onAddTotpClick: () -> Unit,
    onThemeClick: () -> Unit,
    onOledBlackToggle: (Boolean) -> Unit,
    onLanguageClick: () -> Unit,
    onClearDataClick: () -> Unit,
    onChangeLockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 24.dp), // 增加顶部 padding
        verticalArrangement = Arrangement.spacedBy(20.dp) // 增加间距
    ) {
        // 标题区域
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            // 可选：添加一个小的返回按钮，虽然通常物理返回键或手势就够了
            // 但为了明确导航，保留一个小的图标也不错
            /* IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            } */
        }

        // 同步状态卡片
        SyncStatusCard(
            syncState = syncState,
            lastSyncTime = lastSyncTime,
            onSyncClick = onSyncClick,
            isConfigured = isWebDavConfigured
        )
        
        // 云端同步分组
        SettingsSection(title = stringResource(R.string.settings_section_sync)) {
            SettingsItem(
                icon = Icons.Default.Cloud,
                title = stringResource(R.string.settings_webdav_config),
                subtitle = stringResource(if (isWebDavConfigured) R.string.settings_webdav_configured else R.string.settings_webdav_not_configured),
                onClick = onWebDavClick,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        // 验证器管理分组
        SettingsSection(title = stringResource(R.string.settings_section_authenticator)) {
            SettingsItem(
                icon = Icons.Default.Add,
                title = stringResource(R.string.settings_add_totp),
                subtitle = stringResource(R.string.settings_add_totp_subtitle),
                onClick = onAddTotpClick,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        // 外观设置分组
        SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
            SettingsItem(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_color_scheme),
                subtitle = stringResource(currentColorScheme.displayNameResId),
                onClick = onThemeClick,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            
            SettingsItem(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_oled_black),
                subtitle = stringResource(if (useOledBlack) R.string.settings_oled_black_enabled else R.string.settings_oled_black_disabled),
                onClick = { onOledBlackToggle(!useOledBlack) },
                trailingContent = {
                    Switch(
                        checked = useOledBlack,
                        onCheckedChange = onOledBlackToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            )
            
            SettingsItem(
                icon = Icons.Default.Language,
                title = stringResource(R.string.settings_language),
                subtitle = stringResource(currentLanguage.displayNameResId),
                onClick = onLanguageClick,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
        
        // 安全设置分组
        if (securityManager != null) {
            SettingsSection(title = stringResource(R.string.settings_section_security)) {
                // 重新设置PIN码
                SettingsItem(
                    icon = Icons.Default.Key,
                    title = stringResource(R.string.settings_reset_pin),
                    subtitle = stringResource(R.string.settings_reset_pin_subtitle),
                    onClick = onChangeLockClick,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
        
        // 数据管理分组
        SettingsSection(title = stringResource(R.string.settings_section_data)) {
            SettingsItem(
                icon = Icons.Default.DeleteForever,
                title = stringResource(R.string.settings_clear_data),
                subtitle = stringResource(R.string.settings_clear_data_subtitle),
                onClick = onClearDataClick,
                isDestructive = true,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 关于信息
        AboutSection()
        
        // 底部留白，防止被圆角屏幕遮挡
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Material 3 设置分组
 */
@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = 2.dp
            )
        ) {
            Column {
                content()
            }
        }
    }
}

/**
 * 同步状态卡片 - Material 3 设计
 */
@Composable
private fun SyncStatusCard(
    syncState: SyncState,
    lastSyncTime: String,
    onSyncClick: () -> Unit,
    isConfigured: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = when (syncState) {
        is SyncState.Success -> MaterialTheme.colorScheme.primaryContainer
        is SyncState.Error -> MaterialTheme.colorScheme.errorContainer
        SyncState.Syncing -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = when (syncState) {
        is SyncState.Success -> MaterialTheme.colorScheme.onPrimaryContainer
        is SyncState.Error -> MaterialTheme.colorScheme.onErrorContainer
        SyncState.Syncing -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = isConfigured && syncState != SyncState.Syncing,
                onClick = onSyncClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (syncState) {
                            is SyncState.Success -> Icons.Default.CheckCircle
                            is SyncState.Error -> Icons.Default.Error
                            SyncState.Syncing -> Icons.Default.Sync
                            else -> Icons.Default.CloudOff
                        },
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(32.dp)
                    )
                    
                    Text(
                        text = stringResource(when (syncState) {
                            SyncState.Idle -> if (isConfigured) R.string.sync_status_idle else R.string.sync_status_idle_not_configured
                            SyncState.Syncing -> R.string.sync_status_syncing
                            is SyncState.Success -> R.string.sync_status_success
                            is SyncState.Error -> R.string.sync_status_failed
                        }),
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                AnimatedVisibility(
                    visible = syncState == SyncState.Syncing,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                        color = contentColor
                    )
                }
            }
            
            if (isConfigured) {
                if (lastSyncTime.isNotBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = contentColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.sync_last_time, lastSyncTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // 显示同步结果消息
                when (syncState) {
                    is SyncState.Success -> {
                        Text(
                            text = syncState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(contentColor.copy(alpha = 0.1f))
                                .padding(8.dp)
                        )
                    }
                    is SyncState.Error -> {
                        Text(
                            text = syncState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(contentColor.copy(alpha = 0.1f))
                                .padding(8.dp)
                        )
                    }
                    else -> {}
                }
                
                if (syncState != SyncState.Syncing) {
                    Text(
                        text = stringResource(R.string.sync_tap_to_sync),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.sync_configure_first),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Material 3 设置项组件
 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    trailingContent: @Composable () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(28.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            trailingContent()
        }
    }
}

/**
 * 关于信息部分 - Material 3
 */
@Composable
private fun AboutSection(
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            
            Text(
                text = stringResource(R.string.settings_about_app),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = stringResource(R.string.settings_version),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            
            Text(
                text = stringResource(R.string.settings_about_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * WebDAV配置对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebDavConfigDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.webdav_config_title))
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text(stringResource(R.string.webdav_server_url)) },
                    placeholder = { Text(stringResource(R.string.webdav_server_url_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Link, contentDescription = null)
                    }
                )
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.webdav_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    }
                )
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.webdav_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null)
                    }
                )
                
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isLoading = true
                    viewModel.configureWebDav(serverUrl, username, password) { success, error ->
                        isLoading = false
                        if (success) {
                            onDismiss()
                        } else {
                            errorMessage = error ?: context.getString(R.string.webdav_config_failed)
                        }
                    }
                },
                enabled = !isLoading && serverUrl.isNotBlank() && username.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.common_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/**
 * 加密密码配置对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordConfigDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("加密密码配置")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "设置用于解密备份文件的密码",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("加密密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null)
                    }
                )
                
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("确认密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null)
                    }
                )
                
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password != confirmPassword) {
                        errorMessage = "两次密码输入不一致"
                    } else {
                        viewModel.configureEncryptionPassword(password)
                        onDismiss()
                    }
                },
                enabled = password.isNotBlank() && confirmPassword.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 清除数据确认对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearDataConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.dialog_clear_data_title),
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = stringResource(R.string.dialog_clear_data_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/**
 * 配色方案选择对话框
 */
@Composable
private fun ColorSchemeSelectionDialog(
    currentColorScheme: WearColorScheme,
    onColorSchemeSelected: (WearColorScheme) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.dialog_color_scheme_title))
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WearColorScheme.values().forEach { scheme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onColorSchemeSelected(scheme) }
                            .background(
                                if (scheme == currentColorScheme) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    Color.Transparent
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = scheme == currentColorScheme,
                            onClick = null // Handled by Row click
                        )
                        Column {
                            Text(
                                text = stringResource(scheme.displayNameResId),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(scheme.descriptionResId),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/**
 * 语言选择对话框
 */
@Composable
private fun LanguageSelectionDialog(
    currentLanguage: takagi.ru.monica.wear.viewmodel.AppLanguage,
    onLanguageSelected: (takagi.ru.monica.wear.viewmodel.AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.dialog_language_title))
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                takagi.ru.monica.wear.viewmodel.AppLanguage.values().forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onLanguageSelected(language) }
                            .background(
                                if (language == currentLanguage) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    Color.Transparent
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = language == currentLanguage,
                            onClick = null // Handled by Row click
                        )
                        Text(
                            text = stringResource(language.displayNameResId),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/**
 * 添加 TOTP 验证器对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTotpDialog(
    onDismiss: () -> Unit,
    onConfirm: (secret: String, issuer: String, accountName: String, onResult: (Boolean, String?) -> Unit) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var secret by remember { mutableStateOf("") }
    var issuer by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var secretError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(stringResource(R.string.totp_add_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 密钥输入
                OutlinedTextField(
                    value = secret,
                    onValueChange = { 
                        secret = it
                        secretError = null
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.dialog_totp_secret)) },
                    placeholder = { Text(stringResource(R.string.dialog_totp_secret_hint)) },
                    isError = secretError != null,
                    supportingText = secretError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )

                // 发行方输入
                OutlinedTextField(
                    value = issuer,
                    onValueChange = { 
                        issuer = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.dialog_totp_issuer)) },
                    placeholder = { Text(stringResource(R.string.dialog_totp_issuer_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )

                // 账户名输入
                OutlinedTextField(
                    value = accountName,
                    onValueChange = { 
                        accountName = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.dialog_totp_account)) },
                    placeholder = { Text(stringResource(R.string.dialog_totp_account_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )

                // 错误提示
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        secret.isBlank() -> secretError = context.getString(R.string.dialog_totp_secret_error)
                        else -> {
                            isLoading = true
                            errorMessage = null
                            onConfirm(secret, issuer, accountName) { success, error ->
                                isLoading = false
                                if (!success) {
                                    errorMessage = error ?: context.getString(R.string.totp_add_failed)
                                }
                            }
                        }
                    }
                },
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}



