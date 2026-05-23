package takagi.ru.monica.repository

import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem

/**
 * Boundary for user-visible MDBX mutations.
 *
 * Implementations must only return after the local .mdbx working copy has
 * committed the mutation. External SAF/WebDAV propagation is a later sync
 * concern and must not be required for ordinary save success.
 */
interface MdbxRepository {
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
