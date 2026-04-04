package takagi.ru.monica.bitwarden.cache

import android.content.Context
import android.content.SharedPreferences
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.security.SecurityManager

/**
 * Persist the last readable Bitwarden secret locally so unreadable payloads can
 * still be displayed offline.
 */
class BitwardenOfflineSecretCache(
    context: Context,
    private val securityManager: SecurityManager
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREF_NAME,
        Context.MODE_PRIVATE
    )

    @Volatile
    private var memoryCache: Map<Long, CachedSecret> = emptyMap()

    fun remember(entry: PasswordEntry, plainSecret: String) {
        if (!entry.hasBitwardenCipherBinding() || plainSecret.isBlank()) return

        val entryId = entry.id
        val cipherId = entry.bitwardenCipherId.orEmpty()
        val existing = memoryCache[entryId]
        if (existing != null && existing.cipherId == cipherId && existing.secret == plainSecret) {
            return
        }

        val encrypted = runCatching { securityManager.encryptDataLegacyCompat(plainSecret) }
            .getOrNull() ?: return

        prefs.edit()
            .putString(secretKey(entryId), encrypted)
            .putString(cipherKey(entryId), cipherId)
            .apply()

        memoryCache = memoryCache.toMutableMap().also {
            it[entryId] = CachedSecret(cipherId = cipherId, secret = plainSecret)
        }
    }

    fun recall(entry: PasswordEntry): String? {
        if (!entry.hasBitwardenCipherBinding()) return null

        val entryId = entry.id
        val cipherId = entry.bitwardenCipherId.orEmpty()

        val inMemory = memoryCache[entryId]
        if (inMemory != null && inMemory.cipherId == cipherId && inMemory.secret.isNotBlank()) {
            return inMemory.secret
        }

        val cachedCipherId = prefs.getString(cipherKey(entryId), null) ?: return null
        if (cachedCipherId != cipherId) {
            clear(entryId)
            return null
        }

        val encrypted = prefs.getString(secretKey(entryId), null) ?: return null
        val decrypted = runCatching { securityManager.decryptData(encrypted) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        memoryCache = memoryCache.toMutableMap().also {
            it[entryId] = CachedSecret(cipherId = cipherId, secret = decrypted)
        }
        return decrypted
    }

    fun clear(entryId: Long) {
        prefs.edit()
            .remove(secretKey(entryId))
            .remove(cipherKey(entryId))
            .apply()

        if (memoryCache.containsKey(entryId)) {
            memoryCache = memoryCache.toMutableMap().also { it.remove(entryId) }
        }
    }

    private fun secretKey(entryId: Long): String = "secret_$entryId"

    private fun cipherKey(entryId: Long): String = "cipher_$entryId"

    private data class CachedSecret(
        val cipherId: String,
        val secret: String
    )

    companion object {
        private const val PREF_NAME = "bitwarden_offline_secret_cache"
    }
}