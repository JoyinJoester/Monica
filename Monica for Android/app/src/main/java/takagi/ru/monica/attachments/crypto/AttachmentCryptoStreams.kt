package takagi.ru.monica.attachments.crypto

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 附件本地密文的 AES-256-GCM 流式加解密工厂。
 *
 * 文件字节布局（每个附件独立一份）：
 *
 * ```
 *  offset 0        +12        ...        ...-16        ...(EOF)
 *  +---------------+-------------------+---------------+
 *  |   12B  IV     |    GCM ciphertext |   16B tag     |
 *  +---------------+-------------------+---------------+
 * ```
 *
 * 注意：
 * - 这里统一使用 128-bit（16 字节）认证 tag。
 * - 每次写入都必须用 [newIv] 生成独立 IV，禁止复用。
 * - 调用方负责关闭返回的流；`close()` 才会触发 GCM 的 `doFinal`，完成 tag 写入或校验。
 *
 * 对应 requirements.md Requirement 2.1 / 2.3 / 2.4。
 */
internal object AttachmentCryptoStreams {

    const val IV_SIZE: Int = 12
    const val TAG_SIZE_BITS: Int = 128
    const val CEK_SIZE_BYTES: Int = 32
    private const val TRANSFORMATION: String = "AES/GCM/NoPadding"

    private val rng: SecureRandom by lazy { SecureRandom() }

    /** 生成一个 12 字节随机 IV。 */
    fun newIv(): ByteArray = ByteArray(IV_SIZE).also(rng::nextBytes)

    /** 生成一个 32 字节随机 CEK。 */
    fun newCek(): ByteArray = ByteArray(CEK_SIZE_BYTES).also(rng::nextBytes)

    /**
     * 用 [cek] 和 [iv] 初始化一个加密 [CipherOutputStream]，下游写入的字节会被加密后再写入 [out]。
     *
     * 调用方需要在 `out.write(iv)` 之后再用本函数获取 CipherOutputStream；
     * 这样 IV 不会被加密流吞掉，而是以明文前缀形式留在文件头。
     */
    fun encryptingStream(out: OutputStream, cek: ByteArray, iv: ByteArray): CipherOutputStream {
        require(cek.size == CEK_SIZE_BYTES) { "CEK must be 32 bytes" }
        require(iv.size == IV_SIZE) { "IV must be 12 bytes" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(TAG_SIZE_BITS, iv))
        return CipherOutputStream(out, cipher)
    }

    /**
     * 用 [cek] 和 [iv] 初始化一个解密 [CipherInputStream]，从上游读到的字节为密文。
     *
     * 调用方需要已经把 IV 从 [src] 的前 12 字节读掉并传进来。
     */
    fun decryptingStream(src: InputStream, cek: ByteArray, iv: ByteArray): CipherInputStream {
        require(cek.size == CEK_SIZE_BYTES) { "CEK must be 32 bytes" }
        require(iv.size == IV_SIZE) { "IV must be 12 bytes" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(TAG_SIZE_BITS, iv))
        return CipherInputStream(src, cipher)
    }
}
