package takagi.ru.monica.ui.password

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class PasswordBatchDeletePhase {
    RUNNING,
    SUCCESS,
    FAILURE
}

internal data class PasswordBatchDeleteGlobalProgressState(
    val operationId: Long,
    val processed: Int,
    val total: Int,
    val phase: PasswordBatchDeletePhase = PasswordBatchDeletePhase.RUNNING,
    val successCount: Int? = null
) {
    val progressFraction: Float
        get() = if (phase == PasswordBatchDeletePhase.SUCCESS) {
            1f
        } else if (total <= 0) {
            0f
        } else {
            processed.toFloat() / total.toFloat()
        }
}

internal object PasswordBatchDeleteProgressTracker {

    private val _progress = MutableStateFlow<PasswordBatchDeleteGlobalProgressState?>(null)
    val progress: StateFlow<PasswordBatchDeleteGlobalProgressState?> = _progress.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var clearJob: Job? = null
    private var nextOperationId = 0L

    private fun allocateOperationId(): Long {
        nextOperationId += 1
        return nextOperationId
    }

    fun update(processed: Int, total: Int) {
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
            current.phase == PasswordBatchDeletePhase.RUNNING &&
            current.total == safeTotal &&
            safeProcessed >= current.processed
        ) {
            current.operationId
        } else {
            allocateOperationId()
        }
        _progress.value = PasswordBatchDeleteGlobalProgressState(
            operationId = operationId,
            processed = safeProcessed,
            total = safeTotal,
            phase = PasswordBatchDeletePhase.RUNNING
        )
    }

    fun complete(successCount: Int) {
        clearJob?.cancel()
        val safeCount = successCount.coerceAtLeast(0)
        val current = _progress.value
        if (safeCount <= 0 && current == null) {
            clear()
            return
        }
        val operationId = if (
            current != null &&
            current.phase == PasswordBatchDeletePhase.RUNNING
        ) {
            current.operationId
        } else {
            allocateOperationId()
        }
        val total = (current?.total ?: safeCount).coerceAtLeast(safeCount)
        val successState = PasswordBatchDeleteGlobalProgressState(
            operationId = operationId,
            processed = total,
            total = total,
            phase = if (safeCount == total) PasswordBatchDeletePhase.SUCCESS else PasswordBatchDeletePhase.FAILURE,
            successCount = safeCount
        )
        publishTerminal(successState)
    }

    fun fail() {
        val current = _progress.value ?: return
        publishTerminal(current.copy(phase = PasswordBatchDeletePhase.FAILURE, successCount = null))
    }

    private fun publishTerminal(successState: PasswordBatchDeleteGlobalProgressState) {
        clearJob?.cancel()
        _progress.value = successState
        clearJob = scope.launch {
            if (successState.phase == PasswordBatchDeletePhase.SUCCESS) delay(1300) else delay(8000)
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
