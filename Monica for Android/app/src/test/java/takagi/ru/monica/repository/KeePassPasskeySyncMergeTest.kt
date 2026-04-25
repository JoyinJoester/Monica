package takagi.ru.monica.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.data.PasskeyEntry

class KeePassPasskeySyncMergeTest {

    @Test
    fun mergeKeePassImportedPasskeys_preservesExistingRecordIdAndMetadata() {
        val existing = passkey(
            id = 42L,
            credentialId = "credential-a",
            boundPasswordId = 7L,
            categoryId = 9L,
            isBackedUp = true,
            lastUsedAt = 10L
        )
        val imported = passkey(
            id = 0L,
            credentialId = "credential-a",
            userName = "fresh-user",
            lastUsedAt = 20L
        )

        val result = mergeKeePassImportedPasskeys(
            databaseId = 1L,
            importedPasskeys = listOf(imported),
            existingPasskeys = listOf(existing)
        )

        assertEquals(emptyList<Long>(), result.staleRecordIds)
        assertEquals(1, result.mergedPasskeys.size)
        with(result.mergedPasskeys.single()) {
            assertEquals(42L, id)
            assertEquals("fresh-user", userName)
            assertEquals(7L, boundPasswordId)
            assertEquals(9L, categoryId)
            assertEquals(true, isBackedUp)
        }
    }

    @Test
    fun mergeKeePassImportedPasskeys_collapsesRepeatedImportedAndExistingRows() {
        val olderExisting = passkey(id = 11L, credentialId = "credential-a", lastUsedAt = 10L)
        val newerExisting = passkey(id = 12L, credentialId = "credential-a", lastUsedAt = 30L)
        val olderImported = passkey(id = 0L, credentialId = "credential-a", userName = "old", lastUsedAt = 20L)
        val newerImported = passkey(id = 0L, credentialId = "credential-a", userName = "new", lastUsedAt = 40L)

        val result = mergeKeePassImportedPasskeys(
            databaseId = 1L,
            importedPasskeys = listOf(olderImported, newerImported),
            existingPasskeys = listOf(olderExisting, newerExisting)
        )

        assertEquals(listOf(11L), result.staleRecordIds)
        assertEquals(1, result.mergedPasskeys.size)
        with(result.mergedPasskeys.single()) {
            assertEquals(12L, id)
            assertEquals("new", userName)
            assertEquals("credential-a", credentialId)
        }
    }

    private fun passkey(
        id: Long,
        credentialId: String,
        userName: String = "user",
        boundPasswordId: Long? = null,
        categoryId: Long? = null,
        isBackedUp: Boolean = false,
        lastUsedAt: Long = 1L
    ): PasskeyEntry {
        return PasskeyEntry(
            id = id,
            credentialId = credentialId,
            rpId = "telegram.org",
            rpName = "Telegram Messenger",
            userId = "user-id",
            userName = userName,
            userDisplayName = userName,
            publicKey = "public-key",
            privateKeyAlias = "private-key",
            createdAt = 1L,
            lastUsedAt = lastUsedAt,
            isBackedUp = isBackedUp,
            boundPasswordId = boundPasswordId,
            categoryId = categoryId,
            keepassDatabaseId = 1L,
            passkeyMode = PasskeyEntry.MODE_KEEPASS_COMPAT
        )
    }
}
