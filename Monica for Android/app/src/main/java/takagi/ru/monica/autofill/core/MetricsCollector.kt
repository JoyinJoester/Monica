package takagi.ru.monica.autofill.core

import takagi.ru.monica.autofill.core.AutofillLogger
import takagi.ru.monica.autofill.core.AutofillLogCategory

/**
 * 自动填充指标数据类
 * 
 * 记录自动填充服务的各项性能和业务指标
 */
data class AutofillMetrics(
    // ===== 请求相关 =====
    /** 总请求数 */
    var totalRequests: Int = 0,
    
    /** 成功填充次数 */
    var successfulFills: Int = 0,
    
    /** 填充失败次数 */
    var failedFills: Int = 0,
    
    /** 用户取消次数 */
    var cancelledRequests: Int = 0,
    
    // ===== 性能相关 =====
    /** 总响应时间列表 (ms) */
    val responseTimes: MutableList<Long> = mutableListOf(),
    
    /** 匹配耗时列表 (ms) */
    val matchingTimes: MutableList<Long> = mutableListOf(),
    
    /** 填充耗时列表 (ms) */
    val fillingTimes: MutableList<Long> = mutableListOf(),
    
    // ===== 匹配相关 =====
    /** 精确匹配次数 */
    var exactMatches: Int = 0,
    
    /** 模糊匹配次数 */
    var fuzzyMatches: Int = 0,
    
    /** 未找到匹配次数 */
    var noMatches: Int = 0,
    
    // ===== 来源统计 =====
    /** 应用包名统计 (packageName -> count) */
    val sourceApps: MutableMap<String, Int> = mutableMapOf(),
    
    /** 域名统计 (domain -> count) */
    val sourceDomains: MutableMap<String, Int> = mutableMapOf(),
    
    // ===== 错误统计 =====
    /** 错误类型统计 (errorType -> count) */
    val errorTypes: MutableMap<String, Int> = mutableMapOf()
) {
    /**
     * 计算成功率
     */
    fun getSuccessRate(): Double {
        return if (totalRequests > 0) {
            (successfulFills.toDouble() / totalRequests) * 100
        } else {
            0.0
        }
    }
    
    /**
     * 计算平均响应时间
     */
    fun getAverageResponseTime(): Long {
        return if (responseTimes.isNotEmpty()) {
            responseTimes.average().toLong()
        } else {
            0
        }
    }
    
    /**
     * 获取第95百分位响应时间
     */
    fun get95thPercentileResponseTime(): Long {
        if (responseTimes.isEmpty()) return 0
        val sorted = responseTimes.sorted()
        val index = (sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)
        return sorted[index]
    }
    
    /**
     * 获取第99百分位响应时间
     */
    fun get99thPercentileResponseTime(): Long {
        if (responseTimes.isEmpty()) return 0
        val sorted = responseTimes.sorted()
        val index = (sorted.size * 0.99).toInt().coerceAtMost(sorted.size - 1)
        return sorted[index]
    }
    
    /**
     * 获取最大响应时间
     */
    fun getMaxResponseTime(): Long {
        return responseTimes.maxOrNull() ?: 0
    }
    
    /**
     * 获取最小响应时间
     */
    fun getMinResponseTime(): Long {
        return responseTimes.minOrNull() ?: 0
    }
    
    /**
     * 格式化输出统计信息
     */
    fun formatStats(): String {
        return buildString {
            appendLine("=== 自动填充统计信息 ===")
            appendLine("总请求数: $totalRequests")
            appendLine("成功率: ${"%.2f".format(getSuccessRate())}%")
            appendLine("成功: $successfulFills | 失败: $failedFills | 取消: $cancelledRequests")
            appendLine()
            
            appendLine("性能指标:")
            appendLine("  平均响应时间: ${getAverageResponseTime()} ms")
            appendLine("  P95响应时间: ${get95thPercentileResponseTime()} ms")
            appendLine("  P99响应时间: ${get99thPercentileResponseTime()} ms")
            appendLine("  最大响应时间: ${getMaxResponseTime()} ms")
            appendLine("  最小响应时间: ${getMinResponseTime()} ms")
            appendLine()
            
            appendLine("匹配统计:")
            appendLine("  精确匹配: $exactMatches")
            appendLine("  模糊匹配: $fuzzyMatches")
            appendLine("  未找到: $noMatches")
            appendLine()
            
            if (sourceApps.isNotEmpty()) {
                appendLine("Top 5 应用:")
                sourceApps.entries.sortedByDescending { it.value }
                    .take(5)
                    .forEach { (app, count) ->
                        appendLine("  $app: $count 次")
                    }
                appendLine()
            }
            
            if (sourceDomains.isNotEmpty()) {
                appendLine("Top 5 域名:")
                sourceDomains.entries.sortedByDescending { it.value }
                    .take(5)
                    .forEach { (domain, count) ->
                        appendLine("  $domain: $count 次")
                    }
                appendLine()
            }
            
            if (errorTypes.isNotEmpty()) {
                appendLine("错误类型:")
                errorTypes.entries.sortedByDescending { it.value }
                    .forEach { (error, count) ->
                        appendLine("  $error: $count 次")
                    }
            }
        }
    }
}

/**
 * 指标收集器
 * 
 * 负责收集和聚合自动填充服务的各项指标
 */
object MetricsCollector {
    
    private val metrics = AutofillMetrics()
    
    /**
     * 记录新的填充请求
     */
    fun recordRequest(packageName: String, domain: String?) {
        synchronized(metrics) {
            metrics.totalRequests++
            metrics.sourceApps[packageName] = (metrics.sourceApps[packageName] ?: 0) + 1
            domain?.let {
                metrics.sourceDomains[it] = (metrics.sourceDomains[it] ?: 0) + 1
            }
        }
        
        AutofillLogger.d(
            AutofillLogCategory.PERFORMANCE,
            "记录新请求",
            mapOf(
                "packageName" to packageName,
                "domain" to (domain ?: "N/A"),
                "totalRequests" to metrics.totalRequests
            )
        )
    }
    
    /**
     * 记录填充成功
     */
    fun recordSuccess(responseTime: Long, matchingTime: Long = 0, fillingTime: Long = 0) {
        synchronized(metrics) {
            metrics.successfulFills++
            metrics.responseTimes.add(responseTime)
            
            if (matchingTime > 0) {
                metrics.matchingTimes.add(matchingTime)
            }
            
            if (fillingTime > 0) {
                metrics.fillingTimes.add(fillingTime)
            }
            
            // 限制列表大小，避免内存膨胀
            if (metrics.responseTimes.size > 1000) {
                metrics.responseTimes.removeAt(0)
            }
            if (metrics.matchingTimes.size > 1000) {
                metrics.matchingTimes.removeAt(0)
            }
            if (metrics.fillingTimes.size > 1000) {
                metrics.fillingTimes.removeAt(0)
            }
        }
        
        AutofillLogger.i(
            AutofillLogCategory.PERFORMANCE,
            "填充成功",
            mapOf(
                "responseTime" to responseTime,
                "successRate" to "%.2f%%".format(metrics.getSuccessRate())
            )
        )
    }
    
    /**
     * 记录填充失败
     */
    fun recordFailure(errorType: String = "Unknown") {
        synchronized(metrics) {
            metrics.failedFills++
            metrics.errorTypes[errorType] = (metrics.errorTypes[errorType] ?: 0) + 1
        }
        
        AutofillLogger.w(
            AutofillLogCategory.PERFORMANCE,
            "填充失败",
            mapOf(
                "errorType" to errorType,
                "totalFailures" to metrics.failedFills
            )
        )
    }
    
    /**
     * 记录用户取消
     */
    fun recordCancellation() {
        synchronized(metrics) {
            metrics.cancelledRequests++
        }
        
        AutofillLogger.d(
            AutofillLogCategory.USER_ACTION,
            "用户取消填充",
            mapOf("totalCancellations" to metrics.cancelledRequests)
        )
    }
    
    /**
     * 记录精确匹配
     */
    fun recordExactMatch() {
        synchronized(metrics) {
            metrics.exactMatches++
        }
    }
    
    /**
     * 记录模糊匹配
     */
    fun recordFuzzyMatch() {
        synchronized(metrics) {
            metrics.fuzzyMatches++
        }
    }
    
    /**
     * 记录未找到匹配
     */
    fun recordNoMatch() {
        synchronized(metrics) {
            metrics.noMatches++
        }
        
        AutofillLogger.w(
            AutofillLogCategory.MATCHING,
            "未找到匹配的密码",
            mapOf("totalNoMatches" to metrics.noMatches)
        )
    }
    
    /**
     * 获取当前指标的副本
     */
    fun getMetrics(): AutofillMetrics {
        synchronized(metrics) {
            return metrics.copy(
                responseTimes = metrics.responseTimes.toMutableList(),
                matchingTimes = metrics.matchingTimes.toMutableList(),
                fillingTimes = metrics.fillingTimes.toMutableList(),
                sourceApps = metrics.sourceApps.toMutableMap(),
                sourceDomains = metrics.sourceDomains.toMutableMap(),
                errorTypes = metrics.errorTypes.toMutableMap()
            )
        }
    }
    
    /**
     * 重置所有指标
     */
    fun reset() {
        synchronized(metrics) {
            metrics.totalRequests = 0
            metrics.successfulFills = 0
            metrics.failedFills = 0
            metrics.cancelledRequests = 0
            metrics.responseTimes.clear()
            metrics.matchingTimes.clear()
            metrics.fillingTimes.clear()
            metrics.exactMatches = 0
            metrics.fuzzyMatches = 0
            metrics.noMatches = 0
            metrics.sourceApps.clear()
            metrics.sourceDomains.clear()
            metrics.errorTypes.clear()
        }
        
        AutofillLogger.i(AutofillLogCategory.PERFORMANCE, "指标已重置")
    }
    
    /**
     * 获取格式化的统计信息
     */
    fun getFormattedStats(): String {
        return synchronized(metrics) {
            metrics.formatStats()
        }
    }
}

/**
 * 性能跟踪器
 * 
 * 用于跟踪单个操作的耗时
 */
class PerformanceTracker(private val operationName: String) {
    
    private val startTime = System.currentTimeMillis()
    private var isPaused = false
    private var pauseTime = 0L
    private var totalPauseDuration = 0L
    
    /**
     * 暂停计时
     */
    fun pause() {
        if (!isPaused) {
            pauseTime = System.currentTimeMillis()
            isPaused = true
        }
    }
    
    /**
     * 恢复计时
     */
    fun resume() {
        if (isPaused) {
            totalPauseDuration += System.currentTimeMillis() - pauseTime
            isPaused = false
        }
    }
    
    /**
     * 完成操作，返回耗时
     */
    fun finish(): Long {
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime - totalPauseDuration
        
        AutofillLogger.d(
            AutofillLogCategory.PERFORMANCE,
            "操作完成",
            mapOf(
                "operation" to operationName,
                "duration_ms" to duration,
                "pause_duration_ms" to totalPauseDuration
            )
        )
        
        return duration
    }
    
    /**
     * 获取当前已消耗时间（不包括暂停时间）
     */
    fun getElapsedTime(): Long {
        val now = System.currentTimeMillis()
        val pauseDuration = if (isPaused) {
            now - pauseTime + totalPauseDuration
        } else {
            totalPauseDuration
        }
        return now - startTime - pauseDuration
    }
}

