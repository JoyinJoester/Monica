package takagi.ru.monica.ui.common.pull

import kotlin.math.log10
import kotlin.math.sqrt

private const val MIN_PULL_RESISTANCE = 0.12f
private const val MAX_PULL_RESISTANCE = 0.46f

fun calculateDampedPullOffset(
    currentOffset: Float,
    dragDelta: Float,
    maxDragDistance: Float
): Float {
    if (dragDelta <= 0f) {
        return currentOffset.coerceAtLeast(0f)
    }

    val progress = if (maxDragDistance <= 0f) {
        0f
    } else {
        (currentOffset / maxDragDistance).coerceIn(0f, 1f)
    }
    val easedProgress = (
        progress * 0.4f +
            sqrt(progress) * 0.6f
        ).coerceIn(0f, 1f)
    val resistance = lerp(
        start = MAX_PULL_RESISTANCE,
        stop = MIN_PULL_RESISTANCE,
        fraction = easedProgress
    )
    return (currentOffset + dragDelta * resistance).coerceAtMost(maxDragDistance)
}

fun calculatePullVisualProgress(rawProgress: Float): Float {
    if (rawProgress <= 0f) {
        return 0f
    }
    return log10(rawProgress.coerceAtLeast(0f) * 10f + 1f).coerceIn(0f, 1f)
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction.coerceIn(0f, 1f)
}
