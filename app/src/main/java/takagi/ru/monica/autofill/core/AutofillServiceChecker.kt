package takagi.ru.monica.autofill.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import takagi.ru.monica.autofill.MonicaAutofillService
import takagi.ru.monica.utils.DeviceUtils

/**
 * 自动填充服务状态检查器
 * 
 * 检查服务是否正确配置和启用，并提供诊断信息和修复建议
 * 
 * 功能:
 * - 检查服务是否在 Manifest 中声明
 * - 检查系统是否启用了自动填充服务
 * - 检查应用内是否启用了自动填充
 * - 检查所需权限
 * - 检测设备兼容性问题
 * - 生成修复建议
 * 
 * @author Monica Team
 * @since 2.0
 */
class AutofillServiceChecker(private val context: Context) {
    
    companion object {
        private const val TAG = "AutofillServiceChecker"
    }
    
    /**
     * 服务状态数据模型
     */
    data class ServiceStatus(
        val isServiceDeclared: Boolean,
        val isSystemEnabled: Boolean,
        val isAppEnabled: Boolean,
        val hasRequiredPermissions: Boolean,
        val compatibilityIssues: List<String>,
        val recommendations: List<String>
    ) {
        /**
         * 服务是否完全正常
         */
        fun isFullyOperational(): Boolean {
            return isServiceDeclared && 
                   isSystemEnabled && 
                   isAppEnabled && 
                   hasRequiredPermissions &&
                   compatibilityIssues.isEmpty()
        }
        
        /**
         * 获取状态摘要
         */
        fun getSummary(): String {
            return when {
                isFullyOperational() -> "自动填充服务运行正常"
                !isServiceDeclared -> "服务未正确声明"
                !isSystemEnabled -> "系统未启用自动填充服务"
                !isAppEnabled -> "应用内未启用自动填充"
                !hasRequiredPermissions -> "缺少必要权限"
                compatibilityIssues.isNotEmpty() -> "存在兼容性问题"
                else -> "服务状态异常"
            }
        }
    }
    
    /**
     * 检查服务状态
     * 
     * @return 完整的服务状态信息
     */
    fun checkServiceStatus(): ServiceStatus {
        AutofillLogger.i(TAG, "Starting service status check")
        
        val isServiceDeclared = checkServiceDeclared()
        val isSystemEnabled = checkSystemEnabled()
        val isAppEnabled = checkAppEnabled()
        val hasRequiredPermissions = checkPermissions()
        val compatibilityIssues = detectCompatibilityIssues()
        val recommendations = generateRecommendations(
            isServiceDeclared,
            isSystemEnabled,
            isAppEnabled,
            hasRequiredPermissions,
            compatibilityIssues
        )
        
        val status = ServiceStatus(
            isServiceDeclared = isServiceDeclared,
            isSystemEnabled = isSystemEnabled,
            isAppEnabled = isAppEnabled,
            hasRequiredPermissions = hasRequiredPermissions,
            compatibilityIssues = compatibilityIssues,
            recommendations = recommendations
        )
        
        AutofillLogger.i(TAG, "Service status check completed: ${status.getSummary()}")
        
        return status
    }
    
    /**
     * 检查服务是否在 Manifest 中声明
     */
    private fun checkServiceDeclared(): Boolean {
        return try {
            val packageManager = context.packageManager
            val componentName = ComponentName(context, MonicaAutofillService::class.java)
            
            // 检查服务是否存在
            val serviceInfo = packageManager.getServiceInfo(
                componentName,
                PackageManager.GET_META_DATA
            )
            
            // 验证服务配置
            val hasPermission = serviceInfo.permission == android.Manifest.permission.BIND_AUTOFILL_SERVICE
            
            if (!hasPermission) {
                AutofillLogger.w(TAG, "Service declared but missing BIND_AUTOFILL_SERVICE permission")
            }
            
            AutofillLogger.d(TAG, "Service declared: true, has permission: $hasPermission")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            AutofillLogger.e(TAG, "Service not declared in manifest", e)
            false
        } catch (e: Exception) {
            AutofillLogger.e(TAG, "Error checking service declaration", e)
            false
        }
    }
    
    /**
     * 检查系统是否启用了自动填充服务
     */
    private fun checkSystemEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val autofillManager = context.getSystemService(AutofillManager::class.java)
                
                // 检查是否有启用的自动填充服务
                val hasEnabledServices = autofillManager?.hasEnabledAutofillServices() == true
                
                if (hasEnabledServices) {
                    // 进一步检查是否是 Monica 服务
                    val componentName = ComponentName(context, MonicaAutofillService::class.java)
                    val currentService = autofillManager?.autofillServiceComponentName
                    
                    val isMonicaEnabled = currentService == componentName
                    
                    AutofillLogger.d(TAG, "System enabled: $hasEnabledServices, Monica enabled: $isMonicaEnabled")
                    AutofillLogger.d(TAG, "Current service: $currentService")
                    
                    return isMonicaEnabled
                } else {
                    AutofillLogger.d(TAG, "No autofill service enabled in system")
                    return false
                }
            } catch (e: Exception) {
                AutofillLogger.e(TAG, "Error checking system enabled status", e)
                false
            }
        } else {
            AutofillLogger.w(TAG, "Autofill not supported on Android < 8.0")
            false
        }
    }
    
    /**
     * 检查应用内是否启用了自动填充
     */
    private fun checkAppEnabled(): Boolean {
        return try {
            // 从 SharedPreferences 读取应用内设置
            val prefs = context.getSharedPreferences("autofill_prefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("autofill_enabled", true) // 默认启用
            
            AutofillLogger.d(TAG, "App-level autofill enabled: $isEnabled")
            isEnabled
        } catch (e: Exception) {
            AutofillLogger.e(TAG, "Error checking app-level settings", e)
            true // 默认假设启用
        }
    }
    
    /**
     * 检查所需权限
     */
    private fun checkPermissions(): Boolean {
        return try {
            // 自动填充服务不需要运行时权限
            // 只需要在 Manifest 中声明 BIND_AUTOFILL_SERVICE
            // 这个权限由系统自动授予
            
            // 检查是否有其他可选权限
            val hasInternetPermission = context.checkSelfPermission(
                android.Manifest.permission.INTERNET
            ) == PackageManager.PERMISSION_GRANTED
            
            val hasNetworkStatePermission = context.checkSelfPermission(
                android.Manifest.permission.ACCESS_NETWORK_STATE
            ) == PackageManager.PERMISSION_GRANTED
            
            AutofillLogger.d(TAG, "Internet permission: $hasInternetPermission")
            AutofillLogger.d(TAG, "Network state permission: $hasNetworkStatePermission")
            
            // 自动填充核心功能不依赖这些权限
            true
        } catch (e: Exception) {
            AutofillLogger.e(TAG, "Error checking permissions", e)
            true
        }
    }
    
    /**
     * 检测设备兼容性问题
     */
    private fun detectCompatibilityIssues(): List<String> {
        val issues = mutableListOf<String>()
        
        try {
            // 1. 检查 Android 版本
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                issues.add("Android 版本过低（需要 8.0 或更高版本）")
            }
            
            // 2. 检查设备品牌兼容性
            val manufacturer = Build.MANUFACTURER.lowercase()
            val romType = DeviceUtils.getROMType()
            
            when {
                manufacturer.contains("huawei") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    issues.add("华为设备在 Android 10+ 上可能存在自动填充限制")
                }
                manufacturer.contains("xiaomi") -> {
                    issues.add("小米设备需要在 MIUI 安全中心授予自动填充权限")
                }
                manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                    issues.add("OPPO/Realme 设备需要在权限管理中允许自动填充")
                }
                manufacturer.contains("vivo") -> {
                    issues.add("Vivo 设备需要在 i 管家中允许自动填充")
                }
                manufacturer.contains("samsung") -> {
                    // Samsung 通常兼容性较好
                    AutofillLogger.d(TAG, "Samsung device detected, generally good compatibility")
                }
            }
            
            // 3. 检查内联建议支持
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val supportsInline = DeviceUtils.supportsInlineSuggestions()
                if (!supportsInline) {
                    issues.add("设备不支持内联建议（键盘上方显示），将使用下拉菜单模式")
                }
            }
            
            // 4. 检查是否是模拟器
            val isEmulator = Build.FINGERPRINT.contains("generic") ||
                            Build.MODEL.contains("Emulator") ||
                            Build.MODEL.contains("Android SDK")
            
            if (isEmulator) {
                issues.add("检测到模拟器环境，自动填充功能可能不稳定")
            }
            
            // 5. 检查系统自动填充框架
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val autofillManager = context.getSystemService(AutofillManager::class.java)
                val isSupported = autofillManager?.isAutofillSupported == true
                
                if (!isSupported) {
                    issues.add("设备不支持自动填充框架")
                }
            }
            
            AutofillLogger.d(TAG, "Detected ${issues.size} compatibility issues")
            
        } catch (e: Exception) {
            AutofillLogger.e(TAG, "Error detecting compatibility issues", e)
            issues.add("无法完成兼容性检查")
        }
        
        return issues
    }
    
    /**
     * 生成修复建议
     */
    private fun generateRecommendations(
        isServiceDeclared: Boolean,
        isSystemEnabled: Boolean,
        isAppEnabled: Boolean,
        hasRequiredPermissions: Boolean,
        compatibilityIssues: List<String>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        try {
            // 1. 服务未声明
            if (!isServiceDeclared) {
                recommendations.add("请检查 AndroidManifest.xml 中是否正确声明了 MonicaAutofillService")
                recommendations.add("确保服务包含 BIND_AUTOFILL_SERVICE 权限和 autofill intent-filter")
            }
            
            // 2. 系统未启用
            if (!isSystemEnabled) {
                recommendations.add("请在系统设置中启用 Monica 作为自动填充服务")
                recommendations.add("路径：设置 → 系统 → 语言和输入法 → 自动填充服务")
                
                // 针对不同品牌提供具体路径
                val manufacturer = Build.MANUFACTURER.lowercase()
                when {
                    manufacturer.contains("xiaomi") -> {
                        recommendations.add("小米设备：设置 → 更多设置 → 语言和输入法 → 自动填充服务")
                        recommendations.add("同时需要在 MIUI 安全中心授予权限")
                    }
                    manufacturer.contains("huawei") -> {
                        recommendations.add("华为设备：设置 → 系统和更新 → 语言和输入法 → 自动填充服务")
                    }
                    manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                        recommendations.add("OPPO/Realme：设置 → 其他设置 → 键盘与输入法 → 自动填充服务")
                    }
                    manufacturer.contains("vivo") -> {
                        recommendations.add("Vivo：设置 → 更多设置 → 输入法 → 自动填充服务")
                    }
                    manufacturer.contains("samsung") -> {
                        recommendations.add("三星设备：设置 → 常规管理 → 语言和输入 → 自动填充服务")
                    }
                }
            }
            
            // 3. 应用内未启用
            if (!isAppEnabled) {
                recommendations.add("请在 Monica 应用设置中启用自动填充功能")
            }
            
            // 4. 权限问题
            if (!hasRequiredPermissions) {
                recommendations.add("请检查应用权限设置，确保已授予必要权限")
            }
            
            // 5. 兼容性问题
            if (compatibilityIssues.isNotEmpty()) {
                recommendations.add("检测到 ${compatibilityIssues.size} 个兼容性问题，请查看详情")
                
                // 针对特定问题提供建议
                compatibilityIssues.forEach { issue ->
                    when {
                        issue.contains("小米") -> {
                            recommendations.add("小米设备：打开安全中心 → 应用管理 → Monica → 权限管理 → 允许自动填充")
                        }
                        issue.contains("华为") -> {
                            recommendations.add("华为设备：可能需要在手机管家中允许 Monica 自启动")
                        }
                        issue.contains("OPPO") || issue.contains("Realme") -> {
                            recommendations.add("OPPO/Realme：设置 → 应用管理 → Monica → 权限 → 允许自动填充")
                        }
                        issue.contains("Vivo") -> {
                            recommendations.add("Vivo：i 管家 → 应用管理 → 权限管理 → Monica → 允许自动填充")
                        }
                        issue.contains("内联建议") -> {
                            recommendations.add("设备不支持内联建议，将使用传统下拉菜单模式")
                        }
                    }
                }
            }
            
            // 6. 通用建议
            if (recommendations.isEmpty()) {
                recommendations.add("服务配置正常，如仍有问题请尝试重启应用")
                recommendations.add("可以使用故障排查工具查看详细日志")
            } else {
                recommendations.add("完成上述步骤后，请重启应用以确保设置生效")
            }
            
            AutofillLogger.d(TAG, "Generated ${recommendations.size} recommendations")
            
        } catch (e: Exception) {
            AutofillLogger.e(TAG, "Error generating recommendations", e)
            recommendations.add("无法生成建议，请联系技术支持")
        }
        
        return recommendations
    }
    
    /**
     * 快速检查服务是否可用
     * 
     * @return true 如果服务已启用且可用
     */
    fun isServiceAvailable(): Boolean {
        return checkSystemEnabled() && checkAppEnabled()
    }
    
    /**
     * 获取自动填充设置的 Intent
     * 用于跳转到系统设置页面
     */
    fun getAutofillSettingsIntent(): android.content.Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                android.content.Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
            } catch (e: Exception) {
                AutofillLogger.e(TAG, "Error creating autofill settings intent", e)
                null
            }
        } else {
            null
        }
    }
}
