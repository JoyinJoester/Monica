package takagi.ru.monica.perf

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object MainThreadStallMonitor {
    private const val TAG = "MonicaPerfWatchdog"
    private const val CHECK_INTERVAL_MS = 1_000L
    private const val STALL_THRESHOLD_MS = 2_500L
    private const val RELOG_INTERVAL_MS = 10_000L

    private val started = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainThread = Looper.getMainLooper().thread
    private val lastHeartbeatAt = AtomicLong(SystemClock.uptimeMillis())
    private val lastWarningAt = AtomicLong(0L)
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "monica-main-watchdog").apply { isDaemon = true }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return

        val heartbeat = object : Runnable {
            override fun run() {
                lastHeartbeatAt.set(SystemClock.uptimeMillis())
                mainHandler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }
        mainHandler.post(heartbeat)

        executor.scheduleAtFixedRate(
            ::checkMainThread,
            CHECK_INTERVAL_MS,
            CHECK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun checkMainThread() {
        val now = SystemClock.uptimeMillis()
        val blockedForMs = now - lastHeartbeatAt.get()
        if (blockedForMs < STALL_THRESHOLD_MS) return

        val previousWarningAt = lastWarningAt.get()
        if (now - previousWarningAt < RELOG_INTERVAL_MS) return
        if (!lastWarningAt.compareAndSet(previousWarningAt, now)) return

        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)
        val maxMb = runtime.maxMemory() / (1024L * 1024L)
        val mainTop = mainThread.stackTrace
            .take(8)
            .joinToString(separator = " <- ") { frame ->
                "${frame.className}.${frame.methodName}:${frame.lineNumber}"
            }
        Log.w(
            TAG,
            "main_thread_stall blockedForMs=$blockedForMs heapUsedMb=$usedMb heapMaxMb=$maxMb top=$mainTop"
        )
    }
}
