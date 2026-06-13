package takagi.ru.monica.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class CardWalletDataCodecTest {

    @Test
    fun billingAddressDataRoundTrips() {
        val data = BillingAddressData(
            fullName = "Monica User",
            company = "Monica Pass",
            streetAddress = "1 Test Street",
            apartment = "Room 2",
            city = "Tokyo",
            stateProvince = "Tokyo",
            postalCode = "100-0001",
            country = "JP",
            phone = "+81 00 0000 0000",
            email = "monica@example.com",
            isDefault = true
        )

        val encoded = CardWalletDataCodec.encodeBillingAddressData(data)
        val decoded = CardWalletDataCodec.parseBillingAddressData(encoded)

        assertEquals(data, decoded)
    }

    @Test
    fun legacyBillingAddressJsonParsesAsBillingAddressData() {
        val legacy = BillingAddress(
            streetAddress = "1 Test Street",
            apartment = "Room 2",
            city = "Tokyo",
            stateProvince = "Tokyo",
            postalCode = "100-0001",
            country = "JP"
        )
        val encoded = CardWalletDataCodec.encodeBillingAddress(legacy)

        val decoded = CardWalletDataCodec.parseBillingAddressData(encoded)

        assertNotNull(decoded)
        assertEquals("1 Test Street", decoded?.streetAddress)
        assertEquals("Room 2", decoded?.apartment)
        assertEquals("Tokyo", decoded?.city)
        assertFalse(decoded?.isEmpty() ?: true)
    }

    @Test
    fun paymentAccountDataRoundTrips() {
        val data = PaymentAccountData(
            paymentType = PaymentAccountType.PAYMENT_APP,
            provider = "PayPal",
            accountName = "Shopping",
            accountHolderName = "Monica User",
            email = "monica@example.com",
            phone = "+1 000 000 0000",
            username = "monica",
            accountId = "acct_123",
            linkedCardLast4 = "4242",
            billingAddress = CardWalletDataCodec.encodeBillingAddress(
                BillingAddress(
                    streetAddress = "1 Test Street",
                    city = "Tokyo",
                    postalCode = "100-0001",
                    country = "JP"
                )
            ),
            website = "https://paypal.com",
            currency = "USD",
            notes = "Primary payment app",
            isDefault = true,
            customFields = listOf(SecureCustomField(label = "Customer ID", value = "C-123"))
        )

        val encoded = CardWalletDataCodec.encodePaymentAccountData(data)
        val decoded = CardWalletDataCodec.parsePaymentAccountData(encoded)

        assertEquals(data, decoded)
    }

    @Test
    fun legacyPaymentAccountJsonParsesAsPaymentAccountData() {
        val legacy = """
            {
              "type": "bank_account",
              "service": "Wise",
              "name": "Travel balance",
              "holderName": "Monica User",
              "email": "monica@example.com",
              "accountNumber": "****1234",
              "swift": "TRWIBEB1",
              "address1": "1 Test Street",
              "city": "Tokyo",
              "zip": "100-0001",
              "country": "JP"
            }
        """.trimIndent()

        val decoded = CardWalletDataCodec.parsePaymentAccountData(legacy)

        assertNotNull(decoded)
        assertEquals(PaymentAccountType.BANK_ACCOUNT, decoded?.paymentType)
        assertEquals("Wise", decoded?.provider)
        assertEquals("Travel balance", decoded?.accountName)
        assertEquals("****1234", decoded?.maskedAccountNumber)
        assertEquals("TRWIBEB1", decoded?.swiftBic)
        assertEquals("Tokyo", CardWalletDataCodec.parseBillingAddress(decoded?.billingAddress.orEmpty()).city)
        assertFalse(decoded?.isEmpty() ?: true)
    }
}
