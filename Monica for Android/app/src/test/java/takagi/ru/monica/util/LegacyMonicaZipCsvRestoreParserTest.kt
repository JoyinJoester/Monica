package takagi.ru.monica.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.utils.LegacyMonicaSecureCsvRole
import takagi.ru.monica.utils.LegacyMonicaZipCsvRestoreParser
import java.io.File

class LegacyMonicaZipCsvRestoreParserTest {

    @Test
    fun cardsDocsCsvUsesDeterministicCardAndDocumentRestore() {
        val file = File.createTempFile("monica_cards_docs", ".csv")
        file.writeText(
            """
            ID,Type,Title,Data,Notes,IsFavorite,ImagePaths,CreatedAt,UpdatedAt,CategoryId
            1,NOTE,招商银行卡,"{""number"":""6222"",""expMonth"":""12"",""expYear"":""2030"",""code"":""123""}",,false,,1,1,
            2,NOTE,身份证,"{""issueDate"":""2020-01-01"",""issuingAuthority"":""公安局"",""firstName"":""Joy"",""lastName"":""Lin""}",,false,,1,1,
            """.trimIndent()
        )

        val result = LegacyMonicaZipCsvRestoreParser.parseSecureItems(
            file = file,
            role = LegacyMonicaSecureCsvRole.CARDS_DOCS_ONLY
        )

        assertEquals(listOf("BANK_CARD", "DOCUMENT"), result.items.map { it.itemType })
        assertTrue(result.warnings.isEmpty())
        file.delete()
    }

    @Test
    fun notesCsvForcesItemsIntoNoteDomain() {
        val file = File.createTempFile("monica_notes", ".csv")
        file.writeText(
            """
            ID,Type,Title,Data,Notes,IsFavorite,ImagePaths,CreatedAt,UpdatedAt,CategoryId
            1,BANK_CARD,备忘,"{""content"":""hello""}",,false,,1,1,
            """.trimIndent()
        )

        val result = LegacyMonicaZipCsvRestoreParser.parseSecureItems(
            file = file,
            role = LegacyMonicaSecureCsvRole.NOTES_ONLY
        )

        assertEquals(1, result.items.size)
        assertEquals("NOTE", result.items.first().itemType)
        file.delete()
    }
}
