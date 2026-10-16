package takagi.ru.monica.autofill_ng

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillDetectionIntegrationGuardTest {

    @Test
    fun servicePassesManualRequestThroughWeakTargetAndConfidenceGates() {
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/MonicaAutofillServiceNg.kt"
        ).readText()
        val parser = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/EnhancedAutofillStructureParserV2.kt"
        ).readText()

        assertTrue(service.contains("FillRequest.FLAG_MANUAL_REQUEST"))
        assertTrue(service.contains("allowWeakTargets = isManualRequest"))
        assertTrue(service.contains("manualRequest = isManualRequest"))
        assertFalse(service.contains("manualRequest = isManualRequest || usedWeakReparse"))
        assertTrue(service.contains("requireExplicitWeakLoginSignal = true"))
        assertTrue(service.contains("if (!isManualRequest && loginTargetCount == 0"))
        assertTrue(parser.contains("if (allowWeakTargets) return@let list"))
        assertTrue(parser.contains("promotePasswordTermCandidates"))
        assertTrue(parser.contains("nodeHasPasswordTermMatch"))
        assertTrue(parser.contains("nodeHasLoginTermMatch"))
        assertTrue(parser.contains("weakLoginContext"))
        assertTrue(parser.contains("hasLoginTypeField"))
        assertTrue(parser.contains("LOWEST username node signals"))
        assertTrue(parser.contains("autofillLabelLoginTranslations"))
        assertTrue(parser.contains("automaticWeakAccountAccuracy"))
        assertTrue(service.contains("AutofillRequestContextPolicy.allowPackageMatching("))
        assertTrue(service.contains("allowPackageMatch = allowPackageMatch"))
    }

    @Test
    fun manualPickerKeepsAccessibilityFillWithClipboardFallback() {
        val picker = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/AutofillPickerActivityV2.kt"
        ).readText()

        assertTrue(picker.contains("scheduleManualAccessibilityFill"))
        assertTrue(picker.contains("MonicaAccessibilityService.requestCredentialFill"))
        assertTrue(picker.contains("copyManualCredentialFallback"))
        assertFalse(picker.contains("scheduleManualClipboardFill"))
    }

    @Test
    fun parserDiagnosticsDoNotPersistHtmlAttributeValues() {
        val parser = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/EnhancedAutofillStructureParserV2.kt"
        ).readText()

        assertTrue(parser.contains("htmlAttrNames"))
        assertFalse(parser.contains("\"htmlAttrs\""))
        assertFalse(
            parser.contains(
                """joinToString(",") { "${'$'}{it.first}=${'$'}{it.second}" }"""
            )
        )
    }

    @Test
    fun neverExpireAutofillStillHonorsExplicitVaultLock() {
        val builder = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/builder/FilledDataBuilderNg.kt"
        ).readText()

        assertTrue(builder.contains("canAccessVaultNowStrict"))
        assertFalse(builder.contains("canAccessVaultMaterialNow"))
    }

    @Test
    fun genericNumbersAndHiddenFieldsUseTheSharedAdmissionPolicy() {
        val parser = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/EnhancedAutofillStructureParserV2.kt"
        ).readText()
        val numberBranch = parser
            .substringAfter("InputType.TYPE_CLASS_NUMBER ->")
            .substringBefore("return out")

        assertTrue(numberBranch.contains("genericNumberFallbackAccuracy()"))
        assertFalse(numberBranch.contains("Accuracy.MEDIUM"))
        assertTrue(parser.contains("shouldIncludeHiddenCredential("))
        assertTrue(parser.contains("matchesUsernameLabel(hint)"))
        assertTrue(parser.contains("matchesPhoneFieldName"))
        assertTrue(parser.contains("placeholder"))
        assertTrue(parser.contains("inputmode"))
    }

    @Test
    fun parserAndAuthenticationCallbackShareTheConflictPolicy() {
        val parser = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/EnhancedAutofillStructureParserV2.kt"
        ).readText()
        val callback = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/AutofillCipherCallbackActivity.kt"
        ).readText()

        assertTrue(parser.contains("AutofillFieldRolePolicy.selectWithDiagnostics("))
        assertTrue(callback.contains("val parser = EnhancedAutofillStructureParserV2()"))
        assertTrue(callback.contains("source = \"assist_structure\""))
    }

    @Test
    fun phoneLoginFieldsUseTheSavedAccountAcrossEveryPickerPath() {
        val modernPicker = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/AutofillPickerActivityV2.kt"
        ).readText()
        val authenticatedCallback = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/AutofillCipherCallbackActivity.kt"
        ).readText()
        val legacyPicker = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/AutofillPickerActivity.kt"
        ).readText()

        assertTrue(modernPicker.contains("AutofillAccountValues.resolve(normalizedHint, accountValue, allowAccountInEmailField)"))
        assertTrue(authenticatedCallback.contains("AutofillAccountValues.resolve(normalizedHint, accountValue, allowAccountInEmailField)"))
        assertTrue(
            "The legacy picker must also treat an explicit phone login field as the saved account identifier.",
            legacyPicker.contains("FieldHint.PHONE_NUMBER.name -> AutofillAccountValues.resolve(hint, accountValue, false)")
        )
        assertFalse(
            "A phone login field must never receive the password value.",
            legacyPicker.contains("FieldHint.PHONE_NUMBER.name -> decryptedPassword")
        )
    }

    private fun projectFile(relativePath: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!
        }
        return File(directory, relativePath)
    }
}
