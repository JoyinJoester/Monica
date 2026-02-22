package takagi.ru.monica.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class PullActionVisualState {
    IDLE,
    SEARCH_READY,
    SYNC_READY,
    SYNCING,
    SYNC_DONE
}

@Composable
private fun ExpressiveShapeIndicator(
    state: PullActionVisualState,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pull_sync_icon_rotation")
    val syncRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pull_sync_rotation"
    )
    val icon = when (state) {
        PullActionVisualState.SEARCH_READY, PullActionVisualState.IDLE -> Icons.Default.Search
        PullActionVisualState.SYNC_READY, PullActionVisualState.SYNCING -> Icons.Default.Sync
        PullActionVisualState.SYNC_DONE -> Icons.Default.Check
    }
    val iconBgColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = iconBgColor,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            rotationZ = if (state == PullActionVisualState.SYNCING) syncRotation else 0f
                        }
                )
            }
        }
    }
}

@Composable
fun PullGestureIndicator(
    state: PullActionVisualState,
    searchProgress: Float,
    syncProgress: Float,
    text: String,
    modifier: Modifier = Modifier
) {
    val highlight = state == PullActionVisualState.SYNC_READY ||
        state == PullActionVisualState.SYNCING ||
        state == PullActionVisualState.SYNC_DONE
    val containerColor = if (highlight) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (highlight) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExpressiveShapeIndicator(
                    state = state,
                    progress = if (state == PullActionVisualState.SEARCH_READY) searchProgress else syncProgress
                )

                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
