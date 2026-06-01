package takagi.ru.monica.attachments.executor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.attachments.data.AttachmentDao
import takagi.ru.monica.attachments.model.Attachment
import takagi.ru.monica.attachments.model.AttachmentDownloadState
import takagi.ru.monica.attachments.model.AttachmentSource
import takagi.ru.monica.attachments.repository.AttachmentRepository
import takagi.ru.monica.utils.KeePassKdbxService

class KeePassAttachmentReconcilerTest {

    @Test
    fun reconcileSnapshot_insertsKeePassMetadataWithoutDownloadingBytes() = runBlocking {
        val dao = FakeAttachmentDao()
        val reconciler = reconcilerFor(dao)

        val report = reconciler.reconcileSnapshot(
            passwordId = 42,
            remoteAttachments = listOf(kpInfo(hashHex = "abc123", fileName = "invoice.pdf", sizeBytes = 12))
        )

        assertEquals(1, report.inserted)
        val inserted = dao.items.single()
        assertEquals(42, inserted.parentPasswordId)
        assertEquals(AttachmentSource.KEEPASS.name, inserted.source)
        assertEquals("invoice.pdf", inserted.fileName)
        assertEquals(AttachmentDownloadState.PENDING.name, inserted.downloadState)
        assertNull(inserted.localPath)
        assertEquals("invoice.pdf", KeePassAttachmentRef.decode(inserted.keepassBinaryRef!!).fileName)
    }

    @Test
    fun reconcileSnapshot_upgradesLegacyHashRefAndInvalidatesOldCache() = runBlocking {
        val dao = FakeAttachmentDao(
            attachment(
                id = 7,
                keepassBinaryRef = "abc123",
                fileName = "old.txt",
                localPath = "old-cache.enc",
                wrappedCek = "wrapped",
                downloadState = AttachmentDownloadState.DOWNLOADED
            )
        )
        val deletedPaths = mutableListOf<String>()
        val reconciler = reconcilerFor(dao, deletedPaths)

        val report = reconciler.reconcileSnapshot(
            passwordId = 42,
            remoteAttachments = listOf(kpInfo(hashHex = "abc123", fileName = "new.txt", sizeBytes = 24))
        )

        assertEquals(0, report.inserted)
        assertEquals(1, report.updated)
        assertEquals(listOf("old-cache.enc"), deletedPaths)
        val updated = dao.items.single()
        assertEquals(7, updated.id)
        assertEquals("new.txt", updated.fileName)
        assertEquals(AttachmentDownloadState.PENDING.name, updated.downloadState)
        assertNull(updated.localPath)
        assertNull(updated.wrappedCek)
        assertEquals("new.txt", KeePassAttachmentRef.decode(updated.keepassBinaryRef!!).fileName)
    }

    @Test
    fun reconcileSnapshot_keepsSameHashDifferentFileNamesSeparate() = runBlocking {
        val dao = FakeAttachmentDao()
        val reconciler = reconcilerFor(dao)

        val report = reconciler.reconcileSnapshot(
            passwordId = 42,
            remoteAttachments = listOf(
                kpInfo(hashHex = "samehash", fileName = "first.txt", sizeBytes = 10),
                kpInfo(hashHex = "samehash", fileName = "second.txt", sizeBytes = 10)
            )
        )

        assertEquals(2, report.inserted)
        val names = dao.items.map { it.fileName }.toSet()
        val refs = dao.items.map { it.keepassBinaryRef }.toSet()
        assertEquals(setOf("first.txt", "second.txt"), names)
        assertEquals(2, refs.size)
        assertTrue(refs.all { KeePassAttachmentRef.decode(it!!).hashHex == "samehash" })
    }

    private fun reconcilerFor(
        dao: FakeAttachmentDao,
        deletedPaths: MutableList<String> = mutableListOf()
    ): KeePassAttachmentReconciler = KeePassAttachmentReconciler(
        repository = AttachmentRepository(dao, clock = { 1000L }),
        executor = null,
        deleteLocalBlob = {
            deletedPaths += it
            true
        }
    )

    private fun kpInfo(
        hashHex: String,
        fileName: String,
        sizeBytes: Long
    ) = KeePassKdbxService.KeePassAttachmentInfo(
        hashHex = hashHex,
        fileName = fileName,
        sizeBytes = sizeBytes,
        memoryProtection = false
    )

    private fun attachment(
        id: Long,
        keepassBinaryRef: String,
        fileName: String,
        localPath: String? = null,
        wrappedCek: String? = null,
        downloadState: AttachmentDownloadState = AttachmentDownloadState.PENDING
    ) = Attachment(
        id = id,
        parentPasswordId = 42,
        source = AttachmentSource.KEEPASS.name,
        fileName = fileName,
        mimeType = "text/plain",
        sizeBytes = 1,
        localPath = localPath,
        wrappedCek = wrappedCek,
        keepassBinaryRef = keepassBinaryRef,
        downloadState = downloadState.name,
        createdAt = 1,
        updatedAt = 1
    )

    private class FakeAttachmentDao(
        initial: Attachment? = null
    ) : AttachmentDao {
        val items = mutableListOf<Attachment>()
        private var nextId = 1L

        init {
            initial?.let {
                items += it
                nextId = maxOf(nextId, it.id + 1)
            }
        }

        override fun observeActiveByParent(passwordId: Long): Flow<List<Attachment>> =
            flowOf(items.filter { it.parentPasswordId == passwordId && !it.isDeleted })

        override suspend fun getAllByParent(passwordId: Long): List<Attachment> =
            items.filter { it.parentPasswordId == passwordId }

        override suspend fun getActiveByParent(passwordId: Long): List<Attachment> =
            items.filter { it.parentPasswordId == passwordId && !it.isDeleted }

        override suspend fun getById(id: Long): Attachment? =
            items.firstOrNull { it.id == id }

        override suspend fun getByParentAndSource(passwordId: Long, source: String): List<Attachment> =
            items.filter { it.parentPasswordId == passwordId && it.source == source }

        override suspend fun findByBitwardenAttachmentId(attachmentId: String): Attachment? =
            items.firstOrNull { it.bitwardenAttachmentId == attachmentId }

        override suspend fun countActiveByParent(passwordId: Long): Int =
            getActiveByParent(passwordId).size

        override suspend fun countActiveByParents(passwordIds: List<Long>): Int =
            items.count { it.parentPasswordId in passwordIds && !it.isDeleted }

        override suspend fun parentsWithActiveAttachments(passwordIds: List<Long>): List<Long> =
            items.filter { it.parentPasswordId in passwordIds && !it.isDeleted }
                .map { it.parentPasswordId }
                .distinct()

        override fun observeParentsWithActiveAttachments(): Flow<List<Long>> =
            flowOf(items.filter { !it.isDeleted }.map { it.parentPasswordId }.distinct())

        override suspend fun selectAllLocalPaths(): List<String?> =
            items.map { it.localPath }

        override suspend fun selectAllActiveLocalAttachments(): List<Attachment> =
            items.filter { !it.isDeleted && it.localPath != null && it.wrappedCek != null }

        override suspend fun rewriteSourceToLocal(passwordId: Long, fromSource: String, now: Long): Int {
            var changed = 0
            items.replaceAll { item ->
                if (
                    item.parentPasswordId == passwordId &&
                    item.source == fromSource &&
                    item.localPath != null &&
                    item.wrappedCek != null &&
                    !item.isDeleted
                ) {
                    changed++
                    item.copy(
                        source = AttachmentSource.LOCAL.name,
                        bitwardenAttachmentId = null,
                        bitwardenUrl = null,
                        bitwardenFileKeyEnc = null,
                        keepassBinaryRef = null,
                        updatedAt = now
                    )
                } else {
                    item
                }
            }
            return changed
        }

        override suspend fun insert(attachment: Attachment): Long {
            val id = nextId++
            items += attachment.copy(id = id)
            return id
        }

        override suspend fun update(attachment: Attachment): Int {
            val index = items.indexOfFirst { it.id == attachment.id }
            if (index < 0) return 0
            items[index] = attachment
            return 1
        }

        override suspend fun delete(attachment: Attachment): Int =
            deleteById(attachment.id)

        override suspend fun deleteById(id: Long): Int =
            if (items.removeIf { it.id == id }) 1 else 0

        override suspend fun purgeByParent(passwordId: Long): Int {
            val before = items.size
            items.removeIf { it.parentPasswordId == passwordId }
            return before - items.size
        }

        override suspend fun softDeleteByParent(
            passwordId: Long,
            deletedAt: Long,
            updatedAt: Long
        ): Int = updateMatching(passwordId) {
            it.copy(isDeleted = true, deletedAt = deletedAt, updatedAt = updatedAt)
        }

        override suspend fun restoreByParent(passwordId: Long, updatedAt: Long): Int =
            updateMatching(passwordId) {
                it.copy(isDeleted = false, deletedAt = null, updatedAt = updatedAt)
            }

        override suspend fun updateDownloadState(id: Long, state: String, updatedAt: Long): Int {
            val index = items.indexOfFirst { it.id == id }
            if (index < 0) return 0
            items[index] = items[index].copy(downloadState = state, updatedAt = updatedAt)
            return 1
        }

        private fun updateMatching(
            passwordId: Long,
            transform: (Attachment) -> Attachment
        ): Int {
            var changed = 0
            items.replaceAll {
                if (it.parentPasswordId == passwordId) {
                    changed++
                    transform(it)
                } else {
                    it
                }
            }
            return changed
        }
    }
}
