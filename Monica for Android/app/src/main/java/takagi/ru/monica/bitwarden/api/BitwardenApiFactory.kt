package takagi.ru.monica.bitwarden.api

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Bitwarden API 客户端工厂
 * 
 * 创建针对不同服务器端点的 API 客户端
 * 支持官方服务和自托管服务
 */
object BitwardenApiFactory {
    
    private const val TAG = "BitwardenApiFactory"
    
    // 官方服务端点
    const val OFFICIAL_VAULT_URL = "https://vault.bitwarden.com"
    const val OFFICIAL_IDENTITY_URL = "https://identity.bitwarden.com"
    const val OFFICIAL_API_URL = "https://api.bitwarden.com"
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }
    
    // Chrome 版本号 (用于 Cloudflare 绕过)
    private const val CHROME_MAJOR_VERSION = "131"
    private const val CHROME_FULL_VERSION = "$CHROME_MAJOR_VERSION.0.6778.140"
    
    // 模拟 Windows Chrome User-Agent (与 Keyguard 一致)
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME_FULL_VERSION Safari/537.36"
    
    /**
     * 创建 OkHttp 客户端
     * 
     * @param enableLogging 是否启用日志 (仅用于调试)
     */
    fun createOkHttpClient(enableLogging: Boolean = false): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // 添加 Keyguard 使用的 Cloudflare 绕过 headers
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                
            // 添加 Keyguard 使用的 headers (参考 ServerEnvApi.kt)
                builder.header("User-Agent", USER_AGENT)
                builder.header("Keyguard-Client", "1")
                builder.header("Accept-Language", java.util.Locale.getDefault().toLanguageTag())
                builder.header("Sec-Ch-Ua", """"Not.A/Brand";v="8", "Chromium";v="$CHROME_MAJOR_VERSION"""")
                builder.header("Sec-Ch-Ua-Mobile", "?0")
                builder.header("Sec-Ch-Ua-Platform", "Linux")
                
                chain.proceed(builder.build())
            }
            .apply {
                if (enableLogging) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                }
            }
            .build()
    }
    
    /**
     * 创建 Identity API 客户端 (认证)
     * 
     * @param baseUrl Identity 服务端点
     * @param okHttpClient 可选的自定义 OkHttp 客户端
     */
    fun createIdentityApi(
        baseUrl: String = OFFICIAL_IDENTITY_URL,
        okHttpClient: OkHttpClient = createOkHttpClient()
    ): BitwardenIdentityApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(ensureTrailingSlash(baseUrl))
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        
        return retrofit.create(BitwardenIdentityApi::class.java)
    }
    
    /**
     * 创建 Vault API 客户端 (数据操作)
     * 
     * @param baseUrl API 服务端点
     * @param okHttpClient 可选的自定义 OkHttp 客户端
     */
    fun createVaultApi(
        baseUrl: String = OFFICIAL_API_URL,
        okHttpClient: OkHttpClient = createOkHttpClient()
    ): BitwardenVaultApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(ensureTrailingSlash(baseUrl))
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        
        return retrofit.create(BitwardenVaultApi::class.java)
    }
    
    /**
     * 从 Vault URL 推断其他端点 URL (用于自托管服务)
     * 
     * 自托管服务通常使用以下结构:
     * - Vault: https://your-domain.com
     * - Identity: https://your-domain.com/identity
     * - API: https://your-domain.com/api
     */
    fun inferServerUrls(vaultUrl: String): ServerUrls {
        val normalizedUrl = vaultUrl.trimEnd('/')
        
        return if (isOfficialServer(normalizedUrl)) {
            // 官方服务使用独立端点
            ServerUrls(
                vault = OFFICIAL_VAULT_URL,
                identity = OFFICIAL_IDENTITY_URL,
                api = OFFICIAL_API_URL
            )
        } else {
            // 自托管服务使用路径后缀
            ServerUrls(
                vault = normalizedUrl,
                identity = "$normalizedUrl/identity",
                api = "$normalizedUrl/api"
            )
        }
    }
    
    /**
     * 检查是否为官方服务
     */
    fun isOfficialServer(url: String): Boolean {
        val normalized = url.lowercase().trimEnd('/')
        return normalized == OFFICIAL_VAULT_URL.lowercase() ||
               normalized.contains("bitwarden.com") ||
               normalized.contains("bitwarden.eu")
    }
    
    private fun ensureTrailingSlash(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }
    
    /**
     * 服务器 URL 配置
     */
    data class ServerUrls(
        val vault: String,
        val identity: String,
        val api: String
    )
}

/**
 * Bitwarden API 客户端管理器
 * 
 * 管理多个 Vault 的 API 客户端实例
 */
class BitwardenApiManager {
    
    // 启用日志以调试登录问题
    private val okHttpClient = BitwardenApiFactory.createOkHttpClient(enableLogging = true)
    
    // 缓存 API 客户端实例
    private val identityApiCache = mutableMapOf<String, BitwardenIdentityApi>()
    private val vaultApiCache = mutableMapOf<String, BitwardenVaultApi>()
    
    /**
     * 获取 Identity API 客户端
     */
    fun getIdentityApi(identityUrl: String): BitwardenIdentityApi {
        return identityApiCache.getOrPut(identityUrl) {
            BitwardenApiFactory.createIdentityApi(identityUrl, okHttpClient)
        }
    }
    
    /**
     * 获取 Vault API 客户端
     */
    fun getVaultApi(apiUrl: String): BitwardenVaultApi {
        return vaultApiCache.getOrPut(apiUrl) {
            BitwardenApiFactory.createVaultApi(apiUrl, okHttpClient)
        }
    }
    
    /**
     * 清除缓存的客户端
     */
    fun clearCache() {
        identityApiCache.clear()
        vaultApiCache.clear()
    }
}
