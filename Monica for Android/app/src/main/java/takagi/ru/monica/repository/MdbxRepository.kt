package takagi.ru.monica.repository

import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem

/**
 * Boundary for user-visible MDBX mutations.
 *
 * Implementations must commit the local .mdbx working copy and then publish
 * that working copy to the configured SAF/WebDAV source before reporting
 * success. This keeps a second client able to see new MDBX items with its
 * next manual sync instead of requiring a manual sync on the writer first.
 */
interface MdbxRepository {
    suspend fun createFolder(
        databaseId: Long,
        name: String,
        parentFolderId: String? = "root"
    ): MdbxStoredFolderEntry

    suspend fun listFolders(databaseId: Long): List<MdbxStoredFolderEntry>

    suspend fun upsertPassword(entry: PasswordEntry)
    suspend fun deletePassword(entry: PasswordEntry)
    suspend fun upsertPasswords(entries: List<PasswordEntry>) {
        entries.forEach { upsertPassword(it) }
    }
    suspend fun deletePasswords(entries: List<PasswordEntry>) {
        entries.forEach { deletePassword(it) }
    }

    suspend fun upsertSecureItem(item: SecureItem)
    suspend fun deleteSecureItem(item: SecureItem)
    suspend fun upsertSecureItems(items: List<SecureItem>) {
        items.forEach { upsertSecureItem(it) }
    }
    suspend fun deleteSecureItems(items: List<SecureItem>) {
        items.forEach { deleteSecureItem(it) }
    }

    suspend fun upsertPasskey(passkey: PasskeyEntry)
    suspend fun deletePasskey(passkey: PasskeyEntry)
    suspend fun upsertPasskeys(passkeys: List<PasskeyEntry>) {
        passkeys.forEach { upsertPasskey(it) }
    }
    suspend fun deletePasskeys(passkeys: List<PasskeyEntry>) {
        passkeys.forEach { deletePasskey(it) }
    }
}
