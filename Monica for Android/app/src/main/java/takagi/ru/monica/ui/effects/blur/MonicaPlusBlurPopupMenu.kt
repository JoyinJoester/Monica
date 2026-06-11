package takagi.ru.monica.ui.effects.blur

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import takagi.ru.monica.data.AppSettings

@Composable
fun MonicaPlusBlurPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    settings: AppSettings,
    hazeState: HazeState?,
    hazeStyle: HazeStyle,
    offset: DpOffset,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return

    val density = LocalDensity.current
    val positionProvider = remember(offset, density) {
        MonicaPlusBlurPopupMenuPositionProvider(offset, density)
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        MonicaPlusBlurIslandSurface(
            settings = settings,
            enabledForThisSurface = true,
            modifier = modifier.shadow(10.dp, shape),
            shape = shape,
            contentPadding = PaddingValues(0.dp),
            hazeState = hazeState,
            hazeStyle = hazeStyle
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                content = content
            )
        }
    }
}

private class MonicaPlusBlurPopupMenuPositionProvider(
    private val contentOffset: DpOffset,
    private val density: Density
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val offsetX = with(density) { contentOffset.x.roundToPx() }
        val offsetY = with(density) { contentOffset.y.roundToPx() }

        val preferredX = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left + offsetX
        } else {
            anchorBounds.right - popupContentSize.width - offsetX
        }
        val x = preferredX.coerceIn(
            0,
            (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        )

        val belowY = anchorBounds.bottom + offsetY
        val aboveY = anchorBounds.top - popupContentSize.height - offsetY
        val y = when {
            belowY + popupContentSize.height <= windowSize.height -> belowY
            aboveY >= 0 -> aboveY
            else -> belowY.coerceIn(
                0,
                (windowSize.height - popupContentSize.height).coerceAtLeast(0)
            )
        }

        return IntOffset(x, y)
    }
}
