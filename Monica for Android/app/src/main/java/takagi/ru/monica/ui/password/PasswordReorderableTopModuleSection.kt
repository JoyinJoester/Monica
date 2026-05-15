package takagi.ru.monica.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
    val onDragDelta: (Offset) -> Unit,
    val animate: Boolean = true,
)

@Composable
internal fun PasswordReorderableTopModuleSection(
    params: PasswordReorderableTopModuleSectionParams,
    content: @Composable () -> Unit
) {
    // 用 rememberUpdatedState 包装所有回调，确保 pointerInput 的 suspend block
    // 在 recompose 后始终调用最新的 lambda，避免 swap 后使用过时状态导致抽搐。
    val onDragStart by rememberUpdatedState(params.onDragStart)
    val onDragCancel by rememberUpdatedState(params.onDragCancel)
    val onDragEnd by rememberUpdatedState(params.onDragEnd)
    val onDragDelta by rememberUpdatedState(params.onDragDelta)
    val onModuleBoundsChanged by rememberUpdatedState(params.onModuleBoundsChanged)

    PasswordMenuSection(
        title = params.title,
        expanded = params.expanded,
        onExpandedChange = params.onExpandedChange,
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                onModuleBoundsChanged(coordinates.boundsInWindow())
            }
            .graphicsLayer {
                translationX = params.moduleDisplayOffset.x
                translationY = params.moduleDisplayOffset.y
            }
            .zIndex(if (params.isActiveDragModule) 1f else 0f),
        headerModifier = Modifier.pointerInput(params.categoryEditMode, params.expanded) {
            // 只有在编辑模式且该模块处于收缩状态时才允许拖动互换位置，
            // 展开状态下拖动会因内容高度变化导致 bounds 计算错误而抽搐。
            if (!params.categoryEditMode || params.expanded) return@pointerInput
            detectDragGesturesAfterLongPress(
                onDragStart = { onDragStart() },
                onDragCancel = { onDragCancel() },
                onDragEnd = { onDragEnd() },
                onDrag = { change, dragAmount ->
                    change.consume()
                    onDragDelta(Offset(dragAmount.x, dragAmount.y))
                }
            )
        },
        toggleEnabled = !params.categoryEditMode,
        animate = params.animate,
        content = { content() }
    )
}
