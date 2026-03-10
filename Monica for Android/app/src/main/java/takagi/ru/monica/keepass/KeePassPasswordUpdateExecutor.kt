package takagi.ru.monica.keepass

import android.util.Log
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.repository.KeePassCompatibilityBridge

class KeePassPasswordUpdateExecutor(
    private val bridge: KeePassCompatibilityBridge?
) {
    suspend fun syncUpdatedEntry(
        existingEntry: PasswordEntry?,
        updatedEntry: PasswordEntry,
        resolvePassword: (PasswordEntry) -> String
    ) {
        val keepassBridge = bridge ?: return
        val oldKeepassId = existingEntry?.keepassDatabaseId
        val newKeepassId = updatedEntry.keepassDatabaseId

        if (oldKeepassId != null && oldKeepassId != newKeepassId) {
            val deleteResult = keepassBridge.deleteLegacyPasswordEntries(
                databaseId = oldKeepassId,
                entries = listOf(updatedEntry.copy(keepassDatabaseId = oldKeepassId))
            )
            if (deleteResult.isFailure) {
                Log.e(TAG, "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
            }
        }

        if (newKeepassId != null) {
            val updateResult = keepassBridge.updateLegacyPasswordEntry(
                databaseId = newKeepassId,
                entry = updatedEntry,
                resolvePassword = resolvePassword
            )
            if (updateResult.isFailure) {
                Log.e(TAG, "KeePass update failed: ${updateResult.exceptionOrNull()?.message}")
            }
        }
    }

    private companion object {
        const val TAG = "KeePassPasswordUpdate"
    }
}