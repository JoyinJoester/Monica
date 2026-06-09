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
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSyncCoordinatorTest {

    @Test
    fun sameTargetDoesNotRunConcurrentlyAndPageVisibleDuplicateIsMerged() = runBlocking {
        val job = Job()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val runningCount = AtomicInteger(0)
        val maxRunning = AtomicInteger(0)
        val calls = mutableListOf<String>()

        val executor = object : SyncExecutor {
            override fun canExecute(target: SyncTarget): Boolean = true

            override suspend fun execute(request: SyncRequest): SyncExecutionResult {
                calls += request.requestId
                val running = runningCount.incrementAndGet()
                maxRunning.updateAndGet { current -> maxOf(current, running) }
                started.complete(Unit)
                release.await()
                runningCount.decrementAndGet()
                return SyncExecutionResult.Success(finishedAtMillis = 20L)
            }
        }
        val coordinator = DefaultSyncCoordinator(
            scope = CoroutineScope(coroutineContext + job),
            executors = listOf(executor),
            nowMillis = { 10L }
        )

        try {
            val first = coordinator.request(request("first", SyncTrigger.PAGE_VISIBLE))
            assertTrue(first is SyncEnqueueResult.Accepted)
            withTimeout(1_000L) { started.await() }

            val duplicate = coordinator.request(request("duplicate", SyncTrigger.PAGE_VISIBLE))
            assertTrue(duplicate is SyncEnqueueResult.Merged)
            assertEquals(listOf("first"), calls)

            release.complete(Unit)
            repeat(8) { yield() }
            delay(20L)

            assertEquals(listOf("first"), calls)
            assertEquals(1, maxRunning.get())
        } finally {
            job.cancel()
        }
    }

    @Test
    fun manualRequestQueuedBehindRunningAutoSyncRunsNext() = runBlocking {
        val job = Job()
        val firstStarted = CompletableDeferred<Unit>()
        val manualStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val releaseManual = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()

        val executor = object : SyncExecutor {
            override fun canExecute(target: SyncTarget): Boolean = true

            override suspend fun execute(request: SyncRequest): SyncExecutionResult {
                calls += request.requestId
                when (request.requestId) {
                    "auto" -> {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                    }
                    "manual" -> {
                        manualStarted.complete(Unit)
                        releaseManual.await()
                    }
                }
                return SyncExecutionResult.Success(finishedAtMillis = 30L)
            }
        }
        val coordinator = DefaultSyncCoordinator(
            scope = CoroutineScope(coroutineContext + job),
            executors = listOf(executor),
            nowMillis = { 10L }
        )

        try {
            coordinator.request(request("auto", SyncTrigger.PAGE_VISIBLE))
            withTimeout(1_000L) { firstStarted.await() }

            val duplicate = coordinator.request(request("auto-duplicate", SyncTrigger.PAGE_VISIBLE))
            val manual = coordinator.request(request("manual", SyncTrigger.MANUAL))
            assertTrue(duplicate is SyncEnqueueResult.Merged)
            assertTrue(manual is SyncEnqueueResult.Accepted)

            releaseFirst.complete(Unit)
            withTimeout(1_000L) { manualStarted.await() }
            releaseManual.complete(Unit)
            repeat(8) { yield() }

            assertEquals(listOf("auto", "manual"), calls)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun localMutationReplacesLowerPriorityPendingRequest() = runBlocking {
        val job = Job()
        val firstStarted = CompletableDeferred<Unit>()
        val mutationStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val releaseMutation = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()

        val executor = object : SyncExecutor {
            override fun canExecute(target: SyncTarget): Boolean = true

            override suspend fun execute(request: SyncRequest): SyncExecutionResult {
                calls += request.requestId
                when (request.requestId) {
                    "startup" -> {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                    }
                    "mutation" -> {
                        mutationStarted.complete(Unit)
                        releaseMutation.await()
                    }
                }
                return SyncExecutionResult.Success(finishedAtMillis = 40L)
            }
        }
        val coordinator = DefaultSyncCoordinator(
            scope = CoroutineScope(coroutineContext + job),
            executors = listOf(executor),
            nowMillis = { 10L }
        )

        try {
            coordinator.request(request("startup", SyncTrigger.APP_START))
            withTimeout(1_000L) { firstStarted.await() }

            val page = coordinator.request(request("page", SyncTrigger.PAGE_VISIBLE))
            val mutation = coordinator.request(request("mutation", SyncTrigger.LOCAL_MUTATION))
            assertTrue(page is SyncEnqueueResult.Accepted)
            assertTrue(mutation is SyncEnqueueResult.Accepted)
            assertEquals("page", (mutation as SyncEnqueueResult.Accepted).replacedRequestId)

            releaseFirst.complete(Unit)
            withTimeout(1_000L) { mutationStarted.await() }
            releaseMutation.complete(Unit)
            repeat(8) { yield() }

            assertEquals(listOf("startup", "mutation"), calls)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun cancelClearsPendingRequestsWithoutRunningThem() = runBlocking {
        val job = Job()
        val target = SyncTarget.KeePassDatabase(databaseId = 7L)
        val key = target.defaultDedupeKey
        val store = DefaultSyncStatusStore()
        val runningStarted = CompletableDeferred<Unit>()
        val neverReleaseRunning = CompletableDeferred<Unit>()
        val pendingStarted = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()

        val executor = object : SyncExecutor {
            override fun canExecute(target: SyncTarget): Boolean = true

            override suspend fun execute(request: SyncRequest): SyncExecutionResult {
                calls += request.requestId
                if (request.requestId == "running") {
                    runningStarted.complete(Unit)
                    neverReleaseRunning.await()
                } else {
                    pendingStarted.complete(Unit)
                }
                return SyncExecutionResult.Success(finishedAtMillis = 60L)
            }
        }
        val coordinator = DefaultSyncCoordinator(
            scope = CoroutineScope(coroutineContext + job),
            executors = listOf(executor),
            statusStore = store,
            nowMillis = { 10L }
        )

        try {
            coordinator.request(request("running", target, SyncTrigger.PAGE_VISIBLE))
            withTimeout(1_000L) { runningStarted.await() }

            val pending = coordinator.request(request("pending", target, SyncTrigger.MANUAL))
            assertTrue(pending is SyncEnqueueResult.Accepted)

            coordinator.cancel(key, "test_cancel")
            awaitPhase(store, key, SyncPhase.CANCELED)

            assertEquals(listOf("running"), calls)
            assertEquals(false, pendingStarted.isCompleted)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun sameDedupeKeyDifferentTargetsRunSequentiallyWithoutMerging() = runBlocking {
        val job = Job()
        val started = mapOf(
            "cards" to CompletableDeferred<Unit>(),
            "documents" to CompletableDeferred<Unit>(),
            "passkeys" to CompletableDeferred<Unit>()
        )
        val releases = mapOf(
            "cards" to CompletableDeferred<Unit>(),
            "documents" to CompletableDeferred<Unit>(),
            "passkeys" to CompletableDeferred<Unit>()
        )
        val runningCount = AtomicInteger(0)
        val maxRunning = AtomicInteger(0)
        val calls = mutableListOf<String>()

        val executor = object : SyncExecutor {
            override fun canExecute(target: SyncTarget): Boolean = true

            override suspend fun execute(request: SyncRequest): SyncExecutionResult {
                calls += request.requestId
                val running = runningCount.incrementAndGet()
                maxRunning.updateAndGet { current -> maxOf(current, running) }
                started.getValue(request.requestId).complete(Unit)
                releases.getValue(request.requestId).await()
                runningCount.decrementAndGet()
                return SyncExecutionResult.Success(finishedAtMillis = 50L)
            }
        }
        val coordinator = DefaultSyncCoordinator(
            scope = CoroutineScope(coroutineContext + job),
            executors = listOf(executor),
            nowMillis = { 10L }
        )

        val cardTarget = SyncTarget.KeePassCompatibilityIndex(
            databaseId = null,
            itemTypes = setOf(SyncItemKind.BANK_CARD)
        )
        val documentTarget = SyncTarget.KeePassCompatibilityIndex(
            databaseId = null,
            itemTypes = setOf(SyncItemKind.DOCUMENT)
        )
        val passkeyTarget = SyncTarget.KeePassCompatibilityIndex(
            databaseId = null,
            itemTypes = setOf(SyncItemKind.PASSKEY)
        )

        try {
            assertEquals(cardTarget.defaultDedupeKey, documentTarget.defaultDedupeKey)
            assertEquals(cardTarget.defaultDedupeKey, passkeyTarget.defaultDedupeKey)

            coordinator.request(request("cards", cardTarget, SyncTrigger.PAGE_VISIBLE))
            withTimeout(1_000L) { started.getValue("cards").await() }

            val documents = coordinator.request(request("documents", documentTarget, SyncTrigger.PAGE_VISIBLE))
            val passkeys = coordinator.request(request("passkeys", passkeyTarget, SyncTrigger.PAGE_VISIBLE))
            assertTrue(documents is SyncEnqueueResult.Accepted)
            assertTrue(passkeys is SyncEnqueueResult.Accepted)
            assertEquals(listOf("cards"), calls)

            releases.getValue("cards").complete(Unit)
            withTimeout(1_000L) { started.getValue("documents").await() }
            releases.getValue("documents").complete(Unit)
            withTimeout(1_000L) { started.getValue("passkeys").await() }
            releases.getValue("passkeys").complete(Unit)
            repeat(8) { yield() }

            assertEquals(listOf("cards", "documents", "passkeys"), calls)
            assertEquals(1, maxRunning.get())
        } finally {
            job.cancel()
        }
    }

    @Test
    fun keepassCompatibilityIndexSerializesAllAndSpecificDatabaseRefreshesButThrottlesByTarget() {
        val allCards = SyncTarget.KeePassCompatibilityIndex(
            databaseId = null,
            itemTypes = setOf(SyncItemKind.BANK_CARD)
        )
        val databaseCards = SyncTarget.KeePassCompatibilityIndex(
            databaseId = 7L,
            itemTypes = setOf(SyncItemKind.BANK_CARD)
        )
        val databaseDocuments = SyncTarget.KeePassCompatibilityIndex(
            databaseId = 7L,
            itemTypes = setOf(SyncItemKind.DOCUMENT)
        )

        assertEquals(SyncKey(KEEPASS_COMPATIBILITY_INDEX_DEDUPE_KEY), allCards.defaultDedupeKey)
        assertEquals(allCards.defaultDedupeKey, databaseCards.defaultDedupeKey)
        assertEquals(databaseCards.defaultDedupeKey, databaseDocuments.defaultDedupeKey)

        assertEquals(allCards.stableKey, SyncRequest(
            requestId = "all-cards",
            target = allCards,
            trigger = SyncTrigger.PAGE_VISIBLE,
            createdAtMillis = 1L
        ).throttleKey)
        assertEquals(databaseCards.stableKey, SyncRequest(
            requestId = "database-cards",
            target = databaseCards,
            trigger = SyncTrigger.PAGE_VISIBLE,
            createdAtMillis = 1L
        ).throttleKey)
        assertEquals(databaseDocuments.stableKey, SyncRequest(
            requestId = "database-documents",
            target = databaseDocuments,
            trigger = SyncTrigger.PAGE_VISIBLE,
            createdAtMillis = 1L
        ).throttleKey)
    }

    @Test
    fun automaticRequestInsideThrottleWindowIsSkippedAfterSuccess() = runBlocking {
        val job = Job()
        val calls = mutableListOf<String>()
        var nowMs = 1_000L
        val firstFinished = CompletableDeferred<Unit>()

        val executor = object : SyncExecutor {
            override fun canExecute(target: SyncTarget): Boolean = true

            override suspend fun execute(request: SyncRequest): SyncExecutionResult {
                calls += request.requestId
                firstFinished.complete(Unit)
                return SyncExecutionResult.Success(finishedAtMillis = nowMs)
            }
        }
        val coordinator = DefaultSyncCoordinator(
            scope = CoroutineScope(coroutineContext + job),
            executors = listOf(executor),
            nowMillis = { nowMs }
        )

        try {
            coordinator.request(request("first", SyncTrigger.PAGE_VISIBLE, throttleMs = 30_000L))
            withTimeout(1_000L) { firstFinished.await() }
            repeat(4) { yield() }

            nowMs += 5_000L
            val second = coordinator.request(request("second", SyncTrigger.PAGE_VISIBLE, throttleMs = 30_000L))

            assertTrue(second is SyncEnqueueResult.Skipped)
            assertEquals(listOf("first"), calls)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun throttleKeyCanDifferFromGlobalDedupeKey() = runBlocking {
        val job = Job()
        val calls = mutableListOf<String>()
        var nowMs = 1_000L

        val executor = object : SyncExecutor {
            override fun canExecute(target: SyncTarget): Boolean = true

            override suspend fun execute(request: SyncRequest): SyncExecutionResult {
                calls += request.requestId
                return SyncExecutionResult.Success(finishedAtMillis = nowMs)
            }
        }
        val coordinator = DefaultSyncCoordinator(
            scope = CoroutineScope(coroutineContext + job),
            executors = listOf(executor),
            nowMillis = { nowMs }
        )
        val globalDedupeKey = SyncKey("keepass_visible_remote")
        val cardTarget = SyncTarget.KeePassDatabase(databaseId = 7L)
        val documentTarget = SyncTarget.KeePassDatabase(databaseId = 8L)

        try {
            coordinator.request(
                request(
                    id = "first-db",
                    target = cardTarget,
                    trigger = SyncTrigger.PAGE_VISIBLE,
                    throttleMs = 30_000L,
                    dedupeKey = globalDedupeKey,
                    throttleKey = cardTarget.stableKey
                )
            )
            repeat(8) { yield() }

            nowMs += 5_000L
            val secondDatabase = coordinator.request(
                request(
                    id = "second-db",
                    target = documentTarget,
                    trigger = SyncTrigger.PAGE_VISIBLE,
                    throttleMs = 30_000L,
                    dedupeKey = globalDedupeKey,
                    throttleKey = documentTarget.stableKey
                )
            )
            repeat(8) { yield() }

            val firstDatabaseAgain = coordinator.request(
                request(
                    id = "first-db-again",
                    target = cardTarget,
                    trigger = SyncTrigger.PAGE_VISIBLE,
                    throttleMs = 30_000L,
                    dedupeKey = globalDedupeKey,
                    throttleKey = cardTarget.stableKey
                )
            )

            assertTrue(secondDatabase is SyncEnqueueResult.Accepted)
            assertTrue(firstDatabaseAgain is SyncEnqueueResult.Skipped)
            assertEquals(listOf("first-db", "second-db"), calls)
        } finally {
            job.cancel()
        }
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

    private fun request(id: String, trigger: SyncTrigger, throttleMs: Long = 0L): SyncRequest {
        return request(
            id = id,
            target = SyncTarget.KeePassDatabase(databaseId = 7L),
            trigger = trigger,
            throttleMs = throttleMs
        )
    }

    private fun request(
        id: String,
        target: SyncTarget,
        trigger: SyncTrigger,
        throttleMs: Long = 0L,
        dedupeKey: SyncKey = target.defaultDedupeKey,
        throttleKey: SyncKey = target.stableKey
    ): SyncRequest {
        return SyncRequest(
            requestId = id,
            target = target,
            trigger = trigger,
            createdAtMillis = 1L,
            throttleMs = throttleMs,
            dedupeKey = dedupeKey,
            throttleKey = throttleKey
        )
    }
}
