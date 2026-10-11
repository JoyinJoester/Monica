package takagi.ru.monica.ui.scanner

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.qrcode.QRCodeWriter
import takagi.ru.monica.lansync.LanPairingInvitation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 单测（zxing core 无 Android 依赖）。
 * 重点锁死反色二维码（暗底亮码）识别——ML Kit 隐性支持、
 * 旧 zxing 方案以"正反混合扫描"覆盖、而替换初期曾遗漏的能力。
 */
class ZxingBarcodeDecoderTest {

    @Test fun `nearby multi-address qr is decoded and accepted`() {
        val link = "monica-lan:2:10.30.250.111,192.168.1.5:53318:" + "A".repeat(43)
        val size = 300
        val matrix = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 4))
        val luminance = ByteArray(size * size) { index -> if (matrix[index % size, index / size]) 0 else 255.toByte() }
        val source = PlanarYUVLuminanceSource(luminance, size, size, 0, 0, size, size, false)
        val decoded = ZxingBarcodeDecoder(listOf(BarcodeFormat.QR_CODE)).decodeWithFallback(source).single()
        assertEquals(link, decoded)
        assertEquals(listOf("10.30.250.111", "192.168.1.5"), LanPairingInvitation.parse(decoded)!!.addresses)
    }

    private val payload = "otpauth://totp/Monica:scan-test?secret=53EFVQDZNZNA6HACH4W7OVUEP45OX64H&issuer=Monica"
    private val decoder = ZxingBarcodeDecoder(listOf(BarcodeFormat.QR_CODE))

    private fun qrLuminanceSource(inverted: Boolean): PlanarYUVLuminanceSource {
        val size = 420
        val matrix = QRCodeWriter().encode(
            payload, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 4)
        )
        val luminance = ByteArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                // 正色码：暗模块 = 低亮度；反色码整体翻转（暗底亮码）
                val dark = matrix.get(x, y) != inverted
                luminance[y * size + x] = (if (dark) 0 else 255).toByte()
            }
        }
        return PlanarYUVLuminanceSource(luminance, size, size, 0, 0, size, size, false)
    }

    @Test
    fun `normal contrast qr decodes`() {
        assertEquals(listOf(payload), decoder.decodeWithFallback(qrLuminanceSource(inverted = false)))
    }

    @Test
    fun `inverted dark-theme qr decodes`() {
        assertEquals(listOf(payload), decoder.decodeWithFallback(qrLuminanceSource(inverted = true)))
    }

    @Test
    fun `blank frame misses without error`() {
        val size = 420
        val blank = ByteArray(size * size) { 255.toByte() }
        val source = PlanarYUVLuminanceSource(blank, size, size, 0, 0, size, size, false)
        assertTrue(decoder.decodeWithFallback(source).isEmpty())
    }

    @Test
    fun `counter clockwise rotation maps pixels correctly`() {
        // 2x3 源矩阵 [1 2 / 3 4 / 5 6] 逆时针旋转 90° 应得 3x2 的 [2 4 6 / 1 3 5]
        val source = PlanarYUVLuminanceSource(
            byteArrayOf(1, 2, 3, 4, 5, 6), 2, 3, 0, 0, 2, 3, false
        )
        val rotated = decoder.rotateSourceCounterClockwise(source)
        assertEquals(3, rotated.width)
        assertEquals(2, rotated.height)
        assertEquals(listOf<Byte>(2, 4, 6, 1, 3, 5), rotated.matrix.toList())
    }
}
