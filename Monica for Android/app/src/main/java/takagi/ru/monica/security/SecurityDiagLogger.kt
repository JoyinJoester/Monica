package takagi.ru.monica.security

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import takagi.ru.monica.BuildConfig

/**
 * 安全链路持久化诊断日志。
 * 用于分身/解锁问题排障，避免依赖系统 logcat。
 */
object SecurityDiagLogger {

    private const val LOG_DIR_NAME = "security_logs"
    private const val LOG_FILE_NAME = "security_diag_v1.log"
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
                val file = File(logDir, LOG_FILE_NAME)
                persistentLogFile = file
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val header = buildString {
                    appendLine("=== Monica Security Diag Session ===")
                    appendLine("session_start=$time")
                    appendLine("app_version=${BuildConfig.FULL_VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("app_display_version=${BuildConfig.VERSION_NAME}")
                    appendLine("build_time=${BuildConfig.BUILD_TIME}")
                    appendLine("git_sha=${BuildConfig.GIT_SHA}")
                    appendLine("android_api=${Build.VERSION.SDK_INT}")
                    appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("===")
                }
                file.appendText(header)
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

        val header = "=== security diag log rotated at $time ==="
        val output = buildString {
            appendLine(header)
            tail.forEach { appendLine(it) }
        }
        file.writeText(output)
    }

    private fun sanitize(text: String): String {
        return text
            .replace(Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"), "***@***.com")
            .replace(Regex("\\b[A-Za-z0-9]{28,}\\b"), "***TOKEN***")
            .replace(Regex("[A-Za-z0-9+/]{40,}={0,2}"), "***TOKEN***")
    }
}
