package takagi.ru.monica.attachments.executor

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Stable Room-side identifier for a KeePass Entry binary reference.
 *
 * Older Monica builds stored only the binary hash. That is still accepted for compatibility,
 * but new records include the file name too so duplicate binary contents with different
 * names do not collide.
 */
data class KeePassAttachmentRef(
    val hashHex: String,
    val fileName: String? = null
) {
    fun encode(): String {
        val hash = hashHex.trim()
        val name = fileName?.takeIf { it.isNotBlank() } ?: return hash
        return "$PREFIX${urlEncode(hash)}$SEPARATOR${urlEncode(name)}"
    }

    companion object {
        private const val PREFIX = "v1:"
        private const val SEPARATOR = ":"

        fun from(hashHex: String, fileName: String?): KeePassAttachmentRef =
            KeePassAttachmentRef(hashHex = hashHex.trim(), fileName = fileName?.trim())

        fun decode(raw: String): KeePassAttachmentRef {
            val trimmed = raw.trim()
            if (!trimmed.startsWith(PREFIX)) {
                return KeePassAttachmentRef(hashHex = trimmed)
            }
            val body = trimmed.removePrefix(PREFIX)
            val separatorIndex = body.indexOf(SEPARATOR)
            if (separatorIndex < 0) {
                return KeePassAttachmentRef(hashHex = urlDecode(body))
            }
            return KeePassAttachmentRef(
                hashHex = urlDecode(body.substring(0, separatorIndex)),
                fileName = urlDecode(body.substring(separatorIndex + 1)).takeIf { it.isNotBlank() }
            )
        }

        private fun urlEncode(value: String): String =
            URLEncoder.encode(value, Charsets.UTF_8.name())

        private fun urlDecode(value: String): String =
            runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrElse { value }
    }
}
