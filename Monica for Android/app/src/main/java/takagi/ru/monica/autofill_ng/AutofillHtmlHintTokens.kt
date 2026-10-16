package takagi.ru.monica.autofill_ng

import java.util.Locale

/** HTML autocomplete grammar, independent of labels and of Android's native hint heuristics. */
internal object AutofillHtmlHintTokens {
    private val whitespace = Regex("[\\t\\n\\u000C\\r ]+")
    private val contacts = setOf("home", "work", "mobile", "fax", "pager")

    data class Detail(val field: String, val section: String?, val mode: String?, val contact: String?) {
        val fillHint: String get() = listOfNotNull(contact, this.field).joinToString(" ")
        fun sameSection(other: Detail?): Boolean = section == other?.section && mode == other?.mode && contact == other?.contact
    }

    fun detail(value: String): Detail? {
        val field = fieldToken(value) ?: return null
        if (coarseHint(field) == null) return null
        val tokens = value.trim().lowercase(Locale.ROOT).split(whitespace).filter { it.isNotEmpty() }.toMutableList()
        val section = tokens.firstOrNull()?.takeIf { it.startsWith("section-") }?.also { tokens.removeAt(0) }
        val mode = tokens.firstOrNull()?.takeIf { it in setOf("shipping", "billing") }?.also { tokens.removeAt(0) }
        val contact = tokens.firstOrNull()?.takeIf { it in contacts }
        return Detail(field, section, mode, contact)
    }

    fun coarseHint(field: String): EnhancedAutofillStructureParserV2.FieldHint? {
        val name = when (field.lowercase(Locale.ROOT)) {
            "username" -> "USERNAME"
            "password" -> "PASSWORD"
            "new-password" -> "NEW_PASSWORD"
            "email" -> "EMAIL_ADDRESS"
            "phone", "tel", "tel-country-code", "tel-national", "tel-area-code", "tel-local", "tel-local-prefix", "tel-local-suffix", "tel-extension" -> "PHONE_NUMBER"
            "personname", "name", "additional-name", "honorific-prefix", "honorific-suffix" -> "PERSON_NAME"
            "given-name" -> "PERSON_FIRST_NAME"
            "family-name" -> "PERSON_LAST_NAME"
            "organization" -> "COMPANY_NAME"
            "street-address", "address-line1", "address-line2", "address-line3", "address-level3", "address-level4" -> "POSTAL_ADDRESS"
            "address-level2" -> "ADDRESS_CITY"
            "address-level1" -> "ADDRESS_REGION"
            "country", "country-name" -> "ADDRESS_COUNTRY"
            "postalcode", "postal-code" -> "POSTAL_CODE"
            "cc-name", "cc-given-name", "cc-additional-name", "cc-family-name" -> "CREDIT_CARD_HOLDER_NAME"
            "cc-number" -> "CREDIT_CARD_NUMBER"
            "cc-csc" -> "CREDIT_CARD_SECURITY_CODE"
            "cc-exp" -> "CREDIT_CARD_EXPIRATION_DATE"
            "cc-exp-month" -> "CREDIT_CARD_EXPIRATION_MONTH"
            "cc-exp-year" -> "CREDIT_CARD_EXPIRATION_YEAR"
            "one-time-code" -> "OTP_CODE"
            else -> return null
        }
        return EnhancedAutofillStructureParserV2.FieldHint.valueOf(name)
    }

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
