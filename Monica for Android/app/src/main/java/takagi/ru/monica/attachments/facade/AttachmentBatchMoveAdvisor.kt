package takagi.ru.monica.attachments.facade

import takagi.ru.monica.attachments.repository.AttachmentRepository

/**
 * 批量把本地密码移动到 Bitwarden 时的分类器。
 *
 * 对应 requirements.md Requirement 8：
 * - 免费 Bitwarden 账户不能同步附件，因此选中集里凡是带附件的条目，
 *   应该走"在 Bitwarden 新建不含附件的副本，本地保留"的 Copy_Instead_Of_Move 策略；
 * - 付费账户直接走普通移动 + 移动后重新上传附件（fullMoveWithAttachments）；
 * - 没有附件的条目不论 premium 状态都正常移动（plainMove）。
 *
 * 该 Advisor 不执行移动本身，仅把"哪些 id 走哪条路径"告诉调用方。
 */
class AttachmentBatchMoveAdvisor(
    private val attachmentRepository: AttachmentRepository
) {

    data class Classification(
        /** 没有附件的条目，直接普通移动。 */
        val plainMove: List<Long>,
        /** 带附件且 Bitwarden 账户为免费 → 仅在 Bitwarden 新建副本、本地保留。 */
        val copyInsteadOfMove: List<Long>,
        /** 带附件且 Bitwarden 账户为付费 → 正常移动 + 移动后重新上传附件。 */
        val fullMoveWithAttachments: List<Long>
    ) {
        val totalSelected: Int get() = plainMove.size + copyInsteadOfMove.size + fullMoveWithAttachments.size
        val hasAttachmentAwareBranch: Boolean get() = copyInsteadOfMove.isNotEmpty()
    }

    suspend fun classify(
        selectedPasswordIds: List<Long>,
        bitwardenPremium: Boolean
    ): Classification {
        if (selectedPasswordIds.isEmpty()) {
            return Classification(emptyList(), emptyList(), emptyList())
        }
        val withAttachments = attachmentRepository.idsWithActiveAttachments(selectedPasswordIds)
        val plain = mutableListOf<Long>()
        val copy = mutableListOf<Long>()
        val full = mutableListOf<Long>()

        for (id in selectedPasswordIds) {
            if (id in withAttachments) {
                if (bitwardenPremium) full += id else copy += id
            } else {
                plain += id
            }
        }
        return Classification(
            plainMove = plain,
            copyInsteadOfMove = copy,
            fullMoveWithAttachments = full
        )
    }
}
