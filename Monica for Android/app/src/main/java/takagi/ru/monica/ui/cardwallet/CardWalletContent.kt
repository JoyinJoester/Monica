package takagi.ru.monica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import takagi.ru.monica.ui.screens.CardWalletScreen
import takagi.ru.monica.ui.screens.CardWalletTab
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel

internal data class CardWalletContentState(
    val currentTab: CardWalletTab,
    val onTabSelected: (CardWalletTab) -> Unit,
    val onCardClick: (Long) -> Unit,
    val onDocumentClick: (Long) -> Unit,
    val onDocumentSelectionModeChange: (Boolean, Int, () -> Unit, () -> Unit, () -> Unit, () -> Unit) -> Unit,
    val onBankCardSelectionModeChange: (Boolean, Int, () -> Unit, () -> Unit, () -> Unit, () -> Unit, () -> Unit) -> Unit
)

@Composable
internal fun CardWalletContent(
    saveableStateHolder: SaveableStateHolder,
    bankCardViewModel: BankCardViewModel,
    documentViewModel: DocumentViewModel,
    state: CardWalletContentState
) {
    saveableStateHolder.SaveableStateProvider("card_wallet") {
        CardWalletScreen(
            bankCardViewModel = bankCardViewModel,
            documentViewModel = documentViewModel,
            currentTab = state.currentTab,
            onTabSelected = state.onTabSelected,
            onCardClick = state.onCardClick,
            onDocumentClick = state.onDocumentClick,
            onSelectionModeChange = state.onDocumentSelectionModeChange,
            onBankCardSelectionModeChange = state.onBankCardSelectionModeChange
        )
    }
}
