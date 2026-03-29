package takagi.ru.monica.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex

internal data class PasswordReorderableTopModuleSectionParams(
    val title: String,
    val expanded: Boolean,
    val onExpandedChange: (Boolean) -> Unit,
    val categoryEditMode: Boolean,
    val moduleDisplayOffset: Offset,
    val isActiveDragModule: Boolean,
    val onModuleBoundsChanged: (androidx.compose.ui.geometry.Rect) -> Unit,
    val onDragStart: () -> Unit,
    val onDragCancel: () -> Unit,
    val onDragEnd: () -> Unit,
    val onDragDelta: (Offset) -> Unit
)

@Composable
internal fun PasswordReorderableTopModuleSection(
    params: PasswordReorderableTopModuleSectionParams,
    content: @Composable () -> Unit
) {
    PasswordMenuSection(
        title = params.title,
        expanded = params.expanded,
        onExpandedChange = params.onExpandedChange,
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                params.onModuleBoundsChanged(coordinates.boundsInWindow())
            }
            .graphicsLayer {
                translationX = params.moduleDisplayOffset.x
                translationY = params.moduleDisplayOffset.y
            }
            .zIndex(if (params.isActiveDragModule) 1f else 0f),
        headerModifier = Modifier.pointerInput(params.categoryEditMode) {
            if (!params.categoryEditMode) return@pointerInput
            detectDragGesturesAfterLongPress(
                onDragStart = { params.onDragStart() },
                onDragCancel = params.onDragCancel,
                onDragEnd = params.onDragEnd,
                onDrag = { change, dragAmount ->
                    change.consume()
                    params.onDragDelta(Offset(dragAmount.x, dragAmount.y))
                }
            )
        },
        toggleEnabled = !params.categoryEditMode,
        content = { content() }
    )
}
