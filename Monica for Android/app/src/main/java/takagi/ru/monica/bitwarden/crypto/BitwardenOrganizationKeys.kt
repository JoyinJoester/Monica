package takagi.ru.monica.bitwarden.crypto

import java.security.KeyFactory
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import takagi.ru.monica.bitwarden.api.ProfileResponse
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey

/** Short-lived keys for one authenticated account response. Never persists decrypted key material. */
internal class BitwardenOrganizationKeys private constructor(
    val keys: Map<String, SymmetricCryptoKey>
) : AutoCloseable {
    override fun close() = keys.values.forEach(SymmetricCryptoKey::clear)

    companion object {
        fun fromProfile(profile: ProfileResponse, userKey: SymmetricCryptoKey): BitwardenOrganizationKeys {
            val organizations = profile.organizations.filter { !it.key.isNullOrBlank() }
            if (organizations.isEmpty()) return BitwardenOrganizationKeys(emptyMap())
            val encryptedPrivateKey = profile.privateKey ?: error("Organization private key unavailable")
            require(BitwardenCrypto.parseCipherString(encryptedPrivateKey).type == 2) {
                "Organization private key must be authenticated"
            }
            val privateBytes = BitwardenCrypto.decrypt(encryptedPrivateKey, userKey)
            val keys = mutableMapOf<String, SymmetricCryptoKey>()
            try {
                val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(privateBytes))
                try {
                    for (organization in organizations) {
                        require(organization.id.isNotBlank() && !keys.containsKey(organization.id)) {
                            "Ambiguous organization key"
                        }
                        // One unsupported organization must not hide the other readable vaults.
                        val key = runCatching { unwrap(requireNotNull(organization.key), privateKey) }.getOrNull()
                        if (key != null) keys[organization.id] = key
                    }
                } finally {
                    runCatching { privateKey.destroy() }
                }
                return BitwardenOrganizationKeys(keys)
            } catch (error: Exception) {
                keys.values.forEach(SymmetricCryptoKey::clear)
                throw error
            } finally {
                privateBytes.fill(0)
            }
        }

        private fun unwrap(envelope: String, privateKey: java.security.PrivateKey): SymmetricCryptoKey {
            require(envelope.length in 3..16384 && '|' !in envelope) { "Invalid organization key envelope" }
            val algorithm = when (envelope.substringBefore('.')) {
                "3" -> "SHA-256"
                "4" -> "SHA-1"
                else -> error("Unsupported organization key encryption")
            }
            val encrypted = java.util.Base64.getDecoder().decode(envelope.substringAfter('.'))
            val decrypted = try {
                Cipher.getInstance("RSA/ECB/OAEPPadding").run {
                    init(Cipher.DECRYPT_MODE, privateKey,
                        OAEPParameterSpec(algorithm, "MGF1", MGF1ParameterSpec(algorithm), PSource.PSpecified.DEFAULT))
                    doFinal(encrypted)
                }
            } finally { encrypted.fill(0) }
            return try {
                require(decrypted.size == 64) { "Invalid organization key size" }
                SymmetricCryptoKey(decrypted.copyOfRange(0, 32), decrypted.copyOfRange(32, 64))
            } finally { decrypted.fill(0) }
        }
    }
}
