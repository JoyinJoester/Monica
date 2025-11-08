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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                Text("自动填充诊断")
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
                        title = "设备信息",
                        icon = Icons.Default.PhoneAndroid
                    ) {
                        InfoItem("制造商", diagnosticReport.deviceInfo.manufacturer)
                        InfoItem("型号", diagnosticReport.deviceInfo.model)
                        InfoItem("Android 版本", diagnosticReport.deviceInfo.androidVersion)
                        InfoItem("ROM 类型", diagnosticReport.deviceInfo.romType)
                        InfoItem(
                            "内联建议支持",
                            if (diagnosticReport.deviceInfo.supportsInlineSuggestions) "是" else "否"
                        )
                    }
                }
                
                // 服务状态
                item {
                    DiagnosticSection(
                        title = "服务状态",
                        icon = Icons.Default.Settings
                    ) {
                        StatusItem(
                            "服务已声明",
                            diagnosticReport.serviceStatus.isServiceDeclared
                        )
                        StatusItem(
                            "系统已启用",
                            diagnosticReport.serviceStatus.isSystemEnabled
                        )
                        StatusItem(
                            "应用已启用",
                            diagnosticReport.serviceStatus.isAppEnabled
                        )
                        StatusItem(
                            "权限完整",
                            diagnosticReport.serviceStatus.hasPermissions
                        )
                    }
                }
                
                // 统计信息
                item {
                    DiagnosticSection(
                        title = "统计信息",
                        icon = Icons.Default.Analytics
                    ) {
                        diagnosticReport.statistics.forEach { (key, value) ->
                            InfoItem(formatStatKey(key), value.toString())
                        }
                    }
                }
                
                // 最近的请求
                if (diagnosticReport.recentRequests.isNotEmpty()) {
                    item {
                        DiagnosticSection(
                            title = "最近的请求",
                            icon = Icons.Default.History
                        ) {
                            val requests = diagnosticReport.recentRequests.take(3)
                            requests.forEachIndexed { index, request ->
                                RequestItem(request, isLast = index == requests.lastIndex)
                            }
                        }
                    }
                }
                
                // 检测到的问题
                if (diagnosticReport.detectedIssues.isNotEmpty()) {
                    item {
                        DiagnosticSection(
                            title = "检测到的问题",
                            icon = Icons.Default.Warning
                        ) {
                            diagnosticReport.detectedIssues.forEach { issue ->
                                IssueItem(issue)
                            }
                        }
                    }
                }
                
                // 建议
                if (diagnosticReport.recommendations.isNotEmpty()) {
                    item {
                        DiagnosticSection(
                            title = "解决建议",
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
                Text("导出日志")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
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
                text = if (isOk) "正常" else "异常",
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
private fun RequestItem(request: takagi.ru.monica.autofill.core.RequestInfo, isLast: Boolean = false) {
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
            text = "字段: ${request.fieldsDetected}, 匹配: ${request.passwordsMatched}, 数据集: ${request.datasetsCreated}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        
        if (!request.success && request.errorMessage != null) {
            Text(
                text = "错误: ${request.errorMessage}",
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
private fun IssueItem(issue: Issue) {
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
                text = "影响 ${issue.affectedRequests} 次请求",
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
private fun formatStatKey(key: String): String {
    return when (key) {
        "totalRequests" -> "总请求数"
        "successfulRequests" -> "成功请求"
        "failedRequests" -> "失败请求"
        "successRate" -> "成功率"
        "avgResponseTime" -> "平均响应时间"
        "minResponseTime" -> "最快响应"
        "maxResponseTime" -> "最慢响应"
        "totalLogs" -> "日志条数"
        "errorCount" -> "错误数"
        "warningCount" -> "警告数"
        else -> key
    }
}
