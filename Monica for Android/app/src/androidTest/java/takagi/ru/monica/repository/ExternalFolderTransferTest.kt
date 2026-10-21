package takagi.ru.monica.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.credentialexchange.*
import takagi.ru.monica.data.*
import takagi.ru.monica.keepass.KeePassFieldChange
import java.util.UUID

class ExternalFolderTransferTest {
    @Test fun keepassCopiesNestedNativeContentsAndMovesByUuidWithStalePath() = runBlocking {
        val f = TransferFixture()
        try {
            val manager = DatabaseManagerRepository(f.context)
            val service = ExternalFolderRepository(f.context)
            val source = f.keepass(); val target = f.keepass()
            val parent = manager.createFolder(DatabaseManagerLocation(source), "Parent")
            val folder = manager.createFolder(DatabaseManagerLocation(source, parent), "Work")
            val child = manager.createFolder(DatabaseManagerLocation(source, folder), "Child")
            var native = manager.keepass.openNativeBrowser(source.databaseId).getOrThrow()
            val entry = manager.keepass.createNativeEntry(source.databaseId, UUID.fromString(child),
                listOf(KeePassFieldChange("Title", f.prefix), KeePassFieldChange("Password", "secret", true),
                    KeePassFieldChange("UnknownNativeField", "preserve", true)), native.sourceRevision.sha256).getOrThrow()
            val original = ExternalFolderTarget.KeePass(source.databaseId, "stale/path", folder, "Work")
            service.transfer(original, DatabaseManagerLocation(target), true)
            native = manager.keepass.openNativeBrowser(target.databaseId).getOrThrow()
            assertEquals("preserve", native.entries.single().field("UnknownNativeField")!!.rawValue)
            assertEquals("secret", native.entries.single().field("Password")!!.rawValue)
            assertEquals(setOf("Root", "Work", "Child"), native.groups.map { it.name }.toSet())
            assertNotNull(manager.keepass.openNativeBrowser(source.databaseId).getOrThrow().entry(entry.identity))
            val options = service.transferFolders(original, DatabaseManagerStore(source, "Source"))
            assertFalse(options.any { it.first.folderId in setOf(folder, child) })
            assertTrue(runCatching { service.transfer(original, DatabaseManagerLocation(source, child), false) }.isFailure)
            service.transfer(original, DatabaseManagerLocation(source), false)
            native = manager.keepass.openNativeBrowser(source.databaseId).getOrThrow()
            assertEquals(native.rootGroup.identity, native.groups.single { it.identity.groupUuid.toString() == folder }.parentGroup)
            assertEquals(entry.identity, native.entries.single().identity)
            assertEquals("preserve", native.entries.single().field("UnknownNativeField")!!.rawValue)
        } finally { f.close() }
    }

    @Test fun mdbxCopyAndCrossDatabaseMovePreserveNativeTreeAndEntries() = runBlocking {
        val f = TransferFixture()
        try {
            val manager = DatabaseManagerRepository(f.context)
            val service = ExternalFolderRepository(f.context)
            val source = f.mdbx(); val target = f.mdbx()
            val folder = manager.createFolder(DatabaseManagerLocation(source), "Work")
            val child = manager.createFolder(DatabaseManagerLocation(source, folder), "Child")
            f.passwords.insertPasswordEntry(PasswordEntry(title = f.prefix, website = "com.cn21.ecloud", username = "user",
                password = f.security.encryptData("secret"), mdbxDatabaseId = source.databaseId, mdbxFolderId = child))
            val original = ExternalFolderTarget.Mdbx(source.databaseId, folder, "Work")
            service.transfer(original, DatabaseManagerLocation(target), true)
            assertEquals(1, manager.mdbx.readStoredEntries(source.databaseId).size)
            assertEquals(1, manager.mdbx.readStoredEntries(target.databaseId).size)
            assertEquals(setOf("Work", "Child"), manager.mdbx.listFolders(target.databaseId).map { it.name }.toSet())
            val destination = manager.createFolder(DatabaseManagerLocation(target), "Moved")
            service.transfer(original, DatabaseManagerLocation(target, destination), false)
            assertFalse(manager.mdbx.listFolders(source.databaseId).any { it.folderId == folder })
            assertTrue(manager.mdbx.readStoredEntries(source.databaseId).isEmpty())
            val stored = manager.mdbx.readStoredEntries(target.databaseId)
            assertEquals(2, stored.size)
            assertTrue(stored.all { org.json.JSONObject(it.payloadJson).getString("password_plain") == "secret" })
        } finally { f.close() }
    }

    @Test fun readOnlySourceAndMissingFoldersCannotCreateDestinationCopies() = runBlocking {
        val f = TransferFixture()
        try {
            val manager = DatabaseManagerRepository(f.context)
            val service = ExternalFolderRepository(f.context)
            val source = f.keepass(); val target = f.keepass()
            val folder = manager.createFolder(DatabaseManagerLocation(source), "Work")
            val bridge = KeePassCompatibilityBridge(manager.keepass)
            bridge.setDatabaseReadOnly(source.databaseId, true)
            val original = ExternalFolderTarget.KeePass(source.databaseId, "Work", folder, "Work")
            try {
                assertTrue(runCatching { service.transfer(original, DatabaseManagerLocation(target), false) }.isFailure)
                assertEquals(1, manager.keepass.openNativeBrowser(target.databaseId).getOrThrow().groups.size)
                service.transfer(original, DatabaseManagerLocation(target), true)
                assertEquals(2, manager.keepass.openNativeBrowser(target.databaseId).getOrThrow().groups.size)
                assertTrue(runCatching { service.transfer(original.copy(uuid = UUID.randomUUID().toString()), DatabaseManagerLocation(target), true) }.isFailure)
            } finally { bridge.setDatabaseReadOnly(source.databaseId, false) }
        } finally { f.close() }
    }
}
