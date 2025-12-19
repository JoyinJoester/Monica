package takagi.ru.monica.utils

/**
 * Phase 7: 字段验证工具
 * 
 * 提供各种字段的格式验证功能
 */
object FieldValidation {
    
    /**
     * 验证邮箱格式
     * 
     * @param email 邮箱地址
     * @return true if valid or empty
     */
    fun isValidEmail(email: String): Boolean {
        if (email.isEmpty()) return true // 允许为空
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return emailRegex.matches(email)
    }
    
    /**
     * 验证手机号格式 (中国)
     * 
     * @param phone 手机号码
     * @return true if valid or empty
     */
    fun isValidPhone(phone: String): Boolean {
        if (phone.isEmpty()) return true
        val cleanPhone = phone.replace(" ", "").replace("-", "")
        
        // 中国手机号: 1开头, 第二位3-9, 共11位
        val phoneRegex = "^1[3-9]\\d{9}$".toRegex()
        return phoneRegex.matches(cleanPhone)
    }
    
    /**
     * 验证邮编格式 (中国6位)
     * 
     * @param zipCode 邮政编码
     * @return true if valid or empty
     */
    fun isValidZipCode(zipCode: String): Boolean {
        if (zipCode.isEmpty()) return true
        val zipRegex = "^\\d{6}$".toRegex()
        return zipRegex.matches(zipCode.trim())
    }
    
    /**
     * 验证信用卡号 (使用Luhn算法)
     * 
     * @param cardNumber 信用卡号
     * @return true if valid or empty
     */
    fun isValidCreditCard(cardNumber: String): Boolean {
        if (cardNumber.isEmpty()) return true
        
        val cleanNumber = cardNumber.replace(" ", "").replace("-", "")
        
        // 信用卡号长度通常为 13-19 位
        if (cleanNumber.length !in 13..19) return false
        
        // 必须全部是数字
        if (!cleanNumber.all { it.isDigit() }) return false
        
        // Luhn 算法验证
        var sum = 0
        var alternate = false
        for (i in cleanNumber.length - 1 downTo 0) {
            var n = cleanNumber[i].toString().toInt()
            if (alternate) {
                n *= 2
                if (n > 9) {
                    n = n % 10 + 1
                }
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }
    
    /**
     * 验证信用卡有效期
     * 格式: MM/YY
     * 
     * @param expiry 有效期字符串
     * @return true if valid or empty
     */
    fun isValidExpiry(expiry: String): Boolean {
        if (expiry.isEmpty()) return true
        
        // 格式验证: MM/YY
        val expiryRegex = "^(0[1-9]|1[0-2])/\\d{2}$".toRegex()
        if (!expiryRegex.matches(expiry)) return false
        
        try {
            val parts = expiry.split("/")
            val month = parts[0].toInt()
            val year = 2000 + parts[1].toInt()
            
            // 检查是否已过期
            val now = java.util.Calendar.getInstance()
            val currentYear = now.get(java.util.Calendar.YEAR)
            val currentMonth = now.get(java.util.Calendar.MONTH) + 1
            
            return year > currentYear || (year == currentYear && month >= currentMonth)
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * 验证CVV安全码
     * 通常为 3-4 位数字
     * 
     * @param cvv CVV安全码
     * @return true if valid or empty
     */
    fun isValidCVV(cvv: String): Boolean {
        if (cvv.isEmpty()) return true
        return cvv.matches("^\\d{3,4}$".toRegex())
    }
    
    /**
     * 格式化信用卡号显示
     * 每4位加一个空格
     * 
     * 示例: "1234567890123456" -> "1234 5678 9012 3456"
     * 
     * @param cardNumber 原始卡号
     * @return 格式化后的卡号
     */
    fun formatCreditCard(cardNumber: String): String {
        val clean = cardNumber.replace(" ", "").replace("-", "")
        return clean.chunked(4).joinToString(" ")
    }
    
    /**
     * 格式化手机号显示 (中国)
     * 格式: 138 0000 0000
     * 
     * @param phone 原始手机号
     * @return 格式化后的手机号
     */
    fun formatPhone(phone: String): String {
        val clean = phone.replace(" ", "").replace("-", "")
        
        // 中国手机号: 3-4-4 分隔
        if (clean.length == 11 && clean.startsWith("1")) {
            return "${clean.substring(0, 3)} ${clean.substring(3, 7)} ${clean.substring(7)}"
        }
        
        return phone
    }
    
    /**
     * 掩码显示信用卡号
     * 只显示后4位, 其余用*代替
     * 
     * 示例: "1234567890123456" -> "**** **** **** 3456"
     * 
     * @param cardNumber 原始卡号
     * @return 掩码后的卡号
     */
    fun maskCreditCard(cardNumber: String): String {
        val clean = cardNumber.replace(" ", "").replace("-", "")
        if (clean.length < 4) return "****"
        
        val lastFour = clean.takeLast(4)
        val masked = "*".repeat((clean.length - 4).coerceAtLeast(0))
        return formatCreditCard(masked + lastFour)
    }
    
    /**
     * 掩码显示手机号
     * 只显示前3位和后4位
     * 
     * 示例: "13800000000" -> "138 **** 0000"
     * 
     * @param phone 原始手机号
     * @return 掩码后的手机号
     */
    fun maskPhone(phone: String): String {
        val clean = phone.replace(" ", "").replace("-", "")
        
        if (clean.length == 11 && clean.startsWith("1")) {
            return "${clean.substring(0, 3)} **** ${clean.substring(7)}"
        }
        
        return phone
    }
    
    /**
     * 获取邮箱验证错误消息
     */
    fun getEmailError(email: String): String? {
        return if (!isValidEmail(email)) {
            "邮箱格式不正确"
        } else null
    }
    
    /**
     * 获取手机号验证错误消息
     */
    fun getPhoneError(phone: String): String? {
        return if (!isValidPhone(phone)) {
            "手机号格式不正确 (应为11位数字)"
        } else null
    }
    
    /**
     * 获取邮编验证错误消息
     */
    fun getZipCodeError(zipCode: String): String? {
        return if (!isValidZipCode(zipCode)) {
            "邮编格式不正确 (应为6位数字)"
        } else null
    }
    
    /**
     * 获取信用卡号验证错误消息
     */
    fun getCreditCardError(cardNumber: String): String? {
        return if (!isValidCreditCard(cardNumber)) {
            "信用卡号格式不正确或校验失败"
        } else null
    }
    
    /**
     * 获取有效期验证错误消息
     */
    fun getExpiryError(expiry: String): String? {
        return when {
            expiry.isEmpty() -> null
            !expiry.matches("^(0[1-9]|1[0-2])/\\d{2}$".toRegex()) -> "有效期格式不正确 (应为MM/YY)"
            !isValidExpiry(expiry) -> "信用卡已过期"
            else -> null
        }
    }
    
    /**
     * 获取CVV验证错误消息
     */
    fun getCVVError(cvv: String): String? {
        return if (!isValidCVV(cvv)) {
            "CVV格式不正确 (应为3-4位数字)"
        } else null
    }
}
