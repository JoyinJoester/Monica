package takagi.ru.monica.ui.password

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun PasswordManualStackGroup(
    groupKey: String,
    cards: List<PasswordPageCardItemUi>,
    isSelectionMode: Boolean,
    selectedItemKeys: Set<String>,
    onCardClick: (PasswordPageCardItemUi) -> Unit,
    onToggleCardSelection: (PasswordPageCardItemUi) -> Unit,
    onToggleGroupSelection: (List<PasswordPageCardItemUi>) -> Unit,
    onRequestDelete: (List<PasswordPageCardItemUi>) -> Unit,
    onToggleFavorite: (PasswordPageCardItemUi) -> Unit,
    iconCardsEnabled: Boolean,
    unmatchedIconHandlingStrategy: takagi.ru.monica.data.UnmatchedIconHandlingStrategy,
    passwordCardDisplayMode: takagi.ru.monica.data.PasswordCardDisplayMode,
    passwordCardDisplayFields: List<takagi.ru.monica.data.PasswordCardDisplayField>,
    showAuthenticator: Boolean,
    hideOtherContentWhenAuthenticator: Boolean,
    totpTimeOffsetSeconds: Int,
    smoothAuthenticatorProgress: Boolean
) {
    if (cards.isEmpty()) return

    var expanded by rememberSaveable(groupKey) { mutableStateOf(false) }
    val leadCard = cards.first()

    if (!expanded) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val stackCount = cards.size.coerceAtMost(3)
            for (index in (stackCount - 1) downTo 1) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = (index * 4).dp, start = 4.dp, end = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = (index + 1).dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Box(modifier = Modifier.size(width = 1.dp, height = 72.dp))
                }
            }
            PasswordListSingleCardItem(
                entry = leadCard.entry,
                onClick = {
                    if (isSelectionMode) {
                        onToggleGroupSelection(cards)
                    } else {
                        expanded = true
                    }
                },
                onLongClick = { onToggleGroupSelection(cards) },
                onSwipeLeft = { onRequestDelete(cards) },
                onSwipeRight = { onToggleGroupSelection(cards) },
                isSwiped = false,
                isSelectionMode = isSelectionMode,
                isSelected = cards.all { it.key in selectedItemKeys },
                onToggleFavorite = if (leadCard.supportsFavorite) {
                    { onToggleFavorite(leadCard) }
                } else {
                    null
                },
                unmatchedIconHandlingStrategy = unmatchedIconHandlingStrategy,
                passwordCardDisplayMode = passwordCardDisplayMode,
                passwordCardDisplayFields = passwordCardDisplayFields,
                showAuthenticator = showAuthenticator,
                hideOtherContentWhenAuthenticator = hideOtherContentWhenAuthenticator,
                totpTimeOffsetSeconds = totpTimeOffsetSeconds,
                smoothAuthenticatorProgress = smoothAuthenticatorProgress,
                iconCardsEnabled = iconCardsEnabled,
                enableSharedBounds = false,
                badge = leadCard.badgeText?.let { text ->
                    PasswordListCardBadge(
                        text = text,
                        color = leadCard.badgeColor ?: MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        cards.forEach { card ->
            PasswordListSingleCardItem(
                entry = card.entry,
                onClick = {
                    if (isSelectionMode) {
                        onToggleCardSelection(card)
                    } else {
                        onCardClick(card)
                    }
                },
                onLongClick = { onToggleCardSelection(card) },
                onSwipeLeft = { onRequestDelete(listOf(card)) },
                onSwipeRight = { onToggleCardSelection(card) },
                isSwiped = false,
                isSelectionMode = isSelectionMode,
                isSelected = card.key in selectedItemKeys,
                onToggleFavorite = if (card.supportsFavorite) {
                    { onToggleFavorite(card) }
                } else {
                    null
                },
                unmatchedIconHandlingStrategy = unmatchedIconHandlingStrategy,
                passwordCardDisplayMode = passwordCardDisplayMode,
                passwordCardDisplayFields = passwordCardDisplayFields,
                showAuthenticator = showAuthenticator,
                hideOtherContentWhenAuthenticator = hideOtherContentWhenAuthenticator,
                totpTimeOffsetSeconds = totpTimeOffsetSeconds,
                smoothAuthenticatorProgress = smoothAuthenticatorProgress,
                iconCardsEnabled = iconCardsEnabled,
                enableSharedBounds = false,
                badge = card.badgeText?.let { text ->
                    PasswordListCardBadge(
                        text = text,
                        color = card.badgeColor ?: MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
    }
}
