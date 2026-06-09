package takagi.ru.monica.bitwarden.sync

import takagi.ru.monica.sync.SyncPhase
import takagi.ru.monica.sync.SyncTarget
import takagi.ru.monica.sync.SyncTaskStatus
import takagi.ru.monica.sync.SyncTrigger

fun mergeBitwardenSyncStatuses(
    orchestratorStatuses: Map<Long, VaultSyncStatus>,
    coordinatorStatuses: Collection<SyncTaskStatus>
): Map<Long, VaultSyncStatus> {
    val merged = orchestratorStatuses.toMutableMap()
    coordinatorStatuses.forEach { status ->
        val vaultId = (status.target as? SyncTarget.BitwardenVault)?.vaultId ?: return@forEach
        merged[vaultId] = merged[vaultId].mergeCoordinatorStatus(status)
    }
    return merged
}

private fun VaultSyncStatus?.mergeCoordinatorStatus(status: SyncTaskStatus): VaultSyncStatus {
    val existing = this ?: VaultSyncStatus()
    val coordinatorTrigger = status.lastTrigger?.toBitwardenSyncTriggerReason()
    val coordinatorRunning = status.phase == SyncPhase.RUNNING
    val coordinatorQueued = status.queuedCount > 0
    val lastSuccessAt = listOfNotNull(existing.lastSuccessAt, status.lastSuccessAtMillis).maxOrNull()

    return when (status.phase) {
        SyncPhase.RUNNING,
        SyncPhase.QUEUED -> existing.copy(
            isRunning = existing.isRunning || coordinatorRunning,
            queuedReason = when {
                existing.queuedReason != null -> existing.queuedReason
                coordinatorQueued -> coordinatorTrigger
                status.phase == SyncPhase.QUEUED -> coordinatorTrigger
                else -> null
            },
            lastTriggerReason = coordinatorTrigger ?: existing.lastTriggerReason,
            blockedReason = null,
            lastError = null,
            lastSuccessAt = lastSuccessAt,
            nextRetryAt = existing.nextRetryAt
        )

        SyncPhase.SUCCESS -> existing.copy(
            isRunning = existing.isRunning,
            queuedReason = existing.queuedReason,
            lastSuccessAt = lastSuccessAt,
            nextRetryAt = existing.nextRetryAt
        )

        SyncPhase.BLOCKED -> existing.copy(
            isRunning = existing.isRunning,
            queuedReason = existing.queuedReason,
            blockedReason = status.lastError?.toBitwardenBlockReason() ?: existing.blockedReason,
            lastError = status.lastError?.redactedMessage ?: existing.lastError,
            lastTriggerReason = coordinatorTrigger ?: existing.lastTriggerReason,
            lastSuccessAt = lastSuccessAt
        )

        SyncPhase.FAILED,
        SyncPhase.CONFLICT -> existing.copy(
            isRunning = existing.isRunning,
            queuedReason = existing.queuedReason,
            lastError = status.lastError?.redactedMessage ?: existing.lastError,
            lastTriggerReason = coordinatorTrigger ?: existing.lastTriggerReason,
            lastSuccessAt = lastSuccessAt
        )

        SyncPhase.CANCELED,
        SyncPhase.IDLE -> existing.copy(
            isRunning = existing.isRunning,
            queuedReason = existing.queuedReason,
            lastSuccessAt = lastSuccessAt
        )
    }
}

private fun SyncTrigger.toBitwardenSyncTriggerReason(): SyncTriggerReason {
    return when (this) {
        SyncTrigger.MANUAL -> SyncTriggerReason.MANUAL
        SyncTrigger.PAGE_VISIBLE -> SyncTriggerReason.PAGE_ENTER
        SyncTrigger.APP_START,
        SyncTrigger.APP_RESUME -> SyncTriggerReason.APP_RESUME
        SyncTrigger.LOCAL_MUTATION,
        SyncTrigger.AUTOFILL_SAVE -> SyncTriggerReason.LOCAL_MUTATION
        SyncTrigger.RETRY -> SyncTriggerReason.RETRY
        SyncTrigger.WORKER_RECOVERY,
        SyncTrigger.BACKUP_SCHEDULE -> SyncTriggerReason.PERIODIC
        SyncTrigger.RESTORE,
        SyncTrigger.DELETE,
        SyncTrigger.REMOTE_NOTIFICATION,
        SyncTrigger.FILE_CHANGED -> SyncTriggerReason.LOCAL_MUTATION
    }
}
