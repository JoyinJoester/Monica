package takagi.ru.monica.sync

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicLong

object SyncDiagnostics {
    private const val TAG = "MonicaSyncDiag"
    private val sequence = AtomicLong(0)

    fun nextTaskId(prefix: String): String {
        val value = sequence.incrementAndGet()
        return "$prefix-${now()}-$value"
    }

    fun now(): Long = runCatching {
        SystemClock.elapsedRealtime()
    }.getOrElse {
        System.currentTimeMillis()
    }

    fun queued(
        taskId: String,
        target: String,
        trigger: String,
        detail: String? = null
    ) {
        log("queued", taskId, target, trigger, null, detail)
    }

    fun start(
        taskId: String,
        target: String,
        trigger: String,
        detail: String? = null
    ): Long {
        val startedAt = now()
        log("start", taskId, target, trigger, null, detail)
        return startedAt
    }

    fun skipped(
        taskId: String,
        target: String,
        trigger: String,
        reason: String,
        startedAt: Long? = null,
        detail: String? = null
    ) {
        log("skip", taskId, target, trigger, startedAt, "reason=$reason ${detail.orEmpty()}".trim())
    }

    fun success(
        taskId: String,
        target: String,
        trigger: String,
        startedAt: Long,
        detail: String? = null
    ) {
        log("success", taskId, target, trigger, startedAt, detail)
    }

    fun failed(
        taskId: String,
        target: String,
        trigger: String,
        startedAt: Long?,
        error: Throwable,
        detail: String? = null
    ) {
        val errorDetail = "error=${safeError(error)} ${detail.orEmpty()}".trim()
        log("failed", taskId, target, trigger, startedAt, errorDetail)
    }

    fun blocked(
        taskId: String,
        target: String,
        trigger: String,
        reason: String,
        startedAt: Long? = null,
        detail: String? = null
    ) {
        log("blocked", taskId, target, trigger, startedAt, "reason=$reason ${detail.orEmpty()}".trim())
    }

    private fun log(
        event: String,
        taskId: String,
        target: String,
        trigger: String,
        startedAt: Long?,
        detail: String?
    ) {
        val elapsed = startedAt?.let { " elapsedMs=${(now() - it).coerceAtLeast(0)}" }.orEmpty()
        val cleanDetail = detail?.takeIf { it.isNotBlank() }?.let { " detail=${sanitize(it)}" }.orEmpty()
        val message = "event=$event taskId=${sanitize(taskId)} target=${sanitize(target)} " +
            "trigger=${sanitize(trigger)}$elapsed thread=${Thread.currentThread().name}$cleanDetail"
        runCatching {
            Log.i(TAG, message)
        }.onFailure {
            println("$TAG: $message")
        }
    }

    private fun safeError(error: Throwable): String {
        val type = error::class.java.simpleName.ifBlank { "Throwable" }
        val message = error.message?.let(::sanitize).orEmpty()
        return if (message.isBlank()) type else "$type:$message"
    }

    private fun sanitize(value: String): String {
        return value
            .replace(Regex("https?://\\S+"), "<url>")
            .replace(
                Regex("(?i)(access[_-]?token|refresh[_-]?token|password|secret|totp|otp|code|key)\\s*[=:]\\s*[^\\s,;]+"),
                "$1=<redacted>"
            )
            .replace(Regex("\\s+"), " ")
            .take(240)
    }
}
