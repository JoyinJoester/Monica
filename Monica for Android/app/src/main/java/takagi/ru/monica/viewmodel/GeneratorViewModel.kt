package takagi.ru.monica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import takagi.ru.monica.data.GeneratorPreferences
import takagi.ru.monica.data.GeneratorPreferencesManager
import takagi.ru.monica.data.model.SshKeyData
import takagi.ru.monica.util.PasswordGenerator
import takagi.ru.monica.utils.SshKeyGenerator

/**
 * ViewModel for generator screen to persist state across navigation
 */
class GeneratorViewModel(
    private val preferencesManager: GeneratorPreferencesManager? = null
) : ViewModel() {
    private val defaultSymbols = PasswordGenerator.getDefaultSymbols()

    init {
        preferencesManager?.let { manager ->
            viewModelScope.launch(Dispatchers.IO) {
                val saved = manager.load()
                _selectedGenerator.value = runCatching {
                    GeneratorType.valueOf(saved.selectedGenerator)
                }.getOrDefault(GeneratorType.SYMBOL)
                _symbolLength.value = saved.symbolLength
                _includeUppercase.value = saved.includeUppercase
                _includeLowercase.value = saved.includeLowercase
                _includeNumbers.value = saved.includeNumbers
                _includeSymbols.value = saved.includeSymbols
                _useSymbolExclusionMode.value = saved.useSymbolExclusionMode
                _excludedSymbols.value = saved.excludedSymbols
                _customSymbols.value = saved.customSymbols
                _excludeSimilar.value = saved.excludeSimilar
                _excludeAmbiguous.value = saved.excludeAmbiguous
                _analyzeCommonPasswords.value = saved.analyzeCommonPasswords
                _analyzeWeight.value = saved.analyzeWeight
                _uppercaseMin.value = saved.uppercaseMin
                _lowercaseMin.value = saved.lowercaseMin
                _numbersMin.value = saved.numbersMin
                _symbolsMin.value = saved.symbolsMin
                _passphraseWordCount.value = saved.passphraseWordCount
                _passphraseDelimiter.value = saved.passphraseDelimiter
                _passphraseCapitalize.value = saved.passphraseCapitalize
                _passphraseIncludeNumber.value = saved.passphraseIncludeNumber
                _passphraseCustomWord.value = saved.passphraseCustomWord
                _passphraseCustomWords.value = saved.passphraseCustomWords
                _pinLength.value = saved.pinLength
                _passwordLength.value = saved.passwordLength
                _firstLetterUppercase.value = saved.firstLetterUppercase
                _includeNumbersInPassword.value = saved.includeNumbersInPassword
                _customSeparator.value = saved.customSeparator
                _separatorCountsTowardsLength.value = saved.separatorCountsTowardsLength
                _segmentLength.value = saved.segmentLength
                _sshKeyAlgorithm.value = saved.sshKeyAlgorithm
                _sshKeyRsaSize.value = saved.sshKeyRsaSize
            }
        }
    }

    private fun scheduleSave() {
        preferencesManager?.let { manager ->
            viewModelScope.launch(Dispatchers.IO) {
                manager.save(currentPreferences())
            }
        }
    }

    private fun currentPreferences() = GeneratorPreferences(
        selectedGenerator = _selectedGenerator.value.name,
        symbolLength = _symbolLength.value,
        includeUppercase = _includeUppercase.value,
        includeLowercase = _includeLowercase.value,
        includeNumbers = _includeNumbers.value,
        includeSymbols = _includeSymbols.value,
        useSymbolExclusionMode = _useSymbolExclusionMode.value,
        excludedSymbols = _excludedSymbols.value,
        customSymbols = _customSymbols.value,
        excludeSimilar = _excludeSimilar.value,
        excludeAmbiguous = _excludeAmbiguous.value,
        analyzeCommonPasswords = _analyzeCommonPasswords.value,
        analyzeWeight = _analyzeWeight.value,
        uppercaseMin = _uppercaseMin.value,
        lowercaseMin = _lowercaseMin.value,
        numbersMin = _numbersMin.value,
        symbolsMin = _symbolsMin.value,
        passphraseWordCount = _passphraseWordCount.value,
        passphraseDelimiter = _passphraseDelimiter.value,
        passphraseCapitalize = _passphraseCapitalize.value,
        passphraseIncludeNumber = _passphraseIncludeNumber.value,
        passphraseCustomWord = _passphraseCustomWord.value,
        passphraseCustomWords = _passphraseCustomWords.value,
        pinLength = _pinLength.value,
        passwordLength = _passwordLength.value,
        firstLetterUppercase = _firstLetterUppercase.value,
        includeNumbersInPassword = _includeNumbersInPassword.value,
        customSeparator = _customSeparator.value,
        separatorCountsTowardsLength = _separatorCountsTowardsLength.value,
        segmentLength = _segmentLength.value,
        sshKeyAlgorithm = _sshKeyAlgorithm.value,
        sshKeyRsaSize = _sshKeyRsaSize.value
    )
    
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

    private val _useSymbolExclusionMode = MutableStateFlow(true)
    val useSymbolExclusionMode: StateFlow<Boolean> = _useSymbolExclusionMode.asStateFlow()

    private val _excludedSymbols = MutableStateFlow("")
    val excludedSymbols: StateFlow<String> = _excludedSymbols.asStateFlow()

    private val _customSymbols = MutableStateFlow(defaultSymbols)
    val customSymbols: StateFlow<String> = _customSymbols.asStateFlow()
    
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
    private val _passphraseWordCount = MutableStateFlow(5)
    val passphraseWordCount: StateFlow<Int> = _passphraseWordCount.asStateFlow()
    
    private val _passphraseDelimiter = MutableStateFlow("-")
    val passphraseDelimiter: StateFlow<String> = _passphraseDelimiter.asStateFlow()
    
    private val _passphraseCapitalize = MutableStateFlow(false)
    val passphraseCapitalize: StateFlow<Boolean> = _passphraseCapitalize.asStateFlow()
    
    private val _passphraseIncludeNumber = MutableStateFlow(false)
    val passphraseIncludeNumber: StateFlow<Boolean> = _passphraseIncludeNumber.asStateFlow()
    
    private val _passphraseCustomWord = MutableStateFlow("")
    val passphraseCustomWord: StateFlow<String> = _passphraseCustomWord.asStateFlow()

    private val _passphraseCustomWords = MutableStateFlow("")
    val passphraseCustomWords: StateFlow<String> = _passphraseCustomWords.asStateFlow()
    
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

    // ✨ SSH 密钥生成器状态
    private val _sshKeyAlgorithm = MutableStateFlow(SshKeyGenerator.DEFAULT_ALGORITHM)
    val sshKeyAlgorithm: StateFlow<String> = _sshKeyAlgorithm.asStateFlow()

    private val _sshKeyRsaSize = MutableStateFlow(SshKeyGenerator.DEFAULT_RSA_KEY_SIZE)
    val sshKeyRsaSize: StateFlow<Int> = _sshKeyRsaSize.asStateFlow()

    private val _sshKeyResult = MutableStateFlow<SshKeyData?>(null)
    val sshKeyResult: StateFlow<SshKeyData?> = _sshKeyResult.asStateFlow()
    
    // 更新生成器类型
    fun updateSelectedGenerator(generatorType: GeneratorType) {
        _selectedGenerator.value = generatorType
        scheduleSave()
    }

    // 更新随机符号生成器状态
    fun updateSymbolLength(length: Int) {
        _symbolLength.value = length.coerceIn(4, 128)
        scheduleSave()
    }

    fun updateIncludeUppercase(include: Boolean) {
        _includeUppercase.value = include
        scheduleSave()
    }

    fun updateIncludeLowercase(include: Boolean) {
        _includeLowercase.value = include
        scheduleSave()
    }

    fun updateIncludeNumbers(include: Boolean) {
        _includeNumbers.value = include
        scheduleSave()
    }

    fun updateIncludeSymbols(include: Boolean) {
        _includeSymbols.value = include
        scheduleSave()
    }

    fun updateUseSymbolExclusionMode(enabled: Boolean) {
        _useSymbolExclusionMode.value = enabled
        scheduleSave()
    }

    fun updateExcludedSymbols(symbols: String) {
        _excludedSymbols.value = symbols
            .filter { it in defaultSymbols }
            .fold(StringBuilder()) { acc, c ->
                if (c !in acc) {
                    acc.append(c)
                }
                acc
            }
            .toString()
        scheduleSave()
    }

    fun toggleExcludedSymbol(symbol: Char) {
        if (symbol !in defaultSymbols) return
        val current = _excludedSymbols.value
        _excludedSymbols.value = if (symbol in current) {
            current.filter { it != symbol }
        } else {
            current + symbol
        }
        scheduleSave()
    }

    fun clearExcludedSymbols() {
        _excludedSymbols.value = ""
        scheduleSave()
    }

    fun updateCustomSymbols(symbols: String) {
        _customSymbols.value = symbols
            .filter { it in defaultSymbols }
            .fold(StringBuilder()) { acc, c ->
                if (c !in acc) {
                    acc.append(c)
                }
                acc
            }
            .toString()
        scheduleSave()
    }

    fun resetCustomSymbols() {
        _customSymbols.value = defaultSymbols
        scheduleSave()
    }

    fun updateExcludeSimilar(exclude: Boolean) {
        _excludeSimilar.value = exclude
        scheduleSave()
    }

    fun updateExcludeAmbiguous(exclude: Boolean) {
        _excludeAmbiguous.value = exclude
        scheduleSave()
    }

    fun updateAnalyzeCommonPasswords(analyze: Boolean) {
        _analyzeCommonPasswords.value = analyze
        scheduleSave()
    }

    fun updateAnalyzeWeight(weight: Int) {
        _analyzeWeight.value = weight.coerceIn(0, 100)
        scheduleSave()
    }

    // ✨ 最小字符数要求更新方法
    fun updateUppercaseMin(min: Int) {
        _uppercaseMin.value = min.coerceAtLeast(0)
        scheduleSave()
    }

    fun updateLowercaseMin(min: Int) {
        _lowercaseMin.value = min.coerceAtLeast(0)
        scheduleSave()
    }

    fun updateNumbersMin(min: Int) {
        _numbersMin.value = min.coerceAtLeast(0)
        scheduleSave()
    }

    fun updateSymbolsMin(min: Int) {
        _symbolsMin.value = min.coerceAtLeast(0)
        scheduleSave()
    }
    
    fun updateSymbolResult(result: String) {
        _symbolResult.value = result
    }
    
    // 更新口令生成器状态
    fun updatePasswordLength(length: Int) {
        _passwordLength.value = length.coerceIn(4, 128)
        scheduleSave()
    }

    fun updateFirstLetterUppercase(uppercase: Boolean) {
        _firstLetterUppercase.value = uppercase
        scheduleSave()
    }

    fun updateIncludeNumbersInPassword(include: Boolean) {
        _includeNumbersInPassword.value = include
        scheduleSave()
    }

    fun updateCustomSeparator(separator: String) {
        _customSeparator.value = separator
        scheduleSave()
    }

    fun updateSeparatorCountsTowardsLength(counts: Boolean) {
        _separatorCountsTowardsLength.value = counts
        scheduleSave()
    }

    fun updateSegmentLength(length: Int) {
        _segmentLength.value = length
        scheduleSave()
    }
    
    fun updatePasswordResult(result: String) {
        _passwordResult.value = result
    }
    
    // 更新PIN码生成器状态
    fun updatePinLength(length: Int) {
        _pinLength.value = length.coerceIn(3, 9)
        scheduleSave()
    }
    
    fun updatePinResult(result: String) {
        _pinResult.value = result
    }
    
    // ✨ 密码短语更新方法
    fun updatePassphraseWordCount(count: Int) {
        _passphraseWordCount.value = count.coerceIn(1, 20)
        scheduleSave()
    }

    fun updatePassphraseDelimiter(delimiter: String) {
        _passphraseDelimiter.value = delimiter
        scheduleSave()
    }

    fun updatePassphraseCapitalize(capitalize: Boolean) {
        _passphraseCapitalize.value = capitalize
        scheduleSave()
    }

    fun updatePassphraseIncludeNumber(include: Boolean) {
        _passphraseIncludeNumber.value = include
        scheduleSave()
    }

    fun updatePassphraseCustomWord(word: String) {
        _passphraseCustomWord.value = word
        scheduleSave()
    }

    fun updatePassphraseCustomWords(words: String) {
        _passphraseCustomWords.value = words
        scheduleSave()
    }
    
    fun updatePassphraseResult(result: String) {
        _passphraseResult.value = result
    }

    // ✨ SSH 密钥更新方法
    fun updateSshKeyAlgorithm(algorithm: String) {
        _sshKeyAlgorithm.value = when (algorithm.uppercase()) {
            SshKeyData.ALGORITHM_RSA -> SshKeyData.ALGORITHM_RSA
            else -> SshKeyGenerator.DEFAULT_ALGORITHM
        }
        scheduleSave()
    }

    fun updateSshKeyRsaSize(size: Int) {
        _sshKeyRsaSize.value = if (size in SshKeyGenerator.RSA_ALLOWED_KEY_SIZES) {
            size
        } else {
            SshKeyGenerator.DEFAULT_RSA_KEY_SIZE
        }
        scheduleSave()
    }

    fun updateSshKeyResult(result: SshKeyData?) {
        _sshKeyResult.value = result
    }
    
    // 清除所有结果
    fun clearAllResults() {
        _symbolResult.value = ""
        _passwordResult.value = ""  // 保持兼容性
        _passphraseResult.value = ""
        _pinResult.value = ""
        _sshKeyResult.value = null
    }
}

// 生成器类型枚举（扩展以支持新功能）
enum class GeneratorType {
    SYMBOL,     // 原有：随机符号密码
    PASSWORD,   // 原有：基于单词的密码生成（保持兼容）
    PASSPHRASE, // 新增：Diceware 密码短语生成器
    PIN,        // 原有：PIN码
    SSH_KEY     // 新增：SSH 密钥（RSA / Ed25519）
}
