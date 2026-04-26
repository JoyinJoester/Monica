package takagi.ru.monica.autofill_ng

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillInlineClickRegressionGuardTest {

    @Test
    fun directInlineSuggestionsUseRealAuthenticationCallbackInsteadOfNoopIntent() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/builder/FillResponseBuilderNg.kt"
        ).readText()
        val cipherDatasetBody = source.substringAfter("private fun buildCipherDataset(")
            .substringBefore("private fun createCipherAuthPendingIntent(")

        assertTrue(
            "Inline suggestions should still create the real callback PendingIntent for keyboards that launch the slice PendingIntent.",
            cipherDatasetBody.contains("val inlinePendingIntent = if (hasInlinePresentation)") &&
                cipherDatasetBody.contains("createCipherAuthPendingIntent(")
        )
        assertTrue(
            "The real callback PendingIntent should be wired to the inline presentation itself.",
            cipherDatasetBody.contains("pendingIntent = inlinePendingIntent ?: return@create null")
        )
        assertFalse(
            "Inline suggestion clicks must not be wired to a no-op PendingIntent, because some keyboards launch it instead of applying dataset values.",
            cipherDatasetBody.contains("createNoopPendingIntent")
        )
        assertFalse(
            "Concrete dropdown suggestions should remain direct-fill; do not set Dataset authentication just to support inline.",
            cipherDatasetBody.contains("setAuthentication")
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
