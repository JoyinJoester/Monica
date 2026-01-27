package takagi.ru.monica.data

import kotlinx.serialization.Serializable

/**
 * 密码生成类型
 */
enum class GeneratorHistoryType {
    SYMBOL,      // 符号密码
    PASSWORD,    // 单词密码
    PASSPHRASE,  // 密码短语
    PIN,         // PIN码
    AUTOFILL     // 自动填充生成的密码
}

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
    val username: String = "",  // 用户名/邮箱
    val type: String = "AUTOFILL"  // 密码类型，默认为自动填充
)
