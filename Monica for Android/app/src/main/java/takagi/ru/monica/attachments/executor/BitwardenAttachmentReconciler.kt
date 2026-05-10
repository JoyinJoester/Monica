package takagi.ru.monica.attachments.executor

import takagi.ru.monica.attachments.model.Attachment
import takagi.ru.monica.attachments.model.AttachmentDownloadState
import takagi.ru.monica.attachments.model.AttachmentSource
import takagi.ru.monica.attachments.repository.AttachmentRepository
import takagi.ru.monica.attachments.storage.AttachmentStorage
import takagi.ru.monica.bitwarden.api.CipherAttachmentApiData

/**
 * Bitwarden 附件元数据对齐器。
 *
 * 每次拉完 cipher 同步后调用一次 [reconcile]：
 * - 服务端 `attachments == null` → 保持本地不变；
 * - 服务端 `attachments == []` → 视作远端清空，删除该密码下所有 `source = BITWARDEN` 的记录；
 * - 否则按 `bitwardenAttachmentId` 做差异合并：
 *   - 新增：`PENDING` 写入，`downloadState = PENDING`；
 *   - 移除：删除本地记录与缓存；
 *   - 更新：若服务端 `size`/`fileName`/`url`/`key` 有变化则 `update`，同时把 `downloadState`
 *     回退到 `PENDING`（原缓存需要重新下载），并清理旧的本地密文。
 *
 * 本类不做任何网络调用，也不下载字节。字节下载走 [BitwardenAttachmentExecutor.download]。
 * 对应 requirements.md Requirement 5.1 / 5.2 / 9.3 / 9.4。
 */
class BitwardenAttachmentReconciler(
    private val repository: AttachmentRepository,
    private val storage: AttachmentStorage
) {

    data class Report(
        val inserted: Int = 0,
        val removed: Int = 0,
        val updated: Int = 0,
        val skipped: Int = 0
    )

    suspend fun reconcile(
        passwordId: Long,
        remoteAttachments: List<CipherAttachmentApiData>?
    ): Report {
        if (remoteAttachments == null) return Report(skipped = 1)
        val remoteById = remoteAttachments.associateBy { it.id }
        val local = repository.listByParentAndSource(passwordId, AttachmentSource.BITWARDEN)
        val localById = local.mapNotNull { attach ->
            val id = attach.bitwardenAttachmentId ?: return@mapNotNull null
            id to attach
        }.toMap()

        var inserted = 0
        var removed = 0
        var updated = 0

        // 1. 本地存在但远端缺失 → 删除
        for ((attachmentId, localAttach) in localById) {
            if (attachmentId !in remoteById) {
                localAttach.localPath?.let { storage.delete(it) }
                repository.deleteById(localAttach.id)
                removed++
            }
        }

        // 2. 远端存在
        val now = System.currentTimeMillis()
        for ((attachmentId, remote) in remoteById) {
            val localAttach = localById[attachmentId]
            if (localAttach == null) {
                repository.insert(
                    Attachment(
                        id = 0,
                        parentPasswordId = passwordId,
                        source = AttachmentSource.BITWARDEN.name,
                        fileName = remote.fileName ?: DEFAULT_FILE_NAME,
                        mimeType = guessMimeType(remote.fileName),
                        sizeBytes = remote.size.toLongOrNull() ?: 0L,
                        bitwardenAttachmentId = attachmentId,
                        bitwardenUrl = remote.url,
                        bitwardenFileKeyEnc = remote.key,
                        downloadState = AttachmentDownloadState.PENDING.name,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                inserted++
            } else if (needsUpdate(localAttach, remote)) {
                localAttach.localPath?.let { storage.delete(it) }
                repository.update(
                    localAttach.copy(
                        fileName = remote.fileName ?: localAttach.fileName,
                        sizeBytes = remote.size.toLongOrNull() ?: localAttach.sizeBytes,
                        bitwardenUrl = remote.url ?: localAttach.bitwardenUrl,
                        bitwardenFileKeyEnc = remote.key ?: localAttach.bitwardenFileKeyEnc,
                        localPath = null,
                        wrappedCek = null,
                        sha256Hex = null,
                        downloadState = AttachmentDownloadState.PENDING.name,
                        updatedAt = now
                    )
                )
                updated++
            }
        }

        return Report(inserted = inserted, removed = removed, updated = updated)
    }

    private fun needsUpdate(local: Attachment, remote: CipherAttachmentApiData): Boolean {
        val remoteSize = remote.size.toLongOrNull() ?: -1L
        if (remoteSize >= 0 && remoteSize != local.sizeBytes) return true
        if ((remote.fileName ?: "") != local.fileName) return true
        // key 不变但 url 变更时也要保留（短期签名 URL 过期），但不强制触发重新下载。
        return false
    }

    private fun guessMimeType(fileName: String?): String {
        if (fileName.isNullOrBlank()) return "application/octet-stream"
        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (ext.isBlank()) return "application/octet-stream"
        return android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    companion object {
        private const val DEFAULT_FILE_NAME = "attachment"
    }
}
