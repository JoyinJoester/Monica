package takagi.ru.monica.credentialexchange

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters
import org.bouncycastle.crypto.util.PrivateKeyFactory
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.Base64

/** Validates PKCS#8 and derives the original public key; never generates a replacement key. */
object CxfPasskeyMaterial {
    data class Material(val algorithm: Int, val cosePublicKey: String)

    fun decode(pkcs8: ByteArray, requestedAlgorithm: Int? = null): Material? = runCatching {
        val info = PrivateKeyInfo.getInstance(pkcs8)
        val key = PrivateKeyFactory.createKey(info)
        val fields: Map<Int, Any>
        val algorithm: Int
        when (key) {
            is ECPrivateKeyParameters -> {
                val p256 = SECNamedCurves.getByName("secp256r1")
                require(key.parameters.n == p256.n && key.parameters.curve == p256.curve &&
                    key.parameters.g == p256.g && key.d > BigInteger.ZERO && key.d < p256.n)
                val point = key.parameters.g.multiply(key.d).normalize()
                algorithm = -7
                fields = linkedMapOf(1 to 2, 3 to algorithm, -1 to 1,
                    -2 to point.affineXCoord.encoded, -3 to point.affineYCoord.encoded)
            }
            is RSAPrivateCrtKeyParameters -> {
                // A generic RSA key does not identify PSS parameters. Do not guess them.
                require(info.privateKeyAlgorithm.algorithm.id == "1.2.840.113549.1.1.1")
                require(key.modulus.bitLength() in 2048..8192 && key.publicExponent > BigInteger.ONE)
                require(key.p > BigInteger.ONE && key.q > BigInteger.ONE && key.p != key.q && key.p * key.q == key.modulus)
                val pMinusOne = key.p - BigInteger.ONE
                val qMinusOne = key.q - BigInteger.ONE
                require(key.dp == key.exponent.mod(pMinusOne) && key.dq == key.exponent.mod(qMinusOne))
                require(key.qInv.multiply(key.q).mod(key.p) == BigInteger.ONE)
                val lambda = pMinusOne.divide(pMinusOne.gcd(qMinusOne)).multiply(qMinusOne)
                require(key.exponent.multiply(key.publicExponent).mod(lambda) == BigInteger.ONE)
                require(requestedAlgorithm == null || requestedAlgorithm in listOf(-257, -37))
                algorithm = requestedAlgorithm ?: -257
                fields = linkedMapOf(1 to 3, 3 to algorithm,
                    -1 to unsigned(key.modulus), -2 to unsigned(key.publicExponent))
            }
            is Ed25519PrivateKeyParameters -> {
                algorithm = -8
                fields = linkedMapOf(1 to 1, 3 to algorithm, -1 to 6, -2 to key.generatePublicKey().encoded)
            }
            else -> return null
        }
        Material(algorithm, Base64.getEncoder().encodeToString(encodeCose(fields)))
    }.getOrNull()

    private fun unsigned(value: BigInteger): ByteArray = value.toByteArray().let {
        if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
    }

    private fun encodeCose(fields: Map<Int, Any>): ByteArray {
        val output = ByteArrayOutputStream()
        fun head(major: Int, value: Int) {
            when {
                value < 24 -> output.write(major * 32 + value)
                value <= 255 -> { output.write(major * 32 + 24); output.write(value) }
                else -> { output.write(major * 32 + 25); output.write(value shr 8); output.write(value and 255) }
            }
        }
        fun integer(value: Int) = if (value >= 0) head(0, value) else head(1, -1 - value)
        head(5, fields.size)
        fields.forEach { (key, value) ->
            integer(key)
            when (value) {
                is Int -> integer(value)
                is ByteArray -> { head(2, value.size); output.write(value) }
                else -> error("Unsupported COSE value")
            }
        }
        return output.toByteArray()
    }
}
