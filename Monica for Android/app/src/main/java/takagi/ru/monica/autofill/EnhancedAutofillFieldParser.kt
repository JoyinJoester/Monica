package takagi.ru.monica.autofill

import android.app.assist.AssistStructure
import android.view.View
import takagi.ru.monica.autofill.core.safeTextOrNull

/**
 * 增强的自动填充字段解析器
 * 支持智能识别多种字段类型
 * 
 * 功能:
 * - 自动识别Email、电话、OTP等特殊字段
 * - 支持个人信息、地址、支付信息字段
 * - 使用启发式算法和正则表达式
 * - 详细的日志记录
 */
class EnhancedAutofillFieldParser(private val structure: AssistStructure) {
    
    companion object {
        private const val TAG = "EnhancedFieldParser"
    }
    
    /**
     * 解析所有字段
     * @return 包含所有识别字段的集合
     */
    fun parse(): EnhancedAutofillFieldCollection {
        val collection = EnhancedAutofillFieldCollection()
        
        android.util.Log.d(TAG, "Starting field parsing...")
        
        // 遍历所有窗口节点
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            parseNode(windowNode.rootViewNode, collection)
        }
        
        // 打印识别结果
        logParsingResult(collection)
        
        return collection
    }
    
    /**
     * 递归解析节点
     */
    private fun parseNode(
        node: AssistStructure.ViewNode,
        collection: EnhancedAutofillFieldCollection
    ) {
        // 只处理可自动填充的节点
        if (node.autofillId == null) {
            // 递归处理子节点
            for (i in 0 until node.childCount) {
                parseNode(node.getChildAt(i), collection)
            }
            return
        }
        
        // 收集节点信息
        val autofillHints = node.autofillHints
        val idEntry = node.idEntry
        val hint = node.hint
        val text = node.text?.toString()
        val inputType = node.inputType
        val className = node.className
        val autofillValue = (node.autofillValue)
            .safeTextOrNull(TAG, "field ${idEntry ?: className ?: "unknown"}")
        
        // 检查是否是可编辑文本字段
        val isEditableTextField = className?.contains("EditText") == true ||
                                  className?.contains("TextInputEditText") == true ||
                                  node.autofillType == View.AUTOFILL_TYPE_TEXT
        
        if (!isEditableTextField) {
            // 递归处理子节点
            for (i in 0 until node.childCount) {
                parseNode(node.getChildAt(i), collection)
            }
            return
        }
        
        // 使用智能检测器识别字段类型
        val fieldType = SmartFieldDetector.detectFieldType(
            autofillHints,
            idEntry,
            hint,
            text,
            inputType,
            className,
            node.autofillType,
            node.htmlInfo
        )
        
        android.util.Log.d(TAG, "Detected field - Type: $fieldType, ID: $idEntry, Hint: $hint")
        
        // 根据字段类型分配到集合
        assignFieldToCollection(node.autofillId!!, fieldType, autofillValue, collection)
        
        // 递归处理子节点
        for (i in 0 until node.childCount) {
            parseNode(node.getChildAt(i), collection)
        }
    }
    
    /**
     * 将字段分配到对应的集合位置
     */
    private fun assignFieldToCollection(
        autofillId: android.view.autofill.AutofillId,
        fieldType: AutofillFieldType,
        value: String?,
        collection: EnhancedAutofillFieldCollection
    ) {
        when (fieldType) {
            AutofillFieldType.USERNAME -> {
                if (collection.usernameField == null) {
                    collection.usernameField = autofillId
                    collection.setFieldValue(autofillId, value, fieldType)
                }
            }
            AutofillFieldType.PASSWORD -> {
                if (collection.passwordField == null) {
                    collection.passwordField = autofillId
                    collection.setFieldValue(autofillId, value, fieldType)
                }
            }
            AutofillFieldType.EMAIL -> {
                if (collection.emailField == null) {
                    collection.emailField = autofillId
                    collection.setFieldValue(autofillId, value, fieldType)
                    
                    // Email也可以用作用户名
                    if (collection.usernameField == null) {
                        collection.usernameField = autofillId
                    }
                }
            }
            AutofillFieldType.PHONE -> {
                if (collection.phoneField == null) {
                    collection.phoneField = autofillId
                    collection.setFieldValue(autofillId, value, fieldType)
                }
            }
            AutofillFieldType.OTP, AutofillFieldType.SMS_CODE -> {
                if (collection.otpField == null) {
                    collection.otpField = autofillId
                    collection.setFieldValue(autofillId, value, fieldType)
                }
            }
            AutofillFieldType.PERSON_NAME -> {
                if (collection.personNameField == null) {
                    collection.personNameField = autofillId
                    collection.setFieldValue(autofillId, value, fieldType)
                }
            }
            AutofillFieldType.PERSON_NAME_GIVEN -> {
                if (collection.givenNameField == null) {
                    collection.givenNameField = autofillId
                    collection.setFieldValue(autofillId, value, fieldType)
                }
            }
            AutofillFieldType.PERSON_NAME_FAMILY -> {
                if (collection.familyNameField == null) {
                    collection.familyNameField = autofillId
                    collection.setFieldValue(autofillId, value, fieldType)
                }
            }
            AutofillFieldType.ADDRESS_LINE -> {
                if (collection.addressLineField == null) {
                    collection.addressLineField = autofillId
                    collection.setFieldValue(autofillId, value, fieldType)
                }
            }
            AutofillFieldType.ADDRESS_CITY -> {
                if (collection.cityField == null) {
                    collection.cityField = autofillId
                    collection.setFieldValue(autofillId, value, fieldType)
                }
            }
            AutofillFieldType.ADDRESS_STATE -> {
                if (collection.stateField == null) {
                    collection.stateField = autofillId
                    collection.setFieldValue(autofillId, value, fieldType)
                }
            }
            AutofillFieldType.ADDRESS_ZIP -> {
                if (collection.zipField == null) {
                    collection.zipField = autofillId
                    collection.setFieldValue(autofillId, value, fieldType)
                }
            }
            AutofillFieldType.ADDRESS_COUNTRY -> {
                if (collection.countryField == null) {
                    collection.countryField = autofillId
                    collection.setFieldValue(autofillId, value, fieldType)
                }
            }
            AutofillFieldType.CREDIT_CARD_NUMBER -> {
                if (collection.creditCardNumberField == null) {
                    collection.creditCardNumberField = autofillId
                    collection.setFieldValue(autofillId, value, fieldType)
                }
            }
            AutofillFieldType.CREDIT_CARD_HOLDER -> {
                // Phase 7: 持卡人姓名
                if (collection.creditCardHolderField == null) {
                    collection.creditCardHolderField = autofillId
                    collection.setFieldValue(autofillId, value, fieldType)
                }
            }
            AutofillFieldType.CREDIT_CARD_EXPIRATION -> {
                if (collection.creditCardExpirationField == null) {
                    collection.creditCardExpirationField = autofillId
                    collection.setFieldValue(autofillId, value, fieldType)
                }
            }
            AutofillFieldType.CREDIT_CARD_SECURITY_CODE -> {
                if (collection.creditCardSecurityCodeField == null) {
                    collection.creditCardSecurityCodeField = autofillId
                    collection.setFieldValue(autofillId, value, fieldType)
                }
            }
            AutofillFieldType.SEARCH -> {
                // 搜索字段/非凭据字段不需要处理，直接忽略
                android.util.Log.v(TAG, "Ignoring non-credential field (search/comment/chat/etc)")
            }
            AutofillFieldType.UNKNOWN -> {
                // 对于未知类型,仍然记录以便调试
                android.util.Log.v(TAG, "Unknown field type encountered")
            }
        }
    }
    
    /**
     * 记录解析结果
     */
    private fun logParsingResult(collection: EnhancedAutofillFieldCollection) {
        android.util.Log.d(TAG, "Parsing completed:")
        android.util.Log.d(TAG, "  - Has credential fields: ${collection.hasCredentialFields()}")
        android.util.Log.d(TAG, "  - Has OTP fields: ${collection.hasOTPFields()}")
        android.util.Log.d(TAG, "  - Has personal info: ${collection.hasPersonalInfoFields()}")
        android.util.Log.d(TAG, "  - Has address fields: ${collection.hasAddressFields()}")
        android.util.Log.d(TAG, "  - Has payment fields: ${collection.hasPaymentFields()}")
        android.util.Log.d(TAG, "  - Total fields detected: ${collection.fieldTypes.size}")
    }
    
    /**
     * 提取Web域名
     */
    fun extractWebDomain(): String? {
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val domain = extractWebDomainFromNode(windowNode.rootViewNode)
            if (domain != null) {
                return domain
            }
        }
        return null
    }
    
    /**
     * 递归提取Web域名
     */
    private fun extractWebDomainFromNode(node: AssistStructure.ViewNode): String? {
        // 检查webDomain
        node.webDomain?.let { return it }
        
        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val domain = extractWebDomainFromNode(node.getChildAt(i))
            if (domain != null) {
                return domain
            }
        }
        
        return null
    }
}
