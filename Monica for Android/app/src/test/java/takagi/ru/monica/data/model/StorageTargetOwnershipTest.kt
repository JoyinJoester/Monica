package takagi.ru.monica.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.PasswordEntry

class StorageTargetOwnershipTest {

    @Test
    fun applyingMonicaLocalTargetClearsMdbxOwnership() {
        val source = mdbxPasswordEntry()

        val local = StorageTarget.MonicaLocal(categoryId = 42L)
            .applyToPasswordEntry(source, replicaGroupId = null)

        assertEquals(42L, local.categoryId)
        assertNull(local.mdbxDatabaseId)
        assertNull(local.mdbxFolderId)
        assertNull(local.replicaGroupId)
        assertTrue(local.isLocalOnlyEntry())
    }

    @Test
    fun applyingExternalNonMdbxTargetsClearsMdbxOwnership() {
        val source = mdbxPasswordEntry()

        val keepass = StorageTarget.KeePass(databaseId = 9L, groupPath = "Root")
            .applyToPasswordEntry(source, replicaGroupId = "group")
        val bitwarden = StorageTarget.Bitwarden(vaultId = 8L, folderId = "folder")
            .applyToPasswordEntry(source, replicaGroupId = "group")

        assertNull(keepass.mdbxDatabaseId)
        assertNull(keepass.mdbxFolderId)
        assertEquals(9L, keepass.keepassDatabaseId)
        assertNull(bitwarden.mdbxDatabaseId)
        assertNull(bitwarden.mdbxFolderId)
        assertEquals(8L, bitwarden.bitwardenVaultId)
    }

    private fun mdbxPasswordEntry(): PasswordEntry {
        return PasswordEntry(
            id = 1L,
            title = "Example",
            website = "example.com",
            username = "alice",
            password = "secret",
            mdbxDatabaseId = 7L,
            mdbxFolderId = "folder",
            replicaGroupId = "password:source"
        )
    }
}
