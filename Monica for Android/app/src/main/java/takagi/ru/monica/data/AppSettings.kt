package takagi.ru.monica.data

/**
 * Settings data classes
 */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class ColorScheme {
    DEFAULT, 
    OCEAN_BLUE,      // 海洋蓝
    SUNSET_ORANGE,   // 日落橙
    FOREST_GREEN,    // 森林绿
    TECH_PURPLE,     // 科技紫
    BLACK_MAMBA,     // 黑曼巴
    GREY_STYLE,      // 小黑紫
    WATER_LILIES,    // 睡莲
    IMPRESSION_SUNRISE, // 印象·日出
    JAPANESE_BRIDGE, // 日本桥
    HAYSTACKS,       // 干草堆
    ROUEN_CATHEDRAL, // 鲁昂大教堂
    PARLIAMENT_FOG,  // 国会大厦
    CUSTOM           // 自定义
}

enum class Language {
    SYSTEM, ENGLISH, CHINESE, VIETNAMESE, JAPANESE
}

enum class ProgressBarStyle {
    LINEAR,  // 线形进度条
    WAVE     // 波浪形进度条
}

enum class BottomNavContentTab {
    PASSWORDS,
    AUTHENTICATOR,
    CARD_WALLET,
    GENERATOR,
    NOTES;

    companion object {
        val DEFAULT_ORDER: List<BottomNavContentTab> = listOf(
            PASSWORDS,
            AUTHENTICATOR,
            CARD_WALLET,
            NOTES
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
    val cardWallet: Boolean = true,
    val generator: Boolean = false,   // 生成器功能默认关闭
    val notes: Boolean = true        // 笔记功能默认开启
) {
    fun isVisible(tab: BottomNavContentTab): Boolean = when (tab) {
        BottomNavContentTab.PASSWORDS -> passwords
        BottomNavContentTab.AUTHENTICATOR -> authenticator
        BottomNavContentTab.CARD_WALLET -> cardWallet
        BottomNavContentTab.GENERATOR -> generator
        BottomNavContentTab.NOTES -> notes
    }

    fun visibleCount(): Int = listOf(passwords, authenticator, cardWallet, generator, notes).count { it }
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
    val bottomNavOrder: List<BottomNavContentTab> = BottomNavContentTab.DEFAULT_ORDER,
    val disablePasswordVerification: Boolean = false, // 开发者选项：关闭密码验证
    val validatorProgressBarStyle: ProgressBarStyle = ProgressBarStyle.LINEAR, // 验证器进度条样式
    val validatorVibrationEnabled: Boolean = true, // 验证器震动提醒
    val notificationValidatorEnabled: Boolean = false, // 通知栏验证器开关
    val notificationValidatorAutoMatch: Boolean = false, // 通知栏验证器自动匹配
    val notificationValidatorId: Long = -1L, // 通知栏显示的验证器ID
    val isPlusActivated: Boolean = false, // Plus是否已激活
    val stackCardMode: String = "AUTO", // 堆叠卡片模式
    val passwordGroupMode: String = "smart", // 密码分组模式
    val totpTimeOffset: Int = 0 // TOTP时间偏移（秒），用于校正系统时间误差
)