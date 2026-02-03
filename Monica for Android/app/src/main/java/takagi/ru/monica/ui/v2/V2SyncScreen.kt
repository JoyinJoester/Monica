package takagi.ru.monica.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.ui.v2.components.EmptyVaultWarningDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 同步服务状态
 */
enum class SyncServiceStatus {
    CONNECTED,      // 已连接
    DISCONNECTED,   // 未连接
    SYNCING,        // 同步中
    ERROR           // 错误
}

/**
 * 同步服务数据
 */
data class SyncService(
    val id: String,
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val status: SyncServiceStatus,
    val lastSyncTime: String? = null,
    val itemCount: Int = 0,
    val errorMessage: String? = null
)

/**
 * V2 同步中心页面
 * 
 * 功能：
 * - 显示已连接的同步服务状态
 * - 添加新的同步服务
 * - 手动触发同步
 * - 查看同步历史和错误
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2SyncScreen(
    modifier: Modifier = Modifier,
    viewModel: V2ViewModel = viewModel(),
    onNavigateToBitwardenLogin: () -> Unit = {},
    onNavigateToKeePassImport: () -> Unit = {},
    onNavigateToWebDAVSettings: () -> Unit = {}
) {
    // 从 ViewModel 收集状态
    val dataSources by viewModel.dataSources.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    
    // 空 Vault 警告对话框状态
    var showEmptyVaultWarning by remember { mutableStateOf(false) }
    var emptyVaultLocalCount by remember { mutableIntStateOf(0) }
    var emptyVaultServerCount by remember { mutableIntStateOf(0) }
    
    // 处理事件
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is V2ViewModel.V2Event.NavigateToBitwardenLogin -> {
                    onNavigateToBitwardenLogin()
                }
                is V2ViewModel.V2Event.NavigateToKeePassImport -> {
                    onNavigateToKeePassImport()
                }
                is V2ViewModel.V2Event.EmptyVaultWarning -> {
                    // 显示空 Vault 警告对话框
                    emptyVaultLocalCount = event.localCount
                    emptyVaultServerCount = event.serverCount
                    showEmptyVaultWarning = true
                }
                else -> { /* 其他事件处理 */ }
            }
        }
    }
    
    // 空 Vault 警告对话框
    if (showEmptyVaultWarning) {
        EmptyVaultWarningDialog(
            localCount = emptyVaultLocalCount,
            serverCount = emptyVaultServerCount,
            onConfirmClear = {
                showEmptyVaultWarning = false
                viewModel.confirmEmptyVaultClear()
            },
            onCancel = {
                showEmptyVaultWarning = false
                viewModel.cancelEmptyVaultSync()
            },
            onDismiss = {
                showEmptyVaultWarning = false
                viewModel.cancelEmptyVaultSync()
            }
        )
    }
    
    // 转换为 SyncService 格式
    val services = dataSources.map { source ->
        SyncService(
            id = source.id,
            name = source.name,
            icon = when (source.id) {
                "local" -> Icons.Default.PhoneAndroid
                "bitwarden" -> Icons.Default.Cloud
                "keepass" -> Icons.Default.Storage
                else -> Icons.Default.Folder
            },
            status = when {
                syncState is V2SyncState.Syncing && (syncState as V2SyncState.Syncing).sourceId == source.id -> 
                    SyncServiceStatus.SYNCING
                source.errorMessage != null -> SyncServiceStatus.ERROR
                source.isConnected -> SyncServiceStatus.CONNECTED
                else -> SyncServiceStatus.DISCONNECTED
            },
            lastSyncTime = source.lastSyncTime?.let { formatSyncTime(it) } ?: if (source.id == "local") "始终可用" else null,
            itemCount = source.itemCount,
            errorMessage = source.errorMessage
        )
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部应用栏
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = stringResource(R.string.v2_sync_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.v2_sync_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
        
        // 内容区域
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 已连接服务
            item {
                Text(
                    text = "已连接服务",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // 动态显示已连接的服务
            items(services.filter { it.status != SyncServiceStatus.DISCONNECTED }) { service ->
                SyncServiceCard(
                    service = service,
                    onSyncNow = { viewModel.syncDataSource(service.id) },
                    onDisconnect = if (service.id != "local") {
                        { 
                            when (service.id) {
                                "bitwarden" -> viewModel.disconnectBitwarden()
                                // 其他服务的断开逻辑
                            }
                        }
                    } else null
                )
            }
            
            // 添加服务区域
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "添加同步服务",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Bitwarden 连接卡片（仅当未连接时显示）
            if (services.none { it.id == "bitwarden" && it.status == SyncServiceStatus.CONNECTED }) {
                item {
                    AddServiceCard(
                        name = "Bitwarden",
                        description = "连接您的 Bitwarden 账户同步密码",
                        icon = Icons.Default.Cloud,
                        onClick = { viewModel.connectBitwarden() }
                    )
                }
            }
            
            // KeePass 导入卡片
            if (services.none { it.id == "keepass" && it.status == SyncServiceStatus.CONNECTED }) {
                item {
                    AddServiceCard(
                        name = "KeePass",
                        description = "导入 KeePass 数据库文件",
                        icon = Icons.Default.Storage,
                        onClick = { viewModel.importKeePass() }
                    )
                }
            }
            
            // WebDAV 同步（V1 已有功能）
            item {
                AddServiceCard(
                    name = "WebDAV 备份",
                    description = "使用 WebDAV 备份和同步数据",
                    icon = Icons.Default.CloudSync,
                    onClick = onNavigateToWebDAVSettings,
                    isExisting = true
                )
            }
            
            // 底部说明
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            text = "V2 多源密码库复用 V1 的安全基础设施，所有凭据均使用 AES-256-GCM 加密存储，密钥由 Android Keystore 保护。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 同步服务状态卡片
 */
@Composable
private fun SyncServiceCard(
    service: SyncService,
    onSyncNow: () -> Unit,
    onDisconnect: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 图标
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = service.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // 信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = service.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 状态指示器
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (service.status) {
                                        SyncServiceStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                                        SyncServiceStatus.SYNCING -> MaterialTheme.colorScheme.tertiary
                                        SyncServiceStatus.ERROR -> MaterialTheme.colorScheme.error
                                        SyncServiceStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline
                                    }
                                )
                        )
                        
                        Text(
                            text = when (service.status) {
                                SyncServiceStatus.CONNECTED -> "已连接"
                                SyncServiceStatus.SYNCING -> "同步中..."
                                SyncServiceStatus.ERROR -> "错误"
                                SyncServiceStatus.DISCONNECTED -> "未连接"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        service.lastSyncTime?.let { time ->
                            Text(
                                text = "• $time",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // 条目数量
                if (service.itemCount > 0) {
                    Text(
                        text = "${service.itemCount} 项",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 操作按钮
            if (service.status == SyncServiceStatus.CONNECTED && onDisconnect != null) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDisconnect) {
                        Text("断开连接")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    FilledTonalButton(onClick = onSyncNow) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("立即同步")
                    }
                }
            }
        }
    }
}

/**
 * 添加服务卡片
 */
@Composable
private fun AddServiceCard(
    name: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    isExisting: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExisting) 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isExisting)
                            MaterialTheme.colorScheme.surfaceVariant
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isExisting)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    if (isExisting) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "V1",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
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
 * 格式化同步时间
 */
private fun formatSyncTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000} 分钟前"
        diff < 86400_000 -> "${diff / 3600_000} 小时前"
        diff < 604800_000 -> "${diff / 86400_000} 天前"
        else -> {
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}