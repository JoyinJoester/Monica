package takagi.ru.monica.ui.screens

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportDocumentSpec(
    val fileName: String,
    val mimeType: String
)

fun exportDocumentSpec(
    selectedOption: ExportOption,
    currentTimeMillis: Long = System.currentTimeMillis()
): ExportDocumentSpec {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(currentTimeMillis))
    val fileName = when (selectedOption) {
        ExportOption.ZIP_BACKUP -> "monica_backup_${timestamp}.zip"
        ExportOption.KDBX -> "monica_${timestamp}.kdbx"
    }

    val mimeType = when {
        fileName.endsWith(".zip") -> "application/zip"
        fileName.endsWith(".kdbx") -> "application/octet-stream"
        else -> "*/*"
    }

    return ExportDocumentSpec(fileName, mimeType)
}
