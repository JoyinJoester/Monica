package takagi.ru.monica.attachments.facade

import takagi.ru.monica.attachments.model.AttachmentError

/**
 * Free / Plus 账户的附件数量配额。
 *
 * - Monica Free（`isPlusActivated = false`）：每个密码最多 10 个未软删附件。
 * - Monica Plus：不限制数量。
 *
 * 降级到 Free 后不会自动删除已有附件（见 Requirement 4.4），只会阻止继续新增。
 *
 * 对应 requirements.md Requirement 4。
 */
object AttachmentQuotaPolicy {

    /** Free 账户单条密码附件上限。 */
    const val FREE_ATTACHMENT_QUOTA: Int = 10

    /**
     * @param existingActiveCount 该密码目前未软删除的附件数量。
     * @param isPlusActivated    当前账户是否已激活 Monica Plus。
     * @return null 表示允许新增；否则返回应抛出的错误。
     */
    fun check(existingActiveCount: Int, isPlusActivated: Boolean): AttachmentError? {
        if (isPlusActivated) return null
        return if (existingActiveCount >= FREE_ATTACHMENT_QUOTA) AttachmentError.QuotaExceeded else null
    }
}
