package takagi.ru.monica.utils

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Vivo 设备指纹优化帮助类
 * 
 * 基于 vivo 官方 FingerprintInsets SDK 的简化实现
 * 用于优化 vivo 设备上的指纹识别体验
 * 
 * 注意: AndroidX Biometric API 已经自动处理了屏下指纹,
 * 这个类主要用于提供额外的设备信息和优化体验
 */
class VivoFingerprintHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "VivoFingerprintHelper"
        
        /**
         * 检查是否为 vivo 设备
         */
        fun isVivoDevice(): Boolean {
            return Build.MANUFACTURER.equals("vivo", ignoreCase = true)
        }
        
        /**
         * 检查是否可能支持屏下指纹
         * (Android 7.0+ 的 vivo 设备)
         */
        fun mayHaveUnderDisplayFingerprint(): Boolean {
            return isVivoDevice() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        }
        
        /**
         * 获取设备型号信息
         */
        fun getDeviceInfo(): String {
            return "Model: ${Build.MODEL}, Device: ${Build.DEVICE}, Product: ${Build.PRODUCT}"
        }
    }
    
    /**
     * 检查当前设备是否支持屏下指纹
     */
    fun hasUnderDisplayFingerprint(): Boolean {
        if (!isVivoDevice()) {
            return false
        }
        
        // Android 7.0 以下不支持屏下指纹
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }
        
        // 检查已知的屏下指纹机型
        return isKnownUnderDisplayFingerprintDevice()
    }
    
    /**
     * 检查是否为已知的屏下指纹设备
     */
    private fun isKnownUnderDisplayFingerprintDevice(): Boolean {
        val device = Build.DEVICE.uppercase()
        val model = Build.MODEL.uppercase()
        
        // vivo X20 Plus UD
        if (device.contains("PD1721") || device.contains("PD1710")) {
            log("检测到 vivo X20 Plus UD")
            return true
        }
        
        // vivo X21 UD
        if (device.contains("PD1728") || device.contains("1728") || device.contains("1725")) {
            log("检测到 vivo X21 UD")
            return true
        }
        
        // vivo NEX 系列 (大部分支持屏下指纹)
        if (model.contains("NEX") || device.contains("PD1805") || device.contains("PD1821")) {
            log("检测到 vivo NEX 系列")
            return true
        }
        
        // vivo X23 系列
        if (device.contains("PD1832")) {
            log("检测到 vivo X23")
            return true
        }
        
        // vivo X27 系列
        if (device.contains("PD1901") || device.contains("PD1913")) {
            log("检测到 vivo X27")
            return true
        }
        
        // vivo iQOO 系列
        if (model.contains("IQOO") || device.contains("PD1824")) {
            log("检测到 vivo iQOO")
            return true
        }
        
        // vivo S1/S1 Pro
        if (device.contains("PD1913") || device.contains("PD1914")) {
            log("检测到 vivo S1 系列")
            return true
        }
        
        // 如果包含常见的屏下指纹标识
        if (device.contains("UD") || model.contains("UD")) {
            log("检测到 UD 标识,可能支持屏下指纹")
            return true
        }
        
        log("未识别为已知屏下指纹设备: $device / $model")
        return false
    }
    
    /**
     * 获取优化建议
     * 返回针对当前设备的指纹识别优化建议
     */
    fun getOptimizationTips(): List<String> {
        val tips = mutableListOf<String>()
        
        if (!isVivoDevice()) {
            return tips
        }
        
        if (hasUnderDisplayFingerprint()) {
            tips.add("✓ 您的设备支持屏下指纹识别")
            tips.add("提示: 请确保屏幕清洁,以获得最佳识别效果")
            tips.add("提示: 在强光下可能需要调整屏幕亮度")
        } else {
            tips.add("您的设备支持传统指纹识别")
            tips.add("提示: 请确保指纹传感器清洁")
        }
        
        return tips
    }
    
    /**
     * 记录日志
     */
    private fun log(message: String) {
        Log.d(TAG, message)
    }
    
    /**
     * 获取设备详细信息(用于调试)
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== Vivo 设备信息 ===")
            appendLine("制造商: ${Build.MANUFACTURER}")
            appendLine("型号: ${Build.MODEL}")
            appendLine("设备: ${Build.DEVICE}")
            appendLine("产品: ${Build.PRODUCT}")
            appendLine("Android 版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("是否为 vivo 设备: ${isVivoDevice()}")
            appendLine("可能支持屏下指纹: ${mayHaveUnderDisplayFingerprint()}")
            appendLine("检测到屏下指纹: ${hasUnderDisplayFingerprint()}")
            appendLine("=====================")
        }
    }
}
