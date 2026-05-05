package takagi.ru.monica.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomPresetFieldRegressionTest {

    @Test
    fun unfilledDraftShouldNotPersistAsBlankCustomField() {
        val presetDraft = CustomFieldDraft(
            title = "Recovery code",
            value = "",
            isPreset = true
        )
        val manualDraft = CustomFieldDraft(
            title = "Recovery code",
            value = "",
            isPreset = false
        )

        assertFalse(presetDraft.shouldPersist())
        assertFalse(manualDraft.shouldPersist())
        assertTrue(manualDraft.copy(value = "abc123").shouldPersist())
    }

    @Test
    fun blankPresetNamesAreIgnoredWhenReadingSettingsJson() {
        val json = PresetCustomField.listToJson(
            listOf(
                PresetCustomField(id = "blank", fieldName = ""),
                PresetCustomField(id = "valid", fieldName = "Recovery code")
            )
        )

        val fields = PresetCustomField.listFromJson(json)

        assertTrue(fields.none { it.fieldName.isBlank() })
        assertTrue(fields.any { it.fieldName == "Recovery code" })
    }
}
