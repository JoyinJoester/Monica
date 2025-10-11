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
    
    private val _symbolResult = MutableStateFlow("")
    val symbolResult: StateFlow<String> = _symbolResult.asStateFlow()
    
    // 口令生成器状态
    private val _passwordLength = MutableStateFlow(12)
    val passwordLength: StateFlow<Int> = _passwordLength.asStateFlow()
    
    private val _firstLetterUppercase = MutableStateFlow(false)
    val firstLetterUppercase: StateFlow<Boolean> = _firstLetterUppercase.asStateFlow()
    
    private val _includeNumbersInPassword = MutableStateFlow(true)
    val includeNumbersInPassword: StateFlow<Boolean> = _includeNumbersInPassword.asStateFlow()
    
    private val _customSeparator = MutableStateFlow("")
    val customSeparator: StateFlow<String> = _customSeparator.asStateFlow()
    
    private val _separatorCountsTowardsLength = MutableStateFlow(false)
    val separatorCountsTowardsLength: StateFlow<Boolean> = _separatorCountsTowardsLength.asStateFlow()
    
    private val _segmentLength = MutableStateFlow(0)
    val segmentLength: StateFlow<Int> = _segmentLength.asStateFlow()
    
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
    
    // 清除所有结果
    fun clearAllResults() {
        _symbolResult.value = ""
        _passwordResult.value = ""
        _pinResult.value = ""
    }
}

// 生成器类型枚举
enum class GeneratorType {
    SYMBOL, PASSWORD, PIN
}