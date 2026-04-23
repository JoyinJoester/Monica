package takagi.ru.monica.bitwarden.service

import android.util.Log
import takagi.ru.monica.bitwarden.api.CipherApiResponse
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey

internal object BitwardenCipherKeyResolver {

    suspend inline fun <T> withCipherKey(
        cipher: CipherApiResponse,
        vaultKey: SymmetricCryptoKey,
        logTag: String,
        block: suspend (SymmetricCryptoKey) -> T
    ): T {
        val effectiveKey = resolveCipherKey(cipher, vaultKey, logTag)
        return try {
            block(effectiveKey)
        } finally {
            clearIfDerived(effectiveKey, vaultKey)
        }
    }

    fun resolveCipherKey(
        cipher: CipherApiResponse,
        vaultKey: SymmetricCryptoKey,
        logTag: String
    ): SymmetricCryptoKey {
        val encryptedItemKey = cipher.key?.takeIf { it.isNotBlank() } ?: return vaultKey
        return runCatching {
            BitwardenCrypto.decryptSymmetricKey(encryptedItemKey, vaultKey)
        }.getOrElse { error ->
            Log.w(
                logTag,
                "Failed to decrypt cipher key for ${cipher.id} (organization=${cipher.organizationId != null}): ${error.javaClass.simpleName}"
            )
            vaultKey
        }
    }

    fun clearIfDerived(
        effectiveKey: SymmetricCryptoKey?,
        vaultKey: SymmetricCryptoKey?
    ) {
        if (effectiveKey != null && vaultKey != null && effectiveKey !== vaultKey) {
            effectiveKey.clear()
        }
    }
}
