package takagi.ru.monica.ui.password

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordListQuickFilterItem
import takagi.ru.monica.data.PasswordPageContentType
import takagi.ru.monica.viewmodel.CategoryFilter

class PasswordAggregateMdbxFilterTest {

    @Test
    fun mdbxFilterIncludesUnboundPasskeysOwnedByThatDatabase() {
        val passkeys = listOf(
            passkey(id = 1L, mdbxDatabaseId = 7L),
            passkey(id = 2L, mdbxDatabaseId = 8L)
        )

        val items = buildPasswordAggregateItems(
            selectedContentTypes = setOf(PasswordPageContentType.PASSKEY),
            bankCards = emptyList(),
            documents = emptyList(),
            billingAddresses = emptyList(),
            notes = emptyList(),
            totpItems = emptyList(),
            passkeys = passkeys,
            searchQuery = "",
            categoryFilter = CategoryFilter.MdbxDatabase(7L)
        )

        assertEquals(listOf(1L), items.mapNotNull { it.passkeyRecordId })
    }

    @Test
    fun barcodeQuickFilterRemovesAggregateItems() {
        val aggregateItems = listOf(
            PasswordAggregateListItemUi(
                key = "note:1",
                entry = PasswordEntry(id = 1L, title = "Note", website = "", username = "", password = ""),
                type = PasswordPageContentType.NOTE,
                badgeText = "note",
                badgeColor = androidx.compose.ui.graphics.Color.Blue,
                sortTime = 1L,
                secureItemId = 1L
            )
        )

        val filtered = filterPasswordAggregateItemsByQuickFilters(
            items = aggregateItems,
            currentFilter = CategoryFilter.All,
            configuredQuickFilterItems = PasswordListQuickFilterItem.DEFAULT_ORDER,
            quickFilterFavorite = false,
            quickFilter2fa = false,
            quickFilterNotes = false,
            quickFilterUncategorized = false,
            quickFilterLocalOnly = false,
            quickFilterManualStackOnly = false,
            quickFilterNeverStack = false,
            quickFilterUnstacked = false,
            quickFilterBarcode = true,
            effectiveStackCardMode = StackCardMode.AUTO
        )

        assertEquals(emptyList<PasswordAggregateListItemUi>(), filtered)
    }

    private fun passkey(id: Long, mdbxDatabaseId: Long?): PasskeyEntry {
        return PasskeyEntry(
            id = id,
            credentialId = "credential-$id",
            rpId = "example.com",
            rpName = "Example",
            userId = "user-$id",
            userName = "alice",
            userDisplayName = "Alice",
            publicKey = "public-key",
            privateKeyAlias = "alias-$id",
            mdbxDatabaseId = mdbxDatabaseId
        )
    }
}
