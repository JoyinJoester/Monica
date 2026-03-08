package takagi.ru.monica.utils

import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import java.security.MessageDigest
import java.util.Base64
import java.util.LinkedHashMap
import java.util.Locale

data class KeePassCredentialCandidate(
    val label: String,
    val credentials: Credentials
)

object KeePassCredentialSupport {
    fun buildCredentialCandidates(password: String, keyFileBytes: ByteArray?): List<KeePassCredentialCandidate> {
        if (keyFileBytes == null) {
            return listOf(
                KeePassCredentialCandidate(
                    label = "password-only",
                    credentials = Credentials.from(EncryptedValue.fromString(password))
                )
            )
        }

        val keyVariants = buildKeyMaterialVariants(keyFileBytes)
        val candidates = mutableListOf<KeePassCredentialCandidate>()
        val seen = linkedSetOf<String>()

        fun addKeyBasedCandidate(label: String, keyBytes: ByteArray, includeEmptyPasswordVariant: Boolean) {
            val keyFingerprint = sha256Hex(keyBytes)
            if (password.isBlank()) {
                val keyOnlySig = "key-only:$keyFingerprint"
                if (seen.add(keyOnlySig)) {
                    candidates += KeePassCredentialCandidate(
                        label = "$label/key-only",
                        credentials = Credentials.from(keyBytes)
                    )
                }
                if (includeEmptyPasswordVariant) {
                    val comboSig = "empty-password+key:$keyFingerprint"
                    if (seen.add(comboSig)) {
                        candidates += KeePassCredentialCandidate(
                            label = "$label/empty-password+key",
                            credentials = Credentials.from(EncryptedValue.fromString(""), keyBytes)
                        )
                    }
                }
            } else {
                val comboSig = "password+key:$keyFingerprint:${password.length}"
                if (seen.add(comboSig)) {
                    candidates += KeePassCredentialCandidate(
                        label = "$label/password+key",
                        credentials = Credentials.from(EncryptedValue.fromString(password), keyBytes)
                    )
                }
            }
        }

        keyVariants.forEach { (label, keyBytes) ->
            // 历史兼容：空密码 + keyfile 数据库需要同时尝试 key-only 与 empty-password+key 两种组合。
            addKeyBasedCandidate(label = label, keyBytes = keyBytes, includeEmptyPasswordVariant = true)
        }

        return candidates
    }

    fun buildInvalidCredentialMessage(attemptedLabels: List<String>): String {
        val distinct = attemptedLabels.distinct()
        if (distinct.isEmpty()) {
            return "数据库密码或密钥文件不正确"
        }
        val concise = distinct.take(4).joinToString(separator = ", ")
        val suffix = if (distinct.size > 4) " 等${distinct.size}种组合" else ""
        return "数据库密码或密钥文件不正确（已尝试: $concise$suffix）"
    }

    private fun buildKeyMaterialVariants(rawBytes: ByteArray): List<Pair<String, ByteArray>> {
        val variants = LinkedHashMap<String, Pair<String, ByteArray>>()

        fun putVariant(label: String, keyBytes: ByteArray?) {
            if (keyBytes == null || keyBytes.isEmpty()) return
            val hash = sha256Hex(keyBytes)
            if (!variants.containsKey(hash)) {
                variants[hash] = label to keyBytes
            }
        }

        putVariant("raw", rawBytes)

        val text = runCatching { rawBytes.toString(Charsets.UTF_8) }.getOrNull()
        if (text != null) {
            putVariant("xml-data", extractXmlDataKey(text))
            putVariant("hex-text", extractHexTextKey(text))
        }

        putVariant("sha256(raw)", sha256(rawBytes))

        return variants.values.toList()
    }

    private fun extractXmlDataKey(content: String): ByteArray? {
        val regex = Regex("<Data>(.*?)</Data>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val value = regex.find(content)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (value.isBlank()) return null
        val compact = value.replace("\\s".toRegex(), "")
        return decodeCompactKeyData(compact)
    }

    private fun extractHexTextKey(content: String): ByteArray? {
        val compact = content.replace("\\s".toRegex(), "")
        if (compact.isBlank()) return null
        if (compact.length != 64) return null
        if (!compact.all { it.isHexChar() }) return null
        return decodeHex(compact)
    }

    private fun decodeCompactKeyData(compact: String): ByteArray? {
        if (compact.length == 64 && compact.all { it.isHexChar() }) {
            return decodeHex(compact)
        }
        return runCatching { Base64.getDecoder().decode(compact) }.getOrNull()
    }

    private fun Char.isHexChar(): Boolean {
        return this in '0'..'9' || this.lowercaseChar() in 'a'..'f'
    }

    private fun decodeHex(value: String): ByteArray? {
        val clean = value.lowercase(Locale.ROOT)
        if (clean.length % 2 != 0 || !clean.all { it.isHexChar() }) return null
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val hi = clean[i * 2].digitToIntOrNull(16) ?: return null
            val lo = clean[i * 2 + 1].digitToIntOrNull(16) ?: return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun sha256(input: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(input)
    }

    private fun sha256Hex(input: ByteArray): String {
        return sha256(input).joinToString(separator = "") { b ->
            "%02x".format(Locale.US, b.toInt() and 0xff)
        }
    }
}
