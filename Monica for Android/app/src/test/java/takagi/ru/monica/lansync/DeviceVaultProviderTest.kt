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

    private fun vault(synchronousInvalidation: Boolean = false): Pair<PasswordDatabase, DevicePasswordSyncProvider> {
        val context = RuntimeEnvironment.getApplication()
        val builder = Room.inMemoryDatabaseBuilder(context, PasswordDatabase::class.java).allowMainThreadQueries()
        if (synchronousInvalidation) builder.setQueryExecutor { it.run() }
        val database = builder.build()
        databases += database
        val security = SecurityManager(context)
        return database to DevicePasswordSyncProvider(context,security,DeviceSyncPreferenceStore(context,security),database,
            File(context.filesDir,"fixture-${UUID.randomUUID()}.replica"))
    }

    @Test fun local_passkey_with_stale_mdbx_folder_does_not_abort_preparation() = runBlocking { withTimeout(60000) {
        SessionManager.markUnlocked()
        val (database, provider) = vault()
        val (receivedDatabase, receiver) = vault()
        try {
            val context = RuntimeEnvironment.getApplication()
            val folderId = database.categoryDao().insert(Category(name = "Foreign native folder", mdbxDatabaseId = 42L))
            val pair = java.security.KeyPairGenerator.getInstance("EC").apply {
                initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
            }.generateKeyPair()
            val entry = takagi.ru.monica.passkey.PasskeyPrivateKeyStore.protectPasskey(context, PasskeyEntry(
                credentialId = UUID.randomUUID().toString(), rpId = "example.test", rpName = "Legacy local folder",
                userId = "Zml4dHVyZQ==", userName = "fixture", userDisplayName = "Fixture", categoryId = folderId,
                publicKey = Base64.getEncoder().encodeToString(pair.public.encoded),
                privateKeyAlias = Base64.getEncoder().encodeToString(pair.private.encoded)))
            database.passkeyDao().insert(entry)
            val outgoing = provider.prepare(File(context.filesDir, "stale-folder-${UUID.randomUUID()}"))
            receiver.apply(outgoing.directory, JSONObject().put("pairing", JSONObject().put("content_key", outgoing.contentKey)))
            assertNull(receivedDatabase.passkeyDao().getAllPasskeysSync().single().categoryId)
            assertEquals(folderId, database.passkeyDao().getAllPasskeysSync().single().categoryId)
        } finally { provider.pause(); receiver.pause() }
    } }

    @Test fun locally_created_spki_passkey_can_prepare() = runBlocking { withTimeout(60000) {
        SessionManager.markUnlocked()
        val (database, provider) = vault()
        try {
            val context = RuntimeEnvironment.getApplication()
            val pair = java.security.KeyPairGenerator.getInstance("EC").apply {
                initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
            }.generateKeyPair()
            val entry = takagi.ru.monica.passkey.PasskeyPrivateKeyStore.protectPasskey(context, PasskeyEntry(
                credentialId = UUID.randomUUID().toString(), rpId = "example.test", rpName = "Local registration",
                userId = "Zml4dHVyZQ==", userName = "fixture", userDisplayName = "Fixture",
                publicKey = Base64.getEncoder().encodeToString(pair.public.encoded),
                privateKeyAlias = Base64.getEncoder().encodeToString(pair.private.encoded)))
            assertTrue(takagi.ru.monica.passkey.PasskeyPrivateKeyStore.isProtectedReference(entry.privateKeyAlias))
            database.passkeyDao().insert(entry)
            val outgoing = provider.prepare(File(context.filesDir, "spki-${UUID.randomUUID()}"))
            assertTrue(outgoing.contentKey.isNotEmpty())
            assertTrue(outgoing.passkeyIssues.isEmpty())
            assertEquals(entry.publicKey, database.passkeyDao().getAllPasskeysSync().single().publicKey)
        } finally { provider.pause() }
    } }

    @Test fun legacy_passkey_without_cached_public_key_can_prepare_and_sign_on_receiver() = runBlocking { withTimeout(60000) {
        SessionManager.markUnlocked()
        val (database, provider) = vault()
        val (receivedDatabase, receiver) = vault()
        try {
            val context = RuntimeEnvironment.getApplication()
            val pair = java.security.KeyPairGenerator.getInstance("EC").apply {
                initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
            }.generateKeyPair()
            val entry = takagi.ru.monica.passkey.PasskeyPrivateKeyStore.protectPasskey(context, PasskeyEntry(
                credentialId = UUID.randomUUID().toString(), rpId = "example.test", rpName = "Legacy registration",
                userId = "Zml4dHVyZQ==", userName = "fixture", userDisplayName = "Fixture", publicKey = "",
                privateKeyAlias = Base64.getEncoder().encodeToString(pair.private.encoded)))
            database.passkeyDao().insert(entry)
            val outgoing = provider.prepare(File(context.filesDir, "legacy-${UUID.randomUUID()}"))
            receiver.apply(outgoing.directory, JSONObject().put("pairing", JSONObject().put("content_key", outgoing.contentKey)))
            val received = receivedDatabase.passkeyDao().getAllPasskeysSync().single()
            assertTrue(received.publicKey.isNotBlank())
            assertEquals("", database.passkeyDao().getAllPasskeysSync().single().publicKey)
            val privateKey = java.security.KeyFactory.getInstance("EC").generatePrivate(java.security.spec.PKCS8EncodedKeySpec(
                Base64.getDecoder().decode(takagi.ru.monica.passkey.PasskeyPrivateKeyStore.resolve(context, received.privateKeyAlias))))
            val challenge = "synthetic legacy passkey".toByteArray()
            val signature = java.security.Signature.getInstance("SHA256withECDSA").apply { initSign(privateKey); update(challenge) }.sign()
            assertTrue(java.security.Signature.getInstance("SHA256withECDSA").apply { initVerify(pair.public); update(challenge) }.verify(signature))
        } finally { provider.pause(); receiver.pause() }
    } }

    @Test fun mismatched_local_passkey_reports_record_and_reason() = runBlocking { withTimeout(60000) {
        SessionManager.markUnlocked()
        val (database, provider) = vault()
        try {
            val context = RuntimeEnvironment.getApplication()
            val generator = java.security.KeyPairGenerator.getInstance("EC").apply {
                initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
            }
            val original = generator.generateKeyPair()
            val other = generator.generateKeyPair()
            database.passkeyDao().insert(PasskeyEntry(credentialId = UUID.randomUUID().toString(),
                rpId = "private.example.test", rpName = "Private account title", userId = "Zml4dHVyZQ==",
                userName = "private-user", userDisplayName = "Private user", publicKey = Base64.getEncoder().encodeToString(other.public.encoded),
                privateKeyAlias = Base64.getEncoder().encodeToString(original.private.encoded)))
            val id = database.passkeyDao().getAllPasskeysSync().single().id
            val failure = try {
                provider.prepare(File(context.filesDir, "mismatch-${UUID.randomUUID()}"))
                fail("Mismatched keys must remain rejected")
                error("unreachable")
            } catch (failure: LanSyncException) { failure }
            assertEquals("invalid_passkey_material", failure.code)
            assertEquals("passkey_room_${id}_public_key_mismatch", failure.operation)
            assertFalse(failure.message.orEmpty().contains("private.example.test"))
            assertFalse(failure.message.orEmpty().contains("private-user"))
        } finally { provider.pause() }
    } }

    @Test fun copied_protected_reference_reports_missing_key_without_replacing_it() = runBlocking { withTimeout(60000) {
        SessionManager.markUnlocked()
        val (database, provider) = vault()
        try {
            val context = RuntimeEnvironment.getApplication()
            val pair = java.security.KeyPairGenerator.getInstance("EC").apply {
                initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
            }.generateKeyPair()
            val entry = takagi.ru.monica.passkey.PasskeyPrivateKeyStore.protectPasskey(context, PasskeyEntry(
                credentialId = UUID.randomUUID().toString(), rpId = "example.test", rpName = "Copied reference",
                userId = "Zml4dHVyZQ==", userName = "fixture", userDisplayName = "Fixture",
                publicKey = Base64.getEncoder().encodeToString(pair.public.encoded),
                privateKeyAlias = Base64.getEncoder().encodeToString(pair.private.encoded)))
            database.passkeyDao().insert(entry)
            val id = database.passkeyDao().getAllPasskeysSync().single().id
            SecurityManager(context).removeProtectedString(entry.privateKeyAlias.substringAfter(':'))
            val failure = try {
                provider.prepare(File(context.filesDir, "copied-${UUID.randomUUID()}"))
                fail("A missing protected key must not be claimed as synchronized")
                error("unreachable")
            } catch (failure: LanSyncException) { failure }
            assertEquals("passkey_key_unavailable", failure.code)
            assertEquals("passkey_room_${id}_protected_key_missing", failure.operation)
            assertEquals(entry.privateKeyAlias, database.passkeyDao().getAllPasskeysSync().single().privateKeyAlias)
            SecurityManager(context).putProtectedString(entry.privateKeyAlias.substringAfter(':'), "synthetic-invalid-key")
            val corrupt = try {
                provider.prepare(File(context.filesDir, "corrupt-${UUID.randomUUID()}"))
                fail("Invalid protected material must not become a successful reference")
                error("unreachable")
            } catch (failure: LanSyncException) { failure }
            assertEquals("invalid_passkey_material", corrupt.code)
            assertEquals("passkey_room_${id}_protected_key_invalid", corrupt.operation)
            assertEquals(entry.privateKeyAlias, database.passkeyDao().getAllPasskeysSync().single().privateKeyAlias)
        } finally { provider.pause() }
    } }

    @Test fun reference_update_in_cose_keeps_the_local_spki_private_key() = runBlocking { withTimeout(60000) {
        SessionManager.markUnlocked()
        val (a, first) = vault(); val (b, second) = vault()
        try {
            val context = RuntimeEnvironment.getApplication()
            val pair = java.security.KeyPairGenerator.getInstance("EC").apply {
                initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
            }.generateKeyPair()
            val original = takagi.ru.monica.passkey.PasskeyPrivateKeyStore.protectPasskey(context, PasskeyEntry(
                credentialId = UUID.randomUUID().toString(), rpId = "example.test", rpName = "Original SPKI key",
                userId = "Zml4dHVyZQ==", userName = "fixture", userDisplayName = "Fixture",
                publicKey = Base64.getEncoder().encodeToString(pair.public.encoded),
                privateKeyAlias = Base64.getEncoder().encodeToString(pair.private.encoded)))
            a.passkeyDao().insert(original)
            val outgoing = first.prepare(File(context.filesDir, "reference-first-${UUID.randomUUID()}"))
            second.apply(outgoing.directory, JSONObject().put("pairing", JSONObject().put("content_key", outgoing.contentKey)))
            val credential = b.passkeyDao().getAllPasskeysSync().single()
            val cose = requireNotNull(takagi.ru.monica.credentialexchange.CxfPasskeyMaterial.decode(pair.private.encoded, -7)).cosePublicKey
            b.passkeyDao().update(credential.copy(publicKey = cose, privateKeyAlias = "", notes = "Reference metadata update", isBackedUp = false))
            val back = second.prepare(File(context.filesDir, "reference-back-${UUID.randomUUID()}"))
            first.apply(back.directory, JSONObject().put("pairing", JSONObject().put("content_key", back.contentKey)))
            val updated = a.passkeyDao().getAllPasskeysSync().single()
            assertEquals("Reference metadata update", updated.notes)
            assertEquals(original.privateKeyAlias, updated.privateKeyAlias)
            assertEquals(original.syncStatus, updated.syncStatus)
            assertEquals(Base64.getEncoder().encodeToString(pair.private.encoded),
                takagi.ru.monica.passkey.PasskeyPrivateKeyStore.resolve(context, updated.privateKeyAlias))
        } finally { first.pause(); second.pause() }
    } }

    @Test fun external_passkey_copy_does_not_conflict_and_keeps_its_key_after_remote_deletion() = runBlocking { withTimeout(60000) {
        SessionManager.markUnlocked()
        val (a, first) = vault(synchronousInvalidation = true); val (b, second) = vault(synchronousInvalidation = true)
        try {
            val context = RuntimeEnvironment.getApplication()
            val pair = java.security.KeyPairGenerator.getInstance("EC").apply {
                initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
            }.generateKeyPair()
            val privateKey = Base64.getEncoder().encodeToString(pair.private.encoded)
            val original = takagi.ru.monica.passkey.PasskeyPrivateKeyStore.protectPasskey(context, PasskeyEntry(
                credentialId = UUID.randomUUID().toString(), rpId = "example.test", rpName = "Shared credential",
                userId = "Zml4dHVyZQ==", userName = "fixture", userDisplayName = "Fixture",
                publicKey = Base64.getEncoder().encodeToString(pair.public.encoded), privateKeyAlias = privateKey))
            a.passkeyDao().insert(original)
            b.passkeyDao().insert(original.copy(keepassDatabaseId = 42, notes = "External copy"))
            suspend fun sync() {
                val outgoing = first.prepare(File(context.filesDir, "copy-sync-${UUID.randomUUID()}"))
                second.apply(outgoing.directory, JSONObject().put("pairing", JSONObject().put("content_key", outgoing.contentKey)))
            }
            sync()
            assertEquals(2, b.passkeyDao().getAllPasskeysSync().size)
            val source = a.passkeyDao().getAllPasskeysSync().single()
            a.passkeyDao().update(source.copy(notes = "Updated local credential"))
            sync()
            assertEquals("Updated local credential", b.passkeyDao().getAllPasskeysSync().single { it.keepassDatabaseId == null }.notes)
            assertEquals("External copy", b.passkeyDao().getAllPasskeysSync().single { it.keepassDatabaseId == 42L }.notes)
            a.passkeyDao().deleteByRecordId(source.id)
            sync()
            val remaining = b.passkeyDao().getAllPasskeysSync().single()
            assertEquals(42L, requireNotNull(remaining.keepassDatabaseId))
            assertEquals(privateKey, takagi.ru.monica.passkey.PasskeyPrivateKeyStore.resolve(context, remaining.privateKeyAlias))
        } finally { first.pause(); second.pause() }
    } }

    @Test fun coordinator_blocks_first_send_for_restricted_room_keys_but_receive_only_skips_local_export() = runBlocking { withTimeout(60000) {
        SessionManager.markUnlocked()
        val (database, provider) = vault(synchronousInvalidation = true)
        val context = RuntimeEnvironment.getApplication()
        val coordinator = ReflectionHelpers.callConstructor(DeviceSyncCoordinator::class.java,
            ClassParameter.from(Context::class.java, context))
        ReflectionHelpers.setField(coordinator, "dataProvider", lazyOf(provider))
        val settings = DeviceSyncPreferences(peers = mutableListOf(SavedSyncPeer("peer", "Fixture", "pair", "unused")))
        ReflectionHelpers.setField(coordinator, "preferences", settings)
        suspend fun invoke(name: String): Any? = kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn { continuation ->
            val method = DeviceSyncCoordinator::class.java.getDeclaredMethod(name, kotlin.coroutines.Continuation::class.java)
            method.isAccessible = true
            method.invoke(coordinator, continuation)
        }
        try {
            val pair = java.security.KeyPairGenerator.getInstance("EC").apply {
                initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
            }.generateKeyPair()
            val entry = takagi.ru.monica.passkey.PasskeyPrivateKeyStore.protectPasskey(context, PasskeyEntry(
                credentialId = UUID.randomUUID().toString(), rpId = "example.test", rpName = "Local registration",
                userId = "Zml4dHVyZQ==", userName = "fixture", userDisplayName = "Fixture", signCount = 7,
                publicKey = Base64.getEncoder().encodeToString(pair.public.encoded),
                privateKeyAlias = Base64.getEncoder().encodeToString(pair.private.encoded)))
            database.passkeyDao().insert(entry)
            database.passkeyDao().insert(entry.copy(credentialId = "external", keepassDatabaseId = 9, backupEligible = false))
            invoke("maintainConnections")
            val consent = ReflectionHelpers.getField<DevicePasskeySyncConsent>(coordinator, "passkeyConsent")
            val review = requireNotNull(consent.pending)
            assertEquals(1, review.issues.size) // External vault copies stay outside nearby sync.
            assertEquals(takagi.ru.monica.passkey.PasskeyPortability.COUNTER_HISTORY, review.issues.single().portability)
            assertTrue(ReflectionHelpers.getField<Map<*, *>>(coordinator, "transfers").isEmpty())
            assertEquals("passkey_review", ReflectionHelpers.getField<String>(coordinator, "phase"))
            settings.mode = DeviceSyncMode.RECEIVE_ONLY
            val receiving = invoke("currentSnapshot") as DevicePasswordExport
            assertTrue(receiving.passkeyIssues.isEmpty())
            assertEquals("", receiving.contentKey)
            assertEquals(7L, database.passkeyDao().getAllPasskeysSync().first { it.keepassDatabaseId == null }.signCount)
        } finally {
            provider.pause()
            val root = ReflectionHelpers.getField<File>(coordinator, "root")
            assertEquals(File(context.cacheDir, "device-sync").canonicalFile, root.canonicalFile.parentFile)
            root.deleteRecursively()
        }
    } }

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
            // Registration stores X.509 SPKI, whereas imported credentials can store COSE.
            a.passkeyDao().insert(PasskeyEntry(credentialId=UUID.randomUUID().toString(),rpId="example.test",rpName="Fixture passkey",
                userId=Base64.getEncoder().encodeToString("fixture".toByteArray()),userName="fixture",userDisplayName="Fixture",publicKey=Base64.getEncoder().encodeToString(material.public.encoded),
                privateKeyAlias=Base64.getEncoder().encodeToString(material.private.encoded),boundPasswordId=password,categoryId=category,
                backupEligible=false,backupState=false))
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
            assertEquals(false, credential.backupEligible)
            assertEquals(false, credential.backupState)
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
