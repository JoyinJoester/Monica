package takagi.ru.monica.credentialexchange

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import takagi.ru.monica.R
import takagi.ru.monica.bitwarden.api.CipherApiResponse
import takagi.ru.monica.bitwarden.service.*
import takagi.ru.monica.data.*
import takagi.ru.monica.data.model.*
import takagi.ru.monica.ui.LocalUiSecurityManager
import takagi.ru.monica.ui.UnifiedWalletAddScreen
import takagi.ru.monica.ui.screens.*
import takagi.ru.monica.utils.AppLocaleStringResolver
import takagi.ru.monica.viewmodel.*
import java.io.File

class WalletPassTemplatesDeviceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var f: TransferFixture
    private lateinit var bank: BankCardViewModel
    private lateinit var document: DocumentViewModel
    private lateinit var billing: BillingAddressViewModel
    @Before fun setup() {
        f = TransferFixture()
        val strings = AppLocaleStringResolver(f.context)
        bank = BankCardViewModel(f.secureItems, f.context, f.db.localKeePassDatabaseDao(), f.security, strings)
        document = DocumentViewModel(f.secureItems, f.context, f.db.localKeePassDatabaseDao(), f.security, strings)
        billing = BillingAddressViewModel(f.secureItems, f.security, f.context)
    }
    @After fun cleanup() = runBlocking {
        bank.viewModelScope.cancel(); document.viewModelScope.cancel(); billing.viewModelScope.cancel()
        f.close()
    }
    private fun text(id: Int) = f.context.getString(id)
    private fun input(label: Int, value: String) {
        compose.onNode(hasText(text(label)) and hasSetTextAction()).performScrollTo().performTextReplacement(value)
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(400) // Let the Android window draw after Compose semantics settle.
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(f.context.filesDir, "wallet-pass-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun create(template: CardWalletTab, type: String, extraLabel: Int, extraKey: String, extraValue: String, large: Boolean = false) {
        var selected by mutableStateOf(CardWalletTab.BANK_CARDS)
        var phase by mutableIntStateOf(0)
        var savedId by mutableStateOf<Long?>(null)
        compose.setContent {
            CompositionLocalProvider(LocalUiSecurityManager provides f.security,
                LocalDensity provides Density(LocalDensity.current.density, if (large) 1.5f else 1f)) {
                takagi.ru.monica.ui.theme.MonicaTheme(darkTheme = large) {
                    val holder = rememberSaveableStateHolder()
                    if (phase == 0) UnifiedWalletAddScreen(selected, { selected = it }, { phase = 1 }, bank, document, billing, holder,
                        initialStorageExplicit = true)
                    else if (savedId != null) DocumentDetailScreen(document, savedId!!, {}, {})
                }
            }
        }
        fun choose(tab: CardWalletTab) {
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithContentDescription(text(R.string.nav_card_wallet)).performClick()
            compose.onNodeWithTag("wallet_template_${tab.name}").performScrollTo().performClick()
            compose.waitForIdle()
        }
        choose(template)
        compose.onNodeWithTag("item_editor_title").performTextReplacement(f.prefix)
        input(if (type == "TRAVEL") R.string.wallet_pass_booking else R.string.wallet_pass_number, "00001234")
        input(if (type == "TRAVEL") R.string.wallet_pass_passenger else R.string.wallet_pass_holder, "Synthetic Person")
        input(R.string.wallet_pass_issuer, "Synthetic Issuer")
        input(extraLabel, extraValue)
        // Each template owns its own draft, including when leaving and returning.
        choose(if (template == CardWalletTab.TRAVEL_PASSES) CardWalletTab.MEMBERSHIP_CARDS else CardWalletTab.TRAVEL_PASSES)
        choose(template)
        compose.onNode(hasText(extraValue) and hasSetTextAction()).performScrollTo().assertExists()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        capture("$type-editor")
        compose.onNodeWithContentDescription(text(R.string.save)).performClick()
        compose.waitUntil(30000) { phase == 1 }
        val row = runBlocking { f.secureItems.getAllItems().first().single { it.title == f.prefix } }
        val data = CardWalletDataCodec.parseDocumentData(row.itemData, f.security::decryptDataIfMonicaCiphertext)!!
        assertEquals(type, data.walletTemplate)
        assertEquals("00001234", data.documentNumber)
        assertEquals(extraValue, data.walletFields[extraKey])
        compose.runOnIdle { savedId = row.id }
        compose.onNodeWithText(extraValue).performScrollTo().assertIsDisplayed()
        capture("$type-detail")
    }

    @Test fun membershipCreateSwitchDraftAndDetail() = create(CardWalletTab.MEMBERSHIP_CARDS, "MEMBERSHIP", R.string.wallet_pass_tier, "tier", "Gold")
    @Test fun rewardsCreateSwitchDraftAndDetail() = create(CardWalletTab.REWARDS_CARDS, "REWARDS", R.string.wallet_pass_points, "points", "0001250.50")
    @Test fun travelCreateSwitchDraftAndDetailAtLargeFont() = create(CardWalletTab.TRAVEL_PASSES, "TRAVEL", R.string.wallet_pass_seat, "seat", "12A", large = true)

    @Test fun reeditUpdatesInPlaceAndRetainsFutureFields() {
        val original = DocumentData(DocumentType.OTHER, "0001", "Synthetic Person", walletTemplate = "TRAVEL",
            walletFields = mapOf("seat" to "12A", "future" to "keep"))
        val id = runBlocking { f.db.secureItemDao().insertItem(SecureItem(itemType = ItemType.DOCUMENT, title = f.prefix,
            itemData = CardWalletDataCodec.encodeDocumentData(original))) }
        var closed = false
        compose.setContent { CompositionLocalProvider(LocalUiSecurityManager provides f.security) {
            takagi.ru.monica.ui.theme.MonicaTheme { AddEditDocumentScreen(document, documentId = id, onNavigateBack = { closed = true }) }
        } }
        compose.waitUntil(15000) { compose.onAllNodesWithText("12A").fetchSemanticsNodes().isNotEmpty() }
        input(R.string.wallet_pass_seat, "14B")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithContentDescription(text(R.string.save)).performClick()
        compose.waitUntil(30000) { closed }
        val rows = runBlocking { f.secureItems.getAllItems().first().filter { it.title == f.prefix } }
        assertEquals(listOf(id), rows.map { it.id })
        val data = CardWalletDataCodec.parseDocumentData(rows.single().itemData, f.security::decryptDataIfMonicaCiphertext)!!
        assertEquals(original.copy(walletFields = original.walletFields + ("seat" to "14B")), data)
    }

    @Test fun encryptedBitwardenFreshDownloadRetainsEveryTemplate() = runBlocking {
        val target = f.bitwarden(unlocked = false)
        val vault = f.db.bitwardenVaultDao().getVaultById(target.databaseId)!!
        WalletPassTemplate.entries.forEach { template ->
            val data = DocumentData(DocumentType.OTHER, "00001234", "Synthetic Person", issuedBy = "Synthetic Issuer",
                walletTemplate = template.name, walletFields = mapOf("seat" to "12A", "points" to "0001250.50", "future" to "keep"))
            val item = SecureItem(itemType = ItemType.DOCUMENT, title = "${f.prefix}-${template.name}",
                itemData = CardWalletDataCodec.encodeDocumentData(data), bitwardenVaultId = vault.id)
            val id = f.db.secureItemDao().insertItem(item)
            assertTrue(CipherUploadProcessor(f.context).uploadSecureItem(vault, item.copy(id = id), f.accessToken, f.vaultKey) is UploadItemResult.Success)
            val wire = f.remote.created.last()
            assertFalse(wire.toString().contains("0001250.50"))
            assertTrue(wire.getJSONObject("identity").isNull("ssn"))
            val response = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<CipherApiResponse>(
                org.json.JSONObject(wire.toString()).put("id", java.util.UUID.randomUUID().toString()).toString())
            assertFalse(CipherSyncProcessor(f.context).syncCipherFromServer(vault, response, f.vaultKey) is CipherSyncResult.Error)
            val restoredRow = f.db.secureItemDao().getByBitwardenCipherIdInVault(vault.id, response.id)!!
            val restored = CardWalletDataCodec.parseDocumentData(restoredRow.itemData, f.security::decryptDataIfMonicaCiphertext)!!
            assertEquals(data.walletTemplate, restored.walletTemplate)
            assertEquals(data.walletFields, restored.walletFields)
            assertEquals(data.documentNumber, restored.documentNumber)
            assertTrue(restored.customFields.isEmpty())
        }
    }

    @Test fun localKeepassAndMdbxReplicasRetainPassesAfterReopen() = runBlocking {
        val keepass = f.keepass()
        val mdbx = f.mdbx()
        val targets = listOf(StorageTarget.MonicaLocal(null), StorageTarget.KeePass(keepass.databaseId, null), StorageTarget.Mdbx(mdbx.databaseId))
        WalletPassTemplate.entries.forEach { template ->
            val data = DocumentData(DocumentType.OTHER, "0001", "Synthetic", walletTemplate = template.name,
                walletFields = mapOf("origin" to "上海", "seat" to "12A", "points" to "0010"))
            val completed = CompletableDeferred<Result<Long>>()
            withContext(Dispatchers.Main) {
                document.saveDocumentAcrossTargets(null, "${f.prefix}-${template.name}", data, targets = targets,
                    onComplete = { completed.complete(it) })
            }
            withTimeout(30000) { completed.await() }.getOrThrow()
            val replicas = f.secureItems.getAllItems().first().filter { it.title == "${f.prefix}-${template.name}" }
            assertEquals(3, replicas.size)
            replicas.forEach { row -> assertEquals(data, CardWalletDataCodec.parseDocumentData(row.itemData, f.security::decryptDataIfMonicaCiphertext)) }
        }
        takagi.ru.monica.utils.KeePassKdbxService.invalidateProcessCache(keepass.databaseId)
        val reopened = takagi.ru.monica.utils.KeePassKdbxService(f.context, f.db.localKeePassDatabaseDao(), f.security)
            .readSecureItems(keepass.databaseId).getOrThrow()
        assertEquals(3, reopened.size)
        assertEquals(WalletPassTemplate.entries.map { it.name }.toSet(), reopened.map {
            CardWalletDataCodec.parseDocumentData(it.item.itemData)!!.walletTemplate
        }.toSet())
        takagi.ru.monica.repository.Mdbx2NativeReadSessions.clear()
        val manager = MdbxViewModel(f.context.applicationContext as android.app.Application, f.db.localMdbxDatabaseDao(),
            f.db.mdbxRemoteSourceDao(), f.db.passwordEntryDao(), f.db.secureItemDao(), f.db.passkeyDao(), f.db.attachmentDao(), f.db.customFieldDao(), f.security)
        try {
            manager.syncVault(mdbx.databaseId)
            val result = withTimeout(30000) { manager.operationState.first { it is MdbxViewModel.OperationState.Success || it is MdbxViewModel.OperationState.Error } }
            assertTrue(result.toString(), result is MdbxViewModel.OperationState.Success)
            val projected = f.db.secureItemDao().getByMdbxDatabaseIdSync(mdbx.databaseId)
            assertEquals(3, projected.size)
            assertEquals(WalletPassTemplate.entries.map { it.name }.toSet(), projected.map {
                CardWalletDataCodec.parseDocumentData(it.itemData, f.security::decryptDataIfMonicaCiphertext)!!.walletTemplate
            }.toSet())
        } finally { manager.viewModelScope.cancel() }
    }
}
