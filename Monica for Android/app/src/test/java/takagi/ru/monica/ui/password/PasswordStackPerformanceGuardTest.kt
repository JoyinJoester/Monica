package takagi.ru.monica.ui.password

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.ui.PasswordListScrollSnapshot
import takagi.ru.monica.ui.persistenceKey

class PasswordStackPerformanceGuardTest {

    @Test
    fun `stack animation avoids structural churn and offscreen alpha buffers`() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/password/StackedPasswordGroup.kt"
        ).readText()

        assertTrue(source.contains("updateTransition("))
        assertTrue(source.contains("CompositingStrategy.ModulateAlpha"))
        assertFalse(source.contains("if (layerAlpha > 0.01f)"))
        assertFalse(source.contains("label = \"expand_animation\""))
        assertFalse(source.contains("label = \"container_alpha\""))
    }

    @Test
    fun `password card keeps second samples and draw phase animation`() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/password/PasswordCardDisplayContent.kt"
        ).readText()

        assertTrue(source.contains("rememberTotpTickerMillis(smooth = false)"))
        assertTrue(source.contains("progress = { animatedProgress.value }"))
        assertFalse(source.contains("rememberTotpTickerMillis(smoothProgress)"))
    }

    @Test
    fun `password rows declare reusable content types and scroll persistence is coalesced`() {
        val rowsSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/password/PasswordListRows.kt"
        ).readText()
        val supportSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/password/PasswordListContentSupport.kt"
        ).readText()

        assertTrue(rowsSource.contains("contentType = { item -> item.contentTypeKey() }"))
        assertTrue(supportSource.contains("persistenceKey()"))
        assertTrue(supportSource.contains("isScrollInProgress"))
    }

    @Test
    fun `scroll offsets in one moving item share a persistence key and idle keeps final offset`() {
        val moving = PasswordListScrollSnapshot(
            allowPersistence = true,
            pendingRestore = false,
            totalItems = 100,
            index = 12,
            offset = 10,
            anchorKey = "group:12",
            isScrollInProgress = true
        )
        val movingFurther = moving.copy(offset = 180)
        val idle = movingFurther.copy(isScrollInProgress = false)

        assertEquals(moving.persistenceKey(), movingFurther.persistenceKey())
        assertNotEquals(movingFurther.persistenceKey(), idle.persistenceKey())
        assertEquals(180, idle.persistenceKey().offsetWhenIdle)
    }

    @Test
    fun `stack summary preserves merged favorite and cover semantics without allocations`() {
        val first = password(id = 1L, username = "same", favorite = true, cover = true)
            .copy(passwordGroupId = "fixture-project")
        val second = password(id = 2L, username = "same", favorite = true)
            .copy(passwordGroupId = "fixture-project")
        val different = password(id = 3L, username = "different", favorite = false)

        assertEquals(
            PasswordStackSummary(
                isMergedPasswordCard = true,
                isGroupFavorited = true,
                hasGroupCover = true
            ),
            summarizePasswordStack(listOf(first, second))
        )
        assertEquals(
            PasswordStackSummary(
                isMergedPasswordCard = false,
                isGroupFavorited = false,
                hasGroupCover = true
            ),
            summarizePasswordStack(listOf(first, different))
        )
    }

    private fun password(
        id: Long,
        username: String,
        favorite: Boolean,
        cover: Boolean = false
    ): PasswordEntry = PasswordEntry(
        id = id,
        title = "Example",
        website = "https://example.com",
        username = username,
        password = "secret-$id",
        isFavorite = favorite,
        isGroupCover = cover
    )

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
