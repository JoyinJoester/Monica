package takagi.ru.monica.autofill_ng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.PasswordEntry
import java.util.Date

class BitwardenLikeAutofillMatcherNgTest {
    private val matcher = BitwardenLikeAutofillMatcherNg()

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
    fun `should prioritize package plus domain combo over domain only`() {
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
        assertEquals(1L, result.first().id)
        assertTrue(result.any { it.id == 2L })
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
