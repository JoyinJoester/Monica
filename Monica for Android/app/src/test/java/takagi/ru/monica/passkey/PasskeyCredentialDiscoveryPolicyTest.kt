package takagi.ru.monica.passkey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.PasskeyEntry

class PasskeyCredentialDiscoveryPolicyTest {

    @Test
    fun `allowCredentials can expose old non discoverable passkey`() {
        val legacy = passkey(
            credentialId = "legacy-id",
            isDiscoverable = false,
        )

        val result = PasskeyCredentialDiscoveryPolicy.filterByAllowedCredentialIds(
            candidates = listOf(legacy),
            allowedCredentialIds = setOf("legacy-id"),
            normalizer = { it.trim() },
        )

        assertEquals(listOf(legacy), result)
    }

    @Test
    fun `discoverable only query does not expose old non discoverable passkey`() {
        val legacy = passkey(
            credentialId = "legacy-id",
            isDiscoverable = false,
        )
        val modern = passkey(
            credentialId = "modern-id",
            isDiscoverable = true,
        )

        val result = PasskeyCredentialDiscoveryPolicy.filterDiscoverable(listOf(legacy, modern))

        assertEquals(listOf(modern), result)
    }

    @Test
    fun `rpId query compatibility keeps usable old non discoverable passkey`() {
        val legacy = passkey(
            credentialId = "legacy-id",
            isDiscoverable = false,
        )

        val result = PasskeyCredentialDiscoveryPolicy.filterUsable(listOf(legacy))

        assertEquals(listOf(legacy), result)
    }

    @Test
    fun `usable policy keeps private local passkeys`() {
        assertTrue(PasskeyCredentialDiscoveryPolicy.isUsable(passkey()))
    }

    @Test
    fun `usable policy rejects references and missing private key`() {
        assertFalse(PasskeyCredentialDiscoveryPolicy.isUsable(passkey(syncStatus = "REFERENCE")))
        assertFalse(PasskeyCredentialDiscoveryPolicy.isUsable(passkey(privateKeyAlias = "")))
    }

    private fun passkey(
        credentialId: String = "credential-id",
        isDiscoverable: Boolean = true,
        privateKeyAlias: String = "private-key",
        syncStatus: String = "NONE",
    ): PasskeyEntry {
        return PasskeyEntry(
            credentialId = credentialId,
            rpId = "example.com",
            rpName = "Example",
            userId = "user-id",
            userName = "user@example.com",
            userDisplayName = "User",
            publicKey = "public-key",
            privateKeyAlias = privateKeyAlias,
            isDiscoverable = isDiscoverable,
            syncStatus = syncStatus,
        )
    }
}
