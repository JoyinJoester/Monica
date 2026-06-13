package takagi.ru.monica.ui.cardwallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.BillingAddressData
import takagi.ru.monica.data.model.CardType
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.DocumentType
import takagi.ru.monica.ui.components.UnifiedCategoryFilterSelection

class WalletListItemTest {

    @Test
    fun bankCardItemsKeepOriginalSecureItemAndParsedData() {
        val item = secureItem(
            id = 10L,
            itemType = ItemType.BANK_CARD,
            title = "Travel card"
        )
        val cardData = BankCardData(
            cardNumber = "4111111111111111",
            cardholderName = "Monica User",
            expiryMonth = "08",
            expiryYear = "2030",
            bankName = "Example Bank",
            cardType = CardType.CREDIT
        )

        val walletItem = item.toBankCardWalletListItem(cardData)

        assertEquals(10L, walletItem.id)
        assertEquals(WalletListItemType.BANK_CARD, walletItem.type)
        assertSame(item, walletItem.item)
        assertSame(cardData, walletItem.bankCardData)
        assertNull(walletItem.documentData)
        assertNull(walletItem.billingAddressData)
    }

    @Test
    fun documentItemsKeepOriginalSecureItemAndParsedData() {
        val item = secureItem(
            id = 20L,
            itemType = ItemType.DOCUMENT,
            title = "Passport"
        )
        val documentData = DocumentData(
            documentType = DocumentType.PASSPORT,
            documentNumber = "P1234567",
            fullName = "Monica User",
            nationality = "JP"
        )

        val walletItem = item.toDocumentWalletListItem(documentData)

        assertEquals(20L, walletItem.id)
        assertEquals(WalletListItemType.DOCUMENT, walletItem.type)
        assertSame(item, walletItem.item)
        assertSame(documentData, walletItem.documentData)
        assertNull(walletItem.bankCardData)
        assertNull(walletItem.billingAddressData)
    }

    @Test
    fun billingAddressItemsKeepOriginalSecureItemAndParsedData() {
        val item = secureItem(
            id = 30L,
            itemType = ItemType.BILLING_ADDRESS,
            title = "Home address"
        )
        val addressData = BillingAddressData(
            fullName = "Monica User",
            company = "Monica Pass",
            streetAddress = "1 Sakura Street",
            city = "Tokyo",
            postalCode = "100-0001",
            country = "JP",
            phone = "+81 00 0000 0000",
            email = "monica@example.com"
        )

        val walletItem = item.toBillingAddressWalletListItem(addressData)

        assertEquals(30L, walletItem.id)
        assertEquals(WalletListItemType.BILLING_ADDRESS, walletItem.type)
        assertSame(item, walletItem.item)
        assertSame(addressData, walletItem.billingAddressData)
        assertNull(walletItem.bankCardData)
        assertNull(walletItem.documentData)
    }

    @Test
    fun searchMatchesCommonAndTypeSpecificFields() {
        val card = secureItem(
            id = 1L,
            itemType = ItemType.BANK_CARD,
            title = "Daily card",
            notes = "shared expenses"
        ).toBankCardWalletListItem(
            BankCardData(
                cardNumber = "5555444433331111",
                cardholderName = "Monica User",
                expiryMonth = "10",
                expiryYear = "2031",
                bankName = "Quiet Bank",
                nickname = "Groceries"
            )
        )
        val document = secureItem(
            id = 2L,
            itemType = ItemType.DOCUMENT,
            title = "Identity",
            notes = ""
        ).toDocumentWalletListItem(
            DocumentData(
                documentType = DocumentType.ID_CARD,
                documentNumber = "ID-9988",
                fullName = "",
                firstName = "Mona",
                lastName = "Tester",
                issuedBy = "City Hall"
            )
        )
        val address = secureItem(
            id = 3L,
            itemType = ItemType.BILLING_ADDRESS,
            title = "Billing profile"
        ).toBillingAddressWalletListItem(
            BillingAddressData(
                fullName = "Monica User",
                company = "Monica Pass",
                streetAddress = "1 Sakura Street",
                city = "Tokyo",
                postalCode = "100-0001",
                country = "JP",
                email = "monica@example.com"
            )
        )
        assertTrue(card.matchesSearchQuery("shared"))
        assertTrue(card.matchesSearchQuery("Quiet"))
        assertTrue(card.matchesSearchQuery("groceries"))
        assertTrue(document.matchesSearchQuery("ID-9988"))
        assertTrue(document.matchesSearchQuery("tester"))
        assertTrue(document.matchesSearchQuery("city hall"))
        assertTrue(address.matchesSearchQuery("sakura"))
        assertTrue(address.matchesSearchQuery("100-0001"))
        assertTrue(address.matchesSearchQuery("monica@example"))
        assertFalse(card.matchesSearchQuery("passport"))
    }

    @Test
    fun categoryFilterKeepsSourceScopesSeparate() {
        val local = secureItem(id = 1L, itemType = ItemType.BANK_CARD)
            .toBankCardWalletListItem(sampleCardData())
        val bitwarden = secureItem(id = 2L, itemType = ItemType.BANK_CARD, bitwardenVaultId = 7L, bitwardenFolderId = "cards")
            .toBankCardWalletListItem(sampleCardData())
        val keepass = secureItem(id = 3L, itemType = ItemType.DOCUMENT, keepassDatabaseId = 8L, keepassGroupPath = "Wallet")
            .toDocumentWalletListItem(sampleDocumentData())
        val mdbx = secureItem(id = 4L, itemType = ItemType.DOCUMENT, mdbxDatabaseId = 9L, mdbxFolderId = "folder")
            .toDocumentWalletListItem(sampleDocumentData())
        val address = secureItem(id = 5L, itemType = ItemType.BILLING_ADDRESS)
            .toBillingAddressWalletListItem(sampleBillingAddressData())

        assertTrue(local.matchesCategoryFilter(UnifiedCategoryFilterSelection.Local))
        assertFalse(bitwarden.matchesCategoryFilter(UnifiedCategoryFilterSelection.Local))
        assertTrue(bitwarden.matchesCategoryFilter(UnifiedCategoryFilterSelection.BitwardenFolderFilter(7L, "cards")))
        assertFalse(bitwarden.matchesCategoryFilter(UnifiedCategoryFilterSelection.BitwardenFolderFilter(7L, "other")))
        assertTrue(keepass.matchesCategoryFilter(UnifiedCategoryFilterSelection.KeePassGroupFilter(8L, "Wallet")))
        assertFalse(keepass.matchesCategoryFilter(UnifiedCategoryFilterSelection.KeePassDatabaseFilter(9L)))
        assertTrue(mdbx.matchesCategoryFilter(UnifiedCategoryFilterSelection.MdbxDatabaseFilter(9L)))
        assertTrue(address.matchesCategoryFilter(UnifiedCategoryFilterSelection.Local))
    }

    private fun secureItem(
        id: Long,
        itemType: ItemType,
        title: String = "Item $id",
        notes: String = "",
        bitwardenVaultId: Long? = null,
        bitwardenFolderId: String? = null,
        keepassDatabaseId: Long? = null,
        keepassGroupPath: String? = null,
        mdbxDatabaseId: Long? = null,
        mdbxFolderId: String? = null
    ): SecureItem =
        SecureItem(
            id = id,
            itemType = itemType,
            title = title,
            notes = notes,
            itemData = "{}",
            bitwardenVaultId = bitwardenVaultId,
            bitwardenFolderId = bitwardenFolderId,
            keepassDatabaseId = keepassDatabaseId,
            keepassGroupPath = keepassGroupPath,
            mdbxDatabaseId = mdbxDatabaseId,
            mdbxFolderId = mdbxFolderId
        )

    private fun sampleCardData(): BankCardData =
        BankCardData(
            cardNumber = "4111111111111111",
            cardholderName = "Monica User",
            expiryMonth = "08",
            expiryYear = "2030"
        )

    private fun sampleDocumentData(): DocumentData =
        DocumentData(
            documentType = DocumentType.OTHER,
            documentNumber = "DOC",
            fullName = "Monica User"
        )

    private fun sampleBillingAddressData(): BillingAddressData =
        BillingAddressData(
            fullName = "Monica User",
            streetAddress = "1 Sakura Street",
            city = "Tokyo"
        )

}
