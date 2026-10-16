package takagi.ru.monica.utils

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt

/** Device credentials never authorize a Monica biometric operation. */
internal object BiometricAuthenticationPolicy {
    const val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun acceptsResult(authenticationType: Int, sdkInt: Int): Boolean =
        authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC ||
            // Android 9/10 may not report the type. The prompt still only allows strong biometrics.
            (sdkInt <= 29 && authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_UNKNOWN)
}
