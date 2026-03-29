package takagi.ru.monica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import takagi.ru.monica.data.PasswordListQuickFilterItem
import takagi.ru.monica.data.PasswordListTopModule
import java.util.concurrent.CancellationException

private fun <T> reorderList(list: List<T>, fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex == toIndex || fromIndex !in list.indices || toIndex !in list.indices) {
        return list
    }
    return list.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}

internal data class PasswordCategoryMenuSwapResult(
    val moduleOrder: List<PasswordListTopModule>,
    val moduleDragOffset: Offset
)

internal data class PasswordCategoryMenuModuleDragCallbacks(
    val moduleDisplayOffset: (PasswordListTopModule) -> Offset,
    val isActiveDragModule: (PasswordListTopModule) -> Boolean,
    val onModuleBoundsChanged: (PasswordListTopModule, Rect) -> Unit,
    val onDragStart: (PasswordListTopModule) -> Unit,
    val onDragCancel: (PasswordListTopModule) -> Unit,
    val onDragEnd: (PasswordListTopModule) -> Unit,
    val onDragDelta: (PasswordListTopModule, Offset) -> Unit
)

internal fun buildCategoryMenuAvailableModules(
    showDeferredFolderSection: Boolean,
    quickFolderShortcuts: List<PasswordQuickFolderShortcut>,
    quickFilterOrder: List<PasswordListQuickFilterItem>
): List<PasswordListTopModule> {
    return buildList {
        if (quickFilterOrder.isNotEmpty()) add(PasswordListTopModule.QUICK_FILTERS)
        if (showDeferredFolderSection && quickFolderShortcuts.isNotEmpty()) {
            add(PasswordListTopModule.QUICK_FOLDERS)
        }
    }
}

internal fun resolveCategoryMenuOrderedModules(
    moduleOrder: List<PasswordListTopModule>,
    availableModules: List<PasswordListTopModule>
): List<PasswordListTopModule> {
    return moduleOrder.filter { it in availableModules }
}

internal fun resetCategoryMenuDragState(
    onDraggingModuleChange: (PasswordListTopModule?) -> Unit,
    onSettlingModuleChange: (PasswordListTopModule?) -> Unit,
    onModuleDragOffsetChange: (Offset) -> Unit,
    moduleSettleOffset: Animatable<Offset, AnimationVector2D>,
    modulePlacementOffsets: Map<PasswordListTopModule, Animatable<Offset, AnimationVector2D>>,
    lastModuleAnimatedEpoch: MutableMap<PasswordListTopModule, Int>,
    coroutineScope: CoroutineScope
) {
    onDraggingModuleChange(null)
    onSettlingModuleChange(null)
    onModuleDragOffsetChange(Offset.Zero)
    coroutineScope.launch {
        moduleSettleOffset.stop()
        moduleSettleOffset.snapTo(Offset.Zero)
    }
    modulePlacementOffsets.values.forEach { animatable ->
        coroutineScope.launch {
            animatable.stop()
            animatable.snapTo(Offset.Zero)
        }
    }
    lastModuleAnimatedEpoch.clear()
}

internal fun settleCategoryMenuModule(
    module: PasswordListTopModule,
    currentDragOffset: Offset,
    onDraggingModuleChange: (PasswordListTopModule?) -> Unit,
    onModuleDragOffsetChange: (Offset) -> Unit,
    onSettlingModuleChange: (PasswordListTopModule?) -> Unit,
    canClearSettling: () -> Boolean,
    coroutineScope: CoroutineScope,
    moduleSettleOffset: Animatable<Offset, AnimationVector2D>
) {
    onDraggingModuleChange(null)
    onModuleDragOffsetChange(Offset.Zero)
    onSettlingModuleChange(module)
    coroutineScope.launch {
        try {
            moduleSettleOffset.stop()
            moduleSettleOffset.snapTo(currentDragOffset)
            moduleSettleOffset.animateTo(
                targetValue = Offset.Zero,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            )
        } catch (_: CancellationException) {
        } finally {
            if (canClearSettling()) {
                onSettlingModuleChange(null)
            }
            moduleSettleOffset.snapTo(Offset.Zero)
        }
    }
}

internal fun animateCategoryMenuModulePlacementIfNeeded(
    module: PasswordListTopModule,
    newRect: Rect,
    previousModuleBounds: MutableMap<PasswordListTopModule, Rect>,
    draggingModule: PasswordListTopModule?,
    settlingModule: PasswordListTopModule?,
    moduleReorderEpoch: Int,
    lastModuleAnimatedEpoch: MutableMap<PasswordListTopModule, Int>,
    modulePlacementOffsets: MutableMap<PasswordListTopModule, Animatable<Offset, AnimationVector2D>>,
    coroutineScope: CoroutineScope
) {
    val previousRect = previousModuleBounds.put(module, newRect)
    if (previousRect == null || draggingModule == module || settlingModule == module) return
    if (moduleReorderEpoch == 0 || lastModuleAnimatedEpoch[module] == moduleReorderEpoch) return
    val delta = previousRect.topLeft - newRect.topLeft
    if (delta == Offset.Zero) return
    lastModuleAnimatedEpoch[module] = moduleReorderEpoch
    val animatable = modulePlacementOffsets.getOrPut(module) {
        Animatable(Offset.Zero, Offset.VectorConverter)
    }
    coroutineScope.launch {
        try {
            animatable.stop()
            animatable.snapTo(delta)
            animatable.animateTo(
                targetValue = Offset.Zero,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            )
        } catch (_: CancellationException) {
        }
    }
}

internal fun updateCategoryMenuModuleBounds(
    moduleBounds: MutableMap<PasswordListTopModule, Rect>,
    module: PasswordListTopModule,
    rect: Rect
) {
    val previousRect = moduleBounds[module]
    if (previousRect == rect) return
    moduleBounds[module] = rect
}

internal fun swapCategoryMenuModuleIfNeeded(
    module: PasswordListTopModule,
    moduleBounds: Map<PasswordListTopModule, Rect>,
    orderedModules: List<PasswordListTopModule>,
    moduleOrder: List<PasswordListTopModule>,
    moduleDragOffset: Offset
): PasswordCategoryMenuSwapResult? {
    val draggedRect = moduleBounds[module] ?: return null
    val probe = draggedRect.center + moduleDragOffset
    val target = orderedModules.firstOrNull { candidate ->
        candidate != module && moduleBounds[candidate]?.contains(probe) == true
    } ?: return null
    val targetRect = moduleBounds[target] ?: return null
    val fromIndex = moduleOrder.indexOf(module)
    val toIndex = moduleOrder.indexOf(target)
    if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) return null
    val reordered = reorderList(moduleOrder, fromIndex, toIndex)
    return PasswordCategoryMenuSwapResult(
        moduleOrder = reordered,
        moduleDragOffset = moduleDragOffset + draggedRect.center - targetRect.center
    )
}

internal fun buildCategoryMenuModuleDragCallbacks(
    modulePlacementOffsets: MutableMap<PasswordListTopModule, Animatable<Offset, AnimationVector2D>>,
    draggingModule: PasswordListTopModule?,
    settlingModule: PasswordListTopModule?,
    moduleDragOffset: Offset,
    moduleSettleOffset: Animatable<Offset, AnimationVector2D>,
    moduleBounds: MutableMap<PasswordListTopModule, Rect>,
    previousModuleBounds: MutableMap<PasswordListTopModule, Rect>,
    moduleReorderEpoch: Int,
    lastModuleAnimatedEpoch: MutableMap<PasswordListTopModule, Int>,
    moduleOrder: List<PasswordListTopModule>,
    orderedModules: List<PasswordListTopModule>,
    onModuleOrderChange: (List<PasswordListTopModule>) -> Unit,
    onModuleDragOffsetChange: (Offset) -> Unit,
    onModuleReorderEpochChange: (Int) -> Unit,
    onDraggingModuleChange: (PasswordListTopModule?) -> Unit,
    onSettlingModuleChange: (PasswordListTopModule?) -> Unit,
    coroutineScope: CoroutineScope,
    onTopModulesOrderChange: (List<PasswordListTopModule>) -> Unit
): PasswordCategoryMenuModuleDragCallbacks {
    return PasswordCategoryMenuModuleDragCallbacks(
        moduleDisplayOffset = { module ->
            val modulePlacementOffset = modulePlacementOffsets[module]?.value ?: Offset.Zero
            when {
                draggingModule == module -> moduleDragOffset
                settlingModule == module -> moduleSettleOffset.value
                else -> modulePlacementOffset
            }
        },
        isActiveDragModule = { module ->
            draggingModule == module || settlingModule == module
        },
        onModuleBoundsChanged = { module, rect ->
            updateCategoryMenuModuleBounds(
                moduleBounds = moduleBounds,
                module = module,
                rect = rect
            )
            animateCategoryMenuModulePlacementIfNeeded(
                module = module,
                newRect = rect,
                previousModuleBounds = previousModuleBounds,
                draggingModule = draggingModule,
                settlingModule = settlingModule,
                moduleReorderEpoch = moduleReorderEpoch,
                lastModuleAnimatedEpoch = lastModuleAnimatedEpoch,
                modulePlacementOffsets = modulePlacementOffsets,
                coroutineScope = coroutineScope
            )
        },
        onDragStart = { module ->
            onSettlingModuleChange(null)
            coroutineScope.launch {
                moduleSettleOffset.stop()
                moduleSettleOffset.snapTo(Offset.Zero)
            }
            onDraggingModuleChange(module)
            onModuleDragOffsetChange(Offset.Zero)
        },
        onDragCancel = { module ->
            settleCategoryMenuModule(
                module = module,
                currentDragOffset = moduleDragOffset,
                onDraggingModuleChange = onDraggingModuleChange,
                onModuleDragOffsetChange = onModuleDragOffsetChange,
                onSettlingModuleChange = onSettlingModuleChange,
                canClearSettling = { settlingModule == module },
                coroutineScope = coroutineScope,
                moduleSettleOffset = moduleSettleOffset
            )
        },
        onDragEnd = { module ->
            settleCategoryMenuModule(
                module = module,
                currentDragOffset = moduleDragOffset,
                onDraggingModuleChange = onDraggingModuleChange,
                onModuleDragOffsetChange = onModuleDragOffsetChange,
                onSettlingModuleChange = onSettlingModuleChange,
                canClearSettling = { settlingModule == module },
                coroutineScope = coroutineScope,
                moduleSettleOffset = moduleSettleOffset
            )
        },
        onDragDelta = { module, dragAmount ->
            val nextOffset = moduleDragOffset + dragAmount
            onModuleDragOffsetChange(nextOffset)
            val swapResult = swapCategoryMenuModuleIfNeeded(
                module = module,
                moduleBounds = moduleBounds,
                orderedModules = orderedModules,
                moduleOrder = moduleOrder,
                moduleDragOffset = nextOffset
            )
            if (swapResult != null) {
                onModuleOrderChange(swapResult.moduleOrder)
                onModuleDragOffsetChange(swapResult.moduleDragOffset)
                onModuleReorderEpochChange(moduleReorderEpoch + 1)
                onTopModulesOrderChange(swapResult.moduleOrder)
            }
        }
    )
}