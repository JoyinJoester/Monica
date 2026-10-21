package takagi.ru.monica.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.*
import takagi.ru.monica.repository.ExternalFolderTarget
import takagi.ru.monica.ui.category.ManagedExternalFolderActions
import takagi.ru.monica.ui.category.externalFolder
import androidx.compose.ui.Alignment
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
    val onSelectFilter: (CategoryFilter) -> Unit,
    val onExternalFolderChanged: (ExternalFolderTarget) -> Unit = {}
)

internal data class PasswordQuickFolderChipRowParams(
    val currentFilter: CategoryFilter,
    val quickFolderShortcuts: List<PasswordQuickFolderShortcut>,
    val onSelectFilter: (CategoryFilter) -> Unit
)

@Composable
internal fun PasswordQuickFolderFlow(
    params: PasswordQuickFolderFlowParams,
    modifier: Modifier = Modifier
) {
    var externalTarget by remember { mutableStateOf<ExternalFolderTarget?>(null) }
    ManagedExternalFolderActions(externalTarget, { externalTarget = null }, params.onExternalFolderChanged,
        onDeleted = { target ->
            when (target) {
                is ExternalFolderTarget.KeePass -> if ((params.currentFilter as? CategoryFilter.KeePassGroupFilter)?.databaseId == target.databaseId)
                    params.onSelectFilter(CategoryFilter.KeePassDatabase(target.databaseId))
                is ExternalFolderTarget.Mdbx -> if ((params.currentFilter as? CategoryFilter.MdbxFolderFilter)?.databaseId == target.databaseId)
                    params.onSelectFilter(CategoryFilter.MdbxDatabase(target.databaseId))
                else -> Unit
            }
            val selected = params.currentFilter as? CategoryFilter.BitwardenFolderFilter
            if (target is ExternalFolderTarget.Bitwarden && selected?.vaultId == target.databaseId && selected.folderId == target.folderId) {
                params.onSelectFilter(CategoryFilter.BitwardenVault(target.databaseId))
            }
        })
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        params.quickFolderShortcuts.forEach { shortcut ->
            val editableCategory = shortcut.resolveEditableCategory(params.categories)
            MonicaExpressiveFilterChip(
                selected = shortcut.targetFilter == params.currentFilter,
                onClick = {
                    if (params.categoryEditMode && !shortcut.isBack) {
                        // Never fall back to navigation while editing. The
                        // category list can briefly lag behind the shortcut
                        // snapshot; navigating in that window made edit mode
                        // indistinguishable from normal browsing.
                        if (editableCategory != null) params.onRequestCategoryAction(editableCategory)
                        else externalTarget = shortcut.targetFilter.externalFolder(shortcut.title)
                    } else {
                        params.onSelectFilter(shortcut.targetFilter)
                    }
                },
                label = shortcut.title,
                leadingIcon = shortcut.resolveLeadingIcon(
                    categoryEditMode = false,
                    editableCategory = editableCategory
                )
            )
        }
    }
}

@Composable
internal fun PasswordQuickFolderChipRow(
    params: PasswordQuickFolderChipRowParams,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start)
    ) {
        params.quickFolderShortcuts.forEach { shortcut ->
            MonicaExpressiveFilterChip(
                selected = shortcut.targetFilter == params.currentFilter,
                onClick = {
                    params.onSelectFilter(shortcut.resolveChipRowTarget(params.currentFilter))
                },
                label = shortcut.title,
                leadingIcon = shortcut.resolveLeadingIcon(
                    categoryEditMode = false,
                    editableCategory = null
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

private fun PasswordQuickFolderShortcut.resolveChipRowTarget(
    currentFilter: CategoryFilter
): CategoryFilter {
    return when {
        !isBack &&
            currentFilter is CategoryFilter.BitwardenFolderFilter &&
            targetFilter == currentFilter -> CategoryFilter.BitwardenVault(currentFilter.vaultId)
        else -> targetFilter
    }
}
