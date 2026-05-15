package takagi.ru.monica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import takagi.ru.monica.data.Category
import takagi.ru.monica.utils.SettingsManager

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
    val onRenameCategoryInputChange: (String) -> Unit,
    /** DataStore 读取完成后为 true，此前展开/收缩状态变化不播放动画 */
    val isExpandedStateLoaded: Boolean = false,
)

@Composable
internal fun rememberCategoryMenuUiState(): PasswordCategoryMenuUiState {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    var showDeferredFolderSection by remember { mutableStateOf(false) }
    // 初始值用 rememberSaveable 保证进程内旋转等场景不丢失，
    // 同时通过 LaunchedEffect 从 DataStore 读取跨进程持久化的值。
    var quickFiltersExpanded by rememberSaveable { mutableStateOf(true) }
    var foldersExpanded by rememberSaveable { mutableStateOf(true) }
    var isExpandedStateLoaded by remember { mutableStateOf(false) }
    var categoryEditMode by remember { mutableStateOf(false) }
    var categoryActionTarget by remember { mutableStateOf<Category?>(null) }
    var renameCategoryTarget by remember { mutableStateOf<Category?>(null) }
    var renameCategoryInput by remember { mutableStateOf("") }

    // 启动时从 DataStore 恢复持久化的展开状态
    LaunchedEffect(Unit) {
        val initialQuickFiltersExpanded = settingsManager.categoryMenuQuickFiltersExpandedFlow.first()
        val initialFoldersExpanded = settingsManager.categoryMenuFoldersExpandedFlow.first()
        quickFiltersExpanded = initialQuickFiltersExpanded
        foldersExpanded = initialFoldersExpanded
        // 等两帧再启用动画：第一帧让 state 变化触发 recompose（此时 animate=false 不播放动画），
        // 第二帧才允许后续用户操作触发动画。
        withFrameNanos { }
        withFrameNanos { }
        isExpandedStateLoaded = true
        showDeferredFolderSection = true
    }

    return PasswordCategoryMenuUiState(
        showDeferredFolderSection = showDeferredFolderSection,
        quickFiltersExpanded = quickFiltersExpanded,
        onQuickFiltersExpandedChange = { newValue ->
            quickFiltersExpanded = newValue
            coroutineScope.launch(Dispatchers.IO) {
                settingsManager.updateCategoryMenuQuickFiltersExpanded(newValue)
            }
        },
        foldersExpanded = foldersExpanded,
        onFoldersExpandedChange = { newValue ->
            foldersExpanded = newValue
            coroutineScope.launch(Dispatchers.IO) {
                settingsManager.updateCategoryMenuFoldersExpanded(newValue)
            }
        },
        categoryEditMode = categoryEditMode,
        onCategoryEditModeChange = { categoryEditMode = it },
        categoryActionTarget = categoryActionTarget,
        onCategoryActionTargetChange = { categoryActionTarget = it },
        renameCategoryTarget = renameCategoryTarget,
        onRenameCategoryTargetChange = { renameCategoryTarget = it },
        renameCategoryInput = renameCategoryInput,
        onRenameCategoryInputChange = { renameCategoryInput = it },
        isExpandedStateLoaded = isExpandedStateLoaded,
    )
}
