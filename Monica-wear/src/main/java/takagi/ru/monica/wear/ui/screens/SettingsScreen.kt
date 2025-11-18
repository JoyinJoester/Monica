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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import takagi.ru.monica.wear.security.WearSecurityManager
import takagi.ru.monica.wear.ui.components.ChangeLockDialog
import takagi.ru.monica.wear.viewmodel.SettingsViewModel
import takagi.ru.monica.wear.viewmodel.SyncState
import java.text.SimpleDateFormat
import java.util.*

/**
 * 深色主题配置
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFF81C784),
    tertiary = Color(0xFFFFCC80),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2C2C2C),
    error = Color(0xFFCF6679),
    onPrimary = Color(0xFF000000),
    onSecondary = Color(0xFF000000),
    onTertiary = Color(0xFF000000),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFFB0B0B0),
    onError = Color(0xFF000000),
    primaryContainer = Color(0xFF1565C0),
    secondaryContainer = Color(0xFF2E7D32),
    errorContainer = Color(0xFFB71C1C),
    onPrimaryContainer = Color(0xFFE3F2FD),
    onSecondaryContainer = Color(0xFFE8F5E9),
    onErrorContainer = Color(0xFFFFCDD2)
)

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
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val syncState by viewModel.syncState.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val isWebDavConfigured by viewModel.isWebDavConfigured.collectAsState()
    
    var showWebDavDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showChangeLockDialog by remember { mutableStateOf(false) }
    
    // 使用深色主题
    MaterialTheme(colorScheme = DarkColorScheme) {
        SettingsScreenContent(
            scrollState = scrollState,
            syncState = syncState,
            lastSyncTime = lastSyncTime,
            isWebDavConfigured = isWebDavConfigured,
            securityManager = securityManager,
            onBack = onBack,
            onSyncClick = { viewModel.syncNow() },
            onWebDavClick = { showWebDavDialog = true },
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
    securityManager: takagi.ru.monica.wear.security.WearSecurityManager?,
    onBack: () -> Unit,
    onSyncClick: () -> Unit,
    onWebDavClick: () -> Unit,
    onClearDataClick: () -> Unit,
    onChangeLockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 同步状态卡片
            SyncStatusCard(
                syncState = syncState,
                lastSyncTime = lastSyncTime,
                onSyncClick = onSyncClick,
                isConfigured = isWebDavConfigured
            )
            
            // 云端同步分组
            SettingsSection(title = "云端同步") {
                SettingsItem(
                    icon = Icons.Default.Cloud,
                    title = "WebDAV 配置",
                    subtitle = if (isWebDavConfigured) "已配置" else "未配置",
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
            
            // 安全设置分组
            if (securityManager != null) {
                SettingsSection(title = "安全设置") {
                    // 重新设置PIN码
                    SettingsItem(
                        icon = Icons.Default.Key,
                        title = "重新设置PIN码",
                        subtitle = "修改6位数字密码",
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
            SettingsSection(title = "数据管理") {
                SettingsItem(
                    icon = Icons.Default.DeleteForever,
                    title = "清除所有数据",
                    subtitle = "删除本地所有验证器",
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
            
            Spacer(modifier = Modifier.height(16.dp))
        }
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
        SyncState.Success -> MaterialTheme.colorScheme.primaryContainer
        is SyncState.Error -> MaterialTheme.colorScheme.errorContainer
        SyncState.Syncing -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = when (syncState) {
        SyncState.Success -> MaterialTheme.colorScheme.onPrimaryContainer
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
                            SyncState.Success -> Icons.Default.CheckCircle
                            is SyncState.Error -> Icons.Default.Error
                            SyncState.Syncing -> Icons.Default.Sync
                            else -> Icons.Default.CloudOff
                        },
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(32.dp)
                    )
                    
                    Text(
                        text = when (syncState) {
                            SyncState.Idle -> if (isConfigured) "云端同步" else "未配置同步"
                            SyncState.Syncing -> "同步中..."
                            SyncState.Success -> "同步成功"
                            is SyncState.Error -> "同步失败"
                        },
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
                            text = "上次同步: $lastSyncTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }
                }
                
                if (syncState is SyncState.Error) {
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
                
                if (syncState != SyncState.Syncing) {
                    Text(
                        text = "点击立即同步",
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            } else {
                Text(
                    text = "请先配置 WebDAV 连接",
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
                text = "Monica Wear",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "版本 1.0.9",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            
            Text(
                text = "基于 Monica 的轻量版本\n专为小屏设备优化的 TOTP 验证器",
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
                Text("WebDAV 配置")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("https://example.com/webdav") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Link, contentDescription = null)
                    }
                )
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    }
                )
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
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
                            errorMessage = error ?: "配置失败"
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
                    Text("保存")
                }
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
                text = "清除所有数据",
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = "此操作将删除所有本地验证器数据，且无法恢复。确定要继续吗？",
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
                Text("确认删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
