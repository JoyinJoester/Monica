package takagi.ru.monica.utils

import android.app.Application
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28, 29, 32, 34])
class BiometricAuthenticationPolicyTest {
    @Test fun systemPromptOnlyAllowsStrongBiometricsAndKeepsMonicaPasswordButton() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val helper = BiometricAuthHelper(controller.get())
            helper.prepare(controller.get())
            val info = BiometricAuthHelper::class.java.getDeclaredField("promptInfo").apply {
                isAccessible = true
            }.get(helper) as BiometricPrompt.PromptInfo
            assertEquals(BiometricManager.Authenticators.BIOMETRIC_STRONG, info.allowedAuthenticators)
            assertEquals(0, info.allowedAuthenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            assertTrue(info.negativeButtonText.isNotBlank())
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun deviceCredentialAndInvalidCallbackTypesNeverAuthorize() {
        for (api in listOf(26, 28, 29, 30, 32, 34, 36)) {
            assertFalse(BiometricAuthenticationPolicy.acceptsResult(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL, api))
            assertFalse(BiometricAuthenticationPolicy.acceptsResult(937, api))
            assertTrue(BiometricAuthenticationPolicy.acceptsResult(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC, api))
        }
    }

    @Test fun unreportedTypesAreCompatibleOnlyWithOldBiometricOnlyPrompts() {
        assertTrue(BiometricAuthenticationPolicy.acceptsResult(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_UNKNOWN, 28))
        assertTrue(BiometricAuthenticationPolicy.acceptsResult(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_UNKNOWN, 29))
        assertFalse(BiometricAuthenticationPolicy.acceptsResult(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_UNKNOWN, 30))
        assertFalse(BiometricAuthenticationPolicy.acceptsResult(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_UNKNOWN, 36))
    }
}
