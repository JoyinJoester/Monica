package takagi.ru.monica.attachments.storage

import android.util.Base64
import takagi.ru.monica.attachments.crypto.AttachmentCryptoStreams
import takagi.ru.monica.security.SecurityManager

/**
 * 负责 Attachment_CEK 的包裹/解包。
 *
 * 复用 [SecurityManager.encryptData] / [SecurityManager.decryptData] 通道，
 * 这样 CEK 包裹后的格式与全局主密钥（MDK）绑定，用户更换主密码/重置 MDK 时的轮换
 * 行为与现有加密字段一致。
 *
 * 写入数据库时 `wrappedCek` 存的是 [SecurityManager] 生成的带前缀 Base64 字符串，
 * 原始 32 字节 CEK 在内存中使用完即丢弃（调用方负责清理）。
 *
 * 对应 requirements.md Requirement 2.2。
 */
class AttachmentKeyVault(private val securityManager: SecurityManager) {

    /** 把 32B CEK 用主密钥包裹为可持久化字符串。 */
    fun wrap(cek: ByteArray): String {
        require(cek.size == AttachmentCryptoStreams.CEK_SIZE_BYTES) {
            "CEK must be ${AttachmentCryptoStreams.CEK_SIZE_BYTES} bytes"
        }
        val base64 = Base64.encodeToString(cek, Base64.NO_WRAP)
        return securityManager.encryptData(base64)
    }

    /** 从持久化字符串还原 32B CEK；失败时抛异常由上层转为 `AttachmentError.CryptoError`。 */
    fun unwrap(wrapped: String): ByteArray {
        val decoded = securityManager.decryptData(wrapped)
        val cek = Base64.decode(decoded, Base64.NO_WRAP)
        check(cek.size == AttachmentCryptoStreams.CEK_SIZE_BYTES) {
            "Unwrapped CEK has unexpected size ${cek.size}"
        }
        return cek
    }
}
