package takagi.ru.monica.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.model.PermissionImportance
import takagi.ru.monica.data.model.PermissionInfo
import takagi.ru.monica.data.model.PermissionStatus

/**
 * 权限卡片组件
 * Permission card component
 */
@Composable
fun PermissionCard(
    permission: PermissionInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permissionName = stringResource(permission.nameResId)
    val statusText = stringResource(
        when (permission.status) {
            PermissionStatus.GRANTED -> R.string.permission_status_granted
            PermissionStatus.DENIED -> R.string.permission_status_denied
            PermissionStatus.UNAVAILABLE -> R.string.permission_status_unavailable
            PermissionStatus.UNKNOWN -> R.string.permission_status_unknown
        }
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$permissionName - $statusText"
                role = Role.Button
            }
            .clickable(
                enabled = permission.status != PermissionStatus.UNAVAILABLE,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = when (permission.status) {
                PermissionStatus.GRANTED -> MaterialTheme.colorScheme.surfaceVariant
                PermissionStatus.DENIED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                PermissionStatus.UNAVAILABLE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                PermissionStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 权限图标
            Icon(
                imageVector = permission.icon,
                contentDescription = stringResource(
                    R.string.permission_info_title,
                    permissionName
                ),
                modifier = Modifier.size(40.dp),
                tint = when (permission.status) {
                    PermissionStatus.GRANTED -> MaterialTheme.colorScheme.primary
                    PermissionStatus.DENIED -> MaterialTheme.colorScheme.error
                    PermissionStatus.UNAVAILABLE -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    PermissionStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurface
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 权限信息
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = permissionName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 8.dp),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    // 重要性标签
                    PermissionImportanceChip(
                        importance = permission.importance,
                        modifier = Modifier.wrapContentWidth()
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(permission.descriptionResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 状态指示器
            PermissionStatusIndicator(status = permission.status)
        }
    }
}

/**
 * 权限重要性标签
 * Permission importance chip
 */
@Composable
fun PermissionImportanceChip(
    importance: PermissionImportance,
    modifier: Modifier = Modifier
) {
    val (color, containerColor) = when (importance) {
        PermissionImportance.REQUIRED ->
            MaterialTheme.colorScheme.onPrimary to MaterialTheme.colorScheme.primary
        PermissionImportance.RECOMMENDED ->
            MaterialTheme.colorScheme.onSecondary to MaterialTheme.colorScheme.secondary
        PermissionImportance.OPTIONAL ->
            MaterialTheme.colorScheme.onTertiary to MaterialTheme.colorScheme.tertiary
    }

    Surface(
        modifier = modifier
            .wrapContentWidth()
            .heightIn(min = 20.dp),
        shape = MaterialTheme.shapes.small,
        color = containerColor
    ) {
        Text(
            text = stringResource(importance.labelResId),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}

/**
 * 权限状态指示器
 * Permission status indicator
 */
@Composable
fun PermissionStatusIndicator(status: PermissionStatus) {
    val (icon, color, textResId) = when (status) {
        PermissionStatus.GRANTED -> Triple(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primary,
            R.string.permission_status_granted
        )
        PermissionStatus.DENIED -> Triple(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.error,
            R.string.permission_status_denied
        )
        PermissionStatus.UNAVAILABLE -> Triple(
            Icons.Default.Block,
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            R.string.permission_status_unavailable
        )
        PermissionStatus.UNKNOWN -> Triple(
            Icons.Default.Help,
            MaterialTheme.colorScheme.onSurface,
            R.string.permission_status_unknown
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = stringResource(textResId),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
