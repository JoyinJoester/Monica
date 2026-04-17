package takagi.ru.monica.utils

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 生物识别辅助类
 * 
 * 封装 Android BiometricPrompt API，仅提供指纹识别功能。
 * 
 * ## 功能特性
 * - ✅ 检测设备生物识别支持情况
 * - ✅ 检查是否已注册生物识别
 * - ✅ 显示生物识别认证提示
 * - ✅ 处理认证成功、失败、错误回调
 * 
 * ## 安全特性
 * - 🔐 使用系统级生物识别 API
 * - 🔐 不存储生物识别数据
 * - 🔐 失败自动限制
 * - 🔐 支持降级到密码验证
 * 
 * ## 使用示例
 * ```kotlin
 * val helper = BiometricHelper(context)
 * 
 * // 检查支持
 * if (helper.isBiometricAvailable()) {
 *     // 显示认证
 *     helper.authenticate(
 *         activity = activity,
 *         title = "验证身份",
 *         subtitle = "使用生物识别快速填充",
 *         onSuccess = { /* 认证成功 */ },
 *         onError = { error -> /* 处理错误 */ },
 *         onFailed = { /* 认证失败 */ }
 *     )
 * }
 * ```
 * 
 * @param context 应用上下文
 */
class BiometricHelper(private val context: Context) {

    private val biometricManager = BiometricManager.from(context)

    /**
     * 检查设备是否支持生物识别
     * 
     * @return true 如果设备支持且已注册指纹
     */
    fun isBiometricAvailable(): Boolean {
        if (!hasFingerprintHardware() || !hasEnrolledFingerprint()) {
            return false
        }
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> false
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> false
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> false
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> false
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> false
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> false
            else -> false
        }
    }

    /**
     * 检查是否已注册生物识别
     * 
     * @return true 如果用户已注册指纹
     */
    fun hasBiometricEnrolled(): Boolean {
        return hasEnrolledFingerprint()
    }

    /**
     * 获取生物识别不可用的原因
     * 
     * @return 描述不可用原因的字符串
     */
    fun getBiometricStatusMessage(): String {
        if (!hasFingerprintHardware()) {
            return "设备不支持指纹识别"
        }
        if (!hasEnrolledFingerprint()) {
            return "未注册指纹，请在系统设置中添加指纹"
        }
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> 
                "指纹识别可用"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> 
                "设备不支持指纹识别"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> 
                "指纹硬件当前不可用"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> 
                "未注册指纹，请在系统设置中添加指纹"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> 
                "需要安全更新"
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> 
                "不支持的配置"
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> 
                "未知状态"
            else -> 
                "生物识别不可用"
        }
    }

    /**
     * 显示生物识别认证提示
     * 
     * @param activity FragmentActivity 实例（用于显示提示对话框）
     * @param title 对话框标题
     * @param subtitle 对话框副标题（可选）
     * @param description 对话框描述（可选）
     * @param negativeButtonText 取消按钮文本（默认"取消"）
     * @param onSuccess 认证成功回调
     * @param onError 认证错误回调（参数：错误消息）
     * @param onFailed 认证失败回调（指纹不匹配等）
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "验证身份",
        subtitle: String? = "使用生物识别快速填充",
        description: String? = null,
        negativeButtonText: String = "取消",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit
    ) {
        // 检查是否可用
        if (!isBiometricAvailable()) {
            onError(getBiometricStatusMessage())
            return
        }

        // 创建认证提示
        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply {
                if (subtitle != null) setSubtitle(subtitle)
                if (description != null) setDescription(description)
            }
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        AppLauncherIconManager.applyBiometricPromptBranding(context, promptInfoBuilder)
        val promptInfo = promptInfoBuilder.build()

        // 创建认证回调
        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                
                // 用户取消不算错误
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onFailed()
                } else {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // 这里不立即回调失败，因为用户可以多次尝试
                // 只有完全失败时才会触发 onAuthenticationError
            }
        }

        // 显示提示
        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * 检查设备 Android 版本是否支持生物识别
     * 
     * @return true 如果 Android 版本 >= 6.0 (API 23)
     */
    fun isVersionSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }

    private fun hasFingerprintHardware(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
    }

    @Suppress("DEPRECATION")
    private fun hasEnrolledFingerprint(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }
        val fingerprintManager = context.getSystemService(FingerprintManager::class.java) ?: return false
        return fingerprintManager.isHardwareDetected && fingerprintManager.hasEnrolledFingerprints()
    }

    companion object {
        /**
         * 最低支持的 Android 版本
         */
        const val MIN_API_LEVEL = Build.VERSION_CODES.M // Android 6.0

        /**
         * 推荐的 Android 版本（BiometricPrompt 在 Android 9.0 引入）
         */
        const val RECOMMENDED_API_LEVEL = Build.VERSION_CODES.P // Android 9.0
    }
}
