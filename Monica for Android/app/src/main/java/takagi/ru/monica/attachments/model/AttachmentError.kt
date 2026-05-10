package takagi.ru.monica.attachments.model

/**
 * 附件相关所有可恢复错误的分层类型。
 *
 * 对应 requirements.md Requirement 10。所有进入 UI 的错误应收敛为该 sealed 家族，
 * 以便本地化文案、决定是否展示"升级 Plus"或"前往 Bitwarden 升级"等操作入口。
 *
 * 注意：该类型继承自 [Exception] 仅为方便在 Result/try-catch 中携带，
 * 不期望被用户态日志完整打印堆栈（日志脱敏见 Requirement 10.2）。
 */
sealed class AttachmentError(message: String) : Exception(message) {
    /** 单附件字节大小超出来源上限（本地/Bitwarden 100MB 或 KeePass 软上限）。 */
    data class TooLarge(val limitBytes: Long, val actualBytes: Long) :
        AttachmentError("Attachment size $actualBytes exceeds limit $limitBytes")

    /** Free 账户单条密码附件数量达到上限（当前为 10）。 */
    data object QuotaExceeded : AttachmentError("Free quota exceeded") {
        private fun readResolve(): Any = QuotaExceeded
    }

    /** 目标为 Bitwarden 且当前账户非 Premium。 */
    data object PremiumRequired : AttachmentError("Bitwarden premium required") {
        private fun readResolve(): Any = PremiumRequired
    }

    /** 离线或无网络时尝试 Bitwarden 上传/下载。 */
    data object Offline : AttachmentError("Network unavailable") {
        private fun readResolve(): Any = Offline
    }

    /** Bitwarden/HTTP 层错误。 */
    data class NetworkError(val httpStatus: Int?) : AttachmentError("Network error status=$httpStatus")

    /** AES-GCM 解密失败、CEK 包裹无效、Bitwarden MAC 校验失败等。 */
    data object CryptoError : AttachmentError("Attachment crypto error") {
        private fun readResolve(): Any = CryptoError
    }

    /** 本地文件读写错误（权限、IO、磁盘空间）。 */
    data object IoError : AttachmentError("Attachment io error") {
        private fun readResolve(): Any = IoError
    }

    /** 尝试操作 KeePass 附件，但对应数据库当前未解锁。 */
    data object KdbxLocked : AttachmentError("KeePass database locked") {
        private fun readResolve(): Any = KdbxLocked
    }

    /** KeePass 写入时发生 OOM 或 kotpass 抛出容量异常。 */
    data object KdbxCapacityExceeded : AttachmentError("KeePass database capacity exceeded") {
        private fun readResolve(): Any = KdbxCapacityExceeded
    }
}
