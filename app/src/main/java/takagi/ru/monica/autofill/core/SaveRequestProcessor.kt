package takagi.ru.monica.autofill.core

import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillValue
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.first
import takagi.ru.monica.autofill.AutofillPreferences
import takagi.ru.monica.autofill.EnhancedAutofillStructureParserV2
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.repository.PasswordRepository

/**
 * 保存请求处理器
 * 
 * 负责处理 onSaveRequest 的核心逻辑，包括：
 * - 提取表单数据
 * - 验证数据有效性
 * - 检查重复密码
 * - 创建保存 Intent
 * 
 * @author Monica Development Team
 * @version 1.0
 */
@RequiresApi(Build.VERSION_CODES.O)
class SaveRequestProcessor(
    private val context: Context,
    private val passwordRepository: PasswordRepository,
    private val preferences: AutofillPreferences,
    private val diagnostics: AutofillDiagnostics
) {
    
    private val TAG = "SaveRequestProcessor"
    
    /**
     * 保存数据模型
     */
    data class SaveData(
        val username: String,
        val password: String,
        val packageName: String?,
        val domain: String?,
        val isNewPasswordScenario: Boolean
    )
    
    /**
     * 验证结果
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }
    
    /**
     * 重复检查结果
     */
    sealed class DuplicateCheckResult {
        object NoDuplicate : DuplicateCheckResult()
        data class Duplicate(val existingEntry: PasswordEntry) : DuplicateCheckResult()
    }
    
    /**
     * 处理结果
     */
    sealed class ProcessResult {
        data class Success(val intent: Intent) : ProcessResult()
        data class Skip(val reason: String) : ProcessResult()
        data class Error(val message: String, val exception: Exception?) : ProcessResult()
    }
    
    /**
     * 处理保存请求
     * 
     * @param request SaveRequest
     * @return ProcessResult 处理结果
     */
    suspend fun process(request: SaveRequest): ProcessResult {
        val startTime = System.currentTimeMillis()
        
        try {
            AutofillLogger.i(TAG, "Processing save request")
            
            // 1. 提取表单数据
            val saveData = extractFormData(request)
            if (saveData == null) {
                val reason = "Failed to extract form data"
                AutofillLogger.w(TAG, reason)
                diagnostics.logSaveResult(
                    success = false,
                    action = "skipped",
                    reason = reason,
                    processingTimeMs = System.currentTimeMillis() - startTime
                )
                return ProcessResult.Skip(reason)
            }
            
            // 记录保存请求
            diagnostics.logSaveRequest(
                packageName = saveData.packageName ?: "unknown",
                domain = saveData.domain,
                hasUsername = saveData.username.isNotEmpty(),
                hasPassword = saveData.password.isNotEmpty(),
                isNewPasswordScenario = saveData.isNewPasswordScenario
            )
            
            // 2. 验证数据
            val validationResult = validateData(saveData)
            if (validationResult is ValidationResult.Invalid) {
                AutofillLogger.w(TAG, "Data validation failed: ${validationResult.reason}")
                diagnostics.logSaveResult(
                    success = false,
                    action = "skipped",
                    reason = validationResult.reason,
                    processingTimeMs = System.currentTimeMillis() - startTime
                )
                return ProcessResult.Skip(validationResult.reason)
            }
            
            // 3. 检查重复
            val duplicateResult = checkDuplicate(saveData)
            
            // 4. 创建保存 Intent
            val intent = createSaveIntent(saveData, duplicateResult)
            
            val processingTime = System.currentTimeMillis() - startTime
            AutofillLogger.i(TAG, "Save request processed successfully in ${processingTime}ms")
            
            diagnostics.logSaveResult(
                success = true,
                action = when (duplicateResult) {
                    is DuplicateCheckResult.Duplicate -> "update"
                    is DuplicateCheckResult.NoDuplicate -> "create"
                },
                reason = null,
                processingTimeMs = processingTime
            )
            
            return ProcessResult.Success(intent)
            
        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            AutofillLogger.e(TAG, "Error processing save request", e)
            diagnostics.logSaveResult(
                success = false,
                action = "error",
                reason = e.message,
                processingTimeMs = processingTime
            )
            return ProcessResult.Error(e.message ?: "Unknown error", e)
        }
    }
    
    /**
     * 提取表单数据
     */
    private fun extractFormData(request: SaveRequest): SaveData? {
        try {
            val contexts = request.fillContexts
            if (contexts.isEmpty()) {
                AutofillLogger.w(TAG, "No fill contexts in save request")
                return null
            }
            
            val structure = contexts.last().structure
            val parser = EnhancedAutofillStructureParserV2()
            val parsedStructure = parser.parse(structure, respectAutofillOff = false)
            
            // 提取用户名
            val usernameItem = parsedStructure.items.firstOrNull { item ->
                item.hint == EnhancedAutofillStructureParserV2.FieldHint.USERNAME ||
                item.hint == EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS
            }
            
            // 提取密码
            val passwordItem = parsedStructure.items.firstOrNull { item ->
                item.hint == EnhancedAutofillStructureParserV2.FieldHint.PASSWORD ||
                item.hint == EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD
            }
            
            // 从 AssistStructure 中提取实际值
            val username = usernameItem?.let { extractValueFromStructure(structure, it.id) } ?: ""
            val password = passwordItem?.let { extractValueFromStructure(structure, it.id) } ?: ""
            
            // 判断是否是新密码场景
            val isNewPasswordScenario = parsedStructure.items.any { 
                it.hint == EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD 
            }
            
            AutofillLogger.d(TAG, buildString {
                append("Extracted form data:\n")
                append("  Username: ${if (username.isNotEmpty()) "[${username.length} chars]" else "[empty]"}\n")
                append("  Password: ${if (password.isNotEmpty()) "[${password.length} chars]" else "[empty]"}\n")
                append("  Package: ${parsedStructure.applicationId}\n")
                append("  Domain: ${parsedStructure.webDomain}\n")
                append("  New password scenario: $isNewPasswordScenario")
            })
            
            return SaveData(
                username = username,
                password = password,
                packageName = parsedStructure.applicationId,
                domain = parsedStructure.webDomain,
                isNewPasswordScenario = isNewPasswordScenario
            )
            
        } catch (e: Exception) {
            AutofillLogger.e(TAG, "Failed to extract form data", e)
            return null
        }
    }
    
    /**
     * 从 AssistStructure 中提取字段值
     */
    private fun extractValueFromStructure(
        structure: AssistStructure,
        targetId: android.view.autofill.AutofillId
    ): String? {
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val value = extractValueFromNode(windowNode.rootViewNode, targetId)
            if (value != null) {
                return value
            }
        }
        return null
    }
    
    /**
     * 递归从节点中提取值
     */
    private fun extractValueFromNode(
        node: AssistStructure.ViewNode,
        targetId: android.view.autofill.AutofillId
    ): String? {
        if (node.autofillId == targetId) {
            return (node.autofillValue).safeTextOrNull(
                tag = TAG,
                fieldDescription = node.idEntry ?: node.className ?: "unknown"
            )
        }
        
        for (i in 0 until node.childCount) {
            node.getChildAt(i)?.let { child ->
                val value = extractValueFromNode(child, targetId)
                if (value != null) {
                    return value
                }
            }
        }
        
        return null
    }
    
    /**
     * 验证数据有效性
     */
    private fun validateData(saveData: SaveData): ValidationResult {
        // 密码不能为空
        if (saveData.password.isEmpty()) {
            return ValidationResult.Invalid("Password is empty")
        }
        
        // 密码长度检查
        if (saveData.password.length < 1) {
            return ValidationResult.Invalid("Password is too short")
        }
        
        // 如果有用户名，检查用户名长度
        if (saveData.username.isNotEmpty() && saveData.username.length > 255) {
            return ValidationResult.Invalid("Username is too long")
        }
        
        // 密码长度上限检查
        if (saveData.password.length > 1000) {
            return ValidationResult.Invalid("Password is too long")
        }
        
        AutofillLogger.d(TAG, "Data validation passed")
        return ValidationResult.Valid
    }
    
    /**
     * 检查重复密码
     */
    private suspend fun checkDuplicate(saveData: SaveData): DuplicateCheckResult {
        try {
            val allPasswords = passwordRepository.getAllPasswordEntries()
                .first()
            
            // 查找匹配的密码条目
            val duplicate = allPasswords.firstOrNull { entry ->
                // 检查包名匹配
                val packageMatch = saveData.packageName != null && 
                    entry.appPackageName.isNotEmpty() &&
                    entry.appPackageName == saveData.packageName
                
                // 检查域名匹配
                val domainMatch = saveData.domain != null &&
                    entry.website.isNotEmpty() &&
                    entry.website.contains(saveData.domain, ignoreCase = true)
                
                // 检查用户名匹配
                val usernameMatch = saveData.username.isNotEmpty() &&
                    entry.username.isNotEmpty() &&
                    entry.username.equals(saveData.username, ignoreCase = true)
                
                // 如果包名或域名匹配，且用户名也匹配，则认为是重复
                (packageMatch || domainMatch) && usernameMatch
            }
            
            return if (duplicate != null) {
                AutofillLogger.i(TAG, "Found duplicate entry: ${duplicate.title}")
                DuplicateCheckResult.Duplicate(duplicate)
            } else {
                AutofillLogger.d(TAG, "No duplicate found")
                DuplicateCheckResult.NoDuplicate
            }
            
        } catch (e: Exception) {
            AutofillLogger.e(TAG, "Error checking duplicates", e)
            // 出错时假设没有重复
            return DuplicateCheckResult.NoDuplicate
        }
    }
    
    /**
     * 创建保存 Intent
     */
    private fun createSaveIntent(
        saveData: SaveData,
        duplicateResult: DuplicateCheckResult
    ): Intent {
        // 这里应该创建启动保存 Activity 的 Intent
        // 根据项目中现有的保存 Activity 来创建
        val intent = Intent(context, Class.forName("takagi.ru.monica.autofill.AutofillSaveTransparentActivity"))
        
        // 添加数据
        intent.putExtra("username", saveData.username)
        intent.putExtra("password", saveData.password)
        intent.putExtra("package_name", saveData.packageName)
        intent.putExtra("domain", saveData.domain)
        intent.putExtra("is_new_password", saveData.isNewPasswordScenario)
        
        // 如果是重复，添加现有条目的 ID
        if (duplicateResult is DuplicateCheckResult.Duplicate) {
            intent.putExtra("existing_entry_id", duplicateResult.existingEntry.id)
            intent.putExtra("is_update", true)
        } else {
            intent.putExtra("is_update", false)
        }
        
        // 添加必要的标志
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        
        AutofillLogger.d(TAG, "Created save intent: update=${duplicateResult is DuplicateCheckResult.Duplicate}")
        
        return intent
    }
}


/**
 * 安全地从 AutofillValue 提取文本值的扩展函数
 * 处理不同类型的 AutofillValue，避免类型错误
 */
private fun AutofillValue?.safeTextOrNull(tag: String, fieldDescription: String): String? {
    if (this == null) {
        return null
    }
    
    return try {
        when {
            this.isText -> this.textValue?.toString()
            this.isList -> {
                // List 类型（下拉选择），记录但返回 null
                AutofillLogger.d(tag, "Field '$fieldDescription' is LIST type, index=${this.listValue}")
                null
            }
            this.isToggle -> {
                // Toggle 类型（复选框/开关），记录但返回 null
                AutofillLogger.d(tag, "Field '$fieldDescription' is TOGGLE type, value=${this.toggleValue}")
                null
            }
            this.isDate -> {
                // Date 类型，记录但返回 null
                AutofillLogger.d(tag, "Field '$fieldDescription' is DATE type, value=${this.dateValue}")
                null
            }
            else -> {
                AutofillLogger.w(tag, "Field '$fieldDescription' has unknown AutofillValue type")
                null
            }
        }
    } catch (e: Exception) {
        AutofillLogger.e(tag, "Failed to extract value from field '$fieldDescription'", e)
        null
    }
}
