package takagi.ru.monica.utils

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import takagi.ru.monica.R

/**
 * 生物识别认证帮助类
 * 支持指纹识别、面部识别等,兼容各种第三方系统(HyperOS、OriginOS等)
 * 
 * 特别优化:
 * - vivo 设备屏下指纹支持
 * - 自动适配不同安全级别
 */
class BiometricAuthHelper(
    private val context: Context
) {
    companion object {
        private const val TAG = "BiometricAuthHelper"
    }
    
    // vivo 设备优化
    private val vivoHelper = VivoFingerprintHelper(context)
    
    /**
     * 检查设备是否支持生物识别
     * 使用 BIOMETRIC_WEAK 级别以支持更多设备(包括仅指纹识别的设备)
     */
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val strongResult = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        val result = if (strongResult == BiometricManager.BIOMETRIC_SUCCESS) {
            BiometricManager.BIOMETRIC_SUCCESS
        } else {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        }
        
        // 记录 vivo 设备信息
        if (VivoFingerprintHelper.isVivoDevice()) {
            Log.d(TAG, "Vivo device detected: ${VivoFingerprintHelper.getDeviceInfo()}")
            Log.d(TAG, "Has under-display fingerprint: ${vivoHelper.hasUnderDisplayFingerprint()}")
        }
        
        return when (result) {
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

    fun isStrongBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isWeakBiometricOnly(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val strongAvailable = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
        if (strongAvailable) {
            return false
        }
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    
    /**
     * 检查设备是否已注册生物识别信息
     * 使用 BIOMETRIC_WEAK 级别以支持更多设备
     */
    fun isBiometricEnrolled(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val strongResult = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (strongResult == BiometricManager.BIOMETRIC_SUCCESS) {
            return true
        }
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == 
               BiometricManager.BIOMETRIC_SUCCESS
    }
    
    /**
     * 获取生物识别可用状态描述
     */
    fun getBiometricStatusMessage(): String {
        val biometricManager = BiometricManager.from(context)
        val strongResult = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        val result = if (strongResult == BiometricManager.BIOMETRIC_SUCCESS) {
            BiometricManager.BIOMETRIC_SUCCESS
        } else {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        }
        val baseMessage = when (result) {
            BiometricManager.BIOMETRIC_SUCCESS -> 
                context.getString(R.string.biometric_available)
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> 
                context.getString(R.string.biometric_no_hardware)
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> 
                context.getString(R.string.biometric_hw_unavailable)
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> 
                context.getString(R.string.biometric_none_enrolled)
            else -> 
                context.getString(R.string.biometric_not_available)
        }
        
        // 为 vivo 设备添加额外提示
        if (VivoFingerprintHelper.isVivoDevice() && vivoHelper.hasUnderDisplayFingerprint()) {
            return "$baseMessage (屏下指纹)"
        }
        
        return baseMessage
    }
    
    /**
     * 获取优化建议 (vivo 设备特有)
     */
    fun getOptimizationTips(): List<String> {
        return if (VivoFingerprintHelper.isVivoDevice()) {
            vivoHelper.getOptimizationTips()
        } else {
            emptyList()
        }
    }
    
    /**
     * 获取调试信息
     */
    fun getDebugInfo(): String {
        return if (VivoFingerprintHelper.isVivoDevice()) {
            vivoHelper.getDebugInfo()
        } else {
            "非 vivo 设备"
        }
    }
    
    /**
     * 显示生物识别认证对话框
     * 
     * @param activity FragmentActivity 实例
     * @param title 对话框标题
     * @param subtitle 对话框副标题
     * @param description 对话框描述
     * @param negativeButtonText 取消按钮文字
     * @param onSuccess 认证成功回调
     * @param onError 认证失败回调
     * @param onCancel 用户取消回调
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = context.getString(R.string.biometric_login_title),
        subtitle: String = context.getString(R.string.biometric_login_subtitle),
        description: String = context.getString(R.string.biometric_login_description),
        negativeButtonText: String = context.getString(R.string.use_password),
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errorMessage: String) -> Unit,
        onCancel: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED -> {
                            onCancel()
                        }
                        else -> {
                            onError(errorCode, errString.toString())
                        }
                    }
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // 认证失败但可以继续尝试,不需要特殊处理
                }
            }
        )
        
        val allowedAuthenticators = getAllowedAuthenticators()
        val promptBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
            .setAllowedAuthenticators(allowedAuthenticators)
            .setConfirmationRequired(false) // 不需要额外确认,提高用户体验

        // 使用设备凭据时不能设置负按钮
        if (allowedAuthenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL == 0) {
            promptBuilder.setNegativeButtonText(negativeButtonText)
        }

        val promptInfo = promptBuilder.build()
        
        biometricPrompt.authenticate(promptInfo)
    }

    private fun getAllowedAuthenticators(): Int {
        val biometricManager = BiometricManager.from(context)
        val strongResult = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        val biometric = if (strongResult == BiometricManager.BIOMETRIC_SUCCESS) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
        return biometric or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
