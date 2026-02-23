package takagi.ru.monica.ui

import androidx.compose.runtime.Composable
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.ui.components.BankCardCard

@Composable
internal fun BankCardItemCard(
    item: SecureItem,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onToggleFavorite: ((Long, Boolean) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false
) {
    BankCardCard(
        item = item,
        onClick = onClick,
        onDelete = onDelete,
        onToggleFavorite = onToggleFavorite,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onLongClick = onLongClick,
        isSelectionMode = isSelectionMode,
        isSelected = isSelected
    )
}
