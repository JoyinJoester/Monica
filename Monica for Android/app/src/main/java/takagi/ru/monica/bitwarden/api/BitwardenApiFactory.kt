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
    
    // 官方服务端点（US）
    const val OFFICIAL_VAULT_URL = "https://vault.bitwarden.com"
    const val OFFICIAL_IDENTITY_URL = "https://identity.bitwarden.com"
    const val OFFICIAL_API_URL = "https://api.bitwarden.com"

    // 官方服务端点（EU）
    const val OFFICIAL_EU_VAULT_URL = "https://vault.bitwarden.eu"
    const val OFFICIAL_EU_IDENTITY_URL = "https://identity.bitwarden.eu"
    const val OFFICIAL_EU_API_URL = "https://api.bitwarden.eu"
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }
    
    enum class HeaderProfile {
        MONICA_DEFAULT,
        KEYGUARD_FALLBACK
    }

    private data class HeaderSpec(
        val majorVersion: String,
        val fullVersion: String
    )

    // Monica 当前默认请求指纹
    private const val MONICA_CHROME_MAJOR_VERSION = "131"
    private const val MONICA_CHROME_FULL_VERSION = "$MONICA_CHROME_MAJOR_VERSION.0.6778.140"
    // Keyguard 当前使用的请求指纹
    private const val KEYGUARD_CHROME_MAJOR_VERSION = "126"
    private const val KEYGUARD_CHROME_FULL_VERSION = "$KEYGUARD_CHROME_MAJOR_VERSION.0.6478.114"
    
    /**
     * 创建 OkHttp 客户端
     * 
     * @param enableLogging 是否启用日志 (仅用于调试)
     */
    fun createOkHttpClient(
        enableLogging: Boolean = false,
        refererUrl: String? = null,
        headerProfile: HeaderProfile = HeaderProfile.MONICA_DEFAULT
    ): OkHttpClient {
        val headerSpec = getHeaderSpec(headerProfile)
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // 添加 Keyguard 使用的 Cloudflare 绕过 headers
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                
                builder.header("User-Agent", buildUserAgent(headerSpec.fullVersion))
                builder.header("Keyguard-Client", "1")
                builder.header("Accept-Language", java.util.Locale.getDefault().toLanguageTag())
                builder.header("Sec-Ch-Ua", """"Not.A/Brand";v="8", "Chromium";v="${headerSpec.majorVersion}"""")
                builder.header("Sec-Ch-Ua-Mobile", "?0")
                builder.header("Sec-Ch-Ua-Platform", "Linux")
                if (isRefererApplied(headerProfile, refererUrl)) {
                    builder.header("referer", ensureTrailingSlash(refererUrl!!.trim()))
                }
                
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

    fun headerProfileName(profile: HeaderProfile): String = when (profile) {
        HeaderProfile.MONICA_DEFAULT -> "monica_default"
        HeaderProfile.KEYGUARD_FALLBACK -> "keyguard_fallback"
    }

    fun headerProfileUserAgentVersion(profile: HeaderProfile): String {
        val spec = getHeaderSpec(profile)
        return "Chrome/${spec.fullVersion}"
    }

    fun isRefererApplied(profile: HeaderProfile, refererUrl: String?): Boolean {
        val normalized = refererUrl?.trim()?.takeIf { it.isNotBlank() } ?: return false
        return when (profile) {
            HeaderProfile.MONICA_DEFAULT -> true
            HeaderProfile.KEYGUARD_FALLBACK -> {
                val officialUs = isOfficialServer(normalized)
                val officialEu = isOfficialEuServer(normalized)
                !(officialUs || officialEu)
            }
        }
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

        return when {
            isOfficialEuServer(normalizedUrl) -> {
                ServerUrls(
                    vault = OFFICIAL_EU_VAULT_URL,
                    identity = OFFICIAL_EU_IDENTITY_URL,
                    api = OFFICIAL_EU_API_URL
                )
            }

            isOfficialServer(normalizedUrl) -> {
            ServerUrls(
                vault = OFFICIAL_VAULT_URL,
                identity = OFFICIAL_IDENTITY_URL,
                api = OFFICIAL_API_URL
            )
            }

            else -> {
            ServerUrls(
                vault = normalizedUrl,
                identity = "$normalizedUrl/identity",
                api = "$normalizedUrl/api"
            )
            }
        }
    }
    
    /**
     * 检查是否为官方服务
     */
    fun isOfficialServer(url: String): Boolean {
        val normalized = url.lowercase().trimEnd('/')
        return normalized == OFFICIAL_VAULT_URL.lowercase() ||
               normalized.contains("bitwarden.com")
    }

    /**
     * 检查是否为官方 EU 服务
     */
    fun isOfficialEuServer(url: String): Boolean {
        val normalized = url.lowercase().trimEnd('/')
        return normalized == OFFICIAL_EU_VAULT_URL.lowercase() ||
            normalized.contains("bitwarden.eu")
    }
    
    private fun ensureTrailingSlash(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }

    private fun buildUserAgent(chromeFullVersion: String): String {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeFullVersion Safari/537.36"
    }

    private fun getHeaderSpec(profile: HeaderProfile): HeaderSpec {
        return when (profile) {
            HeaderProfile.MONICA_DEFAULT -> HeaderSpec(
                majorVersion = MONICA_CHROME_MAJOR_VERSION,
                fullVersion = MONICA_CHROME_FULL_VERSION
            )

            HeaderProfile.KEYGUARD_FALLBACK -> HeaderSpec(
                majorVersion = KEYGUARD_CHROME_MAJOR_VERSION,
                fullVersion = KEYGUARD_CHROME_FULL_VERSION
            )
        }
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

    // 缓存 API 客户端实例
    private val okHttpClientCache = mutableMapOf<String, OkHttpClient>()
    private val identityApiCache = mutableMapOf<String, BitwardenIdentityApi>()
    private val vaultApiCache = mutableMapOf<String, BitwardenVaultApi>()

    private fun getOrCreateOkHttpClient(
        refererUrl: String?,
        headerProfile: BitwardenApiFactory.HeaderProfile
    ): OkHttpClient {
        val cacheKey = "${headerProfile.name}|${refererUrl?.trim().orEmpty()}"
        return okHttpClientCache.getOrPut(cacheKey) {
            BitwardenApiFactory.createOkHttpClient(
                enableLogging = true,
                refererUrl = refererUrl,
                headerProfile = headerProfile
            )
        }
    }
    
    /**
     * 获取 Identity API 客户端
     */
    fun getIdentityApi(
        identityUrl: String,
        refererUrl: String? = null,
        headerProfile: BitwardenApiFactory.HeaderProfile = BitwardenApiFactory.HeaderProfile.MONICA_DEFAULT
    ): BitwardenIdentityApi {
        val cacheKey = "${identityUrl.trimEnd('/')}|${refererUrl?.trim().orEmpty()}|${headerProfile.name}"
        return identityApiCache.getOrPut(cacheKey) {
            BitwardenApiFactory.createIdentityApi(
                baseUrl = identityUrl,
                okHttpClient = getOrCreateOkHttpClient(refererUrl, headerProfile)
            )
        }
    }
    
    /**
     * 获取 Vault API 客户端
     */
    fun getVaultApi(
        apiUrl: String,
        refererUrl: String? = null,
        headerProfile: BitwardenApiFactory.HeaderProfile = BitwardenApiFactory.HeaderProfile.MONICA_DEFAULT
    ): BitwardenVaultApi {
        val cacheKey = "${apiUrl.trimEnd('/')}|${refererUrl?.trim().orEmpty()}|${headerProfile.name}"
        return vaultApiCache.getOrPut(cacheKey) {
            BitwardenApiFactory.createVaultApi(
                baseUrl = apiUrl,
                okHttpClient = getOrCreateOkHttpClient(refererUrl, headerProfile)
            )
        }
    }
    
    /**
     * 清除缓存的客户端
     */
    fun clearCache() {
        okHttpClientCache.clear()
        identityApiCache.clear()
        vaultApiCache.clear()
    }
}
