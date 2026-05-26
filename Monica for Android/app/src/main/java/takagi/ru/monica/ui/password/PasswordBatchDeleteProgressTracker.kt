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
    SUCCESS
}

internal data class PasswordBatchDeleteGlobalProgressState(
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

    fun update(processed: Int, total: Int) {
        clearJob?.cancel()
        val safeTotal = total.coerceAtLeast(0)
        val safeProcessed = if (safeTotal > 0) {
            processed.coerceIn(0, safeTotal)
        } else {
            processed.coerceAtLeast(0)
        }
        _progress.value = PasswordBatchDeleteGlobalProgressState(
            processed = safeProcessed,
            total = safeTotal,
            phase = PasswordBatchDeletePhase.RUNNING
        )
    }

    fun complete(successCount: Int) {
        clearJob?.cancel()
        val safeCount = successCount.coerceAtLeast(0)
        if (safeCount <= 0) {
            clear()
            return
        }
        val successState = PasswordBatchDeleteGlobalProgressState(
            processed = safeCount,
            total = safeCount,
            phase = PasswordBatchDeletePhase.SUCCESS,
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
