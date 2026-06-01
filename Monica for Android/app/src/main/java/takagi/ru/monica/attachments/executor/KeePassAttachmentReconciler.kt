package takagi.ru.monica.attachments.executor

import takagi.ru.monica.attachments.model.Attachment
import takagi.ru.monica.attachments.model.AttachmentDownloadState
import takagi.ru.monica.attachments.model.AttachmentSource
import takagi.ru.monica.attachments.repository.AttachmentRepository
import takagi.ru.monica.attachments.storage.AttachmentStorage
import takagi.ru.monica.attachments.util.AttachmentLogger
import takagi.ru.monica.utils.KeePassKdbxService
import java.net.URLConnection

/**
 * Keeps Room attachment metadata aligned with KeePass Entry.binaries.
 *
 * This deliberately syncs metadata only. Attachment bytes are fetched lazily through
 * [KeePassAttachmentExecutor.download] when the user previews or exports a file.
 */
class KeePassAttachmentReconciler(
    private val repository: AttachmentRepository,
    private val executor: KeePassAttachmentExecutor?,
    private val deleteLocalBlob: suspend (String) -> Boolean
) {

    data class Report(
        val inserted: Int = 0,
        val removed: Int = 0,
        val updated: Int = 0,
        val skipped: Int = 0
    )

    suspend fun reconcile(
        passwordId: Long,
        databaseId: Long?,
        entryUuid: String?
    ): Report {
        if (passwordId <= 0 || databaseId == null || entryUuid.isNullOrBlank()) {
            return Report(skipped = 1)
        }

        val remoteAttachments = executor?.snapshotAttachments(databaseId, entryUuid)
            ?: return Report(skipped = 1)
        return reconcileSnapshot(passwordId, remoteAttachments)
    }

    suspend fun reconcileSnapshot(
        passwordId: Long,
        remoteAttachments: List<KeePassKdbxService.KeePassAttachmentInfo>
    ): Report {
        val remoteByRef = remoteAttachments.associateBy {
            KeePassAttachmentRef.from(it.hashHex, it.fileName).encode()
        }
        val remoteHashes = remoteAttachments.map { it.hashHex }.toSet()
        val local = repository.listByParentAndSource(passwordId, AttachmentSource.KEEPASS)
        val localByRef = local.mapNotNull { attach ->
            val ref = attach.keepassBinaryRef?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ref to attach
        }.toMap()
        val usedLocalIds = mutableSetOf<Long>()

        var inserted = 0
        var removed = 0
        var updated = 0

        for ((ref, localAttach) in localByRef) {
            if (ref !in remoteByRef && KeePassAttachmentRef.decode(ref).legacyHashKey() !in remoteHashes) {
                localAttach.localPath?.let { deleteLocalBlob(it) }
                repository.deleteById(localAttach.id)
                removed++
            }
        }

        val now = System.currentTimeMillis()
        for ((ref, remote) in remoteByRef) {
            val localAttach = listOfNotNull(localByRef[ref], localByRef[remote.hashHex])
                .firstOrNull { it.id !in usedLocalIds }
            if (localAttach == null) {
                repository.insert(
                    Attachment(
                        id = 0,
                        parentPasswordId = passwordId,
                        source = AttachmentSource.KEEPASS.name,
                        fileName = remote.fileName.ifBlank { DEFAULT_FILE_NAME },
                        mimeType = guessMimeType(remote.fileName),
                        sizeBytes = remote.sizeBytes,
                        keepassBinaryRef = ref,
                        downloadState = AttachmentDownloadState.PENDING.name,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                inserted++
            } else if (needsUpdate(localAttach, remote, ref)) {
                usedLocalIds += localAttach.id
                if (localAttach.keepassBinaryRef != ref) {
                    localAttach.localPath?.let { deleteLocalBlob(it) }
                }
                repository.update(
                    localAttach.copy(
                        fileName = remote.fileName.ifBlank { localAttach.fileName },
                        mimeType = guessMimeType(remote.fileName),
                        sizeBytes = remote.sizeBytes.takeIf { it > 0 } ?: localAttach.sizeBytes,
                        keepassBinaryRef = ref,
                        localPath = if (localAttach.keepassBinaryRef == ref) localAttach.localPath else null,
                        wrappedCek = if (localAttach.keepassBinaryRef == ref) localAttach.wrappedCek else null,
                        sha256Hex = if (localAttach.keepassBinaryRef == ref) localAttach.sha256Hex else null,
                        downloadState = if (localAttach.keepassBinaryRef == ref) {
                            localAttach.downloadState
                        } else {
                            AttachmentDownloadState.PENDING.name
                        },
                        updatedAt = now
                    )
                )
                updated++
            } else {
                usedLocalIds += localAttach.id
            }
        }

        runCatching {
            AttachmentLogger.logOk(
                event = AttachmentLogger.Event.RECONCILE,
                attachmentId = null,
                source = AttachmentSource.KEEPASS,
                extras = mapOf("inserted" to inserted, "removed" to removed, "updated" to updated)
            )
        }
        return Report(inserted = inserted, removed = removed, updated = updated)
    }

    private fun needsUpdate(
        local: Attachment,
        remote: KeePassKdbxService.KeePassAttachmentInfo,
        ref: String
    ): Boolean {
        if (local.keepassBinaryRef != ref) return true
        if (remote.fileName.isNotBlank() && local.fileName != remote.fileName) return true
        if (remote.sizeBytes > 0 && local.sizeBytes != remote.sizeBytes) return true
        return false
    }

    private fun KeePassAttachmentRef.legacyHashKey(): String = hashHex

    private fun guessMimeType(fileName: String?): String {
        if (fileName.isNullOrBlank()) return "application/octet-stream"
        return URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"
    }

    companion object {
        private const val DEFAULT_FILE_NAME = "attachment"

        fun create(
            repository: AttachmentRepository,
            executor: KeePassAttachmentExecutor,
            storage: AttachmentStorage
        ): KeePassAttachmentReconciler = KeePassAttachmentReconciler(
            repository = repository,
            executor = executor,
            deleteLocalBlob = { storage.delete(it) }
        )
    }
}
