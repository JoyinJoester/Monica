package takagi.ru.monica.passkey

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.utils.DeviceUtils

class PasskeyBiometricCompatibilityPolicyTest {

    @Test
    fun `should bypass biometric when hyperos and feature enabled`() {
        val actual = PasskeyBiometricCompatibilityPolicy.shouldBypassBiometricForPasskey(
            romType = DeviceUtils.ROMType.HYPER_OS,
            isBypassEnabled = true,
            hasHyperOsSystemProperty = true,
        )

        assertTrue(actual)
    }

    @Test
    fun `should not bypass biometric when hyperos and feature disabled`() {
        val actual = PasskeyBiometricCompatibilityPolicy.shouldBypassBiometricForPasskey(
            romType = DeviceUtils.ROMType.HYPER_OS,
            isBypassEnabled = false,
            hasHyperOsSystemProperty = true,
        )

        assertFalse(actual)
    }

    @Test
    fun `should not bypass biometric when non hyperos and feature enabled`() {
        val actual = PasskeyBiometricCompatibilityPolicy.shouldBypassBiometricForPasskey(
            romType = DeviceUtils.ROMType.MIUI,
            isBypassEnabled = true,
            hasHyperOsSystemProperty = true,
        )

        assertFalse(actual)
    }

    @Test
    fun `should not bypass biometric when non hyperos and feature disabled`() {
        val actual = PasskeyBiometricCompatibilityPolicy.shouldBypassBiometricForPasskey(
            romType = DeviceUtils.ROMType.OTHER,
            isBypassEnabled = false,
            hasHyperOsSystemProperty = false,
        )

        assertFalse(actual)
    }

    @Test
    fun `should not bypass biometric when hyperos property is missing`() {
        val actual = PasskeyBiometricCompatibilityPolicy.shouldBypassBiometricForPasskey(
            romType = DeviceUtils.ROMType.HYPER_OS,
            isBypassEnabled = true,
            hasHyperOsSystemProperty = false,
        )

        assertFalse(actual)
    }
}
