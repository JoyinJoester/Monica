package takagi.ru.monica.passkey

import android.util.Base64
import takagi.ru.monica.data.PasskeyEntry
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Provider
import java.security.Security
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.PSSParameterSpec

object PasskeyPrivateKeySupport {
    private const val PEM_BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----"
    private const val PEM_END_PRIVATE_KEY = "-----END PRIVATE KEY-----"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

    data class DecodedPrivateKey(
        val privateKey: PrivateKey,
        val pkcs8Bytes: ByteArray,
        val publicKeyAlgorithm: Int
    )

    fun decodeFlexiblePrivateKey(keyMaterial: String?): DecodedPrivateKey? {
        val normalized = keyMaterial?.trim().orEmpty()
        if (normalized.isBlank()) return null

        val pkcs8Bytes = extractPkcs8Bytes(normalized) ?: return null
        return decodePkcs8Bytes(pkcs8Bytes)
    }

    fun exportPem(keyMaterial: String?): String? {
        val decoded = decodeFlexiblePrivateKey(keyMaterial) ?: return null
        return pkcs8ToPem(decoded.pkcs8Bytes)
    }

    fun exportPkcs8Base64(keyMaterial: String?): String? {
        val decoded = decodeFlexiblePrivateKey(keyMaterial) ?: return null
        return Base64.encodeToString(decoded.pkcs8Bytes, Base64.NO_WRAP)
    }

    fun hasBitwardenCompatiblePrivateKey(keyMaterial: String?): Boolean {
        val normalized = keyMaterial?.trim().orEmpty()
        if (normalized.isBlank()) return false
        if (decodeFlexiblePrivateKey(normalized) != null) return true

        return runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            val entry = keyStore.getEntry(normalized, null) as? KeyStore.PrivateKeyEntry
            val encoded = entry?.privateKey?.encoded
            encoded != null && encoded.isNotEmpty()
        }.getOrDefault(false)
    }

    fun normalizeForBitwardenUpload(keyMaterial: String?): String? {
        val normalized = keyMaterial?.trim().orEmpty()
        if (normalized.isBlank()) return null
        exportPkcs8Base64(normalized)?.let { return it }

        return runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            val entry = keyStore.getEntry(normalized, null) as? KeyStore.PrivateKeyEntry
            val encoded = entry?.privateKey?.encoded
            encoded?.takeIf { it.isNotEmpty() }?.let {
                Base64.encodeToString(it, Base64.NO_WRAP)
            }
        }.getOrNull()
    }

    fun createSignature(
        privateKey: PrivateKey,
        publicKeyAlgorithm: Int
    ): Signature {
        val signature = when (publicKeyAlgorithm) {
            PasskeyEntry.ALGORITHM_ES256 -> Signature.getInstance("SHA256withECDSA")
            PasskeyEntry.ALGORITHM_RS256 -> Signature.getInstance("SHA256withRSA")
            PasskeyEntry.ALGORITHM_PS256 -> {
                runCatching { Signature.getInstance("SHA256withRSA/PSS") }.getOrElse {
                    Signature.getInstance("RSASSA-PSS").apply {
                        setParameter(
                            PSSParameterSpec(
                                "SHA-256",
                                "MGF1",
                                MGF1ParameterSpec.SHA256,
                                32,
                                1
                            )
                        )
                    }
                }
            }
            PasskeyEntry.ALGORITHM_EDDSA -> Signature.getInstance("Ed25519")
            else -> defaultSignatureFor(privateKey)
        }
        signature.initSign(privateKey)
        return signature
    }

    private fun extractPkcs8Bytes(keyMaterial: String): ByteArray? {
        val pemBody = keyMaterial
            .substringAfter(PEM_BEGIN_PRIVATE_KEY, missingDelimiterValue = keyMaterial)
            .substringBefore(PEM_END_PRIVATE_KEY, missingDelimiterValue = keyMaterial)
            .replace("\\s".toRegex(), "")

        return decodeBase64Compat(pemBody)
            ?: decodeBase64Compat(keyMaterial.replace("\\s".toRegex(), ""))
    }

    private fun decodePkcs8Bytes(pkcs8Bytes: ByteArray): DecodedPrivateKey? {
        val keySpec = PKCS8EncodedKeySpec(pkcs8Bytes)
        val candidates = listOf(
            "EC" to PasskeyEntry.ALGORITHM_ES256,
            "RSA" to PasskeyEntry.ALGORITHM_RS256,
            "Ed25519" to PasskeyEntry.ALGORITHM_EDDSA
        )

        candidates.forEach { (keyAlgorithm, coseAlgorithm) ->
            val privateKey = generatePrivateKey(keyAlgorithm, keySpec)
            if (privateKey != null) {
                return DecodedPrivateKey(
                    privateKey = privateKey,
                    pkcs8Bytes = pkcs8Bytes,
                    publicKeyAlgorithm = coseAlgorithm
                )
            }
        }

        return null
    }

    private fun generatePrivateKey(
        keyAlgorithm: String,
        keySpec: PKCS8EncodedKeySpec
    ): PrivateKey? {
        val defaultKey = runCatching {
            KeyFactory.getInstance(keyAlgorithm).generatePrivate(keySpec)
        }.getOrNull()
        if (defaultKey != null) return defaultKey

        val provider = getBundledBouncyCastleProvider() ?: return null
        return runCatching {
            KeyFactory.getInstance(keyAlgorithm, provider).generatePrivate(keySpec)
        }.getOrNull()
    }

    private fun getBundledBouncyCastleProvider(): Provider? {
        val existing = Security.getProvider("BC")
        if (existing?.javaClass?.name == BouncyCastleProvider::class.java.name) {
            return existing
        }

        val provider = BouncyCastleProvider()
        return runCatching {
            Security.addProvider(provider)
            provider
        }.getOrElse {
            provider
        }
    }

    private fun defaultSignatureFor(privateKey: PrivateKey): Signature {
        return when (privateKey.algorithm.uppercase()) {
            "RSA" -> Signature.getInstance("SHA256withRSA")
            "ED25519" -> Signature.getInstance("Ed25519")
            else -> Signature.getInstance("SHA256withECDSA")
        }
    }

    private fun decodeBase64Compat(value: String): ByteArray? {
        if (value.isBlank()) return null
        decodeWithJavaBase64(value)?.let { return it }

        val flags = listOf(
            Base64.NO_WRAP or Base64.URL_SAFE,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING,
            Base64.DEFAULT or Base64.URL_SAFE,
            Base64.NO_WRAP,
            Base64.DEFAULT
        )
        flags.forEach { base64Flags ->
            val decoded = runCatching { Base64.decode(value, base64Flags) }.getOrNull()
            if (decoded != null && decoded.isNotEmpty()) {
                return decoded
            }
        }
        return null
    }

    private fun decodeWithJavaBase64(value: String): ByteArray? {
        val compact = value.replace("\\s".toRegex(), "")
        if (compact.isBlank()) return null
        val padded = compact.padEnd(compact.length + (4 - compact.length % 4) % 4, '=')
        val looksUrlSafe = compact.any { it == '-' || it == '_' }

        val candidates = if (looksUrlSafe) {
            listOf(
                { java.util.Base64.getUrlDecoder().decode(padded) },
                { java.util.Base64.getDecoder().decode(padded) }
            )
        } else {
            listOf(
                { java.util.Base64.getDecoder().decode(padded) },
                { java.util.Base64.getUrlDecoder().decode(padded) }
            )
        }

        candidates.forEach { decode ->
            val decoded = runCatching { decode() }.getOrNull()
            if (decoded != null && decoded.isNotEmpty()) {
                return decoded
            }
        }
        return null
    }

    private fun pkcs8ToPem(pkcs8Bytes: ByteArray): String {
        val body = Base64.encodeToString(pkcs8Bytes, Base64.NO_WRAP)
            .chunked(64)
            .joinToString(separator = "\n")
        return buildString {
            appendLine(PEM_BEGIN_PRIVATE_KEY)
            appendLine(body)
            append(PEM_END_PRIVATE_KEY)
        }
    }
}
