package takagi.ru.monica.autofill_ng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.PasswordEntry
import java.util.Date

class AutofillInteractionContextResolverTest {
    @Test
    fun `build should prefer web identifier and keep app alias`() {
        val context = AutofillInteractionContextResolver.build(
            packageName = "com.example.app",
            webDomain = "accounts.example.com",
        )

        assertEquals("web:accounts.example.com", context.primaryIdentifier)
        assertEquals(listOf("app:com.example.app"), context.aliasIdentifiers)
    }

    @Test
    fun `password only login should require password target and no account target`() {
        val passwordOnly = listOf(
            EnhancedAutofillStructureParserV2.FieldHint.PASSWORD
        )
        val mixedLogin = listOf(
            EnhancedAutofillStructureParserV2.FieldHint.USERNAME,
            EnhancedAutofillStructureParserV2.FieldHint.PASSWORD,
        )

        assertTrue(AutofillInteractionContextResolver.isPasswordOnlyLoginHints(passwordOnly))
        assertFalse(AutofillInteractionContextResolver.isPasswordOnlyLoginHints(mixedLogin))
    }

    @Test
    fun `prioritize last filled should move existing entry to front and dedupe`() {
        val first = entry(1)
        val second = entry(2)
        val third = entry(3)

        val prioritized = AutofillInteractionContextResolver.prioritizeLastFilled(
            entries = listOf(first, second, third),
            lastFilled = second,
        )

        assertEquals(listOf(2L, 1L, 3L), prioritized.map { it.id })
    }

    private fun entry(id: Long): PasswordEntry {
        val now = Date()
        return PasswordEntry(
            id = id,
            title = "Entry $id",
            website = "",
            username = "user$id",
            password = "pass$id",
            createdAt = now,
            updatedAt = now,
        )
    }
}
