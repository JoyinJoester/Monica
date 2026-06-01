package takagi.ru.monica.repository

import java.util.Base64

/**
 * Portable representation of an attachment CEK inside an MDBX vault.
 *
 * Room stores Attachment.wrappedCek with the current device SecurityManager. That value cannot
 * be used by another device. MDBX must therefore store the CEK in a vault-portable form and let
 * each importing device wrap it again for its local Room database.
 */
object MdbxAttachmentCekPayload {
    private const val PREFIX = "portable-attachment-cek-v1:"
    private const val CEK_SIZE_BYTES = 32

    fun fromLocalWrappedCek(
        wrappedCek: String,
        unwrapToBase64: (String) -> String
    ): String = PREFIX + unwrapToBase64(wrappedCek)

    fun toLocalWrappedCek(
        storedValue: String,
        wrapBase64: (String) -> String
    ): String {
        val trimmed = storedValue.trim()
        val portableBase64 = when {
            trimmed.startsWith(PREFIX) -> trimmed.removePrefix(PREFIX)
            looksLikeRawCekBase64(trimmed) -> trimmed
            else -> return trimmed
        }
        return wrapBase64(portableBase64)
    }

    fun isPortable(storedValue: String): Boolean =
        storedValue.trim().startsWith(PREFIX)

    private fun looksLikeRawCekBase64(value: String): Boolean {
        if (value.isBlank()) return false
        return runCatching {
            Base64.getDecoder().decode(value).size == CEK_SIZE_BYTES
        }.getOrDefault(false)
    }
}
