package takagi.ru.monica.autofill_ng

import takagi.ru.monica.data.PasswordEntry
import java.net.URL
import java.util.Locale

/**
 * Bitwarden-style matcher:
 * - Prefer exact package/domain matches.
 * - Keep matching deterministic and conservative to reduce false positives.
 */
class BitwardenLikeAutofillMatcherNg {
    data class Config(
        val strictOnly: Boolean = true,
        val allowSubdomainMatch: Boolean = true,
        val allowBaseDomainMatch: Boolean = true,
        val exactDomainOnly: Boolean = false,
        val maxSuggestions: Int = 20,
    )

    private enum class Reason {
        EXACT_PACKAGE,
        EXACT_DOMAIN,
        SUBDOMAIN,
        BASE_DOMAIN,
        PACKAGE_DOMAIN_COMBO,
        EXACT_APP_TITLE,
        PACKAGE_TOKEN_TITLE,
        HEURISTIC_FALLBACK,
    }

    private data class ScoredMatch(
        val entry: PasswordEntry,
        val score: Int,
        val reasons: Set<Reason>,
    )

    fun match(
        entries: List<PasswordEntry>,
        packageName: String,
        webDomain: String?,
        appDisplayName: String? = null,
        config: Config = Config(),
    ): List<PasswordEntry> {
        if (entries.isEmpty()) return emptyList()

        val targetPackage = normalizePackageName(packageName)
        val targetPackageToken = targetPackage
            ?.substringAfterLast('.')
            ?.takeIf { it.length >= 2 }
        val targetHost = normalizeHost(webDomain)
        val targetRoot = targetHost?.let(::extractBaseDomain)
        val targetAppDisplayName = normalizeLabel(appDisplayName)

        val candidates = entries.mapNotNull { entry ->
            scoreEntry(
                entry = entry,
                targetPackage = targetPackage,
                targetPackageToken = targetPackageToken,
                targetHost = targetHost,
                targetRoot = targetRoot,
                targetAppDisplayName = targetAppDisplayName,
                config = config,
            )
        }

        if (candidates.isEmpty()) return emptyList()

        // Deduplicate by entry id and keep higher score.
        val bestByEntry = linkedMapOf<Long, ScoredMatch>()
        candidates.forEach { candidate ->
            val existing = bestByEntry[candidate.entry.id]
            if (existing == null || candidate.score > existing.score) {
                bestByEntry[candidate.entry.id] = candidate
            }
        }

        return bestByEntry.values
            .sortedWith(
                compareByDescending<ScoredMatch> { it.score }
                    .thenByDescending { it.entry.isFavorite }
                    .thenByDescending { it.entry.updatedAt.time },
            )
            .take(config.maxSuggestions.coerceAtLeast(1))
            .map { it.entry }
    }

    private fun scoreEntry(
        entry: PasswordEntry,
        targetPackage: String?,
        targetPackageToken: String?,
        targetHost: String?,
        targetRoot: String?,
        targetAppDisplayName: String?,
        config: Config,
    ): ScoredMatch? {
        val reasons = linkedSetOf<Reason>()
        var score = 0

        val entryPackages = linkedSetOf<String>().apply {
            extractNormalizedPackages(entry.appPackageName).forEach(::add)
            extractAndroidAppPackage(entry.website)?.let(::add)
        }
        val entryHost = normalizeHost(entry.website)
        val entryRoot = entryHost?.let(::extractBaseDomain)

        if (!targetPackage.isNullOrBlank() && entryPackages.contains(targetPackage)) {
            score += 120
            reasons += Reason.EXACT_PACKAGE
        }

        val entryTitle = normalizeLabel(entry.title)
        val entryAppName = normalizeLabel(entry.appName)
        if (!targetAppDisplayName.isNullOrBlank() &&
            (
                entryTitle == targetAppDisplayName ||
                    entryAppName == targetAppDisplayName
                )
        ) {
            score += 95
            reasons += Reason.EXACT_APP_TITLE
        }

        if (!targetPackageToken.isNullOrBlank()) {
            val token = targetPackageToken.lowercase(Locale.ROOT)
            val tokenMatched = entryTitle.contains(token) || entryAppName.contains(token)
            if (tokenMatched) {
                score += 70
                reasons += Reason.PACKAGE_TOKEN_TITLE
            }
        }

        if (!targetHost.isNullOrBlank() && !entryHost.isNullOrBlank()) {
            val hasSubdomainRelation = isSubdomainRelation(entryHost, targetHost)
            when {
                entryHost == targetHost -> {
                    score += 140
                    reasons += Reason.EXACT_DOMAIN
                }

                hasSubdomainRelation && !config.exactDomainOnly && config.allowSubdomainMatch -> {
                    score += 115
                    reasons += Reason.SUBDOMAIN
                }

                hasSubdomainRelation && !config.allowSubdomainMatch -> {
                    // Respect explicit subdomain toggle: do not fall through
                    // to base-domain scoring for strict parent/child host pairs.
                }

                !entryRoot.isNullOrBlank() &&
                    !targetRoot.isNullOrBlank() &&
                    !config.exactDomainOnly &&
                    config.allowBaseDomainMatch &&
                    entryRoot == targetRoot -> {
                    score += 100
                    reasons += Reason.BASE_DOMAIN
                }
            }
        }

        if (Reason.EXACT_PACKAGE in reasons &&
            (
                Reason.EXACT_DOMAIN in reasons ||
                    Reason.SUBDOMAIN in reasons ||
                    Reason.BASE_DOMAIN in reasons
                )
        ) {
            score += 30
            reasons += Reason.PACKAGE_DOMAIN_COMBO
        }

        if (!config.strictOnly && score == 0) {
            score = heuristicFallbackScore(entry, targetPackage, targetHost)
            if (score > 0) {
                reasons += Reason.HEURISTIC_FALLBACK
            }
        }

        if (score <= 0) return null

        if (config.strictOnly) {
            val hasStrongReason = reasons.any {
                it == Reason.EXACT_PACKAGE ||
                    it == Reason.EXACT_DOMAIN ||
                    it == Reason.SUBDOMAIN ||
                    it == Reason.BASE_DOMAIN ||
                    it == Reason.EXACT_APP_TITLE
            }
            if (!hasStrongReason) return null
        }

        return ScoredMatch(entry = entry, score = score, reasons = reasons)
    }

    private fun normalizeLabel(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return value.trim().lowercase(Locale.ROOT)
    }

    private fun heuristicFallbackScore(
        entry: PasswordEntry,
        targetPackage: String?,
        targetHost: String?,
    ): Int {
        val title = entry.title.lowercase(Locale.ROOT)
        val username = entry.username.lowercase(Locale.ROOT)
        val website = entry.website.lowercase(Locale.ROOT)
        val packageName = entry.appPackageName.lowercase(Locale.ROOT)

        val hostToken = targetHost
            ?.substringBefore('.')
            ?.takeIf { it.length >= 3 }
        val packageToken = targetPackage
            ?.substringAfterLast('.')
            ?.takeIf { it.length >= 3 }

        val token = hostToken ?: packageToken ?: return 0

        val haystack = "$title $username $website $packageName"
        return if (haystack.contains(token)) 55 else 0
    }

    private fun normalizePackageName(value: String?): String? {
        val normalized = value
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.removePrefix("androidapp://")
            ?.removePrefix("android-app://")
            ?.substringBefore(':')
            ?.substringBefore('/')
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.takeIf { it.isNotBlank() }
        return normalized
    }

    private fun extractNormalizedPackages(value: String?): Set<String> {
        if (value.isNullOrBlank()) return emptySet()
        return value
            .split(',', ';', '|', ' ')
            .asSequence()
            .mapNotNull { normalizePackageName(it) }
            .filter { it.isNotBlank() }
            .toCollection(linkedSetOf())
    }

    private fun extractAndroidAppPackage(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val raw = value.trim().lowercase(Locale.ROOT)
        if (!raw.startsWith("androidapp://") && !raw.startsWith("android-app://")) {
            return null
        }
        return normalizePackageName(raw)
    }

    private fun normalizeHost(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val raw = value.trim().lowercase(Locale.ROOT)
        if (raw.startsWith("androidapp://")) return null

        val fullValue = if (raw.contains("://")) raw else "https://$raw"
        val parsedHost = runCatching { URL(fullValue).host }
            .getOrNull()
            ?.trim()
            ?.lowercase(Locale.ROOT)
        val fallbackHost = raw
            .substringBefore('/')
            .substringBefore(':')
            .trim()
            .lowercase(Locale.ROOT)
        val host = (parsedHost ?: fallbackHost)
            .removePrefix("www.")
            .trim('.')
            .takeIf { it.isNotBlank() }
        return host
    }

    private fun extractBaseDomain(host: String): String {
        val parts = host.split(".").filter { it.isNotBlank() }
        if (parts.size < 2) return host

        val twoPartTlds = setOf(
            "co.uk", "com.cn", "net.cn", "org.cn", "gov.cn", "ac.uk",
            "co.jp", "ne.jp", "or.jp", "com.au", "net.au", "org.au",
        )
        val lastTwo = parts.takeLast(2).joinToString(".")
        return if (parts.size >= 3 && lastTwo in twoPartTlds) {
            parts.takeLast(3).joinToString(".")
        } else {
            lastTwo
        }
    }

    private fun isSubdomainRelation(left: String, right: String): Boolean {
        if (left == right) return false
        return left.endsWith(".$right") || right.endsWith(".$left")
    }
}
