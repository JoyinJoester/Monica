package takagi.ru.monica.bitwarden.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel
import takagi.ru.monica.data.bitwarden.BitwardenVault
import java.text.SimpleDateFormat
import java.util.*

/**
 * Bitwarden 设置界面
 * 
 * 功能：
 * - 查看已连接的 Vault
 * - 添加新的 Vault
 * - 管理同步设置
 * - 锁定/解锁 Vault
 * - 登出 Vault
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BitwardenSettingsScreen(
    viewModel: BitwardenViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToVault: (Long) -> Unit
) {
    val vaults by viewModel.vaults.collectAsState()
    val activeVault by viewModel.activeVault.collectAsState()
    val unlockState by viewModel.unlockState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    
    // 对话框状态
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var vaultToLogout by remember { mutableStateOf<BitwardenVault?>(null) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var vaultToUnlock by remember { mutableStateOf<BitwardenVault?>(null) }
    
    // 监听事件
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BitwardenViewModel.BitwardenEvent.NavigateToLogin -> {
                    onNavigateToLogin()
                }
                else -> {}
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bitwarden 设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (activeVault != null && unlockState == BitwardenViewModel.UnlockState.Unlocked) {
                        // 同步按钮
                        IconButton(
                            onClick = { viewModel.sync() },
                            enabled = syncState !is BitwardenViewModel.SyncState.Syncing
                        ) {
                            if (syncState is BitwardenViewModel.SyncState.Syncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = "同步")
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToLogin
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加 Vault")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 已连接的 Vault
            item {
                Text(
                    text = "已连接的 Vault",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            if (vaults.isEmpty()) {
                item {
                    EmptyVaultCard(onAddClick = onNavigateToLogin)
                }
            } else {
                items(vaults, key = { it.id }) { vault ->
                    VaultCard(
                        vault = vault,
                        isActive = vault.id == activeVault?.id,
                        isUnlocked = vault.id == activeVault?.id && 
                                unlockState == BitwardenViewModel.UnlockState.Unlocked,
                        syncState = if (vault.id == activeVault?.id) syncState else null,
                        lastSyncTime = viewModel.lastSyncTime,
                        onSelect = { 
                            viewModel.setActiveVault(vault)
                            onNavigateToVault(vault.id)
                        },
                        onLock = { viewModel.lock() },
                        onUnlock = {
                            vaultToUnlock = vault
                            showUnlockDialog = true
                        },
                        onSync = { viewModel.sync() },
                        onLogout = {
                            vaultToLogout = vault
                            showLogoutConfirmDialog = true
                        }
                    )
                }
            }
            
            // 同步设置
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "同步设置",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            item {
                SyncSettingsCard(
                    isAutoSyncEnabled = viewModel.isAutoSyncEnabled,
                    onAutoSyncChanged = { viewModel.isAutoSyncEnabled = it },
                    isSyncOnWifiOnly = viewModel.isSyncOnWifiOnly,
                    onSyncOnWifiOnlyChanged = { viewModel.isSyncOnWifiOnly = it }
                )
            }
            
            // 关于
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            item {
                AboutCard()
            }
            
            // 底部间距
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
    
    // 登出确认对话框
    if (showLogoutConfirmDialog && vaultToLogout != null) {
        AlertDialog(
            onDismissRequest = { 
                showLogoutConfirmDialog = false
                vaultToLogout = null
            },
            icon = {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("确认登出") },
            text = {
                Column {
                    Text("确定要登出 ${vaultToLogout!!.email} 吗？")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "这将删除本地存储的所有 Bitwarden 密码数据。您可以随时重新登录来恢复数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.logout(vaultToLogout!!.id)
                        showLogoutConfirmDialog = false
                        vaultToLogout = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("登出")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirmDialog = false
                        vaultToLogout = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
    
    // 解锁对话框
    if (showUnlockDialog && vaultToUnlock != null) {
        UnlockVaultDialog(
            email = vaultToUnlock!!.email,
            onUnlock = { password ->
                viewModel.setActiveVault(vaultToUnlock!!)
                viewModel.unlock(password)
                showUnlockDialog = false
                vaultToUnlock = null
            },
            onDismiss = {
                showUnlockDialog = false
                vaultToUnlock = null
            }
        )
    }
}

/**
 * Vault 卡片
 */
@Composable
fun VaultCard(
    vault: BitwardenVault,
    isActive: Boolean,
    isUnlocked: Boolean,
    syncState: BitwardenViewModel.SyncState?,
    lastSyncTime: Long,
    onSelect: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onSync: () -> Unit,
    onLogout: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "expand_rotation"
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isActive) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column {
            // 主要内容
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 状态图标
                Box(
                    modifier = Modifier
                        .size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isUnlocked) {
                        Icon(
                            Icons.Outlined.LockOpen,
                            contentDescription = "已解锁",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = "已锁定",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vault.email,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = vault.serverUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (isActive && lastSyncTime > 0) {
                        Text(
                            text = "上次同步: ${formatTime(lastSyncTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 展开按钮
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        modifier = Modifier.rotate(rotationAngle)
                    )
                }
            }
            
            // 展开的操作按钮
            AnimatedVisibility(visible = expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isActive) {
                        if (isUnlocked) {
                            // 锁定按钮
                            OutlinedButton(
                                onClick = onLock,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("锁定")
                            }
                            
                            // 同步按钮
                            OutlinedButton(
                                onClick = onSync,
                                enabled = syncState !is BitwardenViewModel.SyncState.Syncing,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("同步")
                            }
                        } else {
                            // 解锁按钮
                            Button(
                                onClick = onUnlock,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("解锁")
                            }
                        }
                    } else {
                        // 选择此 Vault
                        Button(
                            onClick = onSelect,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("选择")
                        }
                    }
                    
                    // 登出按钮
                    OutlinedButton(
                        onClick = onLogout,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("登出")
                    }
                }
            }
        }
    }
}

/**
 * 空 Vault 提示卡片
 */
@Composable
fun EmptyVaultCard(onAddClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "尚未连接 Bitwarden",
                style = MaterialTheme.typography.titleMedium
            )
            
            Text(
                text = "连接您的 Bitwarden 账户以同步密码",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("添加 Bitwarden 账户")
            }
        }
    }
}

/**
 * 同步设置卡片
 */
@Composable
fun SyncSettingsCard(
    isAutoSyncEnabled: Boolean,
    onAutoSyncChanged: (Boolean) -> Unit,
    isSyncOnWifiOnly: Boolean,
    onSyncOnWifiOnlyChanged: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "自动同步",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "启动时自动同步 Bitwarden 数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isAutoSyncEnabled,
                    onCheckedChange = onAutoSyncChanged
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "仅 Wi-Fi 同步",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "仅在 Wi-Fi 网络下自动同步",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isSyncOnWifiOnly,
                    onCheckedChange = onSyncOnWifiOnlyChanged,
                    enabled = isAutoSyncEnabled
                )
            }
        }
    }
}

/**
 * 关于卡片
 */
@Composable
fun AboutCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Bitwarden 集成",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Monica 支持连接您的 Bitwarden 账户（包括官方服务器和自托管 Vaultwarden）。" +
                        "您的数据使用与 Bitwarden 相同的加密标准进行保护。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "支持的服务器:",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "• Bitwarden 官方服务器\n• Vaultwarden (自托管)\n• 其他兼容 Bitwarden API 的服务",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 解锁 Vault 对话框
 */
@Composable
fun UnlockVaultDialog(
    email: String,
    onUnlock: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("解锁 Vault") },
        text = {
            Column {
                Text(
                    text = "输入主密码解锁 $email",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("主密码") },
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onUnlock(password) },
                enabled = password.isNotBlank()
            ) {
                Text("解锁")
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
 * 格式化时间
 */
private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000} 分钟前"
        diff < 86400_000 -> "${diff / 3600_000} 小时前"
        else -> {
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
