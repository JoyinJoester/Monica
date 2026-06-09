package takagi.ru.monica.ui.effects.blur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.MonicaBlurMode

class MonicaBlurGateTest {

    @Test
    fun plusBlurRequiresPlusActivation() {
        assertFalse(
            MonicaBlurGate.canUsePlusBlur(
                isPlusActivated = false,
                plusBlurEnabled = true,
                isSensitiveScreen = false,
                reduceOnBatterySaver = true,
                isBatterySaver = false
            )
        )
    }

    @Test
    fun plusBlurRejectsSensitiveScreens() {
        assertFalse(
            MonicaBlurGate.canUsePlusBlur(
                isPlusActivated = true,
                plusBlurEnabled = true,
                isSensitiveScreen = true,
                reduceOnBatterySaver = true,
                isBatterySaver = false
            )
        )
    }

    @Test
    fun plusBlurRespectsBatterySaverReduction() {
        assertFalse(
            MonicaBlurGate.canUsePlusBlur(
                isPlusActivated = true,
                plusBlurEnabled = true,
                isSensitiveScreen = false,
                reduceOnBatterySaver = true,
                isBatterySaver = true
            )
        )
    }

    @Test
    fun plusBlurIsAllowedInSafeContext() {
        assertTrue(
            MonicaBlurGate.canUsePlusBlur(
                isPlusActivated = true,
                plusBlurEnabled = true,
                isSensitiveScreen = false,
                reduceOnBatterySaver = true,
                isBatterySaver = false
            )
        )
    }

    @Test
    fun automaticModeChoosesGlassWhenRealtimeBlurIsSupported() {
        val mode = MonicaBlurGate.effectiveMode(
            requestedMode = MonicaBlurMode.AUTOMATIC,
            capabilities = MonicaBlurCapabilities(
                supportsRealtimeBlur = true
            )
        )

        assertEquals(MonicaBlurMode.GLASS, mode)
    }

    @Test
    fun automaticModeFallsBackWhenRealtimeBlurIsUnavailable() {
        val mode = MonicaBlurGate.effectiveMode(
            requestedMode = MonicaBlurMode.AUTOMATIC,
            capabilities = MonicaBlurCapabilities(
                supportsRealtimeBlur = false
            )
        )

        assertEquals(MonicaBlurMode.COMPATIBLE, mode)
    }

    @Test
    fun backgroundImageModeRequiresBitmapBlurSupport() {
        val mode = MonicaBlurGate.effectiveMode(
            requestedMode = MonicaBlurMode.BACKGROUND_IMAGE,
            capabilities = MonicaBlurCapabilities(
                supportsRealtimeBlur = true,
                supportsBitmapBlur = false
            )
        )

        assertEquals(MonicaBlurMode.COMPATIBLE, mode)
    }
}
