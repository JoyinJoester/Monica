package takagi.ru.monica.autofill_ng

/** A repeated profile/card role starts another block when the page omitted section tokens. */
internal object AutofillRepeatedFieldScope {
    fun <T> select(fields: List<Pair<T, String>>, focused: T): Set<T>? {
        val focusHint = fields.firstOrNull { it.first == focused }?.second ?: return null
        fun category(raw: String): Int {
            val hint = AutofillProfileValues.canonical(raw)
            return when {
                hint.startsWith("cc-") -> 1
                hint in setOf("username", "password", "new-password", "one-time-code") -> 0
                else -> 2
            }
        }
        val focusCategory = category(focusHint)
        if (focusCategory == 0) return null
        val seen = mutableSetOf<String>()
        val groups = mutableListOf<MutableSet<T>>(linkedSetOf())
        fields.filter { category(it.second) == focusCategory }.forEach { (id, hint) ->
            if (!seen.add(AutofillProfileValues.canonical(hint))) {
                groups.add(linkedSetOf())
                seen.clear()
                seen.add(AutofillProfileValues.canonical(hint))
            }
            groups.last().add(id)
        }
        return if (groups.size > 1) groups.firstOrNull { focused in it } else null
    }
}
