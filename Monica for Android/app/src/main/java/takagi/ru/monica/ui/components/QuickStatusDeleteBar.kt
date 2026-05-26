package takagi.ru.monica.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class QuickStatusDeletePhase {
    RUNNING,
    SUCCESS
}

data class QuickStatusDeleteState(
    val processed: Int,
    val total: Int,
    val phase: QuickStatusDeletePhase = QuickStatusDeletePhase.RUNNING,
    val successCount: Int? = null
) {
    val progressFraction: Float
        get() = if (phase == QuickStatusDeletePhase.SUCCESS) {
            1f
        } else if (total <= 0) {
            0f
        } else {
            processed.toFloat() / total.toFloat()
        }
}

@Composable
fun QuickStatusDeleteBar(
    state: QuickStatusDeleteState,
    modifier: Modifier = Modifier
) {
    if (state.phase == QuickStatusDeletePhase.SUCCESS) {
        QuickStatusDeleteSuccessStatus(
            state = state,
            modifier = modifier
        )
        return
    }

    val progress by animateFloatAsState(
        targetValue = state.progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 260),
        label = "quick-status-delete-progress"
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 5.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                tonalElevation = 1.dp,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = "正在删除",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )
            Text(
                text = "${state.processed}/${state.total.coerceAtLeast(1)}",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
            QuickStatusDeleteProgressPill(
                progress = progress,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .width(44.dp)
                    .height(18.dp)
            )
        }
    }
}

@Composable
private fun QuickStatusDeleteProgressPill(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
    val progressColor = MaterialTheme.colorScheme.error.copy(alpha = 0.72f)
    Canvas(modifier = modifier) {
        val radius = size.height / 2f
        drawRoundRect(
            color = trackColor,
            cornerRadius = CornerRadius(radius, radius)
        )
        drawRoundRect(
            color = progressColor,
            size = Size(width = size.width * progress.coerceIn(0f, 1f), height = size.height),
            cornerRadius = CornerRadius(radius, radius)
        )
    }
}

@Composable
private fun QuickStatusDeleteSuccessStatus(
    state: QuickStatusDeleteState,
    modifier: Modifier
) {
    val count = state.successCount ?: state.processed
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "删除成功，已删除${count}条",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
