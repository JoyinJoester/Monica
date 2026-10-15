package takagi.ru.monica.attachments.storage

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.attachments.crypto.AttachmentCryptoStreams
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * 附件本地密文存储：
 *
 * - 目录：`<filesDir>/secure_attachments/<uuid>.enc`
 * - 每次 [writeEncrypted] 生成新的 UUID + CEK + IV，调用方拿到 [EncryptedBlobResult] 再把
 *   `wrappedCek` 包裹后写库。
 * - 文件名不含任何用户语义，满足 requirements.md Requirement 2.6。
 *
 * 这里只关心"把 InputStream 流式加密到一个磁盘文件，同时计算明文 SHA-256 和字节数"
 * 和"把一个磁盘文件流式解密回 InputStream"两件事。不负责密钥包裹，也不碰 Room。
 *
 * 所有 IO 都走 [Dispatchers.IO]。
 */
class AttachmentStorage(private val context: Context, private val scope: Scope = Scope.ENTRIES) {

    enum class Scope(val directory: String) {
        ENTRIES("secure_attachments"), DEVICE_METADATA("device_sync_metadata_attachments")
    }

    data class EncryptedBlobResult(
        /** 相对 `filesDir/secure_attachments/` 的文件名，形如 `<uuid>.enc`。 */
        val relativePath: String,
        /** 未包裹的 CEK，调用方必须立刻包裹后丢弃。 */
        val cek: ByteArray,
        val sizeBytes: Long,
        val sha256Hex: String
    ) {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    private val storageDir: File by lazy {
        File(context.applicationContext.filesDir, scope.directory).also { dir ->
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "Failed to create attachments dir")
            }
        }
    }

    /**
     * 流式把 [source] 的字节加密写入到一个新的 `<uuid>.enc` 文件。
     *
     * 返回值包含生成的 CEK（未包裹）、明文字节数与 SHA-256。调用方负责：
     * 1. 用 [AttachmentKeyVault.wrap] 把 CEK 包裹后写库；
     * 2. 在 CEK 不再需要时用 0 覆盖。
     */
    suspend fun writeEncrypted(source: InputStream): EncryptedBlobResult = withContext(Dispatchers.IO) {
        val uuid = UUID.randomUUID().toString()
        val relative = "$uuid.enc"
        // Room registration happens after encryption. A concurrent housekeeping pass must
        // never interpret a newly written, not-yet-registered blob as an orphan.
        writtenThisProcess.add(relative)
        val target = File(storageDir, relative)
        val cek = AttachmentCryptoStreams.newCek()
        val digest = MessageDigest.getInstance("SHA-256")
        var plainSize = 0L

        try {
            target.outputStream().buffered().use { rawOut ->
                plainSize = AttachmentCryptoStreams.writeChunkedEncrypted(
                    source = source,
                    out = rawOut,
                    cek = cek
                ) { buffer, count ->
                    digest.update(buffer, 0, count)
                }
            }
        } catch (e: Exception) {
            // 写入失败时清理半成品，避免遗留密文
            runCatching { target.delete() }
            throw e
        }

        EncryptedBlobResult(
            relativePath = relative,
            cek = cek,
            sizeBytes = plainSize,
            sha256Hex = digest.digest().joinToString("") { "%02x".format(it) }
        )
    }

    /**
     * 打开一个已存在的 `<uuid>.enc` 文件用于流式解密读取。
     *
     * 调用方必须保证 [cek] 是对应文件的正确 CEK（由 [AttachmentKeyVault.unwrap] 还原）。
     * 返回流会负责读取文件头的 IV；调用方只需要消费解密后的明文字节。
     */
    suspend fun openDecryptedStream(relativePath: String, cek: ByteArray): InputStream =
        withContext(Dispatchers.IO) {
            val file = File(storageDir, relativePath)
            if (!file.exists()) throw IOException("Attachment blob missing: $relativePath")
            val raw = file.inputStream().buffered()
            raw.mark(AttachmentCryptoStreams.chunkedMagicSize())
            val magic = ByteArray(AttachmentCryptoStreams.chunkedMagicSize())
            val magicRead = raw.read(magic)
            if (magicRead == magic.size && AttachmentCryptoStreams.isChunkedMagic(magic)) {
                return@withContext AttachmentCryptoStreams.chunkedDecryptingStream(raw, cek)
            }
            raw.reset()
            val iv = ByteArray(AttachmentCryptoStreams.IV_SIZE)
            if (raw.read(iv) != AttachmentCryptoStreams.IV_SIZE) {
                raw.close()
                throw IOException("Attachment header truncated")
            }
            AttachmentCryptoStreams.decryptingStream(raw, cek, iv)
        }

    suspend fun delete(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(storageDir, relativePath)
        if (!file.exists()) return@withContext true
        file.delete().also {
            if (!it) Log.w(TAG, "Failed to delete attachment blob")
        }
    }

    /** 是否存在该路径下的密文文件。 */
    fun exists(relativePath: String?): Boolean {
        if (relativePath.isNullOrBlank()) return false
        return File(storageDir, relativePath).isFile
    }

    /** 列出 `secure_attachments/` 下所有 `<uuid>.enc` 文件的相对路径。 */
    fun listAllBlobs(): List<String> {
        val dir = storageDir
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.endsWith(".enc") }
            ?.map { it.name }
            ?.toList()
            ?: emptyList()
    }

    /** Only collect leftovers from previous processes; active writers are never GC candidates. */
    fun listOrphanCleanupCandidates(): List<String> = listAllBlobs().filter { name ->
        name !in writtenThisProcess && File(storageDir, name).lastModified() < processStartedAt
    }

    /** 仅供调试/诊断，不应该对外暴露绝对路径给用户代码。 */
    internal fun absolutePathOf(relativePath: String): File = File(storageDir, relativePath)

    companion object {
        private val processStartedAt = System.currentTimeMillis()
        private val writtenThisProcess = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        private const val TAG = "AttachmentStorage"
    }
}
