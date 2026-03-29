package takagi.ru.monica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import takagi.ru.monica.data.Category

internal data class PasswordCategoryMenuUiState(
    val showDeferredFolderSection: Boolean,
    val quickFiltersExpanded: Boolean,
    val onQuickFiltersExpandedChange: (Boolean) -> Unit,
    val foldersExpanded: Boolean,
    val onFoldersExpandedChange: (Boolean) -> Unit,
    val categoryEditMode: Boolean,
    val onCategoryEditModeChange: (Boolean) -> Unit,
    val categoryActionTarget: Category?,
    val onCategoryActionTargetChange: (Category?) -> Unit,
    val renameCategoryTarget: Category?,
    val onRenameCategoryTargetChange: (Category?) -> Unit,
    val renameCategoryInput: String,
    val onRenameCategoryInputChange: (String) -> Unit
)

@Composable
internal fun rememberCategoryMenuUiState(): PasswordCategoryMenuUiState {
    var showDeferredFolderSection by remember { mutableStateOf(false) }
    var quickFiltersExpanded by rememberSaveable { mutableStateOf(true) }
    var foldersExpanded by rememberSaveable { mutableStateOf(true) }
    var categoryEditMode by remember { mutableStateOf(false) }
    var categoryActionTarget by remember { mutableStateOf<Category?>(null) }
    var renameCategoryTarget by remember { mutableStateOf<Category?>(null) }
    var renameCategoryInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        showDeferredFolderSection = true
    }

    return PasswordCategoryMenuUiState(
        showDeferredFolderSection = showDeferredFolderSection,
        quickFiltersExpanded = quickFiltersExpanded,
        onQuickFiltersExpandedChange = { quickFiltersExpanded = it },
        foldersExpanded = foldersExpanded,
        onFoldersExpandedChange = { foldersExpanded = it },
        categoryEditMode = categoryEditMode,
        onCategoryEditModeChange = { categoryEditMode = it },
        categoryActionTarget = categoryActionTarget,
        onCategoryActionTargetChange = { categoryActionTarget = it },
        renameCategoryTarget = renameCategoryTarget,
        onRenameCategoryTargetChange = { renameCategoryTarget = it },
        renameCategoryInput = renameCategoryInput,
        onRenameCategoryInputChange = { renameCategoryInput = it }
    )
}
