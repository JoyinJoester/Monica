package takagi.ru.monica.keepass

import android.util.Log
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.repository.KeePassCompatibilityBridge

class KeePassSecureItemUpdateExecutor(
    private val bridge: KeePassCompatibilityBridge?
) {
    suspend fun syncUpdatedItem(
        existingItem: SecureItem?,
        updatedItem: SecureItem
    ) {
        val keepassBridge = bridge ?: return
        val oldKeepassId = existingItem?.keepassDatabaseId
        val newKeepassId = updatedItem.keepassDatabaseId

        if (oldKeepassId != null && oldKeepassId != newKeepassId) {
            val deleteResult = keepassBridge.deleteLegacySecureItems(oldKeepassId, listOf(existingItem))
            if (deleteResult.isFailure) {
                Log.e(TAG, "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
            }
        }

        if (newKeepassId != null) {
            val updateResult = keepassBridge.updateLegacySecureItem(newKeepassId, updatedItem)
            if (updateResult.isFailure) {
                Log.e(TAG, "KeePass update failed: ${updateResult.exceptionOrNull()?.message}")
            }
        }
    }

    private companion object {
        const val TAG = "KeePassSecureUpdate"
    }
}