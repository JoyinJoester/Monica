package takagi.ru.monica.lansync

import android.content.Context
import android.os.Build
import takagi.ru.monica.BuildConfig
import java.io.File
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Persist only operation names and counters; never pairing links, keys or content. */
internal object DeviceSyncDiagLogger {
    private const val LIMIT = 64 * 1024
    private val writer = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(256),
        { task -> Thread(task, "device-sync-diagnostics").apply { isDaemon = true } }, ThreadPoolExecutor.DiscardOldestPolicy())
    @Volatile private var file: File? = null
    private val word = Regex("[A-Za-z0-9_.-]{1,96}")
    private val scannerFields = setOf("label", "event", "requested_formats", "generation", "formats", "error", "duration_ms",
        "streaming", "count", "reason", "frames_started", "rotation", "skipped", "matches", "barcodes", "candidates",
        "invalid", "empty", "failures", "uptime_ms", "frames_completed", "refocus", "restarts", "processing", "last_frame_age_ms", "matched")

    @Synchronized fun initialize(context: Context) {
        val target = File(context.applicationContext.filesDir, "diagnostics/device-sync.log")
        if (file == target) return
        file = target
        append("session app_version=${BuildConfig.VERSION_NAME} api=${Build.VERSION.SDK_INT}")
    }

    private fun safe(value: String) = value.takeIf(word::matches) ?: "unknown"

    fun failure(stage: String, code: String, kind: String, attempts: Int, operation: String = "") =
        append("failure stage=${safe(stage)} error=${safe(code)} kind=${safe(kind)} attempts=${attempts.coerceAtLeast(0)} operation=${safe(operation)}")

    fun event(name: String) = append("event=${safe(name)}")

    fun scanner(message: String) {
        if (!message.startsWith("qr_scan ")) return
        val fields = message.split(' ').drop(1).mapNotNull { field ->
            val key = field.substringBefore('=')
            val value = field.substringAfter('=', "")
            if (key in scannerFields && word.matches(value)) "$key=$value" else null
        }
        append("qr_scan " + fields.joinToString(" "))
    }

    private fun append(message: String) {
        val target = file ?: return
        writer.execute {
            runCatching {
                target.parentFile?.mkdirs()
                if (target.exists() && target.length() > LIMIT) target.writeText(target.readLines().takeLast(160).joinToString("\n") + "\n")
                target.appendText("${Instant.now()} $message\n")
            }
        }
    }

    fun exportPersistedLogs(): String = runCatching {
        writer.submit<String> { file?.takeIf(File::exists)?.readText()?.takeLast(LIMIT).orEmpty() }.get(2, TimeUnit.SECONDS)
    }.getOrDefault("Device sync diagnostics unavailable")

    fun clear() { writer.execute { runCatching { file?.writeText("") } } }
}
