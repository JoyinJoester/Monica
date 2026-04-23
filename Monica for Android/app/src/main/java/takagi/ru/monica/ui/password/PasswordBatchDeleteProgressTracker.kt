package takagi.ru.monica.ui.password

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PasswordBatchDeleteGlobalProgressState(
    val processed: Int,
    val total: Int
) {
    val progressFraction: Float
        get() = if (total <= 0) 0f else processed.toFloat() / total.toFloat()
}

internal object PasswordBatchDeleteProgressTracker {

    private val _progress = MutableStateFlow<PasswordBatchDeleteGlobalProgressState?>(null)
    val progress: StateFlow<PasswordBatchDeleteGlobalProgressState?> = _progress.asStateFlow()

    fun update(processed: Int, total: Int) {
        val safeTotal = total.coerceAtLeast(0)
        val safeProcessed = if (safeTotal > 0) {
            processed.coerceIn(0, safeTotal)
        } else {
            processed.coerceAtLeast(0)
        }
        _progress.value = PasswordBatchDeleteGlobalProgressState(
            processed = safeProcessed,
            total = safeTotal
        )
    }

    fun clear() {
        _progress.value = null
    }
}
