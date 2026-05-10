package takagi.ru.monica.attachments.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import takagi.ru.monica.data.PasswordEntry

/**
 * 附件元数据。
 *
 * 一条 [Attachment] 始终挂在一个 [PasswordEntry] 之下，对应 requirements.md Requirement 1：
 * - 永久删除密码时 CASCADE 清除附件元数据；
 * - 软删除/恢复通过应用层事务同步 [isDeleted]/[deletedAt]；
 * - 不强制 [fileName] 唯一，同名文件可共存。
 *
 * 字段语义见各个 @ColumnInfo 上的注释，[source] 决定了 [localPath]、[bitwardenAttachmentId]、
 * [keepassBinaryRef] 中哪些字段会被填充：
 *
 * | source      | localPath | bitwardenAttachmentId | bitwardenUrl | bitwardenFileKeyEnc | keepassBinaryRef |
 * |-------------|-----------|-----------------------|--------------|---------------------|------------------|
 * | LOCAL       | 非空      | null                  | null         | null                | null             |
 * | BITWARDEN   | 可空*     | 非空                  | 非空         | 非空                | null             |
 * | KEEPASS     | 可空*     | null                  | null         | null                | 非空             |
 *
 * *（BITWARDEN/KEEPASS 的 localPath 在 DOWNLOADED 状态下指向本地缓存密文，PENDING 时为 null。）
 */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = PasswordEntry::class,
            parentColumns = ["id"],
            childColumns = ["parent_password_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["parent_password_id"], name = "index_attachments_parent"),
        Index(value = ["source"], name = "index_attachments_source"),
        Index(value = ["bitwarden_attachment_id"], name = "index_attachments_bw_id"),
        Index(value = ["keepass_binary_ref"], name = "index_attachments_kp_ref")
    ]
)
data class Attachment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 所属密码的数据库 id。 */
    @ColumnInfo(name = "parent_password_id")
    val parentPasswordId: Long,

    /** [AttachmentSource] 的 DB 存储形式（字符串，与枚举 name 对应）。 */
    @ColumnInfo(name = "source")
    val source: String,

    /** 用户可见的文件名（用于展示 / 分享）。不进日志。 */
    @ColumnInfo(name = "file_name")
    val fileName: String,

    /** MIME 类型，如 `image/png`、`application/pdf`。无法识别时写 `application/octet-stream`。 */
    @ColumnInfo(name = "mime_type")
    val mimeType: String,

    /** 原始文件字节数（明文大小，非密文）。 */
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,

    /** 明文 SHA-256 hex。本地附件必填；远端附件在首次下载后回填。 */
    @ColumnInfo(name = "sha256_hex")
    val sha256Hex: String? = null,

    /**
     * 用 Monica 主密钥包裹后的 Attachment_CEK（Base64）。
     * 远端附件在 `PENDING` 状态下为 null，下载并缓存成功后回填。
     */
    @ColumnInfo(name = "wrapped_cek")
    val wrappedCek: String? = null,

    /** 本地密文路径：`filesDir/secure_attachments/<uuid>.enc`。 */
    @ColumnInfo(name = "local_path")
    val localPath: String? = null,

    /** Bitwarden attachment id（服务端 UUID）。 */
    @ColumnInfo(name = "bitwarden_attachment_id")
    val bitwardenAttachmentId: String? = null,

    /** Bitwarden 附件下载 URL（可能是 Azure Blob 直链）。 */
    @ColumnInfo(name = "bitwarden_url")
    val bitwardenUrl: String? = null,

    /**
     * Bitwarden 附件独立密钥（EncString）：使用 cipher key 解包后得到 64 字节的
     * `enc||mac` 用于 AES-CBC-HMAC 解密附件字节。
     */
    @ColumnInfo(name = "bitwarden_file_key_enc")
    val bitwardenFileKeyEnc: String? = null,

    /** KeePass binary pool 的引用键（kotpass BinaryReference.hash 或等价标识）。 */
    @ColumnInfo(name = "keepass_binary_ref")
    val keepassBinaryRef: String? = null,

    /** [AttachmentDownloadState] 的 DB 存储形式（字符串）。 */
    @ColumnInfo(name = "download_state")
    val downloadState: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null
) {
    /** 便捷访问枚举值。 */
    val sourceEnum: AttachmentSource
        get() = AttachmentSource.fromDbValue(source)

    /** 便捷访问枚举值。 */
    val downloadStateEnum: AttachmentDownloadState
        get() = AttachmentDownloadState.fromDbValue(downloadState)
}
