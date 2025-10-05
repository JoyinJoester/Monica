package takagi.ru.monica.data.model

import kotlinx.serialization.Serializable

/**
 * TOTP验证器数据
 */
@Serializable
data class TotpData(
    val secret: String,           // TOTP密钥
    val issuer: String = "",      // 发行者（如：Google, GitHub）
    val accountName: String = "", // 账户名
    val period: Int = 30,         // 时间周期（默认30秒）
    val digits: Int = 6,          // 验证码位数（默认6位）
    val algorithm: String = "SHA1" // 算法（SHA1, SHA256, SHA512）
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
    val billingAddress: String = "" // 账单地址
)

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
