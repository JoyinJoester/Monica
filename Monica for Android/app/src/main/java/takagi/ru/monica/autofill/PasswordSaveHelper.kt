package takagi.ru.monica.autofill

import android.app.assist.AssistStructure
import android.content.Context
import android.content.pm.PackageManager
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.autofill.core.AutofillLogger
import java.net.URL
import java.util.Date

/**
 * 密码保存助手类
 * 
 * 提供密码保存过程中的各种工具方法:
 * - 智能标题生成
 * - 重复密码检测
 * - 应用名称提取
 * - 网站域名提取和清理
 * - 密码更新 vs 新建逻辑
 * 
 * @author Monica Team
 * @since 1.5.0 - Password Save Feature
 */
object PasswordSaveHelper {
    
    /**
     * 保存数据
     * 
     * 封装保存请求中需要处理的所有数据
     */
    data class SaveData(
        val username: String,
        val password: String,
        val newPassword: String? = null,
        val confirmPassword: String? = null,
        val packageName: String,
        val webDomain: String?,
        val isNewPasswordScenario: Boolean = false,
        val keepassDatabaseId: Long? = null
    ) {
        /**
         * 验证保存数据的有效性
         */
        fun validate(): ValidationResult {
            // 1. 至少要有密码
            if (password.isBlank() && newPassword.isNullOrBlank()) {
                return ValidationResult.Error("密码不能为空")
            }
            
            // 2. 如果是新密码场景,验证新密码和确认密码匹配
            if (isNewPasswordScenario && !newPassword.isNullOrBlank()) {
                if (!confirmPassword.isNullOrBlank() && newPassword != confirmPassword) {
                    return ValidationResult.Error("新密码和确认密码不匹配")
                }
                if (confirmPassword.isNullOrBlank()) {
                    return ValidationResult.Warning("已检测到单个新密码字段,建议确认输入无误后继续")
                }
                if (newPassword.length < 6) {
                    return ValidationResult.Warning("密码长度建议至少6位")
                }
            }
            
            // 3. 验证必须有来源(应用或网站)
            if (packageName.isBlank() && webDomain.isNullOrBlank()) {
                return ValidationResult.Error("无法确定密码来源")
            }
            
            return ValidationResult.Success
        }
        
        /**
         * 获取最终要保存的密码(优先使用新密码)
         */
        fun getFinalPassword(): String {
            return if (isNewPasswordScenario && !newPassword.isNullOrBlank()) {
                newPassword
            } else {
                password
            }
        }
    }
    
    /**
     * 验证结果
     */
    sealed class ValidationResult {
        object Success : ValidationResult()
        data class Warning(val message: String) : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }
    
    /**
     * 重复检测结果
     */
    sealed class DuplicateCheckResult {
        /** 没有重复,可以直接保存 */
        object NoDuplicate : DuplicateCheckResult()
        
        /** 完全重复(用户名和密码都相同),无需保存 */
        data class ExactDuplicate(val existingEntry: PasswordEntry) : DuplicateCheckResult()
        
        /** 用户名相同但密码不同,建议更新 */
        data class SameUsernameDifferentPassword(val existingEntry: PasswordEntry) : DuplicateCheckResult()
        
        /** 相同应用/网站的不同账号 */
        data class DifferentAccount(val existingEntries: List<PasswordEntry>) : DuplicateCheckResult()
    }
    
    /**
     * 生成智能标题
     * 
     * 优先级:
     * 1. 应用名称(如果是原生应用)
     * 2. 网站域名(如果是网页)
     * 3. 包名(兜底)
     * 
     * @param context Context
     * @param packageName 应用包名
     * @param webDomain 网站域名
     * @param username 用户名(可选,用于生成更具体的标题)
     * @return 生成的标题
     */
    fun generateTitle(
        context: Context,
        packageName: String,
        webDomain: String?,
        username: String? = null
    ): String {
        AutofillLogger.d("SAVE", "生成标题: packageName=$packageName, domain=$webDomain, username=$username")
        
        val baseTitle = when {
            // 优先使用应用名
            packageName.isNotBlank() && webDomain.isNullOrBlank() -> {
                getAppName(context, packageName) ?: packageName
            }
            // 其次使用域名
            !webDomain.isNullOrBlank() -> {
                cleanDomain(webDomain)
            }
            // 兜底使用包名
            else -> packageName
        }
        
        // 如果有用户名,可以生成更具体的标题
        return if (!username.isNullOrBlank() && username.length <= 20) {
            "$baseTitle ($username)"
        } else {
            baseTitle
        }
    }
    
    /**
     * 获取应用名称
     * 
     * @param context Context
     * @param packageName 应用包名
     * @return 应用名称,失败返回null
     */
    fun getAppName(context: Context, packageName: String): String? {
        return try {
            if (packageName.isBlank()) return null
            
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val appName = context.packageManager.getApplicationLabel(appInfo).toString()
            
            AutofillLogger.d("SAVE", "获取应用名称成功: $packageName -> $appName")
            appName
        } catch (e: PackageManager.NameNotFoundException) {
            AutofillLogger.w("SAVE", "应用未找到: $packageName")
            null
        } catch (e: Exception) {
            AutofillLogger.e("SAVE", "获取应用名称失败", e)
            null
        }
    }
    
    /**
     * 清理域名
     * 
     * 提取主域名,移除子域名、协议、路径等
     * 
     * 示例:
     * - https://www.example.com/login -> example.com
     * - m.facebook.com -> facebook.com
     * - login.microsoftonline.com -> microsoftonline.com
     * 
     * @param domain 原始域名或URL
     * @return 清理后的主域名
     */
    fun cleanDomain(domain: String): String {
        try {
            var cleaned = domain.trim()
            
            // 1. 如果是完整URL,提取域名部分
            if (cleaned.contains("://")) {
                cleaned = try {
                    URL(cleaned).host
                } catch (e: Exception) {
                    // URL解析失败,手动提取
                    cleaned.substringAfter("://").substringBefore("/")
                }
            }
            
            // 2. 移除端口号
            cleaned = cleaned.substringBefore(":")
            
            // 3. 移除常见的子域名前缀
            val commonSubdomains = listOf("www.", "m.", "mobile.", "login.", "auth.", "account.")
            for (prefix in commonSubdomains) {
                if (cleaned.startsWith(prefix, ignoreCase = true)) {
                    cleaned = cleaned.substring(prefix.length)
                    break
                }
            }
            
            AutofillLogger.d("SAVE", "域名清理: $domain -> $cleaned")
            return cleaned
        } catch (e: Exception) {
            AutofillLogger.e("SAVE", "域名清理失败", e)
            return domain
        }
    }
    
    /**
     * 从AssistStructure提取网站域名
     * 
     * 尝试多种方法:
     * 1. 直接读取webDomain属性
     * 2. 从节点文本中提取URL
     * 3. 从contentDescription中提取URL
     * 
     * @param structure AssistStructure
     * @return 提取的域名,失败返回null
     */
    fun extractWebDomain(structure: AssistStructure): String? {
        try {
            for (i in 0 until structure.windowNodeCount) {
                val windowNode = structure.getWindowNodeAt(i)
                val domain = extractWebDomainFromNode(windowNode.rootViewNode)
                if (domain != null) {
                    AutofillLogger.i("SAVE", "提取到网站域名: $domain")
                    return domain
                }
            }
        } catch (e: Exception) {
            AutofillLogger.e("SAVE", "提取网站域名失败", e)
        }
        return null
    }
    
    /**
     * 从ViewNode递归提取域名
     */
    private fun extractWebDomainFromNode(node: AssistStructure.ViewNode): String? {
        // 1. 检查webDomain属性
        node.webDomain?.let { 
            return it 
        }
        
        // 2. 检查节点文本
        node.text?.toString()?.let { text ->
            if (text.contains("://") || text.contains(".com") || text.contains(".org")) {
                extractDomainFromUrl(text)?.let { return it }
            }
        }
        
        // 3. 检查contentDescription
        node.contentDescription?.toString()?.let { desc ->
            if (desc.contains("://") || desc.contains(".com")) {
                extractDomainFromUrl(desc)?.let { return it }
            }
        }
        
        // 4. 递归检查子节点
        for (i in 0 until node.childCount) {
            extractWebDomainFromNode(node.getChildAt(i))?.let { return it }
        }
        
        return null
    }
    
    /**
     * 从URL字符串中提取域名
     */
    private fun extractDomainFromUrl(url: String): String? {
        return try {
            val urlPattern = Regex("https?://([^/\\s]+)")
            val match = urlPattern.find(url)
            match?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 检测重复密码
     * 
     * @param saveData 要保存的数据
     * @param existingPasswords 现有的密码列表
     * @return 重复检测结果
     */
    fun checkDuplicate(
        saveData: SaveData,
        existingPasswords: List<PasswordEntry>,
        resolvePassword: (PasswordEntry) -> String = { it.password }
    ): DuplicateCheckResult {
        val finalPassword = saveData.getFinalPassword()
        
        // 1. 按来源筛选相关密码
        val relevantPasswords = existingPasswords.filter { entry ->
            if (saveData.webDomain != null) {
                // 网站场景:比较域名
                isSameDomain(entry.website, saveData.webDomain)
            } else {
                // 应用场景:比较包名
                entry.appPackageName == saveData.packageName
            }
        }
        
        if (relevantPasswords.isEmpty()) {
            return DuplicateCheckResult.NoDuplicate
        }
        
        // 2. 检查是否有完全相同的条目(用户名和密码都匹配)
        val exactMatch = relevantPasswords.firstOrNull { entry ->
            entry.username.equals(saveData.username, ignoreCase = true) &&
            resolvePassword(entry) == finalPassword
        }
        
        if (exactMatch != null) {
            AutofillLogger.i("SAVE", "发现完全重复的密码条目")
            return DuplicateCheckResult.ExactDuplicate(exactMatch)
        }
        
        // 3. 检查是否有相同用户名但不同密码(可能是密码更新)
        val sameUsername = relevantPasswords.firstOrNull { entry ->
            entry.username.equals(saveData.username, ignoreCase = true)
        }
        
        if (sameUsername != null) {
            AutofillLogger.i("SAVE", "发现相同用户名的密码条目,可能需要更新")
            return DuplicateCheckResult.SameUsernameDifferentPassword(sameUsername)
        }
        
        // 4. 相同应用/网站的不同账号
        if (relevantPasswords.isNotEmpty()) {
            AutofillLogger.i("SAVE", "发现相同应用/网站的其他账号: ${relevantPasswords.size}个")
            return DuplicateCheckResult.DifferentAccount(relevantPasswords)
        }
        
        return DuplicateCheckResult.NoDuplicate
    }
    
    /**
     * 判断两个域名是否相同
     * 
     * 使用清理后的主域名进行比较
     */
    private fun isSameDomain(domain1: String?, domain2: String?): Boolean {
        if (domain1.isNullOrBlank() || domain2.isNullOrBlank()) return false
        
        val cleaned1 = cleanDomain(domain1)
        val cleaned2 = cleanDomain(domain2)
        
        return cleaned1.equals(cleaned2, ignoreCase = true)
    }
    
    /**
     * 创建新的密码条目
     * 
     * @param context Context
     * @param saveData 保存数据
     * @param encryptedPassword 已加密的密码
     * @return PasswordEntry
     */
    fun createNewPasswordEntry(
        context: Context,
        saveData: SaveData,
        encryptedPassword: String
    ): PasswordEntry {
        val appName = if (saveData.packageName.isNotBlank()) {
            getAppName(context, saveData.packageName) ?: ""
        } else {
            ""
        }
        
        val website = if (saveData.webDomain != null) {
            cleanDomain(saveData.webDomain)
        } else {
            ""
        }
        
        val title = generateTitle(
            context,
            saveData.packageName,
            saveData.webDomain,
            saveData.username
        )
        
        AutofillLogger.i("SAVE", "创建新密码条目: title=$title, website=$website, appName=$appName")
        
        return PasswordEntry(
            title = title,
            username = saveData.username,
            password = encryptedPassword,
            website = website,
            appPackageName = saveData.packageName,
            appName = appName,
            notes = if (saveData.isNewPasswordScenario) "通过自动填充保存(注册)" else "通过自动填充保存",
            keepassDatabaseId = saveData.keepassDatabaseId,
            createdAt = Date(),
            updatedAt = Date()
        )
    }
    
    /**
     * 更新现有密码条目
     * 
     * @param existing 现有条目
     * @param saveData 新数据
     * @param encryptedPassword 已加密的新密码
     * @return 更新后的PasswordEntry
     */
    fun updatePasswordEntry(
        existing: PasswordEntry,
        saveData: SaveData,
        encryptedPassword: String
    ): PasswordEntry {
        AutofillLogger.i("SAVE", "更新密码条目: id=${existing.id}, title=${existing.title}")
        
        return existing.copy(
            password = encryptedPassword,
            updatedAt = Date(),
            keepassDatabaseId = saveData.keepassDatabaseId ?: existing.keepassDatabaseId, // Update if new ID provided, else keep existing? Or should SaveData always have it? If UI selects it, SaveData has it.
            notes = existing.notes + "\n[${Date()}] 通过自动填充更新密码"
        )
    }
}
