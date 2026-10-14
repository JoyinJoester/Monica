package takagi.ru.monica.autofill_ng

import java.util.Locale

/** HTML autocomplete grammar, independent of labels and of Android's native hint heuristics. */
internal object AutofillHtmlHintTokens {
    private val whitespace = Regex("[\\t\\n\\u000C\\r ]+")
    private val contacts = setOf("home", "work", "mobile", "fax", "pager")

    fun fieldToken(value: String): String? {
        if (value.length > 1024) return null
        val normalized = value.trim().lowercase(Locale.ROOT)
        // Keep the existing nonstandard spelling supported by Monica.
        if (normalized == "new password") return "new-password"
        val tokens = normalized.split(whitespace).filter { it.isNotEmpty() }.toMutableList()
        if (tokens.firstOrNull()?.startsWith("section-") == true) tokens.removeAt(0)
        if (tokens.firstOrNull() in setOf("shipping", "billing")) tokens.removeAt(0)
        if (tokens.firstOrNull() in contacts) tokens.removeAt(0)
        if (tokens.lastOrNull() == "webauthn") tokens.removeAt(tokens.lastIndex)
        // Multiple field roles or arbitrary extra words are ambiguous, not extra score.
        val token = tokens.singleOrNull() ?: return null
        return when (token) {
            "on" -> null
            "current-password", "wifipassword" -> "password"
            "newpassword", "new_password" -> "new-password"
            "newusername", "new-username" -> "username"
            "2faappotpcode", "smsotpcode", "emailotpcode" -> "one-time-code"
            "tel" -> "phone"
            "postal-code" -> "postalCode"
            "name" -> "personName"
            else -> token
        }
    }
}
