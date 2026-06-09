package takagi.ru.monica.ui.effects.blur

import takagi.ru.monica.data.MonicaBlurMode

data class MonicaBlurCapabilities(
    val supportsRealtimeBlur: Boolean,
    val supportsBitmapBlur: Boolean = true,
    val supportsGlassApproximation: Boolean = true
)

object MonicaBlurGate {
    fun capabilities(): MonicaBlurCapabilities = MonicaBlurCapabilities(
        supportsRealtimeBlur = MonicaBlurDefaults.supportsRealtimeBlur()
    )

    fun canUsePlusBlur(
        isPlusActivated: Boolean,
        plusBlurEnabled: Boolean,
        isSensitiveScreen: Boolean,
        reduceOnBatterySaver: Boolean,
        isBatterySaver: Boolean
    ): Boolean {
        if (!isPlusActivated || !plusBlurEnabled) return false
        if (isSensitiveScreen) return false
        if (reduceOnBatterySaver && isBatterySaver) return false
        return true
    }

    fun effectiveMode(
        requestedMode: MonicaBlurMode,
        capabilities: MonicaBlurCapabilities = capabilities()
    ): MonicaBlurMode {
        return when (requestedMode) {
            MonicaBlurMode.AUTOMATIC -> {
                if (capabilities.supportsRealtimeBlur) MonicaBlurMode.GLASS else MonicaBlurMode.COMPATIBLE
            }
            MonicaBlurMode.GLASS -> {
                if (capabilities.supportsRealtimeBlur) MonicaBlurMode.GLASS else MonicaBlurMode.COMPATIBLE
            }
            MonicaBlurMode.BACKGROUND_IMAGE -> {
                if (capabilities.supportsBitmapBlur) MonicaBlurMode.BACKGROUND_IMAGE else MonicaBlurMode.COMPATIBLE
            }
            MonicaBlurMode.LIGHTWEIGHT -> MonicaBlurMode.LIGHTWEIGHT
            MonicaBlurMode.COMPATIBLE -> MonicaBlurMode.COMPATIBLE
        }
    }
}
