package takagi.ru.monica.credentialexchange

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.bitwarden.api.CipherApiResponse
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.bitwarden.service.*
import takagi.ru.monica.data.*
import takagi.ru.monica.data.model.*

class BankBranchSyncInstrumentedTest {
    @Test fun encryptedBitwardenUploadAndFreshDownloadRetainBranchWithoutDuplicateCustomField() = runBlocking {
        val f = TransferFixture()
        try {
            val destination = f.bitwarden(unlocked = false)
            val vault = f.db.bitwardenVaultDao().getVaultById(destination.databaseId)!!
            val data = BankCardData("4242424242424242", "Synthetic Holder", "12", "2030",
                cardholderFirstName = "Separate", cardholderMiddleName = "Middle", cardholderLastName = "Parts",
                bankName = "Example Bank", bankBranch = "北京海淀支行", branchCode = "0102",
                customFields = listOf(SecureCustomField("开户行", "Original custom field")))
            val item = SecureItem(itemType = ItemType.BANK_CARD, title = f.prefix,
                itemData = CardWalletDataCodec.encodeBankCardData(data), bitwardenVaultId = vault.id)
            val id = f.db.secureItemDao().insertItem(item)
            val result = CipherUploadProcessor(f.context).uploadSecureItem(vault, item.copy(id = id), f.accessToken, f.vaultKey)
            assertTrue(result.toString(), result is UploadItemResult.Success)
            val wire = f.remote.created.single()
            assertFalse(wire.toString().contains(data.bankBranch))
            val fields = wire.getJSONArray("fields")
            val decoded = (0 until fields.length()).associate { i ->
                val field = fields.getJSONObject(i)
                BitwardenCrypto.decryptToString(field.getString("name"), f.vaultKey) to
                    BitwardenCrypto.decryptToString(field.getString("value"), f.vaultKey)
            }
            assertEquals(data.bankBranch, decoded["Bank Branch"])
            assertEquals(data.cardholderFirstName, decoded["Cardholder First Name"])
            assertEquals("Original custom field", decoded["开户行"])
            // A different cipher id forces fresh server import rather than the unchanged-row shortcut.
            val response = Json { ignoreUnknownKeys = true }.decodeFromString<CipherApiResponse>(
                org.json.JSONObject(wire.toString()).put("id", java.util.UUID.randomUUID().toString()).toString())
            val imported = CipherSyncProcessor(f.context).syncCipherFromServer(vault, response, f.vaultKey)
            assertFalse(imported.toString(), imported is CipherSyncResult.Error)
            val row = f.db.secureItemDao().getByBitwardenCipherIdInVault(vault.id, response.id)!!
            val restored = CardWalletDataCodec.parseBankCardData(row.itemData, f.security::decryptDataIfMonicaCiphertext)!!
            assertEquals(data.bankBranch, restored.bankBranch)
            assertEquals(data.cardholderName, restored.cardholderName)
            assertEquals(data.cardholderFirstName, restored.cardholderFirstName)
            assertEquals(data.cardholderMiddleName, restored.cardholderMiddleName)
            assertEquals(data.cardholderLastName, restored.cardholderLastName)
            assertEquals(data.bankName, restored.bankName)
            assertEquals(data.branchCode, restored.branchCode)
            assertEquals(data.customFields, restored.customFields)
        } finally { f.close() }
    }
    @Test fun bitwardenIdentityKeepsIndependentNamesAndGroupedContacts() = runBlocking {
        val f = TransferFixture()
        try {
            val target = f.bitwarden(unlocked = false)
            val vault = f.db.bitwardenVaultDao().getVaultById(target.databaseId)!!
            val data = DocumentData(DocumentType.PASSPORT, "P-TEST", "Original Full Name",
                firstName = "Different", middleName = "Middle", lastName = "Parts", district = "County",
                email = "default@example.test", phone = "+12025550142", homeEmail = "home@example.test",
                homePhone = "+12025550143", workEmail = "work@example.test", workPhone = "+12025550144")
            val item = SecureItem(itemType = ItemType.DOCUMENT, title = f.prefix,
                itemData = CardWalletDataCodec.encodeDocumentData(data), bitwardenVaultId = vault.id)
            val id = f.db.secureItemDao().insertItem(item)
            assertTrue(CipherUploadProcessor(f.context).uploadSecureItem(vault, item.copy(id=id), f.accessToken, f.vaultKey) is UploadItemResult.Success)
            val wire = f.remote.created.single()
            assertFalse(wire.toString().contains(data.homeEmail))
            val response = Json { ignoreUnknownKeys = true }.decodeFromString<CipherApiResponse>(
                org.json.JSONObject(wire.toString()).put("id", java.util.UUID.randomUUID().toString()).toString())
            assertFalse(CipherSyncProcessor(f.context).syncCipherFromServer(vault, response, f.vaultKey) is CipherSyncResult.Error)
            val row = f.db.secureItemDao().getByBitwardenCipherIdInVault(vault.id, response.id)!!
            val restored = CardWalletDataCodec.parseDocumentData(row.itemData, f.security::decryptDataIfMonicaCiphertext)!!
            assertEquals(data.fullName, restored.fullName)
            assertEquals(data.firstName, restored.firstName)
            assertEquals(data.middleName, restored.middleName)
            assertEquals(data.lastName, restored.lastName)
            assertEquals(data.district, restored.district)
            assertEquals(data.homeEmail, restored.homeEmail)
            assertEquals(data.homePhone, restored.homePhone)
            assertEquals(data.workEmail, restored.workEmail)
            assertEquals(data.workPhone, restored.workPhone)
            assertTrue(restored.customFields.isEmpty())

            // A pre-extension remote record must not erase the stored independent full name.
            val legacy = response.copy(
                revisionDate = "2099-01-01T00:00:00.000Z",
                fields = response.fields?.filterNot {
                    BitwardenCrypto.decryptToString(it.name!!, f.vaultKey) == "monica_full_name"
                }
            )
            assertFalse(CipherSyncProcessor(f.context).syncCipherFromServer(vault, legacy, f.vaultKey) is CipherSyncResult.Error)
            val kept = f.db.secureItemDao().getByBitwardenCipherIdInVault(vault.id, response.id)!!
            assertEquals(data.fullName, CardWalletDataCodec.parseDocumentData(kept.itemData, f.security::decryptDataIfMonicaCiphertext)!!.fullName)

            // Clearing is transmitted explicitly, so a later sync does not resurrect it.
            val clearedItem = item.copy(itemData = CardWalletDataCodec.encodeDocumentData(data.copy(fullName = "")))
            val clearedId = f.db.secureItemDao().insertItem(clearedItem)
            assertTrue(CipherUploadProcessor(f.context).uploadSecureItem(vault, clearedItem.copy(id = clearedId), f.accessToken, f.vaultKey) is UploadItemResult.Success)
            val cleared = Json { ignoreUnknownKeys = true }.decodeFromString<CipherApiResponse>(
                org.json.JSONObject(f.remote.created.last().toString()).put("id", response.id)
                    .put("revisionDate", "2099-01-02T00:00:00.000Z").toString())
            assertTrue(cleared.fields.orEmpty().any { BitwardenCrypto.decryptToString(it.name!!, f.vaultKey) == "monica_full_name" })
            assertFalse(CipherSyncProcessor(f.context).syncCipherFromServer(vault, cleared, f.vaultKey) is CipherSyncResult.Error)
            val emptyRow = f.db.secureItemDao().getByBitwardenCipherIdInVault(vault.id, response.id)!!
            val emptyData = CardWalletDataCodec.parseDocumentData(emptyRow.itemData, f.security::decryptDataIfMonicaCiphertext)!!
            assertEquals("", emptyData.fullName)
            assertEquals(data.firstName, emptyData.firstName)
        } finally { f.close() }
    }

}
