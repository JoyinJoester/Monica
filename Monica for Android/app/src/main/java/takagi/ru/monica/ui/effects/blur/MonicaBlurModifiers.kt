package takagi.ru.monica.ui.effects.blur

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import takagi.ru.monica.data.MonicaBlurIntensity
import takagi.ru.monica.data.MonicaBlurMode

fun Modifier.monicaRealtimeBlur(
    enabled: Boolean,
    intensity: MonicaBlurIntensity,
    mode: MonicaBlurMode,
    radiusMultiplier: Float = 1f
): Modifier {
    if (!enabled) return this
    if (mode == MonicaBlurMode.COMPATIBLE || mode == MonicaBlurMode.LIGHTWEIGHT) return this
    if (!MonicaBlurDefaults.supportsRealtimeBlur()) return this
    return blur(MonicaBlurDefaults.radiusFor(intensity) * radiusMultiplier)
}
