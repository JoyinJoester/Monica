package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.model.DiffChange
import takagi.ru.monica.data.model.TimelineBranch
import java.text.SimpleDateFormat
import java.util.*

/**
 * GitHub 风格的 Diff 比较底部弹窗 - 使用 M3E 主题取色
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffComparisonSheet(
    branch: TimelineBranch,
    onDismiss: () -> Unit,
    onRestoreVersion: () -> Unit,
    onSaveAsNewEntry: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // 标题
            Text(
                text = branch.deviceName,
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.primary
            )
            
            // 时间戳
            Text(
                text = formatTimestamp(branch.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            
            // Diff 显示区域
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (branch.changes.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_changes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    } else {
                        branch.changes.forEach { change ->
                            DiffChangeItem(change)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onRestoreVersion,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.restore_version))
                }
                
                Button(
                    onClick = onSaveAsNewEntry,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.save_as_new_entry))
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 单个 Diff 变更项 - GitHub 风格显示
 */
@Composable
private fun DiffChangeItem(change: DiffChange) {
    val colorScheme = MaterialTheme.colorScheme
    
    // 使用 M3E 主题颜色构建删除/添加效果
    val deleteBackground = colorScheme.errorContainer
    val deleteText = colorScheme.error
    val addBackground = colorScheme.primaryContainer
    val addText = colorScheme.primary
    
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 字段名称标签
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = colorScheme.primary.copy(alpha = 0.2f)
        ) {
            Text(
                text = change.fieldName,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        
        // 使用 buildAnnotatedString 渲染富文本 diff
        Text(
            text = buildAnnotatedString {
                // 旧值 - 错误色背景 + 删除线
                if (change.oldValue.isNotEmpty()) {
                    withStyle(
                        SpanStyle(
                            background = deleteBackground,
                            color = deleteText,
                            textDecoration = TextDecoration.LineThrough
                        )
                    ) {
                        append(" ${change.oldValue} ")
                    }
                    append("  ")
                }
                
                // 新值 - 主色背景
                if (change.newValue.isNotEmpty()) {
                    withStyle(
                        SpanStyle(
                            background = addBackground,
                            color = addText
                        )
                    ) {
                        append(" ${change.newValue} ")
                    }
                }
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * 格式化时间戳
 */
private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * 格式化为短时间（仅时分）
 */
fun formatShortTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * 格式化为相对时间描述
 */
fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60 * 1000 -> "刚刚"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} 分钟前"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} 小时前"
        else -> {
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
