package takagi.ru.monica.autofill.utils

/**
 * 信用卡工具类
 * 
 * 提供信用卡相关的工具函数
 */
object CardUtils {
    
    /**
     * 脱敏显示信用卡号
     * 
     * 将卡号中间部分替换为星号，只显示前4位和后4位
     * 例如: "4532123456789012" -> "**** **** **** 9012"
     * 
     * @param cardNumber 完整的信用卡号
     * @return 脱敏后的卡号字符串
     */
    fun maskCardNumber(cardNumber: String): String {
        // 移除所有非数字字符
        val cleanNumber = cardNumber.replace(Regex("[^0-9]"), "")
        
        // 如果卡号太短，直接返回星号
        if (cleanNumber.length < 8) {
            return "**** **** **** ****"
        }
        
        // 获取后4位
        val lastFour = cleanNumber.takeLast(4)
        
        // 根据卡号长度决定显示格式
        return when {
            // American Express (15位): **** ****** *1234
            cleanNumber.length == 15 -> "**** ****** *$lastFour"
            
            // 标准卡号 (16位): **** **** **** 1234
            cleanNumber.length == 16 -> "**** **** **** $lastFour"
            
            // 其他长度: 显示前4位和后4位
            cleanNumber.length > 8 -> {
                val firstFour = cleanNumber.take(4)
                "$firstFour **** **** $lastFour"
            }
            
            // 默认格式
            else -> "**** **** **** $lastFour"
        }
    }
    
    /**
     * 格式化信用卡号（添加空格）
     * 
     * @param cardNumber 信用卡号
     * @return 格式化后的卡号
     */
    fun formatCardNumber(cardNumber: String): String {
        val cleanNumber = cardNumber.replace(Regex("[^0-9]"), "")
        
        return when (cleanNumber.length) {
            // American Express: 4-6-5格式
            15 -> cleanNumber.chunked(4).joinToString(" ").let {
                val parts = it.split(" ")
                "${parts[0]} ${parts[1].take(6)} ${parts[1].drop(6)}${parts.getOrNull(2) ?: ""}"
            }
            
            // 标准卡号: 4-4-4-4格式
            else -> cleanNumber.chunked(4).joinToString(" ")
        }
    }
    
    /**
     * 验证信用卡号是否有效（使用Luhn算法）
     * 
     * @param cardNumber 信用卡号
     * @return 是否有效
     */
    fun isValidCardNumber(cardNumber: String): Boolean {
        val cleanNumber = cardNumber.replace(Regex("[^0-9]"), "")
        
        // 卡号长度检查（通常在13-19位之间）
        if (cleanNumber.length < 13 || cleanNumber.length > 19) {
            return false
        }
        
        // Luhn算法验证
        var sum = 0
        var alternate = false
        
        for (i in cleanNumber.length - 1 downTo 0) {
            var digit = cleanNumber[i].toString().toInt()
            
            if (alternate) {
                digit *= 2
                if (digit > 9) {
                    digit -= 9
                }
            }
            
            sum += digit
            alternate = !alternate
        }
        
        return sum % 10 == 0
    }
    
    /**
     * 验证有效期格式是否正确
     * 
     * @param expiryDate 有效期 (格式: MM/YY 或 MM/YYYY)
     * @return 是否有效
     */
    fun isValidExpiryDate(expiryDate: String): Boolean {
        val regex = Regex("^(0[1-9]|1[0-2])/([0-9]{2}|[0-9]{4})$")
        return regex.matches(expiryDate)
    }
    
    /**
     * 验证CVV格式是否正确
     * 
     * @param cvv CVV安全码
     * @param isAmex 是否为American Express卡（4位CVV）
     * @return 是否有效
     */
    fun isValidCVV(cvv: String, isAmex: Boolean = false): Boolean {
        val expectedLength = if (isAmex) 4 else 3
        return cvv.length == expectedLength && cvv.all { it.isDigit() }
    }
}

