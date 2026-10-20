package takagi.ru.monica.autofill_ng

import takagi.ru.monica.data.CustomFieldDao
import takagi.ru.monica.data.model.PasswordUrlRules

internal object PasswordUrlRuleStore {
    suspend fun load(dao: CustomFieldDao, ids: List<Long>): Map<Long, List<PasswordUrlRules.Rule>> =
        ids.distinct().chunked(800).flatMap { dao.getUrlMatchRules(it) }.groupBy { it.entryId }
            .mapValues { (_, fields) ->
                // Duplicate, malformed or future metadata cannot silently broaden matching.
                fields.singleOrNull()?.let { PasswordUrlRules.decode(it.value) }.orEmpty()
            }
}
