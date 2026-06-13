package takagi.ru.monica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import takagi.ru.monica.ui.screens.CardWalletScreen
import takagi.ru.monica.ui.screens.CardWalletTab
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.BillingAddressViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel

internal data class CardWalletContentState(
    val currentTab: CardWalletTab,
    val onTabSelected: (CardWalletTab) -> Unit,
    val onCardClick: (Long) -> Unit,
    val onDocumentClick: (Long) -> Unit,
    val onBillingAddressClick: (Long) -> Unit,
    val onDocumentSelectionModeChange: (Boolean, Int, () -> Unit, () -> Unit, () -> Unit, () -> Unit) -> Unit,
    val onBankCardSelectionModeChange: (Boolean, Int, () -> Unit, () -> Unit, () -> Unit, () -> Unit, () -> Unit) -> Unit,
    val onBitwardenScopeChanged: (Long?) -> Unit = {}
)

@Composable
internal fun CardWalletContent(
    saveableStateHolder: SaveableStateHolder,
    bankCardViewModel: BankCardViewModel,
    documentViewModel: DocumentViewModel,
    billingAddressViewModel: BillingAddressViewModel,
    passwordViewModel: takagi.ru.monica.viewmodel.PasswordViewModel,
    bitwardenViewModel: takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel? = null,
    state: CardWalletContentState,
    showStandaloneSettingsEntry: Boolean = false,
    onOpenStandaloneSettings: () -> Unit = {}
) {
    saveableStateHolder.SaveableStateProvider("card_wallet") {
        CardWalletScreen(
            bankCardViewModel = bankCardViewModel,
            documentViewModel = documentViewModel,
            billingAddressViewModel = billingAddressViewModel,
            passwordViewModel = passwordViewModel,
            bitwardenViewModel = bitwardenViewModel,
            currentTab = state.currentTab,
            onTabSelected = state.onTabSelected,
            onCardClick = state.onCardClick,
            onDocumentClick = state.onDocumentClick,
            onBillingAddressClick = state.onBillingAddressClick,
            onSelectionModeChange = state.onDocumentSelectionModeChange,
            onBankCardSelectionModeChange = state.onBankCardSelectionModeChange,
            showStandaloneSettingsEntry = showStandaloneSettingsEntry,
            onOpenStandaloneSettings = onOpenStandaloneSettings,
            onBitwardenScopeChanged = state.onBitwardenScopeChanged
        )
    }
}
