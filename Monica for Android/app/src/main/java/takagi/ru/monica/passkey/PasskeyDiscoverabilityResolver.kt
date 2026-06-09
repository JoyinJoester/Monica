package takagi.ru.monica.passkey

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

object PasskeyDiscoverabilityResolver {
    private val parser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun isDiscoverableCreationRequest(requestJson: String): Boolean {
        if (requestJson.isBlank()) return false
        return runCatching {
            val authSelection = requestOptions(requestJson)?.get("authenticatorSelection") as? JsonObject
            isDiscoverableAuthenticatorSelection(authSelection)
        }.getOrDefault(false)
    }

    fun isCredPropsRequested(requestJson: String): Boolean {
        if (requestJson.isBlank()) return false
        return runCatching {
            val extensions = requestOptions(requestJson)?.get("extensions") as? JsonObject
            isCredPropsValueRequested(extensions?.get("credProps"))
        }.getOrDefault(false)
    }

    internal fun isDiscoverableAuthenticatorSelection(authSelection: JsonObject?): Boolean {
        if (authSelection == null) return false
        return isDiscoverableAuthenticatorSelection(
            residentKey = authSelection["residentKey"]?.stringContent(),
            requireResidentKey = authSelection["requireResidentKey"],
        )
    }

    internal fun isDiscoverableAuthenticatorSelection(
        residentKey: String?,
        requireResidentKey: Any?,
    ): Boolean {
        val normalizedResidentKey = residentKey
            .orEmpty()
            .trim()
            .lowercase(Locale.ROOT)

        if (normalizedResidentKey == "required" || normalizedResidentKey == "preferred") {
            return true
        }

        return parseBooleanCompat(requireResidentKey)
    }

    internal fun parseBooleanCompat(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is String -> value.trim().equals("true", ignoreCase = true)
            is JsonElement -> value.booleanCompat()
            else -> false
        }
    }

    internal fun isCredPropsValueRequested(value: Any?): Boolean {
        return parseBooleanCompat(value)
    }

    private fun requestOptions(requestJson: String): JsonObject? {
        val root = parser.parseToJsonElement(requestJson) as? JsonObject ?: return null
        return root["publicKey"] as? JsonObject ?: root
    }

    private fun JsonElement.stringContent(): String? {
        return (this as? JsonPrimitive)?.contentOrNull
    }

    private fun JsonElement.booleanCompat(): Boolean {
        val primitive = this as? JsonPrimitive ?: return false
        primitive.booleanOrNull?.let { return it }
        return primitive.contentOrNull
            ?.trim()
            ?.equals("true", ignoreCase = true)
            ?: false
    }
}
