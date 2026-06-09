package takagi.ru.monica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive

private const val SMOOTH_TICK_MS = 50L
private const val TICK_STOP_TIMEOUT_MS = 5_000L

private val tickerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private val smoothTicker = tickerFlow(smooth = true).stateIn(
    scope = tickerScope,
    started = SharingStarted.WhileSubscribed(TICK_STOP_TIMEOUT_MS),
    initialValue = System.currentTimeMillis()
)

private val secondTicker = tickerFlow(smooth = false).stateIn(
    scope = tickerScope,
    started = SharingStarted.WhileSubscribed(TICK_STOP_TIMEOUT_MS),
    initialValue = System.currentTimeMillis()
)

@Composable
fun rememberTotpTickerMillis(smooth: Boolean): Long {
    val millis by (if (smooth) smoothTicker else secondTicker).collectAsState()
    return millis
}

private fun tickerFlow(smooth: Boolean) = flow {
    while (currentCoroutineContext().isActive) {
        val now = System.currentTimeMillis()
        emit(now)
        val waitMillis = if (smooth) {
            SMOOTH_TICK_MS
        } else {
            (1000L - (now % 1000L)).coerceAtLeast(16L)
        }
        delay(waitMillis)
    }
}
