package takagi.ru.monica.data

/**
 * Settings data classes
 */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class Language {
    SYSTEM, ENGLISH, CHINESE, VIETNAMESE
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: Language = Language.SYSTEM,
    val biometricEnabled: Boolean = false,
    val autoLockMinutes: Int = 5, // Auto lock after X minutes of inactivity
    val screenshotProtectionEnabled: Boolean = true // Prevent screenshots by default
)