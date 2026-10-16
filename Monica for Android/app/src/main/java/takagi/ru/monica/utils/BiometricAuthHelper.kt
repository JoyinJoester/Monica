package takagi.ru.monica.utils

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import takagi.ru.monica.R

/**
 * Fast path for the system identity prompt used by Monica's unlock surfaces.
 *
 * The prompt itself remains fully owned by Android/Google's biometric stack.
 * This helper only removes application-side work from the tap-to-prompt path:
 * - a single BiometricManager instance replaces legacy FingerprintManager probes;
 * - the main executor, BiometricPrompt and PromptInfo are reused while the Activity lives;
 * - Android 11+ may fall back to the device PIN/pattern/password from the same native prompt.
 */
class BiometricAuthHelper(
    context: Context
) {
    companion object {
        private const val TAG = "BiometricAuthHelper"
    }

    private data class PromptKey(
        val title: String,
        val subtitle: String?,
        val description: String?,
        val negativeButtonText: String
    )

    private data class AuthenticationCallbacks(
        val onSuccess: () -> Unit,
        val onError: (errorCode: Int, errorMessage: String) -> Unit,
        val onCancel: () -> Unit,
        val onNegativeButton: (() -> Unit)?
    )

    private val appContext = context.applicationContext
    private val strings = AppLocaleStringResolver(appContext)
    private val biometricManager = BiometricManager.from(appContext)
    private val executor = ContextCompat.getMainExecutor(appContext)
    private val vivoHelper by lazy(LazyThreadSafetyMode.NONE) { VivoFingerprintHelper(appContext) }

    private var promptActivity: FragmentActivity? = null
    private var prompt: BiometricPrompt? = null
    private var promptInfoKey: PromptKey? = null
    private var promptInfo: BiometricPrompt.PromptInfo? = null
    private var callbacks: AuthenticationCallbacks? = null

    /** Strong biometric availability only; preserves the existing settings semantics. */
    fun isBiometricAvailable(): Boolean {
        val result = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (result == BiometricManager.BIOMETRIC_SUCCESS && VivoFingerprintHelper.isVivoDevice()) {
            Log.d(TAG, "Vivo device detected: ${VivoFingerprintHelper.getDeviceInfo()}")
            Log.d(TAG, "Has under-display fingerprint: ${vivoHelper.hasUnderDisplayFingerprint()}")
        }
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isStrongBiometricAvailable(): Boolean = isBiometricAvailable()

    fun isWeakBiometricOnly(): Boolean {
        val weak = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        val strong = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return weak == BiometricManager.BIOMETRIC_SUCCESS && strong != BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isBiometricEnrolled(): Boolean = isBiometricAvailable()

    /**
     * Whether the native authentication sheet can be displayed using Monica's policy.
     * Android 11+ permits either a strong biometric or device credential.
     */
    fun isSystemAuthenticationAvailable(): Boolean {
        return biometricManager.canAuthenticate(allowedAuthenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun getBiometricStatusMessage(): String {
        val result = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        val baseMessage = when (result) {
            BiometricManager.BIOMETRIC_SUCCESS -> strings.get(R.string.biometric_available)
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> strings.get(R.string.biometric_no_hardware)
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> strings.get(R.string.biometric_hw_unavailable)
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> strings.get(R.string.biometric_none_enrolled)
            else -> strings.get(R.string.biometric_not_available)
        }

        return if (
            result == BiometricManager.BIOMETRIC_SUCCESS &&
            VivoFingerprintHelper.isVivoDevice() &&
            vivoHelper.hasUnderDisplayFingerprint()
        ) {
            strings.get(R.string.legacy_ui_biometric_under_display_status, baseMessage)
        } else {
            baseMessage
        }
    }

    fun getOptimizationTips(): List<String> {
        return if (VivoFingerprintHelper.isVivoDevice()) {
            vivoHelper.getOptimizationTips()
        } else {
            emptyList()
        }
    }

    fun getDebugInfo(): String {
        return if (VivoFingerprintHelper.isVivoDevice()) {
            vivoHelper.getDebugInfo()
        } else {
            "非 vivo 设备"
        }
    }

    /**
     * Prepares the reusable AndroidX prompt object without showing UI.
     * Safe to call from an unlock surface as soon as its Activity is ready.
     */
    fun prepare(activity: FragmentActivity) {
        promptFor(activity)
        promptInfoFor(
            title = strings.get(R.string.biometric_login_title),
            subtitle = strings.get(R.string.biometric_login_subtitle),
            description = strings.get(R.string.biometric_login_description),
            negativeButtonText = strings.get(R.string.use_password)
        )
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = strings.get(R.string.biometric_login_title),
        subtitle: String? = strings.get(R.string.biometric_login_subtitle),
        description: String? = strings.get(R.string.biometric_login_description),
        negativeButtonText: String = strings.get(R.string.use_password),
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errorMessage: String) -> Unit,
        onCancel: () -> Unit,
        onNegativeButton: (() -> Unit)? = null,
    ) {
        callbacks = AuthenticationCallbacks(onSuccess, onError, onCancel, onNegativeButton)
        promptFor(activity).authenticate(
            promptInfoFor(title, subtitle, description, negativeButtonText)
        )
    }

    /** Cancel a request whose owning screen has gone away, including its callbacks. */
    fun cancelAuthentication() {
        callbacks = null
        prompt?.cancelAuthentication()
    }

    private fun promptFor(activity: FragmentActivity): BiometricPrompt {
        val cached = prompt
        if (cached != null && promptActivity === activity) {
            return cached
        }

        return BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    callbacks?.onSuccess?.invoke()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    val current = callbacks ?: return
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> (current.onNegativeButton ?: current.onCancel)()
                        BiometricPrompt.ERROR_USER_CANCELED -> current.onCancel()
                        else -> current.onError(errorCode, errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Keep the native prompt open; Android owns retry/lockout policy.
                }
            }
        ).also {
            promptActivity = activity
            prompt = it
        }
    }

    private fun promptInfoFor(
        title: String,
        subtitle: String?,
        description: String?,
        negativeButtonText: String
    ): BiometricPrompt.PromptInfo {
        val key = PromptKey(title, subtitle, description, negativeButtonText)
        if (promptInfoKey == key) {
            promptInfo?.let { return it }
        }

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(allowedAuthenticators())
            .setConfirmationRequired(false)
            .apply {
                if (subtitle != null) setSubtitle(subtitle)
                if (description != null) setDescription(description)
            }

        AppLauncherIconManager.applyBiometricPromptBranding(appContext, builder)

        // DEVICE_CREDENTIAL replaces the negative button in the system prompt.
        // Android 10 and below keep the existing app-password fallback button.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            builder.setNegativeButtonText(negativeButtonText)
        }

        return builder.build().also {
            promptInfoKey = key
            promptInfo = it
        }
    }

    private fun allowedAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
    }
}
