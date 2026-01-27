package takagi.ru.monica.utils

import java.security.SecureRandom

/**
 * Strong password generator with customizable options
 */
class PasswordGenerator {
    
    companion object {
        private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val NUMBERS = "0123456789"
        private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"
        
        private val random = SecureRandom()
    }
    
    data class PasswordOptions(
        val length: Int = 16,
        val includeUppercase: Boolean = true,
        val includeLowercase: Boolean = true,
        val includeNumbers: Boolean = true,
        val includeSymbols: Boolean = true,
        val excludeSimilar: Boolean = true // Exclude similar characters like 0, O, l, I
    )
    
    fun generatePassword(options: PasswordOptions = PasswordOptions()): String {
        if (options.length < 4) {
            throw IllegalArgumentException("Password length must be at least 4 characters")
        }
        
        var characterSet = ""
        val requiredChars = mutableListOf<Char>()
        
        if (options.includeLowercase) {
            val chars = if (options.excludeSimilar) LOWERCASE.replace("l", "") else LOWERCASE
            characterSet += chars
            requiredChars.add(chars[random.nextInt(chars.length)])
        }
        
        if (options.includeUppercase) {
            val chars = if (options.excludeSimilar) UPPERCASE.replace("I", "").replace("O", "") else UPPERCASE
            characterSet += chars
            requiredChars.add(chars[random.nextInt(chars.length)])
        }
        
        if (options.includeNumbers) {
            val chars = if (options.excludeSimilar) NUMBERS.replace("0", "").replace("1", "") else NUMBERS
            characterSet += chars
            requiredChars.add(chars[random.nextInt(chars.length)])
        }
        
        if (options.includeSymbols) {
            characterSet += SYMBOLS
            requiredChars.add(SYMBOLS[random.nextInt(SYMBOLS.length)])
        }
        
        if (characterSet.isEmpty()) {
            throw IllegalArgumentException("At least one character type must be selected")
        }
        
        val password = StringBuilder()
        
        // Add required characters first
        requiredChars.forEach { password.append(it) }
        
        // Fill remaining positions with random characters
        repeat(options.length - requiredChars.size) {
            password.append(characterSet[random.nextInt(characterSet.length)])
        }
        
        // Shuffle the password to avoid predictable patterns
        return password.toString().toCharArray().apply { 
            for (i in indices) {
                val randomIndex = random.nextInt(size)
                val temp = this[i]
                this[i] = this[randomIndex]
                this[randomIndex] = temp
            }
        }.concatToString()
    }
    
    /**
     * Calculate password strength (0-100)
     */
    fun calculatePasswordStrength(password: String): Int {
        if (password.isEmpty()) return 0
        
        var score = 0
        val length = password.length
        
        // Length bonus
        score += when {
            length >= 16 -> 25
            length >= 12 -> 20
            length >= 8 -> 15
            length >= 6 -> 10
            else -> 5
        }
        
        // Character variety bonus
        val hasLowercase = password.any { it.isLowerCase() }
        val hasUppercase = password.any { it.isUpperCase() }
        val hasNumbers = password.any { it.isDigit() }
        val hasSymbols = password.any { !it.isLetterOrDigit() }
        
        var varietyCount = 0
        if (hasLowercase) varietyCount++
        if (hasUppercase) varietyCount++
        if (hasNumbers) varietyCount++
        if (hasSymbols) varietyCount++
        
        score += varietyCount * 15
        
        // Deductions for common patterns
        if (password.lowercase().contains("password")) score -= 20
        if (password.matches(Regex("\\d+"))) score -= 20 // All numbers
        if (password.matches(Regex("[a-zA-Z]+"))) score -= 10 // All letters
        
        // Repetition penalty
        val uniqueChars = password.toSet().size
        val repetitionRatio = uniqueChars.toFloat() / length
        if (repetitionRatio < 0.5) score -= 15
        
        return score.coerceIn(0, 100)
    }
    
    /**
     * Get password strength description
     */
    fun getPasswordStrengthDescription(strength: Int): String {
        return when {
            strength >= 80 -> "Very Strong"
            strength >= 60 -> "Strong"
            strength >= 40 -> "Moderate"
            strength >= 20 -> "Weak"
            else -> "Very Weak"
        }
    }
}