package takagi.ru.monica.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultSyncCoordinator(
    private val scope: CoroutineScope,
    private val executors: List<SyncExecutor>,
    private val statusStore: SyncStatusStore = DefaultSyncStatusStore(),
    private val networkGate: SyncNetworkGate = SyncNetworkGate { null },
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) : SyncCoordinator {

    private data class Runtime(
        var runningRequest: SyncRequest? = null,
        var runningJob: Job? = null,
        val pendingRequests: MutableList<SyncRequest> = mutableListOf(),
        var startedAtMillis: Long? = null,
        var diagnosticStartedAtMillis: Long? = null
    )

    private val mutex = Mutex()
    private val runtimes = mutableMapOf<SyncKey, Runtime>()
    private val lastSuccessAtByThrottleKey = mutableMapOf<SyncKey, Long>()

    override val statuses: Flow<Map<SyncKey, SyncTaskStatus>> = statusStore.statuses

    override fun observe(target: SyncTarget): Flow<SyncTaskStatus?> {
        return statusStore.observe(target.defaultDedupeKey)
    }

    override fun observe(key: SyncKey): Flow<SyncTaskStatus?> {
        return statusStore.observe(key)
    }

    override suspend fun request(request: SyncRequest): SyncEnqueueResult {
        val key = request.dedupeKey
        SyncDiagnostics.queued(
            taskId = request.requestId,
            target = request.target.stableKey.value,
            trigger = request.trigger.name,
            detail = coordinatorDetail(key, request)
        )
        val executor = executors.firstOrNull { it.canExecute(request.target) }
            ?: return blockMissingExecutor(request)
        networkGate.evaluate(request.networkPolicy)?.let { error ->
            return blockPreflight(request, error)
        }

        return mutex.withLock {
            val existingRuntime = runtimes[key]
            val runningRequest = existingRuntime?.runningRequest
            if (runningRequest != null) {
                val runtime = existingRuntime ?: error("Runtime missing for running sync")
                val decision = queueBehindRunningIfNeeded(runtime, runningRequest, request)
                val status = runningStatus(key, runtime)
                publish(status)
                if (decision.accepted) {
                    decision.replacedRequest?.let { replaced ->
                        logReplacedPendingRequest(
                            key = key,
                            replaced = replaced,
                            replacement = request
                        )
                    }
                    SyncEnqueueResult.Accepted(
                        request = request,
                        status = status,
                        replacedRequestId = decision.replacedRequestId
                    )
                } else {
                    SyncDiagnostics.skipped(
                        taskId = request.requestId,
                        target = request.target.stableKey.value,
                        trigger = request.trigger.name,
                        reason = "merged",
                        detail = coordinatorDetail(
                            key = key,
                            request = request,
                            extra = "running=${runningRequest.requestId}"
                        )
                    )
                    SyncEnqueueResult.Merged(request, status)
                }
            } else {
                throttleSkipReason(request)?.let { reason ->
                    SyncDiagnostics.skipped(
                        taskId = request.requestId,
                        target = request.target.stableKey.value,
                        trigger = request.trigger.name,
                        reason = reason,
                        detail = coordinatorDetail(key, request)
                    )
                    return@withLock SyncEnqueueResult.Skipped(request, reason)
                }
                val runtime = existingRuntime ?: Runtime().also { runtimes[key] = it }
                startLocked(key, runtime, request, executor)
                val status = runningStatus(key, runtime)
                SyncEnqueueResult.Accepted(request, status)
            }
        }
    }

    override suspend fun cancel(key: SyncKey, reason: String?) {
        mutex.withLock {
            val runtime = runtimes[key]
            if (runtime == null) {
                return
            }

            logCanceledPendingRequests(key, runtime.pendingRequests, reason)
            runtime.pendingRequests.clear()
            val job = runtime.runningJob
            if (job == null) {
                val target = runtime.runningRequest?.target ?: statusStore.snapshot()[key]?.target
                if (target != null) {
                    publish(
                        SyncTaskStatus(
                            key = key,
                            target = target,
                            phase = SyncPhase.CANCELED,
                            lastFinishedAtMillis = nowMillis(),
                            progressLabel = reason
                        )
                    )
                }
                runtimes.remove(key)
                return
            }
            job.cancel(CancellationException(reason ?: "Sync canceled"))
        }
    }

    private fun logCanceledPendingRequests(
        key: SyncKey,
        pendingRequests: List<SyncRequest>,
        reason: String?
    ) {
        val resolvedReason = reason ?: "canceled"
        pendingRequests.forEach { pending ->
            SyncDiagnostics.skipped(
                taskId = pending.requestId,
                target = pending.target.stableKey.value,
                trigger = pending.trigger.name,
                reason = resolvedReason,
                detail = coordinatorDetail(key, pending)
            )
        }
    }

    private suspend fun blockMissingExecutor(request: SyncRequest): SyncEnqueueResult.Blocked {
        val error = SyncError(
            kind = SyncErrorKind.VALIDATION_FAILED,
            redactedMessage = "No SyncExecutor for ${request.target.kind}",
            retryable = false
        )
        SyncDiagnostics.blocked(
            taskId = request.requestId,
            target = request.target.stableKey.value,
            trigger = request.trigger.name,
            reason = error.redactedMessage ?: error.kind.name,
            detail = coordinatorDetail(request.dedupeKey, request)
        )
        val status = SyncTaskStatus(
            key = request.dedupeKey,
            target = request.target,
            phase = SyncPhase.BLOCKED,
            lastTrigger = request.trigger,
            lastFinishedAtMillis = nowMillis(),
            lastError = error
        )
        publish(status)
        return SyncEnqueueResult.Blocked(request, error)
    }

    private suspend fun blockPreflight(
        request: SyncRequest,
        error: SyncError
    ): SyncEnqueueResult.Blocked {
        SyncDiagnostics.blocked(
            taskId = request.requestId,
            target = request.target.stableKey.value,
            trigger = request.trigger.name,
            reason = error.redactedMessage ?: error.kind.name,
            detail = coordinatorDetail(request.dedupeKey, request)
        )
        val status = SyncTaskStatus(
            key = request.dedupeKey,
            target = request.target,
            phase = SyncPhase.BLOCKED,
            lastTrigger = request.trigger,
            lastFinishedAtMillis = nowMillis(),
            lastError = error
        )
        publish(status)
        return SyncEnqueueResult.Blocked(request, error)
    }

    private data class PendingDecision(
        val accepted: Boolean,
        val replacedRequest: SyncRequest? = null
    ) {
        val replacedRequestId: String? get() = replacedRequest?.requestId
    }

    private fun logReplacedPendingRequest(
        key: SyncKey,
        replaced: SyncRequest,
        replacement: SyncRequest
    ) {
        SyncDiagnostics.skipped(
            taskId = replaced.requestId,
            target = replaced.target.stableKey.value,
            trigger = replaced.trigger.name,
            reason = "replaced",
            detail = coordinatorDetail(
                key = key,
                request = replaced,
                extra = "replacement=${replacement.requestId}"
            )
        )
    }

    private fun queueBehindRunningIfNeeded(
        runtime: Runtime,
        runningRequest: SyncRequest,
        incoming: SyncRequest
    ): PendingDecision {
        val sameTargetPendingIndex = runtime.pendingRequests.indexOfFirst {
            it.target.stableKey == incoming.target.stableKey
        }
        val sameTargetPending = runtime.pendingRequests.getOrNull(sameTargetPendingIndex)
        val shouldReplay = shouldReplayAfterRunning(runningRequest, incoming)
        if (!shouldReplay && sameTargetPending == null) return PendingDecision(accepted = false)

        val winner = when {
            sameTargetPending == null -> incoming
            incoming.priority.rank >= sameTargetPending.priority.rank -> incoming
            else -> sameTargetPending
        }
        val replacedRequest = sameTargetPending
            ?.takeIf { winner.requestId == incoming.requestId && it.requestId != incoming.requestId }
        if (sameTargetPendingIndex >= 0) {
            runtime.pendingRequests[sameTargetPendingIndex] = winner
        } else {
            runtime.pendingRequests += winner
        }
        return PendingDecision(
            accepted = winner.requestId == incoming.requestId,
            replacedRequest = replacedRequest
        )
    }

    private fun shouldReplayAfterRunning(
        runningRequest: SyncRequest,
        incoming: SyncRequest
    ): Boolean {
        if (incoming.target.stableKey != runningRequest.target.stableKey) return true
        if (incoming.priority.rank > runningRequest.priority.rank) return true
        return incoming.trigger in replayTriggers
    }

    private fun throttleSkipReason(request: SyncRequest): String? {
        if (request.throttleMs <= 0L) return null
        if (request.priority.rank >= SyncPriority.MANUAL.rank) return null
        val lastSuccessAt = lastSuccessAtByThrottleKey[request.throttleKey] ?: return null
        val elapsed = nowMillis() - lastSuccessAt
        return if (elapsed in 0 until request.throttleMs) {
            "throttle:${request.throttleMs - elapsed}ms"
        } else {
            null
        }
    }

    private suspend fun startLocked(
        key: SyncKey,
        runtime: Runtime,
        request: SyncRequest,
        executor: SyncExecutor
    ) {
        runtime.runningRequest = request
        runtime.startedAtMillis = nowMillis()
        runtime.diagnosticStartedAtMillis = SyncDiagnostics.start(
            taskId = request.requestId,
            target = request.target.stableKey.value,
            trigger = request.trigger.name,
            detail = coordinatorDetail(
                key = key,
                request = request,
                extra = "pending=${runtime.pendingRequests.size}"
            )
        )
        val job = scope.launch(start = CoroutineStart.LAZY) {
            executeAndFinish(key, request, executor)
        }
        runtime.runningJob = job
        publish(runningStatus(key, runtime))
        job.start()
    }

    private suspend fun executeAndFinish(
        key: SyncKey,
        request: SyncRequest,
        executor: SyncExecutor
    ) {
        val result = try {
            executor.execute(request)
        } catch (error: CancellationException) {
            SyncExecutionResult.Canceled(
                finishedAtMillis = nowMillis(),
                reason = error.message
            )
        } catch (error: Exception) {
            SyncExecutionResult.Failed(
                finishedAtMillis = nowMillis(),
                error = SyncError(
                    kind = SyncErrorKind.UNEXPECTED,
                    redactedMessage = error.message,
                    retryable = false
                )
            )
        }
        finishExecution(key, request, result)
    }

    private suspend fun finishExecution(
        key: SyncKey,
        request: SyncRequest,
        result: SyncExecutionResult
    ) {
        mutex.withLock {
            val runtime = runtimes[key] ?: return
            if (runtime.runningRequest?.requestId != request.requestId) return

            val diagnosticStartedAt = runtime.diagnosticStartedAtMillis ?: SyncDiagnostics.now()
            logCoordinatorResult(
                request = request,
                key = key,
                result = result,
                startedAt = diagnosticStartedAt
            )
            runtime.runningRequest = null
            runtime.runningJob = null
            runtime.startedAtMillis = null
            runtime.diagnosticStartedAtMillis = null
            publish(resultStatus(key, request, result))
            if (result is SyncExecutionResult.Success) {
                lastSuccessAtByThrottleKey[request.throttleKey] = result.finishedAtMillis
            }

            val next = runtime.takeNextPendingRequest()
            if (next == null) {
                runtimes.remove(key)
                return
            }

            val nextExecutor = executors.firstOrNull { it.canExecute(next.target) }
            if (nextExecutor == null) {
                val blockedResult = SyncExecutionResult.Blocked(
                    finishedAtMillis = nowMillis(),
                    error = SyncError(
                        kind = SyncErrorKind.VALIDATION_FAILED,
                        redactedMessage = "No SyncExecutor for ${next.target.kind}",
                        retryable = false
                    )
                )
                SyncDiagnostics.blocked(
                    taskId = next.requestId,
                    target = next.target.stableKey.value,
                    trigger = next.trigger.name,
                    reason = blockedResult.error.redactedMessage ?: blockedResult.error.kind.name,
                    detail = coordinatorDetail(key, next)
                )
                publish(resultStatus(key = key, request = next, result = blockedResult))
                runtimes.remove(key)
                return
            }
            startLocked(key, runtime, next, nextExecutor)
        }
    }

    private fun runningStatus(key: SyncKey, runtime: Runtime): SyncTaskStatus {
        val request = runtime.runningRequest ?: runtime.pendingRequests.firstOrNull()
            ?: statusStore.snapshot()[key]?.let { return it }
            ?: error("Cannot build running status without a request")
        return SyncTaskStatus(
            key = key,
            target = request.target,
            phase = SyncPhase.RUNNING,
            queuedCount = runtime.pendingRequests.size,
            runningRequestId = runtime.runningRequest?.requestId,
            lastTrigger = request.trigger,
            lastStartedAtMillis = runtime.startedAtMillis
        )
    }

    private fun resultStatus(
        key: SyncKey,
        request: SyncRequest,
        result: SyncExecutionResult
    ): SyncTaskStatus {
        val finishedAt = when (result) {
            is SyncExecutionResult.Success -> result.finishedAtMillis
            is SyncExecutionResult.Failed -> result.finishedAtMillis
            is SyncExecutionResult.Blocked -> result.finishedAtMillis
            is SyncExecutionResult.Conflict -> result.finishedAtMillis
            is SyncExecutionResult.Canceled -> result.finishedAtMillis
        }
        val error = when (result) {
            is SyncExecutionResult.Failed -> result.error
            is SyncExecutionResult.Blocked -> result.error
            is SyncExecutionResult.Conflict -> result.error
            else -> null
        }
        val phase = when (result) {
            is SyncExecutionResult.Success -> SyncPhase.SUCCESS
            is SyncExecutionResult.Failed -> SyncPhase.FAILED
            is SyncExecutionResult.Blocked -> SyncPhase.BLOCKED
            is SyncExecutionResult.Conflict -> SyncPhase.CONFLICT
            is SyncExecutionResult.Canceled -> SyncPhase.CANCELED
        }
        return SyncTaskStatus(
            key = key,
            target = request.target,
            phase = phase,
            queuedCount = 0,
            runningRequestId = null,
            lastTrigger = request.trigger,
            lastFinishedAtMillis = finishedAt,
            lastSuccessAtMillis = if (result is SyncExecutionResult.Success) finishedAt else null,
            lastError = error,
            progressLabel = when (result) {
                is SyncExecutionResult.Success -> result.detail
                is SyncExecutionResult.Canceled -> result.reason
                else -> null
            }
        )
    }

    private suspend fun publish(status: SyncTaskStatus) {
        statusStore.update(status)
    }

    private fun logCoordinatorResult(
        request: SyncRequest,
        key: SyncKey,
        result: SyncExecutionResult,
        startedAt: Long
    ) {
        val detail = coordinatorDetail(key, request)
        when (result) {
            is SyncExecutionResult.Success -> SyncDiagnostics.success(
                taskId = request.requestId,
                target = request.target.stableKey.value,
                trigger = request.trigger.name,
                startedAt = startedAt,
                detail = listOfNotNull(detail, result.detail).joinToString(" ")
            )
            is SyncExecutionResult.Failed -> SyncDiagnostics.failed(
                taskId = request.requestId,
                target = request.target.stableKey.value,
                trigger = request.trigger.name,
                startedAt = startedAt,
                error = IllegalStateException(result.error.redactedMessage ?: result.error.kind.name),
                detail = "$detail kind=${result.error.kind.name}"
            )
            is SyncExecutionResult.Blocked -> SyncDiagnostics.blocked(
                taskId = request.requestId,
                target = request.target.stableKey.value,
                trigger = request.trigger.name,
                reason = result.error.redactedMessage ?: result.error.kind.name,
                startedAt = startedAt,
                detail = "$detail kind=${result.error.kind.name}"
            )
            is SyncExecutionResult.Conflict -> SyncDiagnostics.blocked(
                taskId = request.requestId,
                target = request.target.stableKey.value,
                trigger = request.trigger.name,
                reason = result.error.redactedMessage ?: result.error.kind.name,
                startedAt = startedAt,
                detail = "$detail kind=${result.error.kind.name} phase=conflict"
            )
            is SyncExecutionResult.Canceled -> SyncDiagnostics.skipped(
                taskId = request.requestId,
                target = request.target.stableKey.value,
                trigger = request.trigger.name,
                reason = result.reason ?: "canceled",
                startedAt = startedAt,
                detail = detail
            )
        }
    }

    private fun coordinatorDetail(
        key: SyncKey,
        request: SyncRequest,
        extra: String? = null
    ): String {
        return listOfNotNull(
            "layer=coordinator",
            "key=${key.value}",
            "priority=${request.priority.name}",
            "mode=${request.mode.name}",
            "policy=${request.networkPolicy.name}",
            extra?.takeIf { it.isNotBlank() }
        ).joinToString(" ")
    }

    private fun Runtime.takeNextPendingRequest(): SyncRequest? {
        if (pendingRequests.isEmpty()) return null
        val nextIndex = pendingRequests
            .withIndex()
            .maxByOrNull { it.value.priority.rank }
            ?.index ?: return null
        return pendingRequests.removeAt(nextIndex)
    }

    private companion object {
        val replayTriggers = setOf(
            SyncTrigger.MANUAL,
            SyncTrigger.LOCAL_MUTATION,
            SyncTrigger.RESTORE,
            SyncTrigger.DELETE,
            SyncTrigger.AUTOFILL_SAVE
        )
    }
}
