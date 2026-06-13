package takagi.ru.monica.ui.cardwallet

import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.BillingAddressData
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.ui.components.UnifiedCategoryFilterSelection

enum class WalletListItemType {
    BANK_CARD,
    DOCUMENT,
    BILLING_ADDRESS
}

data class WalletListItem(
    val id: Long,
    val type: WalletListItemType,
    val item: SecureItem,
    val bankCardData: BankCardData? = null,
    val documentData: DocumentData? = null,
    val billingAddressData: BillingAddressData? = null
) {
    val itemType: ItemType
        get() = item.itemType

    fun matchesSearchQuery(query: String): Boolean {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return true
        }

        if (item.title.contains(normalizedQuery, ignoreCase = true) ||
            item.notes.contains(normalizedQuery, ignoreCase = true)
        ) {
            return true
        }

        return when (type) {
            WalletListItemType.BANK_CARD -> bankCardData?.matchesBankCardSearch(normalizedQuery) == true
            WalletListItemType.DOCUMENT -> documentData?.matchesDocumentSearch(normalizedQuery) == true
            WalletListItemType.BILLING_ADDRESS -> billingAddressData?.matchesBillingAddressSearch(normalizedQuery) == true
        }
    }

    fun matchesCategoryFilter(filter: UnifiedCategoryFilterSelection): Boolean {
        val vaultId = item.bitwardenVaultId
        val folderId = item.bitwardenFolderId
        val keePassId = item.keepassDatabaseId
        val groupPath = item.keepassGroupPath
        val mdbxId = item.mdbxDatabaseId
        val isLocal = vaultId == null && keePassId == null && mdbxId == null
        return when (filter) {
            UnifiedCategoryFilterSelection.All -> true
            UnifiedCategoryFilterSelection.Local -> isLocal
            UnifiedCategoryFilterSelection.Starred -> item.isFavorite
            UnifiedCategoryFilterSelection.Uncategorized -> item.categoryId == null
            UnifiedCategoryFilterSelection.LocalStarred -> isLocal && item.isFavorite
            UnifiedCategoryFilterSelection.LocalUncategorized -> isLocal && item.categoryId == null
            is UnifiedCategoryFilterSelection.Custom -> item.categoryId == filter.categoryId
            is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> vaultId == filter.vaultId
            is UnifiedCategoryFilterSelection.BitwardenFolderFilter ->
                vaultId == filter.vaultId && folderId == filter.folderId
            is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter ->
                vaultId == filter.vaultId && item.isFavorite
            is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter ->
                vaultId == filter.vaultId && item.categoryId == null
            is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> keePassId == filter.databaseId
            is UnifiedCategoryFilterSelection.KeePassGroupFilter ->
                keePassId == filter.databaseId && groupPath == filter.groupPath
            is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter ->
                keePassId == filter.databaseId && item.isFavorite
            is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter ->
                keePassId == filter.databaseId && item.categoryId == null
            is UnifiedCategoryFilterSelection.MdbxDatabaseFilter -> mdbxId == filter.databaseId
            is UnifiedCategoryFilterSelection.MdbxFolderFilter -> mdbxId == filter.databaseId
        }
    }
}

fun SecureItem.toBankCardWalletListItem(cardData: BankCardData): WalletListItem =
    WalletListItem(
        id = id,
        type = WalletListItemType.BANK_CARD,
        item = this,
        bankCardData = cardData
    )

fun SecureItem.toDocumentWalletListItem(documentData: DocumentData): WalletListItem =
    WalletListItem(
        id = id,
        type = WalletListItemType.DOCUMENT,
        item = this,
        documentData = documentData
    )

fun SecureItem.toBillingAddressWalletListItem(addressData: BillingAddressData): WalletListItem =
    WalletListItem(
        id = id,
        type = WalletListItemType.BILLING_ADDRESS,
        item = this,
        billingAddressData = addressData
    )

private fun BankCardData.matchesBankCardSearch(query: String): Boolean =
    cardNumber.contains(query, ignoreCase = true) ||
        bankName.contains(query, ignoreCase = true) ||
        cardholderName.contains(query, ignoreCase = true) ||
        nickname.contains(query, ignoreCase = true) ||
        brand.contains(query, ignoreCase = true)

private fun DocumentData.matchesDocumentSearch(query: String): Boolean =
    documentNumber.contains(query, ignoreCase = true) ||
        fullName.contains(query, ignoreCase = true) ||
        firstName.contains(query, ignoreCase = true) ||
        middleName.contains(query, ignoreCase = true) ||
        lastName.contains(query, ignoreCase = true) ||
        issuedBy.contains(query, ignoreCase = true) ||
        nationality.contains(query, ignoreCase = true) ||
        country.contains(query, ignoreCase = true)

private fun BillingAddressData.matchesBillingAddressSearch(query: String): Boolean =
    fullName.contains(query, ignoreCase = true) ||
        company.contains(query, ignoreCase = true) ||
        streetAddress.contains(query, ignoreCase = true) ||
        apartment.contains(query, ignoreCase = true) ||
        city.contains(query, ignoreCase = true) ||
        stateProvince.contains(query, ignoreCase = true) ||
        postalCode.contains(query, ignoreCase = true) ||
        country.contains(query, ignoreCase = true) ||
        phone.contains(query, ignoreCase = true) ||
        email.contains(query, ignoreCase = true)
