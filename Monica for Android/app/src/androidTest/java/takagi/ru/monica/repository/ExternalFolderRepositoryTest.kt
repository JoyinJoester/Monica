package takagi.ru.monica.repository

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.data.*
import takagi.ru.monica.keepass.*
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.viewmodel.LocalKeePassViewModel

class ExternalFolderRepositoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun keepassUsesUuidForDuplicateNamesAndHonorsReadOnly() = runBlocking {
        val room = Room.inMemoryDatabaseBuilder(context, PasswordDatabase::class.java).build()
        val dao = room.localKeePassDatabaseDao()
        val security = SecurityManager(context)
        val model = LocalKeePassViewModel(context.applicationContext as Application, dao, security)
        val service = ExternalFolderRepository(context, room)
        var ownedFile: File? = null
        try {
            model.createDatabase("folder_actions_${System.nanoTime()}", "Synthetic123!", KeePassStorageLocation.INTERNAL,
                creationOptions = KeePassDatabaseCreationOptions(kdfAlgorithm = KeePassKdfAlgorithm.AES_KDF, transformRounds = 1))
            val result = withTimeout(20_000) { model.operationState.first {
                it is LocalKeePassViewModel.OperationState.Success || it is LocalKeePassViewModel.OperationState.Error
            } }
            check(result is LocalKeePassViewModel.OperationState.Success) { "$result" }
            val db = dao.getAllDatabases().first { it.isNotEmpty() }.single()
            ownedFile = File(context.filesDir, db.filePath)
            suspend fun snapshot() = model.openNativeBrowser(db.id).getOrThrow()
            suspend fun create() = snapshot().let { s -> model.createNativeGroup(db.id,
                s.rootGroup.identity.groupUuid, "Work", s.sourceRevision.sha256).getOrThrow() }
            val first = create()
            val second = create()
            val target = ExternalFolderTarget.KeePass(db.id, second.legacyPath!!, second.identity.groupUuid.toString(), "Work")
            service.rename(target, "Renamed")
            val renamed = snapshot()
            assertEquals("Work", renamed.group(first.identity)!!.name)
            assertEquals("Renamed", renamed.group(second.identity)!!.name)
            val bridge = KeePassCompatibilityBridge(KeePassWorkspaceRepository(context, dao, security))
            bridge.setDatabaseReadOnly(db.id, true)
            try {
                assertTrue(runCatching { service.delete(target) }.isFailure)
                assertNotNull(snapshot().group(second.identity))
            } finally { bridge.setDatabaseReadOnly(db.id, false) }
            // The old path remains on the target, but the UUID still points at the renamed folder.
            service.delete(target)
            assertNull(snapshot().group(second.identity))
            assertNotNull(snapshot().group(first.identity))
        } finally {
            model.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
            room.close()
            ownedFile?.let { file ->
                check(file.canonicalPath.startsWith(File(context.filesDir, "keepass").canonicalPath + File.separator))
                file.delete()
            }
        }
    }

    @Test fun mdbxMutatesOnlyTheSelectedDatabaseAndRejectsRoot() = runBlocking {
        val room = Room.inMemoryDatabaseBuilder(context, PasswordDatabase::class.java).build()
        val security = SecurityManager(context)
        val native = Mdbx2Repository(context, room.localMdbxDatabaseDao(), security)
        val service = ExternalFolderRepository(context, room)
        val files = mutableListOf<File>()
        try {
            suspend fun create(id: Long): Long {
                val file = native.createInitializedVaultFile(MdbxTigaMode.SKY,
                    MdbxVaultCredential(MdbxUnlockMethod.MASTER_PASSWORD, password = "synthetic-folder"))
                files += file
                return room.localMdbxDatabaseDao().insertDatabase(LocalMdbxDatabase(
                    id = id, name = "Folder test $id", filePath = file.absolutePath,
                    workingCopyPath = file.absolutePath, cacheCopyPath = file.absolutePath,
                    storageLocation = MdbxStorageLocation.INTERNAL.name, sourceType = MdbxSourceType.LOCAL_INTERNAL.name,
                    engineType = MdbxEngineType.RUST_MDBX2.name, tigaMode = MdbxTigaMode.SKY.name,
                    encryptedPassword = security.encryptData("synthetic-folder"),
                    unlockMethod = MdbxUnlockMethod.MASTER_PASSWORD.storedValue,
                    lastSyncStatus = MdbxSyncStatus.LOCAL_ONLY.name))
            }
            val a = create(88211)
            val b = create(88212)
            val folderA = native.createFolder(a, "Work", null)
            val folderB = native.createFolder(b, "Work", null)
            val target = ExternalFolderTarget.Mdbx(a, folderA.folderId, "Work")
            service.rename(target, "Renamed")
            assertEquals("Renamed", native.listFolders(a).single { it.folderId == folderA.folderId }.name)
            assertEquals("Work", native.listFolders(b).single { it.folderId == folderB.folderId }.name)
            assertTrue(runCatching { service.rename(target.copy(databaseId = b), "Wrong database") }.isFailure)
            assertTrue(runCatching { service.delete(target.copy(folderId = "root")) }.isFailure)
            service.delete(target)
            assertFalse(native.listFolders(a).any { it.folderId == folderA.folderId })
            assertTrue(native.listFolders(b).any { it.folderId == folderB.folderId })
        } finally { room.close(); files.forEach { it.delete() } }
    }

    @Test fun bitwardenRejectsStaleFolderBeforeMakingAnAccountRequest() = runBlocking {
        val room = Room.inMemoryDatabaseBuilder(context, PasswordDatabase::class.java).build()
        try {
            val service = ExternalFolderRepository(context, room)
            val target = ExternalFolderTarget.Bitwarden(93, "missing", "Work")
            assertTrue(runCatching { service.rename(target, "Renamed") }.isFailure)
            assertTrue(runCatching { service.delete(target) }.isFailure)
        } finally { room.close() }
    }
}
