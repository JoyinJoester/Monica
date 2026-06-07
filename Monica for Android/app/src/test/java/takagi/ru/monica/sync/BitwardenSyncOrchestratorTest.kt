package takagi.ru.monica.sync

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.bitwarden.sync.BitwardenSyncOrchestrator
import takagi.ru.monica.bitwarden.sync.NetworkGateResult
import takagi.ru.monica.bitwarden.sync.SyncExecutionOutcome
import takagi.ru.monica.bitwarden.sync.SyncManagerConfig
import takagi.ru.monica.bitwarden.sync.SyncTriggerReason

class BitwardenSyncOrchestratorTest {

    @Test
    fun pendingPageEnterWhileSyncingDoesNotBypassThrottleAfterSuccess() = runBlocking {
        val job = Job()
        val syncCount = AtomicInteger(0)
        var nowMs = 100_000L
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val orchestrator = BitwardenSyncOrchestrator(
            scope = CoroutineScope(coroutineContext + job),
            config = SyncManagerConfig(
                pageEnterThrottleMs = 45_000L,
                appResumeThrottleMs = 60_000L
            ),
            isAutoSyncEnabled = { true },
            checkNetwork = { NetworkGateResult.ALLOWED },
            isVaultUnlocked = { true },
            executeSync = { _, _ ->
                syncCount.incrementAndGet()
                firstStarted.complete(Unit)
                releaseFirst.await()
                SyncExecutionOutcome.Success(
                    appliedChangeCount = 0,
                    availableOfflineCount = 0,
                    conflictCount = 0,
                    uploadFailedCount = 0,
                    skippedDueToLocalDirtyCount = 0
                )
            },
            nowProvider = { nowMs }
        )

        try {
            orchestrator.requestSync(vaultId = 7L, reason = SyncTriggerReason.PAGE_ENTER)
            withTimeout(1_000L) { firstStarted.await() }

            nowMs += 3_000L
            orchestrator.requestSync(vaultId = 7L, reason = SyncTriggerReason.PAGE_ENTER)
            releaseFirst.complete(Unit)
            repeat(8) { yield() }
            delay(50L)

            assertEquals(
                "A page-enter request queued behind a running sync must not force a second full sync immediately after success.",
                1,
                syncCount.get()
            )
        } finally {
            job.cancel()
        }
    }

    @Test
    fun pendingLocalMutationWhileSyncingStillRunsAfterCurrentSync() = runBlocking {
        val job = Job()
        val syncCount = AtomicInteger(0)
        var nowMs = 100_000L
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()

        val orchestrator = BitwardenSyncOrchestrator(
            scope = CoroutineScope(coroutineContext + job),
            config = SyncManagerConfig(
                pageEnterThrottleMs = 45_000L,
                appResumeThrottleMs = 60_000L,
                localMutationDebounceMs = 1L
            ),
            isAutoSyncEnabled = { true },
            checkNetwork = { NetworkGateResult.ALLOWED },
            isVaultUnlocked = { true },
            executeSync = { _, _ ->
                when (syncCount.incrementAndGet()) {
                    1 -> {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                    }
                    2 -> {
                        secondStarted.complete(Unit)
                        releaseSecond.await()
                    }
                }
                SyncExecutionOutcome.Success(
                    appliedChangeCount = 0,
                    availableOfflineCount = 0,
                    conflictCount = 0,
                    uploadFailedCount = 0,
                    skippedDueToLocalDirtyCount = 0
                )
            },
            nowProvider = { nowMs }
        )

        try {
            orchestrator.requestSync(vaultId = 7L, reason = SyncTriggerReason.PAGE_ENTER)
            withTimeout(1_000L) { firstStarted.await() }

            nowMs += 3_000L
            orchestrator.requestSync(vaultId = 7L, reason = SyncTriggerReason.LOCAL_MUTATION)
            delay(20L)
            releaseFirst.complete(Unit)

            withTimeout(1_000L) { secondStarted.await() }
            releaseSecond.complete(Unit)
            repeat(8) { yield() }

            assertEquals(
                "A local mutation queued behind a running sync must still replay so newly changed data is uploaded.",
                2,
                syncCount.get()
            )
        } finally {
            job.cancel()
        }
    }
}
