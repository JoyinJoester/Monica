package takagi.ru.monica.lansync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.BeforeClass
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Resetter
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import takagi.ru.monica.data.*
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.security.SecurePreferencesStore
import java.io.File
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Uses real Room and the shared Rust JNI replica, with a deterministic OS-key substitute. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, shadows = [DeviceVaultProviderTest.SecureStoreShadow::class, DeviceVaultProviderTest.SecurityShadow::class])
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceVaultProviderTest {
    companion object {
        @JvmStatic @BeforeClass fun loadNative() {
            val name = System.mapLibraryName("monica_rust_jni")
            val path = listOf(File("../rust-jni/target/release/$name"),File("rust-jni/target/release/$name")).firstOrNull { it.isFile }
            org.junit.Assume.assumeTrue("Build the host JNI to run the Room/native integration fixture",path != null)
            requireNotNull(path)
            require(path.isFile) { "Build the host JNI for the provider integration test" }
            System.setProperty("monica.lan.native.path",path.absolutePath)
        }
    }
    private val databases = mutableListOf<PasswordDatabase>()
    @Before fun dispatchers() { Dispatchers.setMain(Dispatchers.Unconfined) }
    @After fun close() { databases.forEach { it.close() }; SessionManager.markLocked(); Dispatchers.resetMain() }

    private fun vault(): Pair<PasswordDatabase, DevicePasswordSyncProvider> {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, PasswordDatabase::class.java).allowMainThreadQueries().build()
        databases += database
        val security = SecurityManager(context)
        return database to DevicePasswordSyncProvider(context,security,DeviceSyncPreferenceStore(context,security),database,
            File(context.filesDir,"fixture-${UUID.randomUUID()}.replica"))
    }

    @Test fun standalone_items_history_links_and_trash_survive_real_native_exchange() = runBlocking { withTimeout(60000) {
        SessionManager.markUnlocked()
        val (a, first) = vault(); val (b, second) = vault()
        try {
            val category = a.categoryDao().insert(Category(name="Synthetic folder"))
            val data = """{"secret":"GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ","otpType":"HOTP","counter":17,"pin":"synthetic","steamIdentitySecret":"synthetic-recovery"}"""
            val note = a.secureItemDao().insertItem(SecureItem(itemType=ItemType.NOTE,title="Fixture note",itemData="{\"content\":\"synthetic\"}",categoryId=category))
            val password = a.passwordEntryDao().insertPasswordEntry(PasswordEntry(title="Fixture login",website="example.test",username="fixture",password="synthetic-password",categoryId=category,boundNoteId=note))
            a.passwordHistoryDao().insert(PasswordHistoryEntry(entryId=password,password="synthetic-old"))
            a.passwordEntryDao().insertPasswordEntry(PasswordEntry(title="Trash login",website="",username="",password="synthetic-trash",categoryId=category,isDeleted=true))
            val context = RuntimeEnvironment.getApplication()
            val material = java.security.KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
            val portable = takagi.ru.monica.credentialexchange.CxfPasskeyMaterial.decode(material.private.encoded,-7)!!
            a.passkeyDao().insert(PasskeyEntry(credentialId=UUID.randomUUID().toString(),rpId="example.test",rpName="Fixture passkey",
                userId=Base64.getEncoder().encodeToString("fixture".toByteArray()),userName="fixture",userDisplayName="Fixture",publicKey=portable.cosePublicKey,
                privateKeyAlias=Base64.getEncoder().encodeToString(material.private.encoded),boundPasswordId=password,categoryId=category))
            val binary = ByteArray(700017).also { java.security.SecureRandom().nextBytes(it) }
            val storage = takagi.ru.monica.attachments.storage.AttachmentStorage(context)
            val keyVault = takagi.ru.monica.attachments.storage.AttachmentKeyVault(SecurityManager(context))
            val blob = storage.writeEncrypted(binary.inputStream())
            for(index in 0..1) a.attachmentDao().insert(takagi.ru.monica.attachments.model.Attachment(parentPasswordId=password,source="LOCAL",
                fileName="duplicate.bin",mimeType="application/octet-stream",sizeBytes=blob.sizeBytes,sha256Hex=blob.sha256Hex,
                wrappedCek=keyVault.wrap(blob.cek),localPath=blob.relativePath,downloadState="DOWNLOADED",createdAt=1L,updatedAt=1L))
            blob.cek.fill(0)
            val iconBytes = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5n8AAAAASUVORK5CYII=")
            File(takagi.ru.monica.ui.icons.PasswordCustomIconStore.getIconDir(context),"fixture-icon.png").writeBytes(iconBytes)
            a.passwordEntryDao().updatePasswordEntry(a.passwordEntryDao().getPasswordEntryById(password)!!.copy(
                notes=blob.relativePath,customIconType="UPLOADED",customIconValue="fixture-icon.png",passkeyBindings="[{\"credentialId\":\"synthetic-badge\"}]"))
            for(type in listOf(ItemType.TOTP,ItemType.BANK_CARD,ItemType.DOCUMENT,ItemType.BILLING_ADDRESS,ItemType.PAYMENT_ACCOUNT))
                a.secureItemDao().insertItem(SecureItem(itemType=type,title=type.name,itemData=data,categoryId=category))
            val directory = File(RuntimeEnvironment.getApplication().filesDir,"out-${UUID.randomUUID()}")
            val outgoing = first.prepare(directory)
            val peer = JSONObject().put("pairing",JSONObject().put("content_key",outgoing.contentKey))
            val progress = mutableListOf<DeviceSyncProgress>(); second.onProgress = { progress += it }
            assertTrue(second.apply(directory,peer) > 0)
            val received = b.passwordEntryDao().getDeviceSyncLocalPasswords().single { !it.isDeleted }
            assertEquals("synthetic-trash",b.passwordEntryDao().getDeviceSyncLocalPasswords().single { it.isDeleted }.password)
            assertEquals("synthetic-password",received.password)
            assertEquals(blob.relativePath,received.notes)
            assertEquals("[{\"credentialId\":\"synthetic-badge\"}]",received.passkeyBindings)
            assertArrayEquals(iconBytes,takagi.ru.monica.ui.icons.PasswordCustomIconStore.resolveIconFile(context,received.customIconValue)!!.readBytes())
            assertNotNull(received.boundNoteId)
            assertEquals("Fixture note",b.secureItemDao().getItemById(received.boundNoteId!!)!!.title)
            assertEquals("synthetic-old",b.passwordHistoryDao().getHistoryByEntryIdSync(received.id).single().password)
            val items = b.secureItemDao().getDeviceSyncLocalItems()
            assertEquals(6,items.size)
            assertEquals(17,JSONObject(items.single { it.itemType==ItemType.TOTP }.itemData).getInt("counter"))
            val credential = b.passkeyDao().getAllPasskeysSync().single()
            assertEquals(received.id,credential.boundPasswordId)
            assertEquals(received.categoryId,credential.categoryId)
            assertTrue(takagi.ru.monica.passkey.PasskeyPrivateKeyStore.isProtectedReference(credential.privateKeyAlias))
            val imported = java.security.KeyFactory.getInstance("EC").generatePrivate(java.security.spec.PKCS8EncodedKeySpec(Base64.getDecoder().decode(
                takagi.ru.monica.passkey.PasskeyPrivateKeyStore.resolve(context,credential.privateKeyAlias))))
            val challenge = "synthetic-challenge".toByteArray()
            val signature = java.security.Signature.getInstance("SHA256withECDSA").apply { initSign(imported); update(challenge) }.sign()
            assertTrue(java.security.Signature.getInstance("SHA256withECDSA").apply { initVerify(material.public); update(challenge) }.verify(signature))
            assertEquals(2,b.attachmentDao().getAllByParent(received.id).size)
            val attachment = b.attachmentDao().getAllByParent(received.id).first()
            val cek = keyVault.unwrap(attachment.wrappedCek!!)
            try { assertArrayEquals(binary,storage.openDecryptedStream(attachment.localPath!!,cek).use { it.readBytes() }) } finally { cek.fill(0) }
            assertTrue(progress.any { it.stage=="applying" && it.completed > 0 })
            val revision = second.revision()
            assertEquals(revision,second.revision())
            b.passkeyDao().update(credential.copy(notes="Updated on receiver",boundPasswordId=null,categoryId=null))
            b.passwordEntryDao().updatePasswordEntry(received.copy(boundNoteId=null))
            val back = second.prepare(File(context.filesDir,"back-${UUID.randomUUID()}"))
            first.apply(back.directory,JSONObject().put("pairing",JSONObject().put("content_key",back.contentKey)))
            assertEquals("Updated on receiver",a.passkeyDao().getAllPasskeysSync().single().notes)
            assertNull(a.passkeyDao().getAllPasskeysSync().single().boundPasswordId)
            assertNull(a.passwordEntryDao().getPasswordEntryById(password)!!.boundNoteId)
            assertEquals(2,a.attachmentDao().getAllByParent(password).size)
        } finally { first.pause(); second.pause() }
    } }

    @Test fun progress_keeps_the_complete_total_across_multiple_native_batches() = runBlocking { withTimeout(60000) {
        SessionManager.markUnlocked(); val (a,first) = vault(); val (_,second) = vault()
        try {
            for(index in 0 until 270) a.secureItemDao().insertItem(SecureItem(itemType=ItemType.NOTE,title="Batch $index",itemData="{\"content\":\"synthetic $index\"}"))
            val outgoing = first.prepare(File(RuntimeEnvironment.getApplication().filesDir,"batch-${UUID.randomUUID()}"))
            val progress = mutableListOf<DeviceSyncProgress>(); second.onProgress = { if(it.stage == "applying") progress += it }
            assertEquals(270,second.apply(outgoing.directory,JSONObject().put("pairing",JSONObject().put("content_key",outgoing.contentKey))))
            assertTrue(progress.all { it.total == 270L })
            assertTrue(progress.any { it.completed == 256L && it.completed < it.total })
            assertEquals(270L,progress.last().completed)
        } finally { first.pause(); second.pause() }
    } }

    @Implements(value=SecurePreferencesStore::class,isInAndroidSdk=false)
    class SecureStoreShadow {
        companion object {
            @JvmStatic @Implementation fun open(context: Context,name: String,alias: String): SecurePreferencesStore.Opened =
                SecurePreferencesStore.Opened(ReflectionHelpers.callConstructor(MasterKey::class.java,
                    ClassParameter.from(String::class.java,"fixture-master"), ClassParameter.from(Any::class.java,null)),
                    context.getSharedPreferences("fixture-$name",Context.MODE_PRIVATE))
        }
    }
    @Implements(value=SecurityManager::class,isInAndroidSdk=false)
    class SecurityShadow {
        companion object { private val protected = mutableMapOf<String,String>(); @JvmStatic @Resetter fun reset() { protected.clear() } }
        @Implementation fun encryptData(value: String): String = seal(value)
        @Implementation fun encryptDataLegacyCompat(value: String): String = value
        @Implementation fun decryptData(value: String): String = if(value.startsWith("fixture|")) open(value) else value
        @Implementation fun decryptDataIfMonicaCiphertext(value: String): String = decryptData(value)
        @Implementation fun putProtectedString(key: String,value: String?) { if(value==null) protected.remove(key) else protected[key]=seal(value) }
        @Implementation fun getProtectedString(key: String): String? = protected[key]?.let(::open)
        @Implementation fun removeProtectedString(key: String) { protected.remove(key) }
        private fun seal(value: String): String {
            val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
            val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(ByteArray(32){9},"AES"),GCMParameterSpec(128,nonce))
            return "fixture|"+Base64.getEncoder().encodeToString(nonce+cipher.doFinal(value.toByteArray()))
        }
        private fun open(value: String): String {
            val bytes=Base64.getDecoder().decode(value.removePrefix("fixture|")); val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(ByteArray(32){9},"AES"),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            return String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)))
        }
    }
}
