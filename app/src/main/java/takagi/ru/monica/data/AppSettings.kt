package takagi.ru.monica.data

/**
 * Settings data classes
 */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class ColorScheme {
    DEFAULT, DEEP_BLUE_TEAL, DEEP_BLUE_RED, PINK_PURPLE_BLUE, DARK_THEME, GRAY_BLUE_RED, CUSTOM
}

enum class Language {
    SYSTEM, ENGLISH, CHINESE, VIETNAMESE
}

enum class BottomNavContentTab {
    PASSWORDS,
    AUTHENTICATOR,
    DOCUMENTS,
    BANK_CARDS,
    GENERATOR;

    companion object {
        val DEFAULT_ORDER: List<BottomNavContentTab> = listOf(
            PASSWORDS,
            AUTHENTICATOR,
            DOCUMENTS,
            BANK_CARDS
        )

        fun sanitizeOrder(order: List<BottomNavContentTab>): List<BottomNavContentTab> {
            val result = mutableListOf<BottomNavContentTab>()
            val allowed = values().toSet()
            order.forEach { tab ->
                if (tab in allowed && tab !in result) {
                    result.add(tab)
                }
            }
            values().forEach { tab ->
                if (tab !in result) {
                    result.add(tab)
                }
            }
            return result
        }
    }
}

data class BottomNavVisibility(
    val passwords: Boolean = true,
    val authenticator: Boolean = true,
    val documents: Boolean = true,
    val bankCards: Boolean = false,  // 银行卡功能默认关闭
    val generator: Boolean = false   // 生成器功能默认关闭
) {
    fun isVisible(tab: BottomNavContentTab): Boolean = when (tab) {
        BottomNavContentTab.PASSWORDS -> passwords
        BottomNavContentTab.AUTHENTICATOR -> authenticator
        BottomNavContentTab.DOCUMENTS -> documents
        BottomNavContentTab.BANK_CARDS -> bankCards
        BottomNavContentTab.GENERATOR -> generator
    }

    fun visibleCount(): Int = listOf(passwords, authenticator, documents, bankCards, generator).count { it }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorScheme: ColorScheme = ColorScheme.DEFAULT,
    val customPrimaryColor: Long = 0xFF6650a4, // 默认紫色
    val customSecondaryColor: Long = 0xFF625b71, // 默认紫色灰色
    val customTertiaryColor: Long = 0xFF7D5260, // 默认粉色
    val language: Language = Language.SYSTEM,
    val biometricEnabled: Boolean = true, // 生物识别认证默认开启(改为true)
    val autoLockMinutes: Int = 5, // Auto lock after X minutes of inactivity
    val screenshotProtectionEnabled: Boolean = false, // Prevent screenshots by default
    val dynamicColorEnabled: Boolean = true, // 动态颜色默认开启
    val bottomNavVisibility: BottomNavVisibility = BottomNavVisibility(),
    val bottomNavOrder: List<BottomNavContentTab> = BottomNavContentTab.DEFAULT_ORDER
)