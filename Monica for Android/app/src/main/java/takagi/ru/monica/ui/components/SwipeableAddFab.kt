package takagi.ru.monica.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SwipeableAddFab(
    modifier: Modifier = Modifier,
    fabContent: @Composable (expand: () -> Unit) -> Unit,
    expandedContent: @Composable (onCollapse: () -> Unit) -> Unit,
    fabBottomOffset: Dp = 0.dp,
    fabContainerColor: Color = Color.Unspecified,
    expandedContainerColor: Color = Color.Unspecified,
    onFabClickOverride: (() -> Unit)? = null,
    onExpandStateChanged: (Boolean) -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }
    val expandProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val expandAction: () -> Unit = {
        scope.launch {
            expandProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 500,
                    easing = FastOutSlowInEasing
                )
            )
            isExpanded = true
            onExpandStateChanged(true)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(if (isExpanded || expandProgress.value > 0f) 100f else 0f)
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val fabSize = 56.dp
        val fabMargin = 16.dp
        val fabSizePx = with(density) { fabSize.toPx() }
        val fabMarginPx = with(density) { fabMargin.toPx() }
        val fabBottomOffsetPx = with(density) { fabBottomOffset.toPx() }

        val currentWidth = androidx.compose.ui.unit.lerp(fabSize, maxWidth, expandProgress.value)
        val currentHeight = androidx.compose.ui.unit.lerp(fabSize, maxHeight, expandProgress.value)
        val currentCornerRadius = androidx.compose.ui.unit.lerp(16.dp, 0.dp, expandProgress.value)

        if (expandProgress.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f * expandProgress.value))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                    .clickable(enabled = isExpanded) {
                        scope.launch {
                            expandProgress.animateTo(0f)
                            isExpanded = false
                            onExpandStateChanged(false)
                        }
                    }
            )
        }

        val startX = screenWidthPx - fabSizePx - fabMarginPx
        val startY = screenHeightPx - fabSizePx - fabMarginPx - fabBottomOffsetPx
        val currentX = startX * (1f - expandProgress.value)
        val currentY = startY * (1f - expandProgress.value)

        BackHandler(enabled = isExpanded) {
            scope.launch {
                expandProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 450,
                        easing = FastOutSlowInEasing
                    )
                )
                isExpanded = false
                onExpandStateChanged(false)
            }
        }

        val fabColor = if (fabContainerColor == Color.Unspecified) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            fabContainerColor
        }
        val pageColor = if (expandedContainerColor == Color.Unspecified) {
            MaterialTheme.colorScheme.surface
        } else {
            expandedContainerColor
        }
        val currentColor = androidx.compose.ui.graphics.lerp(fabColor, pageColor, expandProgress.value)

        Box(
            modifier = Modifier
                .offset { IntOffset(currentX.roundToInt(), currentY.roundToInt()) }
                .size(currentWidth, currentHeight)
                .shadow(
                    elevation = 6.dp + (10.dp * expandProgress.value),
                    shape = RoundedCornerShape(currentCornerRadius)
                )
                .clip(RoundedCornerShape(currentCornerRadius))
                .background(currentColor)
                .clickable(
                    enabled = !isExpanded,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (onFabClickOverride != null) {
                        onFabClickOverride()
                    } else {
                        expandAction()
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(1f - expandProgress.value),
                contentAlignment = Alignment.Center
            ) {
                if (expandProgress.value < 0.8f) {
                    fabContent {
                        if (onFabClickOverride != null) {
                            onFabClickOverride()
                        } else {
                            expandAction()
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha((expandProgress.value - 0.2f).coerceAtLeast(0f) / 0.8f)
            ) {
                if (expandProgress.value > 0.01f) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        expandedContent {
                            scope.launch {
                                expandProgress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = 450,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                                isExpanded = false
                                onExpandStateChanged(false)
                            }
                        }

                        if (!isExpanded) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
