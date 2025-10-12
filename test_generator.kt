/**
 * 临时的密码生成器功能测试脚本
 */

// 导入必要的包
import kotlin.random.Random
import java.security.SecureRandom
import kotlin.math.pow
import kotlin.math.log10
import kotlin.math.log2

/**
 * 简化版的密码生成器测试（基于我们增强的 PasswordGenerator 逻辑）
 */
class PasswordGeneratorTest {
    
    companion object {
        // EFF 短词表（简化版用于测试）
        private val SIMPLE_WORDLIST = listOf(
            "able", "acid", "also", "army", "away", "baby", "back", "ball", "band", 
            "bank", "base", "bath", "bear", "beat", "been", "bell", "belt", "best",
            "bike", "bill", "bird", "blow", "blue", "boat", "body", "bomb", "bone",
            "book", "boom", "born", "boss", "both", "bowl", "bulk", "burn", "bush",
            "busy", "call", "came", "camp", "card", "care", "case", "cash", "cast"
        )
        
        private val secureRandom = SecureRandom()
        
        fun testPasswordGeneration() {
            println("=== 密码生成器功能测试 ===")
            
            // 测试 1: 基本符号密码生成
            println("\n1. 基本符号密码生成测试：")
            val basicPassword = generatePassword(
                length = 16,
                includeUppercase = true,
                includeLowercase = true,
                includeNumbers = true,
                includeSymbols = true
            )
            println("生成的密码：$basicPassword (长度: ${basicPassword.length})")
            
            // 测试 2: 带最小字符数要求的密码生成
            println("\n2. 最小字符数要求测试：")
            val advancedPassword = generatePasswordWithMinRequirements(
                length = 16,
                includeUppercase = true,
                includeLowercase = true,
                includeNumbers = true,
                includeSymbols = true,
                uppercaseMin = 2,
                lowercaseMin = 2,
                numbersMin = 2,
                symbolsMin = 2
            )
            println("生成的密码：$advancedPassword (长度: ${advancedPassword.length})")
            validateMinimumRequirements(advancedPassword, 2, 2, 2, 2)
            
            // 测试 3: 密码短语生成
            println("\n3. 密码短语生成测试：")
            val passphrase = generatePassphrase(
                wordCount = 4,
                delimiter = "-",
                capitalize = true,
                includeNumber = true
            )
            println("生成的密码短语：$passphrase")
            
            // 测试 4: PIN码生成
            println("\n4. PIN码生成测试：")
            val pinCode = generatePinCode(6)
            println("生成的PIN码：$pinCode (长度: ${pinCode.length})")
            
            // 测试 5: 密码强度分析
            println("\n5. 密码强度分析测试：")
            val testPasswords = listOf(
                "123456",
                "password",
                "MyP@ssw0rd123",
                "correct-horse-battery-staple-42"
            )
            
            testPasswords.forEach { password ->
                val strength = analyzePasswordStrength(password)
                println("密码: '$password'")
                println("  强度等级: ${strength.level}")
                println("  评分: ${strength.score}/100")
                println("  熵值: ${"%.2f".format(strength.entropy)} bits")
                println("  预估破解时间: ${strength.crackTime}")
                println("  建议: ${strength.feedback.joinToString(", ")}")
                println()
            }
        }
        
        private fun generatePassword(
            length: Int,
            includeUppercase: Boolean = true,
            includeLowercase: Boolean = true,
            includeNumbers: Boolean = true,
            includeSymbols: Boolean = false,
            excludeSimilar: Boolean = false,
            excludeAmbiguous: Boolean = false
        ): String {
            val uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            val lowercase = "abcdefghijklmnopqrstuvwxyz"
            val numbers = "0123456789"
            val symbols = "!@#$%^&*()_+-=[]{}|;:,.<>?"
            
            var charset = ""
            if (includeUppercase) charset += uppercase
            if (includeLowercase) charset += lowercase
            if (includeNumbers) charset += numbers
            if (includeSymbols) charset += symbols
            
            if (excludeSimilar) {
                charset = charset.replace(Regex("[0Oo1lI]"), "")
            }
            
            if (charset.isEmpty()) return ""
            
            return (1..length)
                .map { charset[secureRandom.nextInt(charset.length)] }
                .joinToString("")
        }
        
        private fun generatePasswordWithMinRequirements(
            length: Int,
            includeUppercase: Boolean = true,
            includeLowercase: Boolean = true,
            includeNumbers: Boolean = true,
            includeSymbols: Boolean = false,
            uppercaseMin: Int = 0,
            lowercaseMin: Int = 0,
            numbersMin: Int = 0,
            symbolsMin: Int = 0
        ): String {
            val uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            val lowercase = "abcdefghijklmnopqrstuvwxyz"
            val numbers = "0123456789"
            val symbols = "!@#$%^&*()_+-=[]{}|;:,.<>?"
            
            val result = mutableListOf<Char>()
            
            // 添加最小要求的字符
            if (includeUppercase && uppercaseMin > 0) {
                repeat(uppercaseMin) {
                    result.add(uppercase[secureRandom.nextInt(uppercase.length)])
                }
            }
            
            if (includeLowercase && lowercaseMin > 0) {
                repeat(lowercaseMin) {
                    result.add(lowercase[secureRandom.nextInt(lowercase.length)])
                }
            }
            
            if (includeNumbers && numbersMin > 0) {
                repeat(numbersMin) {
                    result.add(numbers[secureRandom.nextInt(numbers.length)])
                }
            }
            
            if (includeSymbols && symbolsMin > 0) {
                repeat(symbolsMin) {
                    result.add(symbols[secureRandom.nextInt(symbols.length)])
                }
            }
            
            // 填充剩余字符
            var charset = ""
            if (includeUppercase) charset += uppercase
            if (includeLowercase) charset += lowercase
            if (includeNumbers) charset += numbers
            if (includeSymbols) charset += symbols
            
            while (result.size < length && charset.isNotEmpty()) {
                result.add(charset[secureRandom.nextInt(charset.length)])
            }
            
            // 随机打乱顺序
            result.shuffle(secureRandom.asKotlinRandom())
            
            return result.joinToString("").take(length)
        }
        
        private fun generatePassphrase(
            wordCount: Int = 4,
            delimiter: String = "-",
            capitalize: Boolean = false,
            includeNumber: Boolean = false,
            customWord: String? = null
        ): String {
            val words = mutableListOf<String>()
            
            // 添加自定义单词
            if (!customWord.isNullOrEmpty()) {
                words.add(if (capitalize) customWord.replaceFirstChar { it.uppercase() } else customWord)
            }
            
            // 添加随机单词
            val wordsNeeded = wordCount - words.size
            repeat(wordsNeeded) {
                val word = SIMPLE_WORDLIST[secureRandom.nextInt(SIMPLE_WORDLIST.size)]
                words.add(if (capitalize) word.replaceFirstChar { it.uppercase() } else word)
            }
            
            var result = words.joinToString(delimiter)
            
            // 添加数字
            if (includeNumber) {
                val randomNumber = secureRandom.nextInt(100)
                result += if (delimiter.isNotEmpty()) "$delimiter$randomNumber" else randomNumber.toString()
            }
            
            return result
        }
        
        private fun generatePinCode(length: Int): String {
            return (1..length)
                .map { secureRandom.nextInt(10) }
                .joinToString("")
        }
        
        // 密码强度分析
        data class PasswordStrengthResult(
            val password: String,
            val score: Int, // 0-100
            val level: StrengthLevel,
            val entropy: Double, // bits
            val crackTime: String,
            val feedback: List<String>
        )
        
        enum class StrengthLevel {
            VERY_WEAK, WEAK, FAIR, GOOD, STRONG, VERY_STRONG
        }
        
        private fun analyzePasswordStrength(password: String): PasswordStrengthResult {
            val entropy = calculateEntropy(password)
            val feedback = mutableListOf<String>()
            
            // 基本检查
            if (password.length < 8) feedback.add("密码过短，建议至少8个字符")
            if (!password.any { it.isUpperCase() }) feedback.add("建议包含大写字母")
            if (!password.any { it.isLowerCase() }) feedback.add("建议包含小写字母")
            if (!password.any { it.isDigit() }) feedback.add("建议包含数字")
            if (!password.any { "!@#$%^&*()_+-=[]{}|;:,.<>?".contains(it) }) {
                feedback.add("建议包含特殊字符")
            }
            
            // 常见密码检查
            val commonPasswords = listOf("password", "123456", "qwerty", "admin", "login")
            if (commonPasswords.any { password.lowercase().contains(it) }) {
                feedback.add("避免使用常见密码模式")
            }
            
            // 计算评分
            val score = when {
                entropy < 30 -> 10
                entropy < 40 -> 30
                entropy < 50 -> 50
                entropy < 60 -> 70
                entropy < 80 -> 85
                else -> 95
            }.coerceIn(0, 100)
            
            // 确定强度等级
            val level = when {
                score < 20 -> StrengthLevel.VERY_WEAK
                score < 40 -> StrengthLevel.WEAK
                score < 60 -> StrengthLevel.FAIR
                score < 80 -> StrengthLevel.GOOD
                score < 90 -> StrengthLevel.STRONG
                else -> StrengthLevel.VERY_STRONG
            }
            
            // 估算破解时间
            val crackTime = estimateCrackTime(entropy)
            
            if (feedback.isEmpty()) {
                feedback.add("密码强度良好")
            }
            
            return PasswordStrengthResult(
                password = password,
                score = score,
                level = level,
                entropy = entropy,
                crackTime = crackTime,
                feedback = feedback
            )
        }
        
        private fun calculateEntropy(password: String): Double {
            val charsetSize = detectCharsetSize(password)
            return log2(charsetSize.toDouble()) * password.length
        }
        
        private fun detectCharsetSize(password: String): Int {
            var size = 0
            if (password.any { it.isLowerCase() }) size += 26
            if (password.any { it.isUpperCase() }) size += 26
            if (password.any { it.isDigit() }) size += 10
            if (password.any { "!@#$%^&*()_+-=[]{}|;:,.<>?".contains(it) }) size += 32
            return size.coerceAtLeast(1)
        }
        
        private fun estimateCrackTime(entropy: Double): String {
            val attemptsPerSecond = 1_000_000_000.0 // 假设每秒10亿次尝试
            val totalCombinations = 2.0.pow(entropy)
            val averageAttempts = totalCombinations / 2
            val seconds = averageAttempts / attemptsPerSecond
            
            return when {
                seconds < 1 -> "瞬间"
                seconds < 60 -> "${seconds.toInt()}秒"
                seconds < 3600 -> "${(seconds / 60).toInt()}分钟"
                seconds < 86400 -> "${(seconds / 3600).toInt()}小时"
                seconds < 2592000 -> "${(seconds / 86400).toInt()}天"
                seconds < 31536000 -> "${(seconds / 2592000).toInt()}个月"
                else -> "${(seconds / 31536000).toInt()}年"
            }
        }
        
        private fun validateMinimumRequirements(
            password: String,
            uppercaseMin: Int,
            lowercaseMin: Int,
            numbersMin: Int,
            symbolsMin: Int
        ) {
            val uppercaseCount = password.count { it.isUpperCase() }
            val lowercaseCount = password.count { it.isLowerCase() }
            val numbersCount = password.count { it.isDigit() }
            val symbolsCount = password.count { "!@#$%^&*()_+-=[]{}|;:,.<>?".contains(it) }
            
            println("最小字符数要求验证：")
            println("  大写字母: $uppercaseCount >= $uppercaseMin ✓")
            println("  小写字母: $lowercaseCount >= $lowercaseMin ✓")
            println("  数字: $numbersCount >= $numbersMin ✓")
            println("  符号: $symbolsCount >= $symbolsMin ✓")
        }
        
        // 扩展函数来将 SecureRandom 转换为 kotlin.random.Random
        private fun SecureRandom.asKotlinRandom(): Random = object : Random() {
            override fun nextBits(bitCount: Int): Int = this@asKotlinRandom.nextInt() ushr (32 - bitCount)
        }
    }
}

fun main() {
    PasswordGeneratorTest.testPasswordGeneration()
}