package takagi.ru.monica.attachments.crypto

import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Bitwarden 附件专用加解密。
 *
 * 和 [BitwardenCrypto] 的 string-in/string-out 模式不同，这里采用"流式 + HMAC-trailer"的
 * 格式，可以承载最多 100MB 的附件字节，不会一次性把密文/明文加载进内存。
 *
 * # 附件文件字节布局（Bitwarden 官方格式）
 *
 * ```
 *  offset 0   +16                    ...          EOF-32   EOF
 *  +----------+-----------------------+-------------+
 *  | 16B IV   | AES-CBC-PKCS7 密文     | 32B HMAC    |
 *  +----------+-----------------------+-------------+
 * ```
 *
 * - `AES key` 与 `MAC key` 都来自 [SymmetricCryptoKey]，各 32B；
 * - HMAC-SHA256 的输入是 `IV || ciphertext`（不含 MAC 尾缀本身）。
 *
 * # 附件密钥（attachment key）
 *
 * 服务器返回的 `bitwardenFileKeyEnc` 是一个 EncString（type=0 或 type=2），
 * 用上层 cipher key（如果 cipher 有独立 key）或 user key 解包后得到 64B 的
 * `encKey || macKey`，也即本附件的 [SymmetricCryptoKey]。
 *
 * 对应 requirements.md Requirement 5.3 / 5.4。
 */
object BitwardenAttachmentCrypto {

    private const val IV_SIZE = 16
    private const val MAC_SIZE = 32
    private const val KEY_MATERIAL_SIZE = 64
    private const val BUFFER_SIZE = 16 * 1024

    private val rng: SecureRandom by lazy { SecureRandom() }

    // ---------------------------------------------------------------- 附件密钥

    /**
     * 用 cipher key（或 user key）解出附件独立密钥。
     *
     * [fileKeyEnc] 是 EncString 格式（`type.iv|data|mac`），[wrappingKey] 可以是：
     * - cipher 自带 key（当 cipher 被 per-item key 保护时），或
     * - user symmetric key（默认情况）。
     *
     * 返回的 [SymmetricCryptoKey] 长度固定 64B，调用方用完应 [SymmetricCryptoKey.clear]。
     */
    fun unwrapAttachmentKey(
        fileKeyEnc: String,
        wrappingKey: SymmetricCryptoKey
    ): SymmetricCryptoKey {
        val raw = BitwardenCrypto.decrypt(fileKeyEnc, wrappingKey)
        require(raw.size == KEY_MATERIAL_SIZE) {
            "Attachment key must be 64 bytes, got ${raw.size}"
        }
        return try {
            SymmetricCryptoKey(
                encKey = raw.copyOfRange(0, 32),
                macKey = raw.copyOfRange(32, 64)
            )
        } finally {
            raw.fill(0)
        }
    }

    /**
     * 生成一个新的附件密钥并用 [wrappingKey] 包裹，返回 `(attachmentKey, fileKeyEnc)`。
     *
     * 调用方应在上传完成后 `attachmentKey.clear()`。
     */
    fun generateAndWrapAttachmentKey(wrappingKey: SymmetricCryptoKey): Pair<SymmetricCryptoKey, String> {
        val raw = ByteArray(KEY_MATERIAL_SIZE).also(rng::nextBytes)
        return try {
            val attachmentKey = SymmetricCryptoKey(
                encKey = raw.copyOfRange(0, 32),
                macKey = raw.copyOfRange(32, 64)
            )
            val fileKeyEnc = BitwardenCrypto.encrypt(raw, wrappingKey)
            attachmentKey to fileKeyEnc
        } finally {
            raw.fill(0)
        }
    }

    // ---------------------------------------------------------------- 文件字节 · 加密

    /**
     * 把 [source] 的明文字节以 Bitwarden 附件格式流式加密到 [sink]，中途不会产生超过
     * [BUFFER_SIZE] + 一个块的内存占用。
     *
     * @return 明文字节数 + SHA-256（调用方需要时可以用于元数据校验）。
     */
    fun encryptStream(
        source: InputStream,
        sink: OutputStream,
        attachmentKey: SymmetricCryptoKey
    ): StreamResult {
        val iv = ByteArray(IV_SIZE).also(rng::nextBytes)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(attachmentKey.encKey, "AES"), IvParameterSpec(iv))
        }
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(attachmentKey.macKey, "HmacSHA256"))
        }
        val plainDigest = MessageDigest.getInstance("SHA-256")

        // 写 IV 明文前缀 + 纳入 MAC
        sink.write(iv)
        mac.update(iv)

        var plainSize = 0L
        val buf = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = source.read(buf)
            if (read <= 0) break
            plainDigest.update(buf, 0, read)
            plainSize += read
            val ct = cipher.update(buf, 0, read)
            if (ct != null && ct.isNotEmpty()) {
                sink.write(ct)
                mac.update(ct)
            }
        }
        val tail = cipher.doFinal()
        if (tail.isNotEmpty()) {
            sink.write(tail)
            mac.update(tail)
        }
        val macBytes = mac.doFinal()
        sink.write(macBytes)
        sink.flush()

        return StreamResult(
            plainSizeBytes = plainSize,
            plainSha256Hex = plainDigest.digest().joinToString("") { "%02x".format(it) }
        )
    }

    // ---------------------------------------------------------------- 文件字节 · 解密

    /**
     * 把 [source] 的 Bitwarden 附件密文流式解密到 [sink]。
     *
     * 解密期间会同步计算 HMAC。`sink` 上会先收到部分明文字节（属于 AES-CBC `update` 的
     * 输出），最终 `doFinal` 的字节在 MAC 校验通过后才会被写入；若 MAC 校验失败将抛出
     * [SecurityException]，此时 [sink] 里已有的字节必须被调用方丢弃。
     */
    fun decryptStream(
        source: InputStream,
        sink: OutputStream,
        attachmentKey: SymmetricCryptoKey
    ): StreamResult {
        // 读取 IV 前缀
        val iv = ByteArray(IV_SIZE)
        if (readFully(source, iv) != IV_SIZE) {
            throw IllegalArgumentException("Attachment header truncated")
        }

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(attachmentKey.encKey, "AES"), IvParameterSpec(iv))
        }
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(attachmentKey.macKey, "HmacSHA256"))
            update(iv)
        }
        val plainDigest = MessageDigest.getInstance("SHA-256")

        // 滑动尾缀缓冲：始终保留"可能是 MAC"的最后 32 字节不立刻喂给 cipher/mac。
        val trailer = TailBuffer(MAC_SIZE)
        val buf = ByteArray(BUFFER_SIZE)
        var plainSize = 0L

        while (true) {
            val read = source.read(buf)
            if (read <= 0) break
            val released = trailer.push(buf, 0, read)
            if (released.isNotEmpty()) {
                mac.update(released)
                val pt = cipher.update(released)
                if (pt != null && pt.isNotEmpty()) {
                    sink.write(pt)
                    plainDigest.update(pt)
                    plainSize += pt.size
                }
            }
        }

        val macTrailer = trailer.snapshotFull()
            ?: throw SecurityException("Attachment truncated before MAC")

        val expectedMac = mac.doFinal()
        if (!MessageDigest.isEqual(expectedMac, macTrailer)) {
            throw SecurityException("Attachment MAC verification failed")
        }

        // MAC 校验通过，再写 cipher.doFinal 的最后明文
        val finalBlock = cipher.doFinal()
        if (finalBlock.isNotEmpty()) {
            sink.write(finalBlock)
            plainDigest.update(finalBlock)
            plainSize += finalBlock.size
        }
        sink.flush()

        return StreamResult(
            plainSizeBytes = plainSize,
            plainSha256Hex = plainDigest.digest().joinToString("") { "%02x".format(it) }
        )
    }

    data class StreamResult(
        val plainSizeBytes: Long,
        val plainSha256Hex: String
    )

    // ---------------------------------------------------------------- 辅助

    private fun readFully(source: InputStream, target: ByteArray): Int {
        var off = 0
        while (off < target.size) {
            val n = source.read(target, off, target.size - off)
            if (n <= 0) break
            off += n
        }
        return off
    }

    /**
     * 固定容量尾缀缓冲区：通过 [push] 不断塞入新字节，返回"已经不再可能是尾缀"的那部分字节。
     * 在流末尾调用 [snapshotFull] 即可拿到真正的尾缀（MAC）。
     */
    private class TailBuffer(private val capacity: Int) {
        private val buf = ByteArray(capacity)
        private var size = 0

        fun push(src: ByteArray, off: Int, len: Int): ByteArray {
            if (len <= 0) return EMPTY
            val total = size + len
            return when {
                total <= capacity -> {
                    System.arraycopy(src, off, buf, size, len)
                    size = total
                    EMPTY
                }
                else -> {
                    val overflow = total - capacity
                    val released = ByteArray(overflow)
                    // 先释放旧尾缀里被挤出的前 overflow 字节（尽量少拷贝）
                    if (overflow <= size) {
                        System.arraycopy(buf, 0, released, 0, overflow)
                        System.arraycopy(buf, overflow, buf, 0, size - overflow)
                        size -= overflow
                        // 再把新输入追加到尾缀末尾
                        System.arraycopy(src, off, buf, size, len)
                        size += len
                    } else {
                        // 旧尾缀全部被挤出，且新输入也有部分要被释放
                        System.arraycopy(buf, 0, released, 0, size)
                        val srcReleasedLen = overflow - size
                        System.arraycopy(src, off, released, size, srcReleasedLen)
                        // 新输入剩余部分全放入 buf
                        val remaining = len - srcReleasedLen
                        System.arraycopy(src, off + srcReleasedLen, buf, 0, remaining)
                        size = remaining
                    }
                    released
                }
            }
        }

        fun snapshotFull(): ByteArray? {
            if (size != capacity) return null
            return buf.copyOf(capacity)
        }

        companion object {
            private val EMPTY = ByteArray(0)
        }
    }

    // ---------------------------------------------------------------- 方便 UTF-8 版本

    /**
     * 把短字符串加密为 Bitwarden EncString，便于 attachments 请求里需要传
     * `fileName`（加密形式）的场景复用。
     */
    fun encryptStringForAttachment(plaintext: String, wrappingKey: SymmetricCryptoKey): String =
        BitwardenCrypto.encryptString(plaintext, wrappingKey)
}
