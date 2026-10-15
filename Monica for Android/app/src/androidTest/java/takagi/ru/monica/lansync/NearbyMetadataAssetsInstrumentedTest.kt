package takagi.ru.monica.lansync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.repository.Mdbx2NativeRuntime
import takagi.ru.monica.repository.Mdbx2VaultSessionExecutor
import uniffi.mdbx_ffi.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Real packaged MDBX engine, composite commits and attachment disclosure. */
@RunWith(AndroidJUnit4::class)
class NearbyMetadataAssetsInstrumentedTest {
    private val password = "synthetic-metadata-assets-only"
    private fun descriptor(bytes: ByteArray, identity: String) = JSONObject()
        .put("id", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        .put("attachment_id", identity).put("file_name", "同名.bin").put("media_type", "application/octet-stream").put("size", bytes.size)
        .put("future_asset", "preserve")
    private fun rootData(bytes: ByteArray, identity: String) = JSONObject().put("entry_type", "vault-metadata").put("title", "Metadata")
        .put("folder", "").put("payload", JSONObject().put("tags", JSONArray().put(JSONObject().put("name", "tag-$identity")
            .put("payload", JSONObject().put("kind", "monica-project-tag").put("opaque", "preserve").toString()).put("version", 1))))
        .put("labels", JSONArray().put(JSONObject().put("name", "standalone-$identity").put("payload", "{\"opaque\":42}").put("version", 1)))
        .put("assets", JSONArray().put(descriptor(bytes, identity))).put("future", JSONObject().put("external", "opaque:42"))
    private fun verify(vault: MdbxVault, snapshot: DevicePasswordSnapshot, bytes: ByteArray, identity: String) {
        assertEquals(identity, snapshot.data.getJSONArray("assets").getJSONObject(0).getString("attachment_id"))
        assertEquals("preserve", snapshot.data.getJSONArray("assets").getJSONObject(0).getString("future_asset"))
        assertEquals(bytes.size.toLong(), snapshot.assets.single().size)
        assertArrayEquals(bytes, vault.readAttachmentContent(snapshot.assets.single().nativeId, 64uL * 1024uL * 1024uL))
    }

    @Test fun native_to_room_to_another_native_vault_forwards_real_metadata_files() = runBlocking {
        Mdbx2NativeRuntime.ensureLoaded()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val security = takagi.ru.monica.security.SecurityManager(context)
        val database = takagi.ru.monica.data.PasswordDatabase.getDatabase(context)
        val room = androidx.room.Room.inMemoryDatabaseBuilder(context,takagi.ru.monica.data.PasswordDatabase::class.java).build()
        val steam = androidx.room.Room.inMemoryDatabaseBuilder(context,takagi.ru.monica.steam.data.SteamDatabase::class.java).build()
        val directory = File(context.cacheDir,"metadata-bridge-${UUID.randomUUID()}").apply { mkdirs() }
        val vaultDirectory = File(context.filesDir,"mdbx2").apply { mkdirs() }
        val paths = (0..1).map { File(vaultDirectory,"${UUID.randomUUID()}.mdbx") }
        val ids = mutableListOf<Long>(); val providers = mutableListOf<DevicePasswordSyncProvider>()
        val repository = takagi.ru.monica.repository.Mdbx2Repository(context,database.localMdbxDatabaseDao(),security)
        val bytes = ByteArray(310_031) { (it % 233).toByte() }
        val owner=UUID.randomUUID().toString(); val folderId=UUID.randomUUID().toString()
        val preferenceNames = mutableListOf<String>()
        takagi.ru.monica.security.SessionManager.markUnlocked()
        try {
            paths.forEachIndexed { index,path ->
                val vault=createVaultWithTigaMode(path.absolutePath,password,"metadata-bridge-$index",MdbxTigaMode.SKY)
                try { if(index==0) {
                    DeviceNativeRootMetadata(vault).apply(owner,null,true,false,rootData(bytes,"bridge-root")) { bytes.copyOf() }
                    val nodes=JSONArray().put(JSONObject().put("id",folderId).put("title","Bridge folder").put("parent",JSONObject.NULL))
                    val data=rootData(bytes,"bridge-folder").put("entry_type","folder").put("title","Bridge folder")
                    data.getJSONObject("payload").put("path",nodes)
                    DeviceNativeFolderMetadata(vault,folderId,0).apply(data,false,listOf(MdbxWriteCommand.CreateProject(folderId,"Bridge folder"))) { bytes.copyOf() }
                } } finally { vault.close() }
                ids += database.localMdbxDatabaseDao().insertDatabase(takagi.ru.monica.data.LocalMdbxDatabase(
                    name="Synthetic metadata bridge",filePath=path.absolutePath,workingCopyPath=path.absolutePath,
                    engineType="RUST_MDBX2",tigaMode="SKY",sourceType="LOCAL_INTERNAL",storageLocation="INTERNAL",encryptedPassword=security.encryptData(password)))
            }
            fun provider(db: takagi.ru.monica.data.PasswordDatabase,nativeId: Long?): DevicePasswordSyncProvider {
                val name="metadata-bridge-${UUID.randomUUID()}"; preferenceNames+=name
                val settings=DeviceSyncPreferenceStore(context,security,name).also { it.save(DeviceSyncPreferences(databaseId=nativeId)) }
                return DevicePasswordSyncProvider(context,security,settings,db,File(directory,"$name.replica"),steam).also { providers+=it }
            }
            val sender=provider(database,ids[0]); val receiver=provider(room,null)
            val outgoing=sender.prepare(File(directory,"outgoing"))
            receiver.apply(outgoing.directory,JSONObject().put("pairing",JSONObject().put("content_key",outgoing.contentKey)))
            receiver.pause()
            // Reopen the same receiver replica and encrypted metadata rows.
            val forwarded=receiver.prepare(File(directory,"forwarded"))
            val transfer=DeviceAssetTransfer(forwarded.directory,forwarded.contentKey)
            val metadata=DeviceRoomVaultMetadata(room,security) { JSONObject() }
            val roots=metadata.rootMetadata()
            val root=roots.single { it.data.optJSONArray("assets")?.optJSONObject(0)?.optString("attachment_id")=="bridge-root" }
            val category=room.categoryDao().getDeviceSyncCategories().single()
            val folder=metadata.folder(category)
            assertEquals("bridge-folder",folder.getJSONArray("assets").getJSONObject(0).getString("attachment_id"))
            assertArrayEquals(bytes,transfer.read(root.data.getJSONArray("assets").getJSONObject(0)))
            assertArrayEquals(bytes,transfer.read(folder.getJSONArray("assets").getJSONObject(0)))
            assertNotNull(repository.applyDeviceSyncPassword(ids[1],null,root.globalId,null,false,root.data) { transfer.read(it) })
            val portableFolder=metadata.global("folder:${category.id}")!!
            assertNotNull(repository.applyDeviceSyncPassword(ids[1],null,portableFolder,null,false,folder) { transfer.read(it) })
            val copied=repository.deviceSyncPasswords(ids[1]).filter { it.assets.isNotEmpty() }
            assertEquals(2,copied.size)
            assertEquals(setOf("bridge-root","bridge-folder"),copied.map { it.data.getJSONArray("assets").getJSONObject(0).getString("attachment_id") }.toSet())
            copied.forEach { item -> assertArrayEquals(bytes,repository.readDeviceSyncAsset(item.assets.single())) }
        } finally {
            providers.forEach { it.pause() }; room.close(); steam.close()
            ids.forEach { database.mdbxSyncStateDao().delete(it); database.localMdbxDatabaseDao().deleteDatabaseById(it) }
            paths.forEach { repository.deleteOwnedVaultFile(it) }
            preferenceNames.forEach { context.deleteSharedPreferences(it) }
            takagi.ru.monica.security.SessionManager.markLocked()
            check(directory.canonicalFile.parentFile==context.cacheDir.canonicalFile); directory.deleteRecursively()
        }
    }

    @Test fun root_files_labels_ownership_replacement_trash_purge_and_reopen() = runBlocking {
        Mdbx2NativeRuntime.ensureLoaded()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "metadata-root-${UUID.randomUUID()}").apply { mkdirs() }
        val path = File(directory, "fixture.mdbx")
        var vault = createVaultWithTigaMode(path.absolutePath, password, "metadata-test", MdbxTigaMode.SKY)
        val a = UUID.randomUUID().toString(); val b = UUID.randomUUID().toString()
        val first = ByteArray(280_017) { (it % 251).toByte() }; val replacement = ByteArray(180_003) { (it % 241).toByte() }
        try {
            var roots = DeviceNativeRootMetadata(vault, 1)
            assertNotNull(roots.apply(a, null, true, false, rootData(first, "stable-a")) { first.copyOf() })
            assertNotNull(roots.apply(b, null, true, false, rootData(first, "stable-b")) { first.copyOf() })
            fun current(id: String) = roots.read().single { it.globalId == id }
            verify(vault, current(a), first, "stable-a"); verify(vault, current(b), first, "stable-b")
            val bGuard = current(b).guard
            val root = Mdbx2VaultSessionExecutor.rootProjectId(vault.info().vaultId)
            val marker = vault.listObjectLabels(root).single { DeviceNativeMetadataContent.kind(it) == DeviceNativeMetadataContent.ROOT_KIND && JSONObject(it.payloadJson).optString("owner") == a }
            val extra = JSONObject(marker.payloadJson).put("future_marker", JSONObject().put("value", 99))
            vault.executeWriteOperation(UUID.randomUUID().toString(), "fixture-extension", listOf(MdbxWriteCommand.UpdateObjectLabel(marker.labelId,marker.name,extra.toString(),1u)))
            assertNotNull(roots.apply(a, current(a).guard, false, false, rootData(replacement, "stable-a")) { replacement.copyOf() })
            assertEquals(bGuard, current(b).guard)
            assertEquals(99, JSONObject(vault.listObjectLabels(root).single { it.labelId == marker.labelId }.payloadJson).getJSONObject("future_marker").getInt("value"))
            val omitted = JSONObject(current(a).data.toString()).apply { remove("assets"); remove("labels") }
            assertNotNull(roots.apply(a,current(a).guard,false,false,omitted) { error("Omitted assets must be retained") })
            verify(vault,current(a),replacement,"stable-a")
            val trashed = JSONObject(current(a).data.toString()).put("trashed", true)
            assertNotNull(roots.apply(a,current(a).guard,false,true,trashed) { error("Existing files must be reused") })
            assertTrue(current(a).deleted); verify(vault,current(a),replacement,"stable-a")
            vault.close(); vault = openVault(path.absolutePath,password,"metadata-reopen"); roots = DeviceNativeRootMetadata(vault,1)
            verify(vault,current(a),replacement,"stable-a")
            assertEquals(42, JSONObject(current(a).data.getJSONArray("labels").getJSONObject(0).getString("payload")).getInt("opaque"))
            assertNotNull(roots.apply(a,current(a).guard,false,false,JSONObject(current(a).data.toString()).put("trashed",false)) { error("Reuse bytes") })
            assertFalse(current(a).deleted)
            val old = current(a); val checkpoint = vault.incrementalSyncCheckpoint()
            val failed = runCatching { roots.apply(a,old.guard,false,false,rootData(first,"stable-a")) { byteArrayOf(1) } }.exceptionOrNull()
            assertTrue(failed is LanSyncException); assertEquals(checkpoint,vault.incrementalSyncCheckpoint()); verify(vault,current(a),replacement,"stable-a")
            assertNotNull(roots.apply(a,current(a).guard,false,true,JSONObject()) { error("Purge does not read files") })
            assertTrue(current(a).deleted); assertEquals(0,current(a).data.length()); assertTrue(current(a).assets.isEmpty())
            verify(vault,current(b),first,"stable-b")
            assertEquals(bGuard,current(b).guard)
        } finally { vault.close(); check(directory.canonicalFile.parentFile == context.cacheDir.canonicalFile); directory.deleteRecursively() }
    }

    @Test fun folder_metadata_round_trip_preserves_unknown_fields_and_rejects_unsupported_trash_before_write() = runBlocking {
        Mdbx2NativeRuntime.ensureLoaded()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "metadata-folder-${UUID.randomUUID()}").apply { mkdirs() }
        val path = File(directory,"fixture.mdbx")
        var vault = createVaultWithTigaMode(path.absolutePath,password,"folder-test",MdbxTigaMode.SKY)
        val id = UUID.randomUUID().toString(); val bytes = ByteArray(290_029) { (it % 237).toByte() }
        val nodes = JSONArray().put(JSONObject().put("id",id).put("title","Folder").put("parent",JSONObject.NULL).put("future_node",87))
        val data = rootData(bytes,"folder-file").put("entry_type","folder").put("title","Folder")
        data.getJSONObject("payload").put("path",nodes).put("unknown_payload", "keep")
        try {
            var folder = DeviceNativeFolderMetadata(vault,id,1)
            folder.apply(data,false,listOf(MdbxWriteCommand.CreateProject(id,"Folder"))) { bytes.copyOf() }
            verify(vault,folder.read(nodes),bytes,"folder-file")
            val withoutUnknownNode = JSONArray().put(JSONObject().put("id",id).put("title","Folder").put("parent",JSONObject.NULL))
            assertEquals(87,folder.read(withoutUnknownNode).data.getJSONObject("payload").getJSONArray("path").getJSONObject(0).getInt("future_node"))
            val checkpoint = vault.incrementalSyncCheckpoint()
            val failure = runCatching { folder.apply(JSONObject(data.toString()).put("trashed",true),true,emptyList()) { bytes.copyOf() } }.exceptionOrNull()
            assertTrue(failure is LanSyncException); assertEquals(checkpoint,vault.incrementalSyncCheckpoint())
            vault.close(); vault=openVault(path.absolutePath,password,"folder-reopen"); folder=DeviceNativeFolderMetadata(vault,id,1)
            verify(vault,folder.read(nodes),bytes,"folder-file")
            assertEquals("opaque:42",folder.read(nodes).data.getJSONObject("future").getString("external"))
            folder.apply(JSONObject(data.toString()).put("assets",JSONArray()),false,emptyList()) { error("No files to create") }
            assertTrue(folder.read(nodes).assets.isEmpty())
            val child = UUID.randomUUID().toString()
            vault.executeWriteOperation(UUID.randomUUID().toString(),"fixture-child",listOf(MdbxWriteCommand.CreateProjectWithParent(child,"Child",id)))
            val beforeDelete = vault.incrementalSyncCheckpoint()
            val beforeData = folder.read(nodes).data.toString()
            assertNotNull(runCatching { folder.apply(JSONObject(),true,emptyList()) { error("No files to create") } }.exceptionOrNull())
            assertEquals(beforeDelete,vault.incrementalSyncCheckpoint())
            assertEquals(beforeData,folder.read(nodes).data.toString())
            vault.executeWriteOperation(UUID.randomUUID().toString(),"fixture-remove-child",listOf(MdbxWriteCommand.DeleteProject(child)))
            folder.apply(JSONObject(),true,emptyList()) { error("No files to create") }
            assertTrue(folder.read(nodes).deleted); assertEquals(0,folder.read(nodes).data.length())
        } finally { vault.close(); check(directory.canonicalFile.parentFile == context.cacheDir.canonicalFile); directory.deleteRecursively() }
    }
}
