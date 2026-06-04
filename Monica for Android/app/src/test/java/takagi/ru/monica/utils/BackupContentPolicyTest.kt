package takagi.ru.monica.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.isLocalOnlyItem

class BackupContentPolicyTest {
    @Test
    fun allOfflineScopeIncludesExternalPasswordsAndSanitizesThemAsLocalCopies() {
        val entries = listOf(
            password("local"),
            password("bitwarden", bitwardenVaultId = 1L, bitwardenCipherId = "cipher"),
            password("mdbx", mdbxDatabaseId = 2L, mdbxFolderId = "folder"),
            password("keepass", keepassDatabaseId = 3L, keepassEntryUuid = "entry")
        )

        val sanitized = entries
            .filter { BackupContentPolicy.shouldIncludePassword(it, BackupContentScope.ALL_OFFLINE) }
            .map(BackupContentPolicy::sanitizePasswordForMonicaBackup)

        assertEquals(4, sanitized.size)
        sanitized.forEach { entry ->
            assertTrue(entry.isLocalOnlyEntry())
            assertNull(entry.keepassDatabaseId)
            assertNull(entry.mdbxDatabaseId)
            assertNull(entry.bitwardenVaultId)
        }
    }

    @Test
    fun localOnlyScopeKeepsLegacyDetachedKeePassRepairBehavior() {
        val local = password("local")
        val detachedKeePass = password("detached", keepassDatabaseId = 3L, categoryId = 7L)
        val activeKeePass = password("keepass", keepassDatabaseId = 3L, keepassEntryUuid = "entry")
        val bitwarden = password("bitwarden", bitwardenVaultId = 1L, bitwardenCipherId = "cipher")

        val included = listOf(local, detachedKeePass, activeKeePass, bitwarden)
            .filter { BackupContentPolicy.shouldIncludePassword(it, BackupContentScope.MONICA_LOCAL_ONLY) }

        assertEquals(listOf("local", "detached"), included.map { it.title })
    }

    @Test
    fun allOfflineScopeIncludesExternalSecureItemsAndSanitizesThemAsLocalCopies() {
        val items = listOf(
            secureItem("local"),
            secureItem("bitwarden", bitwardenVaultId = 1L, bitwardenCipherId = "cipher"),
            secureItem("mdbx", mdbxDatabaseId = 2L, mdbxFolderId = "folder"),
            secureItem("keepass", keepassDatabaseId = 3L, keepassEntryUuid = "entry")
        )

        val sanitized = items
            .filter { BackupContentPolicy.shouldIncludeSecureItem(it, BackupContentScope.ALL_OFFLINE) }
            .map(BackupContentPolicy::sanitizeSecureItemForMonicaBackup)

        assertEquals(4, sanitized.size)
        sanitized.forEach { item ->
            assertTrue(item.isLocalOnlyItem())
            assertNull(item.keepassDatabaseId)
            assertNull(item.mdbxDatabaseId)
            assertNull(item.bitwardenVaultId)
            assertEquals("NONE", item.syncStatus)
        }
    }

    private fun password(
        title: String,
        keepassDatabaseId: Long? = null,
        keepassEntryUuid: String? = null,
        mdbxDatabaseId: Long? = null,
        mdbxFolderId: String? = null,
        bitwardenVaultId: Long? = null,
        bitwardenCipherId: String? = null,
        categoryId: Long? = null
    ): PasswordEntry {
        return PasswordEntry(
            title = title,
            website = "example.com",
            username = "user",
            password = "secret",
            keepassDatabaseId = keepassDatabaseId,
            keepassEntryUuid = keepassEntryUuid,
            mdbxDatabaseId = mdbxDatabaseId,
            mdbxFolderId = mdbxFolderId,
            bitwardenVaultId = bitwardenVaultId,
            bitwardenCipherId = bitwardenCipherId,
            categoryId = categoryId
        )
    }

    private fun secureItem(
        title: String,
        keepassDatabaseId: Long? = null,
        keepassEntryUuid: String? = null,
        mdbxDatabaseId: Long? = null,
        mdbxFolderId: String? = null,
        bitwardenVaultId: Long? = null,
        bitwardenCipherId: String? = null
    ): SecureItem {
        return SecureItem(
            itemType = ItemType.TOTP,
            title = title,
            itemData = "{}",
            keepassDatabaseId = keepassDatabaseId,
            keepassEntryUuid = keepassEntryUuid,
            mdbxDatabaseId = mdbxDatabaseId,
            mdbxFolderId = mdbxFolderId,
            bitwardenVaultId = bitwardenVaultId,
            bitwardenCipherId = bitwardenCipherId,
            syncStatus = "SYNCED"
        )
    }
}
