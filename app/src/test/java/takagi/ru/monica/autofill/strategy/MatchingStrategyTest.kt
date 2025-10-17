package takagi.ru.monica.autofill.strategy

import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.autofill.data.AutofillContext
import takagi.ru.monica.autofill.data.PasswordMatch

/**
 * MatchingStrategy 单元测试
 * 
 * 测试各种匹配策略:
 * - 域名匹配
 * - 包名匹配
 * - 模糊匹配
 * - 组合策略
 */
class MatchingStrategyTest {
    
    // 创建测试用的密码条目
    private fun createPasswordEntry(
        id: Long,
        title: String,
        username: String,
        password: String,
        website: String? = null,
        applicationId: String? = null
    ): PasswordEntry {
        return PasswordEntry(
            id = id,
            title = title,
            username = username,
            password = password,
            website = website,
            applicationId = applicationId,
            folderId = null,
            note = "",
            isFavorite = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
    
    // ===== DomainMatchingStrategy 测试 =====
    
    @Test
    fun `test domain exact match`() = runBlocking {
        val strategy = DomainMatchingStrategy()
        val context = AutofillContext(
            packageName = "com.example",
            domain = "example.com"
        )
        
        val candidates = listOf(
            createPasswordEntry(1, "Example", "user", "pass", website = "example.com"),
            createPasswordEntry(2, "Google", "user", "pass", website = "google.com")
        )
        
        val matches = strategy.match(context, candidates)
        
        assertEquals(1, matches.size)
        assertEquals(1L, matches[0].entry.id)
        assertEquals(100, matches[0].score) // 精确匹配应该得100分
        assertEquals(PasswordMatch.MatchType.EXACT_DOMAIN, matches[0].matchType)
    }
    
    @Test
    fun `test domain subdomain match`() = runBlocking {
        val strategy = DomainMatchingStrategy()
        val context = AutofillContext(
            packageName = "com.example",
            domain = "accounts.google.com"
        )
        
        val candidates = listOf(
            createPasswordEntry(1, "Google", "user", "pass", website = "google.com")
        )
        
        val matches = strategy.match(context, candidates)
        
        assertEquals(1, matches.size)
        assertTrue(matches[0].score >= 70) // 子域名匹配应该有较高分数
        assertEquals(PasswordMatch.MatchType.SUBDOMAIN, matches[0].matchType)
    }
    
    @Test
    fun `test domain no match`() = runBlocking {
        val strategy = DomainMatchingStrategy()
        val context = AutofillContext(
            packageName = "com.example",
            domain = "example.com"
        )
        
        val candidates = listOf(
            createPasswordEntry(1, "Google", "user", "pass", website = "google.com"),
            createPasswordEntry(2, "GitHub", "user", "pass", website = "github.com")
        )
        
        val matches = strategy.match(context, candidates)
        
        assertEquals(0, matches.size) // 没有匹配
    }
    
    @Test
    fun `test domain strategy supports`() {
        val strategy = DomainMatchingStrategy()
        
        val context1 = AutofillContext(packageName = "com.example", domain = "example.com")
        assertTrue(strategy.supports(context1))
        
        val context2 = AutofillContext(packageName = "com.example", domain = null)
        assertFalse(strategy.supports(context2))
        
        val context3 = AutofillContext(packageName = "com.example", domain = "")
        assertFalse(strategy.supports(context3))
    }
    
    // ===== PackageNameMatchingStrategy 测试 =====
    
    @Test
    fun `test package exact match`() = runBlocking {
        val strategy = PackageNameMatchingStrategy()
        val context = AutofillContext(
            packageName = "com.example.app",
            isWebView = false
        )
        
        val candidates = listOf(
            createPasswordEntry(1, "App", "user", "pass", applicationId = "com.example.app"),
            createPasswordEntry(2, "Other", "user", "pass", applicationId = "com.other.app")
        )
        
        val matches = strategy.match(context, candidates)
        
        assertEquals(1, matches.size)
        assertEquals(1L, matches[0].entry.id)
        assertEquals(100, matches[0].score)
        assertEquals(PasswordMatch.MatchType.EXACT_PACKAGE, matches[0].matchType)
    }
    
    @Test
    fun `test package similar match`() = runBlocking {
        val strategy = PackageNameMatchingStrategy()
        val context = AutofillContext(
            packageName = "com.example.app",
            isWebView = false
        )
        
        val candidates = listOf(
            createPasswordEntry(1, "App", "user", "pass", applicationId = "com.example.app.debug")
        )
        
        val matches = strategy.match(context, candidates)
        
        assertEquals(1, matches.size)
        assertTrue(matches[0].score >= 70) // 相似包名应该有较高分数
    }
    
    @Test
    fun `test package webview not supported`() {
        val strategy = PackageNameMatchingStrategy()
        val context = AutofillContext(
            packageName = "com.example.app",
            isWebView = true
        )
        
        assertFalse(strategy.supports(context)) // WebView场景不支持包名匹配
    }
    
    // ===== FuzzyMatchingStrategy 测试 =====
    
    @Test
    fun `test fuzzy title match`() = runBlocking {
        val strategy = FuzzyMatchingStrategy()
        val context = AutofillContext(
            packageName = "com.example",
            domain = "google"
        )
        
        val candidates = listOf(
            createPasswordEntry(1, "Google Account", "user", "pass", website = "google.com"),
            createPasswordEntry(2, "Facebook", "user", "pass", website = "facebook.com")
        )
        
        val matches = strategy.match(context, candidates)
        
        assertTrue(matches.isNotEmpty())
        assertEquals(1L, matches[0].entry.id) // Google 应该匹配
    }
    
    @Test
    fun `test fuzzy username match`() = runBlocking {
        val strategy = FuzzyMatchingStrategy()
        val context = AutofillContext(
            packageName = "com.example",
            domain = "testuser"
        )
        
        val candidates = listOf(
            createPasswordEntry(1, "Account", "testuser@example.com", "pass")
        )
        
        val matches = strategy.match(context, candidates)
        
        assertTrue(matches.isNotEmpty())
    }
    
    @Test
    fun `test fuzzy strategy always supports`() {
        val strategy = FuzzyMatchingStrategy()
        
        val context1 = AutofillContext(packageName = "com.example")
        assertTrue(strategy.supports(context1))
        
        val context2 = AutofillContext(packageName = "com.example", domain = "example.com")
        assertTrue(strategy.supports(context2))
    }
    
    @Test
    fun `test fuzzy threshold`() = runBlocking {
        val strategy = FuzzyMatchingStrategy()
        val context = AutofillContext(
            packageName = "com.example",
            domain = "xyz" // 很短的查询
        )
        
        val candidates = listOf(
            createPasswordEntry(1, "Completely Different Title", "user", "pass", website = "longdomain.com")
        )
        
        val matches = strategy.match(context, candidates)
        
        // 相似度太低应该被过滤掉 (阈值60分)
        assertEquals(0, matches.size)
    }
    
    // ===== CompositeMatchingStrategy 测试 =====
    
    @Test
    fun `test composite strategy combines results`() = runBlocking {
        val strategies = listOf(
            DomainMatchingStrategy(),
            PackageNameMatchingStrategy(),
            FuzzyMatchingStrategy()
        )
        val composite = CompositeMatchingStrategy(strategies)
        
        val context = AutofillContext(
            packageName = "com.example",
            domain = "example.com"
        )
        
        val candidates = listOf(
            createPasswordEntry(1, "Example", "user", "pass", 
                website = "example.com", 
                applicationId = "com.example"
            ),
            createPasswordEntry(2, "Other", "user", "pass", 
                website = "other.com", 
                applicationId = "com.other"
            )
        )
        
        val matches = composite.match(context, candidates)
        
        // 应该匹配到 Example
        assertTrue(matches.isNotEmpty())
        assertEquals(1L, matches[0].entry.id)
        assertTrue(matches[0].score >= 90) // 高分匹配
    }
    
    @Test
    fun `test composite deduplication`() = runBlocking {
        val strategies = listOf(
            DomainMatchingStrategy(),
            FuzzyMatchingStrategy()
        )
        val composite = CompositeMatchingStrategy(strategies)
        
        val context = AutofillContext(
            packageName = "com.example",
            domain = "example.com"
        )
        
        val candidates = listOf(
            createPasswordEntry(1, "Example", "user", "pass", website = "example.com")
        )
        
        val matches = composite.match(context, candidates)
        
        // 虽然两个策略都能匹配,但应该去重,只返回一个
        assertEquals(1, matches.size)
    }
    
    @Test
    fun `test composite sorting by score`() = runBlocking {
        val strategies = listOf(
            DomainMatchingStrategy(),
            FuzzyMatchingStrategy()
        )
        val composite = CompositeMatchingStrategy(strategies)
        
        val context = AutofillContext(
            packageName = "com.example",
            domain = "example.com"
        )
        
        val candidates = listOf(
            createPasswordEntry(1, "Fuzzy Match", "user", "pass", website = "other.com"),
            createPasswordEntry(2, "Exact Match", "user", "pass", website = "example.com")
        )
        
        val matches = composite.match(context, candidates)
        
        // 应该按分数排序,精确匹配在前
        assertTrue(matches.isNotEmpty())
        assertEquals(2L, matches[0].entry.id) // 精确匹配应该排第一
    }
    
    // ===== 计算分数测试 =====
    
    @Test
    fun `test domain score calculation`() {
        val strategy = DomainMatchingStrategy()
        val context = AutofillContext(packageName = "com.example", domain = "example.com")
        
        // 精确匹配
        val entry1 = createPasswordEntry(1, "Test", "user", "pass", website = "example.com")
        assertEquals(100, strategy.calculateScore(entry1, context))
        
        // 子域名匹配
        val entry2 = createPasswordEntry(2, "Test", "user", "pass", website = "www.example.com")
        assertTrue(strategy.calculateScore(entry2, context) >= 70)
        
        // 不匹配
        val entry3 = createPasswordEntry(3, "Test", "user", "pass", website = "other.com")
        assertEquals(0, strategy.calculateScore(entry3, context))
    }
    
    @Test
    fun `test package score calculation`() {
        val strategy = PackageNameMatchingStrategy()
        val context = AutofillContext(packageName = "com.example.app", isWebView = false)
        
        // 精确匹配
        val entry1 = createPasswordEntry(1, "Test", "user", "pass", applicationId = "com.example.app")
        assertEquals(100, strategy.calculateScore(entry1, context))
        
        // 相似包名
        val entry2 = createPasswordEntry(2, "Test", "user", "pass", applicationId = "com.example.app.debug")
        assertTrue(strategy.calculateScore(entry2, context) >= 70)
        
        // 不匹配
        val entry3 = createPasswordEntry(3, "Test", "user", "pass", applicationId = "com.other.app")
        assertEquals(0, strategy.calculateScore(entry3, context))
    }
}
