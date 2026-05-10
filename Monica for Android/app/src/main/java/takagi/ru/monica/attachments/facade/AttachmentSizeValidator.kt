package takagi.ru.monica.attachments.facade

import takagi.ru.monica.attachments.model.AttachmentError
import takagi.ru.monica.attachments.model.AttachmentSource

/**
 * 附件字节大小的上下限校验。
 *
 * - LOCAL / KEEPASS：没有硬上限，但超过 [KEEPASS_SOFT_LIMIT_BYTES] 时需要用户二次确认
 *   （本地存储会占用 Monica 自身目录；kdbx 受设备可用内存约束）。
 * - BITWARDEN：硬上限 100 MB（服务端规则）。
 *
 * 使用方式：
 * ```
 * when (val r = AttachmentSizeValidator.validate(size, source, userAcceptedSoftLimit = false)) {
 *     is Result.Ok        -> proceed()
 *     is Result.TooLarge  -> showError(...)
 *     is Result.NeedsConfirm -> showSoftLimitDialog(...)
 * }
 * ```
 */
object AttachmentSizeValidator {

    const val HARD_LIMIT_BYTES: Long = 100L * 1024L * 1024L
    const val KEEPASS_SOFT_LIMIT_BYTES: Long = 256L * 1024L * 1024L

    sealed class Result {
        data object Ok : Result()
        data class TooLarge(val limitBytes: Long, val actualBytes: Long) : Result()
        /** 仅在目标为 LOCAL / KEEPASS 且超过软上限时返回，需要用户二次确认。 */
        data class NeedsConfirm(val softLimitBytes: Long, val actualBytes: Long) : Result()
    }

    fun validate(
        sizeBytes: Long,
        source: AttachmentSource,
        userAcceptedSoftLimit: Boolean = false
    ): Result {
        if (sizeBytes < 0) return Result.TooLarge(HARD_LIMIT_BYTES, sizeBytes)
        return when (source) {
            AttachmentSource.BITWARDEN -> {
                // Bitwarden 服务端要求单附件 ≤ 100 MB
                if (sizeBytes > HARD_LIMIT_BYTES) Result.TooLarge(HARD_LIMIT_BYTES, sizeBytes)
                else Result.Ok
            }
            AttachmentSource.LOCAL, AttachmentSource.KEEPASS -> when {
                sizeBytes <= KEEPASS_SOFT_LIMIT_BYTES -> Result.Ok
                userAcceptedSoftLimit -> Result.Ok
                else -> Result.NeedsConfirm(KEEPASS_SOFT_LIMIT_BYTES, sizeBytes)
            }
        }
    }

    /** 把 [Result] 折叠为 [AttachmentError]，仅当明确是 TooLarge 时才产生错误。 */
    fun Result.toErrorOrNull(): AttachmentError? = when (this) {
        Result.Ok -> null
        is Result.TooLarge -> AttachmentError.TooLarge(limitBytes, actualBytes)
        is Result.NeedsConfirm -> null
    }
}
