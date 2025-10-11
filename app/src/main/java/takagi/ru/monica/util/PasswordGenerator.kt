package takagi.ru.monica.util

import java.security.SecureRandom

/**
 * 密码生成器工具类
 */
class PasswordGenerator {
    companion object {
        private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        private const val NUMBERS = "0123456789"
        private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"
        
        private val random = SecureRandom()
        
        /**
         * 生成密码
         *
         * @param length 密码长度
         * @param includeUppercase 是否包含大写字母
         * @param includeLowercase 是否包含小写字母
         * @param includeNumbers 是否包含数字
         * @param includeSymbols 是否包含符号
         * @param excludeSimilar 是否排除相似字符 (0, O, l, I等)
         * @param excludeAmbiguous 是否排除不明确字符
         * @return 生成的密码
         */
        fun generatePassword(
            length: Int = 12,
            includeUppercase: Boolean = true,
            includeLowercase: Boolean = true,
            includeNumbers: Boolean = true,
            includeSymbols: Boolean = true,
            excludeSimilar: Boolean = false,
            excludeAmbiguous: Boolean = false
        ): String {
            if (length <= 0) return ""
            
            val charset = buildString {
                if (includeUppercase) append(UPPERCASE)
                if (includeLowercase) append(LOWERCASE)
                if (includeNumbers) append(NUMBERS)
                if (includeSymbols) append(SYMBOLS)
            }
            
            // 排除相似字符
            val filteredCharset = if (excludeSimilar) {
                charset.filter { char ->
                    char !in "0OlI1" || 
                    (!includeNumbers && char in "01") || 
                    (!includeUppercase && char == 'O') || 
                    (!includeLowercase && char == 'l')
                }
            } else {
                charset
            }
            
            // 排除不明确字符
            val finalCharset = if (excludeAmbiguous) {
                filteredCharset.filter { char ->
                    char !in "{}[]()/~`'" || 
                    (!includeSymbols || char !in "{}[]()/~`'")
                }
            } else {
                filteredCharset
            }
            
            if (finalCharset.isEmpty()) return ""
            
            return (1..length)
                .map { finalCharset[random.nextInt(finalCharset.length)] }
                .joinToString("")
        }
        
        /**
         * 计算密码强度
         *
         * @param password 密码
         * @return 强度值 (0-100)
         */
        fun calculatePasswordStrength(password: String): Int {
            var strength = 0
            
            // 长度加分
            strength += (password.length.coerceAtMost(20)) * 4
            
            // 字符类型加分
            if (password.any { it.isUpperCase() }) strength += 10
            if (password.any { it.isLowerCase() }) strength += 10
            if (password.any { it.isDigit() }) strength += 10
            if (password.any { it in SYMBOLS }) strength += 10
            
            // 字符多样性加分
            val uniqueChars = password.toSet().size
            strength += (uniqueChars.coerceAtMost(10)) * 2
            
            // 惩罚项 - 重复字符
            val charCounts = password.groupingBy { it }.eachCount()
            val repeatedChars = charCounts.values.count { it > 1 }
            strength -= repeatedChars * 2
            
            return strength.coerceIn(0, 100)
        }
        
        /**
         * 获取密码强度描述
         *
         * @param strength 强度值
         * @return 强度描述
         */
        fun getPasswordStrengthDescription(strength: Int): String {
            return when {
                strength < 20 -> "非常弱"
                strength < 40 -> "弱"
                strength < 60 -> "中等"
                strength < 80 -> "强"
                else -> "非常强"
            }
        }
    }
    
    /**
     * 密码选项数据类
     */
    data class PasswordOptions(
        val length: Int = 12,
        val includeUppercase: Boolean = true,
        val includeLowercase: Boolean = true,
        val includeNumbers: Boolean = true,
        val includeSymbols: Boolean = true,
        val excludeSimilar: Boolean = false
    )
}