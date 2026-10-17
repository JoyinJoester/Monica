package takagi.ru.monica.ui

import takagi.ru.monica.repository.ExternalFolderTarget
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
    val onExternalFolderChanged: (ExternalFolderTarget) -> Unit = {},
    val onSelectFilter: (CategoryFilter) -> Unit
)

@Composable
internal fun PasswordQuickFiltersMenuModule(params: PasswordQuickFiltersMenuModuleParams) {
    PasswordReorderableTopModuleSection(params = params.sectionParams) {
            PasswordQuickFilterEditGrid(
                params = PasswordQuickFilterEditGridParams(
                    items = params.quickFilterOrder,
                    chipState = params.chipState,
                    chipCallbacks = params.chipCallbacks,
                    onOrderCommitted = params.onOrderCommitted,
                    editing = params.categoryEditMode
                )
            )
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
                onExternalFolderChanged = params.onExternalFolderChanged,
                onSelectFilter = params.onSelectFilter
            )
        )
    }
}
