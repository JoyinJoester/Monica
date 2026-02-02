package takagi.ru.monica.data.model

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
    val pin: String = "",                  // PIN码（仅mOTP使用，应加密存储）
    val link: String = "",                 // 关联链接
    val associatedApp: String = "",        // 关联应用
    val boundPasswordId: Long? = null,     // 绑定的密码ID
    val categoryId: Long? = null,          // 分类ID（用于验证器分类筛选）
    val keepassDatabaseId: Long? = null    // 归属的 KeePass 数据库ID
)

/**
 * 银行卡数据
 */
@Serializable
data class BankCardData(
    val cardNumber: String,       // 卡号（加密存储）
    val cardholderName: String,   // 持卡人姓名
    val expiryMonth: String,      // 有效期月份
    val expiryYear: String,       // 有效期年份
    val cvv: String = "",         // CVV安全码（加密存储）
    val bankName: String = "",    // 银行名称
    val cardType: CardType = CardType.CREDIT, // 卡类型
    val billingAddress: String = "" // 账单地址（JSON格式存储BillingAddress）
)

/**
 * 账单地址详细信息
 */
@Serializable
data class BillingAddress(
    val streetAddress: String = "",   // 街道地址
    val apartment: String = "",       // 公寓/单元号
    val city: String = "",            // 城市
    val stateProvince: String = "",   // 州/省
    val postalCode: String = "",      // 邮政编码
    val country: String = ""          // 国家
)

fun BillingAddress.isEmpty(): Boolean {
    return streetAddress.isBlank() &&
        apartment.isBlank() &&
        city.isBlank() &&
        stateProvince.isBlank() &&
        postalCode.isBlank() &&
        country.isBlank()
}

fun BillingAddress.formatForDisplay(): String {
    val lines = mutableListOf<String>()
    if (streetAddress.isNotBlank()) {
        lines += streetAddress
    }
    if (apartment.isNotBlank()) {
        lines += apartment
    }
    val cityState = listOf(city, stateProvince)
        .filter { it.isNotBlank() }
        .joinToString(", ")
    if (cityState.isNotBlank()) {
        lines += cityState
    }
    val postalCountry = listOf(postalCode, country)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    if (postalCountry.isNotBlank()) {
        lines += postalCountry
    }
    return lines.joinToString("\n")
}

enum class CardType {
    CREDIT,      // 信用卡
    DEBIT,       // 借记卡
    PREPAID      // 预付卡
}

/**
 * 证件数据
 */
@Serializable
data class DocumentData(
    val documentType: DocumentType, // 证件类型
    val documentNumber: String,      // 证件号码（加密存储）
    val fullName: String,            // 姓名
    val issuedDate: String = "",     // 签发日期
    val expiryDate: String = "",     // 有效期至
    val issuedBy: String = "",       // 签发机关
    val nationality: String = "",    // 国籍
    val additionalInfo: String = ""  // 其他信息
)

enum class DocumentType {
    ID_CARD,       // 身份证
    PASSPORT,      // 护照
    DRIVER_LICENSE,// 驾驶证
    SOCIAL_SECURITY,// 社保卡
    OTHER          // 其他
}

/**
 * 笔记数据
 */
@Serializable
data class NoteData(
    val content: String,            // 笔记正文
    val tags: List<String> = emptyList(), // 标签列表
    val isMarkdown: Boolean = false      // 是否为Markdown格式
)
