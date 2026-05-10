package takagi.ru.monica.attachments.storage

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * 附件预览的明文临时文件缓存。
 *
 * - 目录：`<cacheDir>/attachment_preview/`
 * - 文件名：`<uuid>.<ext>`（保留原始扩展名用于外部 App 识别）
 * - 通过 FileProvider 暴露给外部 App，10 分钟后由 `purgeExpired` 清理。
 *
 * 对应 requirements.md Requirement 7.2。
 */
class AttachmentPreviewCache(private val context: Context) {

    private val cacheDir: File by lazy {
        File(context.applicationContext.cacheDir, DIR_NAME).also { dir ->
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "Failed to create preview cache dir: ${dir.absolutePath}")
            }
        }
    }

    /**
     * 把 [source] 的明文字节写入新预览文件，返回该文件。
     *
     * 调用方负责在需要时通过 FileProvider 把它包装成 content URI。
     */
    suspend fun materialize(fileName: String, source: InputStream): File = withContext(Dispatchers.IO) {
        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").let {
            if (it.isBlank() || it.length > 8) "bin" else it.lowercase()
        }
        val target = File(cacheDir, "${UUID.randomUUID()}.$ext")
        target.outputStream().buffered().use { out ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = source.read(buffer)
                if (read <= 0) break
                out.write(buffer, 0, read)
            }
        }
        target
    }

    /** 清理超过 [ttlMillis] 的预览临时文件。 */
    suspend fun purgeExpired(ttlMillis: Long = DEFAULT_TTL_MS) = withContext(Dispatchers.IO) {
        val threshold = System.currentTimeMillis() - ttlMillis
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < threshold) {
                if (!file.delete()) {
                    Log.w(TAG, "Failed to delete expired preview: ${file.name}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "AttachmentPreviewCache"
        private const val DIR_NAME = "attachment_preview"
        const val DEFAULT_TTL_MS: Long = 10 * 60 * 1000L // 10 分钟
    }
}
