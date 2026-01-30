package takagi.ru.monica.autofill.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.core.graphics.drawable.toBitmap
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.autofill.EnhancedAutofillStructureParserV2
import takagi.ru.monica.autofill.AutofillPickerLauncher

/**
 * 安全的填充响应构建器
 * 
 * 确保 Dataset 构建不会失败，提供完整的错误处理
 * 
 * 功能:
 * - 验证字段有效性
 * - 确保至少填充一个字段
 * - 捕获并记录构建异常
 * - 跳过无效的 Dataset
 * - 支持内联和下拉展示
 * - 详细的错误报告
 * 
 * @author Monica Team
 * @since 2.0
 */
class SafeResponseBuilder(
    private val context: Context,
    private val packageManager: PackageManager
) {
    
    companion object {
        private const val TAG = "SafeResponseBuilder"
    }
    
    /**
     * 构建结果
     */
    data class BuildResult(
        val response: FillResponse?,
        val datasetsCreated: Int,
        val datasetsFailed: Int,
        val errors: List<BuildError>
    ) {
        /**
         * 是否成功
         */
        fun isSuccess(): Boolean = response != null && datasetsCreated > 0
        
        /**
         * 获取成功率
         */
        fun getSuccessRate(): Float {
            val total = datasetsCreated + datasetsFailed
            return if (total > 0) datasetsCreated.toFloat() / total else 0f
        }
    }
    
    /**
     * 构建错误
     */
    data class BuildError(
        val passwordId: Long,
        val passwordTitle: String,
        val errorMessage: String,
        val exception: Exception?
    )
    
    /**
     * 解析的字段信息
     */
    data class ParsedFieldInfo(
        val id: AutofillId,
        val hint: EnhancedAutofillStructureParserV2.FieldHint,
        val isFocused: Boolean = false
    )
    
    /**
     * 构建填充响应
     * 带完整的错误处理
     * 
     * @param passwords 密码列表
     * @param parsedFields 解析的字段列表
     * @param inlineRequest 内联建议请求
     * @param packageName 应用包名
     * @param domain 网站域名
     * @param parsedStructure 解析的结构(用于Picker)
     * @param usePickerForMultiple 当有多个密码时是否使用Picker UI(默认true)
     * @return 构建结果
     */
    fun buildResponse(
        passwords: List<PasswordEntry>,
        parsedFields: List<ParsedFieldInfo>,
        inlineRequest: InlineSuggestionsRequest?,
        packageName: String,
        domain: String? = null,
        parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure? = null,
        usePickerForMultiple: Boolean = false  // 默认禁用,避免破坏现有功能
    ): BuildResult {
        AutofillLogger.d(TAG, "Building fill response for ${passwords.size} passwords")
        
        // 🎯 新功能: 当有多个密码时,使用 AutofillPickerActivity (目前默认禁用)
        if (usePickerForMultiple && passwords.size > 1 && parsedStructure != null) {
            AutofillLogger.i(TAG, "Using AutofillPicker UI for ${passwords.size} passwords")
            
            return try {
                val pickerResponse = AutofillPickerLauncher.createPickerResponse(
                    context = context,
                    passwords = passwords,
                    packageName = packageName,
                    domain = domain,
                    parsedStructure = parsedStructure
                )
                
                BuildResult(
                    response = pickerResponse,
                    datasetsCreated = 1,
                    datasetsFailed = 0,
                    errors = emptyList()
                )
            } catch (e: Exception) {
                AutofillLogger.e(TAG, "Failed to create picker response, falling back to standard datasets", e)
                // 如果创建Picker失败,回退到标准方式
                buildStandardResponse(passwords, parsedFields, inlineRequest, packageName)
            }
        }
        
        // 单个密码或禁用Picker时,使用标准方式
        return buildStandardResponse(passwords, parsedFields, inlineRequest, packageName)
    }
    
    /**
     * 构建标准的填充响应(原有逻辑)
     */
    private fun buildStandardResponse(
        passwords: List<PasswordEntry>,
        parsedFields: List<ParsedFieldInfo>,
        inlineRequest: InlineSuggestionsRequest?,
        packageName: String
    ): BuildResult {
        AutofillLogger.d(TAG, "Building standard fill response for ${passwords.size} passwords")
        
        val responseBuilder = FillResponse.Builder()
        val errors = mutableListOf<BuildError>()
        var successCount = 0
        var failCount = 0
        
        // 获取内联建议规格
        val inlineSpecs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlineRequest != null) {
            inlineRequest.inlinePresentationSpecs
        } else {
            null
        }
        
        val maxInlineSuggestions = inlineRequest?.maxSuggestionCount ?: 0
        
        // 为每个密码创建 Dataset
        passwords.forEachIndexed { index, password ->
            try {
                val dataset = buildDataset(
                    password = password,
                    parsedFields = parsedFields,
                    inlineSpec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
                                    inlineSpecs != null && 
                                    index < maxInlineSuggestions && 
                                    index < inlineSpecs.size) {
                        inlineSpecs[index]
                    } else null,
                    packageName = packageName
                )
                
                if (dataset != null) {
                    responseBuilder.addDataset(dataset)
                    successCount++
                    AutofillLogger.d(TAG, "Dataset created successfully for: ${password.title}")
                } else {
                    failCount++
                    errors.add(BuildError(
                        passwordId = password.id,
                        passwordTitle = password.title,
                        errorMessage = "No fields could be filled",
                        exception = null
                    ))
                    AutofillLogger.w(TAG, "Dataset skipped (no fields filled): ${password.title}")
                }
            } catch (e: Exception) {
                failCount++
                errors.add(BuildError(
                    passwordId = password.id,
                    passwordTitle = password.title,
                    errorMessage = e.message ?: "Unknown error",
                    exception = e
                ))
                AutofillLogger.e(TAG, "Failed to build dataset for: ${password.title}", e)
            }
        }
        
        // 如果没有成功创建任何 Dataset，返回 null
        val response = if (successCount > 0) {
            try {
                responseBuilder.build()
            } catch (e: Exception) {
                AutofillLogger.e(TAG, "Failed to build FillResponse", e)
                null
            }
        } else {
            AutofillLogger.w(TAG, "No datasets created, returning null response")
            null
        }
        
        AutofillLogger.i(TAG, "Build completed: $successCount success, $failCount failed")
        
        return BuildResult(
            response = response,
            datasetsCreated = successCount,
            datasetsFailed = failCount,
            errors = errors
        )
    }
    
    /**
     * 构建单个 Dataset
     * 确保至少填充一个字段
     * 
     * @return Dataset 或 null（如果无法构建）
     */
    private fun buildDataset(
        password: PasswordEntry,
        parsedFields: List<ParsedFieldInfo>,
        inlineSpec: InlinePresentationSpec?,
        packageName: String
    ): Dataset? {
        val datasetBuilder = Dataset.Builder()
        var hasFilledAnyField = false
        
        // 创建展示视图
        val presentation = createPresentation(password, packageName)
        val inlinePresentation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlineSpec != null) {
            createInlinePresentation(password, packageName, inlineSpec)
        } else {
            null
        }
        
        // 填充字段
        parsedFields.forEach { field ->
            val value = getValueForField(password, field.hint)
            if (value != null && value.isNotBlank()) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePresentation != null) {
                        @Suppress("NewApi")
                        datasetBuilder.setValue(
                            field.id,
                            AutofillValue.forText(value),
                            presentation,
                            inlinePresentation
                        )
                    } else {
                        datasetBuilder.setValue(
                            field.id,
                            AutofillValue.forText(value),
                            presentation
                        )
                    }
                    hasFilledAnyField = true
                    AutofillLogger.d(TAG, "Filled field: ${field.hint}")
                } catch (e: Exception) {
                    AutofillLogger.w(TAG, "Failed to set value for field: ${field.hint}", mapOf("error" to e.message.toString()))
                }
            }
        }
        
        // 只有在至少填充了一个字段时才返回 Dataset
        return if (hasFilledAnyField) {
            try {
                datasetBuilder.build()
            } catch (e: Exception) {
                AutofillLogger.e(TAG, "Failed to build dataset", e)
                null
            }
        } else {
            null
        }
    }
    
    /**
     * 根据字段类型获取对应的值
     */
    private fun getValueForField(
        password: PasswordEntry,
        fieldHint: EnhancedAutofillStructureParserV2.FieldHint
    ): String? {
        return when (fieldHint) {
            EnhancedAutofillStructureParserV2.FieldHint.USERNAME -> password.username
            EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS -> {
                // 如果用户名是邮箱格式，使用用户名，否则返回 null
                if (password.username.contains("@")) password.username else null
            }
            EnhancedAutofillStructureParserV2.FieldHint.PASSWORD -> password.password
            EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD -> password.password
            EnhancedAutofillStructureParserV2.FieldHint.PHONE_NUMBER -> password.phone
            EnhancedAutofillStructureParserV2.FieldHint.POSTAL_ADDRESS -> password.addressLine
            EnhancedAutofillStructureParserV2.FieldHint.POSTAL_CODE -> password.zipCode
            EnhancedAutofillStructureParserV2.FieldHint.PERSON_NAME -> password.creditCardHolder
            EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_NUMBER -> password.creditCardNumber
            EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_EXPIRATION_DATE -> password.creditCardExpiry
            EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_SECURITY_CODE -> password.creditCardCVV
            else -> null
        }
    }
    
    /**
     * 创建展示视图
     */
    private fun createPresentation(
        password: PasswordEntry,
        packageName: String
    ): RemoteViews {
        val presentation = RemoteViews(context.packageName, R.layout.autofill_dataset_item)
        
        // 设置标题
        val displayTitle = if (password.title.isNotBlank()) {
            password.title
        } else {
            getAppName(packageName)
        }
        presentation.setTextViewText(R.id.text_title, displayTitle)
        
        // 设置用户名
        val displayUsername = if (password.username.isNotBlank()) {
            password.username
        } else {
            "无用户名"
        }
        presentation.setTextViewText(R.id.text_username, displayUsername)
        
        return presentation
    }
    
    /**
     * 创建内联展示
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun createInlinePresentation(
        password: PasswordEntry,
        packageName: String,
        inlineSpec: InlinePresentationSpec
    ): InlinePresentation? {
        return try {
            // 检查是否支持 UiVersions.INLINE_UI_VERSION_1
            if (!UiVersions.getVersions(inlineSpec.style).contains(UiVersions.INLINE_UI_VERSION_1)) {
                AutofillLogger.w(TAG, "Inline UI version 1 not supported")
                return null
            }
            
            val displayTitle = if (password.title.isNotBlank()) {
                password.title
            } else {
                getAppName(packageName)
            }
            
            val displayUsername = if (password.username.isNotBlank()) {
                password.username
            } else {
                "无用户名"
            }
            
            // 创建内联建议 UI
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.app.PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
            val slice = InlineSuggestionUi.newContentBuilder(android.app.PendingIntent.getActivity(
                context,
                0,
                context.packageManager.getLaunchIntentForPackage(context.packageName),
                pendingIntentFlags
            ))
                .setTitle(displayTitle)
                .setSubtitle(displayUsername)
                .build()
                .slice
            
            InlinePresentation(slice, inlineSpec, false)
        } catch (e: Exception) {
            AutofillLogger.e(TAG, "Failed to create inline presentation", e)
            null
        }
    }
    
    /**
     * 获取应用名称
     */
    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
    
    /**
     * 验证字段是否有效
     */
    fun validateField(fieldId: AutofillId?, value: String?): Boolean {
        if (fieldId == null) {
            AutofillLogger.w(TAG, "Field ID is null")
            return false
        }
        
        if (value.isNullOrBlank()) {
            AutofillLogger.w(TAG, "Field value is null or blank")
            return false
        }
        
        return true
    }
    
    /**
     * 获取构建统计信息
     */
    fun getStats(result: BuildResult): Map<String, Any> {
        return mapOf(
            "datasetsCreated" to result.datasetsCreated,
            "datasetsFailed" to result.datasetsFailed,
            "successRate" to String.format("%.1f%%", result.getSuccessRate() * 100),
            "errorCount" to result.errors.size,
            "hasResponse" to (result.response != null)
        )
    }
}
