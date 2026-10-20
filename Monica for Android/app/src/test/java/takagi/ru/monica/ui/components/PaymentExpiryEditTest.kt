package takagi.ru.monica.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentExpiryEditTest {
    @Test fun firstYearDigitAdvancesPastInsertedSlash() {
        val result = formatExpiryEdit(TextFieldValue("073", TextRange(3)))
        assertEquals("07/3", result.text)
        assertEquals(TextRange(4), result.selection)
    }

    @Test fun reversedSelectionRemainsReversedAcrossInsertedSlash() {
        val result = formatExpiryEdit(TextFieldValue("092030", TextRange(6, 2)))
        assertEquals(TextRange(7, 2), result.selection)
    }

    @Test fun cursorOnEitherSideOfExistingSlashStaysOnThatSide() {
        assertEquals(TextRange(2), formatExpiryEdit(TextFieldValue("09/30", TextRange(2))).selection)
        assertEquals(TextRange(3), formatExpiryEdit(TextFieldValue("09/30", TextRange(3))).selection)
    }

    @Test fun filteringMapsSelectionAndComposition() {
        val result = formatExpiryEdit(TextFieldValue("09 20", TextRange(5), TextRange(3, 5)))
        assertEquals("09/20", result.text)
        assertEquals(TextRange(5), result.selection)
        assertEquals(TextRange(2, 5), result.composition)
    }

    @Test fun discardedCompositionIsCleared() {
        val result = formatExpiryEdit(TextFieldValue("09x", TextRange(3), TextRange(2, 3)))
        assertNull(result.composition)
        assertEquals(TextRange(2), result.selection)
    }

    @Test fun legacyYearLimitClampsSelectionAndComposition() {
        val result = formatExpiryEdit(TextFieldValue("09/2030", TextRange(7), TextRange(5, 7)), 2)
        assertEquals("09/20", result.text)
        assertEquals(TextRange(5), result.selection)
        assertNull(result.composition)
    }

    @Test fun removingLastYearDigitAlsoRemovesSeparator() {
        val result = formatExpiryEdit(TextFieldValue("07/", TextRange(3)))
        assertEquals("07", result.text)
        assertEquals(TextRange(2), result.selection)
    }
}
