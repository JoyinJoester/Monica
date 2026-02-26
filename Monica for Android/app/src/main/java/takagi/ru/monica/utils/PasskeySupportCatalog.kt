package takagi.ru.monica.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * 读取 passkeys.json 并提供可登录（signin）站点域名匹配能力。
 */
class PasskeySupportCatalog(
    private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedSigninDomains: List<String>? = null

    suspend fun getSigninDomains(): List<String> {
        cachedSigninDomains?.let { return it }
        return withContext(Dispatchers.IO) {
            cachedSigninDomains?.let { return@withContext it }
            synchronized(this@PasskeySupportCatalog) {
                cachedSigninDomains?.let { return@synchronized it }
                val domains = runCatching { loadSigninDomains() }
                    .getOrDefault(emptyList())
                cachedSigninDomains = domains
                domains
            }
        }
    }

    fun findMatchingDomain(host: String, signinDomains: List<String>): String? {
        val normalizedHost = normalizeDomain(host) ?: return null
        return signinDomains.firstOrNull { domain ->
            normalizedHost == domain || normalizedHost.endsWith(".$domain")
        }
    }

    private fun loadSigninDomains(): List<String> {
        val raw = context.assets.open(PASSKEYS_ASSET_FILE)
            .bufferedReader()
            .use { it.readText() }
        val records = json.decodeFromString<List<PasskeySupportRecord>>(raw)
        return records
            .asSequence()
            .filter { record -> record.features.any { it.equals("signin", ignoreCase = true) } }
            .flatMap { record ->
                sequenceOf(record.domain) + record.additionalDomains.asSequence()
            }
            .mapNotNull(::normalizeDomain)
            .distinct()
            .sortedByDescending { it.length }
            .toList()
    }

    private fun normalizeDomain(raw: String?): String? {
        val value = raw
            ?.trim()
            ?.trimEnd('.')
            ?.lowercase(Locale.US)
            ?.substringBefore(':')
            ?: return null
        return value.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val PASSKEYS_ASSET_FILE = "passkeys.json"
    }
}

@Serializable
private data class PasskeySupportRecord(
    val domain: String = "",
    @SerialName("additional-domains")
    val additionalDomains: List<String> = emptyList(),
    val features: List<String> = emptyList()
)
