package takagi.ru.monica.autofill.core

import kotlinx.coroutines.delay
import takagi.ru.monica.autofill.core.AutofillLogger
import takagi.ru.monica.autofill.core.AutofillLogCategory

/**
 * 自动填充异常层次结构
 * 
 * 所有自动填充相关的异常都应该继承自这个基类
 */
sealed class AutofillException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * 服务未就绪异常
     * 当服务尚未完成初始化时抛出
     */
    class ServiceNotReady(cause: Throwable? = null) : 
        AutofillException("自动填充服务未就绪", cause)
    
    /**
     * 请求超时异常
     * 当填充请求处理时间超过阈值时抛出
     */
    class RequestTimeout(val timeoutMs: Long, cause: Throwable? = null) : 
        AutofillException("填充请求超时 (${timeoutMs}ms)", cause)
    
    /**
     * 未找到匹配密码异常
     * 当无法找到匹配的密码条目时抛出
     */
    class NoMatchingPassword(val domain: String, val packageName: String) : 
        AutofillException("未找到匹配的密码 - 域名: $domain, 包名: $packageName")
    
    /**
     * 结构解析失败异常
     * 当无法解析 AssistStructure 时抛出
     */
    class ParsingFailed(cause: Throwable) : 
        AutofillException("页面结构解析失败", cause)
    
    /**
     * 字段填充失败异常
     * 当填充特定字段失败时抛出
     */
    class FillingFailed(val fieldId: String, cause: Throwable) : 
        AutofillException("字段填充失败: $fieldId", cause)
    
    /**
     * 生物识别验证失败异常
     * 当生物识别认证失败时抛出
     */
    class BiometricAuthFailed(val reason: String, cause: Throwable? = null) : 
        AutofillException("生物识别验证失败: $reason", cause)
    
    /**
     * 数据库操作失败异常
     * 当数据库查询或更新失败时抛出
     */
    class DatabaseError(cause: Throwable) : 
        AutofillException("数据库操作失败", cause)
    
    /**
     * 无效的填充请求
     * 当请求数据格式不正确时抛出
     */
    class InvalidRequest(val reason: String) : 
        AutofillException("无效的填充请求: $reason")
    
    /**
     * 权限不足异常
     * 当缺少必要权限时抛出
     */
    class PermissionDenied(val permission: String) : 
        AutofillException("权限不足: $permission")
    
    /**
     * 配置错误异常
     * 当配置不正确时抛出
     */
    class ConfigurationError(val config: String, cause: Throwable? = null) : 
        AutofillException("配置错误: $config", cause)
}

/**
 * 错误恢复管理器
 * 
 * 提供统一的错误处理和恢复策略
 */
class ErrorRecoveryManager {
    
    /**
     * 不应该重试的异常类型
     */
    private val nonRetryableExceptions = setOf(
        AutofillException.ServiceNotReady::class,
        AutofillException.BiometricAuthFailed::class,
        AutofillException.InvalidRequest::class,
        AutofillException.PermissionDenied::class,
        AutofillException.ConfigurationError::class
    )
    
    /**
     * 执行带错误恢复的操作
     * 
     * @param operation 要执行的操作
     * @param fallback 降级方案（可选）
     * @param retryCount 重试次数（默认不重试）
     * @param retryDelayMs 重试延迟（毫秒）
     * @param beforeRetry 重试前的回调
     * @return Result 包装的结果
     */
    suspend fun <T> executeWithRecovery(
        operation: suspend () -> T,
        fallback: (suspend (Exception) -> T)? = null,
        retryCount: Int = 0,
        retryDelayMs: Long = 100,
        beforeRetry: (suspend (Int, Exception) -> Unit)? = null
    ): Result<T> {
        var lastException: Exception? = null
        
        repeat(retryCount + 1) { attempt ->
            try {
                val result = operation()
                
                if (attempt > 0) {
                    AutofillLogger.i(
                        AutofillLogCategory.ERROR,
                        "操作重试成功",
                        mapOf(
                            "attempt" to attempt,
                            "totalAttempts" to (retryCount + 1)
                        )
                    )
                }
                
                return Result.success(result)
                
            } catch (e: Exception) {
                lastException = e
                
                AutofillLogger.w(
                    AutofillLogCategory.ERROR,
                    "操作失败",
                    mapOf(
                        "attempt" to (attempt + 1),
                        "totalAttempts" to (retryCount + 1),
                        "error" to e.message.orEmpty(),
                        "errorType" to e::class.simpleName.orEmpty()
                    )
                )
                
                // 检查是否应该重试
                if (shouldNotRetry(e)) {
                    AutofillLogger.d(
                        AutofillLogCategory.ERROR,
                        "错误类型不可重试，跳过重试",
                        mapOf("errorType" to e::class.simpleName.orEmpty())
                    )
                    lastException = e
                    return@repeat // 终止重试循环
                }
                
                // 如果还有重试机会
                if (attempt < retryCount) {
                    // 执行重试前回调
                    beforeRetry?.invoke(attempt, e)
                    
                    // 指数退避延迟
                    val delay = retryDelayMs * (attempt + 1)
                    AutofillLogger.d(
                        AutofillLogCategory.ERROR,
                        "准备重试",
                        mapOf(
                            "nextAttempt" to (attempt + 2),
                            "delay_ms" to delay
                        )
                    )
                    delay(delay)
                }
            }
        }
        
        // 所有重试都失败，尝试降级方案
        val finalException = lastException
        return if (fallback != null && finalException != null) {
            try {
                AutofillLogger.i(
                    AutofillLogCategory.ERROR,
                    "尝试降级方案",
                    mapOf("originalError" to finalException.message.orEmpty())
                )
                
                val fallbackResult = fallback(finalException)
                
                AutofillLogger.i(AutofillLogCategory.ERROR, "降级方案执行成功")
                Result.success(fallbackResult)
                
            } catch (e: Exception) {
                AutofillLogger.e(
                    AutofillLogCategory.ERROR,
                    "降级方案也失败了",
                    error = e
                )
                Result.failure(e)
            }
        } else {
            AutofillLogger.e(
                AutofillLogCategory.ERROR,
                "操作最终失败，无降级方案",
                error = lastException
            )
            Result.failure(lastException ?: Exception("Unknown error"))
        }
    }
    
    /**
     * 判断异常是否不应该重试
     */
    private fun shouldNotRetry(exception: Exception): Boolean {
        return nonRetryableExceptions.any { it.isInstance(exception) }
    }
    
    /**
     * 执行带超时控制的操作
     * 
     * @param timeoutMs 超时时间（毫秒）
     * @param operation 要执行的操作
     * @return Result 包装的结果
     */
    suspend fun <T> executeWithTimeout(
        timeoutMs: Long,
        operation: suspend () -> T
    ): Result<T> {
        return try {
            kotlinx.coroutines.withTimeout(timeoutMs) {
                val result = operation()
                Result.success(result)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            AutofillLogger.e(
                AutofillLogCategory.ERROR,
                "操作超时",
                error = e,
                metadata = mapOf("timeout_ms" to timeoutMs)
            )
            Result.failure(AutofillException.RequestTimeout(timeoutMs, e))
        } catch (e: Exception) {
            AutofillLogger.e(
                AutofillLogCategory.ERROR,
                "操作执行异常",
                error = e
            )
            Result.failure(e)
        }
    }
    
    /**
     * 安全执行操作（捕获所有异常）
     * 
     * @param operation 要执行的操作
     * @param onError 错误处理回调
     * @return 操作结果，异常时返回 null
     */
    suspend fun <T> executeSafely(
        operation: suspend () -> T,
        onError: (suspend (Exception) -> Unit)? = null
    ): T? {
        return try {
            operation()
        } catch (e: Exception) {
            AutofillLogger.e(
                AutofillLogCategory.ERROR,
                "安全执行失败",
                error = e
            )
            onError?.invoke(e)
            null
        }
    }
}

/**
 * 错误报告器
 * 
 * 收集和报告错误信息，帮助诊断问题
 */
object ErrorReporter {
    
    private data class ErrorReport(
        val timestamp: Long,
        val exception: Exception,
        val context: Map<String, Any>
    )
    
    private val recentErrors = mutableListOf<ErrorReport>()
    private const val MAX_ERRORS = 100
    
    /**
     * 报告错误
     */
    fun report(exception: Exception, context: Map<String, Any> = emptyMap()) {
        synchronized(recentErrors) {
            recentErrors.add(ErrorReport(System.currentTimeMillis(), exception, context))
            if (recentErrors.size > MAX_ERRORS) {
                recentErrors.removeAt(0)
            }
        }
        
        AutofillLogger.e(
            AutofillLogCategory.ERROR,
            "错误已报告",
            error = exception,
            metadata = context
        )
    }
    
    /**
     * 获取错误统计
     */
    fun getErrorStats(): Map<String, Any> {
        synchronized(recentErrors) {
            return mapOf(
                "totalErrors" to recentErrors.size,
                "errorTypes" to recentErrors.groupingBy { it.exception::class.simpleName }
                    .eachCount(),
                "recentErrors" to recentErrors.takeLast(10).map { report ->
                    mapOf(
                        "type" to report.exception::class.simpleName,
                        "message" to report.exception.message,
                        "timestamp" to report.timestamp
                    )
                }
            )
        }
    }
    
    /**
     * 清除错误历史
     */
    fun clear() {
        synchronized(recentErrors) {
            recentErrors.clear()
        }
        AutofillLogger.i(AutofillLogCategory.ERROR, "错误历史已清除")
    }
}
