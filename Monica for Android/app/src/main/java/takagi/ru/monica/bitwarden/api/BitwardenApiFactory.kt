package takagi.ru.monica.bitwarden.api

import kotlinx.serialization.json.Json
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import takagi.ru.monica.data.bitwarden.BitwardenVault
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

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
        headerProfile: HeaderProfile = HeaderProfile.MONICA_DEFAULT,
        tlsConfig: BitwardenTlsConfig? = null
    ): OkHttpClient {
        val headerSpec = getHeaderSpec(headerProfile)
        val builder = OkHttpClient.Builder()
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

        configureTls(builder, tlsConfig)
        return builder.build()
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

    private fun configureTls(
        builder: OkHttpClient.Builder,
        tlsConfig: BitwardenTlsConfig?
    ) {
        if (tlsConfig == null || tlsConfig.isEmpty()) return

        val trustManager = buildTrustManager(tlsConfig.caCertificatePem)
        val keyManagers = buildClientKeyManagers(
            enabled = tlsConfig.mtlsEnabled,
            pkcs12Base64 = tlsConfig.clientCertPkcs12Base64,
            password = tlsConfig.clientCertPassword
        )

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(keyManagers, arrayOf<TrustManager>(trustManager), SecureRandom())
        builder.sslSocketFactory(sslContext.socketFactory, trustManager)
    }

    private fun buildTrustManager(caCertificatePem: String?): X509TrustManager {
        val systemTrustManager = systemDefaultTrustManager()
        if (caCertificatePem.isNullOrBlank()) return systemTrustManager

        val customTrustManager = customCaTrustManager(caCertificatePem)
        return CompositeX509TrustManager(listOf(systemTrustManager, customTrustManager))
    }

    private fun systemDefaultTrustManager(): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private fun customCaTrustManager(caCertificatePem: String): X509TrustManager {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificates = certificateFactory.generateCertificates(
            ByteArrayInputStream(caCertificatePem.toByteArray(Charsets.UTF_8))
        )

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null)
        certificates.forEachIndexed { index, cert ->
            keyStore.setCertificateEntry("ca_$index", cert)
        }

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(keyStore)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private fun buildClientKeyManagers(
        enabled: Boolean,
        pkcs12Base64: String?,
        password: String?
    ): Array<KeyManager>? {
        if (!enabled) return null
        if (pkcs12Base64.isNullOrBlank()) return null

        val passwordChars = password.orEmpty().toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        val certBytes = Base64.decode(pkcs12Base64, Base64.DEFAULT)
        keyStore.load(ByteArrayInputStream(certBytes), passwordChars)

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, passwordChars)
        return kmf.keyManagers
    }

    private class CompositeX509TrustManager(
        private val delegates: List<X509TrustManager>
    ) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            var lastError: Exception? = null
            delegates.forEach { manager ->
                try {
                    manager.checkClientTrusted(chain, authType)
                    return
                } catch (e: Exception) {
                    lastError = e
                }
            }
            throw lastError ?: IllegalStateException("No trust manager accepted client certificate")
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            var lastError: Exception? = null
            delegates.forEach { manager ->
                try {
                    manager.checkServerTrusted(chain, authType)
                    return
                } catch (e: Exception) {
                    lastError = e
                }
            }
            throw lastError ?: IllegalStateException("No trust manager accepted server certificate")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return delegates
                .flatMap { it.acceptedIssuers.toList() }
                .distinctBy { it.subjectX500Principal.name + it.serialNumber }
                .toTypedArray()
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

    /**
     * 获取 Identity API 客户端
     */
    fun getIdentityApi(
        identityUrl: String,
        refererUrl: String? = null,
        headerProfile: BitwardenApiFactory.HeaderProfile = BitwardenApiFactory.HeaderProfile.MONICA_DEFAULT,
        tlsConfig: BitwardenTlsConfig? = null
    ): BitwardenIdentityApi {
        val cacheKey = "${identityUrl.trimEnd('/')}|${refererUrl?.trim().orEmpty()}|${headerProfile.name}|${tlsConfig?.cacheFingerprint().orEmpty()}"
        return identityApiCache.getOrPut(cacheKey) {
            BitwardenApiFactory.createIdentityApi(
                baseUrl = identityUrl,
                okHttpClient = getOrCreateOkHttpClient(refererUrl, headerProfile, tlsConfig)
            )
        }
    }
    
    /**
     * 获取 Vault API 客户端
     */
    fun getVaultApi(
        apiUrl: String,
        refererUrl: String? = null,
        headerProfile: BitwardenApiFactory.HeaderProfile = BitwardenApiFactory.HeaderProfile.MONICA_DEFAULT,
        tlsConfig: BitwardenTlsConfig? = null
    ): BitwardenVaultApi {
        val cacheKey = "${apiUrl.trimEnd('/')}|${refererUrl?.trim().orEmpty()}|${headerProfile.name}|${tlsConfig?.cacheFingerprint().orEmpty()}"
        return vaultApiCache.getOrPut(cacheKey) {
            BitwardenApiFactory.createVaultApi(
                baseUrl = apiUrl,
                okHttpClient = getOrCreateOkHttpClient(refererUrl, headerProfile, tlsConfig)
            )
        }
    }

    fun getVaultApi(
        vault: BitwardenVault,
        headerProfile: BitwardenApiFactory.HeaderProfile = BitwardenApiFactory.HeaderProfile.MONICA_DEFAULT
    ): BitwardenVaultApi {
        val tlsConfig = BitwardenTlsConfig(
            certificateAlias = vault.tlsCertificateAlias,
            caCertificatePem = vault.tlsCaCertificatePem,
            mtlsEnabled = vault.tlsMtlsEnabled,
            clientCertPkcs12Base64 = vault.tlsClientCertPkcs12Base64,
            clientCertPassword = vault.tlsEncryptedClientCertPassword
        )
        return getVaultApi(
            apiUrl = vault.apiUrl,
            refererUrl = vault.serverUrl,
            headerProfile = headerProfile,
            tlsConfig = tlsConfig
        )
    }
    
    /**
     * 清除缓存的客户端
     */
    fun clearCache() {
        okHttpClientCache.clear()
        identityApiCache.clear()
        vaultApiCache.clear()
    }

    private fun getOrCreateOkHttpClient(
        refererUrl: String?,
        headerProfile: BitwardenApiFactory.HeaderProfile,
        tlsConfig: BitwardenTlsConfig?
    ): OkHttpClient {
        val cacheKey = "${headerProfile.name}|${refererUrl?.trim().orEmpty()}|${tlsConfig?.cacheFingerprint().orEmpty()}"
        return okHttpClientCache.getOrPut(cacheKey) {
            BitwardenApiFactory.createOkHttpClient(
                enableLogging = true,
                refererUrl = refererUrl,
                headerProfile = headerProfile,
                tlsConfig = tlsConfig
            )
        }
    }
}
