package takagi.ru.monica.attachments.model

/**
 * 附件的归属来源。
 *
 * 对应 requirements.md 中的 Attachment_Source：
 * - [LOCAL]：Monica 本地密码条目的附件，密文保存在 `filesDir/secure_attachments/`。
 * - [BITWARDEN]：挂在 Bitwarden cipher 上的附件，元数据来自服务器同步；本地仅保留缓存。
 * - [KEEPASS]：挂在 KeePass Entry.binaries 上的附件，存储位于 kdbx 二进制池。
 */
enum class AttachmentSource {
    LOCAL,
    BITWARDEN,
    KEEPASS;

    companion object {
        fun fromDbValue(raw: String?): AttachmentSource {
            if (raw.isNullOrBlank()) return LOCAL
            return values().firstOrNull { it.name == raw.trim().uppercase() } ?: LOCAL
        }
    }
}

/**
 * 附件本地化可用状态。
 *
 * - [PENDING]：仅有元数据，尚未下载到本地（典型：新同步到的 Bitwarden/KeePass 附件）。
 * - [DOWNLOADING]：后台正在下载或解密。
 * - [DOWNLOADED]：本地已存在可解密的密文文件，可供预览、导出。
 * - [FAILED]：下载或解密失败，UI 需给出重试入口。
 */
enum class AttachmentDownloadState {
    PENDING,
    DOWNLOADING,
    DOWNLOADED,
    FAILED;

    companion object {
        fun fromDbValue(raw: String?): AttachmentDownloadState {
            if (raw.isNullOrBlank()) return PENDING
            return values().firstOrNull { it.name == raw.trim().uppercase() } ?: PENDING
        }
    }
}
