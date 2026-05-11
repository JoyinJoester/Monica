package takagi.ru.monica.utils

import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.AsymmetricCipherKeyPairGenerator
import org.bouncycastle.crypto.KeyGenerationParameters
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.crypto.params.RSAKeyParameters
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import org.bouncycastle.util.encoders.Base64
import takagi.ru.monica.data.model.SshKeyData
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * SSH 密钥生成器。
 *
 * 纯函数、无状态。基于 BouncyCastle `OpenSSHPublicKeyUtil`/`OpenSSHPrivateKeyUtil`
 * 生成符合 OpenSSH 约定的文本公钥、PEM 私钥与 SHA256 指纹。
 *
 * 参考实现：参考项目/keyguard-app-master `KeyPairGeneratorImpl.kt`。
 */
object SshKeyGenerator {

    private const val RSA_PUBLIC_EXPONENT: Long = 0x10001L
    private const val ED25519_STRENGTH: Int = 255
    private const val ED25519_KEY_SIZE: Int = 256

    private const val PEM_LINE_LEN_RSA: Int = 64
    private const val PEM_LINE_LEN_OPENSSH: Int = 70

    /** 允许的 RSA 位数。保持与 UI 下拉保持一致。 */
    val RSA_ALLOWED_KEY_SIZES: Set<Int> = setOf(2048, 3072, 4096)

    /** 默认 RSA 位数（与生成器 UI 默认选项保持一致）。 */
    const val DEFAULT_RSA_KEY_SIZE: Int = 3072

    /** 新建 SSH 密钥默认使用 Ed25519，生成速度与现代 SSH 客户端兼容性都更好。 */
    const val DEFAULT_ALGORITHM: String = SshKeyData.ALGORITHM_ED25519

    sealed class Request {
        data class Rsa(val keySize: Int = DEFAULT_RSA_KEY_SIZE) : Request()
        data object Ed25519 : Request()
    }

    /**
     * 生成一个新的 SSH 密钥对。
     *
     * @throws IllegalArgumentException 当 [Request.Rsa.keySize] 不在 [RSA_ALLOWED_KEY_SIZES]。
     */
    fun generate(request: Request, comment: String = ""): SshKeyData = when (request) {
        is Request.Rsa -> generateRsa(request.keySize, comment)
        Request.Ed25519 -> generateEd25519(comment)
    }

    // region Impl ---------------------------------------------------------------------

    private fun generateRsa(keySize: Int, comment: String): SshKeyData {
        require(keySize in RSA_ALLOWED_KEY_SIZES) {
            "RSA key size must be one of $RSA_ALLOWED_KEY_SIZES, got $keySize"
        }
        val random = SecureRandom()
        val generator = RSAKeyPairGenerator().apply {
            init(
                RSAKeyGenerationParameters(
                    BigInteger.valueOf(RSA_PUBLIC_EXPONENT),
                    random,
                    keySize,
                    rsaCertainty(keySize)
                )
            )
        }
        return buildSshKeyData(
            algorithm = SshKeyData.ALGORITHM_RSA,
            keySize = keySize,
            publicKeyPrefix = "ssh-rsa",
            privateKeyHeader = "RSA PRIVATE KEY",
            privateKeyLineLength = PEM_LINE_LEN_RSA,
            keyPair = generator.generateKeyPair(),
            comment = comment
        )
    }

    private fun generateEd25519(comment: String): SshKeyData {
        val random = SecureRandom()
        val generator: AsymmetricCipherKeyPairGenerator = Ed25519KeyPairGenerator().apply {
            init(KeyGenerationParameters(random, ED25519_STRENGTH))
        }
        return buildSshKeyData(
            algorithm = SshKeyData.ALGORITHM_ED25519,
            keySize = ED25519_KEY_SIZE,
            publicKeyPrefix = "ssh-ed25519",
            privateKeyHeader = "OPENSSH PRIVATE KEY",
            privateKeyLineLength = PEM_LINE_LEN_OPENSSH,
            keyPair = generator.generateKeyPair(),
            comment = comment
        )
    }

    private fun buildSshKeyData(
        algorithm: String,
        keySize: Int,
        publicKeyPrefix: String,
        privateKeyHeader: String,
        privateKeyLineLength: Int,
        keyPair: AsymmetricCipherKeyPair,
        comment: String
    ): SshKeyData {
        // Assert the generated key matches the intended algorithm.
        when (algorithm) {
            SshKeyData.ALGORITHM_RSA -> require(keyPair.public is RSAKeyParameters) {
                "Expected RSA public key, got ${keyPair.public::class.java.simpleName}"
            }
            SshKeyData.ALGORITHM_ED25519 -> require(keyPair.public is Ed25519PublicKeyParameters) {
                "Expected Ed25519 public key, got ${keyPair.public::class.java.simpleName}"
            }
        }

        val encodedPublic = OpenSSHPublicKeyUtil.encodePublicKey(keyPair.public)
        val encodedPrivate = OpenSSHPrivateKeyUtil.encodePrivateKey(keyPair.private)
        val trimmedComment = comment.trim()

        return SshKeyData(
            algorithm = algorithm,
            keySize = keySize,
            publicKeyOpenSsh = formatOpenSshPublicKey(publicKeyPrefix, encodedPublic, trimmedComment),
            privateKeyOpenSsh = formatPemPrivateKey(privateKeyHeader, encodedPrivate, privateKeyLineLength),
            fingerprintSha256 = fingerprintSha256(encodedPublic),
            comment = trimmedComment,
            format = SshKeyData.FORMAT_OPENSSH
        )
    }

    // endregion

    private fun rsaCertainty(keySize: Int): Int = when {
        keySize <= 2048 -> 112
        keySize <= 3072 -> 128
        else -> 152
    }

    // region Formatting ---------------------------------------------------------------

    private fun formatOpenSshPublicKey(
        prefix: String,
        encodedPublic: ByteArray,
        comment: String
    ): String {
        val base64 = encodeBase64(encodedPublic)
        return buildString {
            append(prefix)
            append(' ')
            append(base64)
            if (comment.isNotEmpty()) {
                append(' ')
                append(comment)
            }
        }
    }

    private fun formatPemPrivateKey(
        header: String,
        encodedPrivate: ByteArray,
        lineLength: Int
    ): String {
        val base64 = encodeBase64(encodedPrivate)
        return buildString {
            append("-----BEGIN ")
            append(header)
            append("-----")
            append('\n')
            base64
                .windowedSequence(lineLength, step = lineLength, partialWindows = true)
                .forEach { line ->
                    append(line)
                    append('\n')
                }
            append("-----END ")
            append(header)
            append("-----")
            append('\n')
        }
    }

    /**
     * `ssh-keygen -E sha256 -lf key.pub` 输出格式：SHA256:<base64(sha256(publicBlob))>
     * OpenSSH 的 fingerprint 不带 `=` padding，因此这里 trim。
     */
    private fun fingerprintSha256(publicKeyEncoded: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyEncoded)
        return "SHA256:" + encodeBase64(digest).trimEnd('=')
    }

    private fun encodeBase64(bytes: ByteArray): String =
        Base64.toBase64String(bytes)

    // endregion
}
