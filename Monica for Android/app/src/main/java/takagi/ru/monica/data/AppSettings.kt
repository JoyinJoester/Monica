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
    CATPPUCCIN_LATTE,     // Catppuccin · Latte（Plus）
    CATPPUCCIN_FRAPPE,    // Catppuccin · Frappé（Plus）
    CATPPUCCIN_MACCHIATO, // Catppuccin · Macchiato（Plus）
    CATPPUCCIN_MOCHA,     // Catppuccin · Mocha（Plus）
    CUSTOM           // 自定义
}

enum class Language {
    SYSTEM, ENGLISH, CHINESE, VIETNAMESE, JAPANESE
}

enum class ProgressBarStyle {
    LINEAR,  // 线形进度条
    WAVE     // 波浪形进度条
}

/**
 * 统一进度条模式
 */
enum class UnifiedProgressBarMode {
    DISABLED,  // 关闭统一进度条，每个卡片单独显示
    ENABLED    // 启用统一进度条（30s周期），标准周期卡片隐藏单独进度条
}

/**
 * V1 底部导航内容标签页
 * 用于经典本地密码库模式
 */
enum class BottomNavContentTab {
    // VAULT,        // V2 密码库（多源统一视图） - Removed
    PASSWORDS,
    AUTHENTICATOR,
    CARD_WALLET,
    GENERATOR,
    NOTES,
    SEND,         // V2 发送（安全分享）
    TIMELINE,
    PASSKEY;  // 通行密钥

    companion object {
        val DEFAULT_ORDER: List<BottomNavContentTab> = listOf(
            PASSWORDS,
            AUTHENTICATOR,
            CARD_WALLET,
            PASSKEY,
            NOTES,
            SEND,
            TIMELINE
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

/**
 * V2 底部导航内容标签页
 * 用于多源密码库模式（Bitwarden 风格）
 */
enum class V2BottomNavTab {
    // VAULT,      // 密码库（统一视图，支持多后端） - Removed
    SEND,       // Send（安全分享）
    SYNC,       // 同步中心
    GENERATOR;  // 生成器

    companion object {
        val DEFAULT_ORDER: List<V2BottomNavTab> = listOf(
            SEND,
            SYNC,
            GENERATOR
        )
    }
}

data class BottomNavVisibility(
    // val vault: Boolean = true,        // V2 密码库默认开启 - Removed
    val passwords: Boolean = true,
    val authenticator: Boolean = true,
    val cardWallet: Boolean = true,
    val generator: Boolean = false,   // 生成器功能默认关闭
    val notes: Boolean = true,        // 笔记功能默认开启
    val send: Boolean = false,        // V2 发送功能默认关闭
    val timeline: Boolean = false,    // 时间线功能默认关闭
    val passkey: Boolean = true       // 通行密钥功能默认开启
) {
    fun isVisible(tab: BottomNavContentTab): Boolean = when (tab) {
        // BottomNavContentTab.VAULT -> vault
        BottomNavContentTab.PASSWORDS -> passwords
        BottomNavContentTab.AUTHENTICATOR -> authenticator
        BottomNavContentTab.CARD_WALLET -> cardWallet
        BottomNavContentTab.GENERATOR -> generator
        BottomNavContentTab.NOTES -> notes
        BottomNavContentTab.SEND -> send
        BottomNavContentTab.TIMELINE -> timeline
        BottomNavContentTab.PASSKEY -> passkey
    }

    fun visibleCount(): Int = listOf(passwords, authenticator, cardWallet, generator, notes, send, timeline, passkey).count { it }
}

/**
 * 添加/编辑密码页面字段可见性设置
 * 控制哪些字段卡片在添加密码页面显示
 * 注意：如果条目已有该字段数据，即使关闭也会显示
 */
data class PasswordFieldVisibility(
    val securityVerification: Boolean = true,  // 安全验证（TOTP密钥）
    val categoryAndNotes: Boolean = true,      // 分类与备注
    val appBinding: Boolean = true,            // 应用关联
    val personalInfo: Boolean = true,          // 个人信息（邮箱、电话）
    val addressInfo: Boolean = true,           // 地址信息
    val paymentInfo: Boolean = true            // 支付信息（信用卡）
)

/**
 * 预设自定义字段类型
 */
enum class PresetFieldType(val displayName: String, val icon: String) {
    TEXT("文本", "text"),
    PASSWORD("密码", "password"),
    NUMBER("数字", "number"),
    DATE("日期", "date"),
    URL("网址", "url"),
    EMAIL("邮箱", "email"),
    PHONE("电话", "phone")
}

/**
 * 预设自定义字段模板
 * 用户可以在设置中预先定义常用的自定义字段，添加密码时这些字段会自动出现
 * 
 * @property id 唯一标识（UUID字符串）
 * @property fieldName 字段名称（显示给用户的标题）
 * @property fieldType 字段类型
 * @property isSensitive 是否为敏感数据（显示时默认隐藏，复制时标记敏感）
 * @property isRequired 是否必填
 * @property defaultValue 默认值
 * @property placeholder 占位提示文字
 * @property order 排序顺序
 */
data class PresetCustomField(
    val id: String,
    val fieldName: String,
    val fieldType: PresetFieldType = PresetFieldType.TEXT,
    val isSensitive: Boolean = false,
    val isRequired: Boolean = false,
    val defaultValue: String = "",
    val placeholder: String = "",
    val order: Int = 0
) {
    /**
     * 序列化为 JSON 字符串
     */
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"id\":\"$id\",")
            append("\"fieldName\":\"${fieldName.replace("\"", "\\\"")}\",")
            append("\"fieldType\":\"${fieldType.name}\",")
            append("\"isSensitive\":$isSensitive,")
            append("\"isRequired\":$isRequired,")
            append("\"defaultValue\":\"${defaultValue.replace("\"", "\\\"")}\",")
            append("\"placeholder\":\"${placeholder.replace("\"", "\\\"")}\",")
            append("\"order\":$order")
            append("}")
        }
    }
    
    companion object {
        /**
         * 从 JSON 字符串解析
         */
        fun fromJson(json: String): PresetCustomField? {
            return try {
                // 简单的 JSON 解析
                fun extractString(key: String): String {
                    val pattern = "\"$key\":\"([^\"]*)\""
                    val regex = Regex(pattern)
                    return regex.find(json)?.groupValues?.get(1)
                        ?.replace("\\\"", "\"") ?: ""
                }
                fun extractBoolean(key: String): Boolean {
                    val pattern = "\"$key\":(true|false)"
                    val regex = Regex(pattern)
                    return regex.find(json)?.groupValues?.get(1) == "true"
                }
                fun extractInt(key: String): Int {
                    val pattern = "\"$key\":(\\d+)"
                    val regex = Regex(pattern)
                    return regex.find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                }
                
                PresetCustomField(
                    id = extractString("id"),
                    fieldName = extractString("fieldName"),
                    fieldType = try { 
                        PresetFieldType.valueOf(extractString("fieldType")) 
                    } catch (e: Exception) { 
                        PresetFieldType.TEXT 
                    },
                    isSensitive = extractBoolean("isSensitive"),
                    isRequired = extractBoolean("isRequired"),
                    defaultValue = extractString("defaultValue"),
                    placeholder = extractString("placeholder"),
                    order = extractInt("order")
                )
            } catch (e: Exception) {
                null
            }
        }
        
        /**
         * 解析预设字段列表的 JSON
         */
        fun listFromJson(json: String): List<PresetCustomField> {
            if (json.isBlank() || json == "[]") return emptyList()
            return try {
                // 移除首尾的 [ ]
                val content = json.trim().removePrefix("[").removeSuffix("]")
                if (content.isBlank()) return emptyList()
                
                // 分割各个对象 - 简单处理，假设对象内没有嵌套
                val objects = mutableListOf<String>()
                var depth = 0
                var current = StringBuilder()
                for (char in content) {
                    when (char) {
                        '{' -> {
                            depth++
                            current.append(char)
                        }
                        '}' -> {
                            current.append(char)
                            depth--
                            if (depth == 0) {
                                objects.add(current.toString())
                                current = StringBuilder()
                            }
                        }
                        ',' -> {
                            if (depth == 0) {
                                // 跳过对象之间的逗号
                            } else {
                                current.append(char)
                            }
                        }
                        else -> {
                            if (depth > 0) {
                                current.append(char)
                            }
                        }
                    }
                }
                
                objects.mapNotNull { fromJson(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        /**
         * 将预设字段列表序列化为 JSON
         */
        fun listToJson(fields: List<PresetCustomField>): String {
            return "[${fields.joinToString(",") { it.toJson() }}]"
        }
    }
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
    val useDraggableBottomNav: Boolean = false, // 使用可拖拽底部导航栏
    val disablePasswordVerification: Boolean = false, // 开发者选项：关闭密码验证
    val validatorProgressBarStyle: ProgressBarStyle = ProgressBarStyle.LINEAR, // 验证器进度条样式
    val validatorUnifiedProgressBar: UnifiedProgressBarMode = UnifiedProgressBarMode.ENABLED, // 统一进度条模式
    val validatorSmoothProgress: Boolean = true, // 平滑进度条（无停顿感）
    val validatorVibrationEnabled: Boolean = true, // 验证器震动提醒
    val hideFabOnScroll: Boolean = false, // 滚动时隐藏悬浮按钮
    val copyNextCodeWhenExpiring: Boolean = false, // 倒计时<=5秒时复制下一个验证码
    val notificationValidatorEnabled: Boolean = false, // 通知栏验证器开关
    val notificationValidatorAutoMatch: Boolean = false, // 通知栏验证器自动匹配
    val notificationValidatorId: Long = -1L, // 通知栏显示的验证器ID
    val isPlusActivated: Boolean = false, // Plus是否已激活
    val stackCardMode: String = "AUTO", // 堆叠卡片模式
    val passwordGroupMode: String = "smart", // 密码分组模式
    val totpTimeOffset: Int = 0, // TOTP时间偏移（秒），用于校正系统时间误差
    val trashEnabled: Boolean = true, // 回收站功能是否启用
    val trashAutoDeleteDays: Int = 30, // 回收站自动清空天数（0=不自动清空，-1=禁用回收站）
    val iconCardsEnabled: Boolean = false, // 是否启用带图标卡片
    val passwordCardDisplayMode: PasswordCardDisplayMode = PasswordCardDisplayMode.SHOW_ALL, // 卡片显示模式
    val noteGridLayout: Boolean = true, // 笔记列表使用网格布局 (true = 网格, false = 列表)
    val autofillAuthRequired: Boolean = true, // 自动填充验证 - 默认开启
    val passwordFieldVisibility: PasswordFieldVisibility = PasswordFieldVisibility(), // 添加密码页面字段定制
    val reduceAnimations: Boolean = false, // 减少动画 - 解决部分设备（如 HyperOS 2/Android 15）动画卡顿问题

    // Bitwarden 同步范围设置
    val bitwardenUploadAll: Boolean = false, // 一键上传所有数据到 Bitwarden
    
    // V2 多源密码库设置
    val defaultVaultView: VaultViewMode = VaultViewMode.V1, // 默认密码库视图
    val autofillSources: Set<AutofillSource> = setOf(AutofillSource.V1_LOCAL), // 自动填充数据源
    val autofillPriority: List<AutofillSource> = listOf(AutofillSource.V1_LOCAL), // 自动填充优先级
    
    // 导航栏版本设置
    val navBarVersion: NavBarVersion = NavBarVersion.V1 // 导航栏版本（V1经典/V2简洁）
)

/**
 * 密码卡片显示模式
 */
enum class PasswordCardDisplayMode {
    SHOW_ALL,       // 显示所有信息（默认）
    TITLE_USERNAME, // 仅显示标题和用户名
    TITLE_ONLY      // 仅显示标题
}

/**
 * 密码库视图模式
 * V1 = Monica 经典本地库（卡包/证件/密码/TOTP）
 * V2 = 多源密码库（Bitwarden/KeePass 等后端）
 */
enum class VaultViewMode {
    V1,  // 经典本地库
    V2   // 多源密码库
}

/**
 * 导航栏版本
 * V1 = 经典底部导航栏（可自定义顺序和显示项）
 * V2 = 简洁导航栏（固定4项：库、发送、生成、设置）+ 最近页面动态显示
 */
enum class NavBarVersion {
    V1,  // 经典导航栏
    V2   // 简洁动态导航栏
}

/**
 * 自动填充数据源
 */
enum class AutofillSource {
    V1_LOCAL,    // V1 本地密码库
    BITWARDEN,   // Bitwarden
    KEEPASS      // KeePass（未来支持）
}