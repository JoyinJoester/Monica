package takagi.ru.monica.sync

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object SyncTaskRunner {
    private val tasks = ConcurrentHashMap<String, PendingTask>()
    private val statusStore = DefaultSyncStatusStore()
    private val networkGate = MutableSyncNetworkGate()
    private val coordinator = DefaultSyncCoordinator(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        executors = listOf(BlockExecutor),
        statusStore = statusStore,
        networkGate = networkGate
    )

    val statuses = statusStore.statuses

    suspend fun request(
        request: SyncRequest,
        block: suspend () -> Unit
    ): SyncEnqueueResult {
        tasks[request.requestId] = PendingTask(block = block)
        val result = coordinator.request(request)
        when (result) {
            is SyncEnqueueResult.Accepted -> {
                result.replacedRequestId?.let { requestId ->
                    tasks.remove(requestId)?.drop("replaced")
                }
            }
            else -> {
                tasks.remove(request.requestId)?.drop(result.dropReason())
            }
        }
        return result
    }

    suspend fun <T> requestAndAwait(
        request: SyncRequest,
        block: suspend () -> T
    ): SyncTaskAwaitResult<T> {
        val completion = CompletableDeferred<T>()
        tasks[request.requestId] = PendingTask(
            block = {
                try {
                    completion.complete(block())
                } catch (error: SyncTaskBlockedException) {
                    completion.completeExceptionally(error)
                } catch (error: CancellationException) {
                    completion.cancel(error)
                    throw error
                } catch (error: Exception) {
                    completion.completeExceptionally(error)
                    throw error
                }
            },
            onDropped = { reason ->
                completion.cancel(CancellationException(reason))
            },
            onBlocked = { error ->
                completion.completeExceptionally(SyncTaskBlockedException(error))
            }
        )
        val enqueueResult = coordinator.request(request)
        return when (enqueueResult) {
            is SyncEnqueueResult.Accepted -> {
                enqueueResult.replacedRequestId?.let { requestId ->
                    tasks.remove(requestId)?.drop("replaced")
                }
                try {
                    SyncTaskAwaitResult.Completed(
                        value = completion.await(),
                        enqueueStatus = enqueueResult.status
                    )
                } catch (error: SyncTaskBlockedException) {
                    SyncTaskAwaitResult.Blocked(error.syncError)
                } catch (error: CancellationException) {
                    SyncTaskAwaitResult.Canceled(error.message)
                } catch (error: Exception) {
                    SyncTaskAwaitResult.Failed(error)
                }
            }
            is SyncEnqueueResult.Merged -> {
                tasks.remove(request.requestId)?.drop("merged")
                SyncTaskAwaitResult.Merged(enqueueResult.existingStatus)
            }
            is SyncEnqueueResult.Skipped -> {
                tasks.remove(request.requestId)?.drop(enqueueResult.reason)
                SyncTaskAwaitResult.Skipped(enqueueResult.reason)
            }
            is SyncEnqueueResult.Blocked -> {
                tasks.remove(request.requestId)?.drop(enqueueResult.error.kind.name)
                SyncTaskAwaitResult.Blocked(enqueueResult.error)
            }
        }
    }

    fun observe(target: SyncTarget) = coordinator.observe(target)

    fun observe(key: SyncKey) = coordinator.observe(key)

    fun installNetworkGate(gate: SyncNetworkGate) {
        networkGate.delegate = gate
    }

    internal fun pendingTaskCountForTest(): Int = tasks.size

    private data class PendingTask(
        val block: suspend () -> Unit,
        val onDropped: (String) -> Unit = {},
        val onBlocked: (SyncError) -> Unit = {}
    ) {
        fun drop(reason: String) {
            onDropped(reason)
        }

        fun notifyBlocked(error: SyncError) {
            onBlocked(error)
        }
    }

    private class MutableSyncNetworkGate : SyncNetworkGate {
        @Volatile
        var delegate: SyncNetworkGate = SyncNetworkGate { null }

        override fun evaluate(policy: SyncNetworkPolicy): SyncError? {
            return delegate.evaluate(policy)
        }
    }

    private object BlockExecutor : SyncExecutor {
        override fun canExecute(target: SyncTarget): Boolean = true

        override suspend fun execute(request: SyncRequest): SyncExecutionResult {
            return try {
                val block = tasks.remove(request.requestId)
                    ?: return SyncExecutionResult.Canceled(
                        finishedAtMillis = System.currentTimeMillis(),
                        reason = "task_not_available"
                    )
                networkGate.evaluate(request.networkPolicy)?.let { error ->
                    SyncDiagnostics.blocked(
                        taskId = request.requestId,
                        target = request.target.stableKey.value,
                        trigger = request.trigger.name,
                        reason = error.redactedMessage ?: error.kind.name,
                        detail = "policy=${request.networkPolicy.name}"
                    )
                    block.notifyBlocked(error)
                    return SyncExecutionResult.Blocked(
                        finishedAtMillis = System.currentTimeMillis(),
                        error = error
                    )
                }
                block.block()
                SyncExecutionResult.Success(finishedAtMillis = System.currentTimeMillis())
            } catch (error: CancellationException) {
                SyncExecutionResult.Canceled(
                    finishedAtMillis = System.currentTimeMillis(),
                    reason = error.message
                )
            } catch (error: Exception) {
                SyncExecutionResult.Failed(
                    finishedAtMillis = System.currentTimeMillis(),
                    error = SyncError(
                        kind = SyncErrorKind.UNEXPECTED,
                        redactedMessage = error.message,
                        retryable = false
                    )
                )
            }
        }
    }

    private class SyncTaskBlockedException(val syncError: SyncError) : Exception(
        syncError.redactedMessage ?: syncError.kind.name
    )
}

sealed class SyncTaskAwaitResult<out T> {
    data class Completed<T>(
        val value: T,
        val enqueueStatus: SyncTaskStatus
    ) : SyncTaskAwaitResult<T>()

    data class Merged(val status: SyncTaskStatus) : SyncTaskAwaitResult<Nothing>()
    data class Skipped(val reason: String) : SyncTaskAwaitResult<Nothing>()
    data class Blocked(val error: SyncError) : SyncTaskAwaitResult<Nothing>()
    data class Canceled(val reason: String?) : SyncTaskAwaitResult<Nothing>()
    data class Failed(val error: Exception) : SyncTaskAwaitResult<Nothing>()
}

private fun SyncEnqueueResult.dropReason(): String {
    return when (this) {
        is SyncEnqueueResult.Accepted -> "accepted"
        is SyncEnqueueResult.Merged -> "merged"
        is SyncEnqueueResult.Skipped -> reason
        is SyncEnqueueResult.Blocked -> error.kind.name
    }
}
