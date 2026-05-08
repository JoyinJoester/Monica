package takagi.ru.monica.webdav

import okhttp3.Interceptor
import okhttp3.Response
import takagi.ru.monica.BuildConfig

/**
 * 给每个 WebDAV 出站请求贴上稳定的 `User-Agent` 头（含应用版本号），
 * 并在未指定 `Accept-Charset` 时补齐 `utf-8`，对齐 Kazumi (webdav_client) 行为。
 *
 * 仅当请求尚未显式设置对应头时注入，避免覆盖上游自定义。
 */
class UserAgentInterceptor(
    private val userAgent: String = DEFAULT_USER_AGENT,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
        if (request.header(HEADER_USER_AGENT) == null) {
            builder.header(HEADER_USER_AGENT, userAgent)
        }
        if (request.header(HEADER_ACCEPT_CHARSET) == null) {
            builder.header(HEADER_ACCEPT_CHARSET, "utf-8")
        }
        return chain.proceed(builder.build())
    }

    companion object {
        internal const val HEADER_USER_AGENT = "User-Agent"
        internal const val HEADER_ACCEPT_CHARSET = "Accept-Charset"
        val DEFAULT_USER_AGENT: String = "Monica-Android/${BuildConfig.VERSION_NAME}"
    }
}
