package takagi.ru.monica.attachments.util

import android.util.Log
import takagi.ru.monica.attachments.model.AttachmentError
import takagi.ru.monica.attachments.model.AttachmentSource

/**
 * 附件子系统的结构化日志工具。
 *
 * 设计原则（对应 requirements.md Requirement 10.2）：
 * - 只记录 `attachmentId`、`source`、`errorKind`、`httpStatus` 等元数据；
 * - **禁止** 打印 `fileName`、`wrappedCek`、`localPath`、明文字节；
 * - 成功事件走 [Log.INFO]，失败事件走 [Log.WARN]，不主动上报堆栈（仅在 Throwable 可读时记录 class 名）。
 *
 * 调用方统一通过 [logUpload]/[logDownload]/[logPreview]/[logDelete]/[logReconcile]，
 * 减少散落在各 executor 里的 Log.d 调用。
 */
internal object AttachmentLogger {

    private const val TAG = "Attachments"

    // ---------------------------------------------------------------- 事件
    enum class Event {
        UPLOAD,
        DOWNLOAD,
        PREVIEW,
        EXPORT,
        DELETE,
        RECONCILE,
        CLEANUP,
        CLONE
    }

    fun logOk(
        event: Event,
        attachmentId: Long?,
        source: AttachmentSource,
        extras: Map<String, Any?> = emptyMap()
    ) {
        Log.i(TAG, buildLine("ok", event, attachmentId, source, extras))
    }

    fun logFailure(
        event: Event,
        attachmentId: Long?,
        source: AttachmentSource?,
        error: Throwable,
        httpStatus: Int? = null,
        extras: Map<String, Any?> = emptyMap()
    ) {
        val kind = resolveErrorKind(error)
        val merged = buildMap<String, Any?> {
            putAll(extras)
            put("error", kind)
            error::class.simpleName?.let { put("errorClass", it) }
            if (httpStatus != null) put("httpStatus", httpStatus)
        }
        Log.w(TAG, buildLine("fail", event, attachmentId, source, merged))
    }

    // ---------------------------------------------------------------- 内部

    private fun buildLine(
        outcome: String,
        event: Event,
        attachmentId: Long?,
        source: AttachmentSource?,
        extras: Map<String, Any?>
    ): String {
        val builder = StringBuilder()
        builder.append("[$outcome]")
        builder.append(" event=").append(event.name.lowercase())
        if (attachmentId != null) {
            builder.append(" id=").append(attachmentId)
        }
        if (source != null) {
            builder.append(" source=").append(source.name.lowercase())
        }
        for ((k, v) in extras) {
            if (v == null) continue
            builder.append(' ').append(sanitize(k)).append('=').append(sanitize(v.toString()))
        }
        return builder.toString()
    }

    /** 防御性处理：只保留 ASCII 可打印字符，去掉换行、引号、路径分隔符。 */
    private fun sanitize(raw: String): String {
        if (raw.isEmpty()) return raw
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            if (ch in ' '..'~' && ch != '"' && ch != '\'' && ch != ' ') {
                // 保留非空格可打印字符；单独允许下划线和 `-.:=/,`
                sb.append(ch)
            } else if (ch == '_' || ch == '-' || ch == '.' || ch == ':' || ch == '=' ||
                ch == '/' || ch == ',' || ch == '[' || ch == ']') {
                sb.append(ch)
            } else {
                sb.append('?')
            }
        }
        return sb.toString()
    }

    private fun resolveErrorKind(error: Throwable): String = when (error) {
        is AttachmentError.TooLarge -> "too_large"
        AttachmentError.QuotaExceeded -> "quota_exceeded"
        AttachmentError.PremiumRequired -> "premium_required"
        AttachmentError.Offline -> "offline"
        is AttachmentError.NetworkError -> "network_error"
        AttachmentError.CryptoError -> "crypto_error"
        AttachmentError.IoError -> "io_error"
        AttachmentError.KdbxLocked -> "kdbx_locked"
        AttachmentError.KdbxCapacityExceeded -> "kdbx_capacity_exceeded"
        else -> "unknown"
    }
}
