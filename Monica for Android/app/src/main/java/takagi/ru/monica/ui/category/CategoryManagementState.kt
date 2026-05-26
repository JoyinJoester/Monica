package takagi.ru.monica.ui.category

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.data.model.StorageTarget
import takagi.ru.monica.repository.KeePassCompatibilityBridge
import takagi.ru.monica.ui.PasswordListCategoryChipMenuBottomActions
import takagi.ru.monica.ui.components.CreateCategoryDialog
import takagi.ru.monica.ui.components.CreateDialogTarget
import takagi.ru.monica.ui.components.UnifiedCategoryFilterSelection
import takagi.ru.monica.utils.KeePassGroupInfo
import takagi.ru.monica.utils.planLocalCategoryMove
import takagi.ru.monica.viewmodel.PasswordViewModel

/**
 * Holds the mutable state for category management UI (create/rename/delete/move).
 * Use [rememberCategoryManagementState] to create an instance.
 */
@Stable
class CategoryManagementState internal constructor() {
    var showCreateCategoryDialog by mutableStateOf(false)
        internal set
    var categoryEditMode by mutableStateOf(false)
    var categoryActionTarget by mutableStateOf<Category?>(null)
    var renameCategoryTarget by mutableStateOf<Category?>(null)
    var renameCategoryInput by mutableStateOf("")

    fun requestCreateCategory() {
        showCreateCategoryDialog = true
    }

    fun dismissCreateCategoryDialog() {
        showCreateCategoryDialog = false
    }
}

@Composable
fun rememberCategoryManagementState(): CategoryManagementState {
    return remember { CategoryManagementState() }
}

/**
 * Provides the trailing content lambda for [UnifiedCategoryFilterChipMenu].
 * Call this inside the `trailingContent` parameter.
 */
@Composable
fun ColumnScope.CategoryManagementTrailingContent(
    state: CategoryManagementState,
    categories: List<Category>,
    keepassDatabases: List<LocalKeePassDatabase>,
    bitwardenVaults: List<BitwardenVault>,
    getBitwardenFolders: (Long) -> Flow<List<takagi.ru.monica.data.bitwarden.BitwardenFolder>>,
    getKeePassGroups: ((Long) -> Flow<List<KeePassGroupInfo>>)?,
    passwordViewModel: PasswordViewModel,
    onDismissFilterSheet: () -> Unit
) {
    val context = LocalContext.current
    PasswordListCategoryChipMenuBottomActions(
        categories = categories,
        keepassDatabases = keepassDatabases,
        bitwardenVaults = bitwardenVaults,
        getBitwardenFolders = getBitwardenFolders,
        getKeePassGroups = getKeePassGroups ?: { kotlinx.coroutines.flow.flowOf(emptyList()) },
        categoryEditMode = state.categoryEditMode,
        onCategoryEditModeChange = { state.categoryEditMode = it },
        onDismiss = onDismissFilterSheet,
        onCreateCategory = {
            onDismissFilterSheet()
            state.showCreateCategoryDialog = true
        },
        onMoveCategory = { category, targetParentCategoryId ->
            executeCategoryMove(context, categories, category, targetParentCategoryId, passwordViewModel)
        },
        onMoveCategoryToStorageTarget = { category, target ->
            executeCategoryMoveToTarget(context, categories, category, target, passwordViewModel)
        },
        onRenameCategory = passwordViewModel::updateCategory,
        onDeleteCategory = passwordViewModel::deleteCategory,
        categoryActionTarget = state.categoryActionTarget,
        onCategoryActionTargetChange = { state.categoryActionTarget = it },
        renameCategoryTarget = state.renameCategoryTarget,
        onRenameCategoryTargetChange = { state.renameCategoryTarget = it },
        renameCategoryInput = state.renameCategoryInput,
        onRenameCategoryInputChange = { state.renameCategoryInput = it }
    )
}

/**
 * Renders the CreateCategoryDialog when [CategoryManagementState.showCreateCategoryDialog] is true.
 * Place this at the end of your Screen composable (outside Scaffold content).
 */
@Composable
fun CategoryManagementCreateDialog(
    state: CategoryManagementState,
    currentFilter: UnifiedCategoryFilterSelection,
    categories: List<Category>,
    keepassDatabases: List<LocalKeePassDatabase>,
    mdbxDatabases: List<LocalMdbxDatabase> = emptyList(),
    bitwardenVaults: List<BitwardenVault>,
    getKeePassGroups: ((Long) -> Flow<List<KeePassGroupInfo>>)?,
    passwordViewModel: PasswordViewModel,
    bitwardenRepository: BitwardenRepository,
    keepassBridge: KeePassCompatibilityBridge?,
    scope: CoroutineScope
) {
    if (!state.showCreateCategoryDialog) return
    val context = LocalContext.current

    val initialLocalParentPath = (currentFilter as? UnifiedCategoryFilterSelection.Custom)?.let { filter ->
        categories.firstOrNull { it.id == filter.categoryId }?.name
    }
    val (initialDialogTarget, initialDialogKeePassDbId, initialDialogMdbxDbId, initialDialogBitwardenVaultId) = resolveInitialDialogTarget(currentFilter)
    val initialMdbxParentFolderId = (currentFilter as? UnifiedCategoryFilterSelection.MdbxFolderFilter)?.folderId

    CreateCategoryDialog(
        visible = true,
        onDismiss = { state.dismissCreateCategoryDialog() },
        categories = categories,
        keepassDatabases = keepassDatabases,
        mdbxDatabases = mdbxDatabases,
        bitwardenVaults = bitwardenVaults,
        getKeePassGroups = getKeePassGroups,
        getMdbxFolders = passwordViewModel::getMdbxFolders,
        onCreateCategoryWithName = { name -> passwordViewModel.addCategory(name) },
        onCreateBitwardenFolder = { vaultId, name ->
            scope.launch {
                val result = bitwardenRepository.createFolder(vaultId, name)
                result.exceptionOrNull()?.let { error ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.save_failed_with_error, error.message ?: ""),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        },
        onCreateKeePassGroup = if (keepassBridge != null) {
            { databaseId, parentPath, name ->
                scope.launch {
                    val result = keepassBridge.createLegacyGroup(databaseId, name, parentPath)
                    result.exceptionOrNull()?.let { error ->
                        Toast.makeText(
                            context,
                            context.getString(R.string.save_failed_with_error, error.message ?: ""),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        } else {
            { _, _, _ -> }
        },
        onCreateMdbxProject = { databaseId, parentFolderId, name ->
            scope.launch {
                passwordViewModel.createMdbxFolder(databaseId, name, parentFolderId ?: "root") { result ->
                    result.exceptionOrNull()?.let { error ->
                        Toast.makeText(
                            context,
                            context.getString(R.string.save_failed_with_error, error.message ?: ""),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        },
        initialLocalParentPath = initialLocalParentPath,
        initialTarget = initialDialogTarget,
        initialKeePassDbId = initialDialogKeePassDbId,
        initialMdbxDbId = initialDialogMdbxDbId,
        initialMdbxParentFolderId = initialMdbxParentFolderId,
        initialBitwardenVaultId = initialDialogBitwardenVaultId
    )
}

// --- Private helpers ---

private fun executeCategoryMove(
    context: Context,
    categories: List<Category>,
    category: Category,
    targetParentCategoryId: Long?,
    passwordViewModel: PasswordViewModel
) {
    runCatching {
        planLocalCategoryMove(
            categories = categories,
            sourceCategory = category,
            targetParentCategory = categories.find { it.id == targetParentCategoryId }
        )
    }.onSuccess { plan ->
        plan.updatedCategories.forEach(passwordViewModel::updateCategory)
    }.onFailure { error ->
        Toast.makeText(
            context,
            context.getString(R.string.save_failed_with_error, error.message ?: ""),
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun executeCategoryMoveToTarget(
    context: Context,
    categories: List<Category>,
    category: Category,
    target: StorageTarget,
    passwordViewModel: PasswordViewModel
) {
    when (target) {
        is StorageTarget.MonicaLocal -> {
            executeCategoryMove(context, categories, category, target.categoryId, passwordViewModel)
        }
        is StorageTarget.Bitwarden -> {
            passwordViewModel.updateCategory(
                category.copy(
                    bitwardenVaultId = target.vaultId,
                    bitwardenFolderId = target.folderId.orEmpty()
                )
            )
        }
        is StorageTarget.KeePass -> {
            Toast.makeText(
                context,
                context.getString(R.string.save_failed_with_error, "当前暂不支持将分类移动到 KeePass 数据库"),
                Toast.LENGTH_SHORT
            ).show()
        }
        is StorageTarget.Mdbx -> {
            Toast.makeText(
                context,
                context.getString(R.string.save_failed_with_error, "当前暂不支持将分类移动到 MDBX 数据库"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

private fun resolveInitialDialogTarget(
    filter: UnifiedCategoryFilterSelection
): Quadruple<CreateDialogTarget?, Long?, Long?, Long?> {
    return when (filter) {
        is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> Quadruple(CreateDialogTarget.KeePass, filter.databaseId, null, null)
        is UnifiedCategoryFilterSelection.KeePassGroupFilter -> Quadruple(CreateDialogTarget.KeePass, filter.databaseId, null, null)
        is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> Quadruple(CreateDialogTarget.KeePass, filter.databaseId, null, null)
        is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> Quadruple(CreateDialogTarget.KeePass, filter.databaseId, null, null)
        is UnifiedCategoryFilterSelection.MdbxDatabaseFilter -> Quadruple(CreateDialogTarget.Mdbx, null, filter.databaseId, null)
        is UnifiedCategoryFilterSelection.MdbxFolderFilter -> Quadruple(CreateDialogTarget.Mdbx, null, filter.databaseId, null)
        is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> Quadruple(CreateDialogTarget.Bitwarden, null, null, filter.vaultId)
        is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> Quadruple(CreateDialogTarget.Bitwarden, null, null, filter.vaultId)
        is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> Quadruple(CreateDialogTarget.Bitwarden, null, null, filter.vaultId)
        is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> Quadruple(CreateDialogTarget.Bitwarden, null, null, filter.vaultId)
        else -> Quadruple(null, null, null, null)
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
