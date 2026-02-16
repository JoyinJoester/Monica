package takagi.ru.monica.utils

import android.os.Build
import android.util.Log

/**
 * 设备厂商检测工具
 * 用于识别不同的 Android 设备制造商和 ROM 类型
 * 针对中国主流手机品牌进行特殊适配
 */
object DeviceUtils {
    
    private const val TAG = "DeviceUtils"
    
    /**
     * 设备制造商枚举
     */
    enum class Manufacturer {
        XIAOMI,      // 小米、Redmi、POCO
        OPPO,        // OPPO、Realme、一加
        VIVO,        // vivo、iQOO
        HUAWEI,      // 华为
        HONOR,       // 荣耀
        SAMSUNG,     // 三星
        GOOGLE,      // Google Pixel
        MEIZU,       // 魅族
        LENOVO,      // 联想、摩托罗拉
        ZTE,         // 中兴
        NUBIA,       // 努比亚
        ONEPLUS,     // 一加 (独立品牌)
        REALME,      // Realme (独立品牌)
        OTHER        // 其他
    }
    
    /**
     * ROM 类型枚举
     */
    enum class ROMType {
        MIUI,           // 小米 MIUI
        HYPER_OS,       // 小米 HyperOS
        COLOR_OS,       // OPPO ColorOS
        ORIGIN_OS,      // vivo OriginOS
        FUNTOUCH_OS,    // vivo Funtouch OS
        EMUI,           // 华为 EMUI
        HARMONY_OS,     // 华为 HarmonyOS
        MAGIC_OS,       // 荣耀 MagicOS
        REALME_UI,      // Realme UI
        OXYGEN_OS,      // 一加 OxygenOS
        ONE_UI,         // 三星 One UI
        FLYME,          // 魅族 Flyme
        STOCK_ANDROID,  // 原生 Android
        OTHER
    }
    
    /**
     * 获取设备制造商
     */
    fun getManufacturer(): Manufacturer {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        
        return when {
            // 小米系
            manufacturer.contains("xiaomi") || 
            brand.contains("xiaomi") ||
            brand.contains("redmi") ||
            brand.contains("poco") -> Manufacturer.XIAOMI
            
            // OPPO系 (注意：一加和Realme也可能返回OPPO)
            manufacturer.contains("oppo") || 
            brand.contains("oppo") -> Manufacturer.OPPO
            
            // 一加 (独立识别)
            manufacturer.contains("oneplus") ||
            brand.contains("oneplus") -> Manufacturer.ONEPLUS
            
            // Realme (独立识别)
            manufacturer.contains("realme") ||
            brand.contains("realme") -> Manufacturer.REALME
            
            // vivo系
            manufacturer.contains("vivo") ||
            brand.contains("vivo") ||
            brand.contains("iqoo") -> Manufacturer.VIVO
            
            // 华为
            manufacturer.contains("huawei") ||
            brand.contains("huawei") -> Manufacturer.HUAWEI
            
            // 荣耀
            manufacturer.contains("honor") ||
            brand.contains("honor") -> Manufacturer.HONOR
            
            // 三星
            manufacturer.contains("samsung") -> Manufacturer.SAMSUNG
            
            // Google
            manufacturer.contains("google") -> Manufacturer.GOOGLE
            
            // 魅族
            manufacturer.contains("meizu") ||
            brand.contains("meizu") -> Manufacturer.MEIZU
            
            // 联想/摩托罗拉
            manufacturer.contains("lenovo") ||
            manufacturer.contains("motorola") -> Manufacturer.LENOVO
            
            // 中兴
            manufacturer.contains("zte") -> Manufacturer.ZTE
            
            // 努比亚
            manufacturer.contains("nubia") -> Manufacturer.NUBIA
            
            else -> Manufacturer.OTHER
        }
    }
    
    /**
     * 获取 ROM 类型
     */
    fun getROMType(): ROMType {
        val manufacturer = getManufacturer()
        
        // 检查系统属性来识别具体的ROM
        return when (manufacturer) {
            Manufacturer.XIAOMI -> {
                when {
                    hasSystemProperty("ro.miui.ui.version.name") -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            ROMType.HYPER_OS // Android 14+ 的小米设备使用 HyperOS
                        } else {
                            ROMType.MIUI
                        }
                    }
                    hasSystemProperty("ro.mi.os.version.name") -> ROMType.HYPER_OS
                    else -> ROMType.MIUI
                }
            }
            
            Manufacturer.OPPO, Manufacturer.ONEPLUS -> {
                when {
                    hasSystemProperty("ro.build.version.opporom") -> ROMType.COLOR_OS
                    hasSystemProperty("ro.oxygen.version") -> ROMType.OXYGEN_OS
                    else -> ROMType.COLOR_OS
                }
            }
            
            Manufacturer.REALME -> ROMType.REALME_UI
            
            Manufacturer.VIVO -> {
                when {
                    hasSystemProperty("ro.vivo.os.version") -> ROMType.ORIGIN_OS
                    else -> ROMType.FUNTOUCH_OS
                }
            }
            
            Manufacturer.HUAWEI -> {
                when {
                    hasSystemProperty("hw_sc.build.platform.version") -> ROMType.HARMONY_OS
                    hasSystemProperty("ro.build.version.emui") -> ROMType.EMUI
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> ROMType.HARMONY_OS
                    else -> ROMType.EMUI
                }
            }
            
            Manufacturer.HONOR -> ROMType.MAGIC_OS
            
            Manufacturer.SAMSUNG -> ROMType.ONE_UI
            
            Manufacturer.MEIZU -> ROMType.FLYME
            
            Manufacturer.GOOGLE -> ROMType.STOCK_ANDROID
            
            else -> ROMType.OTHER
        }
    }
    
    /**
     * 获取 ROM 版本
     */
    fun getROMVersion(): String {
        val romType = getROMType()
        
        return when (romType) {
            ROMType.MIUI -> getSystemProperty("ro.miui.ui.version.name") ?: "Unknown"
            ROMType.HYPER_OS -> getSystemProperty("ro.mi.os.version.name") ?: "Unknown"
            ROMType.COLOR_OS -> getSystemProperty("ro.build.version.opporom") ?: "Unknown"
            ROMType.ORIGIN_OS -> getSystemProperty("ro.vivo.os.version") ?: "Unknown"
            ROMType.EMUI -> getSystemProperty("ro.build.version.emui") ?: "Unknown"
            ROMType.HARMONY_OS -> getSystemProperty("hw_sc.build.platform.version") ?: "Unknown"
            ROMType.MAGIC_OS -> "MagicOS ${Build.VERSION.RELEASE}"
            ROMType.REALME_UI -> "Realme UI"
            ROMType.OXYGEN_OS -> getSystemProperty("ro.oxygen.version") ?: "Unknown"
            ROMType.ONE_UI -> "One UI"
            ROMType.FLYME -> "Flyme"
            else -> "Unknown"
        }
    }
    
    /**
     * 是否是中国厂商的 ROM
     */
    fun isChineseROM(): Boolean {
        return when (getManufacturer()) {
            Manufacturer.XIAOMI,
            Manufacturer.OPPO,
            Manufacturer.VIVO,
            Manufacturer.HUAWEI,
            Manufacturer.HONOR,
            Manufacturer.MEIZU,
            Manufacturer.LENOVO,
            Manufacturer.ZTE,
            Manufacturer.NUBIA,
            Manufacturer.ONEPLUS,
            Manufacturer.REALME -> true
            else -> false
        }
    }
    
    /**
     * 是否需要特殊权限处理
     */
    fun needsSpecialPermissions(): Boolean {
        return when (getROMType()) {
            ROMType.MIUI,
            ROMType.HYPER_OS,
            ROMType.COLOR_OS,
            ROMType.ORIGIN_OS,
            ROMType.FUNTOUCH_OS,
            ROMType.EMUI,
            ROMType.HARMONY_OS -> true
            else -> false
        }
    }
    
    /**
     * 是否需要后台保活处理
     */
    fun needsKeepAlive(): Boolean {
        return when (getROMType()) {
            ROMType.MIUI,
            ROMType.HYPER_OS,
            ROMType.COLOR_OS,
            ROMType.ORIGIN_OS -> true
            else -> false
        }
    }
    
    /**
     * 获取推荐的自动填充超时时间 (毫秒)
     */
    fun getRecommendedAutofillTimeout(): Long {
        return when (getROMType()) {
            ROMType.MIUI, 
            ROMType.HYPER_OS -> 3000L      // MIUI 响应较快，设置3秒
            
            ROMType.COLOR_OS,
            ROMType.OXYGEN_OS,
            ROMType.REALME_UI -> 4500L     // ColorOS/Realme 机型适当放宽超时
            
            ROMType.ORIGIN_OS,
            ROMType.FUNTOUCH_OS -> 2500L   // vivo 系统响应更快，2.5秒
            
            ROMType.HARMONY_OS -> 4000L    // HarmonyOS 设置4秒
            
            ROMType.EMUI -> 4000L          // EMUI 设置4秒
            
            ROMType.MAGIC_OS -> 3500L      // MagicOS 设置3.5秒
            
            ROMType.ONE_UI -> 4000L        // One UI 设置4秒
            
            else -> 5000L                  // 默认5秒
        }
    }
    
    /**
     * 获取推荐的重试次数
     */
    fun getRecommendedRetryCount(): Int {
        return when (getROMType()) {
            ROMType.MIUI,
            ROMType.HYPER_OS,
            ROMType.COLOR_OS,
            ROMType.ORIGIN_OS -> 2         // 国产ROM重试2次
            else -> 1                      // 其他系统重试1次
        }
    }
    
    /**
     * 是否支持内联建议 (Inline Suggestions)
     */
    fun supportsInlineSuggestions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false
        }
        
        // 某些国产ROM的内联建议有兼容性问题
        return when (getROMType()) {
            ROMType.MIUI,
            ROMType.HYPER_OS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S // MIUI需要Android 12+
            
            ROMType.COLOR_OS -> true       // ColorOS支持良好
            
            ROMType.ORIGIN_OS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S // vivo需要Android 12+
            
            ROMType.HARMONY_OS -> false    // HarmonyOS暂不完全支持
            
            ROMType.EMUI -> false          // EMUI暂不完全支持
            
            else -> true
        }
    }
    
    /**
     * 获取设备信息摘要
     */
    fun getDeviceSummary(): String {
        return buildString {
            appendLine("=== Device Information ===")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Detected Manufacturer: ${getManufacturer()}")
            appendLine("ROM Type: ${getROMType()}")
            appendLine("ROM Version: ${getROMVersion()}")
            appendLine("Chinese ROM: ${isChineseROM()}")
            appendLine("Needs Special Permissions: ${needsSpecialPermissions()}")
            appendLine("Needs Keep Alive: ${needsKeepAlive()}")
            appendLine("Recommended Timeout: ${getRecommendedAutofillTimeout()}ms")
            appendLine("Recommended Retry: ${getRecommendedRetryCount()}")
            appendLine("Supports Inline Suggestions: ${supportsInlineSuggestions()}")
        }
    }
    
    /**
     * 检查系统属性是否存在
     */
    private fun hasSystemProperty(key: String): Boolean {
        return getSystemProperty(key) != null
    }
    
    /**
     * 获取系统属性值
     */
    private fun getSystemProperty(key: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("getprop $key")
            val result = process.inputStream.bufferedReader().readText().trim()
            if (result.isNotBlank()) result else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get system property: $key", e)
            null
        }
    }
    
    /**
     * 打印设备信息到日志
     */
    fun logDeviceInfo() {
        Log.d(TAG, getDeviceSummary())
    }
}
