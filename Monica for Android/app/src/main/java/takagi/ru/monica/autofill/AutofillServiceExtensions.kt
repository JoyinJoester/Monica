package takagi.ru.monica.autofill

import android.content.Context
import android.service.autofill.FillResponse
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.autofill.core.AutofillLogger

/**
 * MonicaAutofillService 扩展函数
 * 
 * 提供便捷的方法来创建自动填充响应
 */

/**
 * 智能创建填充响应
 * 
 * 新的用户体验:
 * - 直接显示所有匹配的密码作为独立选项
 * - 添加"手动选择"选项查看所有密码
 * - 选择后立即填充,无需再次点击
 * 
 * @param context Context
 * @param passwords 匹配的密码列表
 * @param packageName 应用包名
 * @param domain 网站域名
 * @param parsedStructure 解析的表单结构
 * @return FillResponse 或 null
 */
fun createSmartFillResponse(
    context: Context,
    passwords: List<PasswordEntry>,
    packageName: String?,
    domain: String?,
    parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure
): FillResponse? {
    return try {
        AutofillLogger.i("SMART_FILL", "Creating smart fill response with ${passwords.size} matched passwords")
        
        // 创建包含所有匹配密码 + "手动选择"选项的响应
        AutofillPickerLauncher.createDirectListResponse(
            context = context,
            matchedPasswords = passwords,
            allPasswordIds = emptyList(), // 将在 Picker 中加载所有密码
            packageName = packageName,
            domain = domain,
            parsedStructure = parsedStructure
        )
    } catch (e: Exception) {
        AutofillLogger.e("SMART_FILL", "Failed to create smart fill response", e)
        null
    }
}

/**
 * 创建带Picker的填充响应(强制使用Picker UI)
 * 
 * 即使只有一个密码也使用Picker UI
 * 适用于需要展示更多信息或选项的场景
 * 
 * @param context Context
 * @param passwords 密码列表
 * @param packageName 应用包名
 * @param domain 网站域名
 * @param parsedStructure 解析的表单结构
 * @return FillResponse 或 null
 */
fun createPickerFillResponse(
    context: Context,
    passwords: List<PasswordEntry>,
    packageName: String?,
    domain: String?,
    parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure
): FillResponse? {
    if (passwords.isEmpty()) {
        AutofillLogger.w("PICKER_FILL", "No passwords to show in picker")
        return null
    }
    
    return try {
        AutofillLogger.i("PICKER_FILL", "Creating picker response for ${passwords.size} passwords")
        AutofillPickerLauncher.createPickerResponse(
            context = context,
            passwords = passwords,
            packageName = packageName,
            domain = domain,
            parsedStructure = parsedStructure
        )
    } catch (e: Exception) {
        AutofillLogger.e("PICKER_FILL", "Failed to create picker response", e)
        null
    }
}

/**
 * 创建直接填充响应(不使用Picker UI)
 * 
 * 直接填充指定的密码,不显示选择界面
 * 
 * @param context Context
 * @param password 要填充的密码
 * @param parsedStructure 解析的表单结构
 * @return FillResponse 或 null
 */
fun createDirectFillResponse(
    context: Context,
    password: PasswordEntry,
    parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure
): FillResponse? {
    return try {
        AutofillLogger.i("DIRECT_FILL", "Creating direct fill response for: ${password.title}")
        AutofillPickerLauncher.createDirectFillResponse(
            context = context,
            password = password,
            parsedStructure = parsedStructure
        )
    } catch (e: Exception) {
        AutofillLogger.e("DIRECT_FILL", "Failed to create direct fill response", e)
        null
    }
}

