package takagi.ru.monica.data

/**
 * 安全分析数据
 */
data class SecurityAnalysisData(
    // 重复使用的密码
    val duplicatePasswords: List<DuplicatePasswordGroup> = emptyList(),
    // 重复的URL
    val duplicateUrls: List<DuplicateUrlGroup> = emptyList(),
    // 泄露的密码
    val compromisedPasswords: List<CompromisedPassword> = emptyList(),
    // 未启用2FA的账户
    val no2FAAccounts: List<No2FAAccount> = emptyList(),
    // 是否正在分析
    val isAnalyzing: Boolean = false,
    // 分析进度 (0-100)
    val analysisProgress: Int = 0,
    // 错误信息
    val error: String? = null
)

/**
 * 重复密码组
 */
data class DuplicatePasswordGroup(
    val passwordHash: String,  // 密码的哈希值，用于分组
    val count: Int,            // 使用次数
    val entries: List<PasswordEntry>  // 使用该密码的条目
)

/**
 * 重复URL组
 */
data class DuplicateUrlGroup(
    val url: String,           // URL
    val count: Int,            // 出现次数
    val entries: List<PasswordEntry>  // 该URL的所有条目
)

/**
 * 泄露的密码
 */
data class CompromisedPassword(
    val entry: PasswordEntry,  // 密码条目
    val breachCount: Int       // 泄露次数
)

/**
 * 未启用2FA的账户
 */
data class No2FAAccount(
    val entry: PasswordEntry,  // 密码条目
    val domain: String,        // 域名
    val supports2FA: Boolean   // 该网站是否支持2FA
)
