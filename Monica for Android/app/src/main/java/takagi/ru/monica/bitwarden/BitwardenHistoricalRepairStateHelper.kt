package takagi.ru.monica.bitwarden

import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem

object BitwardenHistoricalRepairStateHelper {
    fun applyToSecureItem(
        candidate: SecureItem,
        shouldQueueRemoteRewrite: Boolean
    ): SecureItem {
        if (!shouldQueueRemoteRewrite || candidate.bitwardenCipherId.isNullOrBlank()) {
            return candidate
        }
        return candidate.copy(
            bitwardenLocalModified = true,
            syncStatus = if (candidate.syncStatus == "REFERENCE") "REFERENCE" else "PENDING"
        )
    }

    fun applyToPasswordEntry(
        candidate: PasswordEntry,
        shouldQueueRemoteRewrite: Boolean
    ): PasswordEntry {
        if (!shouldQueueRemoteRewrite || candidate.bitwardenCipherId.isNullOrBlank()) {
            return candidate
        }
        return candidate.copy(bitwardenLocalModified = true)
    }
}
