package takagi.ru.monica.ui.effects.blur

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import takagi.ru.monica.data.AppSettings

@Composable
fun MonicaPlusBlurDropdownMenuSurface(
    settings: AppSettings,
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    MonicaPlusBlurIslandSurface(
        settings = settings,
        enabledForThisSurface = true,
        modifier = modifier,
        shape = shape,
        contentPadding = contentPadding,
        // DropdownMenu is rendered in a Popup. Binding it to a scrolling page HazeState
        // makes the sampled layer move/clip while the list scrolls, so dropdowns use a
        // stable frosted surface instead of a live hazeChild.
        hazeState = null,
        hazeStyle = hazeStyle,
        content = content
    )
}
