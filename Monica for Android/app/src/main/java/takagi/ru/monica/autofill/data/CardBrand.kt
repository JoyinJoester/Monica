package takagi.ru.monica.autofill.data

/**
 * 信用卡品牌枚举
 * 
 * 支持主流信用卡品牌识别
 */
enum class CardBrand {
    /** Visa卡 */
    VISA,
    
    /** Mastercard卡 */
    MASTERCARD,
    
    /** American Express卡 */
    AMEX,
    
    /** Discover卡 */
    DISCOVER,
    
    /** 银联卡 */
    UNIONPAY,
    
    /** JCB卡 */
    JCB,
    
    /** 未知品牌 */
    UNKNOWN;
    
    companion object {
        /**
         * 根据卡号检测信用卡品牌
         * 
         * @param cardNumber 信用卡号（可能包含空格或破折号）
         * @return 检测到的信用卡品牌
         */
        fun detect(cardNumber: String): CardBrand {
            // 移除所有非数字字符
            val cleanNumber = cardNumber.replace(Regex("[^0-9]"), "")
            
            // 卡号太短无法识别
            if (cleanNumber.length < 2) {
                return UNKNOWN
            }
            
            return when {
                // Visa: 以4开头
                cleanNumber.startsWith("4") -> VISA
                
                // Mastercard: 以51-55或2221-2720开头
                cleanNumber.startsWith("5") && cleanNumber.length >= 2 -> {
                    val prefix = cleanNumber.substring(0, 2).toIntOrNull()
                    if (prefix in 51..55) MASTERCARD else UNKNOWN
                }
                cleanNumber.length >= 4 -> {
                    val prefix4 = cleanNumber.substring(0, 4).toIntOrNull()
                    if (prefix4 != null && prefix4 in 2221..2720) MASTERCARD else checkOtherBrands(cleanNumber)
                }
                
                else -> checkOtherBrands(cleanNumber)
            }
        }
        
        /**
         * 检查其他品牌
         */
        private fun checkOtherBrands(cleanNumber: String): CardBrand {
            return when {
                // American Express: 以34或37开头
                cleanNumber.startsWith("34") || cleanNumber.startsWith("37") -> AMEX
                
                // Discover: 以6011, 622126-622925, 644-649, 或65开头
                cleanNumber.startsWith("6011") -> DISCOVER
                cleanNumber.startsWith("65") -> DISCOVER
                cleanNumber.length >= 3 -> {
                    val prefix3 = cleanNumber.substring(0, 3).toIntOrNull()
                    if (prefix3 in 644..649) DISCOVER else checkUnionPayAndJCB(cleanNumber)
                }
                
                else -> checkUnionPayAndJCB(cleanNumber)
            }
        }
        
        /**
         * 检查银联和JCB
         */
        private fun checkUnionPayAndJCB(cleanNumber: String): CardBrand {
            return when {
                // 银联: 以62开头
                cleanNumber.startsWith("62") -> UNIONPAY
                
                // JCB: 以3528-3589开头
                cleanNumber.length >= 4 -> {
                    val prefix4 = cleanNumber.substring(0, 4).toIntOrNull()
                    if (prefix4 != null && prefix4 in 3528..3589) JCB else UNKNOWN
                }
                
                // 银联也可能以81-88开头（部分卡）
                cleanNumber.length >= 2 -> {
                    val prefix2 = cleanNumber.substring(0, 2).toIntOrNull()
                    if (prefix2 in 81..88) UNIONPAY else UNKNOWN
                }
                
                else -> UNKNOWN
            }
        }
    }
    
    /**
     * 获取品牌显示名称
     */
    fun getDisplayName(): String {
        return when (this) {
            VISA -> "Visa"
            MASTERCARD -> "Mastercard"
            AMEX -> "American Express"
            DISCOVER -> "Discover"
            UNIONPAY -> "银联"
            JCB -> "JCB"
            UNKNOWN -> "未知"
        }
    }
}

