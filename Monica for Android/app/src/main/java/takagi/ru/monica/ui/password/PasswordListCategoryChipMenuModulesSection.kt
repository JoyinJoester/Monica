package takagi.ru.monica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.PasswordListQuickFilterItem
import takagi.ru.monica.data.PasswordListTopModule
import takagi.ru.monica.data.PasswordPageContentType
import takagi.ru.monica.viewmodel.CategoryFilter

@Composable
internal fun PasswordListCategoryChipMenuModulesSection(
    orderedModules: List<PasswordListTopModule>,
    quickFiltersExpanded: Boolean,
    onQuickFiltersExpandedChange: (Boolean) -> Unit,
    foldersExpanded: Boolean,
    onFoldersExpandedChange: (Boolean) -> Unit,
    categoryEditMode: Boolean,
    menuWidth: Dp,
    quickFilterOrder: List<PasswordListQuickFilterItem>,
    quickFilterMeasuredSizes: MutableMap<PasswordListQuickFilterItem, IntSize>,
    quickFilterFavorite: Boolean,
    onQuickFilterFavoriteChange: (Boolean) -> Unit,
    quickFilter2fa: Boolean,
    onQuickFilter2faChange: (Boolean) -> Unit,
    quickFilterNotes: Boolean,
    onQuickFilterNotesChange: (Boolean) -> Unit,
    quickFilterUncategorized: Boolean,
    onQuickFilterUncategorizedChange: (Boolean) -> Unit,
    quickFilterLocalOnly: Boolean,
    onQuickFilterLocalOnlyChange: (Boolean) -> Unit,
    quickFilterManualStackOnly: Boolean,
    onQuickFilterManualStackOnlyChange: (Boolean) -> Unit,
    quickFilterNeverStack: Boolean,
    onQuickFilterNeverStackChange: (Boolean) -> Unit,
    quickFilterUnstacked: Boolean,
    onQuickFilterUnstackedChange: (Boolean) -> Unit,
    aggregateSelectedTypes: Set<PasswordPageContentType>,
    aggregateVisibleTypes: List<PasswordPageContentType>,
    onToggleAggregateType: (PasswordPageContentType) -> Unit,
    onQuickFilterItemsOrderChange: (List<PasswordListQuickFilterItem>) -> Unit,
    currentFilter: CategoryFilter,
    quickFolderShortcuts: List<PasswordQuickFolderShortcut>,
    categories: List<Category>,
    onCategoryActionTargetChange: (Category?) -> Unit,
    onSelectFilter: (CategoryFilter) -> Unit,
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
    onModuleOrderChange: (List<PasswordListTopModule>) -> Unit,
    onModuleDragOffsetChange: (Offset) -> Unit,
    onModuleReorderEpochChange: (Int) -> Unit,
    onDraggingModuleChange: (PasswordListTopModule?) -> Unit,
    onSettlingModuleChange: (PasswordListTopModule?) -> Unit,
    coroutineScope: CoroutineScope,
    onTopModulesOrderChange: (List<PasswordListTopModule>) -> Unit
) {
    val quickFilterBindings = buildCategoryMenuQuickFilterBindings(
        quickFilterFavorite = quickFilterFavorite,
        onQuickFilterFavoriteChange = onQuickFilterFavoriteChange,
        quickFilter2fa = quickFilter2fa,
        onQuickFilter2faChange = onQuickFilter2faChange,
        quickFilterNotes = quickFilterNotes,
        onQuickFilterNotesChange = onQuickFilterNotesChange,
        quickFilterUncategorized = quickFilterUncategorized,
        onQuickFilterUncategorizedChange = onQuickFilterUncategorizedChange,
        quickFilterLocalOnly = quickFilterLocalOnly,
        onQuickFilterLocalOnlyChange = onQuickFilterLocalOnlyChange,
        quickFilterManualStackOnly = quickFilterManualStackOnly,
        onQuickFilterManualStackOnlyChange = onQuickFilterManualStackOnlyChange,
        quickFilterNeverStack = quickFilterNeverStack,
        onQuickFilterNeverStackChange = onQuickFilterNeverStackChange,
        quickFilterUnstacked = quickFilterUnstacked,
        onQuickFilterUnstackedChange = onQuickFilterUnstackedChange,
        aggregateSelectedTypes = aggregateSelectedTypes,
        aggregateVisibleTypes = aggregateVisibleTypes,
        onToggleAggregateType = onToggleAggregateType
    )

    val moduleDragCallbacks = buildCategoryMenuModuleDragCallbacks(
        modulePlacementOffsets = modulePlacementOffsets,
        draggingModule = draggingModule,
        settlingModule = settlingModule,
        moduleDragOffset = moduleDragOffset,
        moduleSettleOffset = moduleSettleOffset,
        moduleBounds = moduleBounds,
        previousModuleBounds = previousModuleBounds,
        moduleReorderEpoch = moduleReorderEpoch,
        lastModuleAnimatedEpoch = lastModuleAnimatedEpoch,
        moduleOrder = moduleOrder,
        orderedModules = orderedModules,
        onModuleOrderChange = onModuleOrderChange,
        onModuleDragOffsetChange = onModuleDragOffsetChange,
        onModuleReorderEpochChange = onModuleReorderEpochChange,
        onDraggingModuleChange = onDraggingModuleChange,
        onSettlingModuleChange = onSettlingModuleChange,
        coroutineScope = coroutineScope,
        onTopModulesOrderChange = onTopModulesOrderChange
    )

    PasswordListCategoryChipMenuReorderableModules(
        params = PasswordListCategoryChipMenuReorderableModulesParams(
            orderedModules = orderedModules,
            quickFiltersExpanded = quickFiltersExpanded,
            onQuickFiltersExpandedChange = onQuickFiltersExpandedChange,
            foldersExpanded = foldersExpanded,
            onFoldersExpandedChange = onFoldersExpandedChange,
            categoryEditMode = categoryEditMode,
            menuWidth = menuWidth,
            quickFilterOrder = quickFilterOrder,
            quickFilterMeasuredSizes = quickFilterMeasuredSizes,
            quickFilterChipState = quickFilterBindings.state,
            quickFilterChipCallbacks = quickFilterBindings.callbacks,
            onQuickFilterOrderCommitted = { reordered ->
                if (reordered != quickFilterOrder) {
                    onQuickFilterItemsOrderChange(reordered)
                }
            },
            currentFilter = currentFilter,
            quickFolderShortcuts = quickFolderShortcuts,
            categories = categories,
            onRequestCategoryAction = { onCategoryActionTargetChange(it) },
            onSelectFilter = onSelectFilter,
            moduleDisplayOffset = moduleDragCallbacks.moduleDisplayOffset,
            isActiveDragModule = moduleDragCallbacks.isActiveDragModule,
            onModuleBoundsChanged = moduleDragCallbacks.onModuleBoundsChanged,
            onDragStart = moduleDragCallbacks.onDragStart,
            onDragCancel = moduleDragCallbacks.onDragCancel,
            onDragEnd = moduleDragCallbacks.onDragEnd,
            onDragDelta = moduleDragCallbacks.onDragDelta
        )
    )
}