package takagi.ru.monica.ui.password

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.PasswordCardDisplayField
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.UnmatchedIconHandlingStrategy
import takagi.ru.monica.ui.gestures.SwipeActions

internal data class PasswordListCardBadge(
    val text: String,
    val color: Color
)

@Composable
internal fun PasswordListSingleCardItem(
    entry: PasswordEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    isSwiped: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleFavorite: (() -> Unit)?,
    unmatchedIconHandlingStrategy: UnmatchedIconHandlingStrategy,
    passwordCardDisplayMode: takagi.ru.monica.data.PasswordCardDisplayMode,
    passwordCardDisplayFields: List<PasswordCardDisplayField>,
    showAuthenticator: Boolean,
    hideOtherContentWhenAuthenticator: Boolean,
    totpTimeOffsetSeconds: Int,
    smoothAuthenticatorProgress: Boolean,
    iconCardsEnabled: Boolean,
    enableSharedBounds: Boolean,
    badge: PasswordListCardBadge? = null
) {
    SwipeActions(
        onSwipeLeft = onSwipeLeft,
        onSwipeRight = onSwipeRight,
        isSwiped = isSwiped,
        enabled = true
    ) {
        PasswordEntryCard(
            entry = entry,
            onClick = onClick,
            onLongClick = onLongClick,
            onToggleFavorite = onToggleFavorite,
            onToggleGroupCover = null,
            supportingBadge = badge
                ?.takeIf { it.text == "passkey" }
                ?.let { badgeData ->
                    {
                        PasswordListCornerBadge(
                            badge = badgeData,
                            modifier = Modifier.padding(
                                top = 10.dp,
                                end = when {
                                    isSelectionMode -> 44.dp
                                    onToggleFavorite != null -> 52.dp
                                    else -> 12.dp
                                }
                            )
                        )
                    }
                },
            isSelectionMode = isSelectionMode,
            isSelected = isSelected,
            canSetGroupCover = false,
            isInExpandedGroup = false,
            isSingleCard = true,
            iconCardsEnabled = iconCardsEnabled,
            unmatchedIconHandlingStrategy = unmatchedIconHandlingStrategy,
            passwordCardDisplayMode = passwordCardDisplayMode,
            passwordCardDisplayFields = passwordCardDisplayFields,
            showAuthenticator = showAuthenticator,
            hideOtherContentWhenAuthenticator = hideOtherContentWhenAuthenticator,
            totpTimeOffsetSeconds = totpTimeOffsetSeconds,
            smoothAuthenticatorProgress = smoothAuthenticatorProgress,
            enableSharedBounds = enableSharedBounds
        )
    }
}

@Composable
private fun PasswordListCornerBadge(
    badge: PasswordListCardBadge,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            text = badge.text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
        )
    }
}
