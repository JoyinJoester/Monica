package takagi.ru.monica.keepass

import android.util.Log
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.repository.KeePassCompatibilityBridge

class KeePassPasswordDeleteExecutor(
    private val bridge: KeePassCompatibilityBridge?
) {
    private val recycleBinUnavailableDatabaseIds = mutableSetOf<Long>()

    suspend fun delete(entry: PasswordEntry, useRecycleBin: Boolean): Boolean {
        val databaseId = entry.keepassDatabaseId ?: return true
        val keepassBridge = bridge ?: return true

        if (!useRecycleBin) {
            return runDirectDelete(keepassBridge, databaseId, entry)
        }

        if (isRecycleBinUnavailable(databaseId)) {
            Log.i(
                TAG,
                "Skip recycle bin attempt for db=$databaseId because recycle bin is known unavailable"
            )
            return runDirectDelete(keepassBridge, databaseId, entry)
        }

        val moveToRecycleBin = keepassBridge.moveLegacyPasswordEntriesToRecycleBin(
            databaseId = databaseId,
            entries = listOf(entry)
        )
        if (moveToRecycleBin.isSuccess) {
            return true
        }

        val failureMessage = moveToRecycleBin.exceptionOrNull()?.message.orEmpty()
        if (failureMessage.contains(RECYCLE_BIN_UNAVAILABLE, ignoreCase = true)) {
            rememberRecycleBinUnavailable(databaseId)
        }
        Log.w(
            TAG,
            "KeePass move to recycle bin failed, fallback to direct delete: $failureMessage"
        )
        return runDirectDelete(keepassBridge, databaseId, entry)
    }

    private suspend fun runDirectDelete(
        bridge: KeePassCompatibilityBridge,
        databaseId: Long,
        entry: PasswordEntry
    ): Boolean {
        val directDelete = bridge.deleteLegacyPasswordEntries(
            databaseId = databaseId,
            entries = listOf(entry)
        )
        if (directDelete.isFailure) {
            Log.e(TAG, "KeePass delete failed: ${directDelete.exceptionOrNull()?.message}")
            return false
        }
        return true
    }

    private fun isRecycleBinUnavailable(databaseId: Long): Boolean = synchronized(recycleBinUnavailableDatabaseIds) {
        recycleBinUnavailableDatabaseIds.contains(databaseId)
    }

    private fun rememberRecycleBinUnavailable(databaseId: Long) {
        synchronized(recycleBinUnavailableDatabaseIds) {
            recycleBinUnavailableDatabaseIds += databaseId
        }
    }

    private companion object {
        const val TAG = "KeePassPasswordDelete"
        const val RECYCLE_BIN_UNAVAILABLE = "recycle bin unavailable"
    }
}