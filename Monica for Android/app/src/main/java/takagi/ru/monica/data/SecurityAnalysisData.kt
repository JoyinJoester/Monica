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
    // 支持通行密钥但尚未配置的账户
    val inactivePasskeyAccounts: List<InactivePasskeyAccount> = emptyList(),
    // 密码强度分布
    val passwordStrengthDistribution: PasswordStrengthDistribution = PasswordStrengthDistribution(),
    // 当前分析范围
    val selectedScopeKey: String = SecurityAnalysisScopeOption.KEY_ALL,
    // 可选分析范围
    val availableScopes: List<SecurityAnalysisScopeOption> = listOf(SecurityAnalysisScopeOption.all()),
    // 安全分数 (0-100)
    val securityScore: Int = 100,
    // 是否正在分析
    val isAnalyzing: Boolean = false,
    // 分析进度 (0-100)
    val analysisProgress: Int = 0,
    // 错误信息
    val error: String? = null
)

enum class SecurityAnalysisScopeType {
    ALL,
    LOCAL,
    KEEPASS,
    BITWARDEN
}

data class SecurityAnalysisScopeOption(
    val key: String,
    val type: SecurityAnalysisScopeType,
    val sourceId: Long? = null,
    val displayName: String? = null,
    val itemCount: Int = 0
) {
    companion object {
        const val KEY_ALL = "all"
        const val KEY_LOCAL = "local"

        fun all(itemCount: Int = 0): SecurityAnalysisScopeOption = SecurityAnalysisScopeOption(
            key = KEY_ALL,
            type = SecurityAnalysisScopeType.ALL,
            sourceId = null,
            itemCount = itemCount
        )

        fun local(itemCount: Int = 0): SecurityAnalysisScopeOption = SecurityAnalysisScopeOption(
            key = KEY_LOCAL,
            type = SecurityAnalysisScopeType.LOCAL,
            sourceId = null,
            itemCount = itemCount
        )

        fun keepass(id: Long, itemCount: Int = 0): SecurityAnalysisScopeOption = SecurityAnalysisScopeOption(
            key = "keepass_$id",
            type = SecurityAnalysisScopeType.KEEPASS,
            sourceId = id,
            displayName = null,
            itemCount = itemCount
        )

        fun keepass(id: Long, name: String?, itemCount: Int = 0): SecurityAnalysisScopeOption = SecurityAnalysisScopeOption(
            key = "keepass_$id",
            type = SecurityAnalysisScopeType.KEEPASS,
            sourceId = id,
            displayName = name,
            itemCount = itemCount
        )

        fun bitwarden(id: Long, itemCount: Int = 0): SecurityAnalysisScopeOption = SecurityAnalysisScopeOption(
            key = "bitwarden_$id",
            type = SecurityAnalysisScopeType.BITWARDEN,
            sourceId = id,
            displayName = null,
            itemCount = itemCount
        )

        fun bitwarden(id: Long, name: String?, itemCount: Int = 0): SecurityAnalysisScopeOption = SecurityAnalysisScopeOption(
            key = "bitwarden_$id",
            type = SecurityAnalysisScopeType.BITWARDEN,
            sourceId = id,
            displayName = name,
            itemCount = itemCount
        )
    }
}

/**
 * 密码强度分布（4档）
 */
data class PasswordStrengthDistribution(
    val weak: Int = 0,
    val medium: Int = 0,
    val strong: Int = 0,
    val veryStrong: Int = 0
) {
    val total: Int get() = weak + medium + strong + veryStrong
}

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

/**
 * 支持通行密钥但未绑定通行密钥的账户
 */
data class InactivePasskeyAccount(
    val entry: PasswordEntry,  // 密码条目
    val domain: String         // 命中的通行密钥支持域名
)
