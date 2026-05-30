package takagi.ru.monica.attachments.facade

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import takagi.ru.monica.attachments.executor.BitwardenAttachmentExecutor
import takagi.ru.monica.attachments.executor.KeePassAttachmentExecutor
import takagi.ru.monica.attachments.executor.LocalAttachmentExecutor
import takagi.ru.monica.attachments.model.Attachment
import takagi.ru.monica.attachments.model.AttachmentDownloadState
import takagi.ru.monica.attachments.model.AttachmentError
import takagi.ru.monica.attachments.model.AttachmentSource
import takagi.ru.monica.attachments.repository.AttachmentRepository
import takagi.ru.monica.attachments.storage.AttachmentKeyVault
import takagi.ru.monica.attachments.storage.AttachmentPreviewCache
import takagi.ru.monica.attachments.storage.AttachmentStorage
import takagi.ru.monica.attachments.util.AttachmentLogger
import takagi.ru.monica.bitwarden.api.BitwardenVaultApi
import takagi.ru.monica.bitwarden.api.CipherAttachmentApiData
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey
import takagi.ru.monica.data.PasswordEntryDao
import takagi.ru.monica.repository.MdbxVaultStore
import java.io.File
import java.io.OutputStream

/**
 * 附件子系统对 ViewModel 的唯一入口。
 *
 * Facade 不自己持有账户状态（Plus、Bitwarden premium、kdbx 解锁态等），这些由调用方在
 * [UploadRequest] 或 [BitwardenContext] 中显式传入，避免把附件逻辑与业务模块互锁。
 *
 * 职责分工：
 * - Quota / Size 校验：[AttachmentQuotaPolicy]、[AttachmentSizeValidator]
 * - 字节加解密：executor 层（Local / Bitwarden / KeePass）
 * - Room 事务：通过 [AttachmentRepository]
 *
 * 对应 requirements.md Requirement 4 / 5 / 6 / 7 / 10。
 */
class AttachmentFacade(
    private val context: Context,
    private val repository: AttachmentRepository,
    private val localExecutor: LocalAttachmentExecutor,
    private val bitwardenExecutor: BitwardenAttachmentExecutor,
    private val keepassExecutor: KeePassAttachmentExecutor,
    private val storage: AttachmentStorage,
    private val keyVault: AttachmentKeyVault,
    private val previewCache: AttachmentPreviewCache,
    private val passwordEntryDao: PasswordEntryDao? = null,
    private val mdbxVaultStore: MdbxVaultStore? = null,
    /** 用于 `openForPreview` 发出的 FileProvider authority，需与 manifest 中注册的 authority 一致。 */
    private val fileProviderAuthority: String
) {

    /**
     * 业务侧发起上传时的最小上下文。
     *
     * [source] 决定走哪个 executor：
     * - LOCAL：自足；[bitwardenContext]/[keepassContext] 应为 null。
     * - BITWARDEN：必须提供 [bitwardenContext]；另外需要 [bitwardenPremium] = true（免费账户会被直接拒绝）。
     * - KEEPASS：必须提供 [keepassContext]。
     */
    data class UploadRequest(
        val parentPasswordId: Long,
        val source: AttachmentSource,
        val uri: Uri,
        val isPlusActivated: Boolean,
        val bitwardenPremium: Boolean = true,
        val kdbxSoftLimitAccepted: Boolean = false,
        val bitwardenContext: BitwardenContext? = null,
        val keepassContext: KeePassContext? = null
    )

    data class BitwardenContext(
        val vaultApi: BitwardenVaultApi,
        val httpClient: OkHttpClient,
        val accessToken: String,
        val cipherId: String,
        /** 用于包裹/解包附件密钥的 cipher 或 user key。 */
        val wrappingKey: SymmetricCryptoKey,
        val isOnline: Boolean
    )

    data class KeePassContext(
        val databaseId: Long,
        val entryUuid: String
    )

    // ---------------------------------------------------------------- 查询

    fun observeByPassword(passwordId: Long): Flow<List<Attachment>> =
        repository.observeByPassword(passwordId)

    suspend fun listByPassword(passwordId: Long): List<Attachment> =
        repository.listByPassword(passwordId)

    suspend fun getById(id: Long): Attachment? = repository.getById(id)

    // ---------------------------------------------------------------- 添加

    /**
     * 添加附件的统一入口。
     *
     * 所有错误都会以 [AttachmentError] 形式抛出，调用方可以直接翻译成 UI 文案。
     */
    suspend fun addAttachment(request: UploadRequest): Attachment = withContext(Dispatchers.IO) {
        // 1. Quota（仅本地/Bitwarden/KeePass 都一致地受 Plus 限制）
        val existingCount = repository.countActive(request.parentPasswordId)
        AttachmentQuotaPolicy.check(existingCount, request.isPlusActivated)?.let { throw it }

        // 2. Size / 类型上限
        val meta = AttachmentUriMetadata.resolve(context, request.uri)
        if (meta.sizeBytes >= 0) {
            val validation = AttachmentSizeValidator.validate(
                sizeBytes = meta.sizeBytes,
                source = request.source,
                userAcceptedSoftLimit = request.kdbxSoftLimitAccepted
            )
            when (validation) {
                is AttachmentSizeValidator.Result.Ok -> Unit
                is AttachmentSizeValidator.Result.TooLarge ->
                    throw AttachmentError.TooLarge(validation.limitBytes, validation.actualBytes)
                is AttachmentSizeValidator.Result.NeedsConfirm -> {
                    // KeePass 软上限：调用方未 opt-in 时阻断，UI 负责重新调用并置 kdbxSoftLimitAccepted=true
                    throw AttachmentError.TooLarge(validation.softLimitBytes, validation.actualBytes)
                }
            }
        }

        // 3. 按 source 分派
        val attachment = when (request.source) {
            AttachmentSource.LOCAL -> localExecutor.writeFromUri(
                parentPasswordId = request.parentPasswordId,
                sourceUri = request.uri,
                fallbackFileName = meta.fileName
            )
            AttachmentSource.BITWARDEN -> {
                if (!request.bitwardenPremium) throw AttachmentError.PremiumRequired
                val bw = request.bitwardenContext ?: throw AttachmentError.IoError
                if (!bw.isOnline) throw AttachmentError.Offline
                val resolver = context.applicationContext.contentResolver
                val input = resolver.openInputStream(request.uri) ?: throw AttachmentError.IoError
                input.use { stream ->
                    bitwardenExecutor.upload(
                        parentPasswordId = request.parentPasswordId,
                        fileName = meta.fileName,
                        mimeType = meta.mimeType,
                        source = stream,
                        sizeBytes = meta.sizeBytes.takeIf { it >= 0 } ?: 0L,
                        ctx = BitwardenAttachmentExecutor.UploadContext(
                            vaultApi = bw.vaultApi,
                            httpClient = bw.httpClient,
                            accessToken = bw.accessToken,
                            cipherId = bw.cipherId,
                            wrappingKey = bw.wrappingKey
                        )
                    )
                }
            }
            AttachmentSource.KEEPASS -> {
                val kp = request.keepassContext ?: throw AttachmentError.IoError
                val bytes = readAllBytes(request.uri)
                keepassExecutor.upload(
                    parentPasswordId = request.parentPasswordId,
                    databaseId = kp.databaseId,
                    entryUuid = kp.entryUuid,
                    fileName = meta.fileName,
                    mimeType = meta.mimeType,
                    sourceBytes = bytes
                )
            }
        }

        // 4. 持久化（executor 返回的 Attachment 已经是完整记录，Room 只负责 insert）
        val id = repository.insert(attachment)
        attachment.copy(id = id).also { saved ->
            mirrorAttachmentToMdbx(saved)
        }
    }

    // ---------------------------------------------------------------- 读/导出

    /**
     * 确保附件的本地密文缓存存在（若已下载直接返回原记录）。
     *
     * Bitwarden / KeePass 附件在 PENDING 状态下会走对应 executor 的 download。
     */
    suspend fun ensureDownloaded(
        attachmentId: Long,
        bitwardenContext: BitwardenContext? = null,
        keepassContext: KeePassContext? = null
    ): Attachment = withContext(Dispatchers.IO) {
        val existing = repository.getById(attachmentId) ?: throw AttachmentError.IoError
        if (existing.downloadStateEnum == AttachmentDownloadState.DOWNLOADED &&
            existing.localPath != null && storage.exists(existing.localPath)
        ) {
            return@withContext existing
        }
        repository.markDownloadState(attachmentId, AttachmentDownloadState.DOWNLOADING)
        val refreshed: Attachment = try {
            when (existing.sourceEnum) {
                AttachmentSource.LOCAL -> existing.copy(
                    downloadState = AttachmentDownloadState.DOWNLOADED.name
                )
                AttachmentSource.BITWARDEN -> {
                    val bw = bitwardenContext ?: throw AttachmentError.IoError
                    if (!bw.isOnline) throw AttachmentError.Offline
                    val remote = CipherAttachmentApiData(
                        id = existing.bitwardenAttachmentId ?: throw AttachmentError.CryptoError,
                        fileName = existing.fileName,
                        size = existing.sizeBytes.toString(),
                        sizeName = null,
                        key = existing.bitwardenFileKeyEnc,
                        url = existing.bitwardenUrl
                    )
                    bitwardenExecutor.download(
                        existing = existing,
                        remote = remote,
                        vaultApi = bw.vaultApi,
                        httpClient = bw.httpClient,
                        accessToken = bw.accessToken,
                        cipherId = bw.cipherId,
                        wrappingKey = bw.wrappingKey
                    )
                }
                AttachmentSource.KEEPASS -> {
                    val kp = keepassContext ?: throw AttachmentError.IoError
                    keepassExecutor.download(
                        existing = existing,
                        databaseId = kp.databaseId,
                        entryUuid = kp.entryUuid
                    )
                }
            }
        } catch (e: Throwable) {
            repository.markDownloadState(attachmentId, AttachmentDownloadState.FAILED)
            throw e
        }
        repository.update(refreshed)
        refreshed.copy(id = attachmentId)
    }

    /**
     * 把已下载的附件解密为临时明文文件，返回可通过 FileProvider 分享的 content URI。
     *
     * 调用方负责后续 `Intent.ACTION_VIEW` + `FLAG_GRANT_READ_URI_PERMISSION`。
     */
    suspend fun openForPreview(
        attachmentId: Long,
        bitwardenContext: BitwardenContext? = null,
        keepassContext: KeePassContext? = null
    ): Uri = withContext(Dispatchers.IO) {
        val ready = ensureDownloaded(attachmentId, bitwardenContext, keepassContext)
        val file = materializePreview(ready)
        try {
            FileProvider.getUriForFile(context.applicationContext, fileProviderAuthority, file)
        } catch (e: Throwable) {
            AttachmentLogger.logFailure(
                event = AttachmentLogger.Event.PREVIEW,
                attachmentId = attachmentId,
                source = null,
                error = e,
                extras = mapOf("fallback" to "file_uri")
            )
            Uri.fromFile(file)
        }
    }

    /** 把附件明文写到用户选择的目标 URI（例如 SAF 创建的文件）。 */
    suspend fun exportToSystem(
        attachmentId: Long,
        targetUri: Uri,
        bitwardenContext: BitwardenContext? = null,
        keepassContext: KeePassContext? = null
    ) = withContext(Dispatchers.IO) {
        val ready = ensureDownloaded(attachmentId, bitwardenContext, keepassContext)
        val wrapped = ready.wrappedCek ?: throw AttachmentError.CryptoError
        val path = ready.localPath ?: throw AttachmentError.IoError
        val cek = try {
            keyVault.unwrap(wrapped)
        } catch (e: Throwable) {
            throw AttachmentError.CryptoError
        }
        val resolver = context.applicationContext.contentResolver
        val out: OutputStream = resolver.openOutputStream(targetUri)
            ?: throw AttachmentError.IoError
        try {
            val plainStream = storage.openDecryptedStream(path, cek)
            plainStream.use { input ->
                out.use { output -> input.copyTo(output) }
            }
        } finally {
            cek.fill(0)
        }
    }

    // ---------------------------------------------------------------- 删除 / 重试

    suspend fun deleteAttachment(
        attachmentId: Long,
        bitwardenContext: BitwardenContext? = null,
        keepassContext: KeePassContext? = null
    ) = withContext(Dispatchers.IO) {
        val existing = repository.getById(attachmentId) ?: return@withContext
        mirrorAttachmentDeleteToMdbx(existing)
        when (existing.sourceEnum) {
            AttachmentSource.LOCAL -> {
                localExecutor.delete(existing)
            }
            AttachmentSource.BITWARDEN -> {
                val bw = bitwardenContext ?: throw AttachmentError.IoError
                if (!bw.isOnline) throw AttachmentError.Offline
                val attachmentRemoteId = existing.bitwardenAttachmentId
                if (!attachmentRemoteId.isNullOrBlank()) {
                    bitwardenExecutor.remove(
                        vaultApi = bw.vaultApi,
                        accessToken = bw.accessToken,
                        cipherId = bw.cipherId,
                        bitwardenAttachmentId = attachmentRemoteId
                    )
                }
                existing.localPath?.let { storage.delete(it) }
            }
            AttachmentSource.KEEPASS -> {
                val kp = keepassContext ?: throw AttachmentError.IoError
                val ref = existing.keepassBinaryRef
                if (!ref.isNullOrBlank()) {
                    keepassExecutor.remove(
                        databaseId = kp.databaseId,
                        entryUuid = kp.entryUuid,
                        binaryHashHex = ref
                    )
                }
                existing.localPath?.let { storage.delete(it) }
            }
        }
        repository.deleteById(attachmentId)
    }

    suspend fun retryFailed(
        attachmentId: Long,
        bitwardenContext: BitwardenContext? = null,
        keepassContext: KeePassContext? = null
    ): Attachment {
        val existing = repository.getById(attachmentId) ?: throw AttachmentError.IoError
        if (existing.downloadStateEnum == AttachmentDownloadState.DOWNLOADED &&
            existing.localPath != null && storage.exists(existing.localPath)
        ) {
            return existing
        }
        // 把状态先从 FAILED 归位到 PENDING，然后走 ensureDownloaded
        repository.markDownloadState(attachmentId, AttachmentDownloadState.PENDING)
        return ensureDownloaded(attachmentId, bitwardenContext, keepassContext)
    }

    // ---------------------------------------------------------------- 级联

    /**
     * 随密码永久删除一起清理：移除 Room 记录 + 本地密文缓存 + 远端字节。
     *
     * 对于 Bitwarden / KeePass 的远端清理，如果调用方没有传 context（例如密码删除时不持有
     * Bitwarden 会话），这里会只清 Room 与本地缓存，远端的孤儿附件靠后续 `reconcile` 自然对齐。
     */
    suspend fun purgeByPassword(
        passwordId: Long,
        bitwardenContext: BitwardenContext? = null,
        keepassContext: KeePassContext? = null
    ) = withContext(Dispatchers.IO) {
        val items = repository.listByPassword(passwordId, includeDeleted = true)
        for (item in items) {
            when (item.sourceEnum) {
                AttachmentSource.LOCAL -> localExecutor.delete(item)
                AttachmentSource.BITWARDEN -> {
                    val bw = bitwardenContext
                    val remoteId = item.bitwardenAttachmentId
                    if (bw != null && bw.isOnline && !remoteId.isNullOrBlank()) {
                        runCatching {
                            bitwardenExecutor.remove(
                                vaultApi = bw.vaultApi,
                                accessToken = bw.accessToken,
                                cipherId = bw.cipherId,
                                bitwardenAttachmentId = remoteId
                            )
                        }
                    }
                    item.localPath?.let { storage.delete(it) }
                }
                AttachmentSource.KEEPASS -> {
                    val kp = keepassContext
                    val ref = item.keepassBinaryRef
                    if (kp != null && !ref.isNullOrBlank()) {
                        runCatching {
                            keepassExecutor.remove(
                                databaseId = kp.databaseId,
                                entryUuid = kp.entryUuid,
                                binaryHashHex = ref
                            )
                        }
                    }
                    item.localPath?.let { storage.delete(it) }
                }
            }
        }
        repository.purgeByPassword(passwordId)
    }

    suspend fun softDeleteByPassword(passwordId: Long) {
        repository.softDeleteByPassword(passwordId)
    }

    suspend fun restoreByPassword(passwordId: Long) {
        repository.restoreByPassword(passwordId)
    }

    /**
     * 把 [sourcePasswordId] 名下的附件（含本地缓存的 KEEPASS / BITWARDEN 附件）克隆成 LOCAL 附件
     * 挂到 [targetPasswordId] 上。
     *
     * 场景：KeePass / Bitwarden 条目被 COPY 到 Monica 本地后需要把附件带过去。
     * - 只处理 `is_deleted = 0 && local_path != null && wrapped_cek != null` 的源附件；
     * - 每条附件会在 `secure_attachments/` 目录下新建一份独立密文（不与源共享 blob），
     *   避免源/目标之一删除时误伤对方；
     * - 新记录一律以 `source = LOCAL` 存入，清空 bitwarden/keepass 专属字段；
     * - 任一条失败都会继续处理剩余项，失败通过返回值汇报。
     *
     * @return 成功克隆的附件数量。
     */
    suspend fun cloneAttachmentsToNewParent(
        sourcePasswordId: Long,
        targetPasswordId: Long
    ): Int = withContext(Dispatchers.IO) {
        if (sourcePasswordId <= 0 || targetPasswordId <= 0 || sourcePasswordId == targetPasswordId) {
            return@withContext 0
        }
        val sources = repository.listByPassword(sourcePasswordId).filter {
            !it.localPath.isNullOrBlank() && !it.wrappedCek.isNullOrBlank()
        }
        if (sources.isEmpty()) return@withContext 0

        var successCount = 0
        sources.forEach { source ->
            runCatching {
                val plainStream = localExecutor.openDecrypted(source)
                val blob = plainStream.use { storage.writeEncrypted(it) }
                val wrapped = try {
                    keyVault.wrap(blob.cek)
                } catch (e: Throwable) {
                    runCatching { storage.delete(blob.relativePath) }
                    throw AttachmentError.CryptoError
                } finally {
                    blob.cek.fill(0)
                }
                val now = System.currentTimeMillis()
                val cloned = source.copy(
                    id = 0,
                    parentPasswordId = targetPasswordId,
                    source = AttachmentSource.LOCAL.name,
                    localPath = blob.relativePath,
                    wrappedCek = wrapped,
                    sizeBytes = blob.sizeBytes.takeIf { it > 0 } ?: source.sizeBytes,
                    sha256Hex = blob.sha256Hex,
                    bitwardenAttachmentId = null,
                    bitwardenUrl = null,
                    bitwardenFileKeyEnc = null,
                    keepassBinaryRef = null,
                    downloadState = AttachmentDownloadState.DOWNLOADED.name,
                    createdAt = now,
                    updatedAt = now,
                    isDeleted = false,
                    deletedAt = null
                )
                repository.insert(cloned)
                successCount++
            }.onFailure { e ->
                AttachmentLogger.logFailure(
                    event = AttachmentLogger.Event.CLONE,
                    attachmentId = source.id,
                    source = source.sourceEnum,
                    error = e,
                    extras = mapOf("target_password_id" to targetPasswordId)
                )
            }
        }
        successCount
    }

    /**
     * 清理已被 Room 遗弃的本地密文文件（例如通过 `ON DELETE CASCADE` 因密码永久删除
     * 被带走元数据，但磁盘上的 `<uuid>.enc` 没人兜底清理时）。
     *
     * 建议在以下时机调用：
     * - 应用启动一次；
     * - 用户从回收站永久删除密码时；
     * - 备份恢复完成后。
     *
     * @return 实际删除的孤儿文件数量。
     */
    suspend fun purgeOrphanedLocalBlobs(): Int = withContext(Dispatchers.IO) {
        val referenced = repository.allReferencedLocalPaths()
        val onDisk = storage.listAllBlobs()
        var removed = 0
        for (name in onDisk) {
            if (name !in referenced) {
                if (storage.delete(name)) {
                    removed++
                }
            }
        }
        if (removed > 0) {
            AttachmentLogger.logOk(
                event = AttachmentLogger.Event.CLEANUP,
                attachmentId = null,
                source = AttachmentSource.LOCAL,
                extras = mapOf("orphan_blobs_removed" to removed)
            )
        }
        removed
    }

    // ---------------------------------------------------------------- 内部

    private fun readAllBytes(uri: Uri): ByteArray {
        val resolver = context.applicationContext.contentResolver
        val input = resolver.openInputStream(uri) ?: throw AttachmentError.IoError
        return input.use { it.readBytes() }
    }

    private suspend fun mirrorAttachmentToMdbx(attachment: Attachment) {
        val vaultStore = mdbxVaultStore ?: return
        val dao = passwordEntryDao ?: return
        if (attachment.localPath.isNullOrBlank() || attachment.wrappedCek.isNullOrBlank()) return
        val parent = dao.getPasswordEntryById(attachment.parentPasswordId) ?: return
        val databaseId = parent.mdbxDatabaseId ?: return
        val parentEntryId = vaultStore.passwordObjectIdForAttachment(parent)
        runCatching {
            vaultStore.upsertAttachment(databaseId, parentEntryId, attachment)
        }.onFailure { error ->
            AttachmentLogger.logFailure(
                event = AttachmentLogger.Event.UPLOAD,
                attachmentId = attachment.id,
                source = attachment.sourceEnum,
                error = error,
                extras = mapOf("mdbx_database_id" to databaseId)
            )
        }
    }

    private suspend fun mirrorAttachmentDeleteToMdbx(attachment: Attachment) {
        val vaultStore = mdbxVaultStore ?: return
        val dao = passwordEntryDao ?: return
        val parent = dao.getPasswordEntryById(attachment.parentPasswordId) ?: return
        val databaseId = parent.mdbxDatabaseId ?: return
        val parentEntryId = vaultStore.passwordObjectIdForAttachment(parent)
        runCatching {
            vaultStore.deleteAttachment(databaseId, parentEntryId, attachment)
        }.onFailure { error ->
            AttachmentLogger.logFailure(
                event = AttachmentLogger.Event.DELETE,
                attachmentId = attachment.id,
                source = attachment.sourceEnum,
                error = error,
                extras = mapOf("mdbx_database_id" to databaseId)
            )
        }
    }

    private suspend fun materializePreview(attachment: Attachment): File {
        val wrapped = attachment.wrappedCek ?: throw AttachmentError.CryptoError
        val path = attachment.localPath ?: throw AttachmentError.IoError
        val cek = try {
            keyVault.unwrap(wrapped)
        } catch (e: Throwable) {
            throw AttachmentError.CryptoError
        }
        return try {
            val plainStream = storage.openDecryptedStream(path, cek)
            plainStream.use { input ->
                previewCache.materialize(attachment.fileName, input)
            }
        } finally {
            cek.fill(0)
        }
    }

    companion object {
        private const val TAG = "AttachmentFacade"
    }
}

