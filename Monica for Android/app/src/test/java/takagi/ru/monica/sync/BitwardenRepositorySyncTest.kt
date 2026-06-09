package takagi.ru.monica.sync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.bitwarden.sync.BitwardenCoordinatedSyncResult
import takagi.ru.monica.bitwarden.sync.SyncBlockReason
import takagi.ru.monica.bitwarden.sync.SyncTriggerReason
import takagi.ru.monica.bitwarden.sync.VaultSyncStatus
import takagi.ru.monica.bitwarden.sync.mergeBitwardenSyncStatuses
import takagi.ru.monica.bitwarden.sync.toBitwardenBlockReason
import takagi.ru.monica.bitwarden.sync.toRepositorySyncResultForUi

class BitwardenRepositorySyncTest {

    @Test
    fun mergedCoordinatorResultMapsToEmptySuccessForUi() {
        val result = BitwardenCoordinatedSyncResult.Merged.toRepositorySyncResultForUi()

        assertTrue(result is BitwardenRepository.SyncResult.Success)
        result as BitwardenRepository.SyncResult.Success
        assertEquals(0, result.appliedChangeCount)
        assertEquals(0, result.uploadedCount)
        assertEquals(0, result.conflictCount)
    }

    @Test
    fun blockedCoordinatorResultKeepsRedactedMessageForUi() {
        val result = BitwardenCoordinatedSyncResult.Blocked(
            SyncError(
                kind = SyncErrorKind.WIFI_REQUIRED,
                redactedMessage = "Wi-Fi required",
                retryable = true
            )
        ).toRepositorySyncResultForUi()

        assertTrue(result is BitwardenRepository.SyncResult.Error)
        assertEquals("Wi-Fi required", (result as BitwardenRepository.SyncResult.Error).message)
    }

    @Test
    fun syncErrorMapsToBitwardenBlockReason() {
        val error = SyncError(kind = SyncErrorKind.TARGET_LOCKED)

        assertEquals(SyncBlockReason.VAULT_LOCKED, error.toBitwardenBlockReason())
    }

    @Test
    fun coordinatorRunningStatusMarksVaultVisibleSyncing() {
        val merged = mergeBitwardenSyncStatuses(
            orchestratorStatuses = emptyMap(),
            coordinatorStatuses = listOf(
                SyncTaskStatus(
                    key = SyncKey("bitwarden:42"),
                    target = SyncTarget.BitwardenVault(42),
                    phase = SyncPhase.RUNNING,
                    queuedCount = 1,
                    lastTrigger = SyncTrigger.MANUAL
                )
            )
        )

        val status = merged.getValue(42)
        assertTrue(status.isRunning)
        assertEquals(SyncTriggerReason.MANUAL, status.queuedReason)
        assertEquals(SyncTriggerReason.MANUAL, status.lastTriggerReason)
    }

    @Test
    fun coordinatorBlockedStatusMapsToVaultBlockReason() {
        val merged = mergeBitwardenSyncStatuses(
            orchestratorStatuses = mapOf(7L to VaultSyncStatus(lastSuccessAt = 100L)),
            coordinatorStatuses = listOf(
                SyncTaskStatus(
                    key = SyncKey("bitwarden:7"),
                    target = SyncTarget.BitwardenVault(7),
                    phase = SyncPhase.BLOCKED,
                    lastTrigger = SyncTrigger.MANUAL,
                    lastError = SyncError(
                        kind = SyncErrorKind.WIFI_REQUIRED,
                        redactedMessage = "Wi-Fi required",
                        retryable = true
                    )
                )
            )
        )

        val status = merged.getValue(7)
        assertEquals(SyncBlockReason.WIFI_REQUIRED, status.blockedReason)
        assertEquals("Wi-Fi required", status.lastError)
        assertEquals(100L, status.lastSuccessAt)
    }

    @Test
    fun legacySendRefreshApiStaysDeprecatedBecauseItBypassesCoordinator() {
        val repositorySource = projectFile(
            "app/src/main/java/takagi/ru/monica/bitwarden/repository/BitwardenRepository.kt"
        ).readText()
        val repositorySyncSource = projectFile(
            "app/src/main/java/takagi/ru/monica/bitwarden/sync/BitwardenRepositorySync.kt"
        ).readText()
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/bitwarden/viewmodel/BitwardenViewModel.kt"
        ).readText()
        val syncSection = repositorySource
            .substringAfter("@Deprecated(")
            .substringBefore("syncMutexForVault(vaultId)")
        val refreshSendsSection = repositorySource
            .substringBefore("suspend fun createTextSend(")
            .substringAfter("@Deprecated(")

        assertTrue(
            "BitwardenRepository.sync is the bare executor body and must stay deprecated so UI/worker code uses syncViaCoordinator.",
            syncSection.contains("suspend fun sync(vaultId: Long)") &&
                syncSection.contains("syncViaCoordinator") &&
                repositorySyncSource.contains("@Suppress(\"DEPRECATION\")") &&
                repositorySyncSource.contains("SyncTaskRunner.requestAndAwait(request) { sync(vaultId) }")
        )
        assertTrue(
            "BitwardenRepository.refreshSends performs a bare sync and must stay deprecated so Send UI uses the coordinator path.",
            refreshSendsSection.contains("suspend fun refreshSends(") &&
                refreshSendsSection.contains("sync(vaultId)") &&
                refreshSendsSection.contains("syncViaCoordinator")
        )
        assertTrue(
            "Send refresh should use the coordinator-backed ViewModel path.",
            viewModelSource.contains("private suspend fun refreshSendsViaCoordinator(") &&
                viewModelSource.contains("repository.syncViaCoordinator(")
        )
    }

    @Test
    fun bitwardenFallbackWorkerDoesNotReplaceRunningMutationSyncWork() {
        val workerSource = projectFile(
            "app/src/main/java/takagi/ru/monica/bitwarden/sync/BitwardenSyncWorker.kt"
        ).readText()
        val triggerImmediateSyncBody = workerSource
            .substringAfter("fun triggerImmediateSync(")
            .substringBefore("override suspend fun doWork()")

        assertTrue(
            "Bitwarden mutation fallback worker must coalesce with an existing one-time work instead of replacing/canceling a running sync.",
            triggerImmediateSyncBody.contains("ExistingWorkPolicy.KEEP")
        )
        assertTrue(
            "Bitwarden one-time worker replacement can cancel the CoroutineWorker while SyncTaskRunner still owns the real sync; do not use REPLACE here.",
            !triggerImmediateSyncBody.contains("ExistingWorkPolicy.REPLACE")
        )
    }

    @Test
    fun bitwardenWorkerKeepsCoordinatorBlockedSeparateFromGenericFailure() {
        val workerSource = projectFile(
            "app/src/main/java/takagi/ru/monica/bitwarden/sync/BitwardenSyncWorker.kt"
        ).readText()
        val doWorkBody = workerSource
            .substringAfter("override suspend fun doWork(): Result")
            .substringBefore("private suspend fun syncVault(")
        val syncVaultBody = workerSource
            .substringAfter("private suspend fun syncVault(")
            .substringBefore("private suspend fun syncAllUnlockedVaults()")

        assertTrue(
            "Bitwarden Worker must log coordinator/network blocks as blocked, not as generic failed syncs.",
            doWorkBody.contains("catch (e: BitwardenWorkerBlockedException)") &&
                doWorkBody.contains("SyncDiagnostics.blocked(") &&
                doWorkBody.contains("e.syncError.retryable && runAttemptCount < 3")
        )
        assertTrue(
            "Bitwarden coordinator Blocked result should use the Worker blocked path instead of throwing IllegalStateException.",
            syncVaultBody.contains("is BitwardenCoordinatedSyncResult.Blocked ->") &&
                syncVaultBody.contains("throw BitwardenWorkerBlockedException(syncResult.error)") &&
                !syncVaultBody.contains("IllegalStateException(\"Bitwarden sync blocked")
        )
    }

    private fun projectFile(relativePath: String): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            dir.parentFile != null &&
            !File(dir, "settings.gradle").exists() &&
            !File(dir, "settings.gradle.kts").exists()
        ) {
            dir = dir.parentFile!!.canonicalFile
        }
        return File(dir, relativePath)
    }
}
