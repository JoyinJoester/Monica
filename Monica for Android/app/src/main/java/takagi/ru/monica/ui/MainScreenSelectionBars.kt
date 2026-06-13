package takagi.ru.monica.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import takagi.ru.monica.ui.common.selection.SelectionActionBar
import takagi.ru.monica.ui.main.navigation.BottomNavItem
import takagi.ru.monica.ui.screens.CardWalletTab

@Composable
internal fun MainScreenSelectionBars(
    modifier: Modifier,
    currentTab: BottomNavItem,
    cardWalletSubTab: CardWalletTab,
    isPasswordSelectionMode: Boolean,
    selectedPasswordCount: Int,
    onExitPasswordSelection: () -> Unit,
    onSelectAllPasswords: () -> Unit,
    onFavoriteSelectedPasswords: (() -> Unit)?,
    onMoveToCategoryPasswords: (() -> Unit)?,
    onManualStackPasswords: (() -> Unit)?,
    onDeleteSelectedPasswords: () -> Unit,
    isTotpSelectionMode: Boolean,
    selectedTotpCount: Int,
    onExitTotpSelection: () -> Unit,
    onSelectAllTotp: () -> Unit,
    onMoveToCategoryTotp: () -> Unit,
    onDeleteSelectedTotp: () -> Unit,
    isBankCardSelectionMode: Boolean,
    selectedBankCardCount: Int,
    onExitBankCardSelection: () -> Unit,
    onSelectAllBankCards: () -> Unit,
    onFavoriteBankCards: () -> Unit,
    onMoveToCategoryBankCards: () -> Unit,
    onDeleteSelectedBankCards: () -> Unit,
    isDocumentSelectionMode: Boolean,
    selectedDocumentCount: Int,
    onExitDocumentSelection: () -> Unit,
    onSelectAllDocuments: () -> Unit,
    onMoveToCategoryDocuments: () -> Unit,
    onDeleteSelectedDocuments: () -> Unit
) {
    when {
        currentTab == BottomNavItem.Passwords && isPasswordSelectionMode -> {
            SelectionActionBar(
                modifier = modifier,
                selectedCount = selectedPasswordCount,
                onExit = onExitPasswordSelection,
                onSelectAll = onSelectAllPasswords,
                onFavorite = onFavoriteSelectedPasswords,
                onMoveToCategory = onMoveToCategoryPasswords,
                onStack = onManualStackPasswords,
                onDelete = onDeleteSelectedPasswords
            )
        }
        currentTab == BottomNavItem.Authenticator && isTotpSelectionMode -> {
            SelectionActionBar(
                modifier = modifier,
                selectedCount = selectedTotpCount,
                onExit = onExitTotpSelection,
                onSelectAll = onSelectAllTotp,
                onMoveToCategory = onMoveToCategoryTotp,
                onDelete = onDeleteSelectedTotp
            )
        }
        currentTab == BottomNavItem.CardWallet &&
            (cardWalletSubTab == CardWalletTab.BANK_CARDS ||
                cardWalletSubTab == CardWalletTab.ALL) &&
            isBankCardSelectionMode -> {
            SelectionActionBar(
                modifier = modifier,
                selectedCount = selectedBankCardCount,
                onExit = onExitBankCardSelection,
                onSelectAll = onSelectAllBankCards,
                onFavorite = onFavoriteBankCards,
                onMoveToCategory = onMoveToCategoryBankCards,
                onDelete = onDeleteSelectedBankCards
            )
        }
        currentTab == BottomNavItem.CardWallet &&
            cardWalletSubTab == CardWalletTab.DOCUMENTS &&
            isDocumentSelectionMode -> {
            SelectionActionBar(
                modifier = modifier,
                selectedCount = selectedDocumentCount,
                onExit = onExitDocumentSelection,
                onSelectAll = onSelectAllDocuments,
                onMoveToCategory = onMoveToCategoryDocuments,
                onDelete = onDeleteSelectedDocuments
            )
        }
    }
}
