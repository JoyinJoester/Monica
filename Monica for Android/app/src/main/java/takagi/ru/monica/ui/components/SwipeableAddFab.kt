package takagi.ru.monica.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

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

    fun animateExpanded(targetExpanded: Boolean) {
        if (isExpanded == targetExpanded) return
        isExpanded = targetExpanded
        onExpandStateChanged(targetExpanded)
    }

    val expandAction: () -> Unit = {
        animateExpanded(true)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(if (isExpanded) 100f else 0f)
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = tween(300)) +
                scaleIn(initialScale = 0.9f, animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(300)) +
                scaleOut(targetScale = 0.9f, animationSpec = tween(400)),
            modifier = Modifier.matchParentSize()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (expandedContainerColor == Color.Unspecified) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            expandedContainerColor
                        }
                    ),
                color = if (expandedContainerColor == Color.Unspecified) {
                    MaterialTheme.colorScheme.surface
                } else {
                    expandedContainerColor
                }
            ) {
                expandedContent { animateExpanded(false) }
            }
        }

        BackHandler(enabled = isExpanded) {
            animateExpanded(false)
        }

        val fabColor = if (fabContainerColor == Color.Unspecified) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            fabContainerColor
        }
        AnimatedVisibility(
            visible = !isExpanded,
            enter = fadeIn(animationSpec = tween(160)) +
                scaleIn(initialScale = 0.9f, animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(120)) +
                scaleOut(targetScale = 0.9f, animationSpec = tween(140)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = fabBottomOffset + 16.dp)
        ) {
            Box(
                modifier = Modifier
                .size(56.dp)
                .background(fabColor, RoundedCornerShape(16.dp))
                .clickable(
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
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    fabContent {
                        if (onFabClickOverride != null) {
                            onFabClickOverride()
                        } else {
                            expandAction()
                        }
                    }
                }
            }
        }
    }
}
