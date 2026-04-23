package takagi.ru.monica.autofill_ng

import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.FieldHint
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.ParsedItem
import takagi.ru.monica.data.PasswordEntry
import java.util.Locale

data class AutofillInteractionContext(
    val primaryIdentifier: String?,
    val aliasIdentifiers: List<String>,
) {
    val allIdentifiers: List<String>
        get() = buildList {
            primaryIdentifier?.let(::add)
            addAll(aliasIdentifiers)
        }
}

object AutofillInteractionContextResolver {
    fun build(
        packageName: String?,
        webDomain: String?,
    ): AutofillInteractionContext {
        val identifiers = linkedSetOf<String>()
        normalizeWebDomain(webDomain)?.let { identifiers += "web:$it" }
        normalizePackageName(packageName)?.let { identifiers += "app:$it" }
        val primary = identifiers.firstOrNull()
        val aliases = identifiers.drop(1)
        return AutofillInteractionContext(
            primaryIdentifier = primary,
            aliasIdentifiers = aliases,
        )
    }

    fun isPasswordOnlyLogin(targets: List<ParsedItem>): Boolean {
        return isPasswordOnlyLoginHints(targets.map { it.hint })
    }

    fun isPasswordOnlyLoginHints(hints: List<FieldHint>): Boolean {
        if (hints.isEmpty()) return false
        val loginTargets = hints.filter { hint ->
            hint == FieldHint.USERNAME ||
                hint == FieldHint.EMAIL_ADDRESS ||
                hint == FieldHint.PHONE_NUMBER ||
                hint == FieldHint.PASSWORD ||
                hint == FieldHint.NEW_PASSWORD
        }
        if (loginTargets.isEmpty()) return false
        val hasPasswordTarget = loginTargets.any {
            it == FieldHint.PASSWORD || it == FieldHint.NEW_PASSWORD
        }
        if (!hasPasswordTarget) return false
        val hasAccountTarget = loginTargets.any {
            it == FieldHint.USERNAME ||
                it == FieldHint.EMAIL_ADDRESS ||
                it == FieldHint.PHONE_NUMBER
        }
        return !hasAccountTarget
    }

    fun prioritizeLastFilled(
        entries: List<PasswordEntry>,
        lastFilled: PasswordEntry?,
    ): List<PasswordEntry> {
        if (lastFilled == null) return entries
        val reordered = linkedMapOf<Long, PasswordEntry>()
        reordered[lastFilled.id] = lastFilled
        entries.forEach { entry -> reordered.putIfAbsent(entry.id, entry) }
        return reordered.values.toList()
    }

    private fun normalizeWebDomain(value: String?): String? {
        return value
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.removePrefix("www.")
            ?.trim('.')
            ?.takeIf { it.isNotBlank() }
    }

    private fun normalizePackageName(value: String?): String? {
        return value
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
    }
}
