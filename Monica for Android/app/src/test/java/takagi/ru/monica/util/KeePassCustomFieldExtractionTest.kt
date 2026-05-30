package takagi.ru.monica.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.utils.KeePassRawStringField
import takagi.ru.monica.utils.extractKeePassCustomFieldsForPasswordEntry

class KeePassCustomFieldExtractionTest {

    @Test
    fun keepsNonStandardKeePassStringFieldsForDetailDisplay() {
        val fields = extractKeePassCustomFieldsForPasswordEntry(
            listOf(
                KeePassRawStringField("Title", "GitHub", isProtected = false),
                KeePassRawStringField("UserName", "monica", isProtected = false),
                KeePassRawStringField("Password", "secret", isProtected = true),
                KeePassRawStringField("Security question", "First pet?", isProtected = false),
                KeePassRawStringField("Recovery PIN", "123456", isProtected = true),
                KeePassRawStringField("MonicaLocalId", "42", isProtected = false),
                KeePassRawStringField("_etm_template", "1", isProtected = false),
                KeePassRawStringField("Empty custom", "", isProtected = false)
            )
        )

        assertEquals(2, fields.size)
        assertEquals("Security question", fields[0].title)
        assertEquals("First pet?", fields[0].value)
        assertFalse(fields[0].isProtected)
        assertEquals(0, fields[0].sortOrder)
        assertEquals("Recovery PIN", fields[1].title)
        assertEquals("123456", fields[1].value)
        assertTrue(fields[1].isProtected)
        assertEquals(1, fields[1].sortOrder)
    }
}
