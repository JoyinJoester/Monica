package takagi.ru.monica.ui.effects.blur

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.MonicaBlurIntensity
import takagi.ru.monica.data.MonicaBlurMode

@Composable
fun MonicaGlassSurface(
    enabled: Boolean,
    mode: MonicaBlurMode,
    intensity: MonicaBlurIntensity,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val effectiveMode = MonicaBlurGate.effectiveMode(mode)
    val colors = MaterialTheme.colorScheme
    val alpha = MonicaBlurDefaults.glassContainerAlpha(enabled)
    val container = when (effectiveMode) {
        MonicaBlurMode.COMPATIBLE -> colors.surfaceContainerHigh
        MonicaBlurMode.LIGHTWEIGHT -> colors.surfaceContainerHigh.copy(alpha = 0.84f)
        else -> colors.surfaceContainerHigh.copy(alpha = alpha)
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = container,
        tonalElevation = if (enabled) 0.dp else 1.dp,
        border = BorderStroke(
            width = 1.dp,
            color = colors.outlineVariant.copy(alpha = if (enabled) 0.58f else 0.34f)
        )
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}
