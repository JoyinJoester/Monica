package takagi.ru.monica.autofill_ng

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.data.PasswordEntry

class AutofillSaveContextPolicyTest {
    private val target = AutofillSaveContextPolicy.Target("browser.test", "https", "login.example.test")
    private fun step(purpose: AutofillFormPurpose, account: String = "", hasAccount: Boolean = account.isNotEmpty(),
                     scope: AutofillSaveContextPolicy.Target = target) = AutofillSaveContextPolicy.Snapshot(scope, purpose, hasAccount, account)

    @Test fun relatedAndroidContextsSupplyTheAccountWithoutGlobalMemory() {
        assertEquals("alice", AutofillSaveContextPolicy.username(listOf(step(AutofillFormPurpose.USERNAME_STEP,"alice"),
            step(AutofillFormPurpose.PASSWORD_STEP), step(AutofillFormPurpose.PASSWORD_STEP))))
        assertEquals("", AutofillSaveContextPolicy.username(listOf(step(AutofillFormPurpose.PASSWORD_STEP))))
    }
    @Test fun explicitEmptyAccountAndNewPasswordNeverReuseEarlierAccount() {
        for (last in listOf(step(AutofillFormPurpose.PASSWORD_STEP, hasAccount = true),
            step(AutofillFormPurpose.RESET_PASSWORD), step(AutofillFormPurpose.CHANGE_PASSWORD))) {
            assertEquals("",AutofillSaveContextPolicy.username(listOf(step(AutofillFormPurpose.USERNAME_STEP,"alice"),last)))
        }
    }
    @Test fun accountCannotCrossAppHostSchemeOrAnUnrelatedIntermediateForm() {
        for (other in listOf(target.copy(packageName="other.test"),target.copy(host="evil.test"),
            target.copy(scheme="http"),target.copy(host=null))) {
            assertEquals("",AutofillSaveContextPolicy.username(listOf(step(AutofillFormPurpose.USERNAME_STEP,"alice",scope=other),step(AutofillFormPurpose.PASSWORD_STEP))))
        }
        assertEquals("",AutofillSaveContextPolicy.username(listOf(step(AutofillFormPurpose.USERNAME_STEP,"alice"),
            step(AutofillFormPurpose.UNKNOWN), step(AutofillFormPurpose.PASSWORD_STEP))))
    }

    @Test fun latestAccountWinsAndRecoveryStopsAtTheBoundedHistory() {
        assertEquals("bob", AutofillSaveContextPolicy.username(listOf(
            step(AutofillFormPurpose.USERNAME_STEP, "alice"), step(AutofillFormPurpose.USERNAME_STEP, "bob"),
            step(AutofillFormPurpose.PASSWORD_STEP))))
        for (distance in 1..10) {
            val contexts = listOf(step(AutofillFormPurpose.USERNAME_STEP, "alice")) +
                List(distance) { step(AutofillFormPurpose.PASSWORD_STEP) }
            assertEquals("distance=$distance", if (distance <= 7) "alice" else "", AutofillSaveContextPolicy.username(contexts))
        }
    }

    @Test fun maliciousAndUnsupportedUrlsNeverSuggestUpdatingAnExistingWebAccount() {
        val entry = PasswordEntry(id=1, title="Fixture", username="ciphertext", password="ciphertext",
            website="https://login.example.test")
        for (site in listOf("https://login.example.test.evil.test", "https://login.example.test@evil.test",
            "https://evil.test/login.example.test", "javascript://login.example.test", "file://login.example.test",
            "https://login.example.test%00.evil.test", "https://login.example.test\\@evil.test")) {
            assertFalse(site, AutofillSaveMatchPolicy.matches(entry, "alice", "alice", "browser.test", site))
        }
    }
    @Test fun updateSuggestionsRequireExactAccountAndHostInsteadOfBrowserPackageOrParentDomain() {
        val entry = PasswordEntry(id=1,title="Fixture",username="encrypted",password="encrypted",
            website="https://login.example.test/path, https://second.test", appPackageName="browser.test")
        fun match(value: PasswordEntry = entry, user: String = "alice", site: String = "login.example.test", account: String? = "alice") =
            AutofillSaveMatchPolicy.matches(value,account,user,"browser.test",site)
        assertTrue(match())
        assertTrue(match(site="second.test"))
        assertFalse(match(site="example.test"))
        assertFalse(match(site="evil.test"))
        assertFalse(match(site="login.example.test@evil.test"))
        assertFalse(match(account=null))
        assertFalse(match(user="ALICE"))
        assertFalse(match(user="",account=""))
        assertFalse(match(entry.copy(isArchived=true)))
        assertFalse(match(entry.copy(isDeleted=true)))
        assertFalse(match(entry.copy(loginType="WIFI")))
    }
}
