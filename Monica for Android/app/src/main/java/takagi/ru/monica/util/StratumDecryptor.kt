package takagi.ru.monica.util

import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Decrypts Stratum / Authenticator Pro backup files.
 *
 * Format reference:
 * https://github.com/stratumauth/app/blob/master/doc/BACKUP_FORMAT.md
 */
class StratumDecryptor {
    enum class StratumFileType {
        MODERN_ENCRYPTED,
        LEGACY_ENCRYPTED,
        UNENCRYPTED,
        NOT_STRATUM
    }

    companion object {
        private val MODERN_HEADER = "AUTHENTICATORPRO".toByteArray(StandardCharsets.UTF_8)
        private val LEGACY_HEADER = "AuthenticatorPro".toByteArray(StandardCharsets.UTF_8)

        private const val MODERN_SALT_LENGTH = 16
        private const val MODERN_IV_LENGTH = 12
        private const val LEGACY_SALT_LENGTH = 20
        private const val LEGACY_IV_LENGTH = 16
        private const val KEY_LENGTH_BYTES = 32
    }

    fun detectFileType(data: ByteArray): StratumFileType {
        if (data.size >= MODERN_HEADER.size && data.copyOfRange(0, MODERN_HEADER.size).contentEquals(MODERN_HEADER)) {
            return StratumFileType.MODERN_ENCRYPTED
        }
        if (data.size >= LEGACY_HEADER.size && data.copyOfRange(0, LEGACY_HEADER.size).contentEquals(LEGACY_HEADER)) {
            return StratumFileType.LEGACY_ENCRYPTED
        }
        if (looksLikeUnencryptedStratumJson(data)) {
            return StratumFileType.UNENCRYPTED
        }
        return StratumFileType.NOT_STRATUM
    }

    fun requiresPassword(data: ByteArray): Boolean {
        return when (detectFileType(data)) {
            StratumFileType.MODERN_ENCRYPTED, StratumFileType.LEGACY_ENCRYPTED -> true
            StratumFileType.UNENCRYPTED, StratumFileType.NOT_STRATUM -> false
        }
    }

    @Throws(Exception::class)
    fun decrypt(data: ByteArray, password: String): String {
        return when (detectFileType(data)) {
            StratumFileType.MODERN_ENCRYPTED -> decryptModern(data, password)
            StratumFileType.LEGACY_ENCRYPTED -> decryptLegacy(data, password)
            StratumFileType.UNENCRYPTED -> data.toString(Charsets.UTF_8)
            StratumFileType.NOT_STRATUM -> throw IllegalArgumentException("File is not a valid Stratum backup")
        }
    }

    private fun looksLikeUnencryptedStratumJson(data: ByteArray): Boolean {
        return try {
            val content = data.toString(Charsets.UTF_8)
            val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(content).jsonObject
            root["Authenticators"]?.jsonArray != null
        } catch (_: Exception) {
            false
        }
    }

    @Throws(Exception::class)
    private fun decryptModern(data: ByteArray, password: String): String {
        val minLength = MODERN_HEADER.size + MODERN_SALT_LENGTH + MODERN_IV_LENGTH + 16
        if (data.size <= minLength) {
            throw IllegalArgumentException("Invalid modern encrypted Stratum file")
        }

        val offsetSalt = MODERN_HEADER.size
        val offsetIv = offsetSalt + MODERN_SALT_LENGTH
        val offsetPayload = offsetIv + MODERN_IV_LENGTH

        val salt = data.copyOfRange(offsetSalt, offsetIv)
        val iv = data.copyOfRange(offsetIv, offsetPayload)
        val payloadWithTag = data.copyOfRange(offsetPayload, data.size)

        val key = deriveArgon2Key(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val plaintext = cipher.doFinal(payloadWithTag)
        return plaintext.toString(Charsets.UTF_8)
    }

    @Throws(Exception::class)
    private fun decryptLegacy(data: ByteArray, password: String): String {
        val minLength = LEGACY_HEADER.size + LEGACY_SALT_LENGTH + LEGACY_IV_LENGTH + 1
        if (data.size <= minLength) {
            throw IllegalArgumentException("Invalid legacy encrypted Stratum file")
        }

        val offsetSalt = LEGACY_HEADER.size
        val offsetIv = offsetSalt + LEGACY_SALT_LENGTH
        val offsetPayload = offsetIv + LEGACY_IV_LENGTH

        val salt = data.copyOfRange(offsetSalt, offsetIv)
        val iv = data.copyOfRange(offsetIv, offsetPayload)
        val payload = data.copyOfRange(offsetPayload, data.size)

        val key = derivePbkdf2Sha1Key(password, salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), javax.crypto.spec.IvParameterSpec(iv))
        val plaintext = cipher.doFinal(payload)
        return plaintext.toString(Charsets.UTF_8)
    }

    private fun deriveArgon2Key(password: String, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withParallelism(4)
            .withIterations(3)
            .withMemoryAsKB(65536)
            .withSalt(salt)
            .build()

        val out = ByteArray(KEY_LENGTH_BYTES)
        Argon2BytesGenerator().apply {
            init(params)
            generateBytes(password.toByteArray(Charsets.UTF_8), out)
        }
        return out
    }

    private fun derivePbkdf2Sha1Key(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 64000, KEY_LENGTH_BYTES * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return factory.generateSecret(spec).encoded
    }
}
