package takagi.ru.monica.autofill

import java.net.URL

/**
 * 域名匹配工具类
 * 根据不同的匹配策略判断两个域名是否匹配
 */
object DomainMatcher {
    
    /**
     * 判断目标URL是否与保存的网站URL匹配
     * @param savedWebsite 保存的网站URL
     * @param targetUrl 目标URL (当前应用/网站)
     * @param strategy 匹配策略
     * @return 是否匹配
     */
    fun matches(savedWebsite: String, targetUrl: String, strategy: DomainMatchStrategy): Boolean {
        if (savedWebsite.isBlank() || targetUrl.isBlank()) {
            return false
        }
        
        return when (strategy) {
            DomainMatchStrategy.NEVER -> false
            DomainMatchStrategy.EXACT_MATCH -> exactMatch(savedWebsite, targetUrl)
            DomainMatchStrategy.STARTS_WITH -> startsWithMatch(savedWebsite, targetUrl)
            DomainMatchStrategy.DOMAIN -> domainMatch(savedWebsite, targetUrl)
            DomainMatchStrategy.BASE_DOMAIN -> baseDomainMatch(savedWebsite, targetUrl)
            DomainMatchStrategy.REGEX -> regexMatch(savedWebsite, targetUrl)
        }
    }
    
    /**
     * 完全匹配
     */
    private fun exactMatch(saved: String, target: String): Boolean {
        return saved.equals(target, ignoreCase = true)
    }
    
    /**
     * 前缀匹配
     */
    private fun startsWithMatch(saved: String, target: String): Boolean {
        return target.startsWith(saved, ignoreCase = true)
    }
    
    /**
     * 域匹配
     */
    private fun domainMatch(saved: String, target: String): Boolean {
        val savedDomain = extractDomain(saved) ?: return false
        val targetDomain = extractDomain(target) ?: return false
        
        // 完全相同或target是saved的子域名
        return targetDomain.equals(savedDomain, ignoreCase = true) ||
               targetDomain.endsWith(".$savedDomain", ignoreCase = true)
    }
    
    /**
     * 主域名匹配
     */
    private fun baseDomainMatch(saved: String, target: String): Boolean {
        val savedBase = extractBaseDomain(saved) ?: return false
        val targetBase = extractBaseDomain(target) ?: return false
        
        return savedBase.equals(targetBase, ignoreCase = true)
    }
    
    /**
     * 正则表达式匹配
     */
    private fun regexMatch(pattern: String, target: String): Boolean {
        return try {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            regex.matches(target)
        } catch (e: Exception) {
            android.util.Log.e("DomainMatcher", "Invalid regex pattern: $pattern", e)
            false
        }
    }
    
    /**
     * 从URL中提取域名
     */
    private fun extractDomain(url: String): String? {
        return try {
            // 如果不是完整URL，尝试添加协议
            val fullUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                url
            } else {
                "https://$url"
            }
            
            val urlObj = URL(fullUrl)
            urlObj.host
        } catch (e: Exception) {
            // 如果解析失败，可能本身就是域名
            url.split("/").firstOrNull()?.split(":")?.firstOrNull()
        }
    }
    
    /**
     * 从URL中提取主域名 (例如: www.example.com -> example.com)
     */
    private fun extractBaseDomain(url: String): String? {
        val domain = extractDomain(url) ?: return null
        
        // 分割域名部分
        val parts = domain.split(".")
        
        // 如果少于2部分，直接返回
        if (parts.size < 2) {
            return domain
        }
        
        // 处理常见的二级域名后缀 (如 .co.uk, .com.cn 等)
        val twoPartTlds = setOf(
            "co.uk", "com.cn", "net.cn", "org.cn", "gov.cn", "ac.uk",
            "co.jp", "ne.jp", "or.jp", "com.au", "net.au", "org.au"
        )
        
        // 检查是否是二级域名后缀
        if (parts.size >= 3) {
            val lastTwo = "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
            if (twoPartTlds.contains(lastTwo)) {
                // 返回三个部分: example.co.uk
                return "${parts[parts.size - 3]}.$lastTwo"
            }
        }
        
        // 默认返回最后两部分: example.com
        return "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
    }
    
    /**
     * 检查是否是Android应用包名
     */
    fun isAndroidPackage(identifier: String): Boolean {
        return identifier.contains(".") && 
               !identifier.contains("/") && 
               !identifier.contains(":") &&
               !identifier.startsWith("http")
    }
}

