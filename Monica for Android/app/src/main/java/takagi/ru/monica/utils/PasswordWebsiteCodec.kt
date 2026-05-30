package takagi.ru.monica.utils

import java.util.Locale

object PasswordWebsiteCodec {
    fun parse(rawValue: String): List<String> {
        val urls = rawValue
            .split(',', '，')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return urls.ifEmpty { listOf("") }
    }

    fun encode(urls: List<String>): String {
        return urls
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .joinToString(", ")
    }

    fun normalizeForDisplay(input: String): List<String> {
        return parse(input)
            .filter { it.isNotBlank() }
            .map(::normalizeSingle)
            .distinct()
    }

    fun normalizeSingleOrNull(input: String): String? {
        return normalizeForDisplay(input).firstOrNull()
    }

    fun normalizeForKey(value: String): String {
        val raw = value.trim()
        if (raw.isEmpty()) return ""
        return raw
            .lowercase(Locale.ROOT)
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .trimEnd('/')
    }

    fun normalizeSingle(value: String): String {
        return if (value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
        ) {
            value
        } else {
            "https://$value"
        }
    }
}
