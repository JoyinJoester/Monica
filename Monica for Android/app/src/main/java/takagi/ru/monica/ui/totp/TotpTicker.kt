package takagi.ru.monica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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

internal fun nextSmoothTotpProgressTarget(
    progress: Float,
    periodSeconds: Int,
): Float {
    val safeProgress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val step = 1f / periodSeconds.coerceAtLeast(1).toFloat()
    return (safeProgress + step).coerceAtMost(1f)
}

internal fun shouldResetSmoothTotpProgress(
    animatedProgress: Float,
    sampledProgress: Float,
): Boolean = animatedProgress > 0.8f && sampledProgress < 0.2f

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
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val millis by produceState(System.currentTimeMillis(), smooth, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // A resumed collector must never render the shared StateFlow's cached time.
            value = System.currentTimeMillis()
            (if (smooth) smoothTicker else secondTicker).collect {
                value = System.currentTimeMillis()
            }
        }
    }
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
