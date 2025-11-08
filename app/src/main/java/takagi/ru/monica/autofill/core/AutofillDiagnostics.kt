package takagi.ru.monica.autofill.core

import android.content.Context
import android.os.Build
import takagi.ru.monica.utils.DeviceUtils
import java.text.SimpleDateFormat
import java.util.*

/**
 * 自动填充诊断系统
 * 
 * 提供详细的诊断日志记录和问题分析功能
 * 
 * 功能:
 * - 记录填充请求的完整生命周期
 * - 记录字段解析结果和准确度
 * - 记录密码匹配过程和结果
 * - 记录响应构建过程和错误
 * - 生成诊断报告
 * - 导出日志用于故障排查
 * 
 * @author Monica Team
 * @since 2.0
 */
class AutofillDiagnostics(private val context: Context) {
    
    companion object {
        private const val TAG = "AutofillDiagnostics"
        private const val MAX_LOG_ENTRIES = 100
    }
    
    /**
     * 日志级别
     */
    enum class LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }
    
    /**
     * 诊断日志条目
     */
    data class DiagnosticEntry(
        val timestamp: Long,
        val level: LogLevel,
        val category: String,
        val message: String,
        val details: Map<String, Any> = emptyMap()
    ) {
        fun format(): String {
            val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            val timeStr = dateFormat.format(Date(timestamp))
            val detailsStr = if (details.isNotEmpty()) {
                "\n  Details: ${details.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
            } else ""
            return "$timeStr [${level.name}] [$category] $message$detailsStr"
        }
    }
    
    /**
     * 匹配详情
     */
    data class MatchDetail(
        val passwordId: Long,
        val passwordTitle: String,
        val matchType: String,
        val score: Float,
        val matchedOn: String,
        val reason: String
    )
    
    // 内存中的日志缓存
    private val logEntries = mutableListOf<DiagnosticEntry>()
    
    // 统计信息
    private var totalRequests = 0
    private var successfulRequests = 0
    private var failedRequests = 0
    private val requestTimes = mutableListOf<Long>()
    
    /**
     * 记录填充请求
     */
    fun logFillRequest(
        packageName: String,
        flags: Int,
        contextCount: Int,
        hasInlineRequest: Boolean
    ) {
        totalRequests++
        
        val details = mapOf(
            "packageName" to packageName,
            "flags" to flags,
            "contextCount" to contextCount,
            "hasInlineRequest" to hasInlineRequest,
            "deviceBrand" to Build.MANUFACTURER,
            "androidVersion" to Build.VERSION.SDK_INT
        )
        
        addLog(
            LogLevel.INFO,
            "REQUEST",
            "Fill request received",
            details
        )
        
        AutofillLogger.i(
            AutofillLogCategory.REQUEST,
            "Fill request: package=$packageName, contexts=$contextCount, inline=$hasInlineRequest"
        )
    }
    
    /**
     * 记录字段解析结果
     */
    fun logFieldParsing(
        totalFields: Int,
        usernameFields: Int,
        passwordFields: Int,
        otherFields: Int,
        parserUsed: String,
        accuracy: Float
    ) {
        val details = mapOf(
            "totalFields" to totalFields,
            "usernameFields" to usernameFields,
            "passwordFields" to passwordFields,
            "otherFields" to otherFields,
            "parserUsed" to parserUsed,
            "accuracy" to accuracy
        )
        
        val level = when {
            accuracy >= 0.8f -> LogLevel.INFO
            accuracy >= 0.5f -> LogLevel.WARNING
            else -> LogLevel.ERROR
        }
        
        addLog(
            level,
            "PARSING",
            "Field parsing completed",
            details
        )
        
        AutofillLogger.i(
            AutofillLogCategory.PARSING,
            "Parsed $totalFields fields (username=$usernameFields, password=$passwordFields) " +
            "using $parserUsed, accuracy=$accuracy"
        )
    }
    
    /**
     * 记录密码匹配结果
     */
    fun logPasswordMatching(
        packageName: String,
        domain: String?,
        matchStrategy: String,
        totalPasswords: Int,
        matchedPasswords: Int,
        matchDetails: List<MatchDetail> = emptyList()
    ) {
        val details = mutableMapOf<String, Any>(
            "packageName" to packageName,
            "matchStrategy" to matchStrategy,
            "totalPasswords" to totalPasswords,
            "matchedPasswords" to matchedPasswords
        )
        
        domain?.let { details["domain"] = it }
        
        if (matchDetails.isNotEmpty()) {
            details["topMatches"] = matchDetails.take(3).map { 
                "${it.passwordTitle} (${it.matchType}, score=${it.score})"
            }
        }
        
        val level = if (matchedPasswords > 0) LogLevel.INFO else LogLevel.WARNING
        
        addLog(
            level,
            "MATCHING",
            "Password matching completed",
            details
        )
        
        AutofillLogger.i(
            AutofillLogCategory.MATCHING,
            "Matched $matchedPasswords/$totalPasswords passwords for $packageName" +
            (domain?.let { " (domain=$it)" } ?: "")
        )
    }
    
    /**
     * 记录响应构建结果
     */
    fun logResponseBuilding(
        datasetsCreated: Int,
        datasetsFailed: Int,
        hasInlinePresentation: Boolean,
        errors: List<String> = emptyList()
    ) {
        val details = mutableMapOf<String, Any>(
            "datasetsCreated" to datasetsCreated,
            "datasetsFailed" to datasetsFailed,
            "hasInlinePresentation" to hasInlinePresentation
        )
        
        if (errors.isNotEmpty()) {
            details["errors"] = errors.take(5)
        }
        
        val level = when {
            datasetsCreated > 0 -> LogLevel.INFO
            datasetsFailed > 0 -> LogLevel.ERROR
            else -> LogLevel.WARNING
        }
        
        addLog(
            level,
            "FILLING",
            "Response building completed",
            details
        )
        
        if (datasetsCreated > 0) {
            successfulRequests++
        } else {
            failedRequests++
        }
        
        AutofillLogger.i(
            AutofillLogCategory.FILLING,
            "Built $datasetsCreated datasets ($datasetsFailed failed), inline=$hasInlinePresentation"
        )
    }
    
    /**
     * 记录请求处理时间
     */
    fun logRequestTime(timeMs: Long) {
        requestTimes.add(timeMs)
        if (requestTimes.size > 100) {
            requestTimes.removeAt(0)
        }
        
        AutofillLogger.d(
            AutofillLogCategory.PERFORMANCE,
            "Request processed in ${timeMs}ms"
        )
    }
    
    /**
     * 记录错误
     */
    fun logError(category: String, message: String, error: Throwable? = null) {
        val details = mutableMapOf<String, Any>()
        error?.let {
            details["error"] = it.toString()
            details["stackTrace"] = it.stackTraceToString().take(500)
        }
        
        addLog(
            LogLevel.ERROR,
            category,
            message,
            details
        )
        
        AutofillLogger.e(category, message, error)
    }
    
    /**
     * 添加日志条目
     */
    private fun addLog(
        level: LogLevel,
        category: String,
        message: String,
        details: Map<String, Any>
    ) {
        synchronized(logEntries) {
            logEntries.add(
                DiagnosticEntry(
                    timestamp = System.currentTimeMillis(),
                    level = level,
                    category = category,
                    message = message,
                    details = details
                )
            )
            
            // 保持日志数量在限制内
            if (logEntries.size > MAX_LOG_ENTRIES) {
                logEntries.removeAt(0)
            }
        }
    }
    
    /**
     * 生成诊断报告
     */
    fun generateDiagnosticReport(): DiagnosticReport {
        val deviceInfo = DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            romType = DeviceUtils.getROMType().name,
            supportsInlineSuggestions = DeviceUtils.supportsInlineSuggestions()
        )
        
        val serviceStatus = ServiceStatusInfo(
            isServiceDeclared = true, // 假设已声明
            isSystemEnabled = checkSystemEnabled(),
            isAppEnabled = true, // 从 preferences 获取
            hasPermissions = true // 检查权限
        )
        
        val recentRequests = synchronized(logEntries) {
            logEntries
                .filter { it.category == "REQUEST" || it.category == "FILLING" }
                .takeLast(10)
                .map { entry ->
                    RequestInfo(
                        timestamp = entry.timestamp,
                        packageName = entry.details["packageName"]?.toString() ?: "unknown",
                        domain = entry.details["domain"]?.toString(),
                        fieldsDetected = entry.details["totalFields"]?.toString()?.toIntOrNull() ?: 0,
                        passwordsMatched = entry.details["matchedPasswords"]?.toString()?.toIntOrNull() ?: 0,
                        datasetsCreated = entry.details["datasetsCreated"]?.toString()?.toIntOrNull() ?: 0,
                        success = entry.level != LogLevel.ERROR,
                        errorMessage = if (entry.level == LogLevel.ERROR) entry.message else null
                    )
                }
        }
        
        val detectedIssues = analyzeIssues()
        val recommendations = generateRecommendations(detectedIssues)
        
        return DiagnosticReport(
            timestamp = System.currentTimeMillis(),
            deviceInfo = deviceInfo,
            serviceStatus = serviceStatus,
            recentRequests = recentRequests,
            detectedIssues = detectedIssues,
            recommendations = recommendations,
            statistics = getStatistics()
        )
    }
    
    /**
     * 分析问题
     */
    private fun analyzeIssues(): List<Issue> {
        val issues = mutableListOf<Issue>()
        
        synchronized(logEntries) {
            // 检查解析失败
            val parsingErrors = logEntries.count { 
                it.category == "PARSING" && it.level == LogLevel.ERROR 
            }
            if (parsingErrors > 0) {
                issues.add(
                    Issue(
                        severity = Severity.HIGH,
                        category = "字段解析",
                        description = "检测到 $parsingErrors 次字段解析失败",
                        affectedRequests = parsingErrors
                    )
                )
            }
            
            // 检查匹配失败
            val matchingWarnings = logEntries.count { 
                it.category == "MATCHING" && it.level == LogLevel.WARNING 
            }
            if (matchingWarnings > 0) {
                issues.add(
                    Issue(
                        severity = Severity.MEDIUM,
                        category = "密码匹配",
                        description = "有 $matchingWarnings 次请求未找到匹配的密码",
                        affectedRequests = matchingWarnings
                    )
                )
            }
            
            // 检查响应构建失败
            val buildingErrors = logEntries.count { 
                it.category == "FILLING" && it.level == LogLevel.ERROR 
            }
            if (buildingErrors > 0) {
                issues.add(
                    Issue(
                        severity = Severity.HIGH,
                        category = "响应构建",
                        description = "有 $buildingErrors 次响应构建失败",
                        affectedRequests = buildingErrors
                    )
                )
            }
            
            // 检查性能问题
            val avgTime = requestTimes.average()
            if (avgTime > 1000) {
                issues.add(
                    Issue(
                        severity = Severity.MEDIUM,
                        category = "性能",
                        description = "平均响应时间过长: ${avgTime.toInt()}ms",
                        affectedRequests = requestTimes.size
                    )
                )
            }
        }
        
        return issues
    }
    
    /**
     * 生成建议
     */
    private fun generateRecommendations(issues: List<Issue>): List<Recommendation> {
        val recommendations = mutableListOf<Recommendation>()
        
        issues.forEach { issue ->
            when (issue.category) {
                "字段解析" -> {
                    recommendations.add(
                        Recommendation(
                            priority = 1,
                            title = "改进字段识别",
                            description = "某些应用的表单字段无法正确识别，建议检查字段解析器配置",
                            actionLabel = "查看日志",
                            action = null
                        )
                    )
                }
                "密码匹配" -> {
                    recommendations.add(
                        Recommendation(
                            priority = 2,
                            title = "添加密码条目",
                            description = "未找到匹配的密码，请为这些应用添加密码条目",
                            actionLabel = "添加密码",
                            action = null
                        )
                    )
                }
                "响应构建" -> {
                    recommendations.add(
                        Recommendation(
                            priority = 1,
                            title = "检查数据完整性",
                            description = "响应构建失败可能是由于数据不完整，请检查密码条目",
                            actionLabel = "查看详情",
                            action = null
                        )
                    )
                }
                "性能" -> {
                    recommendations.add(
                        Recommendation(
                            priority = 3,
                            title = "优化性能",
                            description = "响应时间较长，建议清理缓存或减少密码条目数量",
                            actionLabel = "清理缓存",
                            action = null
                        )
                    )
                }
            }
        }
        
        return recommendations
    }
    
    /**
     * 获取统计信息
     */
    private fun getStatistics(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()
        
        stats["totalRequests"] = totalRequests
        stats["successfulRequests"] = successfulRequests
        stats["failedRequests"] = failedRequests
        
        if (totalRequests > 0) {
            stats["successRate"] = String.format("%.1f%%", successfulRequests * 100.0 / totalRequests)
        }
        
        if (requestTimes.isNotEmpty()) {
            stats["avgResponseTime"] = "${requestTimes.average().toInt()}ms"
            stats["minResponseTime"] = "${requestTimes.minOrNull()}ms"
            stats["maxResponseTime"] = "${requestTimes.maxOrNull()}ms"
        }
        
        synchronized(logEntries) {
            stats["totalLogs"] = logEntries.size
            stats["errorCount"] = logEntries.count { it.level == LogLevel.ERROR }
            stats["warningCount"] = logEntries.count { it.level == LogLevel.WARNING }
        }
        
        return stats
    }
    
    /**
     * 导出日志
     */
    fun exportLogs(): String {
        return buildString {
            appendLine("=== Monica 自动填充诊断日志 ===")
            appendLine("导出时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine()
            
            // 设备信息
            appendLine("【设备信息】")
            appendLine("制造商: ${Build.MANUFACTURER}")
            appendLine("型号: ${Build.MODEL}")
            appendLine("Android 版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ROM 类型: ${DeviceUtils.getROMType()}")
            appendLine("支持内联建议: ${DeviceUtils.supportsInlineSuggestions()}")
            appendLine()
            
            // 统计信息
            appendLine("【统计信息】")
            getStatistics().forEach { (key, value) ->
                appendLine("$key: $value")
            }
            appendLine()
            
            // 日志条目
            appendLine("【日志详情】")
            appendLine("=".repeat(60))
            synchronized(logEntries) {
                logEntries.forEach { entry ->
                    appendLine(entry.format())
                }
            }
            appendLine("=".repeat(60))
            
            appendLine()
            appendLine("=== 日志结束 ===")
        }
    }
    
    /**
     * 检查系统是否启用了自动填充服务
     */
    private fun checkSystemEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val autofillManager = context.getSystemService(android.view.autofill.AutofillManager::class.java)
                autofillManager?.hasEnabledAutofillServices() == true
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }
    
    /**
     * 清除日志
     */
    fun clear() {
        synchronized(logEntries) {
            logEntries.clear()
        }
        totalRequests = 0
        successfulRequests = 0
        failedRequests = 0
        requestTimes.clear()
        
        AutofillLogger.i(TAG, "Diagnostics cleared")
    }
}

/**
 * 诊断报告数据模型
 */
data class DiagnosticReport(
    val timestamp: Long,
    val deviceInfo: DeviceInfo,
    val serviceStatus: ServiceStatusInfo,
    val recentRequests: List<RequestInfo>,
    val detectedIssues: List<Issue>,
    val recommendations: List<Recommendation>,
    val statistics: Map<String, Any>
)

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val romType: String,
    val supportsInlineSuggestions: Boolean
)

data class ServiceStatusInfo(
    val isServiceDeclared: Boolean,
    val isSystemEnabled: Boolean,
    val isAppEnabled: Boolean,
    val hasPermissions: Boolean
)

data class RequestInfo(
    val timestamp: Long,
    val packageName: String,
    val domain: String?,
    val fieldsDetected: Int,
    val passwordsMatched: Int,
    val datasetsCreated: Int,
    val success: Boolean,
    val errorMessage: String?
)

data class Issue(
    val severity: Severity,
    val category: String,
    val description: String,
    val affectedRequests: Int
)

enum class Severity {
    LOW, MEDIUM, HIGH, CRITICAL
}

data class Recommendation(
    val priority: Int,
    val title: String,
    val description: String,
    val actionLabel: String?,
    val action: (() -> Unit)?
)
