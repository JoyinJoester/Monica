package takagi.ru.monica.ui.password

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import takagi.ru.monica.data.PasswordCardDisplayField
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordPageContentType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.viewmodel.CategoryFilter
import takagi.ru.monica.security.maskCardNumbersInPasswordPreview
import takagi.ru.monica.security.maskPasswordNotePreview

class PasswordListPreviewPrivacyTest {
    @Test fun masksLegacyCardNumbersInUsernameAndCardholderSubtitles() {
        for (number in listOf("6222021234565678", "6222 0212 3456 5678", "6222-0212-3456-5678", "6222\u00a00212\u00a03456\u00a05678")) {
            val entry = entry(username = "示例银行 · $number")
            assertEquals("示例银行 · •••• 5678", lines(entry).first().text)
            assertEquals("示例银行 · $number", entry.username)
        }
    }

    @Test fun masksEachNumberAndDoesNotExposeLeadingDigitsOfLongRuns() {
        assertEquals("•••• 1111 / •••• 2222", maskCardNumbersInPasswordPreview("4111111111111111 / 5555555555552222"))
        assertEquals("•••• 3456", maskCardNumbersInPasswordPreview("12345678901234567890123456"))
    }

    @Test fun preservesOrdinaryUsernamesEmailsAndPhoneNumbers() {
        for (username in listOf("alice", "alice@example.invalid", "13800138000", "user-1234")) {
            assertEquals(username, lines(entry(username = username)).first().text)
        }
    }

    @Test fun notesOnlyKeepTwoCharactersAtEachEndOfFirstNonblankLine() {
        val source = "\n  恢复密钥需要离线备份  \n第二行秘密"
        val entry = entry(notes = source)
        assertEquals("恢复••••••备份", lines(entry).last().text)
        assertEquals(source, entry.notes)
        assertFalse(lines(entry).any { it.text.contains("第二行") })
    }

    @Test fun shortNotesAreFullyMaskedAndEmptyNotesAreOmitted() {
        for (note in listOf("a", "12", "密码", "1234")) {
            assertEquals("••••••", maskPasswordNotePreview(note))
        }
        assertEquals("", maskPasswordNotePreview(" \n\t"))
        assertEquals(1, lines(entry()).size)
    }

    @Test fun noteHintsDoNotSplitSupplementaryUnicodeCharacters() {
        assertEquals("😀恢••••••份🔑", maskPasswordNotePreview("😀恢复密钥离线备份🔑"))
    }

    @Test fun noteBasedStackTitlesAreMaskedWithoutChangingGroupingIdentity() {
        val entry = entry(notes = "恢复密钥需要离线备份")
        for (mode in listOf("note", "smart")) {
            val key = getGroupKeyForMode(entry, mode)
            val group = buildPasswordPageListItems(
                selectedContentTypes = setOf(PasswordPageContentType.PASSWORD),
                groupedPasswords = mapOf(key to listOf(entry, entry.copy(id = 2))),
                supplementaryItems = emptyList(), groupMode = mode
            ).single() as PasswordGroupListItemUi
            assertEquals("恢复••••••备份", group.displayTitle)
            assertEquals(key, group.groupKey)
            assertEquals(entry.notes, group.passwords.first().notes)
        }
    }

    @Test fun displayFieldOrderAndWebsiteStayUnchanged() {
        val entry = entry(notes = "恢复密钥离线备份")
        val fields = listOf(PasswordCardDisplayField.NOTE_PREVIEW, PasswordCardDisplayField.WEBSITE)
        val result = resolvePasswordCardDisplayLines(entry, fields)
        assertEquals(fields, result.map { it.field })
        assertEquals(entry.website, result.last().text)
    }

    @Test fun aggregateBankCardSearchStillFindsTheOriginalItemAndMasksLegacySubtitle() {
        val card = SecureItem(id = 42, title = "示例银行", itemType = ItemType.BANK_CARD, itemData = "{}", notes = "恢复密钥需要离线备份")
        val data = BankCardData(cardNumber = "6222021234565678", cardholderName = "6222021234565678", bankName = "示例银行", expiryMonth = "12", expiryYear = "2030")
        val result = buildPasswordAggregateItems(
            selectedContentTypes = setOf(PasswordPageContentType.CARD_WALLET),
            bankCards = listOf(card), documents = emptyList(), billingAddresses = emptyList(),
            notes = emptyList(), totpItems = emptyList(), passkeys = emptyList(),
            searchQuery = "5678", categoryFilter = CategoryFilter.All, parseBankCardData = { data }
        ).single()
        assertEquals(42L, result.secureItemId)
        assertEquals(listOf("示例银行 · •••• 5678", "恢复••••••备份"), lines(result.entry).map { it.text })
        assertEquals("6222021234565678", data.cardNumber)
    }

    private fun entry(username: String = "alice", notes: String = "") = PasswordEntry(
        title = "Fixture", website = "https://example.invalid/1234567890123456", username = username,
        password = "secret", notes = notes
    )

    private fun lines(entry: PasswordEntry) = resolvePasswordCardDisplayLines(
        entry, listOf(PasswordCardDisplayField.USERNAME, PasswordCardDisplayField.NOTE_PREVIEW)
    )
}
