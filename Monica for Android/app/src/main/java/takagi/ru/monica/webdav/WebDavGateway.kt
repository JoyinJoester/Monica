package takagi.ru.monica.webdav

import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * WebDAV 客户端工厂。
 *
 * 统一构造带预置式 Basic Auth、速率限制拦截、User-Agent 注入的
 * [OkHttpSardine] 客户端。调用方（主要是 [takagi.ru.monica.utils.WebDavHelper]）
 * 只需通过该入口构造 sardine 客户端即可获得与 Kazumi (`webdav_client`) 等价的
 * 请求行为。
 */
object WebDavGateway {

    private const val CONNECT_TIMEOUT_SECONDS: Long = 10L
    private const val READ_TIMEOUT_SECONDS: Long = 12L
    private const val WRITE_TIMEOUT_SECONDS: Long = 12L
    private const val CALL_TIMEOUT_SECONDS: Long = 15L

    /**
     * 构造一个已配置好 OkHttp 拦截器链的 sardine 客户端。
     *
     * 重要：不再调用 `sardine.setCredentials(...)`，因为凭据已由
     * [PreemptiveBasicAuthInterceptor] 预置到每个请求中；双重设置反而会让
     * sardine 走 challenge-response 逻辑，与 OpenList 的速率策略冲突。
     */
    fun buildClient(credentials: WebDavCredentials): OkHttpSardine {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(PreemptiveBasicAuthInterceptor(credentials))
            .addInterceptor(RateLimitInterceptor())
            .addInterceptor(UserAgentInterceptor())
            .build()
        return OkHttpSardine(okHttp)
    }

    /** 从任意 URL 字符串中提取 host；若无法解析返回空串。 */
    fun hostOf(url: String): String = WebDavUrlBuilder.hostOf(url)
}
