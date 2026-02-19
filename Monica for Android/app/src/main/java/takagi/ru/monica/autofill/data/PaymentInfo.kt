package takagi.ru.monica.autofill.data

/**
 * 账单信息数据类
 * 
 * 存储信用卡和账单地址信息
 */
data class PaymentInfo(
    /** 唯一标识符 */
    val id: Long,
    
    /** 信用卡号 */
    val cardNumber: String,
    
    /** 持卡人姓名 */
    val cardHolderName: String,
    
    /** 有效期 (格式: MM/YY) */
    val expiryDate: String,
    
    /** CVV安全码 */
    val cvv: String,
    
    /** 账单地址 */
    val billingAddress: String? = null,
    
    /** 邮政编码 */
    val zipCode: String? = null,
    
    /** 城市 */
    val city: String? = null,
    
    /** 州/省 */
    val state: String? = null,
    
    /** 国家 */
    val country: String? = null
) {
    /**
     * 从PasswordEntry创建PaymentInfo
     */
    companion object {
        fun fromPasswordEntry(entry: takagi.ru.monica.data.PasswordEntry): PaymentInfo? {
            // 只有当信用卡号不为空时才创建PaymentInfo
            if (entry.creditCardNumber.isBlank()) {
                return null
            }
            
            return PaymentInfo(
                id = entry.id,
                cardNumber = entry.creditCardNumber,
                cardHolderName = entry.creditCardHolder,
                expiryDate = entry.creditCardExpiry,
                cvv = entry.creditCardCVV,
                billingAddress = entry.addressLine.ifBlank { null },
                zipCode = entry.zipCode.ifBlank { null },
                city = entry.city.ifBlank { null },
                state = entry.state.ifBlank { null },
                country = entry.country.ifBlank { null }
            )
        }
    }
}

