package takagi.ru.monica.ui.cardwallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.rustcore.RustListSortCore
import takagi.ru.monica.ui.components.UnifiedCategoryFilterSelection
import takagi.ru.monica.ui.screens.CardWalletTab
import takagi.ru.monica.viewmodel.LoadedListState
import takagi.ru.monica.viewmodel.ParsedBankCardItem
import takagi.ru.monica.viewmodel.ParsedBillingAddressItem
import takagi.ru.monica.viewmodel.ParsedDocumentItem

@Composable
internal fun rememberPreparedWallet(
    cards: LoadedListState<ParsedBankCardItem>,
    documents: LoadedListState<ParsedDocumentItem>,
    addresses: LoadedListState<ParsedBillingAddressItem>
): State<LoadedListState<WalletListItem>> = produceState(LoadedListState(), cards, documents, addresses) {
    value = withContext(Dispatchers.Default) {
        val items = cards.items.map { it.item.toBankCardWalletListItem(it.cardData) } +
            documents.items.map { it.item.toDocumentWalletListItem(it.documentData) } +
            addresses.items.map { it.item.toBillingAddressWalletListItem(it.addressData) }
        LoadedListState(
            RustListSortCore.sort(items, tieById = true) { it.item },
            cards.isReady && documents.isReady && addresses.isReady
        )
    }
}

@Composable
internal fun rememberWalletActionItems(
    cards: List<SecureItem>, documents: List<SecureItem>, addresses: List<SecureItem>
): State<List<SecureItem>> = produceState(emptyList(), cards, documents, addresses) {
    value = withContext(Dispatchers.Default) {
        RustListSortCore.sort(cards + documents + addresses, tieById = false) { it }
    }
}

@Composable
internal fun rememberFilteredWallet(
    prepared: LoadedListState<WalletListItem>, tab: CardWalletTab,
    query: String, category: UnifiedCategoryFilterSelection
): State<LoadedListState<WalletListItem>> = key(tab, query, category) {
    produceState(LoadedListState(), prepared) {
        value = withContext(Dispatchers.Default) {
            val trimmedQuery = query.trim()
            val matches = prepared.items.filter { item ->
                val matchesTab = when (tab) {
                    CardWalletTab.ALL -> true
                    CardWalletTab.BANK_CARDS -> item.type == WalletListItemType.BANK_CARD
                    CardWalletTab.MEMBERSHIP_CARDS -> item.documentData?.walletTemplate == "MEMBERSHIP"
                    CardWalletTab.REWARDS_CARDS -> item.documentData?.walletTemplate == "REWARDS"
                    CardWalletTab.TRAVEL_PASSES -> item.documentData?.walletTemplate == "TRAVEL"
                    CardWalletTab.DOCUMENTS -> item.type == WalletListItemType.DOCUMENT && item.documentData?.walletTemplate.isNullOrBlank()
                    CardWalletTab.BILLING_ADDRESSES -> item.type == WalletListItemType.BILLING_ADDRESS
                }
                matchesTab && item.matchesCategoryFilter(category) && item.matchesSearchQuery(trimmedQuery)
            }
            LoadedListState(matches, prepared.isReady)
        }
    }
}
