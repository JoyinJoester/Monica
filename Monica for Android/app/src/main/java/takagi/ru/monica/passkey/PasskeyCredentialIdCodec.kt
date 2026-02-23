package takagi.ru.monica.passkey

import android.util.Base64
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Credential ID 编解码兼容层。
 *
 * 兼容两种常见表示：
 * - Base64URL 字符串（WebAuthn 常见传输格式）
 * - UUID 字符串（keyguard 在 16-byte credentialId 上的文本格式）
 */
object PasskeyCredentialIdCodec {
    private const val UUID_BYTE_SIZE = 16

    /**
     * 归一化为“比较用”格式：
     * - 16-byte id 统一为 UUID 文本
     * - 非 16-byte id 统一为 Base64URL（无填充）
     * - 无法识别时返回原始字符串（trim 后）
     */
    fun normalize(credentialId: String?): String? {
        val raw = credentialId?.trim().orEmpty()
        if (raw.isEmpty()) return null

        parseUuid(raw)?.let { return it.toString() }

        val decoded = decodeFlexible(raw) ?: return raw
        return if (decoded.size == UUID_BYTE_SIZE) {
            bytesToUuid(decoded)?.toString() ?: toBase64Url(decoded)
        } else {
            toBase64Url(decoded)
        }
    }

    /**
     * 转换为 WebAuthn 响应中可用的 id/rawId 字符串（Base64URL，无填充）。
     */
    fun toWebAuthnId(credentialId: String?): String? {
        val raw = credentialId?.trim().orEmpty()
        if (raw.isEmpty()) return null

        parseUuid(raw)?.let { return toBase64Url(uuidToBytes(it)) }

        val decoded = decodeFlexible(raw) ?: return raw
        return toBase64Url(decoded)
    }

    /**
     * 转换为 Bitwarden 侧更易与 keyguard 对齐的格式。
     * 当前与 normalize 一致：16-byte id 输出 UUID 文本。
     */
    fun toBitwardenCredentialId(credentialId: String?): String? = normalize(credentialId)

    private fun parseUuid(value: String): UUID? = runCatching {
        UUID.fromString(value)
    }.getOrNull()

    private fun decodeFlexible(value: String): ByteArray? {
        val urlSafeFlags = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        return runCatching { Base64.decode(value, urlSafeFlags) }.getOrNull()
            ?: runCatching { Base64.decode(value, Base64.DEFAULT) }.getOrNull()
    }

    private fun toBase64Url(data: ByteArray): String {
        val flags = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        return Base64.encodeToString(data, flags)
    }

    private fun bytesToUuid(data: ByteArray): UUID? {
        if (data.size != UUID_BYTE_SIZE) return null
        val buffer = ByteBuffer.wrap(data)
        return UUID(buffer.long, buffer.long)
    }

    private fun uuidToBytes(uuid: UUID): ByteArray {
        return ByteBuffer.allocate(UUID_BYTE_SIZE)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
    }
}

