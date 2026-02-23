package takagi.ru.monica.ui

import androidx.compose.runtime.Composable
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.ui.components.DocumentCard

@Composable
internal fun DocumentItemCard(
    item: SecureItem,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onToggleFavorite: ((Long, Boolean) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false
) {
    DocumentCard(
        item = item,
        onClick = onClick,
        onDelete = onDelete,
        onToggleFavorite = onToggleFavorite,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        isSelectionMode = isSelectionMode,
        isSelected = isSelected
    )
}
