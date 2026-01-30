package takagi.ru.monica.autofill.core

import android.os.Build
import android.service.autofill.SaveInfo
import android.view.autofill.AutofillId
import androidx.annotation.RequiresApi
import takagi.ru.monica.autofill.EnhancedAutofillStructureParserV2.FieldHint
import takagi.ru.monica.autofill.EnhancedAutofillStructureParserV2.ParsedStructure
import takagi.ru.monica.utils.DeviceUtils

/**
 * SaveInfo 构建器
 * 
 * 根据设备类型和 ROM 版本构建适配的 SaveInfo，解决不同设备上自动保存功能的兼容性问题。
 * 
 * 主要功能：
 * - 设备特定的标志配置
 * - 字段 ID 收集和验证
 * - SaveInfo 有效性验证
 * 
 * @author Monica Development Team
 * @version 1.0
 */
@RequiresApi(Build.VERSION_CODES.O)
object SaveInfoBuilder {
    
    private const val TAG = "SaveInfoBuilder"
    
    /**
     * 设备信息数据类
     * 封装设备相关信息用于决策
     */
    data class DeviceInfo(
        val manufacturer: DeviceUtils.Manufacturer,
        val romType: DeviceUtils.ROMType,
        val romVersion: String,
        val androidVersion: Int,
        val supportsInlineSuggestions: Boolean
    ) {
        /**
         * 是否支持延迟保存提示
         * 某些设备在视图不可见时触发保存提示会有问题
         */
        fun supportsDelayedSavePrompt(): Boolean {
            return when (romType) {
                DeviceUtils.ROMType.MIUI,
                DeviceUtils.ROMType.HYPER_OS -> false  // 小米设备不支持延迟保存
                
                DeviceUtils.ROMType.ORIGIN_OS,
                DeviceUtils.ROMType.FUNTOUCH_OS -> false  // vivo 设备不支持延迟保存
                
                DeviceUtils.ROMType.COLOR_OS,
                DeviceUtils.ROMType.OXYGEN_OS,
                DeviceUtils.ROMType.REALME_UI -> true  // OPPO 系列支持良好
                
                DeviceUtils.ROMType.ONE_UI -> true  // 三星支持良好
                
                DeviceUtils.ROMType.STOCK_ANDROID -> true  // 原生 Android 支持
                
                DeviceUtils.ROMType.HARMONY_OS,
                DeviceUtils.ROMType.EMUI -> true  // 华为系统支持
                
                else -> androidVersion >= Build.VERSION_CODES.P  // 默认 Android 9+ 支持
            }
        }
        
        /**
         * 获取推荐的 SaveInfo 标志
         */
        fun getRecommendedSaveFlags(): Int {
            return when (romType) {
                DeviceUtils.ROMType.MIUI,
                DeviceUtils.ROMType.HYPER_OS -> {
                    // 小米设备：不使用任何标志
                    0
                }
                
                DeviceUtils.ROMType.ORIGIN_OS,
                DeviceUtils.ROMType.FUNTOUCH_OS -> {
                    // vivo 设备：不使用任何标志
                    0
                }
                
                DeviceUtils.ROMType.COLOR_OS,
                DeviceUtils.ROMType.OXYGEN_OS,
                DeviceUtils.ROMType.REALME_UI -> {
                    // OPPO 系列：使用标准标志
                    SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE
                }
                
                DeviceUtils.ROMType.ONE_UI -> {
                    // 三星：使用标准标志
                    SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE
                }
                
                DeviceUtils.ROMType.HARMONY_OS,
                DeviceUtils.ROMType.EMUI -> {
                    // 华为系统：使用标准标志
                    SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE
                }
                
                DeviceUtils.ROMType.STOCK_ANDROID -> {
                    // 原生 Android：使用标准标志
                    SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE
                }
                
                else -> {
                    // 其他设备：使用标准标志（包括原生 Android 8+）
                    SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE
                }
            }
        }
        
        /**
         * 是否需要自定义保存 UI
         */
        fun needsCustomSaveUI(): Boolean {
            // 目前所有设备都使用系统默认的保存 UI
            // 未来可以根据需要为特定设备启用自定义 UI
            return false
        }
        
        companion object {
            /**
             * 从当前设备创建 DeviceInfo 实例
             */
            fun fromDevice(): DeviceInfo {
                return DeviceInfo(
                    manufacturer = DeviceUtils.getManufacturer(),
                    romType = DeviceUtils.getROMType(),
                    romVersion = DeviceUtils.getROMVersion(),
                    androidVersion = Build.VERSION.SDK_INT,
                    supportsInlineSuggestions = DeviceUtils.supportsInlineSuggestions()
                )
            }
        }
    }
    
    /**
     * 构建 SaveInfo
     * 
     * @param parsedStructure 解析的表单结构
     * @param deviceInfo 设备信息（可选，默认使用当前设备）
     * @return SaveInfo 或 null（如果不应该保存）
     */
    fun build(
        parsedStructure: ParsedStructure,
        deviceInfo: DeviceInfo = DeviceInfo.fromDevice()
    ): SaveInfo? {
        try {
            // 收集需要保存的字段 ID
            val fieldIds = collectSaveFieldIds(parsedStructure)
            
            // 如果没有可保存的字段，返回 null
            if (fieldIds.isEmpty()) {
                AutofillLogger.w(TAG, "No saveable fields found, skipping SaveInfo creation")
                return null
            }
            
            // 确定保存类型
            val saveType = determineSaveType(parsedStructure)
            
            // 获取设备特定的标志
            val flags = getDeviceSpecificFlags(deviceInfo)
            
            // 构建 SaveInfo
            val saveInfoBuilder = SaveInfo.Builder(saveType, fieldIds)
            
            // 设置标志
            if (flags != 0) {
                saveInfoBuilder.setFlags(flags)
            }
            
            val saveInfo = saveInfoBuilder.build()
            
            // 验证 SaveInfo
            if (!validateSaveInfo(saveInfo, fieldIds)) {
                AutofillLogger.e(TAG, "SaveInfo validation failed")
                return null
            }
            
            // 记录成功创建的 SaveInfo
            AutofillLogger.i(TAG, buildString {
                append("SaveInfo created successfully:\n")
                append("  Device: ${deviceInfo.manufacturer} (${deviceInfo.romType})\n")
                append("  Save Type: ${saveTypeToString(saveType)}\n")
                append("  Flags: ${flagsToString(flags)}\n")
                append("  Field Count: ${fieldIds.size}\n")
                append("  Fields: ${fieldIds.joinToString { it.toString() }}")
            })
            
            return saveInfo
            
        } catch (e: Exception) {
            AutofillLogger.e(TAG, "Failed to build SaveInfo", e)
            return null
        }
    }
    
    /**
     * 获取设备特定的 SaveInfo 标志
     */
    private fun getDeviceSpecificFlags(deviceInfo: DeviceInfo): Int {
        val recommendedFlags = deviceInfo.getRecommendedSaveFlags()
        
        AutofillLogger.d(TAG, buildString {
            append("Device-specific flags for ${deviceInfo.manufacturer} (${deviceInfo.romType}):\n")
            append("  Recommended: ${flagsToString(recommendedFlags)}\n")
            append("  Supports Delayed Save: ${deviceInfo.supportsDelayedSavePrompt()}")
        })
        
        return recommendedFlags
    }
    
    /**
     * 收集需要保存的字段 ID
     * 
     * 根据字段类型收集用户名和密码字段的 AutofillId
     */
    private fun collectSaveFieldIds(parsedStructure: ParsedStructure): Array<AutofillId> {
        val fieldIds = mutableListOf<AutofillId>()
        
        // 查找用户名字段
        val usernameField = parsedStructure.items.firstOrNull { item ->
            item.hint == FieldHint.USERNAME || item.hint == FieldHint.EMAIL_ADDRESS
        }
        
        // 查找密码字段
        val passwordField = parsedStructure.items.firstOrNull { item ->
            item.hint == FieldHint.PASSWORD || item.hint == FieldHint.NEW_PASSWORD
        }
        
        // 添加找到的字段
        usernameField?.let { fieldIds.add(it.id) }
        passwordField?.let { fieldIds.add(it.id) }
        
        AutofillLogger.d(TAG, buildString {
            append("Collected save field IDs:\n")
            append("  Username field: ${if (usernameField != null) "found (${usernameField.hint})" else "not found"}\n")
            append("  Password field: ${if (passwordField != null) "found (${passwordField.hint})" else "not found"}\n")
            append("  Total fields: ${fieldIds.size}")
        })
        
        return fieldIds.toTypedArray()
    }
    
    /**
     * 确定保存类型
     */
    private fun determineSaveType(parsedStructure: ParsedStructure): Int {
        val hasUsername = parsedStructure.items.any { 
            it.hint == FieldHint.USERNAME || it.hint == FieldHint.EMAIL_ADDRESS 
        }
        val hasPassword = parsedStructure.items.any { 
            it.hint == FieldHint.PASSWORD || it.hint == FieldHint.NEW_PASSWORD 
        }
        
        return when {
            hasUsername && hasPassword -> SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD
            hasUsername -> SaveInfo.SAVE_DATA_TYPE_USERNAME
            hasPassword -> SaveInfo.SAVE_DATA_TYPE_PASSWORD
            else -> SaveInfo.SAVE_DATA_TYPE_GENERIC
        }
    }
    
    /**
     * 验证 SaveInfo 配置是否有效
     */
    private fun validateSaveInfo(saveInfo: SaveInfo?, fieldIds: Array<AutofillId>): Boolean {
        if (saveInfo == null) {
            AutofillLogger.w(TAG, "SaveInfo is null")
            return false
        }
        
        if (fieldIds.isEmpty()) {
            AutofillLogger.w(TAG, "Field IDs array is empty")
            return false
        }
        
        // 验证字段 ID 不为 null
        if (fieldIds.any { it == null }) {
            AutofillLogger.w(TAG, "Field IDs array contains null values")
            return false
        }
        
        AutofillLogger.d(TAG, "SaveInfo validation passed")
        return true
    }
    
    /**
     * 将保存类型转换为字符串（用于日志）
     */
    private fun saveTypeToString(saveType: Int): String {
        val types = mutableListOf<String>()
        
        if ((saveType and SaveInfo.SAVE_DATA_TYPE_USERNAME) != 0) {
            types.add("USERNAME")
        }
        if ((saveType and SaveInfo.SAVE_DATA_TYPE_PASSWORD) != 0) {
            types.add("PASSWORD")
        }
        if ((saveType and SaveInfo.SAVE_DATA_TYPE_ADDRESS) != 0) {
            types.add("ADDRESS")
        }
        if ((saveType and SaveInfo.SAVE_DATA_TYPE_CREDIT_CARD) != 0) {
            types.add("CREDIT_CARD")
        }
        if ((saveType and SaveInfo.SAVE_DATA_TYPE_GENERIC) != 0) {
            types.add("GENERIC")
        }
        
        return if (types.isEmpty()) "NONE" else types.joinToString(" | ")
    }
    
    /**
     * 将标志转换为字符串（用于日志）
     */
    private fun flagsToString(flags: Int): String {
        if (flags == 0) {
            return "NONE"
        }
        
        val flagList = mutableListOf<String>()
        
        if ((flags and SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE) != 0) {
            flagList.add("SAVE_ON_ALL_VIEWS_INVISIBLE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if ((flags and SaveInfo.FLAG_DONT_SAVE_ON_FINISH) != 0) {
                flagList.add("DONT_SAVE_ON_FINISH")
            }
        }
        
        return if (flagList.isEmpty()) "UNKNOWN($flags)" else flagList.joinToString(" | ")
    }
}
