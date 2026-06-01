package takagi.ru.monica.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class MdbxAttachmentCekPayloadTest {

    @Test
    fun fromLocalWrappedCek_storesPortableCekPayload() {
        val rawCekBase64 = cekBase64()

        val stored = MdbxAttachmentCekPayload.fromLocalWrappedCek(
            wrappedCek = "device-wrapped",
            unwrapToBase64 = { wrapped ->
                assertEquals("device-wrapped", wrapped)
                rawCekBase64
            }
        )

        assertTrue(MdbxAttachmentCekPayload.isPortable(stored))
        assertEquals("portable-attachment-cek-v1:$rawCekBase64", stored)
    }

    @Test
    fun toLocalWrappedCek_rewrapsPortablePayloadForCurrentDevice() {
        val rawCekBase64 = cekBase64()

        val local = MdbxAttachmentCekPayload.toLocalWrappedCek(
            storedValue = "portable-attachment-cek-v1:$rawCekBase64",
            wrapBase64 = { base64 ->
                assertEquals(rawCekBase64, base64)
                "current-device-wrapped"
            }
        )

        assertEquals("current-device-wrapped", local)
    }

    @Test
    fun toLocalWrappedCek_rewrapsLegacyRawCekBase64() {
        val rawCekBase64 = cekBase64()

        val local = MdbxAttachmentCekPayload.toLocalWrappedCek(
            storedValue = rawCekBase64,
            wrapBase64 = { base64 ->
                assertEquals(rawCekBase64, base64)
                "rewrapped-legacy"
            }
        )

        assertEquals("rewrapped-legacy", local)
    }

    @Test
    fun toLocalWrappedCek_keepsOpaqueLegacyWrappedValue() {
        val opaqueLegacyValue = "security-manager-wrapped-value"

        val local = MdbxAttachmentCekPayload.toLocalWrappedCek(
            storedValue = opaqueLegacyValue,
            wrapBase64 = { error("Opaque legacy values must not be wrapped again") }
        )

        assertFalse(MdbxAttachmentCekPayload.isPortable(opaqueLegacyValue))
        assertEquals(opaqueLegacyValue, local)
    }

    private fun cekBase64(): String =
        Base64.getEncoder().encodeToString(ByteArray(32) { index -> (index + 1).toByte() })
}
