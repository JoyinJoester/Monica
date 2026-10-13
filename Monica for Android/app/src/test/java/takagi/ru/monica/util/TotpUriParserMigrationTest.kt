package takagi.ru.monica.util

import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.model.OtpType

class TotpUriParserMigrationTest {

    @Test
    fun `supplied Google Authenticator migration QR decodes every authenticator`() {
        val uri = "otpauth-migration://offline?data=CjYKELnGcaRtOM4zjPc4xnGcaUASB%2Ba1i%2BivlTEgASgBMAJCE2YxMzQ3MTE3ODYxODI5OTg2NDUKNwoRvUZ7pGq7UZSQ96DaNt3DaM4SB%2Ba1i%2BivlTIgASgBMAJCEzZkMWI2YTE3ODYxODMwMzA4ODcQAhgBIAA%3D"

        val result = TotpUriParser.parseScannedContent(uri)

        assertTrue(result is TotpScanParseResult.Multiple)
        val items = (result as TotpScanParseResult.Multiple).items
        assertEquals(2, items.size)
        assertMigrationItem(
            item = items[0],
            expectedLabel = "测试1",
            expectedSecret = "XHDHDJDNHDHDHDHXHDDHDHDJIA"
        )
        assertMigrationItem(
            item = items[1],
            expectedLabel = "测试2",
            expectedSecret = "XVDHXJDKXNIZJEHXUDNDNXODNDHA"
        )
    }

    @Test
    fun `supported HOTP parameters preserve algorithm digits and counter`() {
        val uri = migrationUri(
            otpMessage(
                secret = byteArrayOf(1, 2, 3, 4, 5),
                accountName = "counter@example.com",
                issuer = "Example",
                algorithm = 2,
                digits = 2,
                type = 1,
                counter = 42L
            )
        )

        val result = TotpUriParser.parseScannedContent(uri)

        assertTrue(result is TotpScanParseResult.Single)
        val item = (result as TotpScanParseResult.Single).item
        assertEquals("Example:counter@example.com", item.label)
        assertEquals("Example", item.suggestedTitle)
        assertEquals("counter@example.com", item.totpData.accountName)
        assertEquals(OtpType.HOTP, item.totpData.otpType)
        assertEquals("SHA256", item.totpData.algorithm)
        assertEquals(8, item.totpData.digits)
        assertEquals(42L, item.totpData.counter)
    }

    @Test
    fun `one unsupported entry rejects the complete migration payload`() {
        val valid = otpMessage(
            secret = byteArrayOf(1, 2, 3),
            accountName = "valid@example.com"
        )
        val unsupported = otpMessage(
            secret = byteArrayOf(4, 5, 6),
            accountName = "unsupported@example.com",
            algorithm = 4
        )

        val result = TotpUriParser.parseScannedContent(migrationUri(valid, unsupported))

        assertEquals(
            TotpScanParseResult.MigrationFailure(MigrationFailureReason.UNSUPPORTED_ALGORITHM),
            result
        )
    }

    @Test
    fun `multi QR exports are rejected instead of importing one partial segment`() {
        val result = TotpUriParser.parseScannedContent(
            migrationUri(
                otpMessage(
                    secret = byteArrayOf(1, 2, 3),
                    accountName = "first@example.com"
                ),
                batchSize = 2,
                batchIndex = 0
            )
        )

        assertEquals(
            TotpScanParseResult.MigrationFailure(MigrationFailureReason.MULTI_QR_BATCH),
            result
        )
    }

    @Test
    fun `malformed migration data has a dedicated failure result`() {
        val result = TotpUriParser.parseScannedContent(
            "otpauth-migration://offline?data=not-valid-base64***"
        )

        assertEquals(
            TotpScanParseResult.MigrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD),
            result
        )
    }

    private fun assertMigrationItem(
        item: TotpParseResult,
        expectedLabel: String,
        expectedSecret: String
    ) {
        assertEquals(expectedLabel, item.label)
        assertEquals("", item.totpData.issuer)
        assertEquals(expectedLabel, item.totpData.accountName)
        assertEquals(expectedSecret, item.totpData.secret)
        assertEquals(OtpType.TOTP, item.totpData.otpType)
        assertEquals("SHA1", item.totpData.algorithm)
        assertEquals(6, item.totpData.digits)
        assertEquals(30, item.totpData.period)
    }

    private fun migrationUri(
        vararg otpMessages: ByteArray,
        version: Int = 2,
        batchSize: Int = 1,
        batchIndex: Int = 0
    ): String {
        val payload = ByteArrayOutputStream().apply {
            otpMessages.forEach { writeLengthDelimited(fieldNumber = 1, value = it) }
            writeVarIntField(fieldNumber = 2, value = version.toLong())
            writeVarIntField(fieldNumber = 3, value = batchSize.toLong())
            writeVarIntField(fieldNumber = 4, value = batchIndex.toLong())
        }.toByteArray()
        val encoded = URLEncoder.encode(
            Base64.getEncoder().encodeToString(payload),
            StandardCharsets.UTF_8.name()
        )
        return "otpauth-migration://offline?data=$encoded"
    }

    private fun otpMessage(
        secret: ByteArray,
        accountName: String,
        issuer: String = "",
        algorithm: Int = 1,
        digits: Int = 1,
        type: Int = 2,
        counter: Long = 0L
    ): ByteArray {
        return ByteArrayOutputStream().apply {
            writeLengthDelimited(fieldNumber = 1, value = secret)
            writeLengthDelimited(fieldNumber = 2, value = accountName.toByteArray(StandardCharsets.UTF_8))
            if (issuer.isNotBlank()) {
                writeLengthDelimited(fieldNumber = 3, value = issuer.toByteArray(StandardCharsets.UTF_8))
            }
            writeVarIntField(fieldNumber = 4, value = algorithm.toLong())
            writeVarIntField(fieldNumber = 5, value = digits.toLong())
            writeVarIntField(fieldNumber = 6, value = type.toLong())
            if (counter != 0L) {
                writeVarIntField(fieldNumber = 7, value = counter)
            }
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeLengthDelimited(fieldNumber: Int, value: ByteArray) {
        writeVarInt(((fieldNumber shl 3) or 2).toLong())
        writeVarInt(value.size.toLong())
        write(value)
    }

    private fun ByteArrayOutputStream.writeVarIntField(fieldNumber: Int, value: Long) {
        writeVarInt((fieldNumber shl 3).toLong())
        writeVarInt(value)
    }

    private fun ByteArrayOutputStream.writeVarInt(value: Long) {
        var remaining = value
        while (true) {
            if (remaining and -128L == 0L) {
                write(remaining.toInt())
                return
            }
            write(((remaining and 0x7fL) or 0x80L).toInt())
            remaining = remaining ushr 7
        }
    }
}
