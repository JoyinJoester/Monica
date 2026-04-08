package takagi.ru.monica.bitwarden

import android.util.Log
import takagi.ru.monica.data.SecureItem

data class SecureItemBitwardenTransition(
    val cipherId: String?,
    val revisionDate: String?,
    val localModified: Boolean,
    val syncStatus: String
)

object SecureItemBitwardenTransitionResolver {
    suspend fun resolve(
        tag: String,
        existingItem: SecureItem?,
        targetVaultId: Long?,
        targetFolderId: String?,
        forcePendingWhenKeepingCipher: Boolean,
        abortOnQueueFailure: Boolean,
        queueDelete: suspend (vaultId: Long, cipherId: String, entryId: Long) -> Result<Unit>?
    ): SecureItemBitwardenTransition? {
        val previousVaultId = existingItem?.bitwardenVaultId
        val previousCipherId = existingItem?.bitwardenCipherId
        val hasExistingCipher = previousVaultId != null && !previousCipherId.isNullOrBlank()

        val leavingExistingCipher = hasExistingCipher && previousVaultId != targetVaultId
        if (leavingExistingCipher) {
            val queueResult = queueDelete(
                previousVaultId!!,
                previousCipherId!!,
                existingItem!!.id
            )
            if (queueResult?.isSuccess != true) {
                val errorMessage = queueResult?.exceptionOrNull()?.message ?: "Bitwarden repository unavailable"
                Log.e(tag, "Queue Bitwarden secure item delete failed: $errorMessage")
                if (abortOnQueueFailure) return null
            }
        }

        val keepExistingCipher = hasExistingCipher && previousVaultId == targetVaultId
        if (targetVaultId == null) {
            return SecureItemBitwardenTransition(
                cipherId = null,
                revisionDate = null,
                localModified = false,
                syncStatus = "NONE"
            )
        }

        if (!keepExistingCipher) {
            return SecureItemBitwardenTransition(
                cipherId = null,
                revisionDate = null,
                localModified = false,
                syncStatus = "PENDING"
            )
        }

        val folderChanged = existingItem?.bitwardenFolderId != targetFolderId
        val localModified =
            forcePendingWhenKeepingCipher ||
                folderChanged ||
                (existingItem?.bitwardenLocalModified == true)

        return SecureItemBitwardenTransition(
            cipherId = previousCipherId,
            revisionDate = existingItem?.bitwardenRevisionDate,
            localModified = localModified,
            syncStatus = if (localModified) "PENDING" else "SYNCED"
        )
    }
}
