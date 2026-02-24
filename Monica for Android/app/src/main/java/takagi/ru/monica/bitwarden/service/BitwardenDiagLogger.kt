package takagi.ru.monica.bitwarden.service

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bitwarden 登录诊断持久化日志。
 *
 * 目标：即使系统 logcat 被截断，也能通过开发者日志导出拿到完整诊断链路。
 */
object BitwardenDiagLogger {

    private const val LOG_DIR_NAME = "bitwarden_logs"
    private const val LOG_FILE_NAME = "bitwarden_diag_v1.log"
    private const val MAX_LOG_FILE_BYTES = 1024 * 1024L
    private const val ROTATE_KEEP_LINES = 4000

    private val fileLock = Any()
    @Volatile
    private var persistentLogFile: File? = null

    fun initialize(context: Context) {
        if (persistentLogFile != null) return
        synchronized(fileLock) {
            if (persistentLogFile != null) return
            runCatching {
                val logDir = File(context.applicationContext.filesDir, LOG_DIR_NAME)
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                persistentLogFile = File(logDir, LOG_FILE_NAME)
            }
        }
    }

    fun append(rawLine: String) {
        val file = persistentLogFile ?: return
        synchronized(fileLock) {
            runCatching {
                if (file.exists() && file.length() > MAX_LOG_FILE_BYTES) {
                    rotate(file)
                }
                file.appendText(sanitize(rawLine).trimEnd() + "\n")
            }
        }
    }

    fun exportPersistedLogs(maxEntries: Int = 2000): String {
        val file = persistentLogFile ?: return ""
        if (!file.exists()) return ""
        return synchronized(fileLock) {
            runCatching {
                file.readLines()
                    .takeLast(maxEntries.coerceAtLeast(1))
                    .joinToString(separator = "\n")
            }.getOrDefault("")
        }
    }

    fun clear() {
        synchronized(fileLock) {
            runCatching {
                persistentLogFile?.let { file ->
                    if (file.exists()) {
                        file.writeText("")
                    }
                }
            }
        }
    }

    private fun rotate(file: File) {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val tail = runCatching {
            file.readLines().takeLast(ROTATE_KEEP_LINES)
        }.getOrElse { emptyList() }

        val header = "=== bitwarden diag log rotated at $time ==="
        val output = buildString {
            appendLine(header)
            tail.forEach { appendLine(it) }
        }
        file.writeText(output)
    }

    private fun sanitize(text: String): String {
        return text
            .replace(
                Regex("(password|pwd|passwd)[\"']?\\s*[:=]\\s*[\"']?([^\"',}\\s]+)", RegexOption.IGNORE_CASE),
                "$1=***"
            )
            .replace(
                Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"),
                "***@***.com"
            )
            .replace(Regex("\\b[A-Za-z0-9]{28,}\\b"), "***TOKEN***")
    }
}

