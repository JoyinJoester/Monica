package takagi.ru.monica.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import takagi.ru.monica.utils.DeviceUtils.Manufacturer
import takagi.ru.monica.utils.DeviceUtils.ROMType

/**
 * 权限引导工具
 * 为不同品牌的手机提供专门的权限设置页面跳转
 * 解决国产ROM的各种权限限制问题
 */
object PermissionGuide {
    
    private const val TAG = "PermissionGuide"
    
    /**
     * 打开自启动设置页面
     * 对于国产ROM非常重要，确保应用可以在后台运行
     */
    fun openAutoStartSettings(context: Context): Boolean {
        val romType = DeviceUtils.getROMType()
        
        return try {
            val intent = when (romType) {
                // 小米 MIUI/HyperOS
                ROMType.MIUI, ROMType.HYPER_OS -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    }
                }
                
                // OPPO ColorOS
                ROMType.COLOR_OS -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    }
                }
                
                // vivo OriginOS/Funtouch OS
                ROMType.ORIGIN_OS, ROMType.FUNTOUCH_OS -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    }
                }
                
                // 华为 EMUI/HarmonyOS
                ROMType.EMUI, ROMType.HARMONY_OS -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    }
                }
                
                // 荣耀 MagicOS
                ROMType.MAGIC_OS -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.hihonor.systemmanager",
                            "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    }
                }
                
                // 三星 One UI
                ROMType.ONE_UI -> {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                }
                
                // 一加 OxygenOS
                ROMType.OXYGEN_OS -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.oneplus.security",
                            "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                        )
                    }
                }
                
                // Realme UI
                ROMType.REALME_UI -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    }
                }
                
                else -> {
                    // 默认跳转到应用详情页
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                }
            }
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open auto start settings for ${romType}", e)
            // 降级到应用详情页
            openAppDetailsSettings(context)
        }
    }
    
    /**
     * 打开电池优化设置
     * 关闭电池优化可以让应用在后台更好地运行
     */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            }
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open battery optimization settings", e)
            false
        }
    }
    
    /**
     * 打开后台运行设置
     */
    fun openBackgroundSettings(context: Context): Boolean {
        val romType = DeviceUtils.getROMType()
        
        return try {
            val intent = when (romType) {
                // 小米
                ROMType.MIUI, ROMType.HYPER_OS -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.permissions.PermissionsEditorActivity"
                        )
                        putExtra("extra_pkgname", context.packageName)
                    }
                }
                
                // OPPO
                ROMType.COLOR_OS, ROMType.REALME_UI -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.PermissionManagerActivity"
                        )
                    }
                }
                
                // vivo
                ROMType.ORIGIN_OS, ROMType.FUNTOUCH_OS -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.PurviewTabActivity"
                        )
                    }
                }
                
                // 华为/荣耀
                ROMType.EMUI, ROMType.HARMONY_OS, ROMType.MAGIC_OS -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.permissionmanager.ui.MainActivity"
                        )
                    }
                }
                
                else -> {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                }
            }
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open background settings for ${romType}", e)
            openAppDetailsSettings(context)
        }
    }
    
    /**
     * 打开悬浮窗权限设置
     * 某些ROM需要悬浮窗权限才能显示自动填充弹窗
     */
    fun openFloatingWindowSettings(context: Context): Boolean {
        val romType = DeviceUtils.getROMType()
        
        return try {
            val intent = when (romType) {
                // 小米
                ROMType.MIUI, ROMType.HYPER_OS -> {
                    Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                        setClassName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.permissions.PermissionsEditorActivity"
                        )
                        putExtra("extra_pkgname", context.packageName)
                    }
                }
                
                // OPPO
                ROMType.COLOR_OS, ROMType.REALME_UI -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.floatwindow.FloatWindowListActivity"
                        )
                    }
                }
                
                // vivo
                ROMType.ORIGIN_OS, ROMType.FUNTOUCH_OS -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.PurviewTabActivity"
                        )
                        putExtra("packagename", context.packageName)
                        putExtra("tabId", "1")
                    }
                }
                
                // 华为/荣耀
                ROMType.EMUI, ROMType.HARMONY_OS, ROMType.MAGIC_OS -> {
                    Intent().apply {
                        component = ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.addviewmonitor.AddViewMonitorActivity"
                        )
                    }
                }
                
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    }
                }
            }
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open floating window settings for ${romType}", e)
            openAppDetailsSettings(context)
        }
    }
    
    /**
     * 打开通知权限设置
     */
    fun openNotificationSettings(context: Context): Boolean {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            }
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open notification settings", e)
            false
        }
    }
    
    /**
     * 打开应用详情设置页面
     */
    fun openAppDetailsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app details settings", e)
            false
        }
    }
    
    /**
     * 获取品牌特定的权限设置指南URL
     */
    fun getPermissionGuideUrl(manufacturer: Manufacturer): String {
        return when (manufacturer) {
            Manufacturer.XIAOMI -> 
                "https://www.miui.com/thread-25788493-1-1.html"
            
            Manufacturer.OPPO, Manufacturer.REALME -> 
                "https://www.oppo.com/cn/support/"
            
            Manufacturer.VIVO -> 
                "https://www.vivo.com.cn/support/"
            
            Manufacturer.HUAWEI -> 
                "https://consumer.huawei.com/cn/support/"
            
            Manufacturer.HONOR -> 
                "https://www.hihonor.com/cn/support/"
            
            Manufacturer.ONEPLUS -> 
                "https://www.oneplus.com/cn/support"
            
            else -> 
                "https://www.android.com/intl/zh-CN_cn/"
        }
    }
    
    /**
     * 获取完整的权限设置指引
     */
    fun getPermissionSetupGuide(context: Context): PermissionSetupGuide {
        val manufacturer = DeviceUtils.getManufacturer()
        val romType = DeviceUtils.getROMType()
        
        return PermissionSetupGuide(
            manufacturer = manufacturer,
            romType = romType,
            steps = getSetupSteps(romType),
            guideUrl = getPermissionGuideUrl(manufacturer),
            criticalPermissions = getCriticalPermissions(romType)
        )
    }
    
    /**
     * 获取设置步骤
     */
    private fun getSetupSteps(romType: ROMType): List<String> {
        return when (romType) {
            ROMType.MIUI, ROMType.HYPER_OS -> listOf(
                "1. 打开\"手机管家\" → \"应用管理\" → \"Monica\"",
                "2. 开启\"自启动\"权限",
                "3. 开启\"后台弹出界面\"权限",
                "4. 在\"省电策略\"中选择\"无限制\"",
                "5. 关闭\"MIUI优化\"（可选，提升兼容性）"
            )
            
            ROMType.COLOR_OS, ROMType.REALME_UI -> listOf(
                "1. 打开\"手机管家\" → \"应用管理\" → \"Monica\"",
                "2. 开启\"自启动\"权限",
                "3. 开启\"关联启动\"权限",
                "4. 在\"电池优化\"中选择\"不优化\"",
                "5. 允许\"后台运行\"和\"悬浮窗\"权限"
            )
            
            ROMType.ORIGIN_OS, ROMType.FUNTOUCH_OS -> listOf(
                "1. 打开\"i管家\" → \"应用管理\" → \"Monica\"",
                "2. 开启\"自启动\"权限",
                "3. 在\"后台高耗电\"中选择\"允许\"",
                "4. 在\"电池优化\"中选择\"不限制\"",
                "5. 允许\"悬浮窗\"和\"后台弹出界面\"权限"
            )
            
            ROMType.EMUI, ROMType.HARMONY_OS -> listOf(
                "1. 打开\"手机管家\" → \"应用启动管理\" → \"Monica\"",
                "2. 关闭\"自动管理\"，手动设置：",
                "3. 开启\"自动启动\"",
                "4. 开启\"关联启动\"",
                "5. 开启\"后台活动\"",
                "6. 在\"电池\"设置中选择\"不允许\"后台活动限制"
            )
            
            ROMType.MAGIC_OS -> listOf(
                "1. 打开\"手机管家\" → \"应用启动管理\" → \"Monica\"",
                "2. 设置为\"手动管理\"",
                "3. 开启\"自动启动\"",
                "4. 开启\"关联启动\"",
                "5. 开启\"后台运行\""
            )
            
            ROMType.OXYGEN_OS -> listOf(
                "1. 打开\"设置\" → \"应用\" → \"Monica\"",
                "2. 在\"电池优化\"中选择\"不优化\"",
                "3. 允许\"后台运行\"",
                "4. 在\"应用启动\"中允许自启动"
            )
            
            else -> listOf(
                "1. 打开\"设置\" → \"应用\" → \"Monica\"",
                "2. 允许所有必要权限",
                "3. 在\"电池\"设置中关闭电池优化",
                "4. 允许后台运行"
            )
        }
    }
    
    /**
     * 获取关键权限列表
     */
    private fun getCriticalPermissions(romType: ROMType): List<String> {
        val basePermissions = listOf(
            "自动填充服务",
            "无障碍服务（可选）"
        )
        
        val additionalPermissions = when (romType) {
            ROMType.MIUI, ROMType.HYPER_OS -> listOf(
                "自启动",
                "后台弹出界面",
                "显示悬浮窗"
            )
            
            ROMType.COLOR_OS, ROMType.REALME_UI -> listOf(
                "自启动",
                "关联启动",
                "后台运行",
                "悬浮窗"
            )
            
            ROMType.ORIGIN_OS, ROMType.FUNTOUCH_OS -> listOf(
                "自启动",
                "后台高耗电",
                "悬浮窗",
                "后台弹出界面"
            )
            
            ROMType.EMUI, ROMType.HARMONY_OS, ROMType.MAGIC_OS -> listOf(
                "自动启动",
                "关联启动",
                "后台活动"
            )
            
            else -> emptyList()
        }
        
        return basePermissions + additionalPermissions
    }
    
    /**
     * 权限设置指南数据类
     */
    data class PermissionSetupGuide(
        val manufacturer: Manufacturer,
        val romType: ROMType,
        val steps: List<String>,
        val guideUrl: String,
        val criticalPermissions: List<String>
    )
}
