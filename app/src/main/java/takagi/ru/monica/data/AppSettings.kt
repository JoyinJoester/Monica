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

enum class BottomNavContentTab {
    PASSWORDS,
    AUTHENTICATOR,
    DOCUMENTS,
    BANK_CARDS,
    LEDGER;

    companion object {
        val DEFAULT_ORDER: List<BottomNavContentTab> = listOf(
            PASSWORDS,
            AUTHENTICATOR,
            DOCUMENTS,
            BANK_CARDS,
            LEDGER
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
    val bankCards: Boolean = true,
    val ledger: Boolean = false  // 记账功能默认关闭
) {
    fun isVisible(tab: BottomNavContentTab): Boolean = when (tab) {
        BottomNavContentTab.PASSWORDS -> passwords
        BottomNavContentTab.AUTHENTICATOR -> authenticator
        BottomNavContentTab.DOCUMENTS -> documents
        BottomNavContentTab.BANK_CARDS -> bankCards
        BottomNavContentTab.LEDGER -> ledger
    }

    fun visibleCount(): Int = listOf(passwords, authenticator, documents, bankCards, ledger).count { it }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: Language = Language.SYSTEM,
    val biometricEnabled: Boolean = true, // 生物识别认证默认开启(改为true)
    val autoLockMinutes: Int = 5, // Auto lock after X minutes of inactivity
    val screenshotProtectionEnabled: Boolean = false, // Prevent screenshots by default
    val bottomNavVisibility: BottomNavVisibility = BottomNavVisibility(),
    val bottomNavOrder: List<BottomNavContentTab> = BottomNavContentTab.DEFAULT_ORDER
)