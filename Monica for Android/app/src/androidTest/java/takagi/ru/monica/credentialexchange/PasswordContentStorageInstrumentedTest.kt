package takagi.ru.monica.credentialexchange

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.keemobile.kotpass.models.EntryValue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.data.CustomFieldDraft
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.model.StorageTarget
import takagi.ru.monica.repository.CustomFieldRepository
import takagi.ru.monica.repository.Mdbx2NativeReadSessions
import takagi.ru.monica.repository.Mdbx2Repository
import takagi.ru.monica.utils.AppLocaleStringResolver
import takagi.ru.monica.utils.KeePassKdbxService
import takagi.ru.monica.viewmodel.PasswordViewModel

/** The optional editor must keep using ordinary password rows and native fields. */
@RunWith(AndroidJUnit4::class)
class PasswordContentStorageInstrumentedTest {
    private val cardNumber = "4242424242424242"
    private val note = "部署说明\n\n订阅：年度计划 🔐\n  保留缩进和末尾空白  \n"
    private val extra = listOf(CustomFieldDraft(title = "Recovery hint", value = "synthetic hint", isProtected = true))

    @Test fun mdbxSecureFieldsSurviveNativeReopenProjectionAndTitleEdit() = runBlocking {
        scenario { _ ->
            val target = mdbx()
            val fields = listOf(
                SecureCustomField("Recovery", "synthetic-secret", SecureCustomFieldType.HIDDEN),
                SecureCustomField("Enabled", "true", SecureCustomFieldType.BOOLEAN),
                SecureCustomField("Support", "help@example.invalid"),
            )
            val samples = listOf(
                ItemType.TOTP to Json.encodeToString(TotpData(secret = "JBSWY3DPEHPK3PXP",
                    steamDeviceId = "synthetic-device")),
                ItemType.NOTE to Json.encodeToString(NoteData(content = note, customFields = fields)),
                ItemType.BANK_CARD to Json.encodeToString(BankCardData(cardNumber = cardNumber,
                    cardholderName = "Alice", expiryMonth = "09", expiryYear = "2030", bankName = "Example Bank", bankBranch = "海淀支行", branchCode = "0102", customFields = fields)),
                ItemType.BILLING_ADDRESS to Json.encodeToString(BillingAddressData(
                    streetAddress = "12 Example Street", postalCode = "SW1A 1AA", customFields = fields)),
                ItemType.DOCUMENT to Json.encodeToString(DocumentData(documentType = DocumentType.PASSPORT,
                    documentNumber = "SYNTHETIC-PASSPORT", fullName = "Alice", customFields = fields)),
            )
            mdbx.upsertSecureItems(samples.mapIndexed { index, (type, data) ->
                SecureItem(id = 1000L + index, itemType = type, title = "$prefix-$type",
                    itemData = data, mdbxDatabaseId = target.databaseId,
                    replicaGroupId = java.util.UUID.randomUUID().toString())
            })
            repeat(2) { pass ->
                Mdbx2NativeReadSessions.clear()
                val manager = takagi.ru.monica.viewmodel.MdbxViewModel(
                    context.applicationContext as android.app.Application, db.localMdbxDatabaseDao(),
                    db.mdbxRemoteSourceDao(), db.passwordEntryDao(), db.secureItemDao(),
                    db.passkeyDao(), db.attachmentDao(), db.customFieldDao(), security)
                try {
                    manager.syncVault(target.databaseId)
                    val result = withTimeout(30_000) { manager.operationState.first {
                        it is takagi.ru.monica.viewmodel.MdbxViewModel.OperationState.Success ||
                            it is takagi.ru.monica.viewmodel.MdbxViewModel.OperationState.Error
                    } }
                    assertTrue(result.toString(), result is takagi.ru.monica.viewmodel.MdbxViewModel.OperationState.Success)
                    val projected = db.secureItemDao().getByMdbxDatabaseIdSync(target.databaseId)
                    assertEquals(samples.map { it.first }.toSet(), projected.map { it.itemType }.toSet())
                    projected.forEach { item ->
                        val data = JSONObject(security.decryptDataIfMonicaCiphertext(item.itemData))
                        if (pass == 1) assertTrue(item.title.endsWith(" renamed"))
                        if (item.itemType == ItemType.TOTP) {
                            assertEquals("synthetic-device", data.getString("steamDeviceId"))
                            assertEquals("JBSWY3DPEHPK3PXP", data.getString("secret"))
                            return@forEach
                        }
                        if (item.itemType == ItemType.BANK_CARD) {
                            assertEquals("海淀支行", data.getString("bankBranch"))
                            assertEquals("Example Bank", data.getString("bankName"))
                            assertEquals("0102", data.getString("branchCode"))
                        }
                        val restored = data.getJSONArray("customFields")
                        assertEquals(3, restored.length())
                        assertEquals("HIDDEN", restored.getJSONObject(0).getString("type"))
                        assertEquals("synthetic-secret", restored.getJSONObject(0).getString("value"))
                        assertEquals("BOOLEAN", restored.getJSONObject(1).getString("type"))
                        assertEquals("true", restored.getJSONObject(1).getString("value"))
                    }
                    if (pass == 0) mdbx.upsertSecureItems(projected.map { it.copy(title = it.title + " renamed") })
                } finally { manager.viewModelScope.cancel() }
            }
        }
    }

    private suspend fun scenario(block: suspend TransferFixture.(PasswordViewModel) -> Unit) {
        val fixture = TransferFixture()
        val passwords = PasswordViewModel(fixture.passwords, fixture.security,
            customFieldRepository = CustomFieldRepository(fixture.db.customFieldDao()), context = fixture.context,
            localKeePassDatabaseDao = fixture.db.localKeePassDatabaseDao(), strings = AppLocaleStringResolver(fixture.context))
        try { fixture.block(passwords) } finally { passwords.viewModelScope.cancel(); fixture.close() }
    }

    @Test fun keepassSecureItemEditorsRetainExtendedFieldsAfterSaveEditAndReopen() = runBlocking {
        scenario { _ ->
            val target = keepass()
            val fields = listOf(SecureCustomField("Recovery", "synthetic-secret", SecureCustomFieldType.HIDDEN),
                SecureCustomField("Support", "help@example.invalid"))
            val samples = listOf(
                ItemType.TOTP to Json.encodeToString(TotpData(secret="JBSWY3DPEHPK3PXP", issuer="Example")),
                ItemType.NOTE to Json.encodeToString(NoteData(content=note, customFields=fields)),
                ItemType.BANK_CARD to Json.encodeToString(BankCardData(cardNumber=cardNumber, cardholderName="Alice", cardholderFirstName="Independent", cardholderMiddleName="Middle", cardholderLastName="Parts",
                    expiryMonth="09", expiryYear="2030", bankName="Example Bank", bankBranch="海淀支行", branchCode="0102", customFields=fields)),
                ItemType.BILLING_ADDRESS to Json.encodeToString(BillingAddressData(streetAddress="12 Example Street",
                    postalCode="SW1A 1AA", firstName="Independent", fullName="Full Original", district="County", addressLine3="Line3", homeEmail="home@example.test", workPhone="+12025550144", customFields=fields)),
                ItemType.DOCUMENT to Json.encodeToString(DocumentData(documentType=DocumentType.PASSPORT,
                    documentNumber="SYNTHETIC-PASSPORT", fullName="Alice", firstName="Independent", district="County", workEmail="work@example.test", customFields=fields)),
            )
            val service = KeePassKdbxService(context, db.localKeePassDatabaseDao(), security)
            val items = samples.mapIndexed { index, (type, data) ->
                SecureItem(id=1000L+index, itemType=type, title="$prefix-$type", itemData=data,
                    keepassDatabaseId=target.databaseId)
            }
            service.addOrUpdateSecureItems(target.databaseId, items, forceSyncWrite=true).getOrThrow()
            repeat(2) { pass ->
                KeePassKdbxService.invalidateProcessCache(target.databaseId)
                val reopened = service.readSecureItems(target.databaseId).getOrThrow().map { it.item }
                assertEquals(samples.map { it.first }.toSet(), reopened.map { it.itemType }.toSet())
                reopened.forEach { item ->
                    val data = JSONObject(item.itemData)
                    if (item.itemType == ItemType.TOTP) {
                        assertEquals("Example", data.getString("issuer"))
                        assertEquals("JBSWY3DPEHPK3PXP", data.getString("secret"))
                        return@forEach
                    }
                    val extra = data.getJSONArray("customFields")
                    assertEquals("${item.itemType}: field count",2,extra.length())
                    assertEquals("synthetic-secret",extra.getJSONObject(0).getString("value"))
                    assertEquals("HIDDEN",extra.getJSONObject(0).getString("type"))
                    assertEquals("help@example.invalid",extra.getJSONObject(1).getString("value"))
                    if (item.itemType == ItemType.BILLING_ADDRESS) {
                        assertEquals("Full Original", data.getString("fullName"))
                        assertEquals("Independent", data.getString("firstName"))
                        assertEquals("Line3", data.getString("addressLine3"))
                        assertEquals("County", data.getString("district"))
                        assertEquals("home@example.test", data.getString("homeEmail"))
                    }
                    if (item.itemType == ItemType.DOCUMENT) {
                        assertEquals("Alice", data.getString("fullName"))
                        assertEquals("Independent", data.getString("firstName"))
                        assertEquals("work@example.test", data.getString("workEmail"))
                    }
                    if (item.itemType == ItemType.NOTE) assertEquals(note,data.getString("content"))
                    if (item.itemType == ItemType.BANK_CARD) {
                        assertEquals("2030", data.getString("expiryYear"))
                        assertEquals("Independent", data.getString("cardholderFirstName"))
                        assertEquals("Middle", data.getString("cardholderMiddleName"))
                        assertEquals("Parts", data.getString("cardholderLastName"))
                        assertEquals("海淀支行", data.getString("bankBranch"))
                        assertEquals("Example Bank", data.getString("bankName"))
                        assertEquals("0102", data.getString("branchCode"))
                    }
                }
                if (pass == 0) service.addOrUpdateSecureItems(target.databaseId,
                    reopened.map { it.copy(title=it.title+" renamed") }, forceSyncWrite=true).getOrThrow()
            }
        }
    }
}
