package takagi.ru.monica.data

import kotlinx.serialization.Serializable

/**
 * 密码生成历史记录
 * 记录用户在自动填充时生成并使用的强密码
 */
@Serializable
data class PasswordGenerationHistory(
    val password: String,
    val timestamp: Long,
    val packageName: String = "",
    val domain: String = "",
    val username: String = ""  // 用户名/邮箱
)
