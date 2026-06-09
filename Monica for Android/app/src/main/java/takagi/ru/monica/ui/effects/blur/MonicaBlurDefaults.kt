package takagi.ru.monica.ui.effects.blur

import android.os.Build
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.MonicaBlurIntensity

object MonicaBlurDefaults {
    const val MIN_REALTIME_BLUR_SDK: Int = Build.VERSION_CODES.S_V2
    const val PREVIEW_BITMAP_MAX_DIMENSION: Int = 384

    fun supportsRealtimeBlur(): Boolean = Build.VERSION.SDK_INT >= MIN_REALTIME_BLUR_SDK

    fun radiusFor(intensity: MonicaBlurIntensity): Dp = when (intensity) {
        MonicaBlurIntensity.LIGHT -> 10.dp
        MonicaBlurIntensity.STANDARD -> 20.dp
        MonicaBlurIntensity.STRONG -> 32.dp
    }

    fun frostedGlassRadiusFor(intensity: MonicaBlurIntensity): Dp = when (intensity) {
        MonicaBlurIntensity.LIGHT -> 6.dp
        MonicaBlurIntensity.STANDARD -> 12.dp
        MonicaBlurIntensity.STRONG -> 18.dp
    }

    fun bitmapRadiusFor(intensity: MonicaBlurIntensity): Int = when (intensity) {
        MonicaBlurIntensity.LIGHT -> 8
        MonicaBlurIntensity.STANDARD -> 16
        MonicaBlurIntensity.STRONG -> 24
    }

    fun glassContainerAlpha(enabled: Boolean): Float = if (enabled) 0.68f else 1f
}
