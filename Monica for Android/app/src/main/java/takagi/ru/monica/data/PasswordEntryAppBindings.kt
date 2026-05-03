package takagi.ru.monica.data

data class LinkedAppBinding(
    val packageName: String,
    val appName: String
)

private const val APP_BINDING_DELIMITER = "|"

fun parseLinkedAppPackageNames(rawValue: String): List<String> {
    return rawValue
        .split('|', ',', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
}

fun parseLinkedAppBindings(
    appPackageName: String,
    appName: String
): List<LinkedAppBinding> {
    val packages = parseLinkedAppPackageNames(appPackageName)
    if (packages.isEmpty()) return emptyList()

    val names = appName
        .split(APP_BINDING_DELIMITER)
        .map { it.trim() }

    return packages.mapIndexed { index, packageName ->
        LinkedAppBinding(
            packageName = packageName,
            appName = names.getOrNull(index)
                ?.takeIf { it.isNotBlank() }
                ?: if (packages.size == 1) appName.trim() else packageName
        )
    }
}

fun encodeLinkedAppPackageNames(bindings: List<LinkedAppBinding>): String {
    return bindings
        .map { it.packageName.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .joinToString(APP_BINDING_DELIMITER)
}

fun encodeLinkedAppNames(bindings: List<LinkedAppBinding>): String {
    val normalizedBindings = bindings
        .filter { it.packageName.isNotBlank() }
        .distinctBy { it.packageName.trim().lowercase() }

    return normalizedBindings
        .map { binding ->
            binding.appName.trim().takeIf { it.isNotBlank() } ?: binding.packageName.trim()
        }
        .joinToString(APP_BINDING_DELIMITER)
}

fun addOrReplaceLinkedAppBinding(
    appPackageName: String,
    appName: String,
    packageNameToAdd: String,
    appNameToAdd: String
): Pair<String, String> {
    val normalizedPackage = packageNameToAdd.trim()
    if (normalizedPackage.isBlank()) return appPackageName to appName

    val bindings = parseLinkedAppBindings(appPackageName, appName)
        .filterNot { it.packageName.equals(normalizedPackage, ignoreCase = true) }
        .plus(
            LinkedAppBinding(
                packageName = normalizedPackage,
                appName = appNameToAdd.trim().ifBlank { normalizedPackage }
            )
        )

    return encodeLinkedAppPackageNames(bindings) to encodeLinkedAppNames(bindings)
}

fun removeLinkedAppBinding(
    appPackageName: String,
    appName: String,
    packageNameToRemove: String
): Pair<String, String> {
    val bindings = parseLinkedAppBindings(appPackageName, appName)
        .filterNot { it.packageName.equals(packageNameToRemove.trim(), ignoreCase = true) }

    return encodeLinkedAppPackageNames(bindings) to encodeLinkedAppNames(bindings)
}

fun PasswordEntry.linkedAppBindings(): List<LinkedAppBinding> {
    return parseLinkedAppBindings(appPackageName, appName)
}

fun PasswordEntry.primaryLinkedAppPackageName(): String {
    return linkedAppBindings().firstOrNull()?.packageName.orEmpty()
}

fun PasswordEntry.primaryLinkedAppName(): String {
    return linkedAppBindings().firstOrNull()?.appName.orEmpty()
}

fun PasswordEntry.isLinkedToApp(packageName: String): Boolean {
    if (packageName.isBlank()) return false
    return linkedAppBindings().any { it.packageName.equals(packageName, ignoreCase = true) }
}
