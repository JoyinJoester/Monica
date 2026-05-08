package takagi.ru.monica.webdav

import java.io.IOException

/**
 * 表示 OkHttp 请求因本地 backoff 预算未到期而被拒绝发送，
 * 或响应中携带了导致本次请求应当立即终止的 Retry-After 指令。
 *
 * @property retryAfterMillis 建议调用方在多久之后再发起下一次尝试；
 *   `null` 表示由 [WebDavBackoffState] 根据指数退避自行决定。
 */
class RateLimitedIOException(
    message: String,
    val retryAfterMillis: Long? = null,
    cause: Throwable? = null,
) : IOException(message, cause)
