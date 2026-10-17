package takagi.ru.monica.bitwarden

import java.security.KeyPairGenerator
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import takagi.ru.monica.bitwarden.api.*
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey
import takagi.ru.monica.bitwarden.crypto.BitwardenOrganizationKeys
import takagi.ru.monica.bitwarden.service.BitwardenCipherKeyResolver
import takagi.ru.monica.bitwarden.service.BitwardenCipherOwnership

class BitwardenOrganizationKeysTest {
    private val userKey = key(1)
    private val orgKey = key(71)
    private val itemKey = key(151)
    private val rsa = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    @Test fun parsesBothOfficialAndVaultwardenProfileCasing() {
        for (raw in listOf(
            """{"Organizations":[{"Id":"team","Key":"wrapped"}],"PrivateKey":"private"}""",
            """{"organizations":[{"id":"team","key":"wrapped"}],"privateKey":"private"}"""
        )) {
            val profile = Json.decodeFromString<ProfileResponse>(raw)
            assertEquals("team", profile.organizations.single().id)
            assertEquals("wrapped", profile.organizations.single().key)
            assertEquals("private", profile.privateKey)
        }
        assertTrue(Json.decodeFromString<ProfileResponse>("{}").organizations.isEmpty())
    }

    @Test fun decryptsSha256AndLegacySha1OrganizationEnvelopes() {
        for (type in listOf(3, 4)) {
            val profile = profile(type)
            BitwardenOrganizationKeys.fromProfile(profile, userKey).use { ring ->
                assertEquals(orgKey, ring.keys["team"])
                val cipher = CipherApiResponse(id = "shared", organizationId = "team")
                val resolved = BitwardenCipherKeyResolver.resolveCipherKey(cipher, userKey, "Test", ring.keys)
                assertEquals("team secret", BitwardenCrypto.decryptToString(wrap("team secret".toByteArray(), orgKey), resolved))
                resolved.clear()
                assertEquals(orgKey, ring.keys["team"])
            }
            assertEquals(key(1), userKey)
        }
    }

    @Test fun resolvesOrganizationThenItemKeyAndWipesOwnedKeys() {
        val ring = BitwardenOrganizationKeys.fromProfile(profile(), userKey)
        val cipher = CipherApiResponse(organizationId = "team", key = wrap(itemKey.encKey + itemKey.macKey, orgKey))
        val resolved = BitwardenCipherKeyResolver.resolveCipherKey(cipher, userKey, "Test", ring.keys)
        assertEquals(itemKey, resolved)
        val organization = ring.keys.getValue("team")
        ring.close()
        assertTrue(organization.encKey.all { it == 0.toByte() })
        assertEquals(itemKey, resolved)
        resolved.clear()
        assertEquals(key(1), userKey)
    }

    @Test fun missingWrongAndTamperedKeysNeverFallBackToPersonalVault() {
        val cipher = CipherApiResponse(organizationId = "missing")
        assertNotNull(runCatching { BitwardenCipherKeyResolver.resolveCipherKey(cipher, userKey, "Test") }.exceptionOrNull())
        assertNotNull(runCatching { BitwardenOrganizationKeys.fromProfile(profile(), key(2)) }.exceptionOrNull())
        BitwardenOrganizationKeys.fromProfile(profile(), userKey).use { ring ->
            val wrongItem = CipherApiResponse(organizationId = "team", key = wrap(itemKey.encKey + itemKey.macKey, userKey))
            assertNotNull(runCatching { BitwardenCipherKeyResolver.resolveCipherKey(wrongItem, userKey, "Test", ring.keys) }.exceptionOrNull())
        }
        val bad = profile().copy(organizations = listOf(OrganizationResponse("team", key = "9.invalid")))
        BitwardenOrganizationKeys.fromProfile(bad, userKey).use { assertTrue(it.keys.isEmpty()) }
    }

    @Test fun malformedOrganizationDoesNotHideAnotherReadableOrganization() {
        val valid = profile()
        val profile = valid.copy(organizations = valid.organizations + OrganizationResponse("bad", key = "3.invalid"))
        BitwardenOrganizationKeys.fromProfile(profile, userKey).use { assertEquals(setOf("team"), it.keys.keys) }
    }

    @Test fun teamUpdatesPreserveOwnershipItemKeyCollectionsAndPasskeys() {
        val passkeys = listOf(CipherLoginFido2CredentialApiData(credentialId = "encrypted-credential"))
        val source = CipherApiResponse(id = "shared", organizationId = "team", edit = true,
            collectionIds = listOf("a", "b"), key = "wrapped-item-key", revisionDate = "revision", reprompt = 1,
            login = CipherLoginApiData(fido2Credentials = passkeys))
        val update = BitwardenCipherOwnership.preserve(
            CipherUpdateRequest(type = 1, name = "encrypted-name", login = CipherLoginApiData(password = "encrypted-password")), source)
        assertEquals("team", update.organizationId)
        assertEquals(source.collectionIds, update.collectionIds)
        assertEquals(source.key, update.key)
        assertEquals(source.revisionDate, update.lastKnownRevisionDate)
        assertEquals(passkeys, update.login?.fido2Credentials)
        assertEquals(1, update.reprompt)
    }

    @Test fun deniedOrUnknownTeamEditPermissionAndDeletedSourcesRejectUpdates() {
        val source = CipherApiResponse(id = "shared", organizationId = "team", edit = true)
        for (invalid in listOf(source.copy(edit = false), source.copy(edit = null),
            source.copy(deletedDate = "deleted"), source.copy(viewPassword = false), source.copy(id = "other"))) {
            assertNotNull(runCatching { BitwardenCipherOwnership.requireWritable(invalid, "shared") }.exceptionOrNull())
        }
        BitwardenCipherOwnership.requireWritable(source, "shared")
        BitwardenCipherOwnership.requireWritable(CipherApiResponse(id = "personal"), "personal")
    }

    @Test fun remoteOrganizationResolutionUsesAuthenticatedProfileAndDoesNotPersistKeys(): Unit = runBlocking {
        MockWebServer().use { server ->
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
                .build().create(BitwardenVaultApi::class.java)
            server.enqueue(MockResponse().setBody(Json.encodeToString(profile())))
            val cipher = CipherApiResponse(organizationId = "team", key = wrap(itemKey.encKey + itemKey.macKey, orgKey))
            val resolved = BitwardenCipherKeyResolver.resolveRemoteCipherKey(api, "synthetic-token", cipher, userKey)
            assertEquals(itemKey, resolved)
            val request = server.takeRequest()
            assertEquals("/accounts/profile", request.path)
            assertEquals("Bearer synthetic-token", request.getHeader("Authorization"))
            resolved.clear()
            val personal = BitwardenCipherKeyResolver.resolveRemoteCipherKey(api, "synthetic-token", CipherApiResponse(), userKey)
            assertEquals(userKey, personal)
            personal.clear()
            assertEquals(1, server.requestCount)
        }
    }

    private fun profile(type: Int = 3): ProfileResponse {
        val algorithm = if (type == 3) "SHA-256" else "SHA-1"
        val wrapped = Cipher.getInstance("RSA/ECB/OAEPPadding").run {
            init(Cipher.ENCRYPT_MODE, rsa.public, OAEPParameterSpec(algorithm, "MGF1", MGF1ParameterSpec(algorithm), PSource.PSpecified.DEFAULT))
            doFinal(orgKey.encKey + orgKey.macKey)
        }
        return ProfileResponse(privateKey = wrap(rsa.private.encoded, userKey),
            organizations = listOf(OrganizationResponse("team", key = "$type.${Base64.getEncoder().encodeToString(wrapped)}")))
    }

    private fun key(offset: Int) = SymmetricCryptoKey(ByteArray(32) { (it + offset).toByte() }, ByteArray(32) { (it + offset + 32).toByte() })
    private fun wrap(bytes: ByteArray, key: SymmetricCryptoKey): String {
        val iv = ByteArray(16) { it.toByte() }
        val data = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.encKey, "AES"), IvParameterSpec(iv)); doFinal(bytes)
        }
        val mac = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key.macKey, "HmacSHA256")); doFinal(iv + data)
        }
        return "2." + listOf(iv, data, mac).joinToString("|") { Base64.getEncoder().encodeToString(it) }
    }
}
