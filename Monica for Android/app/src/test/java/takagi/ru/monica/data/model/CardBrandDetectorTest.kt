package takagi.ru.monica.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CardBrandDetectorTest {

    @Test
    fun detectsMainstreamBrandsFromCompleteNumbers() {
        val cases = mapOf(
            "4111 1111 1111 1111" to CardBrand.VISA,
            "5555-5555-5555-4444" to CardBrand.MASTERCARD,
            "2221 0000 0000 0009" to CardBrand.MASTERCARD,
            "378282246310005" to CardBrand.AMERICAN_EXPRESS,
            "30569309025904" to CardBrand.DINERS_CLUB,
            "6011111111111117" to CardBrand.DISCOVER,
            "3530111333300000" to CardBrand.JCB,
            "6200000000000005" to CardBrand.UNIONPAY,
            "6759649826438453" to CardBrand.MAESTRO,
            "2200123456789012" to CardBrand.MIR,
            "6073841234567890" to CardBrand.RUPAY
        )

        cases.forEach { (number, expectedBrand) ->
            assertEquals(number, expectedBrand, CardBrandDetector.detect(number))
        }
    }

    @Test
    fun detectsBrandsFromPartialNumbers() {
        val cases = mapOf(
            "4" to CardBrand.VISA,
            "55" to CardBrand.MASTERCARD,
            "37" to CardBrand.AMERICAN_EXPRESS,
            "35" to CardBrand.JCB,
            "62" to CardBrand.UNIONPAY,
            "2201" to CardBrand.MIR
        )

        cases.forEach { (number, expectedBrand) ->
            assertEquals(number, expectedBrand, CardBrandDetector.detect(number))
        }
    }

    @Test
    fun unknownWhenNumberIsBlankOrUnsupported() {
        assertEquals(CardBrand.UNKNOWN, CardBrandDetector.detect(""))
        assertEquals(CardBrand.UNKNOWN, CardBrandDetector.detect("not a card"))
        assertEquals(CardBrand.UNKNOWN, CardBrandDetector.detect("999999"))
    }

    @Test
    fun storedBrandNameWinsWhenPresent() {
        assertEquals(CardBrand.VISA, CardBrandDetector.detect(number = "", storedBrand = "Visa"))
        assertEquals(CardBrand.AMERICAN_EXPRESS, CardBrandDetector.detect(number = "", storedBrand = "amex"))
        assertEquals(CardBrand.MASTERCARD, CardBrandDetector.detect(number = "", storedBrand = "master-card"))
    }

    @Test
    fun storedCardsUseStoredBrandBeforeNumberGuessing() {
        assertEquals(
            CardBrand.VISA,
            CardBrandDetector.detectStoredCard(number = "3701", storedBrand = "VISA信用卡")
        )
        assertEquals(
            CardBrand.UNIONPAY,
            CardBrandDetector.detectStoredCard(number = "6510", storedBrand = "银联信用卡")
        )
    }

    @Test
    fun storedCardsDoNotUsePartialEagerDetection() {
        assertEquals(CardBrand.UNKNOWN, CardBrandDetector.detectStoredCard("3701"))
        assertEquals(CardBrand.UNKNOWN, CardBrandDetector.detectStoredCard("6510"))
        assertEquals(CardBrand.VISA, CardBrandDetector.detect("4"))
    }
}
