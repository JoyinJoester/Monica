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
            assertEquals("Original custom field", decoded["开户行"])
            // A different cipher id forces fresh server import rather than the unchanged-row shortcut.
            val response = Json { ignoreUnknownKeys = true }.decodeFromString<CipherApiResponse>(
                org.json.JSONObject(wire.toString()).put("id", java.util.UUID.randomUUID().toString()).toString())
            val imported = CipherSyncProcessor(f.context).syncCipherFromServer(vault, response, f.vaultKey)
            assertFalse(imported.toString(), imported is CipherSyncResult.Error)
            val row = f.db.secureItemDao().getByBitwardenCipherIdInVault(vault.id, response.id)!!
            val restored = CardWalletDataCodec.parseBankCardData(row.itemData, f.security::decryptDataIfMonicaCiphertext)!!
            assertEquals(data.bankBranch, restored.bankBranch)
            assertEquals(data.bankName, restored.bankName)
            assertEquals(data.branchCode, restored.branchCode)
            assertEquals(data.customFields, restored.customFields)
        } finally { f.close() }
    }
}
