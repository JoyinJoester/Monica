package takagi.ru.monica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import takagi.ru.monica.data.PasswordListTopModule

@Composable
internal fun BindCategoryMenuModuleDragState(
    topModulesOrder: List<PasswordListTopModule>,
    categoryEditMode: Boolean,
    moduleDragState: PasswordCategoryMenuModuleDragState,
    coroutineScope: CoroutineScope
) {
    LaunchedEffect(topModulesOrder) {
        if (moduleDragState.draggingModule == null) {
            moduleDragState.onModuleOrderChange(
                PasswordListTopModule.sanitizeOrder(topModulesOrder)
            )
        }
    }
    LaunchedEffect(categoryEditMode) {
        if (!categoryEditMode) {
            resetCategoryMenuDragState(
                onDraggingModuleChange = moduleDragState.onDraggingModuleChange,
                onSettlingModuleChange = moduleDragState.onSettlingModuleChange,
                onModuleDragOffsetChange = moduleDragState.onModuleDragOffsetChange,
                moduleSettleOffset = moduleDragState.moduleSettleOffset,
                modulePlacementOffsets = moduleDragState.modulePlacementOffsets,
                lastModuleAnimatedEpoch = moduleDragState.lastModuleAnimatedEpoch,
                coroutineScope = coroutineScope
            )
        }
    }
}