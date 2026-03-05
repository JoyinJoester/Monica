package takagi.ru.monica.autofill_ng

import android.util.Base64
import android.util.Log
import takagi.ru.monica.security.SecurityManager

/**
 * Autofill-only secret resolver.
 *
 * Never returns encrypted payload as fillable password.
 */
object AutofillSecretResolver {
    private const val TAG = "AutofillSecret"
    private const val DATA_PREFIX_V2 = "V2|"
    private const val DATA_PREFIX_MDK = "MDK|"

    fun decryptPasswordOrNull(
        securityManager: SecurityManager,
        encryptedOrPlain: String,
        logTag: String = TAG,
    ): String? {
        if (encryptedOrPlain.isBlank()) return ""

        val decrypted = runCatching {
            securityManager.decryptData(encryptedOrPlain)
        }.onFailure { e ->
            Log.w(logTag, "Password decrypt failed for autofill entry", e)
        }.getOrNull() ?: return if (looksEncryptedPayload(encryptedOrPlain)) {
            null
        } else {
            encryptedOrPlain
        }

        if (decrypted == encryptedOrPlain && looksEncryptedPayload(encryptedOrPlain)) {
            Log.w(logTag, "Password decrypt unresolved, skipping encrypted payload")
            return null
        }

        return decrypted
    }

    private fun looksEncryptedPayload(value: String): Boolean {
        if (value.startsWith(DATA_PREFIX_V2) || value.startsWith(DATA_PREFIX_MDK)) {
            return true
        }

        // Legacy V1 payload format: Base64(12-byte IV + encrypted bytes + 16-byte GCM tag)
        val decoded = runCatching {
            Base64.decode(value, Base64.DEFAULT)
        }.getOrNull() ?: return false

        return decoded.size > 28
    }
}


