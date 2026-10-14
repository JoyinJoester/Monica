package takagi.ru.monica.autofill_ng

import android.os.SystemClock
import android.view.autofill.AutofillId
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.ParsedItem
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.FieldHint

/** Short-lived, bounded structural fallback. Typed values are never retained. */
internal class AutofillStructureMemory(private val clock: () -> Long = SystemClock::elapsedRealtime) {
    private data class Entry(val at: Long, val sequence: Long, val fields: List<ParsedItem>)
    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized fun put(key: String, fields: List<ParsedItem>, sequence: Long) {
        expire()
        if ((entries[key]?.sequence ?: Long.MIN_VALUE) > sequence) return
        if (fields.size > 64 || fields.any { it.hint == FieldHint.NEW_PASSWORD }) {
            entries.remove(key)
            return
        }
        entries.remove(key)
        entries[key] = Entry(clock(), sequence, fields.map { it.copy(value = null) })
        while (entries.size > 32) entries.remove(entries.keys.first())
    }

    @Synchronized fun get(key: String, ids: Set<AutofillId>): List<ParsedItem>? {
        expire()
        return entries[key]?.fields?.takeIf { it.isNotEmpty() && it.all { field -> field.id in ids } }
    }

    @Synchronized fun clear() = entries.clear()

    private fun expire() {
        val now = clock()
        entries.entries.removeAll { now - it.value.at !in 0 until 15_000L }
    }
}
