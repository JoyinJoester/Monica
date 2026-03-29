package takagi.ru.monica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import takagi.ru.monica.R
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.PasswordListQuickFilterItem
import takagi.ru.monica.data.PasswordListTopModule
import takagi.ru.monica.data.PasswordPageContentType
import takagi.ru.monica.viewmodel.CategoryFilter

internal data class PasswordListCategoryChipMenuReorderableModulesParams(
    val orderedModules: List<PasswordListTopModule>,
    val quickFiltersExpanded: Boolean,
    val onQuickFiltersExpandedChange: (Boolean) -> Unit,
    val foldersExpanded: Boolean,
    val onFoldersExpandedChange: (Boolean) -> Unit,
    val categoryEditMode: Boolean,
    val menuWidth: Dp,
    val quickFilterOrder: List<PasswordListQuickFilterItem>,
    val quickFilterMeasuredSizes: MutableMap<PasswordListQuickFilterItem, IntSize>,
    val quickFilterChipState: PasswordQuickFilterChipState,
    val quickFilterChipCallbacks: PasswordQuickFilterChipCallbacks,
    val onQuickFilterOrderCommitted: (List<PasswordListQuickFilterItem>) -> Unit,
    val currentFilter: CategoryFilter,
    val quickFolderShortcuts: List<PasswordQuickFolderShortcut>,
    val categories: List<Category>,
    val onRequestCategoryAction: (Category) -> Unit,
    val onSelectFilter: (CategoryFilter) -> Unit,
    val moduleDisplayOffset: (PasswordListTopModule) -> Offset,
    val isActiveDragModule: (PasswordListTopModule) -> Boolean,
    val onModuleBoundsChanged: (PasswordListTopModule, Rect) -> Unit,
    val onDragStart: (PasswordListTopModule) -> Unit,
    val onDragCancel: (PasswordListTopModule) -> Unit,
    val onDragEnd: (PasswordListTopModule) -> Unit,
    val onDragDelta: (PasswordListTopModule, Offset) -> Unit
)

@Composable
internal fun PasswordListCategoryChipMenuReorderableModules(
    params: PasswordListCategoryChipMenuReorderableModulesParams
) {
    params.orderedModules.forEach { module ->
        key(module) {
            val sectionParams = PasswordReorderableTopModuleSectionParams(
                title = stringResource(
                    if (module == PasswordListTopModule.QUICK_FILTERS) {
                        R.string.category_selection_menu_quick_filters
                    } else {
                        R.string.category_selection_menu_folders
                    }
                ),
                expanded = if (module == PasswordListTopModule.QUICK_FILTERS) {
                    params.quickFiltersExpanded
                } else {
                    params.foldersExpanded
                },
                onExpandedChange = { expanded ->
                    if (module == PasswordListTopModule.QUICK_FILTERS) {
                        params.onQuickFiltersExpandedChange(expanded)
                    } else {
                        params.onFoldersExpandedChange(expanded)
                    }
                },
                categoryEditMode = params.categoryEditMode,
                moduleDisplayOffset = params.moduleDisplayOffset(module),
                isActiveDragModule = params.isActiveDragModule(module),
                onModuleBoundsChanged = { rect -> params.onModuleBoundsChanged(module, rect) },
                onDragStart = { params.onDragStart(module) },
                onDragCancel = { params.onDragCancel(module) },
                onDragEnd = { params.onDragEnd(module) },
                onDragDelta = { dragAmount -> params.onDragDelta(module, dragAmount) }
            )
            when (module) {
                PasswordListTopModule.QUICK_FILTERS -> {
                    PasswordQuickFiltersMenuModule(
                        params = PasswordQuickFiltersMenuModuleParams(
                            sectionParams = sectionParams,
                            menuWidth = params.menuWidth,
                            categoryEditMode = params.categoryEditMode,
                            quickFilterOrder = params.quickFilterOrder,
                            quickFilterMeasuredSizes = params.quickFilterMeasuredSizes,
                            chipState = params.quickFilterChipState,
                            chipCallbacks = params.quickFilterChipCallbacks,
                            onOrderCommitted = params.onQuickFilterOrderCommitted
                        )
                    )
                }

                PasswordListTopModule.QUICK_FOLDERS -> {
                    PasswordQuickFoldersMenuModule(
                        params = PasswordQuickFoldersMenuModuleParams(
                            sectionParams = sectionParams,
                            currentFilter = params.currentFilter,
                            quickFolderShortcuts = params.quickFolderShortcuts,
                            categoryEditMode = params.categoryEditMode,
                            categories = params.categories,
                            onRequestCategoryAction = params.onRequestCategoryAction,
                            onSelectFilter = params.onSelectFilter
                        )
                    )
                }
            }
        }
    }
}