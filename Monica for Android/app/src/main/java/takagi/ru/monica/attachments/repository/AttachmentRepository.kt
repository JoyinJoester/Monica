package takagi.ru.monica.attachments.repository

import kotlinx.coroutines.flow.Flow
import takagi.ru.monica.attachments.data.AttachmentDao
import takagi.ru.monica.attachments.model.Attachment
import takagi.ru.monica.attachments.model.AttachmentDownloadState
import takagi.ru.monica.attachments.model.AttachmentSource

/**
 * 附件元数据仓库，对 ViewModel / Facade 暴露 flow 优先、挂起风格的 API。
 *
 * 这里只负责 Room 层的读写事务，不处理加密、网络或 Bitwarden/KeePass 特化逻辑，
 * 那些职责在 storage / executor / facade 子包中。
 */
class AttachmentRepository(
    private val dao: AttachmentDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    // ---------------------------------------------------------------- 读

    fun observeByPassword(passwordId: Long): Flow<List<Attachment>> =
        dao.observeActiveByParent(passwordId)

    suspend fun listByPassword(passwordId: Long, includeDeleted: Boolean = false): List<Attachment> =
        if (includeDeleted) dao.getAllByParent(passwordId) else dao.getActiveByParent(passwordId)

    suspend fun getById(id: Long): Attachment? = dao.getById(id)

    suspend fun getByBitwardenAttachmentId(attachmentId: String): Attachment? =
        dao.findByBitwardenAttachmentId(attachmentId)

    suspend fun listByParentAndSource(passwordId: Long, source: AttachmentSource): List<Attachment> =
        dao.getByParentAndSource(passwordId, source.name)

    suspend fun countActive(passwordId: Long): Int = dao.countActiveByParent(passwordId)

    /** 返回 [passwordIds] 中存在未软删附件的 id 集合。 */
    suspend fun idsWithActiveAttachments(passwordIds: List<Long>): Set<Long> {
        if (passwordIds.isEmpty()) return emptySet()
        return dao.parentsWithActiveAttachments(passwordIds).toSet()
    }

    /** 返回当前数据库里所有仍被引用的本地密文文件相对路径。 */
    suspend fun allReferencedLocalPaths(): Set<String> =
        dao.selectAllLocalPaths().filterNotNull().toSet()

    /** 列出所有"本地已下载 + 未软删除"的附件（用于备份 / 迁移）。 */
    suspend fun listAllActiveLocalAttachments(): List<Attachment> =
        dao.selectAllActiveLocalAttachments()

    /**
     * 把某密码下 [fromSource] 的附件改写为 LOCAL（清 Bitwarden/KeePass 专属字段）。
     * 返回影响的行数。
     */
    suspend fun convertSourceToLocal(
        passwordId: Long,
        fromSource: AttachmentSource
    ): Int {
        if (fromSource == AttachmentSource.LOCAL) return 0
        return dao.rewriteSourceToLocal(
            passwordId = passwordId,
            fromSource = fromSource.name,
            now = clock()
        )
    }

    // ---------------------------------------------------------------- 写

    suspend fun insert(attachment: Attachment): Long = dao.insert(
        attachment.copy(
            createdAt = if (attachment.createdAt == 0L) clock() else attachment.createdAt,
            updatedAt = if (attachment.updatedAt == 0L) clock() else attachment.updatedAt
        )
    )

    suspend fun update(attachment: Attachment): Int =
        dao.update(attachment.copy(updatedAt = clock()))

    suspend fun markDownloadState(id: Long, state: AttachmentDownloadState) =
        dao.updateDownloadState(id, state.name, clock())

    // ---------------------------------------------------------------- 删

    /** 永久删除单条附件的元数据，不处理底层密文文件的清理。 */
    suspend fun deleteById(id: Long): Int = dao.deleteById(id)

    /** 永久清空某个密码的所有附件元数据，用于密码永久删除级联。 */
    suspend fun purgeByPassword(passwordId: Long): Int = dao.purgeByParent(passwordId)

    /** 随密码软删除一并软删附件（仅元数据标记，不释放密文）。 */
    suspend fun softDeleteByPassword(passwordId: Long): Int {
        val now = clock()
        return dao.softDeleteByParent(passwordId, deletedAt = now, updatedAt = now)
    }

    /** 与密码从回收站恢复联动。 */
    suspend fun restoreByPassword(passwordId: Long): Int =
        dao.restoreByParent(passwordId, updatedAt = clock())
}
