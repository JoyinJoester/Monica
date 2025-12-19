package takagi.ru.monica.autofill

import android.app.assist.AssistStructure
import android.view.autofill.AutofillId

/**
 * 简化的自动填充结构解析器占位符
 * 
 * 这是一个临时的占位符实现，用于解决编译问题
 * 原来的增强版解析器因为编译错误被暂时禁用
 */
class EnhancedAutofillStructureParser {
    
    /**
     * 解析结果数据类
     */
    data class ParsedStructure(
        val applicationId: String? = null,
        val webScheme: String? = null,
        val webDomain: String? = null,
        val webView: Boolean = false,
        val items: List<ParsedItem> = emptyList()
    )
    
    /**
     * 解析的字段项
     */
    data class ParsedItem(
        val id: AutofillId,
        val hint: FieldHint,
        val accuracy: Accuracy,
        val value: String? = null,
        val isFocused: Boolean = false,
        val isVisible: Boolean = true,
        val parentWebViewNodeId: Int? = null,
        val score: Int = 0 // 添加评分属性
    )
    
    /**
     * 字段类型提示
     */
    enum class FieldHint {
        USERNAME,
        PASSWORD,
        NEW_PASSWORD,
        EMAIL_ADDRESS,
        PHONE_NUMBER,
        CREDIT_CARD_NUMBER,
        CREDIT_CARD_EXPIRATION_DATE,
        CREDIT_CARD_SECURITY_CODE,
        OTP_CODE,
        POSTAL_ADDRESS,
        POSTAL_CODE,
        PERSON_NAME,
        UNKNOWN
    }
    
    /**
     * 准确度等级
     */
    enum class Accuracy(val score: Int) {
        LOW(1),
        MEDIUM(2),
        HIGH(3),
        VERY_HIGH(4)
    }
    
    /**
     * 占位符解析方法
     * 返回空的解析结果
     */
    fun parse(structure: AssistStructure, respectAutofillOff: Boolean = true): ParsedStructure {
        // 临时返回空结果，避免编译错误
        return ParsedStructure(
            applicationId = structure.activityComponent.packageName,
            webView = false,
            items = emptyList()
        )
    }
}