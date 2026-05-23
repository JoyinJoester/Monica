package takagi.ru.monica.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordOwnershipTest {

    @Test
    fun mdbxPasswordIsNotLocalOnly() {
        val entry = PasswordEntry(
            title = "MDBX",
            website = "example.com",
            username = "alice",
            password = "secret",
            mdbxDatabaseId = 7L
        )

        assertTrue(entry.isMdbxEntry())
        assertFalse(entry.isLocalOnlyEntry())
    }

    @Test
    fun localOwnershipHelperTreatsMdbxAsExternalStorage() {
        assertFalse(
            isLocalPasswordOwnership(
                keepassDatabaseId = null,
                bitwardenVaultId = null,
                mdbxDatabaseId = 7L
            )
        )
    }
}
