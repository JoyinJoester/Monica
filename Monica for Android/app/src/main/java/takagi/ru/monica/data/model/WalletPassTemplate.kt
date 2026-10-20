package takagi.ru.monica.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Wallet templates share the portable document envelope, attachments and storage pipeline. */
enum class WalletPassTemplate {
    MEMBERSHIP, REWARDS, TRAVEL;

    companion object {
        fun fromStored(value: String): WalletPassTemplate? = entries.firstOrNull { it.name == value }
    }
}

object WalletPassFieldsCodec {
    fun decode(raw: String): Map<String, String> = runCatching {
        (Json.parseToJsonElement(raw) as JsonObject).mapNotNull { (key, value) ->
            (value as? JsonPrimitive)?.contentOrNull?.let { key to it }
        }.toMap()
    }.getOrDefault(emptyMap())
}
