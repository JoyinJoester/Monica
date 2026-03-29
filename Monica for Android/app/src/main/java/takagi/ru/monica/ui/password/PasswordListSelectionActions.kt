package takagi.ru.monica.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordPageContentType
import takagi.ru.monica.ui.password.PasswordAggregateListItemUi
import takagi.ru.monica.ui.password.passwordIdFromSelectionKey
import takagi.ru.monica.viewmodel.PasswordViewModel

internal data class PasswordListSelectionHandlers(
    val onExitSelection: () -> Unit,
    val onSelectAll: () -> Unit,
    val onFavoriteSelected: () -> Unit,
    val onMoveToCategory: () -> Unit,
    val onStackSelected: () -> Unit,
    val onDeleteSelected: () -> Unit
)

@Composable
internal fun rememberPasswordListSelectionHandlers(
    context: Context,
    coroutineScope: CoroutineScope,
    viewModel: PasswordViewModel,
    selectedItemKeys: Set<String>,
    visibleSelectableKeys: Set<String>,
    selectedPasswords: Set<Long>,
    passwordEntries: List<PasswordEntry>,
    selectedSupplementaryItems: List<PasswordAggregateListItemUi>,
    aggregateUiState: PasswordListAggregateUiState,
    onClearSelection: () -> Unit,
    onSelectedItemKeysChange: (Set<String>) -> Unit,
    onShowMoveToCategoryDialog: () -> Unit,
    onShowManualStackConfirmDialog: () -> Unit,
    onShowBatchDeleteDialog: () -> Unit
): PasswordListSelectionHandlers {
    return PasswordListSelectionHandlers(
        onExitSelection = onClearSelection,
        onSelectAll = {
            onSelectedItemKeysChange(
                if (selectedItemKeys.size == visibleSelectableKeys.size) {
                    emptySet()
                } else {
                    visibleSelectableKeys
                }
            )
        },
        onFavoriteSelected = {
            coroutineScope.launch {
                val toggledCount = applyFavoriteSelectionToggle(
                    FavoriteSelectionToggleRequest(
                        context = context,
                        viewModel = viewModel,
                        selectedPasswords = selectedPasswords,
                        passwordEntries = passwordEntries,
                        selectedSupplementaryItems = selectedSupplementaryItems,
                        aggregateUiState = aggregateUiState
                    )
                )
                if (toggledCount <= 0) return@launch
                onClearSelection()
            }
            Unit
        },
        onMoveToCategory = onShowMoveToCategoryDialog,
        onStackSelected = {
            if (selectedPasswords.isEmpty()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.multi_del_select_items),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                onShowManualStackConfirmDialog()
            }
        },
        onDeleteSelected = onShowBatchDeleteDialog
    )
}

@Composable
internal fun BindPasswordListSelectionModeChange(
    isSelectionMode: Boolean,
    selectedItemKeys: Set<String>,
    selectedPasswords: Set<Long>,
    selectedSupplementaryItems: List<PasswordAggregateListItemUi>,
    handlers: PasswordListSelectionHandlers,
    onSelectionModeChange: (
        isSelectionMode: Boolean,
        selectedCount: Int,
        onExit: () -> Unit,
        onSelectAll: () -> Unit,
        onFavorite: (() -> Unit)?,
        onMoveToCategory: (() -> Unit)?,
        onStack: (() -> Unit)?,
        onDelete: () -> Unit
    ) -> Unit
) {
    LaunchedEffect(
        isSelectionMode,
        selectedItemKeys.size,
        selectedPasswords,
        selectedSupplementaryItems
    ) {
        val favoriteAction = handlers.onFavoriteSelected.takeIf {
            selectedItemKeys.any { passwordIdFromSelectionKey(it) != null } ||
                selectedSupplementaryItems.any { it.type != PasswordPageContentType.PASSKEY }
        }
        val hasSelectedSupplementaryItems = selectedSupplementaryItems.isNotEmpty()
        val passwordOnlySelection = !hasSelectedSupplementaryItems && selectedPasswords.isNotEmpty()
        onSelectionModeChange(
            isSelectionMode,
            selectedItemKeys.size,
            handlers.onExitSelection,
            handlers.onSelectAll,
            favoriteAction,
            handlers.onMoveToCategory.takeIf { passwordOnlySelection },
            handlers.onStackSelected.takeIf { passwordOnlySelection },
            handlers.onDeleteSelected
        )
    }
}
