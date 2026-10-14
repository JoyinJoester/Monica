package takagi.ru.monica.autofill_ng

import java.net.URI
import java.util.Locale
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.utils.PasswordWebsiteCodec

/** Suggestions only; an explicit choice and the ordinary editor are still required. */
internal object AutofillSaveMatchPolicy {
    fun matches(entry: PasswordEntry, account: String?, username: String, packageName: String, website: String): Boolean {
        if (entry.isDeleted || entry.isArchived || entry.hasOwnershipConflict() ||
            !entry.loginType.equals("PASSWORD", ignoreCase = true) || username.isBlank() || account != username) return false
        return matchesTarget(entry, packageName, website)
    }

    fun matchesTarget(entry: PasswordEntry, packageName: String, website: String): Boolean {
        // A browser package is never evidence that two websites belong to the same account.
        if (website.isNotBlank()) {
            val host = host(website) ?: return false
            return PasswordWebsiteCodec.parse(entry.website).any { host(it) == host }
        }
        return packageName.isNotBlank() && entry.appPackageName == packageName
    }

    private fun host(value: String): String? = runCatching {
        val uri = URI(if (value.contains("://")) value else "https://$value")
        if (uri.scheme.lowercase(Locale.ROOT) !in setOf("http", "https") || uri.userInfo != null) null
        else uri.host?.lowercase(Locale.ROOT)?.trimEnd('.')
    }.getOrNull()
}
