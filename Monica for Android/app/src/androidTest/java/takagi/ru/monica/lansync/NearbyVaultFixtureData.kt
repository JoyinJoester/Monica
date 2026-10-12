package takagi.ru.monica.lansync

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject
import org.junit.Assert.*
import takagi.ru.monica.attachments.model.Attachment
import takagi.ru.monica.attachments.storage.AttachmentKeyVault
import takagi.ru.monica.attachments.storage.AttachmentStorage
import takagi.ru.monica.data.*
import takagi.ru.monica.data.model.*
import takagi.ru.monica.security.SecurityManager
import java.security.MessageDigest
import java.util.Base64

/** User-independent data covering real item schemas and encrypted attachment storage. */
internal class NearbyVaultFixtureData(private val context: Context) {
    private val storage = AttachmentStorage(context)
    private val createdBlobs = mutableSetOf<String>()
    private val payloads = linkedMapOf(
        ItemType.NOTE to Json.encodeToString(NoteData.serializer(), NoteData("# 合成测试\nUnicode café 🔐", tags=listOf("fixture"), isMarkdown=true)),
        ItemType.TOTP to Json.encodeToString(TotpData.serializer(), TotpData("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", issuer="Fixture", accountName="fixture@example.test", algorithm="SHA256", digits=8)),
        ItemType.BANK_CARD to Json.encodeToString(BankCardData.serializer(), BankCardData("4111111111111111","SYNTHETIC TEST","12","2030",cvv="123")),
        ItemType.DOCUMENT to Json.encodeToString(DocumentData.serializer(), DocumentData(DocumentType.PASSPORT,"TEST-0000","SYNTHETIC TEST",nationality="Fixture")),
        ItemType.BILLING_ADDRESS to Json.encodeToString(BillingAddressData.serializer(), BillingAddressData(fullName="SYNTHETIC TEST",streetAddress="1 Test Street",city="Fixture",email="fixture@example.test")),
        ItemType.PAYMENT_ACCOUNT to Json.encodeToString(PaymentAccountData.serializer(), PaymentAccountData(provider="Fixture",accountId="synthetic-account",username="fixture",currency="USD"))
    )
    private data class ExpectedAttachment(val parent: String, val password: Boolean, val name: String, val mime: String, val bytes: ByteArray)
    private val attachments = listOf(
        ExpectedAttachment("Bulk 0",true,"大文件.bin","application/octet-stream",ByteArray(4 * 1024 * 1024 + 17) { ((it * 31 + 7) % 251).toByte() }),
        ExpectedAttachment("Bulk 0",true,"重复.txt","text/plain","synthetic first\n中文🔐".toByteArray()),
        ExpectedAttachment("Bulk 0",true,"重复.txt","text/plain","synthetic second".toByteArray()),
        ExpectedAttachment("Fixture NOTE",false,"图片.png","image/png",Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5n8AAAAASUVORK5CYII=")),
        ExpectedAttachment("Fixture DOCUMENT",false,"empty.txt","text/plain",byteArrayOf()),
        ExpectedAttachment("Trash DOCUMENT",false,"回收站.txt","text/plain","synthetic retained trash attachment".toByteArray())
    )

    suspend fun seed(db: PasswordDatabase, security: SecurityManager) {
        val folder = db.categoryDao().insert(Category(name="各种数据 / Unicode 🔐"))
        val itemIds = payloads.mapValues { (type,payload) -> db.secureItemDao().insertItem(SecureItem(
            itemType=type,title="Fixture $type",itemData=security.encryptData(payload),categoryId=folder,notes="Synthetic notes")) }
        val entries = db.passwordEntryDao().getDeviceSyncLocalPasswords().associateBy { it.title }
        val first = entries.getValue("Bulk 0")
        db.passwordEntryDao().updatePasswordEntry(first.copy(categoryId=folder,boundNoteId=itemIds.getValue(ItemType.NOTE),
            notes="Multiline\n中文 café 🔐",authenticatorKey=security.encryptData("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")))
        db.customFieldDao().insert(CustomField(entryId=first.id,title="Protected fixture",value=security.encryptData("synthetic protected value"),isProtected=true))
        db.customFieldDao().insert(CustomField(entryId=first.id,title="Visible fixture",value="visible value",sortOrder=1))
        db.passwordHistoryDao().insert(PasswordHistoryEntry(entryId=first.id,password=security.encryptData("synthetic previous password")))
        for ((index,type) in listOf("SSH_KEY","WIFI","API_KEY","SSO","BARCODE").withIndex()) {
            val entry = entries.getValue("Bulk ${index+1}")
            db.passwordEntryDao().updatePasswordEntry(entry.copy(loginType=type,
                sshKeyData=if(type=="SSH_KEY") "{\"privateKey\":\"synthetic-ssh-material\"}" else "",
                wifiMetadata=if(type=="WIFI") "{\"ssid\":\"Synthetic network\",\"securityType\":\"WPA2\"}" else "",
                ssoProvider=if(type=="SSO") "GOOGLE" else ""))
        }
        val archived = entries.getValue("Bulk 6")
        db.passwordEntryDao().updatePasswordEntry(archived.copy(isArchived=true,archivedAt=java.util.Date(1700000000000)))
        val keyVault = AttachmentKeyVault(security)
        for (fixture in attachments) {
            val secure = if(fixture.password) null else db.secureItemDao().getDeviceSyncLocalItems().single { it.title==fixture.parent }
            val blob = storage.writeEncrypted(fixture.bytes.inputStream()); createdBlobs += blob.relativePath
            try { db.attachmentDao().insert(Attachment(parentPasswordId=if(fixture.password) entries.getValue(fixture.parent).id else null,
                parentSecureItemId=secure?.id,source="LOCAL",fileName=fixture.name,mimeType=fixture.mime,sizeBytes=blob.sizeBytes,
                sha256Hex=blob.sha256Hex,wrappedCek=keyVault.wrap(blob.cek),localPath=blob.relativePath,downloadState="DOWNLOADED",
                isDeleted=secure?.isDeleted==true,createdAt=1L,updatedAt=1L)) } finally { blob.cek.fill(0) }
        }
    }

    suspend fun verify(db: PasswordDatabase, security: SecurityManager) {
        val entries = db.passwordEntryDao().getDeviceSyncLocalPasswords().associateBy { it.title }
        val items = db.secureItemDao().getDeviceSyncLocalItems().associateBy { it.title }
        for ((type,payload) in payloads) {
            val item = items.getValue("Fixture $type")
            assertFalse(item.isDeleted)
            val actual = Json.parseToJsonElement(security.decryptDataIfMonicaCiphertext(item.itemData)).jsonObject
            Json.parseToJsonElement(payload).jsonObject.forEach { (field,value) -> assertEquals("$type.$field",value,actual[field]) }
            assertEquals("各种数据 / Unicode 🔐",db.categoryDao().getCategoryById(requireNotNull(item.categoryId))!!.name)
        }
        val first = entries.getValue("Bulk 0")
        assertEquals(items.getValue("Fixture NOTE").id,first.boundNoteId)
        assertEquals("Multiline\n中文 café 🔐",first.notes)
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ",security.decryptDataIfMonicaCiphertext(first.authenticatorKey))
        val fields = db.customFieldDao().getFieldsByEntryIds(listOf(first.id)).associateBy { it.title }
        assertEquals(2,fields.size)
        assertEquals("synthetic protected value",security.decryptDataIfMonicaCiphertext(fields.getValue("Protected fixture").value))
        assertTrue(fields.getValue("Protected fixture").isProtected)
        assertEquals("visible value",fields.getValue("Visible fixture").value)
        assertEquals("synthetic previous password",security.decryptDataIfMonicaCiphertext(db.passwordHistoryDao().getHistoryByEntryIdSync(first.id).single().password))
        for ((index,type) in listOf("SSH_KEY","WIFI","API_KEY","SSO","BARCODE").withIndex()) assertEquals(type,entries.getValue("Bulk ${index+1}").loginType)
        assertTrue(entries.getValue("Bulk 6").isArchived)
        assertEquals(1700000000000L,entries.getValue("Bulk 6").archivedAt!!.time)
        assertEquals("Synthetic network",JSONObject(entries.getValue("Bulk 2").wifiMetadata).getString("ssid"))
        assertEquals("synthetic-ssh-material",JSONObject(entries.getValue("Bulk 1").sshKeyData).getString("privateKey"))
        val keyVault = AttachmentKeyVault(security)
        for ((parent,expected) in attachments.groupBy { it.parent }) {
            val rows = if(expected.first().password) db.attachmentDao().getAllByParent(entries.getValue(parent).id)
                else db.attachmentDao().getAllBySecureItem(items.getValue(parent).id)
            assertEquals(expected.size,rows.size)
            for (fixture in expected) {
                val hash = MessageDigest.getInstance("SHA-256").digest(fixture.bytes).joinToString("") { "%02x".format(it) }
                val row = rows.single { it.fileName==fixture.name && it.sha256Hex==hash }
                assertEquals(fixture.mime,row.mimeType); assertEquals(fixture.bytes.size.toLong(),row.sizeBytes)
                val key = keyVault.unwrap(requireNotNull(row.wrappedCek))
                try { assertArrayEquals(fixture.bytes,storage.openDecryptedStream(requireNotNull(row.localPath),key).use { it.readBytes() }) }
                finally { key.fill(0) }
            }
        }
    }

    suspend fun clean(db: PasswordDatabase) {
        for (entry in db.passwordEntryDao().getDeviceSyncLocalPasswords()) db.attachmentDao().getAllByParent(entry.id).mapNotNullTo(createdBlobs) { it.localPath }
        for (entry in db.secureItemDao().getDeviceSyncLocalItems()) db.attachmentDao().getAllBySecureItem(entry.id).mapNotNullTo(createdBlobs) { it.localPath }
        createdBlobs.forEach { storage.delete(it) }; createdBlobs.clear()
    }
}
