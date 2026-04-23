package takagi.ru.monica.plus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class PlusLicenseApiService(
    baseUrl: String,
    private val client: OkHttpClient = createDefaultClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
) {
    private val normalizedBaseUrls = baseUrl
        .split(',', ';', '|', '\n', '\r')
        .map { it.trim().trimEnd('/') }
        .filter { it.startsWith("https://") || it.startsWith("http://") }
        .distinct()

    fun isConfigured(): Boolean {
        return normalizedBaseUrls.isNotEmpty()
    }

    suspend fun activate(request: PlusActivateRequest): PlusApiCallResult {
        val body = json.encodeToString(request)
        return postJson("/activate", body)
    }

    suspend fun verify(request: PlusVerifyRequest): PlusApiCallResult {
        val body = json.encodeToString(request)
        return postJson("/verify", body)
    }

    private suspend fun postJson(path: String, bodyJson: String): PlusApiCallResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext PlusApiCallResult.Failure(
                message = "Cloudflare 校验地址未配置",
                code = "CONFIG_MISSING"
            )
        }

        val networkErrors = mutableListOf<String>()
        val endpointPath = path.trimStart('/')

        normalizedBaseUrls.forEachIndexed { index, base ->
            val url = "$base/$endpointPath"
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    val result = parseResponse(response.code, response.isSuccessful, responseBody)

                    if (result is PlusApiCallResult.Failure &&
                        response.code in 500..599 &&
                        index < normalizedBaseUrls.lastIndex
                    ) {
                        networkErrors += "${base} -> HTTP ${response.code}"
                        return@forEachIndexed
                    }

                    return@withContext result
                }
            } catch (e: Exception) {
                networkErrors += "${base} -> ${e.message ?: "network error"}"
            }
        }

        val mergedError = networkErrors.joinToString(separator = " | ").take(MAX_NETWORK_ERROR_MESSAGE)
        return@withContext PlusApiCallResult.Failure(
            message = if (mergedError.isBlank()) {
                "网络请求失败"
            } else {
                "网络请求失败：$mergedError"
            }
            ,
            code = "NETWORK_ERROR"
        )
    }

    private fun parseResponse(statusCode: Int, isSuccessful: Boolean, rawBody: String): PlusApiCallResult {
        val payload = runCatching {
            json.decodeFromString(PlusLicenseResponse.serializer(), rawBody)
        }.getOrNull()

        if (isSuccessful) {
            if (payload != null) {
                return PlusApiCallResult.Success(payload = payload, statusCode = statusCode)
            }
            return PlusApiCallResult.Failure(
                message = "服务器返回了无法解析的数据",
                statusCode = statusCode,
                code = "INVALID_RESPONSE"
            )
        }

        val errorPayload = runCatching {
            json.decodeFromString(PlusApiErrorResponse.serializer(), rawBody)
        }.getOrNull()

        val message = payload?.error
            ?: payload?.message
            ?: errorPayload?.error
            ?: errorPayload?.message
            ?: "请求失败（$statusCode）"

        val errorCode = payload?.code ?: errorPayload?.code

        return PlusApiCallResult.Failure(
            message = message,
            statusCode = statusCode,
            code = errorCode
        )
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_NETWORK_ERROR_MESSAGE = 280

        val DOH_MEDIA_TYPE = "application/dns-message".toMediaType()

        val DOH_BOOTSTRAP_HOSTS = listOf(
            InetAddress.getByName("223.5.5.5"),
            InetAddress.getByName("223.6.6.6"),
            InetAddress.getByName("119.29.29.29"),
            InetAddress.getByName("1.12.12.12"),
            InetAddress.getByName("1.1.1.1")
        )

        fun createDefaultClient(): OkHttpClient {
            val baseClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build()

            val dohDns = createDohDns(baseClient)
            if (dohDns == null) {
                return baseClient
            }

            return baseClient.newBuilder()
                .dns(FallbackDns(listOf(dohDns, Dns.SYSTEM)))
                .build()
        }

        private fun createDohDns(baseClient: OkHttpClient): Dns? {
            val endpoints = listOf(
                "https://dns.alidns.com/dns-query",
                "https://doh.pub/dns-query"
            )

            for (endpoint in endpoints) {
                val dns = runCatching {
                    DnsOverHttps.Builder()
                        .client(baseClient)
                        .url(endpoint.toHttpUrl())
                        .post(true)
                        .includeIPv6(false)
                        .resolvePrivateAddresses(false)
                        .bootstrapDnsHosts(*DOH_BOOTSTRAP_HOSTS.toTypedArray())
                        .build()
                }.getOrNull()

                if (dns != null) {
                    return dns
                }
            }

            return null
        }
    }

    private class FallbackDns(
        private val delegates: List<Dns>
    ) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val errors = mutableListOf<String>()

            delegates.forEach { dns ->
                try {
                    val resolved = dns.lookup(hostname)
                    if (resolved.isNotEmpty()) {
                        return resolved
                    }
                } catch (e: IOException) {
                    errors += e.message ?: "io-error"
                } catch (e: Exception) {
                    errors += e.message ?: "unexpected-error"
                }
            }

            throw UnknownHostException("$hostname: ${errors.joinToString("; ")}")
        }
    }
}
