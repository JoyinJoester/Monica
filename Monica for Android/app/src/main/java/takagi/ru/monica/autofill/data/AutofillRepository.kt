package takagi.ru.monica.autofill.data

import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.autofill.core.AutofillLogger
import takagi.ru.monica.autofill.core.AutofillLogCategory
import takagi.ru.monica.autofill.core.PerformanceTracker
import takagi.ru.monica.autofill.core.AutofillException
import kotlinx.coroutines.flow.first

/**
 * 自动填充数据仓库
 * 
 * 实现 AutofillDataSource 接口，提供数据访问功能
 * 集成缓存机制以提升性能
 */
class AutofillRepository(
    private val passwordRepository: PasswordRepository,
    private val cache: AutofillCache = AutofillCache()
) : AutofillDataSource {
    
    /**
     * 根据域名和包名搜索密码
     */
    override suspend fun searchPasswords(domain: String?, packageName: String): List<PasswordEntry> {
        val tracker = PerformanceTracker("searchPasswords")
        
        try {
            val cacheKey = "search_${domain}_$packageName"
            
            // 尝试从缓存获取
            cache.get(cacheKey)?.let { cachedResults ->
                AutofillLogger.d(
                    AutofillLogCategory.MATCHING,
                    "从缓存返回搜索结果",
                    mapOf(
                        "domain" to (domain ?: "N/A"),
                        "packageName" to packageName,
                        "count" to cachedResults.size
                    )
                )
                return cachedResults
            }
            
            // 缓存未命中，从数据库查询
            val results = mutableListOf<PasswordEntry>()
            
            // 1. 如果有域名，优先按域名搜索
            if (!domain.isNullOrBlank()) {
                val domainMatches = searchByWebsite(domain)
                results.addAll(domainMatches)
                
                AutofillLogger.d(
                    AutofillLogCategory.MATCHING,
                    "域名匹配结果",
                    mapOf(
                        "domain" to domain,
                        "count" to domainMatches.size
                    )
                )
            }
            
            // 2. 按包名搜索（如果还没有结果）
            if (results.isEmpty()) {
                val allPasswords = passwordRepository.getAllPasswordEntries().first()
                val packageMatches = allPasswords.filter { entry ->
                    entry.appPackageName.equals(packageName, ignoreCase = true)
                }
                results.addAll(packageMatches)
                
                AutofillLogger.d(
                    AutofillLogCategory.MATCHING,
                    "包名匹配结果",
                    mapOf(
                        "packageName" to packageName,
                        "count" to packageMatches.size
                    )
                )
            }
            
            // 缓存结果
            cache.put(cacheKey, results)
            
            val duration = tracker.finish()
            AutofillLogger.i(
                AutofillLogCategory.MATCHING,
                "搜索完成",
                mapOf(
                    "domain" to (domain ?: "N/A"),
                    "packageName" to packageName,
                    "resultsCount" to results.size,
                    "duration_ms" to duration
                )
            )
            
            return results
            
        } catch (e: Exception) {
            AutofillLogger.e(
                AutofillLogCategory.ERROR,
                "搜索密码失败",
                error = e,
                metadata = mapOf(
                    "domain" to (domain ?: "N/A"),
                    "packageName" to packageName
                )
            )
            throw AutofillException.DatabaseError(e)
        }
    }
    
    /**
     * 根据 ID 获取密码
     */
    override suspend fun getPasswordById(id: Long): PasswordEntry? {
        val tracker = PerformanceTracker("getPasswordById")
        
        try {
            val cacheKey = "id_$id"
            
            // 尝试从缓存获取
            cache.get(cacheKey)?.firstOrNull()?.let { cachedEntry ->
                AutofillLogger.d(
                    AutofillLogCategory.MATCHING,
                    "从缓存返回密码",
                    mapOf("id" to id)
                )
                return cachedEntry
            }
            
            // 从数据库查询
            val entry = passwordRepository.getPasswordEntryById(id)
            
            // 缓存结果
            entry?.let {
                cache.put(cacheKey, listOf(it))
            }
            
            val duration = tracker.finish()
            AutofillLogger.d(
                AutofillLogCategory.MATCHING,
                "根据ID获取密码",
                mapOf(
                    "id" to id,
                    "found" to (entry != null),
                    "duration_ms" to duration
                )
            )
            
            return entry
            
        } catch (e: Exception) {
            AutofillLogger.e(
                AutofillLogCategory.ERROR,
                "获取密码失败",
                error = e,
                metadata = mapOf("id" to id)
            )
            throw AutofillException.DatabaseError(e)
        }
    }
    
    /**
     * 模糊搜索密码
     */
    override suspend fun fuzzySearch(query: String): List<PasswordEntry> {
        val tracker = PerformanceTracker("fuzzySearch")
        
        try {
            val cacheKey = "fuzzy_$query"
            
            // 尝试从缓存获取
            cache.get(cacheKey)?.let { cachedResults ->
                AutofillLogger.d(
                    AutofillLogCategory.MATCHING,
                    "从缓存返回模糊搜索结果",
                    mapOf("query" to query, "count" to cachedResults.size)
                )
                return cachedResults
            }
            
            // 从数据库查询所有密码，然后进行模糊匹配
            val allPasswords = passwordRepository.getAllPasswordEntries().first()
            val queryLower = query.lowercase()
            
            val results = allPasswords.filter { entry ->
                entry.title.lowercase().contains(queryLower) ||
                entry.username.lowercase().contains(queryLower) ||
                entry.website.lowercase().contains(queryLower) ||
                entry.appPackageName.lowercase().contains(queryLower)
            }
            
            // 缓存结果
            cache.put(cacheKey, results)
            
            val duration = tracker.finish()
            AutofillLogger.i(
                AutofillLogCategory.MATCHING,
                "模糊搜索完成",
                mapOf(
                    "query" to query,
                    "resultsCount" to results.size,
                    "duration_ms" to duration
                )
            )
            
            return results
            
        } catch (e: Exception) {
            AutofillLogger.e(
                AutofillLogCategory.ERROR,
                "模糊搜索失败",
                error = e,
                metadata = mapOf("query" to query)
            )
            throw AutofillException.DatabaseError(e)
        }
    }
    
    /**
     * 获取所有密码
     */
    override suspend fun getAllPasswords(): List<PasswordEntry> {
        val tracker = PerformanceTracker("getAllPasswords")
        
        try {
            val cacheKey = "all_passwords"
            
            // 尝试从缓存获取
            cache.get(cacheKey)?.let { cachedResults ->
                AutofillLogger.d(
                    AutofillLogCategory.MATCHING,
                    "从缓存返回所有密码",
                    mapOf("count" to cachedResults.size)
                )
                return cachedResults
            }
            
            // 从数据库查询
            val results = passwordRepository.getAllPasswordEntries().first()
            
            // 缓存结果（有效期短一些）
            cache.put(cacheKey, results)
            
            val duration = tracker.finish()
            AutofillLogger.d(
                AutofillLogCategory.MATCHING,
                "获取所有密码",
                mapOf(
                    "count" to results.size,
                    "duration_ms" to duration
                )
            )
            
            return results
            
        } catch (e: Exception) {
            AutofillLogger.e(
                AutofillLogCategory.ERROR,
                "获取所有密码失败",
                error = e
            )
            throw AutofillException.DatabaseError(e)
        }
    }
    
    /**
     * 根据网站 URL 搜索
     */
    override suspend fun searchByWebsite(website: String): List<PasswordEntry> {
        val tracker = PerformanceTracker("searchByWebsite")
        
        try {
            val cacheKey = "website_$website"
            
            // 尝试从缓存获取
            cache.get(cacheKey)?.let { cachedResults ->
                AutofillLogger.d(
                    AutofillLogCategory.MATCHING,
                    "从缓存返回网站搜索结果",
                    mapOf("website" to website, "count" to cachedResults.size)
                )
                return cachedResults
            }
            
            // 从数据库查询
            val allPasswords = passwordRepository.getAllPasswordEntries().first()
            val websiteLower = website.lowercase()
            
            val results = allPasswords.filter { entry ->
                val entryWebsite = entry.website.lowercase()
                // 精确匹配或包含关系
                entryWebsite == websiteLower ||
                entryWebsite.contains(websiteLower) ||
                websiteLower.contains(entryWebsite)
            }
            
            // 缓存结果
            cache.put(cacheKey, results)
            
            val duration = tracker.finish()
            AutofillLogger.i(
                AutofillLogCategory.MATCHING,
                "网站搜索完成",
                mapOf(
                    "website" to website,
                    "resultsCount" to results.size,
                    "duration_ms" to duration
                )
            )
            
            return results
            
        } catch (e: Exception) {
            AutofillLogger.e(
                AutofillLogCategory.ERROR,
                "网站搜索失败",
                error = e,
                metadata = mapOf("website" to website)
            )
            throw AutofillException.DatabaseError(e)
        }
    }
    
    /**
     * 清除缓存
     */
    fun clearCache() {
        cache.clear()
    }
    
    /**
     * 移除特定缓存
     */
    fun invalidateCache(domain: String?, packageName: String) {
        cache.remove("search_${domain}_$packageName")
    }
    
    /**
     * 获取缓存统计
     */
    fun getCacheStats(): Map<String, Any> {
        return cache.getStats()
    }
}

