package takagi.ru.monica.autofill_ng

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillResponseUnlockRegressionGuardTest {

    @Test
    fun verificationUsesTheSystemGateOnlyAfterSelection() {
        val builder = projectFile("app/src/main/java/takagi/ru/monica/autofill_ng/builder/FillResponseBuilderNg.kt").readText()
        assertFalse(builder.contains("buildLockedResponse"))
        assertFalse(builder.contains("responseBuilder.setAuthentication"))
        assertTrue(builder.contains("requireAuthentication = partition.requiresAuthentication"))
        for (name in listOf("AutofillCipherCallbackActivity", "AutofillPickerActivityV2")) {
            val activity = projectFile("app/src/main/java/takagi/ru/monica/autofill_ng/$name.kt").readText()
            assertTrue(activity.contains("AutofillDeviceVerification("))
            assertFalse(activity.contains("PasswordVerificationContent("))
            assertFalse(activity.contains("MonicaPasswordDialogAuthScreen("))
        }
    }

    @Test
    fun screenOffClearsTemporaryAutofillGrant() {
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/MonicaAutofillServiceNg.kt"
        ).readText()

        assertTrue(service.contains("Intent.ACTION_SCREEN_OFF"))
        assertTrue(service.contains("AutofillSessionGrants.clear"))
        assertTrue(service.contains("AutofillUnlockRequests.clear"))
    }

    @Test
    fun pendingUnlockRequestCachesOnlyCredentialIds() {
        val requestStore = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/auth/AutofillUnlockRequestStore.kt"
        ).readText()

        assertTrue(requestStore.contains("val passwordIds: List<Long>"))
        assertFalse(requestStore.contains("PasswordEntry"))
    }

    private fun projectFile(relativePath: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, relativePath)
    }
}
