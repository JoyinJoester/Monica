package takagi.ru.monica.webdav

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.Response
import java.nio.charset.StandardCharsets

/**
 * 在每个出站请求上预先注入 HTTP Basic Authorization 头。
 *
 * 该拦截器解决 sardine-android 默认走 "challenge-then-auth" 流程导致的兼容性问题：
 * OpenList / AList 等实现会把首次无凭据的请求直接计入速率限制，因此我们需要
 * 像 Dart `webdav_client` 那样在首次请求即携带凭据。
 *
 * - 当 [credentials] 为空时不注入任何头部。
 * - 当请求已手动设置 `Authorization` 头（例如上游主动重试 Digest）时不覆盖。
 * - 凭据按 UTF-8 编码后再 Base64，支持非 ASCII 用户名/密码。
 */
class PreemptiveBasicAuthInterceptor(
    @Volatile private var credentials: WebDavCredentials,
) : Interceptor {

    /** 更新内部凭据；线程安全。 */
    fun update(newCredentials: WebDavCredentials) {
        credentials = newCredentials
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val current = credentials
        if (!current.hasValue) {
            return chain.proceed(request)
        }
        if (request.header(HEADER_AUTHORIZATION) != null) {
            return chain.proceed(request)
        }
        val value = buildHeaderValue(current)
        val newRequest = request.newBuilder()
            .header(HEADER_AUTHORIZATION, value)
            .build()
        return chain.proceed(newRequest)
    }

    companion object {
        internal const val HEADER_AUTHORIZATION = "Authorization"

        /** 暴露给测试的辅助方法：构造 `Basic <base64(UTF-8(user:password))>`。 */
        @JvmStatic
        fun buildHeaderValue(credentials: WebDavCredentials): String {
            val raw = "${credentials.username}:${credentials.password}".toByteArray(StandardCharsets.UTF_8)
            val encoded = Base64.encodeToString(raw, Base64.NO_WRAP)
            return "Basic $encoded"
        }
    }
}
