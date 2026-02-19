package takagi.ru.monica.autofill.data

import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.autofill.core.AutofillLogger
import takagi.ru.monica.autofill.core.AutofillLogCategory
import takagi.ru.monica.autofill.core.PerformanceTracker

/**
 * 自动填充数据源接口
 * 
 * 定义自动填充服务需要的数据访问方法
 */
interface AutofillDataSource {
    
    /**
     * 根据域名和包名搜索密码
     * 
     * @param domain 域名（可为 null）
     * @param packageName 应用包名
     * @return 匹配的密码列表
     */
    suspend fun searchPasswords(domain: String?, packageName: String): List<PasswordEntry>
    
    /**
     * 根据 ID 获取密码
     * 
     * @param id 密码 ID
     * @return 密码条目，未找到时返回 null
     */
    suspend fun getPasswordById(id: Long): PasswordEntry?
    
    /**
     * 模糊搜索密码
     * 
     * @param query 搜索关键词
     * @return 匹配的密码列表
     */
    suspend fun fuzzySearch(query: String): List<PasswordEntry>
    
    /**
     * 获取所有密码
     * 
     * @return 所有密码列表
     */
    suspend fun getAllPasswords(): List<PasswordEntry>
    
    /**
     * 根据网站 URL 搜索
     * 
     * @param website 网站 URL
     * @return 匹配的密码列表
     */
    suspend fun searchByWebsite(website: String): List<PasswordEntry>
}

/**
 * 密码匹配结果
 * 
 * @param entry 密码条目
 * @param matchType 匹配类型
 * @param score 匹配分数 (0-100)
 */
data class PasswordMatch(
    val entry: PasswordEntry,
    val matchType: MatchType,
    val score: Int
) {
    enum class MatchType {
        /** 精确域名匹配 */
        EXACT_DOMAIN,
        
        /** 子域名匹配 */
        SUBDOMAIN,
        
        /** 包名精确匹配 */
        EXACT_PACKAGE,
        
        /** 模糊匹配 */
        FUZZY,
        
        /** 手动选择 */
        MANUAL
    }
    
    /**
     * 是否为高质量匹配
     */
    fun isHighQualityMatch(): Boolean {
        return matchType in listOf(MatchType.EXACT_DOMAIN, MatchType.EXACT_PACKAGE) && score >= 80
    }
}

/**
 * 自动填充上下文
 * 
 * 包含填充请求的所有上下文信息
 */
data class AutofillContext(
    /** 应用包名 */
    val packageName: String,
    
    /** 域名（WebView 场景） */
    val domain: String? = null,
    
    /** 网页 URL（WebView 场景） */
    val webUrl: String? = null,
    
    /** 应用名称 */
    val appLabel: String? = null,
    
    /** 是否为 WebView */
    val isWebView: Boolean = false,
    
    /** 检测到的字段类型 */
    val detectedFields: List<String> = emptyList(),
    
    /** 附加元数据 */
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * 获取用于匹配的主键（域名或包名）
     */
    fun getPrimaryKey(): String {
        return domain ?: packageName
    }
    
    /**
     * 获取显示名称
     */
    fun getDisplayName(): String {
        return when {
            !domain.isNullOrBlank() -> domain
            !appLabel.isNullOrBlank() -> appLabel
            else -> packageName
        }
    }
}

/**
 * 自动填充缓存
 * 
 * 缓存最近的搜索结果以提高性能
 */
class AutofillCache(
    private val maxSize: Int = 50,
    private val ttlMillis: Long = 5 * 60 * 1000 // 5分钟
) {
    
    private data class CacheEntry(
        val data: List<PasswordEntry>,
        val timestamp: Long
    )
    
    private val cache = mutableMapOf<String, CacheEntry>()
    
    /**
     * 获取缓存
     */
    fun get(key: String): List<PasswordEntry>? {
        synchronized(cache) {
            val entry = cache[key] ?: return null
            
            // 检查是否过期
            if (System.currentTimeMillis() - entry.timestamp > ttlMillis) {
                cache.remove(key)
                AutofillLogger.d(
                    AutofillLogCategory.PERFORMANCE,
                    "缓存已过期",
                    mapOf("key" to key)
                )
                return null
            }
            
            AutofillLogger.d(
                AutofillLogCategory.PERFORMANCE,
                "缓存命中",
                mapOf("key" to key, "size" to entry.data.size)
            )
            
            return entry.data
        }
    }
    
    /**
     * 设置缓存
     */
    fun put(key: String, data: List<PasswordEntry>) {
        synchronized(cache) {
            // 如果超过最大容量，移除最旧的条目
            if (cache.size >= maxSize) {
                val oldestKey = cache.entries.minByOrNull { it.value.timestamp }?.key
                oldestKey?.let { cache.remove(it) }
            }
            
            cache[key] = CacheEntry(data, System.currentTimeMillis())
            
            AutofillLogger.d(
                AutofillLogCategory.PERFORMANCE,
                "缓存已设置",
                mapOf("key" to key, "size" to data.size)
            )
        }
    }
    
    /**
     * 清除缓存
     */
    fun clear() {
        synchronized(cache) {
            cache.clear()
            AutofillLogger.i(AutofillLogCategory.PERFORMANCE, "缓存已清除")
        }
    }
    
    /**
     * 移除指定缓存
     */
    fun remove(key: String) {
        synchronized(cache) {
            cache.remove(key)
            AutofillLogger.d(
                AutofillLogCategory.PERFORMANCE,
                "缓存已移除",
                mapOf("key" to key)
            )
        }
    }
    
    /**
     * 获取缓存统计
     */
    fun getStats(): Map<String, Any> {
        synchronized(cache) {
            return mapOf(
                "size" to cache.size,
                "maxSize" to maxSize,
                "ttlMillis" to ttlMillis,
                "keys" to cache.keys.toList()
            )
        }
    }
}

/**
 * 自动填充结果
 * 
 * 包含填充操作的结果信息
 */
data class AutofillResult(
    /** 匹配的密码列表 */
    val matches: List<PasswordMatch>,
    
    /** 处理时间（毫秒） */
    val processingTimeMs: Long,
    
    /** 是否成功 */
    val isSuccess: Boolean,
    
    /** 错误信息（如果有） */
    val error: String? = null,
    
    /** 上下文信息 */
    val context: AutofillContext
) {
    /**
     * 获取最佳匹配
     */
    fun getBestMatch(): PasswordMatch? {
        return matches.maxByOrNull { it.score }
    }
    
    /**
     * 获取高质量匹配
     */
    fun getHighQualityMatches(): List<PasswordMatch> {
        return matches.filter { it.isHighQualityMatch() }
    }
    
    /**
     * 是否有匹配结果
     */
    fun hasMatches(): Boolean {
        return matches.isNotEmpty()
    }
}

/**
 * 自动填充首选项
 * 
 * 定义自动填充的配置选项
 */
data class AutofillPreferencesData(
    /** 是否启用自动填充 */
    val enabled: Boolean = true,
    
    /** 是否需要生物识别 */
    val requireBiometric: Boolean = false,
    
    /** 是否启用模糊搜索 */
    val enableFuzzySearch: Boolean = true,
    
    /** 是否自动提交 */
    val autoSubmit: Boolean = false,
    
    /** 最大建议数量 */
    val maxSuggestions: Int = 5,
    
    /** 是否启用内联建议 */
    val enableInlineSuggestions: Boolean = true,
    
    /** 是否启用日志 */
    val enableLogging: Boolean = false,
    
    /** 超时时间（毫秒） */
    val timeoutMs: Long = 5000
)

