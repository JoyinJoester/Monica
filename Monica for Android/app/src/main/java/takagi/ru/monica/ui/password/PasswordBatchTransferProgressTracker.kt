package takagi.ru.monica.ui.password

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import takagi.ru.monica.ui.components.UnifiedMoveAction

internal data class PasswordBatchTransferGlobalProgressState(
    val action: UnifiedMoveAction,
    val targetLabel: String,
    val processed: Int,
    val total: Int
) {
    val progressFraction: Float
        get() = if (total <= 0) 0f else processed.toFloat() / total.toFloat()
}

internal object PasswordBatchTransferProgressTracker {

    private val _progress = MutableStateFlow<PasswordBatchTransferGlobalProgressState?>(null)
    val progress: StateFlow<PasswordBatchTransferGlobalProgressState?> = _progress.asStateFlow()

    fun update(
        action: UnifiedMoveAction,
        targetLabel: String,
        processed: Int,
        total: Int
    ) {
        val safeTotal = total.coerceAtLeast(0)
        val safeProcessed = if (safeTotal > 0) {
            processed.coerceIn(0, safeTotal)
        } else {
            processed.coerceAtLeast(0)
        }
        _progress.value = PasswordBatchTransferGlobalProgressState(
            action = action,
            targetLabel = targetLabel,
            processed = safeProcessed,
            total = safeTotal
        )
    }

    fun clear() {
        _progress.value = null
    }
}
