package takagi.ru.monica.bitwarden.sync

import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.sync.SyncDiagnostics
import takagi.ru.monica.sync.SyncError
import takagi.ru.monica.sync.SyncErrorKind
import takagi.ru.monica.sync.SyncMode
import takagi.ru.monica.sync.SyncNetworkPolicy
import takagi.ru.monica.sync.SyncPriority
import takagi.ru.monica.sync.SyncRequest
import takagi.ru.monica.sync.SyncTarget
import takagi.ru.monica.sync.SyncTaskAwaitResult
import takagi.ru.monica.sync.SyncTaskRunner
import takagi.ru.monica.sync.SyncTrigger

sealed class BitwardenCoordinatedSyncResult {
    data class Completed(val result: BitwardenRepository.SyncResult) : BitwardenCoordinatedSyncResult()
    data object Merged : BitwardenCoordinatedSyncResult()
    data class Skipped(val reason: String) : BitwardenCoordinatedSyncResult()
    data class Blocked(val error: SyncError) : BitwardenCoordinatedSyncResult()
    data class Canceled(val reason: String?) : BitwardenCoordinatedSyncResult()
    data class Failed(val error: Exception) : BitwardenCoordinatedSyncResult()
}

suspend fun BitwardenRepository.syncViaCoordinator(
    vaultId: Long,
    requestIdPrefix: String,
    trigger: SyncTrigger,
    priority: SyncPriority = SyncPriority.forTrigger(trigger),
    mode: SyncMode = SyncMode.BACKGROUND,
    networkPolicy: SyncNetworkPolicy = if (isSyncOnWifiOnly) {
        SyncNetworkPolicy.WIFI_ONLY
    } else {
        SyncNetworkPolicy.REQUIRED
    },
    requiresUnlockedTarget: Boolean = true
): BitwardenCoordinatedSyncResult {
    val request = SyncRequest(
        requestId = SyncDiagnostics.nextTaskId(requestIdPrefix),
        target = SyncTarget.BitwardenVault(vaultId),
        trigger = trigger,
        createdAtMillis = System.currentTimeMillis(),
        priority = priority,
        mode = mode,
        networkPolicy = networkPolicy,
        requiresUnlockedTarget = requiresUnlockedTarget
    )

    @Suppress("DEPRECATION")
    return when (val result = SyncTaskRunner.requestAndAwait(request) { sync(vaultId) }) {
        is SyncTaskAwaitResult.Completed -> BitwardenCoordinatedSyncResult.Completed(result.value)
        is SyncTaskAwaitResult.Merged -> BitwardenCoordinatedSyncResult.Merged
        is SyncTaskAwaitResult.Skipped -> BitwardenCoordinatedSyncResult.Skipped(result.reason)
        is SyncTaskAwaitResult.Blocked -> BitwardenCoordinatedSyncResult.Blocked(result.error)
        is SyncTaskAwaitResult.Canceled -> BitwardenCoordinatedSyncResult.Canceled(result.reason)
        is SyncTaskAwaitResult.Failed -> BitwardenCoordinatedSyncResult.Failed(result.error)
    }
}

suspend fun BitwardenRepository.syncForUserVisibleRequest(
    vaultId: Long,
    requestIdPrefix: String
): BitwardenRepository.SyncResult {
    return syncViaCoordinator(
        vaultId = vaultId,
        requestIdPrefix = requestIdPrefix,
        trigger = SyncTrigger.MANUAL,
        priority = SyncPriority.MANUAL,
        mode = SyncMode.FOREGROUND
    ).toRepositorySyncResultForUi()
}

fun BitwardenCoordinatedSyncResult.toRepositorySyncResultForUi(): BitwardenRepository.SyncResult {
    return when (this) {
        is BitwardenCoordinatedSyncResult.Completed -> result
        BitwardenCoordinatedSyncResult.Merged -> emptyBitwardenSyncSuccess()
        is BitwardenCoordinatedSyncResult.Skipped -> emptyBitwardenSyncSuccess()
        is BitwardenCoordinatedSyncResult.Blocked -> {
            BitwardenRepository.SyncResult.Error(error.redactedMessage ?: error.kind.name)
        }
        is BitwardenCoordinatedSyncResult.Canceled -> {
            BitwardenRepository.SyncResult.Error(reason ?: "同步被取消")
        }
        is BitwardenCoordinatedSyncResult.Failed -> {
            BitwardenRepository.SyncResult.Error(error.message ?: "同步失败")
        }
    }
}

fun SyncError.toBitwardenBlockReason(): SyncBlockReason {
    return when (kind) {
        SyncErrorKind.NETWORK_UNAVAILABLE -> SyncBlockReason.NETWORK_UNAVAILABLE
        SyncErrorKind.WIFI_REQUIRED -> SyncBlockReason.WIFI_REQUIRED
        SyncErrorKind.AUTH_REQUIRED -> SyncBlockReason.AUTH_REQUIRED
        SyncErrorKind.TARGET_LOCKED -> SyncBlockReason.VAULT_LOCKED
        else -> SyncBlockReason.NETWORK_UNAVAILABLE
    }
}

private fun emptyBitwardenSyncSuccess(): BitwardenRepository.SyncResult.Success {
    return BitwardenRepository.SyncResult.Success(
        appliedChangeCount = 0,
        remoteAddedCount = 0,
        remoteUpdatedCount = 0,
        uploadedCount = 0,
        deletedCount = 0,
        availableOfflineCount = 0,
        conflictCount = 0,
        uploadFailedCount = 0,
        skippedDueToLocalDirtyCount = 0
    )
}
