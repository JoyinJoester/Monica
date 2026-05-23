package takagi.ru.monica.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.viewmodel.CategoryFilter

class PasswordListMdbxFilterTest {

    @Test
    fun mdbxFilterMatchesPasswordEntriesOwnedByThatDatabase() {
        val entry = passwordEntry(mdbxDatabaseId = 7L)

        assertTrue(entry.matchesPasswordCategoryFilter(CategoryFilter.MdbxDatabase(7L)))
        assertFalse(entry.matchesPasswordCategoryFilter(CategoryFilter.MdbxDatabase(8L)))
    }

    @Test
    fun quickFolderRootVisibilityKeepsMdbxDatabaseEntries() {
        val entry = passwordEntry(mdbxDatabaseId = 7L)

        val visible = applyQuickFolderRootVisibility(
            entries = listOf(entry),
            currentFilter = CategoryFilter.MdbxDatabase(7L)
        )

        assertTrue(visible.contains(entry))
    }

    @Test
    fun localRootVisibilityDoesNotIncludeMdbxEntries() {
        val entry = passwordEntry(mdbxDatabaseId = 7L)

        val visible = applyQuickFolderRootVisibility(
            entries = listOf(entry),
            currentFilter = CategoryFilter.Local
        )

        assertTrue(visible.isEmpty())
    }

    private fun passwordEntry(mdbxDatabaseId: Long?): PasswordEntry {
        return PasswordEntry(
            id = 1L,
            title = "MDBX entry",
            website = "example.com",
            username = "alice",
            password = "secret",
            mdbxDatabaseId = mdbxDatabaseId
        )
    }
}
