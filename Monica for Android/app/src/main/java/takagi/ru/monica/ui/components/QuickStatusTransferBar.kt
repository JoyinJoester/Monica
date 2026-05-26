package takagi.ru.monica.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class QuickStatusTransferPhase {
    RUNNING,
    SUCCESS
}

data class QuickStatusTransferState(
    val action: UnifiedMoveAction,
    val sourceLabel: String,
    val targetLabel: String,
    val processed: Int,
    val total: Int,
    val phase: QuickStatusTransferPhase = QuickStatusTransferPhase.RUNNING,
    val successCount: Int? = null
) {
    val progressFraction: Float
        get() = if (phase == QuickStatusTransferPhase.SUCCESS) {
            1f
        } else if (total <= 0) {
            0f
        } else {
            processed.toFloat() / total.toFloat()
        }
}

@Composable
fun QuickStatusTransferBar(
    state: QuickStatusTransferState,
    modifier: Modifier = Modifier
) {
    if (state.phase == QuickStatusTransferPhase.SUCCESS) {
        QuickStatusTransferSuccessStatus(
            state = state,
            modifier = modifier
        )
        return
    }

    val progress by animateFloatAsState(
        targetValue = state.progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 260),
        label = "quick-status-transfer-progress"
    )
    val sourceWeight = (1.12f - 0.58f * progress).coerceAtLeast(0.42f)
    val targetWeight = (0.58f + 0.72f * progress).coerceAtMost(1.42f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickStatusTransferPathChip(
            text = state.sourceLabel,
            modifier = Modifier.weight(sourceWeight),
            isTarget = false
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        QuickStatusTransferPathChip(
            text = state.targetLabel,
            modifier = Modifier.weight(targetWeight),
            isTarget = true
        )
    }
}

@Composable
private fun QuickStatusTransferPathChip(
    text: String,
    modifier: Modifier,
    isTarget: Boolean
) {
    Surface(
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isTarget) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
        } else {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
        },
        contentColor = if (isTarget) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        tonalElevation = if (isTarget) 2.dp else 1.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickStatusTransferSuccessStatus(
    state: QuickStatusTransferState,
    modifier: Modifier
) {
    val actionText = when (state.action) {
        UnifiedMoveAction.MOVE -> "移动"
        UnifiedMoveAction.COPY -> "复制"
    }
    val count = state.successCount ?: state.processed
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${actionText}成功，已${actionText}${count}条",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
