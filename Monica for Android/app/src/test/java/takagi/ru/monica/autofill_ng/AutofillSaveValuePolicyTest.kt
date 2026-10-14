package takagi.ru.monica.autofill_ng

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.FieldHint

class AutofillSaveValuePolicyTest {
    @Test fun changePasswordSavesNewValueIncludingIntentionalWhitespace() {
        assertEquals(" new value ", AutofillSaveValuePolicy.password(listOf(
            FieldHint.PASSWORD to "old value", FieldHint.NEW_PASSWORD to " new value ",
            FieldHint.NEW_PASSWORD to " new value ", FieldHint.OTP_CODE to "123456")))
    }
    @Test fun missingOrMismatchedConfirmationDoesNotSaveOldPassword() {
        for (confirmation in listOf(null,"","different")) assertNull(AutofillSaveValuePolicy.password(listOf(
            FieldHint.PASSWORD to "old", FieldHint.NEW_PASSWORD to "new", FieldHint.NEW_PASSWORD to confirmation)))
    }
    @Test fun legacyLoginRemainsSavableButOtpAndAccountOnlyDoNot() {
        assertEquals("legacy",AutofillSaveValuePolicy.password(listOf(FieldHint.PASSWORD to "legacy")))
        assertNull(AutofillSaveValuePolicy.password(listOf(FieldHint.USERNAME to "user",FieldHint.OTP_CODE to "123456")))
        assertNull(AutofillSaveValuePolicy.password(listOf(FieldHint.PASSWORD to "one",FieldHint.PASSWORD to "two")))
    }

    @Test fun unicodeAndLongPasswordsAreSavedVerbatimRegardlessOfFieldOrder() {
        val random = kotlin.random.Random(320)
        for (password in listOf(" leading and trailing ", "密碼🔐e\u0301", "p".repeat(4096))) {
            val fields = listOf(FieldHint.PASSWORD to "old", FieldHint.NEW_PASSWORD to password,
                FieldHint.NEW_PASSWORD to password, FieldHint.OTP_CODE to "123456", FieldHint.USERNAME to "account")
            repeat(20) { assertEquals(password, AutofillSaveValuePolicy.password(fields.shuffled(random))) }
        }
    }

    @Test fun confirmationIsExactWithoutUnicodeNormalizationOrWhitespaceTrimming() {
        for ((password, confirmation) in listOf("secret " to "secret", "é" to "e\u0301", "Secret" to "secret")) {
            assertNull(AutofillSaveValuePolicy.password(listOf(FieldHint.NEW_PASSWORD to password,
                FieldHint.NEW_PASSWORD to confirmation, FieldHint.PASSWORD to "old")))
        }
    }
}
