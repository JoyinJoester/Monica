package takagi.ru.monica.ui

import androidx.compose.runtime.Composable
import takagi.ru.monica.data.Category

@Composable
internal fun PasswordListCategoryChipMenuBottomActions(
    categories: List<Category>,
    categoryEditMode: Boolean,
    onCategoryEditModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onCreateCategory: (() -> Unit)?,
    onRenameCategory: ((Category) -> Unit)?,
    onDeleteCategory: ((Category) -> Unit)?,
    categoryActionTarget: Category?,
    onCategoryActionTargetChange: (Category?) -> Unit,
    renameCategoryTarget: Category?,
    onRenameCategoryTargetChange: (Category?) -> Unit,
    renameCategoryInput: String,
    onRenameCategoryInputChange: (String) -> Unit
) {
    PasswordCategoryActionButtons(
        params = PasswordCategoryActionButtonsParams(
            canCreateCategory = onCreateCategory != null,
            canManageExistingCategories = (onRenameCategory != null || onDeleteCategory != null) &&
                categories.isNotEmpty(),
            categoryEditMode = categoryEditMode,
            onCreateCategory = {
                onCategoryEditModeChange(false)
                onDismiss()
                onCreateCategory?.invoke()
            },
            onToggleEditMode = { onCategoryEditModeChange(!categoryEditMode) }
        )
    )

    PasswordCategoryDialogs(
        params = PasswordCategoryDialogsParams(
            categoryActionTarget = if (onDeleteCategory != null || onRenameCategory != null) {
                categoryActionTarget
            } else {
                null
            },
            renameCategoryTarget = if (onRenameCategory != null) {
                renameCategoryTarget
            } else {
                null
            },
            renameCategoryInput = renameCategoryInput,
            onDismissCategoryAction = { onCategoryActionTargetChange(null) },
            onStartRename = onRenameCategory?.let {
                { target: Category ->
                    onCategoryActionTargetChange(null)
                    onRenameCategoryTargetChange(target)
                    onRenameCategoryInputChange(target.name)
                }
            },
            onDeleteCategory = onDeleteCategory?.let {
                { target: Category ->
                    onCategoryActionTargetChange(null)
                    onDismiss()
                    it(target)
                }
            },
            onRenameInputChange = onRenameCategoryInputChange,
            onConfirmRename = { target, input ->
                val newName = input.trim()
                if (newName.isNotBlank()) {
                    onRenameCategory?.invoke(target.copy(name = newName))
                    onRenameCategoryTargetChange(null)
                }
            },
            onDismissRename = { onRenameCategoryTargetChange(null) }
        )
    )
}