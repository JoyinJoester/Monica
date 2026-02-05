package takagi.ru.monica.bitwarden.mapper

/**
 * Monica SecureItem 数据类型相关常量
 * 
 * 注意: 实际的数据类定义在各自的 Mapper 文件中:
 * - CardItemData -> CardMapper.kt
 * - NoteItemData -> SecureNoteMapper.kt
 * - DocumentItemData -> IdentityMapper.kt
 * - TotpItemData -> TotpMapper.kt
 */

/**
 * 支持的证件类型
 */
object DocumentTypes {
    const val PASSPORT = "PASSPORT"
    const val ID_CARD = "ID_CARD"
    const val DRIVER_LICENSE = "DRIVER_LICENSE"
    const val SOCIAL_SECURITY = "SOCIAL_SECURITY"
    const val INSURANCE = "INSURANCE"
    const val MEMBERSHIP = "MEMBERSHIP"
    const val OTHER = "OTHER"
    
    fun getDisplayName(type: String): String = when (type) {
        PASSPORT -> "护照"
        ID_CARD -> "身份证"
        DRIVER_LICENSE -> "驾驶证"
        SOCIAL_SECURITY -> "社保卡"
        INSURANCE -> "保险卡"
        MEMBERSHIP -> "会员卡"
        else -> "其他证件"
    }
}

/**
 * 支持的银行卡品牌
 */
object CardBrands {
    const val VISA = "Visa"
    const val MASTERCARD = "Mastercard"
    const val AMEX = "Amex"
    const val DISCOVER = "Discover"
    const val DINERS_CLUB = "Diners Club"
    const val JCB = "JCB"
    const val UNIONPAY = "UnionPay"
    const val OTHER = "Other"
    
    /**
     * 根据卡号前缀猜测品牌
     */
    fun detectBrand(cardNumber: String): String {
        val digits = cardNumber.filter { it.isDigit() }
        return when {
            digits.startsWith("4") -> VISA
            digits.startsWith("5") || digits.startsWith("2") -> MASTERCARD
            digits.startsWith("34") || digits.startsWith("37") -> AMEX
            digits.startsWith("6011") || digits.startsWith("65") -> DISCOVER
            digits.startsWith("36") || digits.startsWith("38") -> DINERS_CLUB
            digits.startsWith("35") -> JCB
            digits.startsWith("62") -> UNIONPAY
            else -> OTHER
        }
    }
}
