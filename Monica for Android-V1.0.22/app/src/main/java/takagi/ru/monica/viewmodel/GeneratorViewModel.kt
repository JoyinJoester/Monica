package takagi.ru.monica.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for generator screen to persist state across navigation
 */
class GeneratorViewModel : ViewModel() {
    
    // 生成器类型状态
    private val _selectedGenerator = MutableStateFlow(GeneratorType.SYMBOL)
    val selectedGenerator: StateFlow<GeneratorType> = _selectedGenerator.asStateFlow()
    
    // 随机符号生成器状态
    private val _symbolLength = MutableStateFlow(12)
    val symbolLength: StateFlow<Int> = _symbolLength.asStateFlow()
    
    private val _includeUppercase = MutableStateFlow(true)
    val includeUppercase: StateFlow<Boolean> = _includeUppercase.asStateFlow()
    
    private val _includeLowercase = MutableStateFlow(true)
    val includeLowercase: StateFlow<Boolean> = _includeLowercase.asStateFlow()
    
    private val _includeNumbers = MutableStateFlow(true)
    val includeNumbers: StateFlow<Boolean> = _includeNumbers.asStateFlow()
    
    private val _includeSymbols = MutableStateFlow(true)
    val includeSymbols: StateFlow<Boolean> = _includeSymbols.asStateFlow()
    
    private val _excludeSimilar = MutableStateFlow(false)
    val excludeSimilar: StateFlow<Boolean> = _excludeSimilar.asStateFlow()
    
    private val _excludeAmbiguous = MutableStateFlow(false)
    val excludeAmbiguous: StateFlow<Boolean> = _excludeAmbiguous.asStateFlow()
    
    // ✨ New: Common Password Analysis
    private val _analyzeCommonPasswords = MutableStateFlow(false)
    val analyzeCommonPasswords: StateFlow<Boolean> = _analyzeCommonPasswords.asStateFlow()

    // 关联权重（0-100），数值越大越偏向沿用常见片段
    private val _analyzeWeight = MutableStateFlow(60)
    val analyzeWeight: StateFlow<Int> = _analyzeWeight.asStateFlow()

    // ✨ 新增：最小字符数要求（Keyguard 特性）
    private val _uppercaseMin = MutableStateFlow(0)
    val uppercaseMin: StateFlow<Int> = _uppercaseMin.asStateFlow()
    
    private val _lowercaseMin = MutableStateFlow(0)
    val lowercaseMin: StateFlow<Int> = _lowercaseMin.asStateFlow()
    
    private val _numbersMin = MutableStateFlow(0)
    val numbersMin: StateFlow<Int> = _numbersMin.asStateFlow()
    
    private val _symbolsMin = MutableStateFlow(0)
    val symbolsMin: StateFlow<Int> = _symbolsMin.asStateFlow()
    
    private val _symbolResult = MutableStateFlow("")
    val symbolResult: StateFlow<String> = _symbolResult.asStateFlow()
    
    // ✨ 密码短语生成器状态（重新设计）
    private val _passphraseWordCount = MutableStateFlow(4)
    val passphraseWordCount: StateFlow<Int> = _passphraseWordCount.asStateFlow()
    
    private val _passphraseDelimiter = MutableStateFlow("-")
    val passphraseDelimiter: StateFlow<String> = _passphraseDelimiter.asStateFlow()
    
    private val _passphraseCapitalize = MutableStateFlow(false)
    val passphraseCapitalize: StateFlow<Boolean> = _passphraseCapitalize.asStateFlow()
    
    private val _passphraseIncludeNumber = MutableStateFlow(false)
    val passphraseIncludeNumber: StateFlow<Boolean> = _passphraseIncludeNumber.asStateFlow()
    
    private val _passphraseCustomWord = MutableStateFlow("")
    val passphraseCustomWord: StateFlow<String> = _passphraseCustomWord.asStateFlow()
    
    private val _passphraseResult = MutableStateFlow("")
    val passphraseResult: StateFlow<String> = _passphraseResult.asStateFlow()
    
    // 保持旧的状态以支持向后兼容（将在UI中逐步移除）
    @Deprecated("Use passphrase states instead")
    private val _passwordLength = MutableStateFlow(12)
    val passwordLength: StateFlow<Int> = _passwordLength.asStateFlow()
    
    @Deprecated("Use passphraseCapitalize instead")
    private val _firstLetterUppercase = MutableStateFlow(false)
    val firstLetterUppercase: StateFlow<Boolean> = _firstLetterUppercase.asStateFlow()
    
    @Deprecated("Use passphraseIncludeNumber instead")
    private val _includeNumbersInPassword = MutableStateFlow(true)
    val includeNumbersInPassword: StateFlow<Boolean> = _includeNumbersInPassword.asStateFlow()
    
    @Deprecated("Use passphraseDelimiter instead")
    private val _customSeparator = MutableStateFlow("")
    val customSeparator: StateFlow<String> = _customSeparator.asStateFlow()
    
    @Deprecated("No longer used")
    private val _separatorCountsTowardsLength = MutableStateFlow(false)
    val separatorCountsTowardsLength: StateFlow<Boolean> = _separatorCountsTowardsLength.asStateFlow()
    
    @Deprecated("No longer used")
    private val _segmentLength = MutableStateFlow(0)
    val segmentLength: StateFlow<Int> = _segmentLength.asStateFlow()
    
    @Deprecated("Use passphraseResult instead")
    private val _passwordResult = MutableStateFlow("")
    val passwordResult: StateFlow<String> = _passwordResult.asStateFlow()
    
    // PIN码生成器状态
    private val _pinLength = MutableStateFlow(6)
    val pinLength: StateFlow<Int> = _pinLength.asStateFlow()
    
    private val _pinResult = MutableStateFlow("")
    val pinResult: StateFlow<String> = _pinResult.asStateFlow()
    
    // 更新生成器类型
    fun updateSelectedGenerator(generatorType: GeneratorType) {
        _selectedGenerator.value = generatorType
    }
    
    // 更新随机符号生成器状态
    fun updateSymbolLength(length: Int) {
        _symbolLength.value = length
    }
    
    fun updateIncludeUppercase(include: Boolean) {
        _includeUppercase.value = include
    }
    
    fun updateIncludeLowercase(include: Boolean) {
        _includeLowercase.value = include
    }
    
    fun updateIncludeNumbers(include: Boolean) {
        _includeNumbers.value = include
    }
    
    fun updateIncludeSymbols(include: Boolean) {
        _includeSymbols.value = include
    }
    
    fun updateExcludeSimilar(exclude: Boolean) {
        _excludeSimilar.value = exclude
    }
    
    fun updateExcludeAmbiguous(exclude: Boolean) {
        _excludeAmbiguous.value = exclude
    }

    fun updateAnalyzeCommonPasswords(analyze: Boolean) {
        _analyzeCommonPasswords.value = analyze
    }

    fun updateAnalyzeWeight(weight: Int) {
        _analyzeWeight.value = weight.coerceIn(0, 100)
    }
    
    // ✨ 最小字符数要求更新方法
    fun updateUppercaseMin(min: Int) {
        _uppercaseMin.value = min.coerceAtLeast(0)
    }
    
    fun updateLowercaseMin(min: Int) {
        _lowercaseMin.value = min.coerceAtLeast(0)
    }
    
    fun updateNumbersMin(min: Int) {
        _numbersMin.value = min.coerceAtLeast(0)
    }
    
    fun updateSymbolsMin(min: Int) {
        _symbolsMin.value = min.coerceAtLeast(0)
    }
    
    fun updateSymbolResult(result: String) {
        _symbolResult.value = result
    }
    
    // 更新口令生成器状态
    fun updatePasswordLength(length: Int) {
        _passwordLength.value = length
    }
    
    fun updateFirstLetterUppercase(uppercase: Boolean) {
        _firstLetterUppercase.value = uppercase
    }
    
    fun updateIncludeNumbersInPassword(include: Boolean) {
        _includeNumbersInPassword.value = include
    }
    
    fun updateCustomSeparator(separator: String) {
        _customSeparator.value = separator
    }
    
    fun updateSeparatorCountsTowardsLength(counts: Boolean) {
        _separatorCountsTowardsLength.value = counts
    }
    
    fun updateSegmentLength(length: Int) {
        _segmentLength.value = length
    }
    
    fun updatePasswordResult(result: String) {
        _passwordResult.value = result
    }
    
    // 更新PIN码生成器状态
    fun updatePinLength(length: Int) {
        _pinLength.value = length
    }
    
    fun updatePinResult(result: String) {
        _pinResult.value = result
    }
    
    // ✨ 密码短语更新方法
    fun updatePassphraseWordCount(count: Int) {
        _passphraseWordCount.value = count.coerceIn(2, 8)
    }
    
    fun updatePassphraseDelimiter(delimiter: String) {
        _passphraseDelimiter.value = delimiter
    }
    
    fun updatePassphraseCapitalize(capitalize: Boolean) {
        _passphraseCapitalize.value = capitalize
    }
    
    fun updatePassphraseIncludeNumber(include: Boolean) {
        _passphraseIncludeNumber.value = include
    }
    
    fun updatePassphraseCustomWord(word: String) {
        _passphraseCustomWord.value = word
    }
    
    fun updatePassphraseResult(result: String) {
        _passphraseResult.value = result
    }
    
    // 清除所有结果
    fun clearAllResults() {
        _symbolResult.value = ""
        _passwordResult.value = ""  // 保持兼容性
        _passphraseResult.value = ""
        _pinResult.value = ""
    }
}

// 生成器类型枚举（扩展以支持新功能）
enum class GeneratorType {
    SYMBOL,     // 原有：随机符号密码
    PASSWORD,   // 原有：基于单词的密码生成（保持兼容）
    PASSPHRASE, // 新增：Diceware 密码短语生成器
    PIN         // 原有：PIN码
}