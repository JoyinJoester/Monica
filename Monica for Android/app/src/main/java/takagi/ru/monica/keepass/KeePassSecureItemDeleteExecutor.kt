package takagi.ru.monica.keepass

import android.util.Log
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.repository.KeePassCompatibilityBridge

class KeePassSecureItemDeleteExecutor(
    private val bridge: KeePassCompatibilityBridge?
) {
    private val recycleBinUnavailableDatabaseIds = mutableSetOf<Long>()

    suspend fun delete(item: SecureItem, useRecycleBin: Boolean): Boolean {
        val databaseId = item.keepassDatabaseId ?: return true
        val keepassBridge = bridge ?: return true

        if (!useRecycleBin) {
            return runDirectDelete(keepassBridge, databaseId, item)
        }

        if (isRecycleBinUnavailable(databaseId)) {
            Log.i(
                TAG,
                "Skip recycle bin attempt for db=$databaseId because recycle bin is known unavailable"
            )
            return runDirectDelete(keepassBridge, databaseId, item)
        }

        val moveToRecycleBin = keepassBridge.moveLegacySecureItemsToRecycleBin(
            databaseId = databaseId,
            items = listOf(item)
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
        return runDirectDelete(keepassBridge, databaseId, item)
    }

    private suspend fun runDirectDelete(
        bridge: KeePassCompatibilityBridge,
        databaseId: Long,
        item: SecureItem
    ): Boolean {
        val directDelete = bridge.deleteLegacySecureItems(
            databaseId = databaseId,
            items = listOf(item)
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

    private fun isRecycleBinUnavailable(databaseId: Long): Boolean = synchronized(recycleBinUnavailableDatabaseIds) {
        recycleBinUnavailableDatabaseIds.contains(databaseId)
    }

    private fun rememberRecycleBinUnavailable(databaseId: Long) {
        synchronized(recycleBinUnavailableDatabaseIds) {
            recycleBinUnavailableDatabaseIds += databaseId
        }
    }

    private companion object {
        const val TAG = "KeePassSecureDelete"
        const val RECYCLE_BIN_UNAVAILABLE = "recycle bin unavailable"
    }
}