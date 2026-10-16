package takagi.ru.monica.autofill_ng

import android.app.assist.AssistStructure
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 34], application = android.app.Application::class)
class AutofillTypedValuesTest {
    @Test fun singleLineStreetKeepsSeparatorsAndTextareaKeepsLineBreaks() {
        val node = ReflectionHelpers.newInstance(AssistStructure.ViewNode::class.java)
        ReflectionHelpers.setField(node, "mAutofillType", View.AUTOFILL_TYPE_TEXT)
        for ((tag, type) in listOf("input" to "text", "textarea" to "", "input" to "textarea")) {
            ReflectionHelpers.setField(node, "mHtmlInfo", object : android.view.ViewStructure.HtmlInfo() {
                override fun getTag() = tag
                override fun getAttributes() = mutableListOf(android.util.Pair("type", type))
            })
            assertEquals(if (tag == "input" && type != "textarea") "Street, Suite" else "Street\nSuite",
                AutofillTypedValues.resolve(node, "street-address", "Street\nSuite")!!.textValue.toString())
        }
    }

    @Test fun selectsReceiveListIndicesAndTextInputsReceiveText() {
        val node = ReflectionHelpers.newInstance(AssistStructure.ViewNode::class.java)
        ReflectionHelpers.setField(node, "mAutofillType", View.AUTOFILL_TYPE_LIST)
        ReflectionHelpers.setField(node, "mAutofillOptions", arrayOf<CharSequence>("", "Canada", "United States"))
        val selected = AutofillTypedValues.resolve(node, "country", "US")!!
        assertTrue(selected.isList)
        assertEquals(2, selected.listValue)
        assertNull(AutofillTypedValues.resolve(node, "country", "XX"))
        ReflectionHelpers.setField(node, "mAutofillType", View.AUTOFILL_TYPE_TEXT)
        assertEquals("US", AutofillTypedValues.resolve(node, "country", "US")!!.textValue.toString())
        ReflectionHelpers.setField(node, "mAutofillType", View.AUTOFILL_TYPE_TOGGLE)
        assertNull(AutofillTypedValues.resolve(node, "country", "US"))
    }

    @Test fun monthYearAndRegionLabelsMatchWithoutGuessingUnknownOptions() {
        assertEquals(1, AutofillTypedValues.optionIndex("cc-exp-month", "08", listOf("July", "August", "September")))
        assertEquals(1, AutofillTypedValues.optionIndex("cc-exp-year", "2030", listOf("29", "30", "31")))
        assertEquals(1, AutofillTypedValues.optionIndex("address-level1", "CA", listOf("New York", "California")))
        assertNull(AutofillTypedValues.optionIndex("country", "US", listOf("US", "United States")))
        assertNull(AutofillTypedValues.optionIndex("address-level1", "Unknown", listOf("California")))
    }
}
