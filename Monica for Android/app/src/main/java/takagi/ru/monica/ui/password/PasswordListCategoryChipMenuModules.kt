package takagi.ru.monica.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.PasswordListQuickFilterItem
import takagi.ru.monica.viewmodel.CategoryFilter

internal data class PasswordQuickFiltersMenuModuleParams(
    val sectionParams: PasswordReorderableTopModuleSectionParams,
    val menuWidth: Dp,
    val categoryEditMode: Boolean,
    val quickFilterOrder: List<PasswordListQuickFilterItem>,
    val quickFilterMeasuredSizes: MutableMap<PasswordListQuickFilterItem, IntSize>,
    val chipState: PasswordQuickFilterChipState,
    val chipCallbacks: PasswordQuickFilterChipCallbacks,
    val onOrderCommitted: (List<PasswordListQuickFilterItem>) -> Unit
)

internal data class PasswordQuickFoldersMenuModuleParams(
    val sectionParams: PasswordReorderableTopModuleSectionParams,
    val currentFilter: CategoryFilter,
    val quickFolderShortcuts: List<PasswordQuickFolderShortcut>,
    val categoryEditMode: Boolean,
    val categories: List<Category>,
    val onRequestCategoryAction: (Category) -> Unit,
    val onSelectFilter: (CategoryFilter) -> Unit
)

@Composable
internal fun PasswordQuickFiltersMenuModule(params: PasswordQuickFiltersMenuModuleParams) {
    PasswordReorderableTopModuleSection(params = params.sectionParams) {
        if (params.categoryEditMode) {
            val horizontalContentPadding = 32.dp
            PasswordQuickFilterEditGrid(
                params = PasswordQuickFilterEditGridParams(
                    items = params.quickFilterOrder,
                    measuredSizes = params.quickFilterMeasuredSizes,
                    availableWidth = (params.menuWidth - horizontalContentPadding).coerceAtLeast(220.dp),
                    chipState = params.chipState,
                    chipCallbacks = params.chipCallbacks,
                    onOrderCommitted = params.onOrderCommitted
                )
            )
        } else {
            PasswordQuickFilterFlow(
                params = PasswordQuickFilterFlowParams(
                    items = params.quickFilterOrder,
                    measuredSizes = params.quickFilterMeasuredSizes,
                    chipState = params.chipState,
                    chipCallbacks = params.chipCallbacks
                )
            )
        }
    }
}

@Composable
internal fun PasswordQuickFoldersMenuModule(params: PasswordQuickFoldersMenuModuleParams) {
    PasswordReorderableTopModuleSection(params = params.sectionParams) {
        PasswordQuickFolderFlow(
            params = PasswordQuickFolderFlowParams(
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
