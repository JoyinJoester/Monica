package takagi.ru.monica.autofill.core

import android.app.assist.AssistStructure
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.autofill.DomainMatcher
import takagi.ru.monica.autofill.DomainMatchStrategy

/**
 * 增强的密码匹配引擎
 * 
 * 提供更智能的密码匹配逻辑，支持多种匹配策略
 * 
 * 功能:
 * - 精确包名匹配
 * - 域名匹配（精确、子域名、基础域名）
 * - 模糊标题匹配
 * - 增强的域名提取（支持 Chrome 等浏览器）
 * - 匹配结果排序和评分
 * - 详细的诊断信息
 * 
 * @author Monica Team
 * @since 2.0
 */
class EnhancedPasswordMatcher(
    private val matchStrategy: DomainMatchStrategy = DomainMatchStrategy.BASE_DOMAIN
) {
    
    companion object {
        private const val TAG = "EnhancedPasswordMatcher"
        
        // Chrome 浏览器包名
        private val CHROME_PACKAGES = listOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary"
        )
        
        // 常见浏览器包名
        private val BROWSER_PACKAGES = listOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.brave.browser",
            "com.kiwibrowser.browser",
            "com.UCMobile.intl",
            "mark.via.gp"
        ) + CHROME_PACKAGES
    }
    
    /**
     * 匹配结果
     */
    data class MatchResult(
        val matches: List<PasswordMatch>,
        val matchStrategy: String,
        val confidence: Float,
        val diagnostics: MatchDiagnostics
    ) {
        /**
         * 是否有匹配结果
         */
        fun hasMatches(): Boolean = matches.isNotEmpty()
        
        /**
         * 获取最佳匹配
         */
        fun getBestMatch(): PasswordMatch? = matches.firstOrNull()
        
        /**
         * 按匹配类型分组
         */
        fun groupByMatchType(): Map<MatchType, List<PasswordMatch>> {
            return matches.groupBy { it.matchType }
        }
    }
    
    /**
     * 密码匹配项
     */
    data class PasswordMatch(
        val entry: PasswordEntry,
        val matchType: MatchType,
        val score: Float,
        val reason: String
    ) : Comparable<PasswordMatch> {
        override fun compareTo(other: PasswordMatch): Int {
            // 首先按匹配类型优先级排序
            val typePriority = matchType.priority.compareTo(other.matchType.priority)
            if (typePriority != 0) return typePriority
            
            // 然后按分数排序（降序）
            return other.score.compareTo(score)
        }
    }
    
    /**
     * 匹配类型
     */
    enum class MatchType(val priority: Int) {
        EXACT_PACKAGE(1),      // 精确包名匹配
        EXACT_DOMAIN(2),       // 精确域名匹配
        SUBDOMAIN(3),          // 子域名匹配
        BASE_DOMAIN(4),        // 基础域名匹配
        FUZZY_TITLE(5),        // 模糊标题匹配
        MANUAL(6)              // 手动关联
    }
    
    /**
     * 匹配诊断信息
     */
    data class MatchDiagnostics(
        val packageName: String,
        val extractedDomain: String?,
        val totalPasswordsChecked: Int,
        val matchesByType: Map<MatchType, Int>,
        val extractionMethod: String
    )
    
    /**
     * 查找匹配的密码
     * 
     * @param packageName 应用包名
     * @param structure AssistStructure 用于提取域名
     * @param allPasswords 所有密码条目
     * @return 匹配结果
     */
    fun findMatches(
        packageName: String,
        structure: AssistStructure,
        allPasswords: List<PasswordEntry>
    ): MatchResult {
        AutofillLogger.d(TAG, "Starting password matching for package: $packageName")
        
        // 1. 提取域名（如果是浏览器或 WebView）
        val (domain, extractionMethod) = extractDomain(structure, packageName)
        
        if (domain != null) {
            AutofillLogger.d(TAG, "Extracted domain: $domain (method: $extractionMethod)")
        } else {
            AutofillLogger.d(TAG, "No domain extracted, using package name only")
        }
        
        // 2. 执行多层匹配
        val matches = performMultiLayerMatching(packageName, domain, allPasswords)
        
        // 3. 排序和过滤
        val sortedMatches = sortAndFilterMatches(matches)
        
        // 4. 计算置信度
        val confidence = calculateConfidence(sortedMatches)
        
        // 5. 生成诊断信息
        val diagnostics = MatchDiagnostics(
            packageName = packageName,
            extractedDomain = domain,
            totalPasswordsChecked = allPasswords.size,
            matchesByType = sortedMatches.groupBy { it.matchType }.mapValues { it.value.size },
            extractionMethod = extractionMethod
        )
        
        AutofillLogger.i(TAG, "Found ${sortedMatches.size} matches (confidence: $confidence)")
        
        return MatchResult(
            matches = sortedMatches,
            matchStrategy = matchStrategy.name,
            confidence = confidence,
            diagnostics = diagnostics
        )
    }
    
    /**
     * 增强的域名提取
     * 支持多种提取方法
     * 
     * @return Pair<域名, 提取方法>
     */
    private fun extractDomain(
        structure: AssistStructure,
        packageName: String
    ): Pair<String?, String> {
        // 方法1: 从 webDomain 属性提取
        val webDomain = extractFromWebDomain(structure)
        if (webDomain != null) {
            return Pair(webDomain, "webDomain")
        }
        
        // 方法2: 从节点文本提取（Chrome地址栏）
        if (isBrowserPackage(packageName)) {
            val textDomain = extractFromNodeText(structure)
            if (textDomain != null) {
                return Pair(textDomain, "nodeText")
            }
            
            // 方法3: 从 contentDescription 提取
            val descDomain = extractFromContentDescription(structure)
            if (descDomain != null) {
                return Pair(descDomain, "contentDescription")
            }
        }
        
        // 方法4: 从包名推断（某些浏览器）
        val inferredDomain = inferDomainFromPackage(packageName)
        if (inferredDomain != null) {
            return Pair(inferredDomain, "packageInference")
        }
        
        return Pair(null, "none")
    }
    
    /**
     * 从 webDomain 属性提取域名
     */
    private fun extractFromWebDomain(structure: AssistStructure): String? {
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val domain = findWebDomainInNode(windowNode.rootViewNode)
            if (domain != null) {
                return cleanDomain(domain)
            }
        }
        return null
    }
    
    /**
     * 递归查找 webDomain
     */
    private fun findWebDomainInNode(node: AssistStructure.ViewNode): String? {
        node.webDomain?.let { return it }
        
        for (i in 0 until node.childCount) {
            val domain = findWebDomainInNode(node.getChildAt(i))
            if (domain != null) return domain
        }
        
        return null
    }
    
    /**
     * 从节点文本提取域名（Chrome地址栏）
     */
    private fun extractFromNodeText(structure: AssistStructure): String? {
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val domain = findDomainInNodeText(windowNode.rootViewNode)
            if (domain != null) {
                return cleanDomain(domain)
            }
        }
        return null
    }
    
    /**
     * 递归查找节点文本中的域名
     */
    private fun findDomainInNodeText(node: AssistStructure.ViewNode): String? {
        // 检查节点文本
        node.text?.toString()?.let { text ->
            if (looksLikeUrl(text)) {
                val domain = extractDomainFromUrl(text)
                if (domain != null) return domain
            }
        }
        
        // 递归子节点
        for (i in 0 until node.childCount) {
            val domain = findDomainInNodeText(node.getChildAt(i))
            if (domain != null) return domain
        }
        
        return null
    }
    
    /**
     * 从 contentDescription 提取域名
     */
    private fun extractFromContentDescription(structure: AssistStructure): String? {
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val domain = findDomainInContentDescription(windowNode.rootViewNode)
            if (domain != null) {
                return cleanDomain(domain)
            }
        }
        return null
    }
    
    /**
     * 递归查找 contentDescription 中的域名
     */
    private fun findDomainInContentDescription(node: AssistStructure.ViewNode): String? {
        node.contentDescription?.toString()?.let { desc ->
            if (looksLikeUrl(desc)) {
                val domain = extractDomainFromUrl(desc)
                if (domain != null) return domain
            }
        }
        
        for (i in 0 until node.childCount) {
            val domain = findDomainInContentDescription(node.getChildAt(i))
            if (domain != null) return domain
        }
        
        return null
    }
    
    /**
     * 从包名推断域名
     */
    private fun inferDomainFromPackage(packageName: String): String? {
        // 某些应用的包名可以推断出域名
        // 例如: com.example.app -> example.com
        return try {
            val parts = packageName.split(".")
            if (parts.size >= 2) {
                val domain = "${parts[1]}.${parts[0]}"
                if (isValidDomain(domain)) {
                    domain
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 检查文本是否看起来像 URL
     */
    private fun looksLikeUrl(text: String): Boolean {
        return text.contains("://") || 
               text.matches(Regex(".*\\.(com|org|net|edu|gov|cn|io|app|co|uk|de|fr|jp|kr).*"))
    }
    
    /**
     * 从 URL 字符串提取域名
     */
    private fun extractDomainFromUrl(url: String): String? {
        return try {
            // 处理完整 URL
            if (url.contains("://")) {
                val urlPattern = Regex("https?://([^/:?#\\s]+)")
                val match = urlPattern.find(url)
                match?.groupValues?.get(1)
            } else {
                // 处理纯域名
                val domainPattern = Regex("([a-zA-Z0-9-]+\\.[a-zA-Z]{2,})")
                val match = domainPattern.find(url)
                match?.groupValues?.get(1)
            }
        } catch (e: Exception) {
            AutofillLogger.e(TAG, "Error extracting domain from URL", e)
            null
        }
    }
    
    /**
     * 清理域名
     */
    private fun cleanDomain(domain: String): String {
        return domain.lowercase()
            .removePrefix("www.")
            .removePrefix("m.")
            .trim()
    }
    
    /**
     * 检查是否是有效域名
     */
    private fun isValidDomain(domain: String): Boolean {
        return domain.matches(Regex("[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}"))
    }
    
    /**
     * 检查是否是浏览器包名
     */
    private fun isBrowserPackage(packageName: String): Boolean {
        return BROWSER_PACKAGES.any { packageName.contains(it) }
    }
    
    /**
     * 多层匹配算法
     */
    private fun performMultiLayerMatching(
        packageName: String,
        domain: String?,
        passwords: List<PasswordEntry>
    ): List<PasswordMatch> {
        val matches = mutableListOf<PasswordMatch>()
        
        passwords.forEach { password ->
            // 1. 精确包名匹配
            if (password.appPackageName.isNotBlank() && 
                password.appPackageName.equals(packageName, ignoreCase = true)) {
                matches.add(PasswordMatch(
                    entry = password,
                    matchType = MatchType.EXACT_PACKAGE,
                    score = 1.0f,
                    reason = "Exact package name match"
                ))
                return@forEach
            }
            
            // 2. 域名匹配（如果有域名）
            if (domain != null && password.website.isNotBlank()) {
                val domainMatch = matchDomain(password.website, domain)
                if (domainMatch != null) {
                    matches.add(domainMatch)
                    return@forEach
                }
            }
            
            // 3. 模糊标题匹配
            val appName = extractAppName(packageName)
            if (appName.isNotBlank() && 
                password.title.contains(appName, ignoreCase = true)) {
                matches.add(PasswordMatch(
                    entry = password,
                    matchType = MatchType.FUZZY_TITLE,
                    score = 0.5f,
                    reason = "Fuzzy title match with app name"
                ))
            }
        }
        
        return matches
    }
    
    /**
     * 域名匹配
     */
    private fun matchDomain(passwordWebsite: String, targetDomain: String): PasswordMatch? {
        val cleanPasswordDomain = cleanDomain(passwordWebsite)
        val cleanTargetDomain = cleanDomain(targetDomain)
        
        // 精确匹配
        if (cleanPasswordDomain == cleanTargetDomain) {
            return PasswordMatch(
                entry = PasswordEntry(
                    title = "",
                    website = passwordWebsite,
                    username = "",
                    password = ""
                ),
                matchType = MatchType.EXACT_DOMAIN,
                score = 0.95f,
                reason = "Exact domain match"
            )
        }
        
        // 使用 DomainMatcher 进行匹配
        if (DomainMatcher.matches(passwordWebsite, targetDomain, matchStrategy)) {
            val matchType = when (matchStrategy) {
                DomainMatchStrategy.EXACT_MATCH -> MatchType.EXACT_DOMAIN
                DomainMatchStrategy.BASE_DOMAIN -> MatchType.BASE_DOMAIN
                DomainMatchStrategy.DOMAIN -> MatchType.SUBDOMAIN
                DomainMatchStrategy.STARTS_WITH -> MatchType.SUBDOMAIN
                DomainMatchStrategy.REGEX -> MatchType.SUBDOMAIN
                DomainMatchStrategy.NEVER -> return null
            }
            
            val score = when (matchStrategy) {
                DomainMatchStrategy.EXACT_MATCH -> 0.95f
                DomainMatchStrategy.BASE_DOMAIN -> 0.75f
                DomainMatchStrategy.DOMAIN -> 0.85f
                DomainMatchStrategy.STARTS_WITH -> 0.80f
                DomainMatchStrategy.REGEX -> 0.70f
                DomainMatchStrategy.NEVER -> return null
            }
            
            return PasswordMatch(
                entry = PasswordEntry(
                    title = "",
                    website = passwordWebsite,
                    username = "",
                    password = ""
                ),
                matchType = matchType,
                score = score,
                reason = "Domain match using ${matchStrategy.name} strategy"
            )
        }
        
        return null
    }
    
    /**
     * 从包名提取应用名
     */
    private fun extractAppName(packageName: String): String {
        return try {
            val parts = packageName.split(".")
            parts.lastOrNull()?.capitalize() ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * 排序和过滤匹配结果
     */
    private fun sortAndFilterMatches(matches: List<PasswordMatch>): List<PasswordMatch> {
        return matches
            .sorted() // 使用 Comparable 排序
            .distinctBy { it.entry.id } // 去重
            .take(10) // 限制数量
    }
    
    /**
     * 计算置信度
     */
    private fun calculateConfidence(matches: List<PasswordMatch>): Float {
        if (matches.isEmpty()) return 0f
        
        // 基于最佳匹配的分数和匹配类型
        val bestMatch = matches.firstOrNull() ?: return 0f
        
        return when (bestMatch.matchType) {
            MatchType.EXACT_PACKAGE -> 1.0f
            MatchType.EXACT_DOMAIN -> 0.95f
            MatchType.SUBDOMAIN -> 0.85f
            MatchType.BASE_DOMAIN -> 0.75f
            MatchType.FUZZY_TITLE -> 0.5f
            MatchType.MANUAL -> 0.9f
        }
    }
}

