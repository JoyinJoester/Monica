package takagi.ru.monica.credentialexchange

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import takagi.ru.monica.R
import takagi.ru.monica.data.model.*
import takagi.ru.monica.ui.LocalUiSecurityManager
import takagi.ru.monica.ui.screens.AddEditTotpScreen
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.util.*
import takagi.ru.monica.utils.AppLocaleStringResolver
import takagi.ru.monica.viewmodel.TotpViewModel

class TotpImportTitleDeviceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var fixture: TransferFixture
    private lateinit var model: TotpViewModel
    @Before fun setup() {
        fixture = TransferFixture()
        model = TotpViewModel(fixture.secureItems, fixture.passwords, fixture.context,
            fixture.db.localKeePassDatabaseDao(), fixture.security, AppLocaleStringResolver(fixture.context))
    }
    @After fun cleanup() = runBlocking { model.viewModelScope.cancel(); fixture.close() }

    private fun checkEditor(fromScan: Boolean, customTitle: Boolean) {
        val issuer = fixture.prefix + ": Team"
        val account = "alice:work@example.com"
        val data = TotpData(secret = "JBSWY3DPEHPK3PXP", issuer = issuer, accountName = account,
            otpType = OtpType.HOTP, counter = 42, digits = 8, algorithm = "SHA256")
        val uri = TotpUriParser.generateUri("$issuer:$account", data)
        var qr by mutableStateOf(if (fromScan) uri else null)
        var savedTitle: String? = null
        var savedData: TotpData? = null
        compose.setContent { CompositionLocalProvider(LocalUiSecurityManager provides fixture.security) { MonicaTheme {
            AddEditTotpScreen(null, null, if (customTitle) fixture.prefix + " custom" else "", "",
                initialStorageExplicit = true, pendingQrResult = qr, onConsumePendingQrResult = { qr = null },
                onSave = { title, _, otp, _, _, done -> savedTitle = title; savedData = otp; done(true) },
                onBatchImport = { _, _, _ -> }, onNavigateBack = {}, onScanQrCode = {})
        } } }
        if (!fromScan) {
            compose.onNodeWithTag("totp_item_editor").performScrollToNode(hasText(fixture.context.getString(R.string.totp_import_uri_action)))
            compose.onNodeWithText(fixture.context.getString(R.string.totp_import_uri_action)).performClick()
            compose.onNode(hasText(fixture.context.getString(R.string.totp_import_uri_dialog_label)) and hasSetTextAction()).performTextInput(uri)
            compose.onNodeWithText(fixture.context.getString(R.string.confirm)).performClick()
        }
        val expected = if (customTitle) fixture.prefix + " custom" else issuer
        compose.waitUntil(10000) { compose.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("totp_item_editor").performScrollToNode(hasTestTag("item_editor_title"))
        compose.onNodeWithTag("item_editor_title").assertTextContains(expected)
        compose.onNodeWithContentDescription(fixture.context.getString(R.string.save)).performClick()
        compose.waitUntil(10000) { savedData != null }
        assertEquals(expected, savedTitle)
        assertEquals(issuer, savedData!!.issuer)
        assertEquals(account, savedData!!.accountName)
        assertEquals(data.secret, savedData!!.secret)
        assertEquals(data.counter, savedData!!.counter)
        assertEquals(data.algorithm, savedData!!.algorithm)
        assertEquals(data.digits, savedData!!.digits)
        assertEquals(data.otpType, savedData!!.otpType)
        val exported = TotpDataResolver.toBitwardenPayload(savedTitle!!, savedData!!)
        assertEquals(account, TotpUriParser.parseUri(exported)!!.totpData.accountName)
    }

    @Test fun scannedUriSuggestsOnlyIssuerAndKeepsAccountAndHotpParameters() = checkEditor(true, false)
    @Test fun pastedUriSuggestsOnlyIssuerAndKeepsAccount() = checkEditor(false, false)
    @Test fun importingIntoNamedDraftKeepsUserTitle() = checkEditor(false, true)

    @Test fun uriWithoutIssuerUsesAccountAndLabelIssuerIsRecognized() {
        val accountOnly = TotpUriParser.parseUri("otpauth://totp/alice%2Bwork%40example.com?secret=JBSWY3DPEHPK3PXP")!!
        assertEquals("alice+work@example.com", accountOnly.suggestedTitle)
        assertEquals("alice+work@example.com", accountOnly.totpData.accountName)
        val labelIssuer = TotpUriParser.parseUri("otpauth://totp/Example%3Aalice%40example.com?secret=JBSWY3DPEHPK3PXP")!!
        assertEquals("Example", labelIssuer.suggestedTitle)
        assertEquals("alice@example.com", labelIssuer.totpData.accountName)
    }

    @Test fun migrationSavesTwoAccountsWithSameIssuerAsSeparateRows() = runBlocking {
        val issuer = fixture.prefix
        val items = listOf("alice" to "JBSWY3DPEHPK3PXP", "bob" to "GEZDGNBVGY3TQOJQ").map { (account, secret) ->
            TotpParseResult(TotpData(secret = secret, issuer = issuer, accountName = account), "$issuer:$account", account)
        }
        val result = CompletableDeferred<takagi.ru.monica.viewmodel.TotpMigrationSaveResult>()
        model.saveTotpMigrationBatch(items, listOf(StorageTarget.MonicaLocal(null))) { result.complete(it) }
        val saved = withTimeout(30000) { result.await() }
        assertEquals(2, saved.importedCount)
        assertEquals(0, saved.failedCount)
        val rows = fixture.db.secureItemDao().getAllItems().first().filter { it.title == issuer }
        assertEquals(2, rows.size)
        assertEquals(setOf("alice", "bob"), rows.map { model.parseTotpDataForDisplay(it)!!.accountName }.toSet())
    }
}
