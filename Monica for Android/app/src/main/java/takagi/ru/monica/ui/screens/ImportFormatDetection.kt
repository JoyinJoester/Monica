package takagi.ru.monica.ui.screens

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import takagi.ru.monica.R
import takagi.ru.monica.utils.KeePassErrorCode
import takagi.ru.monica.utils.KeePassOperationException

fun isPasswordDecryptError(errorMessage: String): Boolean {
    val normalized = errorMessage.lowercase()
    return normalized.contains("wrong password") ||
        normalized.contains("password incorrect") ||
        normalized.contains("decrypt") ||
        normalized.contains("invalid credentials") ||
        normalized.contains("密码错误") ||
        normalized.contains("解密失败")
}

fun isPasswordRequiredError(errorMessage: String): Boolean {
    val normalized = errorMessage.lowercase()
    return normalized.contains("password required") ||
        normalized.contains("password needed") ||
        normalized.contains("need password")
}

fun formatImportErrorMessage(error: Throwable, fallback: String): String {
    return if (error is KeePassOperationException) {
        val message = error.message.takeIf { !it.isNullOrBlank() } ?: fallback
        "[${error.code.name}] $message"
    } else {
        error.message ?: fallback
    }
}

fun isLikelyLegacyKdbFile(fileName: String?, uri: Uri?): Boolean {
    val candidate = (
        fileName
            ?: uri?.lastPathSegment?.substringAfterLast('/')
            ?: ""
        ).lowercase()
    return candidate.endsWith(".kdb") && !candidate.endsWith(".kdbx")
}

private enum class PasswordKeyboardFileMode {
    PASSWORD,
    NOTE,
    CARD,
    UNKNOWN
}

fun shouldPromptPasswordKeyboardTagDialog(context: Context, uri: Uri): Boolean {
    return when (detectPasswordKeyboardFileMode(context, uri)) {
        PasswordKeyboardFileMode.NOTE -> false
        PasswordKeyboardFileMode.PASSWORD,
        PasswordKeyboardFileMode.CARD,
        PasswordKeyboardFileMode.UNKNOWN -> true
    }
}

private fun detectPasswordKeyboardFileMode(
    context: Context,
    uri: Uri
): PasswordKeyboardFileMode {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                val firstLine = reader.readLine()?.removePrefix("\uFEFF")?.trim().orEmpty()
                val firstFields = parsePasswordKeyboardCsvPreviewFields(firstLine)
                    .map { it.trim().lowercase() }

                if (firstFields.firstOrNull() == "secretinputexportfile") {
                    return@use when (firstFields.getOrNull(1)) {
                        "normal" -> PasswordKeyboardFileMode.NOTE
                        "card" -> PasswordKeyboardFileMode.CARD
                        "password" -> PasswordKeyboardFileMode.PASSWORD
                        else -> PasswordKeyboardFileMode.UNKNOWN
                    }
                }

                val headers = firstFields.toSet()
                val hasCardHeader =
                    (headers.contains("cardno") ||
                        headers.contains("card_no") ||
                        headers.contains("cardnumber") ||
                        headers.contains("card number") ||
                        headers.contains("卡号")) &&
                        headers.contains("title")
                val hasPasswordHeader =
                    headers.contains("username") &&
                        headers.contains("password") &&
                        headers.contains("title")
                val hasNoteHeader =
                    headers.contains("title") &&
                        (headers.contains("remarks") ||
                            headers.contains("remark") ||
                            headers.contains("notes") ||
                            headers.contains("note")) &&
                        !headers.contains("password")

                when {
                    hasPasswordHeader -> PasswordKeyboardFileMode.PASSWORD
                    hasCardHeader -> PasswordKeyboardFileMode.CARD
                    hasNoteHeader -> PasswordKeyboardFileMode.NOTE
                    else -> PasswordKeyboardFileMode.UNKNOWN
                }
            }
        } ?: PasswordKeyboardFileMode.UNKNOWN
    }.getOrElse { PasswordKeyboardFileMode.UNKNOWN }
}

private fun parsePasswordKeyboardCsvPreviewFields(line: String): List<String> {
    if (line.isBlank()) return emptyList()

    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var index = 0

    while (index < line.length) {
        val ch = line[index]
        when (ch) {
            '"' -> {
                if (inQuotes && index + 1 < line.length && line[index + 1] == '"') {
                    current.append('"')
                    index++
                } else {
                    inQuotes = !inQuotes
                }
            }

            ',' -> {
                if (inQuotes) {
                    current.append(ch)
                } else {
                    fields += current.toString().trim()
                    current.clear()
                }
            }

            else -> current.append(ch)
        }
        index++
    }

    fields += current.toString().trim()
    return fields
}

fun keepassImportSuggestion(context: Context, code: KeePassErrorCode): String {
    return when (code) {
        KeePassErrorCode.LEGACY_KDB_UNSUPPORTED ->
            context.getString(R.string.import_data_keepass_tip_legacy_kdb)
        KeePassErrorCode.INVALID_CREDENTIAL ->
            context.getString(R.string.import_data_keepass_tip_invalid_credential)
        KeePassErrorCode.URI_PERMISSION_DENIED ->
            context.getString(R.string.import_data_keepass_tip_permission)
        KeePassErrorCode.KDF_MEMORY_INSUFFICIENT ->
            context.getString(R.string.import_data_keepass_tip_kdf_memory)
        KeePassErrorCode.FORMAT_UNSUPPORTED ->
            context.getString(R.string.import_data_keepass_tip_format_unsupported)
        KeePassErrorCode.IO_READ_WRITE_FAILED ->
            context.getString(R.string.import_data_keepass_tip_io_failed)
    }
}
