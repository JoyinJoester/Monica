package takagi.ru.monica.repository

import android.util.Base64
import org.json.JSONObject
import takagi.ru.monica.data.MdbxTigaMode
import takagi.ru.monica.data.MdbxUnlockMethod
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class MdbxVaultCredential(
    val unlockMethod: MdbxUnlockMethod,
    val password: String? = null,
    val keyFileBytes: ByteArray? = null,
    val keyFileName: String? = null,
    val keyFileFingerprint: String? = keyFileBytes?.let(MdbxVaultCrypto::fingerprint)
) {
    fun requiresPassword(): Boolean =
        unlockMethod == MdbxUnlockMethod.MASTER_PASSWORD ||
            unlockMethod == MdbxUnlockMethod.MASTER_PASSWORD_AND_KEY_FILE

    fun requiresKeyFile(): Boolean =
        unlockMethod == MdbxUnlockMethod.KEY_FILE ||
            unlockMethod == MdbxUnlockMethod.MASTER_PASSWORD_AND_KEY_FILE
}

data class MdbxVaultKeyMaterial(
    val salt: ByteArray,
    val verifier: ByteArray,
    val wrappedEpochKey: ByteArray,
    val kdfProfile: String,
    val epochKey: ByteArray
)

object MdbxVaultCrypto {
    private const val KEY_FILE_MAGIC = "MONICA-MDBX-KEY-FILE-V1"
    private const val PBKDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val VERIFIER_LABEL = "Monica Database eXtended credential verifier v1"
    private const val FIELD_PREFIX = "mdbx:v1:"

    fun generateKeyFileBytes(): ByteArray {
        val random = ByteArray(64)
        SecureRandom().nextBytes(random)
        return "$KEY_FILE_MAGIC\n".toByteArray(Charsets.UTF_8) + random
    }

    fun fingerprint(bytes: ByteArray): String =
        sha256(bytes).joinToString("") { "%02x".format(it) }

    fun buildKeyMaterial(
        vaultId: String,
        credential: MdbxVaultCredential,
        tigaMode: MdbxTigaMode
    ): MdbxVaultKeyMaterial {
        validateCredentialShape(credential)
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val credentialKey = deriveCredentialKey(credential, salt, iterationsFor(tigaMode))
        val epochKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return MdbxVaultKeyMaterial(
            salt = salt,
            verifier = verifier(credentialKey, vaultId),
            wrappedEpochKey = wrapEpochKey(credentialKey, epochKey),
            kdfProfile = "pbkdf2-sha256:${iterationsFor(tigaMode)}",
            epochKey = epochKey
        )
    }

    fun verifyCredential(
        vaultId: String,
        credential: MdbxVaultCredential,
        salt: ByteArray,
        expectedVerifier: ByteArray,
        kdfProfile: String
    ): Boolean {
        validateCredentialShape(credential)
        val key = deriveCredentialKey(credential, salt, iterationsFrom(kdfProfile))
        return MessageDigest.isEqual(verifier(key, vaultId), expectedVerifier)
    }

    fun unwrapEpochKey(
        credential: MdbxVaultCredential,
        salt: ByteArray,
        wrappedEpochKey: ByteArray,
        kdfProfile: String
    ): ByteArray {
        validateCredentialShape(credential)
        val key = deriveCredentialKey(credential, salt, iterationsFrom(kdfProfile))
        return unwrapEpochKeyWithCredentialKey(key, wrappedEpochKey)
    }

    fun unlockEpochKey(
        vaultId: String,
        credential: MdbxVaultCredential,
        salt: ByteArray,
        expectedVerifier: ByteArray,
        wrappedEpochKey: ByteArray,
        kdfProfile: String
    ): ByteArray? {
        validateCredentialShape(credential)
        val key = deriveCredentialKey(credential, salt, iterationsFrom(kdfProfile))
        if (!MessageDigest.isEqual(verifier(key, vaultId), expectedVerifier)) return null
        return unwrapEpochKeyWithCredentialKey(key, wrappedEpochKey)
    }

    private fun unwrapEpochKeyWithCredentialKey(
        credentialKey: ByteArray,
        wrappedEpochKey: ByteArray
    ): ByteArray {
        val json = JSONObject(wrappedEpochKey.toString(Charsets.UTF_8))
        val nonce = Base64.decode(json.getString("nonce"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(json.getString("ct"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(credentialKey, "AES"), GCMParameterSpec(128, nonce))
        return cipher.doFinal(ciphertext)
    }

    fun encryptText(epochKey: ByteArray, value: String): ByteArray {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(epochKey, "AES"), GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val encoded = JSONObject()
            .put("n", Base64.encodeToString(nonce, Base64.NO_WRAP))
            .put("c", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .toString()
        return "$FIELD_PREFIX$encoded".toByteArray(Charsets.UTF_8)
    }

    fun decryptText(epochKey: ByteArray?, value: ByteArray): String {
        val raw = value.toString(Charsets.UTF_8)
        if (!raw.startsWith(FIELD_PREFIX)) return raw
        if (epochKey == null) {
            throw IllegalStateException("MDBX encrypted field requires an unlocked epoch key")
        }
        val json = JSONObject(raw.removePrefix(FIELD_PREFIX))
        val nonce = Base64.decode(json.getString("n"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(json.getString("c"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(epochKey, "AES"), GCMParameterSpec(128, nonce))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    fun isEncryptedText(value: ByteArray): Boolean =
        value.toString(Charsets.UTF_8).startsWith(FIELD_PREFIX)

    private fun validateCredentialShape(credential: MdbxVaultCredential) {
        if (credential.requiresPassword() && credential.password.isNullOrEmpty()) {
            throw IllegalArgumentException("MDBX master password is required")
        }
        if (credential.requiresKeyFile() && credential.keyFileBytes == null) {
            throw IllegalArgumentException("MDBX key file is required")
        }
    }

    private fun deriveCredentialKey(
        credential: MdbxVaultCredential,
        salt: ByteArray,
        iterations: Int
    ): ByteArray {
        val passwordBytes = credential.password.orEmpty().toByteArray(Charsets.UTF_8)
        val keyFileHash = credential.keyFileBytes?.let(::sha256) ?: ByteArray(32)
        val materialHash = sha256(
            credential.unlockMethod.storedValue.toByteArray(Charsets.UTF_8) +
                byteArrayOf(0) +
                passwordBytes +
                byteArrayOf(0) +
                keyFileHash
        )
        val material = Base64.encodeToString(materialHash, Base64.NO_WRAP).toCharArray()
        return try {
            SecretKeyFactory.getInstance(PBKDF_ALGORITHM)
                .generateSecret(PBEKeySpec(material, salt, iterations, 256))
                .encoded
        } finally {
            material.fill('\u0000')
        }
    }

    private fun verifier(credentialKey: ByteArray, vaultId: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(credentialKey, "HmacSHA256"))
        return mac.doFinal("$VERIFIER_LABEL:$vaultId".toByteArray(Charsets.UTF_8))
    }

    private fun wrapEpochKey(credentialKey: ByteArray, epochKey: ByteArray): ByteArray {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(credentialKey, "AES"), GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(epochKey)
        return JSONObject()
            .put("v", 1)
            .put("alg", "AES-256-GCM")
            .put("nonce", Base64.encodeToString(nonce, Base64.NO_WRAP))
            .put("ct", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    private fun iterationsFor(tigaMode: MdbxTigaMode): Int = when (tigaMode) {
        MdbxTigaMode.POWER -> 360_000
        MdbxTigaMode.MULTI -> 210_000
        MdbxTigaMode.SKY -> 90_000
    }

    private fun iterationsFrom(profile: String): Int =
        profile.substringAfter("pbkdf2-sha256:", "")
            .toIntOrNull()
            ?.coerceAtLeast(50_000)
            ?: iterationsFor(MdbxTigaMode.MULTI)

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
