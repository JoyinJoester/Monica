package takagi.ru.monica.util

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.utils.KeePassContainerFormat
import takagi.ru.monica.utils.KeePassFormatInspector

class KeePassFormatInspectorTest {

    @Test
    fun detect_kdbxSignature() {
        val bytes = byteArrayOf(
            0x03, 0xD9.toByte(), 0xA2.toByte(), 0x9A.toByte(),
            0x67, 0xFB.toByte(), 0x4B, 0xB5.toByte(), 0x00
        )
        val detected = KeePassFormatInspector.detect(bytes, "sample.kdbx")
        assertEquals(KeePassContainerFormat.KDBX, detected)
    }

    @Test
    fun detect_legacyKdbSignature() {
        val bytes = byteArrayOf(
            0x03, 0xD9.toByte(), 0xA2.toByte(), 0x9A.toByte(),
            0x65, 0xFB.toByte(), 0x4B, 0xB5.toByte(), 0x00
        )
        val detected = KeePassFormatInspector.detect(bytes, "sample.kdb")
        assertEquals(KeePassContainerFormat.KDB_LEGACY, detected)
    }

    @Test
    fun detect_legacyByExtensionFallback() {
        val bytes = byteArrayOf(0x00, 0x01, 0x02)
        val detected = KeePassFormatInspector.detect(bytes, "unknown.kdb")
        assertEquals(KeePassContainerFormat.KDB_LEGACY, detected)
    }
}

