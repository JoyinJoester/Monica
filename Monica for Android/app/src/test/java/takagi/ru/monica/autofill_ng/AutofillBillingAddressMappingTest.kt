package takagi.ru.monica.autofill_ng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.FieldHint
import takagi.ru.monica.data.model.BillingAddressData

class AutofillBillingAddressMappingTest {

    @Test
    fun billingAddressFieldsMapToAutofillHints() {
        val data = sampleAddress()

        assertEquals("Monica User", mapBillingAddressAutofillValue(FieldHint.PERSON_NAME.name, data))
        assertEquals("Monica Pass", mapBillingAddressAutofillValue(FieldHint.COMPANY_NAME.name, data))
        assertEquals("1 Sakura Street, Apt 8, Tokyo, Tokyo, 100-0001, JP", mapBillingAddressAutofillValue(FieldHint.POSTAL_ADDRESS.name, data))
        assertEquals("100-0001", mapBillingAddressAutofillValue(FieldHint.POSTAL_CODE.name, data))
        assertEquals("Tokyo", mapBillingAddressAutofillValue(FieldHint.ADDRESS_CITY.name, data))
        assertEquals("JP", mapBillingAddressAutofillValue(FieldHint.ADDRESS_COUNTRY.name, data))
        assertEquals("monica@example.com", mapBillingAddressAutofillValue(FieldHint.EMAIL_ADDRESS.name, data))
    }

    @Test
    fun billingAddressSearchCoversAddressAndContactFields() {
        val data = sampleAddress()

        assertTrue(data.matchesAutofillSearch("sakura"))
        assertTrue(data.matchesAutofillSearch("100-0001"))
        assertTrue(data.matchesAutofillSearch("monica@example"))
    }

    private fun sampleAddress(): BillingAddressData =
        BillingAddressData(
            fullName = "Monica User",
            company = "Monica Pass",
            streetAddress = "1 Sakura Street",
            apartment = "Apt 8",
            city = "Tokyo",
            stateProvince = "Tokyo",
            postalCode = "100-0001",
            country = "JP",
            phone = "+81 00 0000 0000",
            email = "monica@example.com",
        )
}
