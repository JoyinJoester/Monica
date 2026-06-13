package takagi.ru.monica.ime

import java.util.Locale
import takagi.ru.monica.data.parseLinkedAppPackageNames

internal fun imeEntryMatchesPackage(
    entryPackageName: String,
    website: String,
    title: String,
    activePackageName: String
): Boolean {
    val activePackage = normalizeImePackageName(activePackageName)
    if (activePackage.isBlank()) return false

    val linkedPackages = parseLinkedAppPackageNames(entryPackageName)
        .map(::normalizeImePackageName)

    if (linkedPackages.any { it.equals(activePackage, ignoreCase = true) }) {
        return true
    }

    val packageHint = activePackage.substringAfterLast('.')
    return packageHint.isNotBlank() &&
        (
            website.contains(packageHint, ignoreCase = true) ||
                title.contains(packageHint, ignoreCase = true)
            )
}

private fun normalizeImePackageName(value: String): String {
    return value
        .trim()
        .lowercase(Locale.ROOT)
        .removePrefix("androidapp://")
        .removePrefix("android-app://")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringBefore(':')
        .trim()
}
