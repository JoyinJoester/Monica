package takagi.ru.monica.attachments.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.attachments.model.Attachment
import takagi.ru.monica.attachments.model.AttachmentDownloadState
import takagi.ru.monica.attachments.model.AttachmentSource

/**
 * 备份 / 恢复时的附件元数据编解码。
 *
 * 只处理 `source = LOCAL` 的附件：Bitwarden 附件在服务端、KeePass 附件在 kdbx 内部，
 * 都不属于 Monica 自己的备份范畴（恢复时再经由 sync / kdbx 解锁自动重建）。
 *
 * 文件结构（写入 zip 的 `attachments/` 目录）：
 *
 * ```
 * attachments/
 *   attachments_meta.json          # 本文件，元数据清单
 *   <uuid1>.enc                    # AES-GCM 密文文件（由 AttachmentStorage 写入）
 *   <uuid2>.enc
 *   ...
 * ```
 */
object AttachmentBackupCodec {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 单个附件的备份条目（JSON 序列化结构）。 */
    @Serializable
    data class Entry(
        val parentPasswordId: Long,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val sha256Hex: String?,
        val wrappedCek: String,
        val localPath: String,
        val createdAt: Long,
        val updatedAt: Long
    )

    /** 整个备份 manifest 的顶层结构；保留版本号便于后续演进。 */
    @Serializable
    data class Manifest(
        val version: Int = 1,
        val entries: List<Entry> = emptyList()
    )

    fun encode(attachments: List<Attachment>): String {
        val entries = attachments.mapNotNull { it.toEntry() }
        return json.encodeToString(Manifest(entries = entries))
    }

    fun decode(jsonText: String): Manifest {
        if (jsonText.isBlank()) return Manifest()
        return runCatching { json.decodeFromString<Manifest>(jsonText) }
            .getOrElse { Manifest() }
    }

    fun Entry.toAttachment(now: Long = System.currentTimeMillis()): Attachment = Attachment(
        id = 0,
        parentPasswordId = parentPasswordId,
        source = AttachmentSource.LOCAL.name,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        sha256Hex = sha256Hex,
        wrappedCek = wrappedCek,
        localPath = localPath,
        downloadState = AttachmentDownloadState.DOWNLOADED.name,
        createdAt = createdAt.takeIf { it > 0 } ?: now,
        updatedAt = now
    )

    private fun Attachment.toEntry(): Entry? {
        val path = localPath ?: return null
        val wrapped = wrappedCek ?: return null
        if (sourceEnum != AttachmentSource.LOCAL) return null
        return Entry(
            parentPasswordId = parentPasswordId,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            sha256Hex = sha256Hex,
            wrappedCek = wrapped,
            localPath = path,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
