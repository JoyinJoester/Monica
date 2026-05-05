package takagi.ru.monica.keepass

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeePassFolderPathRegressionGuardTest {

    @Test
    fun passwordFallbackCreationUsesEntryGroupPath() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/utils/KeePassKdbxService.kt"
        ).readText()

        val addOrUpdateBody = source.substringAfter("suspend fun addOrUpdatePasswordEntries(")
            .substringBefore("suspend fun updatePasswordEntry(")
        val updateBody = source.substringAfter("suspend fun updatePasswordEntry(")
            .substringBefore("suspend fun addPasswordEntry(")

        assertTrue(
            "addOrUpdatePasswordEntries must add new password entries to entry.keepassGroupPath, not root.",
            addOrUpdateBody.contains("groupPath = entry.keepassGroupPath")
        )
        assertTrue(
            "updatePasswordEntry fallback creation must add new password entries to entry.keepassGroupPath.",
            updateBody.contains("groupPath = entry.keepassGroupPath")
        )
        assertFalse(
            "Password creation fallback must not append directly to the root group.",
            addOrUpdateBody.contains("entries + newEntry")
        )
        assertFalse(
            "Password update fallback must not append directly to the root group.",
            updateBody.contains("entries + newEntry")
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
