package takagi.ru.monica.autofill_ng

import org.junit.Assert.*
import org.junit.Test

class AutofillHtmlHintTokensTest {
    @Test fun qualifiersAndWhitespaceDoNotChangeTheRole() {
        assertEquals("email", AutofillHtmlHintTokens.fieldToken(" SECTION-account\tBILLING\nHOME email "))
        assertEquals("username", AutofillHtmlHintTokens.fieldToken("section-password username webauthn"))
        assertEquals("password", AutofillHtmlHintTokens.fieldToken("section-email current-password"))
    }

    @Test fun ambiguousOrOversizedHintsAreNotCombinedIntoARole() {
        listOf("", "on", "username password", "section-a", "email extra", "a".repeat(1025))
            .forEach { assertNull(it.take(40), AutofillHtmlHintTokens.fieldToken(it)) }
        assertEquals("not-a-password", AutofillHtmlHintTokens.fieldToken("not-a-password"))
    }

    @Test fun legacyAndStandardAliasesStayCompatible() {
        mapOf("new password" to "new-password", "newPassword" to "new-password",
            "newUsername" to "username", "wifiPassword" to "password",
            "smsOTPCode" to "one-time-code", "tel" to "phone",
            "postal-code" to "postalCode", "name" to "personName")
            .forEach { (input, expected) -> assertEquals(input, expected, AutofillHtmlHintTokens.fieldToken(input)) }
    }
}
