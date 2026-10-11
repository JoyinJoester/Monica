package takagi.ru.monica.lansync

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import takagi.ru.monica.credentialexchange.CxfPasskeyMaterial
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DevicePasskeyShapeTest {
    @Test fun spki_and_cose_encodings_of_one_key_both_validate() {
        for (algorithm in listOf(-7, -257, -37, -8)) {
            val pair = keyPair(algorithm)
            val material = requireNotNull(CxfPasskeyMaterial.decode(pair.private.encoded, algorithm))
            // Control: the same private key already passes with its COSE public key.
            DevicePasskeyShape.verify(data(pair, algorithm, material.cosePublicKey))
            DevicePasskeyShape.verify(data(pair, algorithm, base64(pair.public.encoded)))
        }
    }

    @Test fun mismatched_public_keys_still_fail_for_spki_and_cose() {
        for (algorithm in listOf(-7, -257, -37, -8)) {
            val pair = keyPair(algorithm)
            val other = keyPair(algorithm)
            val publicKeys = listOf(base64(other.public.encoded),
                requireNotNull(CxfPasskeyMaterial.decode(other.private.encoded, algorithm)).cosePublicKey)
            for (publicKey in publicKeys) {
                assertInvalidMaterial(data(pair, algorithm, publicKey))
            }
        }
    }

    @Test fun algorithm_mismatch_and_trailing_spki_bytes_still_fail() {
        val pair = keyPair(-7)
        assertInvalidMaterial(data(pair, -8, base64(pair.public.encoded)))
        assertInvalidMaterial(data(pair, -7, base64(pair.public.encoded + byteArrayOf(0))))
    }

    private fun assertInvalidMaterial(value: JSONObject) {
        val error = assertThrows(LanSyncException::class.java) { DevicePasskeyShape.verify(value) }
        assertTrue(error.message.orEmpty().contains("invalid_passkey_material"))
    }

    private fun data(pair: KeyPair, algorithm: Int, publicKey: String): JSONObject =
        DeviceVaultShape.normalize(JSONObject()
            .put("credential_id", "synthetic-credential").put("rp_id", "example.test")
            .put("user_id", "Zml4dHVyZQ==").put("public_key_algorithm", algorithm)
            .put("public_key", publicKey).put("private_key_alias", base64(pair.private.encoded))
            .put("key_status", "portable"), "Synthetic passkey", "", "passkey")

    private fun keyPair(algorithm: Int): KeyPair = when (algorithm) {
        -7 -> KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        -8 -> KeyPairGenerator.getInstance("Ed25519")
        else -> KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
    }.generateKeyPair()

    private fun base64(value: ByteArray): String = Base64.getEncoder().encodeToString(value)
}
