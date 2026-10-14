package takagi.ru.monica.autofill_ng

import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.FieldHint

internal object AutofillSaveValuePolicy {
    /** A blank/mismatched new password must never fall back to the old password. */
    fun password(fields: List<Pair<FieldHint, String?>>): String? {
        val new = fields.filter { it.first == FieldHint.NEW_PASSWORD }
        val candidates = new.ifEmpty { fields.filter { it.first == FieldHint.PASSWORD } }
        if (candidates.isEmpty() || candidates.any { it.second.isNullOrBlank() }) return null
        return candidates.map { it.second }.distinct().singleOrNull()
    }
}
