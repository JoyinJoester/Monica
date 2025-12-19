package takagi.ru.monica.wear.data.model

import kotlinx.serialization.Serializable

/**
 * OTP类型枚举
 */
@Serializable
enum class OtpType {
    TOTP,   // 基于时间的一次性密码 (Time-based OTP)
    HOTP,   // 基于计数器的一次性密码 (HMAC-based OTP)
    STEAM,  // Steam Guard
    YANDEX, // Yandex OTP
    MOTP    // Mobile-OTP
}

/**
 * TOTP验证器数据
 * 支持多种OTP类型: TOTP, HOTP, Steam, Yandex, mOTP
 * 复用主应用的数据模型
 */
@Serializable
data class TotpData(
    val secret: String,                    // OTP密钥
    val issuer: String = "",               // 发行者（如：Google, GitHub, Steam）
    val accountName: String = "",          // 账户名
    val period: Int = 30,                  // 时间周期（默认30秒，仅TOTP/Steam/Yandex/mOTP使用）
    val digits: Int = 6,                   // 验证码位数（默认6位，Steam为5位）
    val algorithm: String = "SHA1",        // 算法（SHA1, SHA256, SHA512）
    val otpType: OtpType = OtpType.TOTP,  // OTP类型（默认TOTP，确保向后兼容）
    val counter: Long = 0,                 // 计数器（仅HOTP使用）
    val pin: String = ""                   // PIN码（仅mOTP使用，应加密存储）
)
