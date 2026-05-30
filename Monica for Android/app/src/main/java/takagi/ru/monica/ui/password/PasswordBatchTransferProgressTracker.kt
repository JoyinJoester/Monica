package takagi.ru.monica.ui.password

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import takagi.ru.monica.ui.components.UnifiedMoveAction

internal enum class PasswordBatchTransferPhase {
    RUNNING,
    SUCCESS
}

internal data class PasswordBatchTransferGlobalProgressState(
    val operationId: Long,
    val action: UnifiedMoveAction,
    val targetLabel: String,
    val processed: Int,
    val total: Int,
    val phase: PasswordBatchTransferPhase = PasswordBatchTransferPhase.RUNNING,
    val successCount: Int? = null
) {
    val progressFraction: Float
        get() = if (phase == PasswordBatchTransferPhase.SUCCESS) {
            1f
        } else if (total <= 0) {
            0f
        } else {
            processed.toFloat() / total.toFloat()
        }
}

internal object PasswordBatchTransferProgressTracker {

    private val _progress = MutableStateFlow<PasswordBatchTransferGlobalProgressState?>(null)
    val progress: StateFlow<PasswordBatchTransferGlobalProgressState?> = _progress.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var clearJob: Job? = null
    private var nextOperationId = 0L

    private fun allocateOperationId(): Long {
        nextOperationId += 1
        return nextOperationId
    }

    fun update(
        action: UnifiedMoveAction,
        targetLabel: String,
        processed: Int,
        total: Int
    ) {
        clearJob?.cancel()
        val safeTotal = total.coerceAtLeast(0)
        val safeProcessed = if (safeTotal > 0) {
            processed.coerceIn(0, safeTotal)
        } else {
            processed.coerceAtLeast(0)
        }
        val current = _progress.value
        val operationId = if (
            current != null &&
            current.phase == PasswordBatchTransferPhase.RUNNING &&
            current.action == action &&
            current.targetLabel == targetLabel &&
            current.total == safeTotal &&
            safeProcessed >= current.processed
        ) {
            current.operationId
        } else {
            allocateOperationId()
        }
        _progress.value = PasswordBatchTransferGlobalProgressState(
            operationId = operationId,
            action = action,
            targetLabel = targetLabel,
            processed = safeProcessed,
            total = safeTotal,
            phase = PasswordBatchTransferPhase.RUNNING
        )
    }

    fun complete(
        action: UnifiedMoveAction,
        targetLabel: String,
        successCount: Int
    ) {
        clearJob?.cancel()
        val safeCount = successCount.coerceAtLeast(0)
        if (safeCount <= 0) {
            clear()
            return
        }
        val current = _progress.value
        val operationId = if (
            current != null &&
            current.phase == PasswordBatchTransferPhase.RUNNING &&
            current.action == action &&
            current.targetLabel == targetLabel
        ) {
            current.operationId
        } else {
            allocateOperationId()
        }
        val successState = PasswordBatchTransferGlobalProgressState(
            operationId = operationId,
            action = action,
            targetLabel = targetLabel,
            processed = safeCount,
            total = safeCount,
            phase = PasswordBatchTransferPhase.SUCCESS,
            successCount = safeCount
        )
        _progress.value = successState
        clearJob = scope.launch {
            delay(1300)
            if (_progress.value == successState) {
                _progress.value = null
            }
        }
    }

    fun clear() {
        clearJob?.cancel()
        clearJob = null
        _progress.value = null
    }
}
