package takagi.ru.monica.ui.effects.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import takagi.ru.monica.data.MonicaBlurIntensity
import takagi.ru.monica.data.MonicaBlurMode

@Composable
fun MonicaBlurBackground(
    enabled: Boolean,
    mode: MonicaBlurMode,
    intensity: MonicaBlurIntensity,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val effectiveMode = MonicaBlurGate.effectiveMode(mode)
    val colors = MaterialTheme.colorScheme
    val baseColor = colors.surfaceContainerLow
    val primary = colors.primary.copy(alpha = if (enabled) 0.36f else 0.16f)
    val tertiary = colors.tertiary.copy(alpha = if (enabled) 0.30f else 0.12f)
    val secondary = colors.secondary.copy(alpha = if (enabled) 0.22f else 0.10f)

    Box(modifier = modifier.background(baseColor)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.surfaceContainerHigh,
                            colors.surfaceContainerLow
                        )
                    )
                )
                .drawBehind {
                    drawCircle(
                        color = primary,
                        radius = size.maxDimension * 0.42f,
                        center = Offset(size.width * 0.18f, size.height * 0.16f)
                    )
                    drawCircle(
                        color = tertiary,
                        radius = size.maxDimension * 0.36f,
                        center = Offset(size.width * 0.86f, size.height * 0.28f)
                    )
                    drawCircle(
                        color = secondary,
                        radius = size.maxDimension * 0.34f,
                        center = Offset(size.width * 0.56f, size.height * 0.98f)
                    )
                }
                .monicaRealtimeBlur(
                    enabled = enabled && effectiveMode != MonicaBlurMode.COMPATIBLE,
                    intensity = intensity,
                    mode = effectiveMode
                )
        )
        if (!enabled || effectiveMode == MonicaBlurMode.COMPATIBLE) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = 0.04f))
            )
        }
        content()
    }
}
