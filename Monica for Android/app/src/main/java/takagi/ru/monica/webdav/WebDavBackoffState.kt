package takagi.ru.monica.webdav

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * 按主机追踪 WebDAV 调用的 backoff 状态，作为应用层面的速率限制器。
 *
 * 行为摘要：
 * - `recordRateLimit` 在每次 429 / 503+Retry-After 响应后调用；
 *   若传入 `retryAfterMillis>0` 则严格遵守服务器指示，否则采用指数退避
 *   `min(60s, 1s * 2^(n-1))`，其中 n 为 60 秒窗口内累计触发次数。
 * - 60 秒窗口内累计 ≥3 次 429 会把主机临时禁用 5 分钟。
 * - `recordSuccess` 在业务成功后调用，清除该主机的所有 backoff 状态。
 *
 * 线程安全：外层 [ConcurrentHashMap] + 每个 [HostState] 的 `synchronized` 块。
 *
 * 可选持久化：调用 [attachPersistence] 后，状态会同步到
 * `webdav_backoff` SharedPreferences，使得 WorkManager 进程重启仍能遵守 backoff。
 */
object WebDavBackoffState {

    private const val PREFS_NAME = "webdav_backoff"
    private const val KEY_PREFIX = "host:"
    private const val KEY_BLOCK_UNTIL = ":blockUntil"
    private const val KEY_DISABLE_UNTIL = ":disableUntil"
    private const val KEY_RL_WINDOW = ":rl_window"

    internal const val RATE_LIMIT_WINDOW_MS: Long = 60_000L
    internal const val DISABLE_DURATION_MS: Long = 5 * 60_000L
    internal const val DISABLE_THRESHOLD: Int = 3
    internal const val EXPONENTIAL_BASE_MS: Long = 1_000L
    internal const val EXPONENTIAL_CAP_MS: Long = 60_000L

    private val hosts = ConcurrentHashMap<String, HostState>()

    @Volatile
    private var prefs: SharedPreferences? = null

    /**
     * 把 backoff 状态绑定到 SharedPreferences，保证进程重启后仍可恢复。
     * 只需在应用启动阶段调用一次。
     */
    @Synchronized
    fun attachPersistence(context: Context) {
        if (prefs != null) return
        val sharedPreferences = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sharedPreferences
        restoreFromPrefs(sharedPreferences)
    }

    private fun restoreFromPrefs(prefs: SharedPreferences) {
        val all = prefs.all
        val hostNames = all.keys
            .asSequence()
            .filter { it.startsWith(KEY_PREFIX) }
            .map { it.removePrefix(KEY_PREFIX).substringBefore(':') }
            .distinct()
            .toList()
        for (host in hostNames) {
            val blockUntil = prefs.getLong(KEY_PREFIX + host + KEY_BLOCK_UNTIL, 0L)
            val disableUntil = prefs.getLong(KEY_PREFIX + host + KEY_DISABLE_UNTIL, 0L)
            val window = prefs.getString(KEY_PREFIX + host + KEY_RL_WINDOW, null).orEmpty()
            val timestamps = window
                .split(',')
                .mapNotNull { it.trim().takeIf(String::isNotEmpty)?.toLongOrNull() }
            val state = HostState(blockUntil = blockUntil, disableUntil = disableUntil)
            for (t in timestamps) state.rateLimitTimestamps.addLast(t)
            hosts[host] = state
        }
    }

    /** 记录一次 429 / 503+Retry-After 事件，更新阻塞截止时间。 */
    fun recordRateLimit(
        host: String,
        retryAfterMillis: Long? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        if (host.isEmpty()) return
        val state = hosts.getOrPut(host) { HostState() }
        synchronized(state) {
            // 维护 60 秒窗口
            pruneLocked(state, now)
            state.rateLimitTimestamps.addLast(now)
            val attempt = state.rateLimitTimestamps.size

            val waitMs = if (retryAfterMillis != null && retryAfterMillis > 0L) {
                retryAfterMillis
            } else {
                computeExponentialWait(attempt)
            }
            state.blockUntil = maxOf(state.blockUntil, now + waitMs)

            if (attempt >= DISABLE_THRESHOLD) {
                state.disableUntil = maxOf(state.disableUntil, now + DISABLE_DURATION_MS)
            }
            persistLocked(host, state)
        }
    }

    /** 标记一次业务成功，清除该主机所有 backoff 状态。 */
    fun recordSuccess(host: String) {
        if (host.isEmpty()) return
        val state = hosts[host] ?: return
        synchronized(state) {
            state.blockUntil = 0L
            state.disableUntil = 0L
            state.rateLimitTimestamps.clear()
            clearLocked(host)
        }
    }

    /** 当前主机是否应阻塞请求（尚未到阻塞截止或临时禁用中）。 */
    fun shouldBlock(host: String, now: Long = System.currentTimeMillis()): Boolean {
        if (host.isEmpty()) return false
        val state = hosts[host] ?: return false
        synchronized(state) {
            return now < maxOf(state.blockUntil, state.disableUntil)
        }
    }

    /** 建议等待毫秒数；若无 backoff 返回 0。 */
    fun suggestedWaitMillis(host: String, now: Long = System.currentTimeMillis()): Long {
        if (host.isEmpty()) return 0L
        val state = hosts[host] ?: return 0L
        synchronized(state) {
            val deadline = maxOf(state.blockUntil, state.disableUntil)
            return (deadline - now).coerceAtLeast(0L)
        }
    }

    /** 该主机是否处于 5 分钟临时禁用状态。 */
    fun isTemporarilyDisabled(host: String, now: Long = System.currentTimeMillis()): Boolean {
        if (host.isEmpty()) return false
        val state = hosts[host] ?: return false
        synchronized(state) {
            return now < state.disableUntil
        }
    }

    /** 仅测试用：清空所有内存状态。 */
    internal fun resetForTest() {
        hosts.clear()
        prefs?.edit()?.clear()?.apply()
    }

    internal fun computeExponentialWait(attempt: Int): Long {
        val safeAttempt = attempt.coerceAtLeast(1)
        if (safeAttempt >= 64) return EXPONENTIAL_CAP_MS
        val naive = EXPONENTIAL_BASE_MS shl (safeAttempt - 1)
        return minOf(EXPONENTIAL_CAP_MS, naive)
    }

    private fun pruneLocked(state: HostState, now: Long) {
        while (state.rateLimitTimestamps.isNotEmpty() &&
            now - state.rateLimitTimestamps.first() > RATE_LIMIT_WINDOW_MS
        ) {
            state.rateLimitTimestamps.removeFirst()
        }
    }

    private fun persistLocked(host: String, state: HostState) {
        val p = prefs ?: return
        val window = state.rateLimitTimestamps.joinToString(",")
        p.edit()
            .putLong(KEY_PREFIX + host + KEY_BLOCK_UNTIL, state.blockUntil)
            .putLong(KEY_PREFIX + host + KEY_DISABLE_UNTIL, state.disableUntil)
            .putString(KEY_PREFIX + host + KEY_RL_WINDOW, window)
            .apply()
    }

    private fun clearLocked(host: String) {
        val p = prefs ?: return
        p.edit()
            .remove(KEY_PREFIX + host + KEY_BLOCK_UNTIL)
            .remove(KEY_PREFIX + host + KEY_DISABLE_UNTIL)
            .remove(KEY_PREFIX + host + KEY_RL_WINDOW)
            .apply()
    }

    internal class HostState(
        @Volatile var blockUntil: Long = 0L,
        @Volatile var disableUntil: Long = 0L,
    ) {
        val rateLimitTimestamps: ArrayDeque<Long> = ArrayDeque(16)
    }
}
