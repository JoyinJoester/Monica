package takagi.ru.monica.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.bitwarden.sync.SyncStatus

/**
 * 同步状态指示器图标
 * 
 * 用于在密码列表项、卡片等位置显示 Bitwarden 同步状态
 */
@Composable
fun SyncStatusIcon(
    status: SyncStatus?,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    showTooltip: Boolean = false
) {
    if (status == null || status == SyncStatus.NONE) {
        // 未启用 Bitwarden 同步，不显示图标
        return
    }
    
    val (icon, tint, contentDescription) = when (status) {
        SyncStatus.SYNCED -> Triple(
            Icons.Outlined.CloudDone,
            MaterialTheme.colorScheme.primary,
            stringResource(R.string.sync_status_synced_short)
        )
        SyncStatus.PENDING -> Triple(
            Icons.Outlined.CloudQueue,
            MaterialTheme.colorScheme.secondary,
            stringResource(R.string.sync_status_pending_short)
        )
        SyncStatus.SYNCING -> Triple(
            Icons.Outlined.CloudSync,
            MaterialTheme.colorScheme.primary,
            stringResource(R.string.sync_status_syncing_short)
        )
        SyncStatus.FAILED -> Triple(
            Icons.Outlined.CloudOff,
            MaterialTheme.colorScheme.error,
            stringResource(R.string.sync_status_failed_short)
        )
        SyncStatus.CONFLICT -> Triple(
            Icons.Outlined.Warning,
            MaterialTheme.colorScheme.error,
            stringResource(R.string.sync_status_conflict_short)
        )
        SyncStatus.NONE -> Triple(
            Icons.Outlined.Cloud,
            MaterialTheme.colorScheme.outline,
            stringResource(R.string.sync_status_local_short)
        )
    }
    
    // 同步中时的旋转动画
    val rotation by if (status == SyncStatus.SYNCING) {
        val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    
    val animatedTint by animateColorAsState(
        targetValue = tint,
        label = "tint_color"
    )
    
    Box(modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = animatedTint,
            modifier = Modifier
                .size(size)
                .then(
                    if (status == SyncStatus.SYNCING) 
                        Modifier.rotate(rotation) 
                    else 
                        Modifier
                )
        )
    }
}

/**
 * 同步状态徽章
 * 
 * 带背景的同步状态显示，用于更明显的状态展示
 */
@Composable
fun SyncStatusBadge(
    status: SyncStatus?,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    if (status == null || status == SyncStatus.NONE) {
        return
    }
    
    val (backgroundColor, textColor, label) = when (status) {
        SyncStatus.SYNCED -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            stringResource(R.string.sync_status_synced_short)
        )
        SyncStatus.PENDING -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            stringResource(R.string.sync_status_pending_badge)
        )
        SyncStatus.SYNCING -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            stringResource(R.string.sync_status_syncing_badge)
        )
        SyncStatus.FAILED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            stringResource(R.string.sync_status_failed_badge)
        )
        SyncStatus.CONFLICT -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            stringResource(R.string.sync_status_conflict_badge)
        )
        SyncStatus.NONE -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.sync_status_local_badge)
        )
    }
    
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SyncStatusIcon(
                status = status,
                size = 12.dp
            )
            
            if (showLabel) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Bitwarden 图标徽章
 * 
 * 显示条目是否来自 Bitwarden 的小图标
 */
@Composable
fun BitwardenBadge(
    isBitwardenItem: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp
) {
    if (!isBitwardenItem) return
    
    Icon(
        imageVector = Icons.Outlined.CloudDone,
        contentDescription = stringResource(R.string.bitwarden_synced_item),
        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
        modifier = modifier.size(size)
    )
}

/**
 * 同步状态行
 * 
 * 用于在详情页面显示完整的同步信息
 */
@Composable
fun SyncStatusRow(
    status: SyncStatus?,
    lastSyncTime: Long?,
    bitwardenVaultName: String?,
    modifier: Modifier = Modifier,
    onSyncClick: (() -> Unit)? = null
) {
    if (status == null || status == SyncStatus.NONE) {
        return
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SyncStatusIcon(status = status, size = 20.dp)
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = getStatusText(status),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (bitwardenVaultName != null) {
                        Text(
                            text = stringResource(R.string.sync_status_with_vault, bitwardenVaultName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                if (lastSyncTime != null && lastSyncTime > 0) {
                    Text(
                        text = stringResource(R.string.sync_last_sync, formatSyncRelativeTime(lastSyncTime)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            if (onSyncClick != null && status != SyncStatus.SYNCING) {
                IconButton(onClick = onSyncClick) {
                    Icon(
                        Icons.Outlined.Sync,
                        contentDescription = stringResource(R.string.sync_now),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun getStatusText(status: SyncStatus): String {
    return when (status) {
        SyncStatus.SYNCED -> stringResource(R.string.sync_status_synced_full)
        SyncStatus.PENDING -> stringResource(R.string.sync_status_pending_full)
        SyncStatus.SYNCING -> stringResource(R.string.sync_status_syncing_full)
        SyncStatus.FAILED -> stringResource(R.string.sync_status_failed_full)
        SyncStatus.CONFLICT -> stringResource(R.string.sync_status_conflict_full)
        SyncStatus.NONE -> stringResource(R.string.sync_status_local_full)
    }
}

@Composable
private fun formatSyncRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> stringResource(R.string.time_just_now)
        diff < 3600_000 -> stringResource(R.string.time_minutes_ago, diff / 60_000)
        diff < 86400_000 -> stringResource(R.string.time_hours_ago, diff / 3600_000)
        diff < 604800_000 -> stringResource(R.string.time_days_ago, diff / 86400_000)
        else -> {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}
