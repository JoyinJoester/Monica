package takagi.ru.monica.credentialexchange

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import takagi.ru.monica.R
import takagi.ru.monica.data.*
import takagi.ru.monica.data.model.*
import takagi.ru.monica.repository.Mdbx2NativeReadSessions
import takagi.ru.monica.ui.LocalUiSecurityManager
import takagi.ru.monica.ui.screens.*
import takagi.ru.monica.utils.AppLocaleStringResolver
import takagi.ru.monica.viewmodel.*
import java.io.File

/** Real editors and persistence, using only uniquely named disposable fixtures. */
class UnifiedWalletEditorsDeviceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var fixture: TransferFixture
    private lateinit var bank: BankCardViewModel
    private lateinit var document: DocumentViewModel
    private lateinit var passwords: PasswordViewModel
    private lateinit var billing: BillingAddressViewModel

    @Before fun setup() {
        fixture = TransferFixture()
        val strings = AppLocaleStringResolver(fixture.context)
        bank = BankCardViewModel(fixture.secureItems, fixture.context, fixture.db.localKeePassDatabaseDao(), fixture.security, strings)
        document = DocumentViewModel(fixture.secureItems, fixture.context, fixture.db.localKeePassDatabaseDao(), fixture.security, strings)
        passwords = PasswordViewModel(fixture.passwords, fixture.security, strings = strings)
        billing = BillingAddressViewModel(fixture.secureItems, fixture.security, fixture.context)
    }

    @After fun cleanup() = runBlocking {
        passwords.viewModelScope.cancel()
        bank.viewModelScope.cancel(); document.viewModelScope.cancel(); billing.viewModelScope.cancel()
        fixture.close()
    }

    @Composable private fun EditorTheme(dark: Boolean = false, content: @Composable () -> Unit) {
        val density = LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density, 1f)) {
            takagi.ru.monica.ui.theme.MonicaTheme(darkTheme = dark, content = content)
        }
    }

    private fun text(id: Int) = fixture.context.getString(id)
    private fun add(key: String, lazy: Boolean = false, wallet: String? = null) {
        closeWalletContent()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        if (wallet != null) compose.onNodeWithTag(wallet).performScrollToNode(hasTestTag("item_editor_add_content"))
        else if (lazy) compose.onNodeWithTag("totp_item_editor").performScrollToNode(hasTestTag("item_editor_add_content"))
        else compose.onNodeWithTag("item_editor_add_content").performScrollTo()
        compose.onNodeWithTag("item_editor_add_content").performClick()
        capture("add-content-$key")
        compose.onNodeWithTag("item_editor_choose_$key").performScrollTo().performClick()
        compose.waitForIdle()
    }
    private fun input(id: Int, value: String) {
        compose.onNode(hasText(text(id)) and hasSetTextAction()).performScrollTo().performTextReplacement(value)
    }
    private fun closeWalletContent() {
        if (compose.onAllNodesWithTag("wallet_detail_done").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("wallet_detail_done").performClick()
        }
    }
    private fun walletScroll(root: String, tag: String) {
        compose.onNodeWithTag(root).performScrollToNode(hasTestTag(tag))
    }
    private fun save() {
        closeWalletContent()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithContentDescription(text(R.string.save)).performClick()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val file = File(File(fixture.context.filesDir, "totp-compact-315").apply { mkdirs() }, "$name.png")
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun authenticatorAddsNotesAndReachesAdvancedSettingsFromSecret() {
        var saved: TotpData? = null
        var notes: String? = null
        var scanned = false
        compose.setContent { EditorTheme {
            AddEditTotpScreen(null, null, fixture.prefix, "", initialStorageExplicit = true, passwordViewModel = passwords,
                onSave = { _, n, data, _, _, done -> notes = n; saved = data; done(true) },
                onBatchImport = { _, _, _ -> }, onNavigateBack = {}, onScanQrCode = { scanned = true })
        } }
        compose.onNodeWithTag("item_editor_secret").performTextInput("JBSWY3DPEHPK3PXP")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("item_editor_otp_preview").fetchSemanticsNodes().isNotEmpty() }
        capture("authenticator-light")
        compose.onNodeWithTag("item_editor_otp_settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("item_editor_section_advanced").assertIsDisplayed()
        compose.onNodeWithTag("totp_item_editor").performScrollToNode(hasTestTag("otp_type_selector"))
        capture("authenticator-advanced-compact")
        add("association", lazy = true)
        compose.onNodeWithTag("totp_item_editor").performScrollToNode(hasTestTag("item_editor_section_advanced"))
        capture("authenticator-association-compact")
        compose.onNodeWithTag("totp_item_editor").performScrollToNode(hasText(text(R.string.linked_app)))
        compose.onNodeWithText(text(R.string.no_app_selected)).performClick()
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        add("notes", lazy = true)
        compose.onNode(hasText(text(R.string.notes)) and hasSetTextAction()).performTextInput("Added notes")
        save()
        compose.waitUntil(15000) { saved != null }
        assertEquals("Added notes", notes)
        assertEquals(OtpType.TOTP, saved!!.otpType)
        assertEquals("JBSWY3DPEHPK3PXP", saved!!.secret)
        // Existing scan/import entry points remain usable without changing the draft.
        compose.onNodeWithTag("totp_item_editor").performScrollToNode(hasText(text(R.string.scan_qr_code)))
        compose.onNodeWithText(text(R.string.scan_qr_code)).performClick()
        assertTrue(scanned)
        compose.onNodeWithText(text(R.string.totp_import_uri_action)).performClick()
        capture("authenticator-import-dialog")
    }

    @Test fun authenticatorEditKeepsNondefaultParametersAndHiddenSteamMetadata() {
        val original = TotpData(secret = "JBSWY3DPEHPK3PXP", issuer = "Issuer", accountName = "Synthetic account",
            otpType = OtpType.HOTP, counter = 17, digits = 8, algorithm = "SHA256",
            steamDeviceId = "synthetic-device", steamRevocationCode = "synthetic-revocation",
            link = "https://example.invalid", associatedApp = "example.synthetic")
        var saved: TotpData? = null
        compose.setContent { EditorTheme(dark = true) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.4f)) {
                AddEditTotpScreen(123, original, fixture.prefix, "Original notes", initialStorageExplicit = true,
                    onSave = { _, _, data, _, _, done -> saved = data; done(true) },
                    onBatchImport = { _, _, _ -> }, onNavigateBack = {}, onScanQrCode = {})
            }
        } }
        compose.onNodeWithTag("totp_item_editor").performScrollToNode(hasTestTag("item_editor_section_advanced"))
        capture("authenticator-dark-large-font")
        compose.onNodeWithTag("totp_item_editor").performScrollToNode(hasTestTag("item_editor_section_association"))
        capture("authenticator-association-dark-large-font")
        save()
        compose.waitUntil(15000) { saved != null }
        assertEquals(original, saved)
    }

    @Test fun bankNewHiddenFieldAndNotesSurviveMdbxFileAndEditorReopen() {
        val target = runBlocking { fixture.mdbx() }
        var closed by mutableStateOf(false)
        var reopenId by mutableStateOf<Long?>(null)
        compose.setContent { CompositionLocalProvider(LocalUiSecurityManager provides fixture.security) { EditorTheme {
            key(reopenId) { AddEditBankCardScreen(bank, cardId = reopenId, initialStorageExplicit = true,
                initialMdbxDatabaseId = target.databaseId, onNavigateBack = { closed = true }) }
        } } }
        compose.onNodeWithTag("item_editor_title").performTextInput(fixture.prefix)
        walletScroll("bank_item_editor", "entry_payment_number")
        compose.onNodeWithTag("entry_payment_number").performTextInput("4242424242424242")
        walletScroll("bank_item_editor", "entry_payment_holder")
        compose.onNodeWithTag("entry_payment_holder").performTextInput("SYNTHETIC HOLDER")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        walletScroll("bank_item_editor", "item_editor_title")
        walletScroll("bank_item_editor", "bank_branch")
        compose.onNodeWithTag("bank_branch").assertIsDisplayed().performTextInput("中国工商银行北京海淀支行")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        capture("bank-branch-320")
        add("custom_hidden", wallet = "bank_item_editor")
        compose.onNodeWithTag("wallet_custom_name_0").performTextInput("Recovery")
        input(R.string.custom_field_value, "Synthetic secret")
        add("notes", wallet = "bank_item_editor")
        input(R.string.notes, "Bank notes")
        closeWalletContent()
        compose.onNodeWithTag("bank_item_editor").performScrollToIndex(1)
        compose.onNodeWithTag("wallet_actions_notes").performClick()
        compose.onNodeWithText(text(R.string.move_up)).performClick()
        save()
        compose.waitUntil(30000) { closed }
        val item = runBlocking { fixture.db.secureItemDao().getAllItems().first().single { it.title == fixture.prefix } }
        assertEquals(target.databaseId, item.mdbxDatabaseId)
        val data = requireNotNull(CardWalletDataCodec.parseBankCardData(item.itemData, fixture.security::decryptDataIfMonicaCiphertext))
        assertEquals("中国工商银行北京海淀支行", data.bankBranch)
        assertEquals("Synthetic secret", data.customFields.single().value)
        assertTrue(data.customFields.single().isProtected())
        assertEquals(listOf("notes", "custom"), data.editorSectionOrder)
        assertEquals("Bank notes", item.notes)
        runBlocking {
            Mdbx2NativeReadSessions.clear()
            assertTrue(fixture.mdbx.readStoredEntries(target.databaseId).any {
                !it.deleted && it.payloadJson.contains("Synthetic secret") && it.payloadJson.contains("editorSectionOrder")
            })
        }
        compose.runOnIdle { reopenId = item.id }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("bank_item_editor").fetchSemanticsNodes().isNotEmpty() }
        walletScroll("bank_item_editor", "bank_branch")
        compose.onNodeWithTag("bank_branch").assertTextContains("中国工商银行北京海淀支行")
            .performTextReplacement("中国工商银行北京中关村支行")
        compose.runOnIdle { closed = false }
        save()
        compose.waitUntil(30000) { closed }
        val edited = runBlocking { requireNotNull(fixture.secureItems.getItemById(item.id)) }
        assertEquals("中国工商银行北京中关村支行", CardWalletDataCodec.parseBankCardData(
            edited.itemData, fixture.security::decryptDataIfMonicaCiphertext)!!.bankBranch)
        walletScroll("bank_item_editor", "wallet_content_custom")
        compose.onNodeWithTag("wallet_content_custom").performClick()
        compose.onNodeWithText("Recovery").assertIsDisplayed()
        closeWalletContent()
        walletScroll("bank_item_editor", "wallet_content_notes")
        compose.onNodeWithTag("wallet_content_notes").performClick()
        compose.onNodeWithText("Bank notes").assertIsDisplayed()
    }

    @Test fun bankBranchDetailCopiesFullValueAndEditorCanClearIt() {
        val branch = "中国工商银行北京海淀支行"
        val original = BankCardData("4242424242424242", "Synthetic Holder", "12", "2030",
            bankName = "Example Bank", bankBranch = branch, branchCode = "0102")
        val id = runBlocking { fixture.db.secureItemDao().insertItem(SecureItem(itemType = ItemType.BANK_CARD,
            title = fixture.prefix, itemData = CardWalletDataCodec.encodeBankCardData(original))) }
        var editing by mutableStateOf(false)
        var closed = false
        compose.setContent { CompositionLocalProvider(LocalUiSecurityManager provides fixture.security) { EditorTheme {
            if (editing) AddEditBankCardScreen(bank, cardId = id, onNavigateBack = { closed = true })
            else BankCardDetailScreen(bank, id, onNavigateBack = {}, onEditCard = { editing = true })
        } } }
        compose.waitUntil(15000) { compose.onAllNodesWithText(branch).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(branch).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.copy), useUnmergedTree = true).performClick()
        compose.runOnIdle {
            val clipboard = fixture.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            assertEquals(branch, clipboard.primaryClip!!.getItemAt(0).text.toString())
            editing = true
        }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("bank_branch").fetchSemanticsNodes().isNotEmpty() }
        walletScroll("bank_item_editor", "bank_branch")
        compose.onNodeWithTag("bank_branch").assertTextContains(branch).performTextClearance()
        save()
        compose.waitUntil(30000) { closed }
        val saved = runBlocking { fixture.secureItems.getItemById(id)!! }
        assertEquals(original.copy(bankBranch = ""), CardWalletDataCodec.parseBankCardData(saved.itemData,
            fixture.security::decryptDataIfMonicaCiphertext))
    }

    @Test fun documentEditKeepsRareFieldsAndAddsNotes() {
        val original = DocumentData(DocumentType.PASSPORT, "TEST-12345", "SYNTHETIC PERSON",
            address3 = "Third address line", passportNumber = "EXTRA-123", ssn = "SYNTHETIC-SSN",
            firstName = "Synthetic", middleName = "M", email = "synthetic@example.invalid",
            additionalInfo = "Existing information")
        val id = runBlocking { fixture.db.secureItemDao().insertItem(SecureItem(itemType = ItemType.DOCUMENT,
            title = fixture.prefix, itemData = CardWalletDataCodec.encodeDocumentData(original))) }
        var closed = false
        compose.setContent { CompositionLocalProvider(LocalUiSecurityManager provides fixture.security) { EditorTheme {
            AddEditDocumentScreen(document, documentId = id, onNavigateBack = { closed = true })
        } } }
        compose.waitUntil(15000) { compose.onAllNodesWithText("TEST-12345").fetchSemanticsNodes().isNotEmpty() }
        capture("document-light")
        walletScroll("document_item_editor", "wallet_content_address")
        compose.onNodeWithTag("wallet_content_address").performClick()
        compose.onNodeWithText("Third address line").performScrollTo().assertIsDisplayed()
        add("notes", wallet = "document_item_editor")
        input(R.string.notes, "Document notes")
        save()
        compose.waitUntil(30000) { closed }
        val item = runBlocking { requireNotNull(fixture.secureItems.getItemById(id)) }
        assertEquals(original, CardWalletDataCodec.parseDocumentData(item.itemData, fixture.security::decryptDataIfMonicaCiphertext))
        assertEquals("Document notes", item.notes)
    }

    @Test fun billingContactAndCustomFieldSurviveSaveAtLargeFont() {
        val original = BillingAddressData(fullName = "SYNTHETIC NAME", streetAddress = "Test street", apartment = "12B",
            city = "Test city", stateProvince = "Test region", postalCode = "100000", country = "Test country")
        val id = runBlocking { fixture.db.secureItemDao().insertItem(SecureItem(itemType = ItemType.BILLING_ADDRESS,
            title = fixture.prefix, itemData = CardWalletDataCodec.encodeBillingAddressData(original))) }
        var closed = false
        compose.setContent { EditorTheme(dark = true) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.4f)) {
                AddEditBillingAddressScreen(billing, addressId = id, onNavigateBack = { closed = true })
            }
        } }
        compose.waitUntil(15000) { compose.onAllNodesWithText("SYNTHETIC NAME").fetchSemanticsNodes().isNotEmpty() }
        capture("billing-dark-large-font")
        add("contact")
        compose.onNodeWithTag("entry_contact_email_0").performScrollTo().performTextInput("synthetic@example.invalid")
        add("custom_text")
        input(R.string.custom_field_name_placeholder, "Delivery")
        input(R.string.custom_field_value, "At reception")
        save()
        compose.waitUntil(30000) { closed }
        val item = runBlocking { requireNotNull(fixture.secureItems.getItemById(id)) }
        val data = requireNotNull(CardWalletDataCodec.parseBillingAddressData(item.itemData, fixture.security::decryptDataIfMonicaCiphertext))
        assertEquals(original.copy(email = "synthetic@example.invalid", customFields = data.customFields), data)
        assertEquals("At reception", data.customFields.single().value)
        assertFalse(data.customFields.single().isProtected())
    }
}
