package takagi.ru.monica.bitwarden.service

import takagi.ru.monica.bitwarden.api.BitwardenVaultApi
import takagi.ru.monica.bitwarden.api.CipherApiResponse
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey
import takagi.ru.monica.bitwarden.crypto.BitwardenOrganizationKeys

internal object BitwardenCipherKeyResolver {
    suspend inline fun <T> withCipherKey(
        cipher: CipherApiResponse,
        vaultKey: SymmetricCryptoKey,
        logTag: String,
        organizationKeys: Map<String, SymmetricCryptoKey> = emptyMap(),
        block: suspend (SymmetricCryptoKey) -> T
    ): T {
        val effectiveKey = resolveCipherKey(cipher, vaultKey, logTag, organizationKeys)
        return try { block(effectiveKey) } finally { clearIfDerived(effectiveKey, vaultKey) }
    }

    /** Fetch key envelopes with the same account/API token; no extra request for personal ciphers. */
    suspend fun resolveRemoteCipherKey(
        api: BitwardenVaultApi, accessToken: String, cipher: CipherApiResponse, vaultKey: SymmetricCryptoKey
    ): SymmetricCryptoKey {
        if (cipher.organizationId.isNullOrBlank()) return resolveCipherKey(cipher, vaultKey, "RemoteCipher")
        val response = api.profile("Bearer $accessToken")
        check(response.isSuccessful) { "Organization profile unavailable (${response.code()})" }
        val profile = response.body() ?: error("Organization profile unavailable")
        return BitwardenOrganizationKeys.fromProfile(profile, vaultKey).use {
            resolveCipherKey(cipher, vaultKey, "RemoteCipher", it.keys)
        }
    }

    /** Always returns owned key bytes. Missing/invalid organization or item keys never fall back. */
    @Suppress("UNUSED_PARAMETER")
    fun resolveCipherKey(
        cipher: CipherApiResponse,
        vaultKey: SymmetricCryptoKey,
        logTag: String,
        organizationKeys: Map<String, SymmetricCryptoKey> = emptyMap()
    ): SymmetricCryptoKey {
        val parent = cipher.organizationId?.takeIf(String::isNotBlank)?.let {
            organizationKeys[it] ?: error("Organization key unavailable; sync the account again")
        } ?: vaultKey
        val encryptedKey = cipher.key?.takeIf(String::isNotBlank)
        if (encryptedKey == null) return SymmetricCryptoKey(parent.encKey.copyOf(), parent.macKey.copyOf())
        require(BitwardenCrypto.parseCipherString(encryptedKey).type == 2) { "Item key must be authenticated" }
        return BitwardenCrypto.decryptSymmetricKey(encryptedKey, parent)
    }

    fun clearIfDerived(effectiveKey: SymmetricCryptoKey?, vaultKey: SymmetricCryptoKey?) {
        if (effectiveKey != null && effectiveKey !== vaultKey) effectiveKey.clear()
    }
}
