package takagi.ru.monica.autofill_ng

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.data.model.*

class AutofillProfileValuesTest {
    private val address = BillingAddressData(fullName = "Original Full Name", firstName = "Separate",
        middleName = "Middle", lastName = "Parts", streetAddress = "First line", apartment = "Second line",
        addressLine3 = "Third line", district = "County", city = "City", stateProvince = "CA", country = "US",
        phone = "+12025550142", email = "default@example.test", homeEmail = "home@example.test",
        homePhone = "+12025550143", workEmail = "work@example.test", workPhone = "+12025550144")

    @Test fun fullNameAndPartsNeverReplaceEachOther() {
        mapOf("name" to "Original Full Name", "given-name" to "Separate", "additional-name" to "Middle", "family-name" to "Parts")
            .forEach { (hint, expected) -> assertEquals(expected, mapBillingAddressAutofillValue(hint, address)) }
        assertNull(mapBillingAddressAutofillValue("given-name", address.copy(firstName = "")))
        assertNull(mapBillingAddressAutofillValue("name", address.copy(fullName = "")))
        assertNull(mapBillingAddressAutofillValue("username", address))
        assertNull(mapBillingAddressAutofillValue("some-name", address))
    }

    @Test fun cardNamePartsAreIndependentAndNeverFillProfileNames() {
        val card = BankCardData(cardNumber = "4242424242424242", cardholderName = "Full Holder",
            expiryMonth = "08", expiryYear = "2030", cardholderFirstName = "Other", cardholderMiddleName = "M")
        assertEquals("Full Holder", mapBankCardAutofillValue("cc-name", card))
        assertEquals("Other", mapBankCardAutofillValue("cc-given-name", card))
        assertEquals("M", mapBankCardAutofillValue("cc-additional-name", card))
        assertNull(mapBankCardAutofillValue("cc-family-name", card))
        assertNull(mapBankCardAutofillValue("name", card))
    }

    @Test fun documentNamesRemainIndependent() {
        val doc = DocumentData(documentType = DocumentType.PASSPORT, documentNumber = "TEST", fullName = "Full",
            firstName = "Other", middleName = "M", lastName = "L")
        assertEquals("Full", mapDocumentAutofillValue("name", doc))
        assertEquals("Other", mapDocumentAutofillValue("given-name", doc))
        assertNull(mapDocumentAutofillValue("name", doc.copy(fullName = "")))
        assertNull(mapDocumentAutofillValue("given-name", doc.copy(firstName = "")))
    }

    @Test fun streetLinesDoNotContainCityPostalCodeOrCountry() {
        assertEquals("First line", mapBillingAddressAutofillValue("address-line1", address))
        assertEquals("Second line", mapBillingAddressAutofillValue("address-line2", address))
        assertEquals("Third line", mapBillingAddressAutofillValue("address-line3", address))
        assertEquals("First line\nSecond line\nThird line", mapBillingAddressAutofillValue("street-address", address))
        assertEquals("County", mapBillingAddressAutofillValue("address-level3", address))
        assertEquals("US", mapBillingAddressAutofillValue("country", address))
        assertEquals("US", AutofillProfileValues.countryCode(mapBillingAddressAutofillValue("country-name", address)!!))
    }

    @Test fun contactsUseOnlyTheirExplicitGroup() {
        assertEquals("home@example.test", mapBillingAddressAutofillValue("home email", address))
        assertEquals("work@example.test", mapBillingAddressAutofillValue("work email", address))
        assertEquals("default@example.test", mapBillingAddressAutofillValue("email", address))
        assertEquals("+12025550143", mapBillingAddressAutofillValue("home phone", address))
        assertNull(mapBillingAddressAutofillValue("home tel", address.copy(homePhone = "")))
        assertNull(mapBillingAddressAutofillValue("work email", address.copy(workEmail = "")))
        assertNull(mapBillingAddressAutofillValue("fax tel", address))
    }

    @Test fun internationalPhoneUsesItsNumberingPlan() {
        mapOf("tel-country-code" to "1", "tel-national" to "2025550142", "tel-area-code" to "202",
            "tel-local" to "5550142", "tel-local-prefix" to "555", "tel-local-suffix" to "0142")
            .forEach { (hint, expected) -> assertEquals(hint, expected, mapBillingAddressAutofillValue(hint, address)) }
        assertNull(mapBillingAddressAutofillValue("tel-extension", address))
        assertEquals("44", AutofillProfileValues.phonePart("tel-country-code", "+44 20 7946 0123", "GB"))
        assertNull(AutofillProfileValues.phonePart("tel-area-code", "not a phone", "US"))
    }

    @Test fun loginIdentifiersNeverLeakIntoProfileFields() {
        assertNull(AutofillAccountValues.resolve("tel", "email@example.test", true))
        assertNull(AutofillAccountValues.resolve("tel-area-code", "someone", true))
        assertNull(AutofillAccountValues.resolve("name", "someone", true))
        assertEquals("202", AutofillAccountValues.resolve("tel-area-code", "+12025550142", true))
        assertEquals("someone", AutofillAccountValues.resolve("username", "someone", false))
    }

    @Test fun semanticHintsRetainScopeAndProfileType() {
        val hint = AutofillHtmlHintTokens.detail("section-a shipping work email")!!
        assertEquals("work email", hint.fillHint)
        assertFalse(hint.sameSection(AutofillHtmlHintTokens.detail("section-a billing work email")))
        assertFalse(hint.sameSection(AutofillHtmlHintTokens.detail("section-b shipping work email")))
        for (raw in listOf("cc-name", "cc-number", "cc-given-name")) assertTrue(buildAutofillPickerRequestProfile(listOf(raw)).wantsBankCards)
        for (raw in listOf("additional-name", "address-line3", "home email", "work tel", "personName")) {
            val profile = buildAutofillPickerRequestProfile(listOf(raw))
            assertTrue(raw, profile.wantsDocuments && profile.wantsBillingAddresses)
        }
    }

    @Test fun repeatedCardsAndAddressesStayInTheFocusedBlock() {
        val fields = listOf(1 to "cc-name", 2 to "cc-number", 3 to "cc-exp-month", 4 to "cc-name", 5 to "cc-number", 6 to "cc-exp-month")
        assertEquals(setOf(1, 2, 3), AutofillRepeatedFieldScope.select(fields, 2))
        assertEquals(setOf(4, 5, 6), AutofillRepeatedFieldScope.select(fields, 5))
        assertEquals(setOf(3, 4), AutofillRepeatedFieldScope.select(listOf(1 to "street-address", 2 to "country", 3 to "street-address", 4 to "country"), 3))
        assertNull(AutofillRepeatedFieldScope.select(listOf(1 to "username", 2 to "password"), 1))
    }

    @Test fun newFieldsSurviveJsonAndEmbeddedEditsWithoutChangingFullName() {
        val decoded = CardWalletDataCodec.parseBillingAddressData(CardWalletDataCodec.encodeBillingAddressData(address))!!
        assertEquals(address, decoded)
        assertEquals("", CardWalletDataCodec.parseBillingAddressData("""{"fullName":"Only full name"}""")!!.firstName)
        val envelope = EmbeddedWalletContent.create(takagi.ru.monica.data.SecureItem(
            itemType = takagi.ru.monica.data.ItemType.BILLING_ADDRESS, title = "Synthetic",
            itemData = CardWalletDataCodec.encodeBillingAddressData(address)))
        val edited = envelope.edited("Changed title", "", kotlinx.serialization.json.Json.parseToJsonElement("""{"middleName":"Edited"}""") as kotlinx.serialization.json.JsonObject)
        val after = CardWalletDataCodec.parseBillingAddressData(edited.displayItem().itemData)!!
        assertEquals(address.copy(middleName = "Edited"), after)
    }
}
