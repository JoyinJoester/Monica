package takagi.ru.monica.data.model

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.ui.cardwallet.toDocumentWalletListItem

class WalletPassTemplateTest {
    @Test fun oldDocumentsRemainIdentityDocuments() {
        val data = CardWalletDataCodec.parseDocumentData("""{"documentType":"PASSPORT","documentNumber":"P1","fullName":"Test"}""")!!
        assertEquals("", data.walletTemplate)
        assertTrue(data.walletFields.isEmpty())
    }

    @Test fun allTemplatesRetainUnicodeLeadingZeroesAndFutureFields() {
        WalletPassTemplate.entries.forEach { template ->
            val data = DocumentData(DocumentType.OTHER, "00001234", "测试旅客", walletTemplate = template.name,
                walletFields = mapOf("points" to "0001.50", "departure" to "2026-11-01 09:30 +08:00", "future" to "\n备注\n"))
            assertEquals(data, CardWalletDataCodec.parseDocumentData(CardWalletDataCodec.encodeDocumentData(data)))
        }
    }

    @Test fun embeddedEditsRetainPassMetadataAndCanClearAField() {
        val data = DocumentData(DocumentType.OTHER, "0001", "Test", walletTemplate = "TRAVEL",
            walletFields = mapOf("seat" to "12A", "future" to "keep"))
        val snapshot = EmbeddedWalletContent.create(SecureItem(itemType = ItemType.DOCUMENT, title = "Trip",
            itemData = CardWalletDataCodec.encodeDocumentData(data)))
        val editor = EmbeddedDocumentEditorData(snapshot)
        val result = editor.edited("Trip", "Notes", false,
            editor.data.copy(walletFields = editor.data.walletFields + ("seat" to "")), editor.customFields)
        val restored = EmbeddedDocumentEditorData(result).data
        assertEquals("TRAVEL", restored.walletTemplate)
        assertEquals(mapOf("seat" to "", "future" to "keep"), restored.walletFields)
    }

    @Test fun searchFindsFlightOriginAndRewardMetadata() {
        val data = DocumentData(DocumentType.OTHER, "0001", "Test", walletTemplate = "TRAVEL",
            walletFields = mapOf("serviceNumber" to "MU537", "origin" to "上海浦东"))
        val row = SecureItem(itemType = ItemType.DOCUMENT, title = "Trip", itemData = "").toDocumentWalletListItem(data)
        assertTrue(row.matchesSearchQuery("mu537"))
        assertTrue(row.matchesSearchQuery("浦东"))
        assertFalse(row.matchesSearchQuery("missing"))
    }

    @Test fun encryptedExtensionDecoderPreservesTextAndIgnoresNonTextContainers() {
        assertEquals(mapOf("seat" to "12A", "points" to "0010"),
            WalletPassFieldsCodec.decode("""{"seat":"12A","points":"0010","future":{"x":1}}"""))
        assertTrue(WalletPassFieldsCodec.decode("").isEmpty())
        assertNull(WalletPassTemplate.fromStored("FUTURE"))
    }

    @Test fun walletPassesAreNeverOfferedAsIdentityDocumentsByAutofill() {
        WalletPassTemplate.entries.forEach { template ->
            val data = DocumentData(DocumentType.OTHER, "0001", "Synthetic", walletTemplate = template.name)
            val item = SecureItem(itemType = ItemType.DOCUMENT, title = "Pass", itemData = CardWalletDataCodec.encodeDocumentData(data))
            assertNull(takagi.ru.monica.autofill_ng.parseDocumentCandidate(item))
            assertNotNull(takagi.ru.monica.autofill_ng.parseDocumentCandidate(item.copy(
                itemData = CardWalletDataCodec.encodeDocumentData(data.copy(walletTemplate = "")))))
        }
    }
}
