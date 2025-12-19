package takagi.ru.monica.autofill.engine

import takagi.ru.monica.autofill.data.*
import takagi.ru.monica.autofill.strategy.MatchingStrategy
import takagi.ru.monica.autofill.core.*
import takagi.ru.monica.data.PasswordEntry
import android.service.autofill.FillRequest

/**
 * 自动填充核心引擎
 * 
 * 统一的自动填充业务逻辑处理引擎
 * 
 * 功能:
 * - 处理填充请求
 * - 协调各个策略进行密码匹配
 * - 集成日志、监控、错误处理
 * - 应用用户偏好设置
 * 
 * @param dataSource 数据源
 * @param strategies 匹配策略列表
 * @param preferences 用户偏好设置
 * 
 * @author Monica Team
 * @since 1.0
 */
class AutofillEngine(
    private val dataSource: AutofillDataSource,
    private val strategies: List<MatchingStrategy>,
    private val preferences: AutofillPreferencesData
) {
    
    private val errorRecovery = ErrorRecoveryManager()
    
    /**
     * 处理填充请求
     * 
     * @param context 自动填充上下文
     * @return 自动填充结果
     */
    suspend fun processRequest(context: AutofillContext): AutofillResult {
        val tracker = PerformanceTracker("processRequest")
        
        AutofillLogger.i(
            AutofillLogCategory.REQUEST,
            "开始处理填充请求",
            mapOf(
                "packageName" to context.packageName,
                "domain" to (context.domain ?: "N/A"),
                "isWebView" to context.isWebView
            )
        )
        
        // 记录请求
        MetricsCollector.recordRequest(context.packageName, context.domain)
        
        // 使用错误恢复机制执行
        val result = errorRecovery.executeWithRecovery(
            operation = { processRequestInternal(context) },
            fallback = { e ->
                AutofillLogger.w(
                    AutofillLogCategory.ERROR,
                    "使用降级方案",
                    mapOf("error" to e.message.orEmpty())
                )
                
                // 降级方案: 返回空结果
                AutofillResult(
                    matches = emptyList(),
                    processingTimeMs = 0,
                    isSuccess = false,
                    error = e.message,
                    context = context
                )
            },
            retryCount = 1
        )
        
        val duration = tracker.finish()
        
        return result.getOrElse { e ->
            AutofillLogger.e(
                AutofillLogCategory.ERROR,
                "填充请求处理失败",
                error = e as? Exception,
                metadata = mapOf("context" to context.toString())
            )
            
            MetricsCollector.recordFailure(e::class.simpleName ?: "Unknown")
            
            AutofillResult(
                matches = emptyList(),
                processingTimeMs = duration,
                isSuccess = false,
                error = e.message,
                context = context
            )
        }
    }
    
    /**
     * 内部处理逻辑
     */
    private suspend fun processRequestInternal(context: AutofillContext): AutofillResult {
        val startTime = System.currentTimeMillis()
        
        // 1. 获取候选密码
        val candidates = fetchCandidates(context)
        
        AutofillLogger.d(
            AutofillLogCategory.MATCHING,
            "获取候选密码",
            mapOf("count" to candidates.size)
        )
        
        if (candidates.isEmpty()) {
            MetricsCollector.recordNoMatch()
            return AutofillResult(
                matches = emptyList(),
                processingTimeMs = System.currentTimeMillis() - startTime,
                isSuccess = true,
                context = context
            )
        }
        
        // 2. 应用匹配策略
        val matches = applyStrategies(context, candidates)
        
        // 3. 过滤和排序
        val filteredMatches = filterAndSort(matches)
        
        // 4. 限制数量
        val limitedMatches = filteredMatches.take(preferences.maxSuggestions)
        
        val duration = System.currentTimeMillis() - startTime
        
        // 5. 记录指标
        if (filteredMatches.isNotEmpty()) {
            val bestMatch = filteredMatches.first()
            when (bestMatch.matchType) {
                PasswordMatch.MatchType.EXACT_DOMAIN,
                PasswordMatch.MatchType.EXACT_PACKAGE -> MetricsCollector.recordExactMatch()
                else -> MetricsCollector.recordFuzzyMatch()
            }
            MetricsCollector.recordSuccess(duration)
        } else {
            MetricsCollector.recordNoMatch()
        }
        
        AutofillLogger.i(
            AutofillLogCategory.REQUEST,
            "填充请求处理完成",
            mapOf(
                "matchCount" to limitedMatches.size,
                "duration_ms" to duration,
                "bestScore" to (limitedMatches.firstOrNull()?.score ?: 0)
            )
        )
        
        return AutofillResult(
            matches = limitedMatches,
            processingTimeMs = duration,
            isSuccess = true,
            context = context
        )
    }
    
    /**
     * 获取候选密码
     */
    private suspend fun fetchCandidates(context: AutofillContext): List<PasswordEntry> {
        val tracker = PerformanceTracker("fetchCandidates")
        
        return try {
            val candidates = if (preferences.enableFuzzySearch) {
                // 模糊搜索模式: 获取所有密码
                dataSource.getAllPasswords()
            } else {
                // 精确搜索模式: 只按域名/包名搜索
                dataSource.searchPasswords(context.domain, context.packageName)
            }
            
            tracker.finish()
            candidates
            
        } catch (e: Exception) {
            AutofillLogger.e(
                AutofillLogCategory.ERROR,
                "获取候选密码失败",
                error = e,
                metadata = mapOf("context" to context.toString())
            )
            throw AutofillException.DatabaseError(e)
        }
    }
    
    /**
     * 应用匹配策略
     */
    private suspend fun applyStrategies(
        context: AutofillContext,
        candidates: List<PasswordEntry>
    ): List<PasswordMatch> {
        val tracker = PerformanceTracker("applyStrategies")
        
        val allMatches = mutableListOf<PasswordMatch>()
        val usedEntries = mutableSetOf<Long>()
        
        // 按优先级应用策略
        val sortedStrategies = strategies.sortedByDescending { it.priority }
        
        for (strategy in sortedStrategies) {
            if (!strategy.supports(context)) {
                AutofillLogger.d(
                    AutofillLogCategory.MATCHING,
                    "策略不支持当前上下文",
                    mapOf("strategy" to strategy.name)
                )
                continue
            }
            
            val matches = strategy.match(context, candidates)
            
            // 去重:只添加尚未匹配过的条目
            matches.forEach { match ->
                if (match.entry.id !in usedEntries) {
                    allMatches.add(match)
                    usedEntries.add(match.entry.id)
                }
            }
            
            AutofillLogger.d(
                AutofillLogCategory.MATCHING,
                "策略执行完成",
                mapOf(
                    "strategy" to strategy.name,
                    "matches" to matches.size,
                    "totalMatches" to allMatches.size
                )
            )
        }
        
        tracker.finish()
        return allMatches
    }
    
    /**
     * 过滤和排序匹配结果
     */
    private fun filterAndSort(matches: List<PasswordMatch>): List<PasswordMatch> {
        return matches
            .filter { it.score >= 60 } // 过滤低分匹配
            .sortedWith(
                compareByDescending<PasswordMatch> { it.score }
                    .thenByDescending { it.matchType.ordinal }
                    .thenBy { it.entry.title }
            )
    }
    
    /**
     * 获取最佳匹配
     */
    suspend fun getBestMatch(context: AutofillContext): PasswordMatch? {
        val result = processRequest(context)
        return result.getBestMatch()
    }
    
    /**
     * 验证密码是否匹配
     */
    fun verifyMatch(entry: PasswordEntry, context: AutofillContext): Int {
        return strategies
            .filter { it.supports(context) }
            .maxOfOrNull { it.calculateScore(entry, context) }
            ?: 0
    }
}

/**
 * 自动填充引擎构建器
 * 
 * 提供便捷的构建方式
 */
class AutofillEngineBuilder {
    
    private var dataSource: AutofillDataSource? = null
    private val strategies = mutableListOf<MatchingStrategy>()
    private var preferences: AutofillPreferencesData = AutofillPreferencesData()
    
    /**
     * 设置数据源
     */
    fun setDataSource(dataSource: AutofillDataSource) = apply {
        this.dataSource = dataSource
    }
    
    /**
     * 添加策略
     */
    fun addStrategy(strategy: MatchingStrategy) = apply {
        strategies.add(strategy)
    }
    
    /**
     * 设置偏好
     */
    fun setPreferences(preferences: AutofillPreferencesData) = apply {
        this.preferences = preferences
    }
    
    /**
     * 构建引擎
     */
    fun build(): AutofillEngine {
        requireNotNull(dataSource) { "DataSource must be set" }
        require(strategies.isNotEmpty()) { "At least one strategy must be added" }
        
        return AutofillEngine(dataSource!!, strategies, preferences)
    }
}
