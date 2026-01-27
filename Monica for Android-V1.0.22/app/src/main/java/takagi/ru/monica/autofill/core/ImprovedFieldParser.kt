package takagi.ru.monica.autofill.core

import android.app.assist.AssistStructure
import android.view.autofill.AutofillId
import takagi.ru.monica.autofill.EnhancedAutofillStructureParserV2
import takagi.ru.monica.autofill.EnhancedAutofillFieldParser

/**
 * 改进的字段解析器
 * 
 * 结合多种解析策略，提高字段识别准确率
 * 
 * 解析策略优先级：
 * 1. EnhancedAutofillStructureParserV2 (最新，支持15+种语言)
 * 2. EnhancedAutofillFieldParser (增强版，支持智能检测)
 * 3. AutofillFieldParser (传统版，作为后备)
 * 
 * 功能:
 * - 多层解析策略
 * - 准确度评分
 * - 字段验证
 * - 详细的诊断信息
 * 
 * @author Monica Team
 * @since 2.0
 */
class ImprovedFieldParser(private val structure: AssistStructure) {
    
    companion object {
        private const val TAG = "ImprovedFieldParser"
        
        // 准确度阈值
        private const val HIGH_CONFIDENCE_THRESHOLD = 0.8f
        private const val MEDIUM_CONFIDENCE_THRESHOLD = 0.6f
    }
    
    /**
     * 解析结果
     */
    data class ParseResult(
        val fields: List<ParsedField>,
        val confidence: Float,
        val parserUsed: String,
        val warnings: List<String>
    ) {
        /**
         * 是否有足够的置信度
         */
        fun hasHighConfidence(): Boolean = confidence >= HIGH_CONFIDENCE_THRESHOLD
        
        /**
         * 是否有中等置信度
         */
        fun hasMediumConfidence(): Boolean = confidence >= MEDIUM_CONFIDENCE_THRESHOLD
        
        /**
         * 获取用户名字段
         */
        fun getUsernameFields(): List<ParsedField> {
            return fields.filter { 
                it.type == FieldType.USERNAME || it.type == FieldType.EMAIL 
            }
        }
        
        /**
         * 获取密码字段
         */
        fun getPasswordFields(): List<ParsedField> {
            return fields.filter { 
                it.type == FieldType.PASSWORD || it.type == FieldType.NEW_PASSWORD 
            }
        }
    }
    
    /**
     * 解析的字段
     */
    data class ParsedField(
        val id: AutofillId,
        val type: FieldType,
        val confidence: Float,
        val value: String?,
        val metadata: Map<String, String>
    ) {
        /**
         * 是否是高置信度字段
         */
        fun isHighConfidence(): Boolean = confidence >= HIGH_CONFIDENCE_THRESHOLD
    }
    
    /**
     * 字段类型
     */
    enum class FieldType {
        USERNAME,
        EMAIL,
        PASSWORD,
        NEW_PASSWORD,
        OTP,
        PHONE,
        ADDRESS,
        CREDIT_CARD,
        OTHER
    }
    
    /**
     * 解析表单结构
     * 使用多层解析策略
     */
    fun parse(): ParseResult {
        AutofillLogger.d(TAG, "Starting multi-layer field parsing")
        
        // 1. 尝试使用 V2 增强解析器
        val v2Result = tryV2Parser()
        if (v2Result.confidence > HIGH_CONFIDENCE_THRESHOLD) {
            AutofillLogger.i(TAG, "V2 parser succeeded with high confidence: ${v2Result.confidence}")
            return v2Result
        }
        
        // 2. 尝试使用增强解析器
        val enhancedResult = tryEnhancedParser()
        if (enhancedResult.confidence > MEDIUM_CONFIDENCE_THRESHOLD) {
            AutofillLogger.i(TAG, "Enhanced parser succeeded with medium confidence: ${enhancedResult.confidence}")
            return enhancedResult
        }
        
        // 3. 使用传统解析器作为后备
        val legacyResult = tryLegacyParser()
        AutofillLogger.i(TAG, "Using legacy parser as fallback, confidence: ${legacyResult.confidence}")
        return legacyResult
    }
    
    /**
     * 尝试使用 V2 解析器
     */
    private fun tryV2Parser(): ParseResult {
        return try {
            val parser = EnhancedAutofillStructureParserV2()
            val parsedStructure = parser.parse(structure, respectAutofillOff = true)
            
            val fields = parsedStructure.items.map { item ->
                ParsedField(
                    id = item.id,
                    type = mapV2FieldHint(item.hint),
                    confidence = item.accuracy.score / 10f, // 归一化到 0-1
                    value = item.value,
                    metadata = mapOf(
                        "parser" to "V2",
                        "accuracy" to item.accuracy.name,
                        "isFocused" to item.isFocused.toString(),
                        "isVisible" to item.isVisible.toString()
                    )
                )
            }
            
            val avgConfidence = if (fields.isNotEmpty()) {
                fields.map { it.confidence }.average().toFloat()
            } else 0f
            
            val warnings = mutableListOf<String>()
            if (fields.isEmpty()) {
                warnings.add("No fields detected by V2 parser")
            }
            
            ParseResult(
                fields = fields,
                confidence = avgConfidence,
                parserUsed = "EnhancedAutofillStructureParserV2",
                warnings = warnings
            )
        } catch (e: Exception) {
            AutofillLogger.e(TAG, "V2 parser failed", e)
            ParseResult(
                fields = emptyList(),
                confidence = 0f,
                parserUsed = "V2 (failed)",
                warnings = listOf("V2 parser error: ${e.message}")
            )
        }
    }
    
    /**
     * 尝试使用增强解析器
     */
    private fun tryEnhancedParser(): ParseResult {
        return try {
            val parser = EnhancedAutofillFieldParser(structure)
            val collection = parser.parse()
            
            val fields = mutableListOf<ParsedField>()
            
            // 添加用户名字段
            collection.usernameField?.let { id ->
                fields.add(ParsedField(
                    id = id,
                    type = FieldType.USERNAME,
                    confidence = 0.8f,
                    value = null,
                    metadata = mapOf("parser" to "Enhanced", "source" to "usernameField")
                ))
            }
            
            // 添加邮箱字段
            collection.emailField?.let { id ->
                fields.add(ParsedField(
                    id = id,
                    type = FieldType.EMAIL,
                    confidence = 0.85f,
                    value = null,
                    metadata = mapOf("parser" to "Enhanced", "source" to "emailField")
                ))
            }
            
            // 添加密码字段
            collection.passwordField?.let { id ->
                fields.add(ParsedField(
                    id = id,
                    type = FieldType.PASSWORD,
                    confidence = 0.9f,
                    value = null,
                    metadata = mapOf("parser" to "Enhanced", "source" to "passwordField")
                ))
            }
            
            // 注意：EnhancedAutofillFieldCollection 可能没有 newPasswordField 和 otpFields
            // 这些字段由 V2 解析器处理
            
            // 添加电话字段
            collection.phoneField?.let { id ->
                fields.add(ParsedField(
                    id = id,
                    type = FieldType.PHONE,
                    confidence = 0.8f,
                    value = null,
                    metadata = mapOf("parser" to "Enhanced", "source" to "phoneField")
                ))
            }
            
            val avgConfidence = if (fields.isNotEmpty()) {
                fields.map { it.confidence }.average().toFloat()
            } else 0f
            
            val warnings = mutableListOf<String>()
            if (fields.isEmpty()) {
                warnings.add("No fields detected by Enhanced parser")
            }
            
            ParseResult(
                fields = fields,
                confidence = avgConfidence,
                parserUsed = "EnhancedAutofillFieldParser",
                warnings = warnings
            )
        } catch (e: Exception) {
            AutofillLogger.e(TAG, "Enhanced parser failed", e)
            ParseResult(
                fields = emptyList(),
                confidence = 0f,
                parserUsed = "Enhanced (failed)",
                warnings = listOf("Enhanced parser error: ${e.message}")
            )
        }
    }
    
    /**
     * 尝试使用传统解析器（降级到增强解析器）
     */
    private fun tryLegacyParser(): ParseResult {
        // 由于 AutofillFieldParser 不可访问，使用增强解析器作为后备
        return try {
            val parser = EnhancedAutofillFieldParser(structure)
            val collection = parser.parse()
            
            val fields = mutableListOf<ParsedField>()
            
            // 添加用户名字段
            collection.usernameField?.let { id ->
                fields.add(ParsedField(
                    id = id,
                    type = FieldType.USERNAME,
                    confidence = 0.6f,
                    value = null,
                    metadata = mapOf("parser" to "Fallback", "source" to "usernameField")
                ))
            }
            
            // 添加密码字段
            collection.passwordField?.let { id ->
                fields.add(ParsedField(
                    id = id,
                    type = FieldType.PASSWORD,
                    confidence = 0.7f,
                    value = null,
                    metadata = mapOf("parser" to "Fallback", "source" to "passwordField")
                ))
            }
            
            val avgConfidence = if (fields.isNotEmpty()) {
                fields.map { it.confidence }.average().toFloat()
            } else 0.3f // 低置信度
            
            val warnings = mutableListOf<String>()
            if (fields.isEmpty()) {
                warnings.add("No fields detected by fallback parser")
            }
            warnings.add("Using fallback parser - results may be less accurate")
            
            ParseResult(
                fields = fields,
                confidence = avgConfidence,
                parserUsed = "EnhancedAutofillFieldParser (fallback)",
                warnings = warnings
            )
        } catch (e: Exception) {
            AutofillLogger.e(TAG, "Fallback parser failed", e)
            ParseResult(
                fields = emptyList(),
                confidence = 0f,
                parserUsed = "Fallback (failed)",
                warnings = listOf("Fallback parser error: ${e.message}")
            )
        }
    }
    
    /**
     * 映射 V2 字段提示到通用字段类型
     */
    private fun mapV2FieldHint(hint: EnhancedAutofillStructureParserV2.FieldHint): FieldType {
        return when (hint) {
            EnhancedAutofillStructureParserV2.FieldHint.USERNAME -> FieldType.USERNAME
            EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS -> FieldType.EMAIL
            EnhancedAutofillStructureParserV2.FieldHint.PASSWORD -> FieldType.PASSWORD
            EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD -> FieldType.NEW_PASSWORD
            EnhancedAutofillStructureParserV2.FieldHint.OTP_CODE -> FieldType.OTP
            EnhancedAutofillStructureParserV2.FieldHint.PHONE_NUMBER -> FieldType.PHONE
            EnhancedAutofillStructureParserV2.FieldHint.POSTAL_ADDRESS -> FieldType.ADDRESS
            EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_NUMBER -> FieldType.CREDIT_CARD
            else -> FieldType.OTHER
        }
    }
    
    /**
     * 验证解析结果
     */
    fun validateParseResult(result: ParseResult): Boolean {
        // 至少需要一个用户名或密码字段
        val hasCredentialFields = result.fields.any { 
            it.type == FieldType.USERNAME || 
            it.type == FieldType.EMAIL ||
            it.type == FieldType.PASSWORD 
        }
        
        if (!hasCredentialFields) {
            AutofillLogger.w(TAG, "Validation failed: No credential fields found")
            return false
        }
        
        // 检查置信度
        if (result.confidence < 0.3f) {
            AutofillLogger.w(TAG, "Validation warning: Low confidence (${result.confidence})")
        }
        
        AutofillLogger.d(TAG, "Validation passed: ${result.fields.size} fields, confidence: ${result.confidence}")
        return true
    }
    
    /**
     * 获取解析统计信息
     */
    fun getParsingStats(result: ParseResult): Map<String, Any> {
        return mapOf(
            "totalFields" to result.fields.size,
            "usernameFields" to result.getUsernameFields().size,
            "passwordFields" to result.getPasswordFields().size,
            "confidence" to result.confidence,
            "parserUsed" to result.parserUsed,
            "warnings" to result.warnings.size,
            "hasHighConfidence" to result.hasHighConfidence()
        )
    }
}
