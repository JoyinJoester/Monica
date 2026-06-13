package takagi.ru.monica.autofill_ng

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.FieldHint

class AutofillPickerRequestProfileTest {
    @Test
    fun `payment form with email should request passwords and bank cards together`() {
        val profile = buildAutofillPickerRequestProfile(
            listOf(
                FieldHint.EMAIL_ADDRESS.name,
                FieldHint.CREDIT_CARD_NUMBER.name,
                FieldHint.CREDIT_CARD_EXPIRATION_DATE.name,
            )
        )

        assertTrue(profile.wantsPasswords)
        assertTrue(profile.wantsBankCards)
        assertFalse(profile.wantsDocuments)
        assertFalse(profile.wantsBillingAddresses)
    }

    @Test
    fun `billing address fields should request bank cards and documents together`() {
        val profile = buildAutofillPickerRequestProfile(
            listOf(
                FieldHint.POSTAL_ADDRESS.name,
                FieldHint.POSTAL_CODE.name,
                FieldHint.ADDRESS_CITY.name,
            )
        )

        assertFalse(profile.wantsPasswords)
        assertTrue(profile.wantsBankCards)
        assertTrue(profile.wantsDocuments)
        assertTrue(profile.wantsBillingAddresses)
    }

    @Test
    fun `mixed structured form should request cards and documents together`() {
        val profile = buildAutofillPickerRequestProfile(
            listOf(
                FieldHint.CREDIT_CARD_NUMBER.name,
                FieldHint.IDENTITY_NUMBER.name,
                FieldHint.PERSON_NAME.name,
            )
        )

        assertFalse(profile.wantsPasswords)
        assertTrue(profile.wantsBankCards)
        assertTrue(profile.wantsDocuments)
        assertFalse(profile.wantsBillingAddresses)
    }

    @Test
    fun `pure login form should keep password only suggestions`() {
        val profile = buildAutofillPickerRequestProfile(
            listOf(
                FieldHint.USERNAME.name,
                FieldHint.PASSWORD.name,
            )
        )

        assertTrue(profile.wantsPasswords)
        assertFalse(profile.wantsBankCards)
        assertFalse(profile.wantsDocuments)
        assertFalse(profile.wantsBillingAddresses)
    }
}
