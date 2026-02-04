package takagi.ru.monica.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PasskeyBinding(
    val credentialId: String = "",
    val rpId: String = "",
    val rpName: String = "",
    val userName: String = "",
    val userDisplayName: String = ""
)

object PasskeyBindingCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun decodeList(raw: String): MutableList<PasskeyBinding> {
        if (raw.isBlank()) return mutableListOf()
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun encodeList(list: List<PasskeyBinding>): String {
        return try {
            json.encodeToString(list)
        } catch (e: Exception) {
            ""
        }
    }

    fun addBinding(raw: String, binding: PasskeyBinding): String {
        val list = decodeList(raw)
        val existingIndex = list.indexOfFirst { it.credentialId == binding.credentialId && it.credentialId.isNotBlank() }
        if (existingIndex >= 0) {
            list[existingIndex] = binding
        } else {
            list.add(binding)
        }
        return encodeList(list)
    }

    fun removeBinding(raw: String, credentialId: String): String {
        if (credentialId.isBlank()) return raw
        val list = decodeList(raw).filterNot { it.credentialId == credentialId }
        return encodeList(list)
    }
}
