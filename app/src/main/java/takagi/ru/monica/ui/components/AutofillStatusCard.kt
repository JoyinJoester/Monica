package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.autofill.core.AutofillServiceChecker

/**
 * 自动填充状态卡片
 * 
 * 显示服务状态和问题提示
 * 
 * @param status 服务状态
 * @param onEnableClick 启用服务点击回调
 * @param onTroubleshootClick 故障排查点击回调
 */
@Composable
fun AutofillStatusCard(
    status: AutofillServiceChecker.ServiceStatus,
    onEnableClick: () -> Unit,
    onTroubleshootClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (status.isSystemEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 状态图标和标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (status.isSystemEnabled) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = if (status.isSystemEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(32.dp)
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = status.getSummary(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (status.isSystemEnabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                    
                    if (status.isFullyOperational()) {
                        Text(
                            text = "所有功能正常",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // 状态详情
            if (!status.isSystemEnabled) {
                HorizontalDivider()
                
                Text(
                    text = "请在系统设置中启用 Monica 作为自动填充服务",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                
                Button(
                    onClick = onEnableClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("前往系统设置")
                }
            }
            
            // 兼容性问题警告
            if (status.compatibilityIssues.isNotEmpty()) {
                HorizontalDivider()
                
                Text(
                    text = "检测到兼容性问题:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (status.isSystemEnabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    status.compatibilityIssues.take(3).forEach { issue ->
                        Text(
                            text = "• $issue",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.isSystemEnabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            }
                        )
                    }
                    
                    if (status.compatibilityIssues.size > 3) {
                        Text(
                            text = "还有 ${status.compatibilityIssues.size - 3} 个问题...",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.isSystemEnabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f)
                            }
                        )
                    }
                }
            }
            
            // 故障排查按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onTroubleshootClick
                ) {
                    Text(
                        "故障排查",
                        color = if (status.isSystemEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        }
    }
}
