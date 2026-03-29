package takagi.ru.monica.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SshKeyData(
    val algorithm: String = "",
    val keySize: Int = 0,
    val publicKeyOpenSsh: String = "",
    val privateKeyOpenSsh: String = "",
    val fingerprintSha256: String = "",
    val comment: String = "",
    val format: String = FORMAT_OPENSSH
) {
    fun isEmpty(): Boolean {
        return algorithm.isBlank() &&
            publicKeyOpenSsh.isBlank() &&
            privateKeyOpenSsh.isBlank() &&
            fingerprintSha256.isBlank()
    }

    companion object {
        const val ALGORITHM_ED25519 = "ED25519"
        const val ALGORITHM_RSA = "RSA"
        const val FORMAT_OPENSSH = "OPENSSH"
    }
}

object SshKeyDataCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun decode(raw: String?): SshKeyData? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<SshKeyData>(raw) }
            .getOrNull()
            ?.takeUnless { it.isEmpty() }
    }

    fun encode(data: SshKeyData?): String {
        if (data == null || data.isEmpty()) return ""
        return json.encodeToString(data)
    }
}
