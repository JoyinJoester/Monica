package takagi.ru.monica.keepass

import android.util.Log
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.repository.KeePassCompatibilityBridge

class KeePassPasswordDeleteExecutor(
    private val bridge: KeePassCompatibilityBridge?
) {
    private val recycleBinUnavailableDatabaseIds = mutableSetOf<Long>()

    suspend fun deleteBatch(entries: List<PasswordEntry>, useRecycleBin: Boolean): Boolean {
        if (entries.isEmpty()) return true
        val keepassBridge = bridge ?: return true

        return entries
            .groupBy { it.keepassDatabaseId }
            .all { (databaseId, groupedEntries) ->
                if (databaseId == null) {
                    true
                } else {
                    deleteBatchForDatabase(
                        bridge = keepassBridge,
                        databaseId = databaseId,
                        entries = groupedEntries,
                        useRecycleBin = useRecycleBin
                    )
                }
            }
    }

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
        val movedCount = moveToRecycleBin.getOrNull()
        if (movedCount != null && movedCount > 0) {
            return true
        }

        if (movedCount != null) {
            Log.w(
                TAG,
                "KeePass move to recycle bin affected 0 entries for db=$databaseId, fallback to direct delete"
            )
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

    private suspend fun deleteBatchForDatabase(
        bridge: KeePassCompatibilityBridge,
        databaseId: Long,
        entries: List<PasswordEntry>,
        useRecycleBin: Boolean
    ): Boolean {
        if (entries.isEmpty()) return true
        if (!useRecycleBin) {
            return runDirectDeleteBatch(bridge, databaseId, entries)
        }

        if (isRecycleBinUnavailable(databaseId)) {
            Log.i(
                TAG,
                "Skip recycle bin batch attempt for db=$databaseId because recycle bin is known unavailable"
            )
            return runDirectDeleteBatch(bridge, databaseId, entries)
        }

        val moveToRecycleBin = bridge.moveLegacyPasswordEntriesToRecycleBin(
            databaseId = databaseId,
            entries = entries
        )
        val movedCount = moveToRecycleBin.getOrNull()
        if (movedCount == entries.size) {
            return true
        }

        if (movedCount != null && movedCount > 0) {
            Log.e(
                TAG,
                "KeePass batch move to recycle bin partially succeeded for db=$databaseId: moved=$movedCount expected=${entries.size}"
            )
            return false
        }

        if (movedCount != null) {
            Log.w(
                TAG,
                "KeePass batch move to recycle bin affected 0 entries for db=$databaseId, fallback to direct delete"
            )
        }

        val failureMessage = moveToRecycleBin.exceptionOrNull()?.message.orEmpty()
        if (failureMessage.contains(RECYCLE_BIN_UNAVAILABLE, ignoreCase = true)) {
            rememberRecycleBinUnavailable(databaseId)
        }
        Log.w(
            TAG,
            "KeePass batch move to recycle bin failed, fallback to direct delete: $failureMessage"
        )
        return runDirectDeleteBatch(bridge, databaseId, entries)
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
        val deletedCount = directDelete.getOrNull() ?: 0
        if (deletedCount <= 0) {
            Log.e(TAG, "KeePass delete affected 0 entries for db=$databaseId")
            return false
        }
        return true
    }

    private suspend fun runDirectDeleteBatch(
        bridge: KeePassCompatibilityBridge,
        databaseId: Long,
        entries: List<PasswordEntry>
    ): Boolean {
        val directDelete = bridge.deleteLegacyPasswordEntries(
            databaseId = databaseId,
            entries = entries
        )
        if (directDelete.isFailure) {
            Log.e(TAG, "KeePass batch delete failed: ${directDelete.exceptionOrNull()?.message}")
            return false
        }
        val deletedCount = directDelete.getOrNull() ?: 0
        if (deletedCount != entries.size) {
            Log.e(
                TAG,
                "KeePass batch delete count mismatch for db=$databaseId: deleted=$deletedCount expected=${entries.size}"
            )
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