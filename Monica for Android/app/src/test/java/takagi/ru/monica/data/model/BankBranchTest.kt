package takagi.ru.monica.data.model

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.data.*
import takagi.ru.monica.ui.cardwallet.toBankCardWalletListItem

class BankBranchTest {
    private val branch = "中国工商银行北京海淀支行"
    private fun card() = BankCardData("4242424242424242", "Synthetic Holder", "12", "2030",
        bankName = "中国工商银行", bankBranch = branch, branchCode = "0102",
        customFields = listOf(SecureCustomField("开户行", "Existing custom value")))

    @Test fun fieldRoundTripPreservesBankNameCodeAndExistingCustomField() {
        val original = card()
        assertEquals(original, CardWalletDataCodec.parseBankCardData(CardWalletDataCodec.encodeBankCardData(original)))
        val cleared = original.copy(bankBranch = "")
        assertEquals(cleared, CardWalletDataCodec.parseBankCardData(CardWalletDataCodec.encodeBankCardData(cleared)))
    }

    @Test fun oldCardsDefaultToEmptyAndLegacyFallbackRetainsBranch() {
        val old = """{"cardNumber":"4242","cardholderName":"Holder","expiryMonth":"12","expiryYear":"30","bankName":"Bank","branchCode":"123"}"""
        assertEquals("", CardWalletDataCodec.parseBankCardData(old)!!.bankBranch)
        assertEquals("123", CardWalletDataCodec.parseBankCardData(old)!!.branchCode)
        val legacy = """{"number":"4242","bankBranch":"海淀支行"}"""
        assertEquals("海淀支行", CardWalletDataCodec.parseBankCardData(legacy)!!.bankBranch)
    }

    @Test fun embeddedCardAndLegacyPaymentProjectionRetainBranch() {
        val snapshot = EmbeddedWalletContent.create(SecureItem(itemType = ItemType.BANK_CARD,
            title = "Card", itemData = CardWalletDataCodec.encodeBankCardData(card())))
        val restored = (EmbeddedWalletContent.read(snapshot.encode()) as EmbeddedWalletContent.ReadResult.Available).snapshot
        assertEquals(card(), CardWalletDataCodec.parseBankCardData(restored.displayItem().itemData))
        val payment = PasswordWalletProjection.payment(PasswordEntry(title = "Payment", username = "", password = "", website = ""),
            listOf(CustomFieldDraft(title = EntryContentFields.key("PAYMENT", "bankBranch"), value = branch)), "Payment")
        assertEquals(branch, CardWalletDataCodec.parseBankCardData(payment.displayItem().itemData)!!.bankBranch)
    }

    @Test fun branchIsSearchableAndSuggestionsRemainFieldSpecific() {
        val item = SecureItem(itemType = ItemType.BANK_CARD, title = "Card", itemData = "{}").toBankCardWalletListItem(card())
        assertTrue(item.matchesSearchQuery("海淀"))
        assertFalse(item.matchesSearchQuery("不存在的支行"))
        val raw = CardWalletDataCodec.encodeBankCardData(card())
        assertEquals(branch, CommonSuggestionField.BANK_BRANCH.walletValue(raw, ItemType.BANK_CARD))
        assertEquals("中国工商银行", CommonSuggestionField.BANK_NAME.walletValue(raw, ItemType.BANK_CARD))
        assertEquals("0102", CommonSuggestionField.BRANCH_CODE.walletValue(raw, ItemType.BANK_CARD))
    }
}
