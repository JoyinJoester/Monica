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
}
