package takagi.ru.monica.bitwarden.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 同步类型项数据
 */
data class SyncTypeItem(
    val type: String,
    val displayName: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val enabled: Boolean = true
)

/**
 * 同步类型配置卡片
 * 
 * 用于配置哪些数据类型要同步到 Bitwarden
 */
@Composable
fun SyncTypesConfigCard(
    enabledTypes: Set<String>,
    onTypesChanged: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val allTypes = remember {
        listOf(
            SyncTypeItem(
                type = "PASSWORD",
                displayName = "密码",
                description = "登录凭据和密码",
                icon = Icons.Outlined.Key
            ),
            SyncTypeItem(
                type = "TOTP",
                displayName = "验证器",
                description = "两步验证 (TOTP) 令牌",
                icon = Icons.Outlined.QrCode2
            ),
            SyncTypeItem(
                type = "CARD",
                displayName = "银行卡",
                description = "信用卡和借记卡信息",
                icon = Icons.Outlined.CreditCard
            ),
            SyncTypeItem(
                type = "NOTE",
                displayName = "安全笔记",
                description = "加密的文本笔记",
                icon = Icons.Outlined.Notes
            ),
            SyncTypeItem(
                type = "IDENTITY",
                displayName = "身份证件",
                description = "身份证、护照等证件信息",
                icon = Icons.Outlined.Badge
            ),
            SyncTypeItem(
                type = "PASSKEY",
                displayName = "通行密钥",
                description = "WebAuthn 通行密钥",
                icon = Icons.Outlined.Fingerprint
            )
        )
    }
    
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    Icons.Outlined.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "同步数据类型",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "选择要与 Bitwarden 同步的数据类型",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
            
            // 全选/取消全选
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (enabledTypes.size == allTypes.size) {
                            onTypesChanged(emptySet())
                        } else {
                            onTypesChanged(allTypes.map { it.type }.toSet())
                        }
                    }
                    .padding(vertical = 8.dp)
            ) {
                Checkbox(
                    checked = enabledTypes.size == allTypes.size,
                    onCheckedChange = { checked ->
                        if (checked) {
                            onTypesChanged(allTypes.map { it.type }.toSet())
                        } else {
                            onTypesChanged(emptySet())
                        }
                    }
                )
                Text(
                    text = "全部类型",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            // 各类型选项
            allTypes.forEach { item ->
                SyncTypeRow(
                    item = item,
                    isEnabled = enabledTypes.contains(item.type),
                    onToggle = { enabled ->
                        if (enabled) {
                            onTypesChanged(enabledTypes + item.type)
                        } else {
                            onTypesChanged(enabledTypes - item.type)
                        }
                    }
                )
            }
        }
    }
}

/**
 * 同步类型行
 */
@Composable
private fun SyncTypeRow(
    item: SyncTypeItem,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.enabled) { onToggle(!isEnabled) }
            .padding(vertical = 8.dp)
    ) {
        Checkbox(
            checked = isEnabled,
            onCheckedChange = onToggle,
            enabled = item.enabled
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Icon(
            item.icon,
            contentDescription = null,
            tint = if (isEnabled) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isEnabled) 
                    MaterialTheme.colorScheme.onSurface 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (isEnabled) 0.8f else 0.5f
                )
            )
        }
    }
}

/**
 * 同步方向配置卡片
 */
@Composable
fun SyncDirectionCard(
    syncDirection: SyncDirection,
    onDirectionChanged: (SyncDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    Icons.Outlined.SyncAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "同步方向",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "配置数据同步的方向",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
            
            SyncDirection.entries.forEach { direction ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDirectionChanged(direction) }
                        .padding(vertical = 8.dp)
                ) {
                    RadioButton(
                        selected = syncDirection == direction,
                        onClick = { onDirectionChanged(direction) }
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Icon(
                        when (direction) {
                            SyncDirection.BIDIRECTIONAL -> Icons.Outlined.SyncAlt
                            SyncDirection.UPLOAD_ONLY -> Icons.Outlined.Upload
                            SyncDirection.DOWNLOAD_ONLY -> Icons.Outlined.Download
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = direction.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = direction.description,
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
 * 同步方向枚举
 */
enum class SyncDirection(val displayName: String, val description: String) {
    BIDIRECTIONAL("双向同步", "Monica 和 Bitwarden 之间互相同步"),
    UPLOAD_ONLY("仅上传", "只将 Monica 数据上传到 Bitwarden"),
    DOWNLOAD_ONLY("仅下载", "只从 Bitwarden 下载数据到 Monica")
}

/**
 * 同步队列状态卡片
 */
@Composable
fun SyncQueueStatusCard(
    pendingCount: Int,
    failedCount: Int,
    lastSyncTime: Long,
    onViewQueue: () -> Unit,
    onRetryFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    Icons.Outlined.Queue,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "同步队列",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
            
            // 状态显示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatusChip(
                    icon = Icons.Outlined.Pending,
                    label = "待处理",
                    count = pendingCount,
                    color = MaterialTheme.colorScheme.primary
                )
                
                StatusChip(
                    icon = Icons.Outlined.Error,
                    label = "失败",
                    count = failedCount,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            if (lastSyncTime > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "上次同步: ${formatSyncTime(lastSyncTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            
            // 操作按钮
            if (pendingCount > 0 || failedCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (failedCount > 0) {
                        OutlinedButton(
                            onClick = onRetryFailed,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("重试失败项")
                        }
                    }
                    
                    OutlinedButton(
                        onClick = onViewQueue,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Outlined.List,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("查看队列")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = color
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }
        }
    }
}

private fun formatSyncTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000} 分钟前"
        diff < 86400_000 -> "${diff / 3600_000} 小时前"
        else -> {
            val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}
