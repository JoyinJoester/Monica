package takagi.ru.monica.attachments.facade

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap

/**
 * 从 SAF [Uri] 提取上传前校验必需的元数据（文件名、字节数、MIME）。
 *
 * 所有字段都是尽力而为；Cursor 不可用或字段缺失时用合理默认（`attachment`、`-1` 表示未知、`application/octet-stream`）。
 * 调用方拿到 `sizeBytes = -1` 时应当视为"未知大小，允许尝试但不保证触发 Size 上限校验"。
 */
object AttachmentUriMetadata {

    data class Metadata(
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long
    )

    fun resolve(context: Context, uri: Uri, fallbackFileName: String? = null): Metadata {
        val resolver = context.applicationContext.contentResolver
        var name: String? = null
        var size: Long = -1L
        runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        name = cursor.getString(nameIdx)
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        val resolvedName = name ?: fallbackFileName ?: DEFAULT_NAME
        val mime = resolver.getType(uri) ?: guessMime(resolvedName)
        return Metadata(
            fileName = resolvedName,
            mimeType = mime,
            sizeBytes = size
        )
    }

    private fun guessMime(fileName: String): String {
        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (ext.isBlank()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private const val DEFAULT_NAME = "attachment"
}
