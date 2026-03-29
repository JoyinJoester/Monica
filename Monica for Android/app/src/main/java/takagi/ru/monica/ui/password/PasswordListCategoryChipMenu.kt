package takagi.ru.monica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.PasswordListTopModule
import takagi.ru.monica.data.PasswordPageContentType
import takagi.ru.monica.ui.components.rememberUnifiedCategoryFilterChipMenuWidth
import takagi.ru.monica.viewmodel.CategoryFilter

@Composable
internal fun PasswordListCategoryChipMenu(
    currentFilter: CategoryFilter,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    bitwardenVaults: List<takagi.ru.monica.data.bitwarden.BitwardenVault>,
    configuredQuickFilterItems: List<takagi.ru.monica.data.PasswordListQuickFilterItem>,
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
    quickFolderShortcuts: List<PasswordQuickFolderShortcut>,
    topModulesOrder: List<PasswordListTopModule>,
    onTopModulesOrderChange: (List<PasswordListTopModule>) -> Unit,
    onQuickFilterItemsOrderChange: (List<takagi.ru.monica.data.PasswordListQuickFilterItem>) -> Unit,
    launchAnchorBounds: Rect?,
    onDismiss: () -> Unit,
    onSelectFilter: (CategoryFilter) -> Unit,
    categories: List<Category> = emptyList(),
    onCreateCategory: (() -> Unit)? = null,
    onRenameCategory: ((Category) -> Unit)? = null,
    onDeleteCategory: ((Category) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val menuWidth = rememberUnifiedCategoryFilterChipMenuWidth()
    val uiState = rememberCategoryMenuUiState()

    val quickFilterState = rememberCategoryMenuQuickFilterState(configuredQuickFilterItems)
    val moduleDragState = rememberCategoryMenuModuleDragState(topModulesOrder)
    BindCategoryMenuModuleDragState(
        topModulesOrder = topModulesOrder,
        categoryEditMode = uiState.categoryEditMode,
        moduleDragState = moduleDragState,
        coroutineScope = coroutineScope
    )

    val availableModules = remember(uiState.showDeferredFolderSection, quickFolderShortcuts, quickFilterState.order) {
        buildCategoryMenuAvailableModules(
            showDeferredFolderSection = uiState.showDeferredFolderSection,
            quickFolderShortcuts = quickFolderShortcuts,
            quickFilterOrder = quickFilterState.order
        )
    }
    val orderedModules = remember(moduleDragState.moduleOrder, availableModules) {
        resolveCategoryMenuOrderedModules(
            moduleOrder = moduleDragState.moduleOrder,
            availableModules = availableModules
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PasswordDatabaseFiltersSection(
            params = PasswordDatabaseFiltersSectionParams(
                currentFilter = currentFilter,
                keepassDatabases = keepassDatabases,
                bitwardenVaults = bitwardenVaults,
                onSelectFilter = onSelectFilter
            )
        )

        PasswordListCategoryChipMenuModulesSection(
            orderedModules = orderedModules,
            quickFiltersExpanded = uiState.quickFiltersExpanded,
            onQuickFiltersExpandedChange = uiState.onQuickFiltersExpandedChange,
            foldersExpanded = uiState.foldersExpanded,
            onFoldersExpandedChange = uiState.onFoldersExpandedChange,
            categoryEditMode = uiState.categoryEditMode,
            menuWidth = menuWidth,
            quickFilterOrder = quickFilterState.order,
            quickFilterMeasuredSizes = quickFilterState.measuredSizes,
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
            onToggleAggregateType = onToggleAggregateType,
            onQuickFilterItemsOrderChange = { reordered ->
                quickFilterState.onOrderChange(reordered)
                onQuickFilterItemsOrderChange(reordered)
            },
            currentFilter = currentFilter,
            quickFolderShortcuts = quickFolderShortcuts,
            categories = categories,
            onCategoryActionTargetChange = uiState.onCategoryActionTargetChange,
            onSelectFilter = onSelectFilter,
            modulePlacementOffsets = moduleDragState.modulePlacementOffsets,
            draggingModule = moduleDragState.draggingModule,
            settlingModule = moduleDragState.settlingModule,
            moduleDragOffset = moduleDragState.moduleDragOffset,
            moduleSettleOffset = moduleDragState.moduleSettleOffset,
            moduleBounds = moduleDragState.moduleBounds,
            previousModuleBounds = moduleDragState.previousModuleBounds,
            moduleReorderEpoch = moduleDragState.moduleReorderEpoch,
            lastModuleAnimatedEpoch = moduleDragState.lastModuleAnimatedEpoch,
            moduleOrder = moduleDragState.moduleOrder,
            onModuleOrderChange = moduleDragState.onModuleOrderChange,
            onModuleDragOffsetChange = moduleDragState.onModuleDragOffsetChange,
            onModuleReorderEpochChange = moduleDragState.onModuleReorderEpochChange,
            onDraggingModuleChange = moduleDragState.onDraggingModuleChange,
            onSettlingModuleChange = moduleDragState.onSettlingModuleChange,
            coroutineScope = coroutineScope,
            onTopModulesOrderChange = onTopModulesOrderChange
        )

        PasswordListCategoryChipMenuBottomActions(
            categories = categories,
            categoryEditMode = uiState.categoryEditMode,
            onCategoryEditModeChange = uiState.onCategoryEditModeChange,
            onDismiss = onDismiss,
            onCreateCategory = onCreateCategory,
            onRenameCategory = onRenameCategory,
            onDeleteCategory = onDeleteCategory,
            categoryActionTarget = uiState.categoryActionTarget,
            onCategoryActionTargetChange = uiState.onCategoryActionTargetChange,
            renameCategoryTarget = uiState.renameCategoryTarget,
            onRenameCategoryTargetChange = uiState.onRenameCategoryTargetChange,
            renameCategoryInput = uiState.renameCategoryInput,
            onRenameCategoryInputChange = uiState.onRenameCategoryInputChange
        )
    }
}
