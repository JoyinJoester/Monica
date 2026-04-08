package takagi.ru.monica.bitwarden

import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem

enum class BitwardenRestoreQueueOutcome {
    NO_REMOTE_ACTION,
    CANCELED_PENDING_DELETE,
    ENQUEUED_REMOTE_RESTORE,
    REMOTE_RESTORE_ALREADY_QUEUED
}

object BitwardenTrashRestoreStateHelper {

    fun applyToPasswordEntry(
        candidate: PasswordEntry,
        restoreOutcome: BitwardenRestoreQueueOutcome
    ): PasswordEntry {
        return candidate.copy(
            bitwardenLocalModified = resolveLocalModified(
                hasBitwardenVault = candidate.bitwardenVaultId != null,
                hasRemoteCipher = !candidate.bitwardenCipherId.isNullOrBlank(),
                currentValue = candidate.bitwardenLocalModified,
                restoreOutcome = restoreOutcome
            )
        )
    }

    fun applyToSecureItem(
        candidate: SecureItem,
        restoreOutcome: BitwardenRestoreQueueOutcome
    ): SecureItem {
        val localModified = resolveLocalModified(
            hasBitwardenVault = candidate.bitwardenVaultId != null,
            hasRemoteCipher = !candidate.bitwardenCipherId.isNullOrBlank(),
            currentValue = candidate.bitwardenLocalModified,
            restoreOutcome = restoreOutcome
        )
        return candidate.copy(
            bitwardenLocalModified = localModified,
            syncStatus = resolveSyncStatus(
                currentValue = candidate.syncStatus,
                localModified = localModified
            )
        )
    }

    private fun resolveLocalModified(
        hasBitwardenVault: Boolean,
        hasRemoteCipher: Boolean,
        currentValue: Boolean,
        restoreOutcome: BitwardenRestoreQueueOutcome
    ): Boolean {
        if (!hasBitwardenVault) return false
        if (!hasRemoteCipher) return true

        return when (restoreOutcome) {
            BitwardenRestoreQueueOutcome.CANCELED_PENDING_DELETE -> false
            BitwardenRestoreQueueOutcome.ENQUEUED_REMOTE_RESTORE,
            BitwardenRestoreQueueOutcome.REMOTE_RESTORE_ALREADY_QUEUED -> true
            BitwardenRestoreQueueOutcome.NO_REMOTE_ACTION -> currentValue
        }
    }

    private fun resolveSyncStatus(
        currentValue: String,
        localModified: Boolean
    ): String {
        if (currentValue == "REFERENCE") return "REFERENCE"
        return if (localModified) "PENDING" else currentValue
    }
}
