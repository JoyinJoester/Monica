package takagi.ru.monica.util

import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.utils.KeePassCredentialSupport
import java.util.Base64

class KeePassCredentialSupportTest {

    @Test
    fun noKeyFile_buildsPasswordOnlyCandidate() {
        val candidates = KeePassCredentialSupport.buildCredentialCandidates(
            password = "demo",
            keyFileBytes = null
        )
        assertTrue(candidates.size == 1)
        assertTrue(candidates.first().label == "password-only")
    }

    @Test
    fun xmlKeyFile_buildsXmlVariantCandidates() {
        val rawKey = ByteArray(32) { (it + 1).toByte() }
        val b64 = Base64.getEncoder().encodeToString(rawKey)
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <KeyFile><Key><Data>$b64</Data></Key></KeyFile>
        """.trimIndent().toByteArray()

        val candidates = KeePassCredentialSupport.buildCredentialCandidates(
            password = "",
            keyFileBytes = xml
        )
        val labels = candidates.map { it.label }
        assertTrue(labels.any { it.startsWith("xml-data/") })
    }

    @Test
    fun hexKeyFile_buildsHexVariantCandidates() {
        val hex = "00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF".toByteArray()
        val candidates = KeePassCredentialSupport.buildCredentialCandidates(
            password = "",
            keyFileBytes = hex
        )
        val labels = candidates.map { it.label }
        assertTrue(labels.any { it.startsWith("hex-text/") })
    }

    @Test
    fun invalidCredentialMessage_containsAttemptSummary() {
        val message = KeePassCredentialSupport.buildInvalidCredentialMessage(
            listOf("raw/password+key", "xml-data/password+key")
        )
        assertTrue(message.contains("已尝试"))
        assertTrue(message.contains("raw/password+key"))
    }
}
