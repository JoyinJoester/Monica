package takagi.ru.monica.util

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Test

class DataExportImportManagerCsvFormatTest {

    private val manager = DataExportImportManager(ContextWrapper(null))

    @Test
    fun chromeHeader_prefersChromeFormat() {
        val format = detectCsvFormat("name,url,username,password,note")

        assertEquals(DataExportImportManager.CsvFormat.CHROME_PASSWORD, format)
    }

    @Test
    fun passwordKeyboardHeader_stillDetectedAsPasswordKeyboard() {
        val format = detectCsvFormat("username,password,title,remarks,url,tag,custom")

        assertEquals(DataExportImportManager.CsvFormat.PASSWORD_KEYBOARD, format)
    }

    private fun detectCsvFormat(header: String): DataExportImportManager.CsvFormat {
        val method = DataExportImportManager::class.java.getDeclaredMethod(
            "detectCsvFormat",
            String::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(manager, header) as DataExportImportManager.CsvFormat
    }
}
