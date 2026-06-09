package takagi.ru.monica.ui.effects.blur

import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.MonicaBlurIntensity
import takagi.ru.monica.data.MonicaBlurMode

@Composable
fun rememberMonicaFrostedGlassHazeStyle(
    intensity: MonicaBlurIntensity
): HazeStyle {
    val colors = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()

    return remember(colors.surface, isDark, intensity) {
        HazeStyle(
            tint = colors.surface.copy(alpha = if (isDark) 0.22f else 0.18f),
            blurRadius = MonicaBlurDefaults.frostedGlassRadiusFor(intensity),
            noiseFactor = 0.02f
        )
    }
}

@Composable
fun MonicaPlusBlurIslandSurface(
    settings: AppSettings,
    enabledForThisSurface: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    contentAlignment: Alignment = Alignment.Center,
    fillContent: Boolean = false,
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle = HazeStyle.Unspecified,
    isSensitiveScreen: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val enabled = rememberMonicaPlusBlurEnabledForSurface(
        settings = settings,
        enabledForThisSurface = enabledForThisSurface,
        isSensitiveScreen = isSensitiveScreen
    )
    val effectiveMode = MonicaBlurGate.effectiveMode(settings.plusBlurMode)
    val colors = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val useHaze = enabled && hazeState != null && effectiveMode != MonicaBlurMode.COMPATIBLE
    val defaultHazeStyle = rememberMonicaFrostedGlassHazeStyle(settings.plusBlurIntensity)

    val containerAlpha = when {
        !enabled -> 1f
        useHaze -> if (isDark) 0.46f else 0.38f
        effectiveMode == MonicaBlurMode.COMPATIBLE -> if (isDark) 0.82f else 0.76f
        effectiveMode == MonicaBlurMode.LIGHTWEIGHT -> if (isDark) 0.78f else 0.72f
        else -> if (isDark) 0.74f else 0.68f
    }
    val container = if (enabled) {
        colors.surfaceContainerHigh.copy(alpha = containerAlpha)
    } else {
        colors.surfaceContainerHigh
    }
    val border = if (enabled) {
        null
    } else {
        BorderStroke(
            width = 1.dp,
            color = colors.outlineVariant.copy(alpha = 0.34f)
        )
    }
    val surfaceModifier = if (useHaze) {
        modifier.hazeChild(
            state = requireNotNull(hazeState),
            shape = shape,
            style = if (hazeStyle != HazeStyle.Unspecified) {
                hazeStyle
            } else {
                defaultHazeStyle
            }
        )
    } else {
        modifier
    }

    Surface(
        modifier = surfaceModifier,
        shape = shape,
        color = Color.Transparent,
        contentColor = colors.onSurface,
        tonalElevation = if (enabled) 0.dp else 1.dp,
        shadowElevation = 0.dp,
        border = border
    ) {
        Box(
            modifier = Modifier.background(container)
        ) {
            val contentModifier = if (fillContent) {
                Modifier.fillMaxSize()
            } else {
                Modifier.align(contentAlignment)
            }
            Box(
                modifier = contentModifier
                    .padding(contentPadding),
                contentAlignment = contentAlignment,
                content = content
            )
        }
    }
}

@Composable
fun rememberMonicaPlusBlurEnabledForSurface(
    settings: AppSettings,
    enabledForThisSurface: Boolean,
    isSensitiveScreen: Boolean = false
): Boolean {
    val isBatterySaver = rememberBatterySaverEnabled()
    return enabledForThisSurface && MonicaBlurGate.canUsePlusBlur(
        isPlusActivated = settings.isPlusActivated,
        plusBlurEnabled = settings.plusBlurEnabled,
        isSensitiveScreen = isSensitiveScreen,
        reduceOnBatterySaver = settings.plusBlurReduceOnBatterySaver,
        isBatterySaver = isBatterySaver
    )
}

@Composable
private fun rememberBatterySaverEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        powerManager?.isPowerSaveMode == true
    }
}
