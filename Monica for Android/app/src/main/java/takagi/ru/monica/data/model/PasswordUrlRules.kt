package takagi.ru.monica.data.model

import kotlinx.serialization.json.*
import takagi.ru.monica.data.CustomFieldDraft

/** URL rows travel with the existing custom-field save/sync/export pipeline. */
object PasswordUrlRules {
    const val FIELD = "monica.autofill.url_rules"
    enum class Mode { DEFAULT, BASE_DOMAIN, HOST, STARTS_WITH, EXACT, REGEX, NEVER }
    data class Rule(val url: String, val mode: Mode = Mode.DEFAULT)

    fun decode(value: String): List<Rule>? = runCatching {
        val root = Json.parseToJsonElement(value).jsonObject
        require(root["version"]?.jsonPrimitive?.int == 1)
        root.getValue("urls").jsonArray.map {
            val row = it.jsonObject
            require(row.getValue("url").jsonPrimitive.isString)
            require(row.getValue("mode").jsonPrimitive.isString)
            Rule(row.getValue("url").jsonPrimitive.content,
                Mode.valueOf(row.getValue("mode").jsonPrimitive.content))
        }.also { require(it.isNotEmpty()) }
    }.getOrNull()

    fun read(fields: List<CustomFieldDraft>): List<Rule>? =
        fields.singleOrNull { it.title == FIELD }?.let { decode(it.value) }

    fun put(fields: List<CustomFieldDraft>, rules: List<Rule>): List<CustomFieldDraft> {
        require(fields.none { it.title == FIELD } || read(fields) != null) {
            "Unsupported URL matching metadata must be preserved"
        }
        val retained = fields.filterNot { it.title == FIELD }
        val rows = rules.filter { it.url.isNotBlank() }.map { it.copy(url = it.url.trim()) }
        if (rows.none { it.mode != Mode.DEFAULT }) return retained
        val value = buildJsonObject {
            put("version", 1)
            put("urls", buildJsonArray { rows.forEach { rule ->
                add(buildJsonObject { put("url", rule.url); put("mode", rule.mode.name) })
            } })
        }.toString()
        return retained + (fields.firstOrNull { it.title == FIELD }?.copy(value = value)
            ?: CustomFieldDraft(title = FIELD, value = value))
    }
}
