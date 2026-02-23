package takagi.ru.monica.ui.password

import java.util.Locale
import takagi.ru.monica.data.PasswordEntry

fun getPasswordInfoKey(entry: PasswordEntry): String {
    val sourceKey = buildPasswordSourceKey(entry)
    val title = entry.title.trim().lowercase(Locale.ROOT)
    val username = entry.username.trim().lowercase(Locale.ROOT)
    val website = normalizeWebsiteForGroupKey(entry.website)
    return "$sourceKey|$title|$website|$username"
}

fun buildPasswordSourceKey(entry: PasswordEntry): String {
    return when {
        !entry.bitwardenCipherId.isNullOrBlank() ->
            "bw:${entry.bitwardenVaultId}:${entry.bitwardenCipherId}"
        entry.bitwardenVaultId != null ->
            "bw-local:${entry.bitwardenVaultId}:${entry.bitwardenFolderId.orEmpty()}"
        entry.keepassDatabaseId != null ->
            "kp:${entry.keepassDatabaseId}:${entry.keepassGroupPath.orEmpty()}"
        else -> "local"
    }
}

fun normalizeWebsiteForGroupKey(value: String): String {
    val raw = value.trim()
    if (raw.isEmpty()) return ""
    return raw
        .lowercase(Locale.ROOT)
        .removePrefix("http://")
        .removePrefix("https://")
        .removePrefix("www.")
        .trimEnd('/')
}

fun getGroupKeyForMode(entry: PasswordEntry, mode: String): String {
    val noteLabel = entry.notes
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
    val website = entry.website.trim()
    val appName = entry.appName.trim()
    val packageName = entry.appPackageName.trim()
    val title = entry.title.trim()
    val idKey = "id-${entry.id}"

    return when (mode) {
        "note" -> noteLabel.takeUnless { it.isNullOrEmpty() } ?: idKey
        "website" -> website.takeUnless { it.isEmpty() } ?: idKey
        "app" -> appName.takeUnless { it.isEmpty() }
            ?: packageName.takeUnless { it.isEmpty() }
            ?: idKey
        "title" -> title.takeUnless { it.isEmpty() } ?: idKey
        else -> {
            noteLabel.takeUnless { it.isNullOrEmpty() }
                ?: website.takeUnless { it.isEmpty() }
                ?: appName.takeUnless { it.isEmpty() }
                ?: packageName.takeUnless { it.isEmpty() }
                ?: title.takeUnless { it.isEmpty() }
                ?: idKey
        }
    }
}

fun getPasswordGroupTitle(entry: PasswordEntry): String =
    getGroupKeyForMode(entry, "smart")

enum class StackCardMode {
    AUTO,
    ALWAYS_EXPANDED
}
