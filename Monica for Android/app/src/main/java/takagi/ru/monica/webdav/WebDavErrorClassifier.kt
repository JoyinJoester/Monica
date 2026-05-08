package takagi.ru.monica.webdav

import com.thegrizzlylabs.sardineandroid.impl.SardineException
import kotlinx.coroutines.TimeoutCancellationException
import org.xmlpull.v1.XmlPullParserException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/**
 * 将 WebDAV 调用抛出的各种异常归一为 [WebDavErrorKind]，便于上层 UI 展示
 * 本地化错误消息、以及 [takagi.ru.monica.workers.AutoBackupWorker] 做调度决策。
 *
 * 返回的 [ClassifiedError] 同时携带建议等待时长（仅针对 RateLimited）。
 */
object WebDavErrorClassifier {

    /**
     * 分类结果：包含错误种类与可选的建议等待毫秒数。
     *
     * @property kind 错误分类。
     * @property retryAfterMillis 对 [WebDavErrorKind.RateLimited] 场景，
     *   服务器（或本地 backoff）建议的等待时长；其他分类为 null。
     * @property cause 原始异常引用，便于日志保留调用栈。
     */
    data class ClassifiedError(
        val kind: WebDavErrorKind,
        val retryAfterMillis: Long? = null,
        val cause: Throwable? = null,
    )

    /**
     * 将任意 [Throwable] 归为 [WebDavErrorKind]。
     *
     * `null` 视作成功返回。
     */
    fun classify(error: Throwable?): ClassifiedError {
        if (error == null) return ClassifiedError(WebDavErrorKind.Ok)

        return when (val unwrapped = unwrap(error)) {
            is RateLimitedIOException -> ClassifiedError(
                kind = WebDavErrorKind.RateLimited,
                retryAfterMillis = unwrapped.retryAfterMillis,
                cause = unwrapped,
            )
            is SardineException -> classifySardine(unwrapped)
            is SocketTimeoutException,
            is TimeoutCancellationException,
            is TimeoutException -> ClassifiedError(WebDavErrorKind.Timeout, cause = unwrapped)
            is UnknownHostException,
            is NoRouteToHostException,
            is ConnectException -> ClassifiedError(WebDavErrorKind.NetworkUnreachable, cause = unwrapped)
            is XmlPullParserException -> ClassifiedError(WebDavErrorKind.MalformedResponse, cause = unwrapped)
            else -> {
                // 某些 IO 异常信息里也能识别出速率限制或超时：启发式兜底
                val msg = unwrapped.message.orEmpty()
                when {
                    msg.contains("429", ignoreCase = true) ||
                        msg.contains("Too Many Requests", ignoreCase = true) ->
                        ClassifiedError(WebDavErrorKind.RateLimited, cause = unwrapped)
                    msg.contains("timeout", ignoreCase = true) ->
                        ClassifiedError(WebDavErrorKind.Timeout, cause = unwrapped)
                    msg.contains("Unable to resolve host", ignoreCase = true) ||
                        msg.contains("failed to connect", ignoreCase = true) ->
                        ClassifiedError(WebDavErrorKind.NetworkUnreachable, cause = unwrapped)
                    else -> ClassifiedError(WebDavErrorKind.Unknown, cause = unwrapped)
                }
            }
        }
    }

    /**
     * 将原始 URL 字符串归一为日志/错误消息里应呈现的规范化形式。
     */
    fun formatUrl(raw: String): String = WebDavUrlBuilder.normalizeServer(raw)

    private fun classifySardine(e: SardineException): ClassifiedError {
        return when (e.statusCode) {
            401, 403 -> ClassifiedError(WebDavErrorKind.AuthFailed, cause = e)
            404 -> ClassifiedError(WebDavErrorKind.NotFound, cause = e)
            405, 501 -> ClassifiedError(WebDavErrorKind.MethodNotAllowed, cause = e)
            429 -> ClassifiedError(WebDavErrorKind.RateLimited, cause = e)
            503 -> ClassifiedError(WebDavErrorKind.RateLimited, cause = e)
            else -> ClassifiedError(WebDavErrorKind.Unknown, cause = e)
        }
    }

    private fun unwrap(t: Throwable): Throwable {
        var current: Throwable = t
        var depth = 0
        while (depth < 8) {
            val cause = current.cause ?: return current
            if (cause === current) return current
            if (current is RateLimitedIOException || current is SardineException) return current
            current = cause
            depth++
        }
        return current
    }
}
