package takagi.ru.monica.generator

import java.security.SecureRandom
import kotlin.math.absoluteValue

/**
 * 🔐 增强的密码生成器
 * 
 * 基于 Keyguard 的 GetPasswordImpl 实现，融合 Monica 的设计风格
 * 
 * 核心特性：
 * - ✅ 最小字符数要求（大写/小写/数字/符号）
 * - ✅ SecureRandom 安全随机数生成
 * - ✅ 字符集完全自定义
 * - ✅ 排除相似字符和模糊字符
 * - ✅ 密码打乱确保随机性
 * 
 * 参考：keyguard-app-master/common/src/commonMain/kotlin/com/artemchep/keyguard/common/usecase/impl/GetPasswordImpl.kt
 */
class EnhancedPasswordGenerator {
    
    companion object {
        // 默认字符集
        private const val UPPERCASE_DEFAULT = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val LOWERCASE_DEFAULT = "abcdefghijklmnopqrstuvwxyz"
        private const val NUMBERS_DEFAULT = "0123456789"
        private const val SYMBOLS_DEFAULT = "!@#$%^&*()_+-=[]{}|;:,.<>?"
        
        // 相似字符（容易混淆）
        private const val SIMILAR_CHARS = "0Ol1I"
        
        // 模糊字符（某些字体难以区分）
        private const val AMBIGUOUS_CHARS = "{}[]()/\\'\"`~,;:.<>"
        
        // 安全随机数生成器
        private val secureRandom = SecureRandom()
        
        /**
         * 生成增强密码
         * 
         * @param config 密码配置
         * @return 生成的密码，如果配置无效则返回空字符串
         */
        fun generate(config: PasswordConfig): String {
            // 验证配置有效性
            if (config.length < 1 || config.allChars.isEmpty()) {
                return ""
            }
            
            val output = mutableListOf<Char>()
            
            // ✨ Phase 1: 确保满足最小字符数要求（Keyguard 核心特性）
            var curUppercaseMin = 0
            var curLowercaseMin = 0
            var curNumbersMin = 0
            var curSymbolsMin = 0
            
            do {
                var shouldContinue = false
                
                // 添加大写字母
                if (curUppercaseMin < config.uppercaseMin) {
                    curUppercaseMin++
                    shouldContinue = true
                    if (config.uppercaseChars.isNotEmpty()) {
                        output += config.uppercaseChars.randomChar()
                    }
                }
                
                // 添加小写字母
                if (curLowercaseMin < config.lowercaseMin) {
                    curLowercaseMin++
                    shouldContinue = true
                    if (config.lowercaseChars.isNotEmpty()) {
                        output += config.lowercaseChars.randomChar()
                    }
                }
                
                // 添加数字
                if (curNumbersMin < config.numbersMin) {
                    curNumbersMin++
                    shouldContinue = true
                    if (config.numberChars.isNotEmpty()) {
                        output += config.numberChars.randomChar()
                    }
                }
                
                // 添加符号
                if (curSymbolsMin < config.symbolsMin) {
                    curSymbolsMin++
                    shouldContinue = true
                    if (config.symbolChars.isNotEmpty()) {
                        output += config.symbolChars.randomChar()
                    }
                }
            } while (shouldContinue)
            
            // ✨ Phase 2: 填充剩余长度
            repeat(config.length - output.size) {
                output += config.allChars.randomChar()
            }
            
            // ✨ Phase 3: 使用 SecureRandom 打乱顺序（确保随机性）
            val shuffled = output
                .take(config.length)
                .shuffled(secureRandom)
                .toCharArray()
            
            return String(shuffled)
        }
        
        /**
         * 从字符串中随机选择一个字符（使用 SecureRandom）
         */
        private fun String.randomChar(): Char {
            if (isEmpty()) return ' '
            val index = secureRandom.nextInt(length).absoluteValue
            return this[index % length]
        }
        
        /**
         * 创建默认配置
         */
        fun createDefaultConfig(length: Int = 16): PasswordConfig {
            return PasswordConfig(
                length = length,
                uppercaseChars = UPPERCASE_DEFAULT,
                lowercaseChars = LOWERCASE_DEFAULT,
                numberChars = NUMBERS_DEFAULT,
                symbolChars = SYMBOLS_DEFAULT,
                uppercaseMin = 1,
                lowercaseMin = 1,
                numbersMin = 1,
                symbolsMin = 1
            )
        }
        
        /**
         * 创建强密码配置（高安全性）
         */
        fun createStrongConfig(length: Int = 20): PasswordConfig {
            return PasswordConfig(
                length = length,
                uppercaseChars = UPPERCASE_DEFAULT,
                lowercaseChars = LOWERCASE_DEFAULT,
                numberChars = NUMBERS_DEFAULT,
                symbolChars = SYMBOLS_DEFAULT,
                uppercaseMin = 3,
                lowercaseMin = 3,
                numbersMin = 3,
                symbolsMin = 3,
                excludeSimilar = true
            )
        }
        
        /**
         * 创建易读配置（排除相似和模糊字符）
         */
        fun createReadableConfig(length: Int = 16): PasswordConfig {
            return PasswordConfig(
                length = length,
                uppercaseChars = UPPERCASE_DEFAULT,
                lowercaseChars = LOWERCASE_DEFAULT,
                numberChars = NUMBERS_DEFAULT,
                symbolChars = "!@#$%&*+-=?",  // 简化符号集
                uppercaseMin = 1,
                lowercaseMin = 1,
                numbersMin = 1,
                symbolsMin = 1,
                excludeSimilar = true,
                excludeAmbiguous = true
            )
        }
    }
}

/**
 * 密码配置数据类
 * 
 * @param length 密码长度（推荐 12-20）
 * @param uppercaseChars 大写字母字符集
 * @param lowercaseChars 小写字母字符集
 * @param numberChars 数字字符集
 * @param symbolChars 符号字符集
 * @param uppercaseMin 最少大写字母数量（0 = 不要求）
 * @param lowercaseMin 最少小写字母数量（0 = 不要求）
 * @param numbersMin 最少数字数量（0 = 不要求）
 * @param symbolsMin 最少符号数量（0 = 不要求）
 * @param excludeSimilar 排除相似字符（0, O, l, I, 1）
 * @param excludeAmbiguous 排除模糊字符（{}, [], (), 等）
 */
data class PasswordConfig(
    val length: Int = 16,
    val uppercaseChars: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
    val lowercaseChars: String = "abcdefghijklmnopqrstuvwxyz",
    val numberChars: String = "0123456789",
    val symbolChars: String = "!@#$%^&*()_+-=[]{}|;:,.<>?",
    val uppercaseMin: Int = 0,
    val lowercaseMin: Int = 0,
    val numbersMin: Int = 0,
    val symbolsMin: Int = 0,
    val excludeSimilar: Boolean = false,
    val excludeAmbiguous: Boolean = false
) {
    /**
     * 所有可用字符（自动过滤）
     */
    val allChars: String
        get() {
            val chars = buildString {
                if (uppercaseChars.isNotEmpty()) append(uppercaseChars)
                if (lowercaseChars.isNotEmpty()) append(lowercaseChars)
                if (numberChars.isNotEmpty()) append(numberChars)
                if (symbolChars.isNotEmpty()) append(symbolChars)
            }
            
            return when {
                excludeSimilar && excludeAmbiguous -> {
                    chars.filter { it !in "0Ol1I" && it !in "{}[]()/\\'\"`~,;:.<>" }
                }
                excludeSimilar -> {
                    chars.filter { it !in "0Ol1I" }
                }
                excludeAmbiguous -> {
                    chars.filter { it !in "{}[]()/\\'\"`~,;:.<>" }
                }
                else -> chars
            }
        }
    
    /**
     * 验证配置有效性
     */
    fun isValid(): Boolean {
        // 检查长度
        if (length < 1) return false
        
        // 检查字符集
        if (allChars.isEmpty()) return false
        
        // 检查最小数量总和不超过长度
        val minSum = uppercaseMin + lowercaseMin + numbersMin + symbolsMin
        if (minSum > length) return false
        
        return true
    }
    
    /**
     * 获取配置描述（用于UI显示）
     */
    fun getDescription(): String {
        val parts = mutableListOf<String>()
        
        if (uppercaseChars.isNotEmpty()) parts.add("大写")
        if (lowercaseChars.isNotEmpty()) parts.add("小写")
        if (numberChars.isNotEmpty()) parts.add("数字")
        if (symbolChars.isNotEmpty()) parts.add("符号")
        
        val requirements = mutableListOf<String>()
        if (uppercaseMin > 0) requirements.add("至少${uppercaseMin}个大写")
        if (lowercaseMin > 0) requirements.add("至少${lowercaseMin}个小写")
        if (numbersMin > 0) requirements.add("至少${numbersMin}个数字")
        if (symbolsMin > 0) requirements.add("至少${symbolsMin}个符号")
        
        return buildString {
            append("${length}位密码，包含${parts.joinToString("、")}")
            if (requirements.isNotEmpty()) {
                append("（${requirements.joinToString("，")}）")
            }
            if (excludeSimilar) append("，排除相似字符")
            if (excludeAmbiguous) append("，排除模糊字符")
        }
    }
}

/**
 * 密码配置构建器（链式调用）
 */
class PasswordConfigBuilder {
    private var length: Int = 16
    private var uppercaseChars: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private var lowercaseChars: String = "abcdefghijklmnopqrstuvwxyz"
    private var numberChars: String = "0123456789"
    private var symbolChars: String = "!@#$%^&*()_+-=[]{}|;:,.<>?"
    private var uppercaseMin: Int = 0
    private var lowercaseMin: Int = 0
    private var numbersMin: Int = 0
    private var symbolsMin: Int = 0
    private var excludeSimilar: Boolean = false
    private var excludeAmbiguous: Boolean = false
    
    fun length(value: Int) = apply { this.length = value }
    fun uppercaseChars(value: String) = apply { this.uppercaseChars = value }
    fun lowercaseChars(value: String) = apply { this.lowercaseChars = value }
    fun numberChars(value: String) = apply { this.numberChars = value }
    fun symbolChars(value: String) = apply { this.symbolChars = value }
    fun uppercaseMin(value: Int) = apply { this.uppercaseMin = value }
    fun lowercaseMin(value: Int) = apply { this.lowercaseMin = value }
    fun numbersMin(value: Int) = apply { this.numbersMin = value }
    fun symbolsMin(value: Int) = apply { this.symbolsMin = value }
    fun excludeSimilar(value: Boolean) = apply { this.excludeSimilar = value }
    fun excludeAmbiguous(value: Boolean) = apply { this.excludeAmbiguous = value }
    
    /**
     * 快捷方法：启用所有字符类型
     */
    fun enableAll() = apply {
        uppercaseMin = 1
        lowercaseMin = 1
        numbersMin = 1
        symbolsMin = 1
    }
    
    /**
     * 快捷方法：只使用字母和数字（无符号）
     */
    fun alphanumericOnly() = apply {
        symbolChars = ""
        symbolsMin = 0
    }
    
    /**
     * 构建配置
     */
    fun build(): PasswordConfig {
        return PasswordConfig(
            length = length,
            uppercaseChars = uppercaseChars,
            lowercaseChars = lowercaseChars,
            numberChars = numberChars,
            symbolChars = symbolChars,
            uppercaseMin = uppercaseMin,
            lowercaseMin = lowercaseMin,
            numbersMin = numbersMin,
            symbolsMin = symbolsMin,
            excludeSimilar = excludeSimilar,
            excludeAmbiguous = excludeAmbiguous
        )
    }
}
