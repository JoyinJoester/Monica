package takagi.ru.monica.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorHistoryRegressionGuardTest {

    @Test
    fun generatedValuesEnterHistoryOnlyAfterCopy() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/GeneratorScreen.kt"
        ).readText()
        val regenerateBody = source
            .substringAfter("val regenerateNow: () -> Unit = regenerate@{")
            .substringBefore("LaunchedEffect(externalRefreshRequestKey)")
        val copyGeneratedResultBody = source
            .substringAfter("val copyGeneratedResult: (String) -> Unit = { text ->")
            .substringBefore("// SSH_KEY 的结果卡片")

        assertFalse(
            "Generator history must not be appended from regenerate/auto-generate paths.",
            regenerateBody.contains("appendGeneratorHistory(")
        )
        assertTrue(
            "Copying a generated value must copy it, then append it to generator history using the current generator type.",
            copyGeneratedResultBody.contains("copyToClipboard(context, text)") &&
                copyGeneratedResultBody.contains("appendGeneratorHistory(") &&
                copyGeneratedResultBody.contains("generatorTypeTitle(selectedGenerator, context)") &&
                copyGeneratedResultBody.contains("selectedGenerator.name")
        )
        assertTrue(
            "Both normal and SSH result cards should use the same copy-and-history path.",
            Regex("onCopy = copyGeneratedResult").findAll(source).count() >= 2
        )
        assertTrue(
            "Duplicate suppression should inspect full history, not the currently filtered view.",
            source.contains("val latest = historyList.firstOrNull()") &&
                !source.contains("val latest = filteredHistoryList.firstOrNull()")
        )
    }

    private fun projectFile(path: String): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            dir.parentFile != null &&
            !File(dir, "settings.gradle").exists() &&
            !File(dir, "settings.gradle.kts").exists()
        ) {
            dir = dir.parentFile!!.canonicalFile
        }
        return File(dir, path)
    }
}
