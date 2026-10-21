package takagi.ru.monica.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.credentialexchange.*
import takagi.ru.monica.data.*
import takagi.ru.monica.keepass.KeePassFieldChange
import takagi.ru.monica.security.SessionManager
import uniffi.mdbx_ffi.*
import java.util.UUID

class DatabaseManagerDeletionTest {
    @Test fun localFolderDeletionTrashesContentRemovesPasskeysAndKeepsOtherItems() = runBlocking {
        val f = TransferFixture()
        try {
            val repo = DatabaseManagerRepository(f.context)
            val location = DatabaseManagerLocation(ImportDestination.Local)
            val folder = repo.createFolder(location, f.prefix)
            val category = folder.substringAfter(':').toLong()
            val password = f.db.passwordEntryDao().insertPasswordEntry(PasswordEntry(title = f.prefix,
                website = "", username = "", password = "synthetic", categoryId = category))
            val outside = f.db.passwordEntryDao().insertPasswordEntry(PasswordEntry(title = "${f.prefix}-outside",
                website = "", username = "", password = "synthetic"))
            takagi.ru.monica.attachments.AttachmentContainer.facade(f.context).addInlineAttachment(
                takagi.ru.monica.attachments.facade.AttachmentFacade.InlineUploadRequest(
                    takagi.ru.monica.attachments.model.AttachmentOwner.password(password),
                    takagi.ru.monica.attachments.model.AttachmentSource.LOCAL, "synthetic.bin", "application/octet-stream", byteArrayOf(1, 2, 3), true))
            val item = f.db.secureItemDao().insertItem(SecureItem(title = f.prefix, itemType = ItemType.NOTE, itemData = "synthetic", categoryId = category))
            val key = f.db.passkeyDao().insertCopy(PasskeyEntry(credentialId = f.credentialId, rpId = f.rpId, rpName = f.prefix,
                userId = "synthetic", userName = "synthetic", userDisplayName = f.prefix, publicKey = "synthetic", privateKeyAlias = "", categoryId = category))
            val snapshot = repo.browse(location)
            repo.delete(location, setOf(folder, "password:$password"), snapshot.revision)
            assertNull(f.db.categoryDao().getCategoryById(category))
            val deletedPassword = f.db.passwordEntryDao().getPasswordEntryById(password)!!
            assertTrue(deletedPassword.isDeleted); assertNotNull(deletedPassword.deletedAt); assertNull(deletedPassword.categoryId)
            assertTrue(f.db.attachmentDao().getActiveByParents(listOf(password)).isEmpty())
            f.db.attachmentDao().restoreByParent(password, System.currentTimeMillis())
            assertEquals(1, f.db.attachmentDao().getActiveByParents(listOf(password)).size)
            assertTrue(f.db.secureItemDao().getItemById(item)!!.isDeleted)
            assertNull(f.db.passkeyDao().getPasskeyByRecordId(key))
            assertFalse(f.db.passwordEntryDao().getPasswordEntryById(outside)!!.isDeleted)
            assertTrue(repo.browse(location).nodes.none { it.id == folder || it.id == "password:$password" || it.id == "item:$item" || it.id == "passkey:$key" })
        } finally { f.close() }
    }

    @Test fun changedMissingAndLockedSelectionsCannotDeleteLocalData() = runBlocking {
        val f = TransferFixture()
        try {
            val repo = DatabaseManagerRepository(f.context)
            val location = DatabaseManagerLocation(ImportDestination.Local)
            val id = f.db.passwordEntryDao().insertPasswordEntry(PasswordEntry(title = f.prefix, website = "", username = "", password = "synthetic"))
            val snapshot = repo.browse(location)
            val entry = f.db.passwordEntryDao().getPasswordEntryById(id)!!
            f.db.passwordEntryDao().updatePasswordEntry(entry.copy(username = "new value"))
            assertTrue(runCatching { repo.delete(location, setOf("password:$id"), snapshot.revision) }.isFailure)
            val revision = repo.browse(location).revision
            assertTrue(runCatching { repo.delete(location, setOf("password:$id", "password:-1"), revision) }.isFailure)
            SessionManager.markLocked()
            assertTrue(runCatching { repo.delete(location, setOf("password:$id"), revision) }.isFailure)
            SessionManager.markUnlocked()
            assertFalse(f.db.passwordEntryDao().getPasswordEntryById(id)!!.isDeleted)
        } finally { SessionManager.markUnlocked(); f.close() }
    }

    @Test fun localFolderSharedByExternalItemsStopsBeforeAnyWrite() = runBlocking {
        val f = TransferFixture()
        try {
            val source = f.mdbx(); val repo = DatabaseManagerRepository(f.context)
            val location = DatabaseManagerLocation(ImportDestination.Local)
            val folder = repo.createFolder(location, f.prefix)
            val category = folder.substringAfter(':').toLong()
            val local = f.db.passwordEntryDao().insertPasswordEntry(PasswordEntry(title = f.prefix,
                website = "", username = "", password = "synthetic", categoryId = category))
            val external = f.db.passwordEntryDao().insertPasswordEntry(PasswordEntry(title = "${f.prefix}-external",
                website = "", username = "", password = "synthetic", categoryId = category, mdbxDatabaseId = source.databaseId))
            val snapshot = repo.browse(location)
            assertTrue(runCatching { repo.delete(location, setOf(folder), snapshot.revision) }.isFailure)
            assertFalse(f.db.passwordEntryDao().getPasswordEntryById(local)!!.isDeleted)
            assertFalse(f.db.passwordEntryDao().getPasswordEntryById(external)!!.isDeleted)
            assertNotNull(f.db.categoryDao().getCategoryById(category))
        } finally { f.close() }
    }

    @Test fun mdbxMixedSelectionDeletesNativeTreeOnceAndPersistsOtherObjects() = runBlocking {
        val f = TransferFixture()
        try {
            val source = f.mdbx(); val repo = DatabaseManagerRepository(f.context)
            val location = DatabaseManagerLocation(source)
            val folder = repo.createFolder(location, f.prefix)
            val path = f.db.localMdbxDatabaseDao().getDatabaseById(source.databaseId)!!.filePath
            val ids = openVault(path, "Synthetic transfer fixture password", "manager-delete-test").use { vault ->
                listOf(vault.createObject(folder, "com.example.future", f.prefix, "{\"unknown\":true}", 9u).objectId,
                    vault.createObject(Mdbx2VaultSessionExecutor.rootProjectId(vault.info().vaultId), "com.example.future", "${f.prefix}-keep", "{}", 9u).objectId)
            }
            Mdbx2NativeReadSessions.clear()
            val snapshot = repo.browse(location)
            assertTrue(runCatching { repo.delete(location, setOf(folder), "stale-revision") }.isFailure)
            repo.delete(location, setOf(folder, ids[0]), snapshot.revision)
            Mdbx2NativeReadSessions.clear()
            val after = repo.browse(location)
            assertEquals(setOf(ids[1]), after.nodes.map { it.id }.toSet())
        } finally { f.close() }
    }

    @Test fun keepassMixedSelectionUsesUuidAndOneVerifiedWrite() = runBlocking {
        val f = TransferFixture()
        try {
            val source = f.keepass(); val repo = DatabaseManagerRepository(f.context)
            val location = DatabaseManagerLocation(source)
            val folder = repo.createFolder(location, f.prefix)
            val native = repo.keepass.openNativeBrowser(source.databaseId).getOrThrow()
            suspend fun create(parent: UUID, title: String): String {
                val revision = repo.keepass.openNativeBrowser(source.databaseId).getOrThrow().sourceRevision.sha256
                return repo.kdbx.createNativeEntry(source.databaseId, parent, listOf(KeePassFieldChange("Title", title, false)), revision)
                    .getOrThrow().identity.entryUuid.toString()
            }
            val child = create(UUID.fromString(folder), f.prefix)
            val loose = create(native.rootGroup.identity.groupUuid, "${f.prefix}-loose")
            val keep = create(native.rootGroup.identity.groupUuid, "${f.prefix}-keep")
            val snapshot = repo.browse(location)
            assertTrue(runCatching { repo.delete(location, setOf(folder, loose), "stale-revision") }.isFailure)
            repo.delete(location, setOf(folder, child, loose), snapshot.revision)
            assertEquals(setOf(keep), repo.browse(location).nodes.map { it.id }.toSet())
            assertEquals(listOf("${f.prefix}-keep"), f.keepassEntries(source.databaseId).map { it.fields["Title"]!!.content })
        } finally { f.close() }
    }
}
