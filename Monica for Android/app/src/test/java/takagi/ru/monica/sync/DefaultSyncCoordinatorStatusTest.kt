package takagi.ru.monica.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSyncCoordinatorStatusTest {

    @Test
    fun coordinatorPublishesRunningAndSuccess() = runBlocking {
        val job = Job()
        val target = SyncTarget.KeePassDatabase(databaseId = 7L)
        val store = DefaultSyncStatusStore()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val executor = object : SyncExecutor {
            override fun canExecute(target: SyncTarget): Boolean = true

            override suspend fun execute(request: SyncRequest): SyncExecutionResult {
                started.complete(Unit)
                release.await()
                return SyncExecutionResult.Success(finishedAtMillis = 30L, detail = "ok")
            }
        }
        val coordinator = DefaultSyncCoordinator(
            scope = CoroutineScope(coroutineContext + job),
            executors = listOf(executor),
            statusStore = store,
            nowMillis = { 10L }
        )

        try {
            coordinator.request(request("success", target, SyncTrigger.PAGE_VISIBLE))
            withTimeout(1_000L) { started.await() }

            assertEquals(SyncPhase.RUNNING, store.snapshot()[target.stableKey]?.phase)
            assertEquals("success", store.snapshot()[target.stableKey]?.runningRequestId)

            release.complete(Unit)
            val finalStatus = awaitPhase(store, target.stableKey, SyncPhase.SUCCESS)

            assertEquals(30L, finalStatus.lastSuccessAtMillis)
            assertEquals("ok", finalStatus.progressLabel)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun coordinatorPublishesFailedResult() = runBlocking {
        val job = Job()
        val target = SyncTarget.BitwardenVault(vaultId = 3L)
        val store = DefaultSyncStatusStore()
        val executor = object : SyncExecutor {
            override fun canExecute(target: SyncTarget): Boolean = true

            override suspend fun execute(request: SyncRequest): SyncExecutionResult {
                return SyncExecutionResult.Failed(
                    finishedAtMillis = 40L,
                    error = SyncError(
                        kind = SyncErrorKind.NETWORK_UNAVAILABLE,
                        redactedMessage = "offline",
                        retryable = true
                    )
                )
            }
        }
        val coordinator = DefaultSyncCoordinator(
            scope = CoroutineScope(coroutineContext + job),
            executors = listOf(executor),
            statusStore = store,
            nowMillis = { 10L }
        )

        try {
            coordinator.request(request("failed", target, SyncTrigger.MANUAL))
            val finalStatus = awaitPhase(store, target.stableKey, SyncPhase.FAILED)

            assertEquals(SyncErrorKind.NETWORK_UNAVAILABLE, finalStatus.lastError?.kind)
            assertTrue(finalStatus.lastError?.retryable == true)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun coordinatorPublishesBlockedWhenExecutorIsMissing() = runBlocking {
        val target = SyncTarget.MdbxVault(databaseId = 9L)
        val dedupeKey = SyncKey("mdbx:9:repair")
        val store = DefaultSyncStatusStore()
        val coordinator = DefaultSyncCoordinator(
            scope = CoroutineScope(coroutineContext + Job()),
            executors = emptyList(),
            statusStore = store,
            nowMillis = { 10L }
        )

        val result = coordinator.request(
            request(
                id = "blocked",
                target = target,
                trigger = SyncTrigger.MANUAL,
                dedupeKey = dedupeKey
            )
        )
        val status = store.snapshot()[dedupeKey]

        assertTrue(result is SyncEnqueueResult.Blocked)
        assertNotNull(status)
        assertEquals(SyncPhase.BLOCKED, status?.phase)
        assertEquals(SyncErrorKind.VALIDATION_FAILED, status?.lastError?.kind)
        assertNull(store.snapshot()[target.stableKey])
    }

    @Test
    fun observeTargetUsesDefaultDedupeKey() = runBlocking {
        val target = SyncTarget.KeePassCompatibilityIndex(
            databaseId = null,
            itemTypes = setOf(SyncItemKind.PASSKEY)
        )
        val store = DefaultSyncStatusStore()
        val coordinator = DefaultSyncCoordinator(
            scope = CoroutineScope(coroutineContext + Job()),
            executors = emptyList(),
            statusStore = store,
            nowMillis = { 10L }
        )

        coordinator.request(request("blocked", target, SyncTrigger.MANUAL))
        val status = coordinator.observe(target).firstNonNull()

        assertEquals(target.defaultDedupeKey, status.key)
        assertEquals(SyncPhase.BLOCKED, status.phase)
    }

    @Test
    fun coordinatorBlocksRequestWhenNetworkGateFails() = runBlocking {
        val target = SyncTarget.KeePassDatabase(databaseId = 7L)
        val calls = mutableListOf<String>()
        val store = DefaultSyncStatusStore()
        val executor = object : SyncExecutor {
            override fun canExecute(target: SyncTarget): Boolean = true

            override suspend fun execute(request: SyncRequest): SyncExecutionResult {
                calls += request.requestId
                return SyncExecutionResult.Success(finishedAtMillis = 50L)
            }
        }
        val coordinator = DefaultSyncCoordinator(
            scope = CoroutineScope(coroutineContext + Job()),
            executors = listOf(executor),
            statusStore = store,
            networkGate = SyncNetworkGate { policy ->
                if (policy == SyncNetworkPolicy.REQUIRED) {
                    SyncError(
                        kind = SyncErrorKind.NETWORK_UNAVAILABLE,
                        redactedMessage = "offline",
                        retryable = true
                    )
                } else {
                    null
                }
            },
            nowMillis = { 10L }
        )

        val result = coordinator.request(
            SyncRequest(
                requestId = "offline",
                target = target,
                trigger = SyncTrigger.PAGE_VISIBLE,
                createdAtMillis = 1L,
                networkPolicy = SyncNetworkPolicy.REQUIRED
            )
        )
        val status = store.snapshot()[target.stableKey]

        assertTrue(result is SyncEnqueueResult.Blocked)
        assertEquals(emptyList<String>(), calls)
        assertEquals(SyncPhase.BLOCKED, status?.phase)
        assertEquals(SyncErrorKind.NETWORK_UNAVAILABLE, status?.lastError?.kind)
        assertTrue(status?.lastError?.retryable == true)
    }

    private suspend fun awaitPhase(
        store: DefaultSyncStatusStore,
        key: SyncKey,
        phase: SyncPhase
    ): SyncTaskStatus {
        return withTimeout(1_000L) {
            while (true) {
                val status = store.snapshot()[key]
                if (status?.phase == phase) return@withTimeout status
                delay(10L)
            }
            error("Unreachable")
        }
    }

    private suspend fun Flow<SyncTaskStatus?>.firstNonNull(): SyncTaskStatus {
        return withTimeout(1_000L) {
            filterNotNull().first()
        }
    }

    private fun request(
        id: String,
        target: SyncTarget,
        trigger: SyncTrigger,
        dedupeKey: SyncKey = target.defaultDedupeKey
    ): SyncRequest {
        return SyncRequest(
            requestId = id,
            target = target,
            trigger = trigger,
            createdAtMillis = 1L,
            dedupeKey = dedupeKey
        )
    }
}
