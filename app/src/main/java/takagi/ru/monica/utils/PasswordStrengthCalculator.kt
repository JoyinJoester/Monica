package takagi.ru.monica.utils

/**
 * 密码强度计算器
 * 评估密码的安全强度
 */
object PasswordStrengthCalculator {
    
    enum class PasswordStrength {
        WEAK,           // 弱: 0-40分
        FAIR,           // 一般: 41-60分
        GOOD,           // 良好: 61-75分
        STRONG,         // 强: 76-90分
        VERY_STRONG     // 非常强: 91-100分
    }
    
    /**
     * 计算密码强度
     * @param password 要评估的密码
     * @return PasswordStrength 强度等级
     */
    fun calculateStrength(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.WEAK
        
        var score = 0
        
        // 1. 长度评分 (最高30分)
        score += when {
            password.length < 6 -> 0
            password.length < 8 -> 5
            password.length < 10 -> 10
            password.length < 12 -> 15
            password.length < 16 -> 20
            password.length < 20 -> 25
            else -> 30
        }
        
        // 2. 字符多样性评分 (最高40分)
        val hasLowercase = password.any { it.isLowerCase() }
        val hasUppercase = password.any { it.isUpperCase() }
        val hasDigits = password.any { it.isDigit() }
        val hasSymbols = password.any { !it.isLetterOrDigit() }
        
        var diversityCount = 0
        if (hasLowercase) diversityCount++
        if (hasUppercase) diversityCount++
        if (hasDigits) diversityCount++
        if (hasSymbols) diversityCount++
        
        score += when (diversityCount) {
            1 -> 5
            2 -> 15
            3 -> 25
            4 -> 40
            else -> 0
        }
        
        // 3. 字符种类数量评分 (最高20分)
        val uniqueChars = password.toSet().size
        score += when {
            uniqueChars < 4 -> 0
            uniqueChars < 6 -> 5
            uniqueChars < 8 -> 10
            uniqueChars < 10 -> 15
            else -> 20
        }
        
        // 4. 惩罚项: 重复字符
        val consecutiveRepeats = countConsecutiveRepeats(password)
        score -= consecutiveRepeats * 5
        
        // 5. 惩罚项: 连续字符 (如 abc, 123)
        val consecutiveSequences = countConsecutiveSequences(password)
        score -= consecutiveSequences * 5
        
        // 6. 奖励项: 混合复杂性
        if (password.length >= 12 && diversityCount >= 3) {
            score += 10
        }
        
        // 确保分数在 0-100 范围内
        score = score.coerceIn(0, 100)
        
        return when {
            score <= 40 -> PasswordStrength.WEAK
            score <= 60 -> PasswordStrength.FAIR
            score <= 75 -> PasswordStrength.GOOD
            score <= 90 -> PasswordStrength.STRONG
            else -> PasswordStrength.VERY_STRONG
        }
    }
    
    /**
     * 统计连续重复字符的数量
     * 例如: "aaa" 返回 2, "aaabbb" 返回 4
     */
    private fun countConsecutiveRepeats(password: String): Int {
        if (password.length < 2) return 0
        
        var count = 0
        for (i in 0 until password.length - 1) {
            if (password[i] == password[i + 1]) {
                count++
            }
        }
        return count
    }
    
    /**
     * 统计连续序列字符的数量
     * 例如: "abc" 返回 2, "123" 返回 2
     */
    private fun countConsecutiveSequences(password: String): Int {
        if (password.length < 3) return 0
        
        var count = 0
        for (i in 0 until password.length - 2) {
            val char1 = password[i]
            val char2 = password[i + 1]
            val char3 = password[i + 2]
            
            // 检查是否为递增序列 (如 abc, 123)
            if (char2.code == char1.code + 1 && char3.code == char2.code + 1) {
                count++
            }
            // 检查是否为递减序列 (如 cba, 321)
            else if (char2.code == char1.code - 1 && char3.code == char2.code - 1) {
                count++
            }
        }
        return count
    }
    
    /**
     * 获取密码强度的描述文本
     */
    fun getStrengthDescription(strength: PasswordStrength): String {
        return when (strength) {
            PasswordStrength.WEAK -> "此密码较弱,建议增加长度并使用多种字符类型"
            PasswordStrength.FAIR -> "此密码一般,建议增加长度或字符多样性"
            PasswordStrength.GOOD -> "此密码良好,但仍有改进空间"
            PasswordStrength.STRONG -> "此密码很强,足以保护您的账户"
            PasswordStrength.VERY_STRONG -> "此密码非常强,极难破解"
        }
    }
    
    /**
     * 检查密码是否符合最低安全要求
     * @param password 要检查的密码
     * @param minLength 最小长度要求
     * @param requireUppercase 是否要求大写字母
     * @param requireLowercase 是否要求小写字母
     * @param requireDigit 是否要求数字
     * @param requireSymbol 是否要求特殊字符
     * @return Pair<Boolean, String> (是否符合要求, 不符合的原因)
     */
    fun checkPasswordRequirements(
        password: String,
        minLength: Int = 8,
        requireUppercase: Boolean = true,
        requireLowercase: Boolean = true,
        requireDigit: Boolean = true,
        requireSymbol: Boolean = false
    ): Pair<Boolean, String> {
        val errors = mutableListOf<String>()
        
        if (password.length < minLength) {
            errors.add("密码长度至少需要 $minLength 个字符")
        }
        
        if (requireUppercase && !password.any { it.isUpperCase() }) {
            errors.add("需要至少一个大写字母")
        }
        
        if (requireLowercase && !password.any { it.isLowerCase() }) {
            errors.add("需要至少一个小写字母")
        }
        
        if (requireDigit && !password.any { it.isDigit() }) {
            errors.add("需要至少一个数字")
        }
        
        if (requireSymbol && !password.any { !it.isLetterOrDigit() }) {
            errors.add("需要至少一个特殊字符")
        }
        
        return if (errors.isEmpty()) {
            Pair(true, "")
        } else {
            Pair(false, errors.joinToString("; "))
        }
    }
}
