package takagi.ru.monica.util

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData

class BitwardenAuthenticatorCsvTest {
    private val header = "folder,favorite,type,name,login_uri,login_totp"
    private val uri = "otpauth://totp/Example:alice%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example"
    private fun read(csv: String) = BitwardenAuthenticatorCsv.read(csv.reader().buffered())
    private fun data(item: DataExportImportManager.ExportItem) = Json.decodeFromString<TotpData>(item.itemData)

    @Test fun androidSixColumnHeaderWithNineValuesImportsOnlyStandaloneAuthenticators() {
        val report = read("$header\n,,1,Example,,$uri,ignored,30,6\n,,1,Example,,${uri.replace("alice", "bob")},ignored,30,6")
        assertEquals(0, report.rejectedCount)
        assertEquals(listOf("TOTP", "TOTP"), report.items.map { it.itemType })
        assertEquals(listOf("Example", "Example"), report.items.map { it.title })
        assertEquals(listOf("alice@example.com", "bob@example.com"), report.items.map { data(it).accountName })
        assertTrue(report.items.all { data(it).boundPasswordId == null })
    }

    @Test fun uriParametersOverrideRedundantTailAndPreserveHotpCounter() {
        val hotp = uri.replace("/totp/", "/hotp/") + "&algorithm=SHA512&digits=8&period=60&counter=4294967296"
        val data = data(read("$header\n,,1,Example,,$hotp,ignored,30,6").items.single())
        assertEquals(OtpType.HOTP, data.otpType)
        assertEquals(4294967296L, data.counter)
        assertEquals("SHA512", data.algorithm)
        assertEquals(8, data.digits)
        assertEquals(60, data.period)
    }

    @Test fun reorderedHeadersBomQuotedCommaQuoteAndMultilineNotesArePreserved() {
        val report = read("\uFEFFlogin_totp,name,notes,favorite,type\r\n\"$uri\",\"Other, \"\"name\"\"\",\"line 1\r\nline 2\",true,login\r\n")
        val item = report.items.single()
        assertEquals("Example", item.title)
        assertEquals("line 1\r\nline 2", item.notes)
        assertTrue(item.isFavorite)
    }

    @Test fun sixColumnRowsAndTrailingBlankLinesAreAccepted() {
        assertEquals(1, read("$header\r\n,,1,Example,,$uri\r\n\r\n").items.size)
    }

    @Test fun absentUriIssuerUsesExportedNameWithoutLosingAccount() {
        val plain = "otpauth://totp/alice%2Bwork%40example.com?secret=JBSWY3DPEHPK3PXP"
        val item = read("$header\n,,1,Example,,$plain").items.single()
        assertEquals("Example", item.title)
        assertEquals("alice+work@example.com", data(item).accountName)
    }

    @Test fun invalidRecordsAreCountedWhileValidRecordsRemainImportable() {
        val invalid = listOf("", "https://example.invalid/", uri.replace("JBSWY3DPEHPK3PXP", "!bad!"),
            "$uri&digits=oops", "$uri&period=0", "$uri&algorithm=MD5", "$uri&counter=-1",
            uri.replace("/totp/", "/hotp/"), "$uri&secret=GEZDGNBVGY3TQOJQ")
        val rows = invalid.map { ",,1,Example,,$it" } + ",,1,Example,,$uri"
        val report = read(header + "\n" + rows.joinToString("\n"))
        assertEquals(invalid.size, report.rejectedCount)
        assertEquals(1, report.items.size)
    }

    @Test fun mismatchedColumnsAndNonLoginRecordsAreRejected() {
        val report = read("$header\n,,1,Example,,$uri,unexpected\n,,2,Example,,$uri")
        assertEquals(2, report.rejectedCount)
        assertTrue(report.items.isEmpty())
    }

    @Test fun passwordManagerCsvIsRejectedBySeparateImporter() {
        assertThrows(IllegalArgumentException::class.java) {
            read("folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\n,,login,Example,,,,,alice,password,$uri")
        }
    }

    @Test fun missingOrDuplicateHeadersAreRejected() {
        for (invalid in listOf("", "name,url,username,password", "name,login_totp,login_totp")) {
            assertThrows(IllegalArgumentException::class.java) { read(invalid) }
        }
    }

    @Test fun malformedQuotedFileFailsWithoutExposingSourceData() {
        val error = assertThrows(IllegalArgumentException::class.java) { read("$header\n,,1,Example,,\"$uri") }
        assertFalse(error.message.orEmpty().contains("JBSWY3DPEHPK3PXP"))
    }
}
