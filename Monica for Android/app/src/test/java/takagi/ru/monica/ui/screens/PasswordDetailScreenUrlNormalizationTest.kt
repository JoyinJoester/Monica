package takagi.ru.monica.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordDetailScreenUrlNormalizationTest {

    @Test
    fun normalizeWebsiteUrls_returnsEmpty_whenInputIsBlank() {
        assertEquals(emptyList<String>(), normalizeWebsiteUrls("   ， ,  "))
    }

    @Test
    fun normalizeWebsiteUrls_returnsAllUrls_whenCommaSeparated() {
        assertEquals(
            listOf("https://first.example.com", "https://second.example.com"),
            normalizeWebsiteUrls("first.example.com,second.example.com")
        )
    }

    @Test
    fun normalizeWebsiteUrls_supportsChineseCommaDelimiter() {
        assertEquals(
            listOf("https://first.example.com", "https://second.example.com"),
            normalizeWebsiteUrls("first.example.com，second.example.com")
        )
    }

    @Test
    fun normalizeWebsiteUrls_deduplicates_afterNormalization() {
        assertEquals(
            listOf("https://first.example.com", "https://second.example.com"),
            normalizeWebsiteUrls("first.example.com,https://first.example.com,second.example.com")
        )
    }

    @Test
    fun normalizeWebsiteUrl_returnsNull_whenInputIsBlank() {
        assertNull(normalizeWebsiteUrl("   "))
    }

    @Test
    fun normalizeWebsiteUrl_addsHttps_whenSchemeMissing() {
        assertEquals("https://example.com", normalizeWebsiteUrl("example.com"))
    }

    @Test
    fun normalizeWebsiteUrl_keepsExistingScheme() {
        assertEquals("http://example.com", normalizeWebsiteUrl("http://example.com"))
    }

    @Test
    fun normalizeWebsiteUrl_usesFirstUrl_whenCommaSeparated() {
        assertEquals(
            "https://first.example.com",
            normalizeWebsiteUrl("first.example.com,second.example.com")
        )
    }

    @Test
    fun normalizeWebsiteUrl_skipsEmptySegments_whenCommaSeparated() {
        assertEquals(
            "https://second.example.com",
            normalizeWebsiteUrl(" , , second.example.com,third.example.com")
        )
    }

    @Test
    fun normalizeWebsiteUrl_supportsChineseCommaDelimiter() {
        assertEquals(
            "https://first.example.com",
            normalizeWebsiteUrl("first.example.com，second.example.com")
        )
    }
}
