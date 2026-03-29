package takagi.ru.monica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.Category
import takagi.ru.monica.ui.components.MonicaExpressiveFilterChip
import takagi.ru.monica.viewmodel.CategoryFilter

internal data class PasswordQuickFolderFlowParams(
    val currentFilter: CategoryFilter,
    val quickFolderShortcuts: List<PasswordQuickFolderShortcut>,
    val categoryEditMode: Boolean,
    val categories: List<Category>,
    val onRequestCategoryAction: (Category) -> Unit,
    val onSelectFilter: (CategoryFilter) -> Unit
)

@Composable
internal fun PasswordQuickFolderFlow(
    params: PasswordQuickFolderFlowParams,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        params.quickFolderShortcuts.forEach { shortcut ->
            val editableCategory = (shortcut.targetFilter as? CategoryFilter.Custom)
                ?.let { filter -> params.categories.firstOrNull { it.id == filter.categoryId } }
            MonicaExpressiveFilterChip(
                selected = shortcut.targetFilter == params.currentFilter,
                onClick = {
                    if (params.categoryEditMode && editableCategory != null) {
                        params.onRequestCategoryAction(editableCategory)
                    } else {
                        params.onSelectFilter(shortcut.targetFilter)
                    }
                },
                label = shortcut.title,
                leadingIcon = shortcut.resolveLeadingIcon(
                    categoryEditMode = params.categoryEditMode,
                    editableCategory = editableCategory
                )
            )
        }
    }
}

private fun PasswordQuickFolderShortcut.resolveLeadingIcon(
    categoryEditMode: Boolean,
    editableCategory: Category?
): ImageVector {
    return if (categoryEditMode && editableCategory != null) {
        Icons.Default.Edit
    } else if (isBack) {
        Icons.AutoMirrored.Filled.KeyboardArrowLeft
    } else {
        Icons.Default.Folder
    }
}
