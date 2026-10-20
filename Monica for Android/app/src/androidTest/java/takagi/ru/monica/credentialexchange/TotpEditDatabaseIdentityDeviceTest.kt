package takagi.ru.monica.credentialexchange

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import takagi.ru.monica.data.*
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.data.model.StorageTarget
import takagi.ru.monica.utils.*
import takagi.ru.monica.viewmodel.*
import java.util.UUID

/** Only disposable fixture vaults; equal OTP secrets must remain separate entries. */
class TotpEditDatabaseIdentityDeviceTest {
    private lateinit var f: TransferFixture
    private lateinit var model: TotpViewModel
    private lateinit var settings: SettingsManager
    private lateinit var previousFilter: SavedCategoryFilterState
    @Before fun setup() {
        f = TransferFixture()
        settings = SettingsManager(f.context)
        previousFilter = runBlocking { settings.categoryFilterStateFlow(SettingsManager.CategoryFilterScope.TOTP).first() }
        model = TotpViewModel(f.secureItems, f.passwords, f.context,
            f.db.localKeePassDatabaseDao(), f.security, AppLocaleStringResolver(f.context))
    }
    @After fun cleanup() = runBlocking {
        model.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
        try { f.close() } finally {
            settings.updateCategoryFilterState(SettingsManager.CategoryFilterScope.TOTP, previousFilter)
        }
    }

    private fun checkEdit(legacy: Boolean, sourceId: Boolean = true, ambiguous: Boolean = false) = runBlocking {
        val destination = f.keepass()
        val databaseId = destination.databaseId!!
        val service = KeePassKdbxService(f.context, f.db.localKeePassDatabaseDao(), f.security)
        val data = TotpData(secret = "JBSWY3DPEHPK3PXP", issuer = "Synthetic", accountName = "fixture@example.invalid")
        val originalUuid = UUID.randomUUID().toString()
        val id = f.db.secureItemDao().insertItem(SecureItem(itemType = ItemType.TOTP, title = f.prefix,
            itemData = Json.encodeToString(data), keepassDatabaseId = databaseId,
            keepassEntryUuid = if (legacy) null else originalUuid))
        val original = f.secureItems.getItemById(id)!!
        service.updateSecureItem(databaseId, original.copy(id = if (sourceId) id else 0, keepassEntryUuid = originalUuid)).getOrThrow()
        if (!sourceId) {
            val native = service.readSecureItems(databaseId, setOf(ItemType.TOTP)).getOrThrow().single().item
            f.db.secureItemDao().updateItem(original.copy(keepassGroupPath = native.keepassGroupPath))
        }
        val otherUuid = UUID.randomUUID().toString()
        service.updateSecureItem(databaseId, original.copy(id = 0, title = if (ambiguous) f.prefix else "${f.prefix}-distinct",
            keepassEntryUuid = otherUuid)).getOrThrow()
        assertEquals(2, f.keepassEntries(databaseId).size)
        val result = CompletableDeferred<Boolean>()
        var failure: String? = null
        withContext(Dispatchers.Main) {
            model.saveTotpAcrossTargets(id, "${f.prefix}-edited", "Edited notes", data,
                targets = listOf(StorageTarget.KeePass(databaseId, null)), onFailure = { failure = it },
                onComplete = { result.complete(it) })
        }
        assertEquals(!ambiguous, withTimeout(30000) { result.await() })
        assertEquals("Editing must not append a third native entry", 2, f.keepassEntries(databaseId).size)
        if (ambiguous) {
            assertEquals(f.prefix, f.secureItems.getItemById(id)!!.title)
            assertTrue(!failure.isNullOrBlank())
            assertTrue(f.keepassEntries(databaseId).all { it.fields["Title"]?.content == f.prefix })
            return@runBlocking
        }
        val native = f.keepassEntries(databaseId).single { it.uuid.toString() == originalUuid }
        assertEquals("${f.prefix}-edited", native.fields["Title"]?.content)
        assertEquals("Edited notes", native.fields["Notes"]?.content)
        assertEquals(originalUuid, f.secureItems.getItemById(id)!!.keepassEntryUuid)
        repeat(6) {
            withContext(Dispatchers.Main) {
                model.setCategoryFilter(TotpCategoryFilter.Local)
                model.setCategoryFilter(TotpCategoryFilter.All)
                model.setCategoryFilter(TotpCategoryFilter.KeePassDatabase(databaseId))
            }
            KeePassKdbxService.invalidateProcessCache(databaseId)
            val reopened = service.readSecureItems(databaseId, setOf(ItemType.TOTP)).getOrThrow()
            assertEquals(setOf(originalUuid, otherUuid), reopened.map { it.item.keepassEntryUuid }.toSet())
        }
        withTimeout(30000) {
            f.secureItems.getItemsByType(ItemType.TOTP).first { rows -> rows.count { it.keepassDatabaseId == databaseId } >= 2 }
        }
        val rows = f.secureItems.getItemsByType(ItemType.TOTP).first().filter { it.keepassDatabaseId == databaseId }
        assertEquals(2, rows.size)
        assertEquals(id, rows.single { it.keepassEntryUuid == originalUuid }.id)
        assertEquals("${f.prefix}-edited", rows.single { it.id == id }.title)
        assertEquals("Edited notes", rows.single { it.id == id }.notes)
    }
    @Test fun legacyProjectionWithoutUuidEditsOriginalThenSwitchesDatabases() = checkEdit(true)
    @Test fun currentProjectionEditsOriginalWithoutMergingEqualSecrets() = checkEdit(false)
    @Test fun legacyWithoutSourceIdResolvesUsingOriginalDataBeforeRename() = checkEdit(true, sourceId = false)
    @Test fun ambiguousLegacyIdentityRejectsEditWithoutWriting() = checkEdit(true, sourceId = false, ambiguous = true)

    @Test fun movingLocalTotpToKeepassStillCreatesOneNativeEntry() = runBlocking {
        val databaseId = f.keepass().databaseId!!
        val data = TotpData(secret = "JBSWY3DPEHPK3PXP")
        val id = f.db.secureItemDao().insertItem(SecureItem(itemType = ItemType.TOTP, title = f.prefix,
            itemData = Json.encodeToString(data)))
        val result = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main) {
            model.saveTotpAcrossTargets(id, f.prefix, "", data,
                targets = listOf(StorageTarget.KeePass(databaseId, null)), onComplete = { result.complete(it) })
        }
        assertTrue(withTimeout(30000) { result.await() })
        assertEquals(1, f.keepassEntries(databaseId).size)
        assertEquals(f.secureItems.getItemById(id)!!.keepassEntryUuid, f.keepassEntries(databaseId).single().uuid.toString())
    }
}
