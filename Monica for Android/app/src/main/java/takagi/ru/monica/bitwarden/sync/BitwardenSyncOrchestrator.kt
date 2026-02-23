package takagi.ru.monica.bitwarden.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

enum class SyncTriggerReason {
    PAGE_ENTER,
    LOCAL_MUTATION,
    APP_RESUME,
    MANUAL,
    PERIODIC,
    RETRY
}

enum class SyncBlockReason {
    AUTO_SYNC_DISABLED,
    NETWORK_UNAVAILABLE,
    WIFI_REQUIRED,
    VAULT_LOCKED,
    AUTH_REQUIRED
}

sealed class SyncExecutionOutcome {
    data class Success(val syncedCount: Int, val conflictCount: Int) : SyncExecutionOutcome()
    data class RetryableError(val message: String) : SyncExecutionOutcome()
    data class Blocked(val reason: SyncBlockReason, val message: String? = null) : SyncExecutionOutcome()
    data class FatalError(val message: String) : SyncExecutionOutcome()
}

enum class NetworkGateResult {
    ALLOWED,
    NETWORK_UNAVAILABLE,
    WIFI_REQUIRED
}

data class SyncManagerConfig(
    val pageEnterThrottleMs: Long = 45_000L,
    val appResumeThrottleMs: Long = 60_000L,
    val localMutationDebounceMs: Long = 2_000L,
    val retryBaseDelayMs: Long = 5_000L,
    val retryMaxDelayMs: Long = 15 * 60 * 1000L,
    val retryMaxAttempts: Int = 5
)

data class VaultSyncStatus(
    val isRunning: Boolean = false,
    val queuedReason: SyncTriggerReason? = null,
    val lastTriggerReason: SyncTriggerReason? = null,
    val blockedReason: SyncBlockReason? = null,
    val lastError: String? = null,
    val lastSuccessAt: Long? = null,
    val nextRetryAt: Long? = null,
    val retryAttempt: Int = 0
)

private data class VaultRuntime(
    var isRunning: Boolean = false,
    var pendingReason: SyncTriggerReason? = null,
    var lastPageEnterAt: Long = 0L,
    var lastAppResumeAt: Long = 0L,
    var retryAttempt: Int = 0,
    var mutationDebounceJob: Job? = null,
    var retryJob: Job? = null
)

class BitwardenSyncOrchestrator(
    private val scope: CoroutineScope,
    private val config: SyncManagerConfig = SyncManagerConfig(),
    private val isAutoSyncEnabled: () -> Boolean,
    private val checkNetwork: () -> NetworkGateResult,
    private val isVaultUnlocked: (Long) -> Boolean,
    private val executeSync: suspend (vaultId: Long, silent: Boolean) -> SyncExecutionOutcome,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val mutex = Mutex()
    private val runtimes = mutableMapOf<Long, VaultRuntime>()
    private val _statusByVault = MutableStateFlow<Map<Long, VaultSyncStatus>>(emptyMap())
    val statusByVault: StateFlow<Map<Long, VaultSyncStatus>> = _statusByVault.asStateFlow()

    fun requestSync(vaultId: Long, reason: SyncTriggerReason, force: Boolean = false) {
        scope.launch {
            processRequest(vaultId = vaultId, reason = reason, force = force)
        }
    }

    fun clearVault(vaultId: Long) {
        scope.launch {
            mutex.withLock {
                runtimes.remove(vaultId)?.let { runtime ->
                    runtime.retryJob?.cancel()
                    runtime.mutationDebounceJob?.cancel()
                }
                updateStatus(vaultId) { null }
            }
        }
    }

    private suspend fun processRequest(vaultId: Long, reason: SyncTriggerReason, force: Boolean) {
        var runNow = false
        var silent = reason != SyncTriggerReason.MANUAL

        mutex.withLock {
            val runtime = runtimeOf(vaultId)

            if (reason == SyncTriggerReason.LOCAL_MUTATION && !force) {
                runtime.mutationDebounceJob?.cancel()
                runtime.mutationDebounceJob = scope.launch {
                    delay(config.localMutationDebounceMs)
                    requestSync(vaultId, SyncTriggerReason.LOCAL_MUTATION, force = true)
                }
                setQueued(vaultId, runtime, reason)
                return
            }

            if (runtime.isRunning) {
                runtime.pendingReason = pickHigherPriority(runtime.pendingReason, reason)
                setQueued(vaultId, runtime, runtime.pendingReason)
                return
            }

            if (isAutoReason(reason) && !force) {
                if (!isAutoSyncEnabled()) {
                    setBlocked(vaultId, runtime, SyncBlockReason.AUTO_SYNC_DISABLED, "自动同步已关闭")
                    return
                }
                if (!passesThrottle(runtime, reason, nowProvider())) return
            }

            when (checkNetwork()) {
                NetworkGateResult.ALLOWED -> Unit
                NetworkGateResult.NETWORK_UNAVAILABLE -> {
                    setBlocked(vaultId, runtime, SyncBlockReason.NETWORK_UNAVAILABLE, "网络不可用")
                    return
                }
                NetworkGateResult.WIFI_REQUIRED -> {
                    setBlocked(vaultId, runtime, SyncBlockReason.WIFI_REQUIRED, "仅 Wi-Fi 同步")
                    return
                }
            }

            if (!isVaultUnlocked(vaultId)) {
                setBlocked(vaultId, runtime, SyncBlockReason.VAULT_LOCKED, "Vault 未解锁")
                return
            }

            runtime.retryJob?.cancel()
            runtime.retryJob = null
            runtime.isRunning = true
            silent = reason != SyncTriggerReason.MANUAL
            updateStatus(vaultId) { old ->
                val existing = old ?: VaultSyncStatus()
                existing.copy(
                    isRunning = true,
                    queuedReason = null,
                    lastTriggerReason = reason,
                    blockedReason = null,
                    nextRetryAt = null
                )
            }
            runNow = true
        }

        if (!runNow) return

        val outcome = executeSync(vaultId, silent)

        var pendingReasonToReplay: SyncTriggerReason? = null
        mutex.withLock {
            val runtime = runtimeOf(vaultId)
            runtime.isRunning = false

            when (outcome) {
                is SyncExecutionOutcome.Success -> {
                    runtime.retryAttempt = 0
                    updateStatus(vaultId) { old ->
                        val existing = old ?: VaultSyncStatus()
                        existing.copy(
                            isRunning = false,
                            queuedReason = null,
                            blockedReason = null,
                            lastError = null,
                            lastSuccessAt = nowProvider(),
                            retryAttempt = 0,
                            nextRetryAt = null
                        )
                    }
                }

                is SyncExecutionOutcome.Blocked -> {
                    updateStatus(vaultId) { old ->
                        val existing = old ?: VaultSyncStatus()
                        existing.copy(
                            isRunning = false,
                            blockedReason = outcome.reason,
                            lastError = outcome.message ?: existing.lastError,
                            queuedReason = runtime.pendingReason
                        )
                    }
                }

                is SyncExecutionOutcome.RetryableError -> {
                    if (runtime.retryAttempt < config.retryMaxAttempts) {
                        runtime.retryAttempt += 1
                        scheduleRetryLocked(vaultId, runtime, outcome.message)
                    } else {
                        updateStatus(vaultId) { old ->
                            val existing = old ?: VaultSyncStatus()
                            existing.copy(
                                isRunning = false,
                                lastError = outcome.message,
                                nextRetryAt = null,
                                retryAttempt = runtime.retryAttempt
                            )
                        }
                    }
                }

                is SyncExecutionOutcome.FatalError -> {
                    runtime.retryAttempt = 0
                    updateStatus(vaultId) { old ->
                        val existing = old ?: VaultSyncStatus()
                        existing.copy(
                            isRunning = false,
                            lastError = outcome.message,
                            blockedReason = null,
                            retryAttempt = 0,
                            nextRetryAt = null
                        )
                    }
                }
            }

            pendingReasonToReplay = runtime.pendingReason
            runtime.pendingReason = null
        }

        if (pendingReasonToReplay != null) {
            requestSync(vaultId, pendingReasonToReplay ?: SyncTriggerReason.RETRY, force = true)
        }
    }

    private fun runtimeOf(vaultId: Long): VaultRuntime {
        return runtimes.getOrPut(vaultId) { VaultRuntime() }
    }

    private fun isAutoReason(reason: SyncTriggerReason): Boolean {
        return reason != SyncTriggerReason.MANUAL
    }

    private fun passesThrottle(runtime: VaultRuntime, reason: SyncTriggerReason, now: Long): Boolean {
        return when (reason) {
            SyncTriggerReason.PAGE_ENTER -> {
                if (now - runtime.lastPageEnterAt < config.pageEnterThrottleMs) {
                    false
                } else {
                    runtime.lastPageEnterAt = now
                    true
                }
            }

            SyncTriggerReason.APP_RESUME -> {
                if (now - runtime.lastAppResumeAt < config.appResumeThrottleMs) {
                    false
                } else {
                    runtime.lastAppResumeAt = now
                    true
                }
            }

            else -> true
        }
    }

    private fun setQueued(vaultId: Long, runtime: VaultRuntime, reason: SyncTriggerReason?) {
        updateStatus(vaultId) { old ->
            val existing = old ?: VaultSyncStatus()
            existing.copy(
                isRunning = runtime.isRunning,
                queuedReason = reason,
                blockedReason = null
            )
        }
    }

    private fun setBlocked(
        vaultId: Long,
        runtime: VaultRuntime,
        reason: SyncBlockReason,
        message: String
    ) {
        updateStatus(vaultId) { old ->
            val existing = old ?: VaultSyncStatus()
            existing.copy(
                isRunning = false,
                blockedReason = reason,
                lastError = message,
                queuedReason = runtime.pendingReason
            )
        }
    }

    private fun scheduleRetryLocked(vaultId: Long, runtime: VaultRuntime, message: String) {
        val delayMs = min(
            config.retryMaxDelayMs,
            config.retryBaseDelayMs * (1L shl (runtime.retryAttempt - 1))
        )
        val nextRetryAt = nowProvider() + delayMs
        runtime.retryJob?.cancel()
        runtime.retryJob = scope.launch {
            delay(delayMs)
            requestSync(vaultId, SyncTriggerReason.RETRY, force = true)
        }
        updateStatus(vaultId) { old ->
            val existing = old ?: VaultSyncStatus()
            existing.copy(
                isRunning = false,
                lastError = message,
                nextRetryAt = nextRetryAt,
                retryAttempt = runtime.retryAttempt
            )
        }
    }

    private fun pickHigherPriority(
        current: SyncTriggerReason?,
        incoming: SyncTriggerReason
    ): SyncTriggerReason {
        if (current == null) return incoming
        return if (priorityOf(incoming) >= priorityOf(current)) incoming else current
    }

    private fun priorityOf(reason: SyncTriggerReason): Int = when (reason) {
        SyncTriggerReason.MANUAL -> 6
        SyncTriggerReason.LOCAL_MUTATION -> 5
        SyncTriggerReason.RETRY -> 4
        SyncTriggerReason.APP_RESUME -> 3
        SyncTriggerReason.PAGE_ENTER -> 2
        SyncTriggerReason.PERIODIC -> 1
    }

    private fun updateStatus(
        vaultId: Long,
        transform: (VaultSyncStatus?) -> VaultSyncStatus?
    ) {
        val current = _statusByVault.value.toMutableMap()
        val updated = transform(current[vaultId])
        if (updated == null) {
            current.remove(vaultId)
        } else {
            current[vaultId] = updated
        }
        _statusByVault.value = current
    }
}
