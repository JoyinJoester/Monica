package takagi.ru.monica.service

import android.net.Uri
import java.net.IDN
import java.util.Locale

object BrowserAutofillContextStore {
    private const val MAX_DOMAIN_AGE_MS = 15_000L

    data class Snapshot(
        val packageName: String,
        val domain: String,
        val updatedAt: Long,
    )

    @Volatile
    private var latestSnapshot: Snapshot? = null

    @Synchronized
    fun update(packageName: String, rawUrlOrDomain: String) {
        val normalizedPackage = packageName.trim()
        val normalizedDomain = normalizeDomain(rawUrlOrDomain) ?: return
        latestSnapshot = Snapshot(
            packageName = normalizedPackage,
            domain = normalizedDomain,
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun getRecentDomain(packageName: String, maxAgeMs: Long = MAX_DOMAIN_AGE_MS): String? {
        val snapshot = latestSnapshot ?: return null
        if (!snapshot.packageName.equals(packageName.trim(), ignoreCase = true)) return null
        if (System.currentTimeMillis() - snapshot.updatedAt > maxAgeMs) return null
        return snapshot.domain
    }

    private fun normalizeDomain(rawValue: String): String? {
        val candidate = rawValue.trim()
        if (candidate.isBlank()) return null

        val host = runCatching {
            val parsed = Uri.parse(candidate)
            when {
                !parsed.host.isNullOrBlank() -> parsed.host
                candidate.contains("://") -> null
                else -> Uri.parse("https://$candidate").host
            }
        }.getOrNull() ?: return null

        val asciiHost = runCatching { IDN.toASCII(host.trim().trimEnd('.')) }.getOrNull() ?: return null
        val normalized = asciiHost.lowercase(Locale.ROOT)
        return normalized.takeIf { it.isNotBlank() }
    }
}
