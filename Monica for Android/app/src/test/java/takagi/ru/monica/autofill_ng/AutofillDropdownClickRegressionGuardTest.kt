package takagi.ru.monica.autofill_ng

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillDropdownClickRegressionGuardTest {

    @Test
    fun dropdownCipherSuggestionsUseDirectValuesAndKeepManualFallback() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/builder/FillResponseBuilderNg.kt"
        ).readText()
        val buildBody = source.substringAfter("fun build(")
            .substringBefore("private fun buildCipherDataset(")
        val cipherDatasetBody = source.substringAfter("private fun buildCipherDataset(")
            .substringBefore("private fun createCipherAuthPendingIntent(")
        val authIntentBody = source.substringAfter("private fun createCipherAuthPendingIntent(")
            .substringBefore("private fun buildVaultItemDataset(")

        assertTrue(
            "Concrete dropdown suggestions should keep the 1.0.281 direct-fill behavior instead of requiring a callback before values can be applied.",
            cipherDatasetBody.contains("partition.filledItems.forEach { filledItem ->") &&
                cipherDatasetBody.contains("value = filledItem.value")
        )
        assertFalse(
            "Concrete dropdown suggestions must not set Dataset authentication; affected vendor ROMs dropped callback args and caused tap-flash-without-fill.",
            cipherDatasetBody.contains("setAuthentication")
        )
        assertTrue(
            "Manual picker fallback should remain available even when direct autofill has a single match.",
            buildBody.contains("responseBuilder.addDataset(\r\n            buildVaultItemDataset") ||
                buildBody.contains("responseBuilder.addDataset(\n            buildVaultItemDataset")
        )
        assertFalse(
            "Manual picker fallback must not be hidden behind preferDirectAutoFill; it is the safety path when device-specific direct suggestions misbehave.",
            buildBody.contains("!request.preferDirectAutoFill || filledData.filledPartitions.size != 1")
        )
        assertTrue(
            "Callback autofill IDs and hints must be produced from the same ordered target list.",
            authIntentBody.contains("autofillIds = ArrayList(targets.map { it.autofillId })") &&
                authIntentBody.contains("autofillHints = ArrayList(targets.map { it.hintName })")
        )
        assertFalse(
            "Callback IDs must not come only from filledItems while hints come from all views; that mismatch makes dropdown clicks flash without filling.",
            authIntentBody.contains("partition.filledItems.map { it.autofillId }.distinct()")
        )
    }

    private fun projectFile(relativePath: String): File {
        val candidates = mutableListOf<File>()
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            candidates += File(dir, relativePath)
            dir = dir.parentFile
        }

        return candidates.firstOrNull { it.isFile }
            ?: error("Unable to find project file: $relativePath from ${System.getProperty("user.dir")}")
    }
}
