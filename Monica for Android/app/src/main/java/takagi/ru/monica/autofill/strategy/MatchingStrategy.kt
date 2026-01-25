package takagi.ru.monica.autofill.strategy

import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.autofill.data.AutofillContext
import takagi.ru.monica.autofill.data.PasswordMatch
import takagi.ru.monica.autofill.core.AutofillLogger
import takagi.ru.monica.autofill.core.AutofillLogCategory

/**
 * 匹配策略接口
 * 
 * 定义密码匹配的统一接口,支持多种匹配算法
 * 
 * @author Monica Team
 * @since 1.0
 */
interface MatchingStrategy {
    
    /**
     * 策略名称
     */
    val name: String
    
    /**
     * 策略优先级 (数字越大优先级越高)
     */
    val priority: Int
    
    /**
     * 是否支持当前上下文
     * 
     * @param context 自动填充上下文
     * @return true 如果此策略可以处理当前上下文
     */
    fun supports(context: AutofillContext): Boolean
    
    /**
     * 执行匹配
     * 
     * @param context 自动填充上下文
     * @param candidates 候选密码列表
     * @return 匹配结果列表,按评分从高到低排序
     */
    suspend fun match(context: AutofillContext, candidates: List<PasswordEntry>): List<PasswordMatch>
    
    /**
     * 计算匹配分数
     * 
     * @param entry 密码条目
     * @param context 自动填充上下文
     * @return 匹配分数 (0-100)
     */
    fun calculateScore(entry: PasswordEntry, context: AutofillContext): Int
}

/**
 * 抽象匹配策略基类
 * 
 * 提供通用的日志记录功能
 */
abstract class BaseMatchingStrategy : MatchingStrategy {
    
    protected fun logMatch(context: AutofillContext, resultCount: Int, duration: Long) {
        AutofillLogger.d(
            AutofillLogCategory.MATCHING,
            "[$name] 匹配完成",
            mapOf(
                "primaryKey" to context.getPrimaryKey(),
                "resultCount" to resultCount,
                "duration_ms" to duration
            )
        )
    }
    
    protected fun logNoMatch(context: AutofillContext) {
        AutofillLogger.d(
            AutofillLogCategory.MATCHING,
            "[$name] 无匹配结果",
            mapOf("primaryKey" to context.getPrimaryKey())
        )
    }
}

/**
 * 域名匹配策略
 * 
 * 支持:
 * - 精确域名匹配
 * - 子域名匹配
 * - 根域名匹配
 */
class DomainMatchingStrategy : BaseMatchingStrategy() {
    
    override val name = "DomainMatcher"
    override val priority = 100 // 最高优先级
    
    override fun supports(context: AutofillContext): Boolean {
        return !context.domain.isNullOrBlank()
    }
    
    override suspend fun match(
        context: AutofillContext,
        candidates: List<PasswordEntry>
    ): List<PasswordMatch> {
        val startTime = System.currentTimeMillis()
        
        val domain = context.domain ?: run {
            logNoMatch(context)
            return emptyList()
        }
        
        val matches = candidates.mapNotNull { entry ->
            val score = calculateScore(entry, context)
            if (score > 0) {
                val matchType = determineMatchType(entry, domain)
                PasswordMatch(entry, matchType, score)
            } else {
                null
            }
        }.sortedByDescending { it.score }
        
        val duration = System.currentTimeMillis() - startTime
        logMatch(context, matches.size, duration)
        
        return matches
    }
    
    override fun calculateScore(entry: PasswordEntry, context: AutofillContext): Int {
        val domain = context.domain ?: return 0
        val website = entry.website?.lowercase() ?: return 0
        
        return when {
            // 精确匹配: example.com == example.com
            website == domain.lowercase() -> 100
            
            // 子域名匹配: www.example.com contains example.com
            website.contains(domain.lowercase()) -> 85
            
            // 根域名匹配: example.com contains google (for accounts.google.com)
            domain.lowercase().contains(website) -> 75
            
            // 模糊包含
            extractRootDomain(website) == extractRootDomain(domain) -> 70
            
            else -> 0
        }
    }
    
    /**
     * 确定匹配类型
     */
    private fun determineMatchType(entry: PasswordEntry, domain: String): PasswordMatch.MatchType {
        val website = entry.website?.lowercase() ?: return PasswordMatch.MatchType.FUZZY
        val domainLower = domain.lowercase()
        
        return when {
            website == domainLower -> PasswordMatch.MatchType.EXACT_DOMAIN
            website.contains(domainLower) || domainLower.contains(website) -> PasswordMatch.MatchType.SUBDOMAIN
            else -> PasswordMatch.MatchType.FUZZY
        }
    }
    
    /**
     * 提取根域名
     * 
     * 例如: www.example.com -> example.com
     */
    private fun extractRootDomain(domain: String): String {
        val parts = domain.split(".")
        return if (parts.size >= 2) {
            parts.takeLast(2).joinToString(".")
        } else {
            domain
        }
    }
}

/**
 * 包名匹配策略
 * 
 * 用于原生应用的包名匹配
 */
class PackageNameMatchingStrategy : BaseMatchingStrategy() {
    
    override val name = "PackageMatcher"
    override val priority = 90
    
    override fun supports(context: AutofillContext): Boolean {
        return context.packageName.isNotBlank() && !context.isWebView
    }
    
    override suspend fun match(
        context: AutofillContext,
        candidates: List<PasswordEntry>
    ): List<PasswordMatch> {
        val startTime = System.currentTimeMillis()
        
        val matches = candidates.mapNotNull { entry ->
            val score = calculateScore(entry, context)
            if (score > 0) {
                PasswordMatch(entry, PasswordMatch.MatchType.EXACT_PACKAGE, score)
            } else {
                null
            }
        }.sortedByDescending { it.score }
        
        val duration = System.currentTimeMillis() - startTime
        logMatch(context, matches.size, duration)
        
        return matches
    }
    
    override fun calculateScore(entry: PasswordEntry, context: AutofillContext): Int {
        val appId = entry.appPackageName.lowercase()
        val packageName = context.packageName.lowercase()
        
        if (appId.isBlank()) return 0
        
        return when {
            // 精确匹配
            appId == packageName -> 100
            
            // 包含匹配
            appId.contains(packageName) || packageName.contains(appId) -> 80
            
            // 部分匹配 (com.example.app vs com.example.app.debug)
            isSimilarPackage(appId, packageName) -> 70
            
            else -> 0
        }
    }
    
    /**
     * 判断是否为相似包名
     */
    private fun isSimilarPackage(package1: String, package2: String): Boolean {
        val parts1 = package1.split(".")
        val parts2 = package2.split(".")
        
        // 至少前两段相同
        return parts1.size >= 2 && parts2.size >= 2 &&
                parts1[0] == parts2[0] && parts1[1] == parts2[1]
    }
}

/**
 * 模糊匹配策略
 * 
 * 基于文本相似度的模糊匹配
 */
class FuzzyMatchingStrategy : BaseMatchingStrategy() {
    
    override val name = "FuzzyMatcher"
    override val priority = 50 // 较低优先级
    
    override fun supports(context: AutofillContext): Boolean {
        return true // 总是支持,作为兜底策略
    }
    
    override suspend fun match(
        context: AutofillContext,
        candidates: List<PasswordEntry>
    ): List<PasswordMatch> {
        val startTime = System.currentTimeMillis()
        
        val matches = candidates.mapNotNull { entry ->
            val score = calculateScore(entry, context)
            if (score >= 70) { // 模糊匹配阈值 - 提高以减少误报
                PasswordMatch(entry, PasswordMatch.MatchType.FUZZY, score)
            } else {
                null
            }
        }.sortedByDescending { it.score }
        
        val duration = System.currentTimeMillis() - startTime
        logMatch(context, matches.size, duration)
        
        return matches
    }
    
    override fun calculateScore(entry: PasswordEntry, context: AutofillContext): Int {
        val searchKey = context.getPrimaryKey().lowercase()
        
        // 多字段匹配
        val scores = mutableListOf<Int>()
        
        // 标题匹配
        scores.add(calculateTextSimilarity(entry.title.lowercase(), searchKey))
        
        // 网站匹配
        if (entry.website.isNotBlank()) {
            scores.add(calculateTextSimilarity(entry.website.lowercase(), searchKey))
        }
        
        // 应用包名匹配
        if (entry.appPackageName.isNotBlank()) {
            scores.add(calculateTextSimilarity(entry.appPackageName.lowercase(), searchKey))
        }
        
        // 用户名匹配
        scores.add(calculateTextSimilarity(entry.username.lowercase(), searchKey))
        
        return scores.maxOrNull() ?: 0
    }
    
    /**
     * 计算文本相似度
     * 
     * 使用简单的包含关系和编辑距离
     */
    private fun calculateTextSimilarity(text: String, query: String): Int {
        return when {
            // 完全匹配
            text == query -> 100
            
            // 包含查询
            text.contains(query) -> 85
            
            // 查询包含文本
            query.contains(text) -> 80
            
            // 计算编辑距离
            else -> {
                val distance = levenshteinDistance(text, query)
                val maxLen = maxOf(text.length, query.length)
                val similarity = (1 - distance.toDouble() / maxLen) * 100
                similarity.toInt().coerceIn(0, 100)
            }
        }
    }
    
    /**
     * 计算莱文斯坦距离 (编辑距离)
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j
        
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // 删除
                    dp[i][j - 1] + 1,      // 插入
                    dp[i - 1][j - 1] + cost // 替换
                )
            }
        }
        
        return dp[len1][len2]
    }
}

/**
 * 组合匹配策略
 * 
 * 组合多个策略,按优先级依次尝试
 */
class CompositeMatchingStrategy(
    private val strategies: List<MatchingStrategy>
) : BaseMatchingStrategy() {
    
    override val name = "CompositeMatcher"
    override val priority = 0 // 不参与优先级排序
    
    init {
        // 按优先级排序
        strategies.sortedByDescending { it.priority }
    }
    
    override fun supports(context: AutofillContext): Boolean {
        return strategies.any { it.supports(context) }
    }
    
    override suspend fun match(
        context: AutofillContext,
        candidates: List<PasswordEntry>
    ): List<PasswordMatch> {
        val startTime = System.currentTimeMillis()
        val allMatches = mutableListOf<PasswordMatch>()
        val usedEntries = mutableSetOf<Long>()
        
        // 按优先级依次应用策略
        for (strategy in strategies.filter { it.supports(context) }) {
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
                "[${strategy.name}] 策略执行",
                mapOf(
                    "matches" to matches.size,
                    "totalMatches" to allMatches.size
                )
            )
        }
        
        // 按分数排序
        val sortedMatches = allMatches.sortedByDescending { it.score }
        
        val duration = System.currentTimeMillis() - startTime
        logMatch(context, sortedMatches.size, duration)
        
        return sortedMatches
    }
    
    override fun calculateScore(entry: PasswordEntry, context: AutofillContext): Int {
        // 使用最高分数
        return strategies
            .filter { it.supports(context) }
            .maxOfOrNull { it.calculateScore(entry, context) }
            ?: 0
    }
}
