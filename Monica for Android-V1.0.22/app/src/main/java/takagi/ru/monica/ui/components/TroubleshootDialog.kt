package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.autofill.core.DiagnosticReport
import takagi.ru.monica.autofill.core.Issue
import takagi.ru.monica.autofill.core.Severity
import java.text.SimpleDateFormat
import java.util.*

/**
 * 故障排查对话框
 * 
 * 显示诊断信息和解决建议
 * 
 * @param diagnosticReport 诊断报告
 * @param onDismiss 关闭回调
 * @param onExportLogs 导出日志回调
 */
@Composable
fun TroubleshootDialog(
    diagnosticReport: DiagnosticReport,
    onDismiss: () -> Unit,
    onExportLogs: () -> Unit
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(context.getString(R.string.autofill_troubleshoot_title))
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 设备信息
                item {
                    DiagnosticSection(
                        title = context.getString(R.string.autofill_troubleshoot_device_info),
                        icon = Icons.Default.PhoneAndroid
                    ) {
                        InfoItem(context.getString(R.string.autofill_troubleshoot_manufacturer), diagnosticReport.deviceInfo.manufacturer)
                        InfoItem(context.getString(R.string.autofill_troubleshoot_model), diagnosticReport.deviceInfo.model)
                        InfoItem(context.getString(R.string.autofill_troubleshoot_android_version), diagnosticReport.deviceInfo.androidVersion)
                        InfoItem(context.getString(R.string.autofill_troubleshoot_rom_type), diagnosticReport.deviceInfo.romType)
                        InfoItem(
                            context.getString(R.string.autofill_troubleshoot_inline_support),
                            if (diagnosticReport.deviceInfo.supportsInlineSuggestions) 
                                context.getString(R.string.autofill_troubleshoot_yes) 
                            else 
                                context.getString(R.string.autofill_troubleshoot_no)
                        )
                    }
                }
                
                // 服务状态
                item {
                    DiagnosticSection(
                        title = context.getString(R.string.autofill_troubleshoot_service_status),
                        icon = Icons.Default.Settings
                    ) {
                        StatusItem(
                            context.getString(R.string.autofill_troubleshoot_service_declared),
                            diagnosticReport.serviceStatus.isServiceDeclared
                        )
                        StatusItem(
                            context.getString(R.string.autofill_troubleshoot_system_enabled),
                            diagnosticReport.serviceStatus.isSystemEnabled
                        )
                        StatusItem(
                            context.getString(R.string.autofill_troubleshoot_app_enabled),
                            diagnosticReport.serviceStatus.isAppEnabled
                        )
                        StatusItem(
                            context.getString(R.string.autofill_troubleshoot_permissions_complete),
                            diagnosticReport.serviceStatus.hasPermissions
                        )
                    }
                }
                
                // 统计信息
                item {
                    DiagnosticSection(
                        title = context.getString(R.string.autofill_troubleshoot_statistics),
                        icon = Icons.Default.Analytics
                    ) {
                        diagnosticReport.statistics.forEach { (key, value) ->
                            InfoItem(formatStatKey(context, key), value.toString())
                        }
                    }
                }
                
                // 最近的请求
                if (diagnosticReport.recentRequests.isNotEmpty()) {
                    item {
                        DiagnosticSection(
                            title = context.getString(R.string.autofill_troubleshoot_recent_requests),
                            icon = Icons.Default.History
                        ) {
                            val requests = diagnosticReport.recentRequests.take(3)
                            requests.forEachIndexed { index, request ->
                                RequestItem(context, request, isLast = index == requests.lastIndex)
                            }
                        }
                    }
                }
                
                // 检测到的问题
                if (diagnosticReport.detectedIssues.isNotEmpty()) {
                    item {
                        DiagnosticSection(
                            title = context.getString(R.string.autofill_troubleshoot_detected_issues),
                            icon = Icons.Default.Warning
                        ) {
                            diagnosticReport.detectedIssues.forEach { issue ->
                                IssueItem(context, issue)
                            }
                        }
                    }
                }
                
                // 建议
                if (diagnosticReport.recommendations.isNotEmpty()) {
                    item {
                        DiagnosticSection(
                            title = context.getString(R.string.autofill_troubleshoot_recommendations),
                            icon = Icons.Default.Lightbulb
                        ) {
                            diagnosticReport.recommendations.forEach { recommendation ->
                                RecommendationItem(recommendation)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onExportLogs) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(context.getString(R.string.autofill_troubleshoot_export_logs))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.autofill_troubleshoot_close))
            }
        }
    )
}

/**
 * 诊断区块
 */
@Composable
private fun DiagnosticSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * 信息项
 */
@Composable
private fun InfoItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 状态项
 */
@Composable
private fun StatusItem(label: String, isOk: Boolean) {
    val context = LocalContext.current
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (isOk) 
                    context.getString(R.string.autofill_troubleshoot_status_normal) 
                else 
                    context.getString(R.string.autofill_troubleshoot_status_abnormal),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 请求项
 */
@Composable
private fun RequestItem(
    context: android.content.Context,
    request: takagi.ru.monica.autofill.core.RequestInfo, 
    isLast: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = request.packageName.split(".").lastOrNull() ?: request.packageName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = if (request.success) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (request.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp)
            )
        }
        
        Text(
            text = context.getString(
                R.string.autofill_troubleshoot_request_details,
                request.fieldsDetected,
                request.passwordsMatched,
                request.datasetsCreated
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        
        if (!request.success && request.errorMessage != null) {
            Text(
                text = context.getString(R.string.autofill_troubleshoot_request_error, request.errorMessage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
    
    if (!isLast) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }
}

/**
 * 问题项
 */
@Composable
private fun IssueItem(context: android.content.Context, issue: Issue) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = when (issue.severity) {
                Severity.CRITICAL -> Icons.Default.Error
                Severity.HIGH -> Icons.Default.Warning
                Severity.MEDIUM -> Icons.Default.Info
                Severity.LOW -> Icons.Default.Info
            },
            contentDescription = null,
            tint = when (issue.severity) {
                Severity.CRITICAL, Severity.HIGH -> MaterialTheme.colorScheme.error
                Severity.MEDIUM -> MaterialTheme.colorScheme.tertiary
                Severity.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(16.dp)
        )
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = issue.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = context.getString(R.string.autofill_troubleshoot_issue_affected, issue.affectedRequests),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * 建议项
 */
@Composable
private fun RecommendationItem(recommendation: takagi.ru.monica.autofill.core.Recommendation) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "${recommendation.priority}.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = recommendation.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = recommendation.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 格式化统计键名
 */
private fun formatStatKey(context: android.content.Context, key: String): String {
    return when (key) {
        "totalRequests" -> context.getString(R.string.autofill_troubleshoot_stat_total_requests)
        "successfulRequests" -> context.getString(R.string.autofill_troubleshoot_stat_successful_requests)
        "failedRequests" -> context.getString(R.string.autofill_troubleshoot_stat_failed_requests)
        "successRate" -> context.getString(R.string.autofill_troubleshoot_stat_success_rate)
        "avgResponseTime" -> context.getString(R.string.autofill_troubleshoot_stat_avg_response_time)
        "minResponseTime" -> context.getString(R.string.autofill_troubleshoot_stat_min_response_time)
        "maxResponseTime" -> context.getString(R.string.autofill_troubleshoot_stat_max_response_time)
        "totalLogs" -> context.getString(R.string.autofill_troubleshoot_stat_total_logs)
        "errorCount" -> context.getString(R.string.autofill_troubleshoot_stat_error_count)
        "warningCount" -> context.getString(R.string.autofill_troubleshoot_stat_warning_count)
        else -> key
    }
}
