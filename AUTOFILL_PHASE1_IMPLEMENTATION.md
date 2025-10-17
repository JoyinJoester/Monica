# 自动填充功能优化 - 阶段 1 实施方案

## 📋 阶段目标
**时间**: 2周  
**目标**: 重构基础架构，建立日志与监控体系，为后续优化奠定基础

---

## 🎯 核心任务

### 任务 1: 架构重构
**优先级**: P0  
**预估工时**: 5天

#### 1.1 服务层解耦

**现状问题**:
```kotlin
class MonicaAutofillService : AutofillService() {
    // 数据访问、业务逻辑、UI展示混在一起
    private lateinit var passwordRepository: PasswordRepository
    private val enhancedParserV2 = EnhancedAutofillStructureParserV2()
    
    override fun onFillRequest(...) {
        // 超过 400 行的复杂逻辑
    }
}
```

**重构方案**:
```kotlin
// 1. 数据层
interface AutofillDataSource {
    suspend fun searchPasswords(domain: String, packageName: String): List<PasswordEntry>
    suspend fun getPasswordById(id: Long): PasswordEntry?
}

class AutofillRepository(
    private val passwordRepository: PasswordRepository,
    private val cache: AutofillCache
) : AutofillDataSource {
    override suspend fun searchPasswords(domain: String, packageName: String): List<PasswordEntry> {
        // 实现缓存 + 数据库查询
    }
}

// 2. 策略层
interface MatchingStrategy {
    suspend fun match(context: AutofillContext): List<PasswordMatch>
}

class DomainMatchingStrategy : MatchingStrategy { ... }
class PackageNameMatchingStrategy : MatchingStrategy { ... }
class FuzzyMatchingStrategy : MatchingStrategy { ... }

// 3. 业务层
class AutofillEngine(
    private val dataSource: AutofillDataSource,
    private val strategies: List<MatchingStrategy>,
    private val preferences: AutofillPreferences
) {
    suspend fun processRequest(request: FillRequest): AutofillResult {
        // 统一的填充逻辑
    }
}

// 4. 服务层（简化）
class MonicaAutofillService : AutofillService() {
    private lateinit var engine: AutofillEngine
    
    override fun onFillRequest(...) {
        serviceScope.launch {
            val result = engine.processRequest(request)
            callback.onSuccess(result.toFillResponse())
        }
    }
}
```

#### 1.2 依赖注入

引入简单的 DI 容器:
```kotlin
object AutofillDI {
    fun provideEngine(context: Context): AutofillEngine {
        val database = PasswordDatabase.getDatabase(context)
        val repository = PasswordRepository(database.passwordEntryDao())
        val cache = AutofillCache()
        val dataSource = AutofillRepository(repository, cache)
        val preferences = AutofillPreferences(context)
        
        val strategies = listOf(
            DomainMatchingStrategy(),
            PackageNameMatchingStrategy(),
            FuzzyMatchingStrategy()
        )
        
        return AutofillEngine(dataSource, strategies, preferences)
    }
}
```

---

### 任务 2: 统一日志模块
**优先级**: P0  
**预估工时**: 2天

#### 2.1 日志框架设计

```kotlin
/**
 * 自动填充日志系统
 * - 支持分级别日志
 * - 自动脱敏敏感信息
 * - 本地持久化（可选）
 * - 导出诊断报告
 */
object AutofillLogger {
    private const val TAG = "MonicaAutofill"
    
    enum class Level { DEBUG, INFO, WARN, ERROR }
    
    data class LogEntry(
        val timestamp: Long,
        val level: Level,
        val category: String,
        val message: String,
        val metadata: Map<String, Any> = emptyMap()
    )
    
    private val logs = mutableListOf<LogEntry>()
    private var isEnabled = BuildConfig.DEBUG
    
    fun d(category: String, message: String, metadata: Map<String, Any> = emptyMap()) {
        log(Level.DEBUG, category, message, metadata)
    }
    
    fun i(category: String, message: String, metadata: Map<String, Any> = emptyMap()) {
        log(Level.INFO, category, message, metadata)
    }
    
    fun w(category: String, message: String, metadata: Map<String, Any> = emptyMap()) {
        log(Level.WARN, category, message, metadata)
    }
    
    fun e(category: String, message: String, error: Throwable? = null, metadata: Map<String, Any> = emptyMap()) {
        val meta = metadata.toMutableMap()
        error?.let { meta["error"] = it.toString() }
        log(Level.ERROR, category, message, meta)
    }
    
    private fun log(level: Level, category: String, message: String, metadata: Map<String, Any>) {
        // 自动脱敏
        val sanitizedMessage = sanitize(message)
        val sanitizedMetadata = metadata.mapValues { sanitize(it.value.toString()) }
        
        // 控制台输出
        val logMessage = "[$category] $sanitizedMessage ${sanitizedMetadata.takeIf { it.isNotEmpty() }?.toString() ?: ""}"
        when (level) {
            Level.DEBUG -> Log.d(TAG, logMessage)
            Level.INFO -> Log.i(TAG, logMessage)
            Level.WARN -> Log.w(TAG, logMessage)
            Level.ERROR -> Log.e(TAG, logMessage)
        }
        
        // 内存存储（最近 500 条）
        if (isEnabled) {
            synchronized(logs) {
                logs.add(LogEntry(System.currentTimeMillis(), level, category, sanitizedMessage, sanitizedMetadata))
                if (logs.size > 500) logs.removeAt(0)
            }
        }
    }
    
    /**
     * 脱敏处理
     */
    private fun sanitize(text: String): String {
        return text
            .replace(Regex("password[\"']?\\s*[:=]\\s*[\"']?([^\"',}\\s]+)", RegexOption.IGNORE_CASE), "password=***")
            .replace(Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"), "***@***.com")
            .replace(Regex("\\b\\d{11}\\b"), "***********")
    }
    
    /**
     * 导出日志
     */
    fun exportLogs(): String {
        return synchronized(logs) {
            logs.joinToString("\n") { entry ->
                "${entry.timestamp} [${entry.level}] ${entry.category}: ${entry.message}"
            }
        }
    }
    
    /**
     * 清除日志
     */
    fun clear() {
        synchronized(logs) { logs.clear() }
    }
}
```

#### 2.2 日志分类

```kotlin
object AutofillLogCategory {
    const val SERVICE = "Service"        // 服务生命周期
    const val REQUEST = "Request"        // 填充请求
    const val MATCHING = "Matching"      // 数据匹配
    const val PARSING = "Parsing"        // 结构解析
    const val FILLING = "Filling"        // 字段填充
    const val PERFORMANCE = "Perf"       // 性能监控
    const val ERROR = "Error"            // 错误追踪
    const val USER_ACTION = "UserAction" // 用户操作
}
```

---

### 任务 3: 埋点与监控
**优先级**: P1  
**预估工时**: 3天

#### 3.1 指标定义

```kotlin
data class AutofillMetrics(
    // 请求相关
    var totalRequests: Int = 0,
    var successfulFills: Int = 0,
    var failedFills: Int = 0,
    var cancelledRequests: Int = 0,
    
    // 性能相关
    val responseTimes: MutableList<Long> = mutableListOf(),
    val matchingTimes: MutableList<Long> = mutableListOf(),
    val fillingTimes: MutableList<Long> = mutableListOf(),
    
    // 匹配相关
    var exactMatches: Int = 0,
    var fuzzyMatches: Int = 0,
    var noMatches: Int = 0,
    
    // 来源统计
    val sourceApps: MutableMap<String, Int> = mutableMapOf(),
    val sourceDomains: MutableMap<String, Int> = mutableMapOf()
) {
    fun getSuccessRate(): Double {
        return if (totalRequests > 0) successfulFills.toDouble() / totalRequests else 0.0
    }
    
    fun getAverageResponseTime(): Long {
        return if (responseTimes.isNotEmpty()) responseTimes.average().toLong() else 0
    }
    
    fun get95thPercentile(): Long {
        if (responseTimes.isEmpty()) return 0
        val sorted = responseTimes.sorted()
        val index = (sorted.size * 0.95).toInt()
        return sorted[index]
    }
}

object MetricsCollector {
    private val metrics = AutofillMetrics()
    
    fun recordRequest(packageName: String, domain: String?) {
        metrics.totalRequests++
        metrics.sourceApps[packageName] = (metrics.sourceApps[packageName] ?: 0) + 1
        domain?.let { 
            metrics.sourceDomains[it] = (metrics.sourceDomains[it] ?: 0) + 1 
        }
    }
    
    fun recordSuccess(responseTime: Long) {
        metrics.successfulFills++
        metrics.responseTimes.add(responseTime)
    }
    
    fun recordFailure() {
        metrics.failedFills++
    }
    
    fun getMetrics(): AutofillMetrics = metrics.copy()
    
    fun reset() {
        // 重置统计
    }
}
```

#### 3.2 性能监控

```kotlin
class PerformanceTracker(private val name: String) {
    private val startTime = System.currentTimeMillis()
    
    fun finish(): Long {
        val duration = System.currentTimeMillis() - startTime
        AutofillLogger.d(
            AutofillLogCategory.PERFORMANCE,
            "Operation completed",
            mapOf(
                "operation" to name,
                "duration_ms" to duration
            )
        )
        return duration
    }
}

// 使用示例
suspend fun processRequest(request: FillRequest): AutofillResult {
    val tracker = PerformanceTracker("fillRequest")
    try {
        // ... 处理逻辑
        val duration = tracker.finish()
        MetricsCollector.recordSuccess(duration)
    } catch (e: Exception) {
        MetricsCollector.recordFailure()
        throw e
    }
}
```

---

### 任务 4: 错误处理增强
**优先级**: P1  
**预估工时**: 2天

#### 4.1 异常分类

```kotlin
sealed class AutofillException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ServiceNotReady(cause: Throwable? = null) : AutofillException("自动填充服务未就绪", cause)
    class RequestTimeout(cause: Throwable? = null) : AutofillException("填充请求超时", cause)
    class NoMatchingPassword(val domain: String) : AutofillException("未找到匹配的密码: $domain")
    class ParsingFailed(cause: Throwable) : AutofillException("页面结构解析失败", cause)
    class FillingFailed(val fieldId: String, cause: Throwable) : AutofillException("字段填充失败: $fieldId", cause)
    class BiometricAuthFailed(cause: Throwable? = null) : AutofillException("生物识别验证失败", cause)
    class DatabaseError(cause: Throwable) : AutofillException("数据库操作失败", cause)
}
```

#### 4.2 错误恢复策略

```kotlin
class ErrorRecoveryManager {
    suspend fun <T> executeWithRecovery(
        operation: suspend () -> T,
        fallback: (suspend (Exception) -> T)? = null,
        retryCount: Int = 0
    ): Result<T> {
        var lastException: Exception? = null
        
        repeat(retryCount + 1) { attempt ->
            try {
                val result = operation()
                if (attempt > 0) {
                    AutofillLogger.i(AutofillLogCategory.ERROR, "操作重试成功", mapOf("attempt" to attempt))
                }
                return Result.success(result)
            } catch (e: Exception) {
                lastException = e
                AutofillLogger.w(
                    AutofillLogCategory.ERROR,
                    "操作失败",
                    mapOf("attempt" to attempt, "error" to e.message.orEmpty())
                )
                
                // 某些错误不重试
                if (e is AutofillException.ServiceNotReady || 
                    e is AutofillException.BiometricAuthFailed) {
                    break
                }
                
                // 延迟后重试
                if (attempt < retryCount) {
                    delay(100L * (attempt + 1))
                }
            }
        }
        
        // 尝试降级方案
        return if (fallback != null && lastException != null) {
            try {
                Result.success(fallback(lastException))
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(lastException ?: Exception("Unknown error"))
        }
    }
}
```

---

## 📊 验收标准

### 功能验收
- [ ] 服务层成功解耦为 数据层/策略层/业务层/服务层
- [ ] 所有日志使用 `AutofillLogger` 统一管理
- [ ] 实现日志导出功能并验证脱敏效果
- [ ] 埋点系统记录所有关键操作
- [ ] 错误处理覆盖所有主要异常场景

### 性能验收
- [ ] 重构后响应时间不超过重构前的 1.1 倍
- [ ] 内存占用增加不超过 5MB
- [ ] 启动时间增加不超过 100ms

### 代码质量
- [ ] 单元测试覆盖率 ≥ 60%
- [ ] 所有 public 方法有 KDoc 注释
- [ ] 通过 ktlint 代码规范检查

---

## 🗓️ 时间规划

| 日期 | 任务 | 产出 |
|------|------|------|
| Day 1-2 | 架构设计评审 | 架构文档 v1.0 |
| Day 3-5 | 数据层/策略层实现 | 代码 + 单元测试 |
| Day 6-7 | 日志模块实现 | 日志系统 + 文档 |
| Day 8-9 | 埋点与监控 | 指标采集 + Dashboard |
| Day 10-11 | 错误处理 | 异常体系 + 恢复策略 |
| Day 12-13 | 集成测试 | 测试报告 |
| Day 14 | Code Review & 总结 | 阶段报告 |

---

## 📝 下一阶段预告

阶段 2 (数据匹配算法增强) 将基于本阶段的架构，实现:
1. 多语言字段词典
2. 智能域名匹配
3. 场景识别模型
4. 手动标注机制

---

## 📌 注意事项

1. **向后兼容**: 确保重构不影响现有功能
2. **增量迭代**: 按模块逐步替换，避免大爆炸式改动
3. **充分测试**: 每个模块完成后立即编写测试
4. **文档同步**: 代码与文档同步更新
5. **性能监控**: 持续关注性能指标变化

---

> 本阶段是整个优化计划的基石，确保基础扎实才能支撑后续复杂功能的开发。
