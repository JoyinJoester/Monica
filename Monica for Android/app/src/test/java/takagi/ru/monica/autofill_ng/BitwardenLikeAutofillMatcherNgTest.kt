package takagi.ru.monica.autofill_ng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.PasswordEntry
import java.util.Date

class BitwardenLikeAutofillMatcherNgTest {
    private val matcher = BitwardenLikeAutofillMatcherNg()

    @Test fun `local html app matches bindings without title or package token fallback`() {
        val entries = listOf(
            entry(id = 1, title = "Bound app", appPackage = "com.cn21.ecloud"),
            entry(id = 2, title = "Package URL", website = "com.cn21.ecloud"),
            entry(id = 3, title = "天翼云盘"),
            entry(id = 4, title = "ecloud backup", appPackage = "com.other.app"),
        )
        for (strict in listOf(true, false)) {
            val matches = matcher.match(entries, "com.cn21.ecloud", null, "天翼云盘",
                BitwardenLikeAutofillMatcherNg.Config(strictOnly = strict,
                    allowPackageMatch = false, allowExactPackageMatch = true))
            assertEquals(setOf(1L, 2L), matches.map { it.id }.toSet())
        }
    }

    @Test
    fun `strict mode should return exact package match`() {
        val entries = listOf(
            entry(id = 1, title = "Target", appPackage = "com.example.app"),
            entry(id = 2, title = "Other", appPackage = "com.other.app"),
        )

        val result = matcher.match(
            entries = entries,
            packageName = "com.example.app",
            webDomain = null,
            config = BitwardenLikeAutofillMatcherNg.Config(strictOnly = true),
        )

        assertEquals(1, result.size)
        assertEquals(1L, result.first().id)
    }

    @Test
    fun `domainless browser request does not use shared browser package bindings`() {
        val entries = listOf(
            entry(id = 1, title = "Feijipan", website = "https://www.feijipan.com", appPackage = "com.android.chrome"),
            entry(id = 2, title = "123 Pan", website = "https://www.123pan.com", appPackage = "com.android.chrome"),
        )

        val result = matcher.match(
            entries = entries,
            packageName = "com.android.chrome",
            webDomain = null,
            config = BitwardenLikeAutofillMatcherNg.Config(
                strictOnly = true,
                allowPackageMatch = false,
            ),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `strict mode should normalize androidapp package prefix`() {
        val entries = listOf(
            entry(id = 1, title = "Target", appPackage = "androidapp://com.example.app"),
            entry(id = 2, title = "Other", appPackage = "com.other.app"),
        )

        val result = matcher.match(
            entries = entries,
            packageName = "com.example.app",
            webDomain = null,
            config = BitwardenLikeAutofillMatcherNg.Config(strictOnly = true),
        )

        assertEquals(1, result.size)
        assertEquals(1L, result.first().id)
    }

    @Test
    fun `strict mode should match androidapp package stored in website`() {
        val entries = listOf(
            entry(id = 1, title = "Target", website = "androidapp://com.example.app"),
            entry(id = 2, title = "Other", website = "https://example.com"),
        )

        val result = matcher.match(
            entries = entries,
            packageName = "com.example.app",
            webDomain = null,
            config = BitwardenLikeAutofillMatcherNg.Config(strictOnly = true),
        )

        assertEquals(1, result.size)
        assertEquals(1L, result.first().id)
    }

    @Test
    fun `strict mode should match bare package name stored in website`() {
        val entries = listOf(
            entry(id = 1, title = "Tianyi Cloud", website = "com.cn21.ecloud"),
            entry(id = 2, title = "Other", website = "https://example.com"),
        )

        val result = matcher.match(
            entries = entries,
            packageName = "com.cn21.ecloud",
            webDomain = null,
            config = BitwardenLikeAutofillMatcherNg.Config(strictOnly = true),
        )

        assertEquals(1, result.size)
        assertEquals(1L, result.first().id)
    }

    @Test
    fun `strict mode should match base domain`() {
        val entries = listOf(
            entry(id = 1, title = "Example", website = "https://example.com"),
            entry(id = 2, title = "Other", website = "https://other.com"),
        )

        val result = matcher.match(
            entries = entries,
            packageName = "com.android.chrome",
            webDomain = "accounts.example.com",
            config = BitwardenLikeAutofillMatcherNg.Config(
                strictOnly = true,
                allowSubdomainMatch = true,
            ),
        )

        assertEquals(1, result.size)
        assertEquals(1L, result.first().id)
    }

    @Test
    fun `non strict mode should allow heuristic fallback`() {
        val entries = listOf(
            entry(id = 1, title = "GitHub Account"),
            entry(id = 2, title = "Random"),
        )

        val result = matcher.match(
            entries = entries,
            packageName = "com.android.chrome",
            webDomain = "github.com",
            config = BitwardenLikeAutofillMatcherNg.Config(
                strictOnly = false,
                allowSubdomainMatch = false,
            ),
        )

        assertEquals(1, result.size)
        assertEquals(1L, result.first().id)
    }

    @Test
    fun `domain match does not depend on browser package ordering`() {
        val entries = listOf(
            entry(
                id = 1,
                title = "Combo",
                website = "https://example.com",
                appPackage = "com.example.app",
            ),
            entry(id = 2, title = "DomainOnly", website = "https://example.com"),
        )

        val result = matcher.match(
            entries = entries,
            packageName = "com.example.app",
            webDomain = "example.com",
            config = BitwardenLikeAutofillMatcherNg.Config(strictOnly = true),
        )

        assertEquals(2, result.size)
        assertEquals(setOf(1L, 2L), result.map { it.id }.toSet())
    }

    @Test
    fun `strict mode should match one of multiple websites`() {
        val entries = listOf(
            entry(id = 1, title = "Multi", website = "https://old.example.com, https://accounts.qq.com"),
            entry(id = 2, title = "Other", website = "https://other.com"),
        )

        val result = matcher.match(
            entries = entries,
            packageName = "com.tencent.mobileqq",
            webDomain = "accounts.qq.com",
            config = BitwardenLikeAutofillMatcherNg.Config(strictOnly = true),
        )

        assertEquals(1, result.size)
        assertEquals(1L, result.first().id)
    }

    @Test
    fun `strict mode should match androidapp package from mixed website bindings`() {
        val entries = listOf(
            entry(id = 1, title = "QQ", website = "https://accounts.qq.com, androidapp://com.tencent.mobileqq"),
            entry(id = 2, title = "Other", website = "https://accounts.qq.com"),
        )

        val result = matcher.match(
            entries = entries,
            packageName = "com.tencent.mobileqq",
            webDomain = null,
            config = BitwardenLikeAutofillMatcherNg.Config(strictOnly = true),
        )

        assertEquals(1, result.size)
        assertEquals(1L, result.first().id)
    }

    @Test
    fun `strict mode rejects title-only package token matches`() {
        val entries = listOf(
            entry(id = 1, title = "GitHub Account"),
        )

        val strictResult = matcher.match(
            entries = entries,
            packageName = "com.github.fake",
            webDomain = null,
            config = BitwardenLikeAutofillMatcherNg.Config(strictOnly = true),
        )
        val nonStrictResult = matcher.match(
            entries = entries,
            packageName = "com.github.fake",
            webDomain = null,
            config = BitwardenLikeAutofillMatcherNg.Config(strictOnly = false),
        )

        assertTrue(strictResult.isEmpty())
        assertEquals(listOf(1L), nonStrictResult.map { it.id })
    }

    @Test
    fun `bare package bindings use exact matching across cached and uncached paths`() {
        val entries = listOf(
            entry(1, "Bare", website = "https://cloud.189.cn/login, COM.CN21.ECLOUD"),
            entry(2, "Direct", appPackage = "com.old.app|com.cn21.ecloud"),
            entry(3, "Different", website = "com.cn21.ecloud.other"),
            entry(4, "Web only", website = "https://com.cn21.ecloud"),
            entry(5, "Path", website = "com.cn21.ecloud/login"),
        )
        for (cache in listOf(false, true)) {
            val subject = BitwardenLikeAutofillMatcherNg(useNativeIndex = false, cacheMetadata = cache)
            try {
                assertEquals(setOf(1L, 2L), subject.match(entries, "com.cn21.ecloud", null).map { it.id }.toSet())
                assertTrue(subject.match(entries, "com.cn21.ecloud", null,
                    config = BitwardenLikeAutofillMatcherNg.Config(allowPackageMatch = false)).isEmpty())
                assertEquals(listOf(1L), subject.match(entries, "com.android.chrome", "cloud.189.cn").map { it.id })
                assertTrue(subject.match(entries, "com.cn21.ecloud", "unrelated.test").isEmpty())
                val removed = entries.map { if (it.id <= 2) it.copy(website = "", appPackageName = "") else it }
                assertTrue(subject.match(removed, "com.cn21.ecloud", null).isEmpty())
            } finally { subject.clear() }
        }
    }

    private fun entry(
        id: Long,
        title: String,
        website: String = "",
        appPackage: String = "",
    ): PasswordEntry {
        val now = Date()
        return PasswordEntry(
            id = id,
            title = title,
            website = website,
            username = "user$id",
            password = "pass$id",
            createdAt = now,
            updatedAt = now,
            appPackageName = appPackage,
        )
    }
}
