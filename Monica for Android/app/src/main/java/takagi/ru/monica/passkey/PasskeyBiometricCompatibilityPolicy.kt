package takagi.ru.monica.passkey

import takagi.ru.monica.utils.DeviceUtils

/**
 * HyperOS compatibility policy for passkey biometric verification.
 */
object PasskeyBiometricCompatibilityPolicy {

    fun shouldBypassBiometricForPasskey(
        romType: DeviceUtils.ROMType,
        isBypassEnabled: Boolean,
        hasHyperOsSystemProperty: Boolean,
    ): Boolean {
        return isBypassEnabled &&
            romType == DeviceUtils.ROMType.HYPER_OS &&
            hasHyperOsSystemProperty
    }
}
