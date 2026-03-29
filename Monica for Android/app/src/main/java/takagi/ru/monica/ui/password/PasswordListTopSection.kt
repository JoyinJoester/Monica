package takagi.ru.monica.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.CategorySelectionUiMode
import takagi.ru.monica.data.PasswordCardDisplayMode
import takagi.ru.monica.data.PasswordPageContentType
import takagi.ru.monica.data.PasswordListQuickFilterItem
import takagi.ru.monica.ui.components.CreateCategoryDialog
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.components.UnifiedCategoryFilterBottomSheet
import takagi.ru.monica.ui.components.UnifiedCategoryFilterChipMenuDropdown
import takagi.ru.monica.ui.components.UnifiedCategoryFilterChipMenuOffset
import takagi.ru.monica.ui.components.UnifiedCategoryFilterSelection
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.utils.decodeKeePassPathForDisplay
import takagi.ru.monica.viewmodel.CategoryFilter
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel
import takagi.ru.monica.ui.password.StackCardMode

@Composable
internal fun PasswordListTopSection(
    currentFilter: CategoryFilter,
    categories: List<Category>,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    bitwardenVaults: List<takagi.ru.monica.data.bitwarden.BitwardenVault>,
    viewModel: PasswordViewModel,
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    bitwardenViewModel: BitwardenViewModel,
    selectedBitwardenVaultId: Long?,
    isTopBarSyncing: Boolean,
    isArchiveView: Boolean,
    isKeePassDatabaseView: Boolean,
    searchQuery: String,
    isSearchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    topActionsMenuExpanded: Boolean,
    onTopActionsMenuExpandedChange: (Boolean) -> Unit,
    isCategorySheetVisible: Boolean,
    onCategorySheetVisibleChange: (Boolean) -> Unit,
    categoryPillBoundsInWindow: androidx.compose.ui.geometry.Rect?,
    onCategoryPillBoundsChange: (androidx.compose.ui.geometry.Rect?) -> Unit,
    showDisplayOptionsSheet: Boolean,
    onShowDisplayOptionsSheetChange: (Boolean) -> Unit,
    configuredQuickFilterItems: List<PasswordListQuickFilterItem>,
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
    categoryMenuQuickFolderShortcuts: List<PasswordQuickFolderShortcut>,
    stackCardMode: StackCardMode,
    groupMode: String,
    passwordCardDisplayMode: PasswordCardDisplayMode,
    settingsViewModel: SettingsViewModel,
    context: Context,
    activity: FragmentActivity?,
    biometricHelper: BiometricHelper,
    canUseBiometric: Boolean,
    coroutineScope: CoroutineScope,
    bitwardenRepository: BitwardenRepository,
    onRenameCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onOpenCommonAccountTemplates: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTrash: () -> Unit
) {
    val appSettings by settingsViewModel.settings.collectAsState()
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    Column {
        val title = when (val filter = currentFilter) {
            is CategoryFilter.All -> "ALL"
            is CategoryFilter.Archived -> stringResource(R.string.archive_page_title)
            is CategoryFilter.Local -> stringResource(R.string.filter_monica)
            is CategoryFilter.LocalOnly -> stringResource(R.string.filter_local_only)
            is CategoryFilter.Starred -> stringResource(R.string.filter_starred)
            is CategoryFilter.Uncategorized -> stringResource(R.string.filter_uncategorized)
            is CategoryFilter.LocalStarred -> "${stringResource(R.string.filter_monica)} · ${stringResource(R.string.filter_starred)}"
            is CategoryFilter.LocalUncategorized -> "${stringResource(R.string.filter_monica)} · ${stringResource(R.string.filter_uncategorized)}"
            is CategoryFilter.Custom -> categories.find { it.id == filter.categoryId }?.name ?: stringResource(R.string.filter_all)
            is CategoryFilter.KeePassDatabase -> keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"
            is CategoryFilter.KeePassGroupFilter -> decodeKeePassPathForDisplay(filter.groupPath)
            is CategoryFilter.KeePassDatabaseStarred -> "${keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"} · ${stringResource(R.string.filter_starred)}"
            is CategoryFilter.KeePassDatabaseUncategorized -> "${keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"} · ${stringResource(R.string.filter_uncategorized)}"
            is CategoryFilter.BitwardenVault -> "Bitwarden"
            is CategoryFilter.BitwardenFolderFilter -> "Bitwarden"
            is CategoryFilter.BitwardenVaultStarred -> "${stringResource(R.string.filter_bitwarden)} · ${stringResource(R.string.filter_starred)}"
            is CategoryFilter.BitwardenVaultUncategorized -> "${stringResource(R.string.filter_bitwarden)} · ${stringResource(R.string.filter_uncategorized)}"
        }

        ExpressiveTopBar(
            title = title,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            isSearchExpanded = isSearchExpanded,
            onSearchExpandedChange = onSearchExpandedChange,
            searchHint = stringResource(R.string.search_passwords_hint),
            onActionPillBoundsChanged = if (isArchiveView) null else onCategoryPillBoundsChange,
            actions = {
                if (isArchiveView) {
                    IconButton(onClick = { viewModel.setCategoryFilter(CategoryFilter.All) }) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.nav_passwords_short),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isArchiveView) {
                    IconButton(onClick = { onCategorySheetVisibleChange(true) }) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = stringResource(R.string.category),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { onSearchExpandedChange(true) }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { onTopActionsMenuExpandedChange(true) }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!isArchiveView && appSettings.categorySelectionUiMode == CategorySelectionUiMode.CHIP_MENU) {
                        UnifiedCategoryFilterChipMenuDropdown(
                            expanded = isCategorySheetVisible,
                            onDismissRequest = { onCategorySheetVisibleChange(false) },
                            offset = UnifiedCategoryFilterChipMenuOffset
                        ) {
                            PasswordListCategoryChipMenu(
                                currentFilter = currentFilter,
                                keepassDatabases = keepassDatabases,
                                bitwardenVaults = bitwardenVaults,
                                configuredQuickFilterItems = configuredQuickFilterItems,
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
                                quickFolderShortcuts = categoryMenuQuickFolderShortcuts,
                                topModulesOrder = appSettings.passwordListTopModulesOrder,
                                onTopModulesOrderChange = settingsViewModel::updatePasswordListTopModulesOrder,
                                onQuickFilterItemsOrderChange = settingsViewModel::updatePasswordListQuickFilterItems,
                                launchAnchorBounds = null,
                                onDismiss = { onCategorySheetVisibleChange(false) },
                                onSelectFilter = viewModel::setCategoryFilter,
                                categories = categories,
                                onCreateCategory = {
                                    onCategorySheetVisibleChange(false)
                                    showCreateCategoryDialog = true
                                },
                                onRenameCategory = onRenameCategory,
                                onDeleteCategory = onDeleteCategory
                            )
                        }
                    }
                    MaterialTheme(
                        shapes = MaterialTheme.shapes.copy(
                            extraSmall = RoundedCornerShape(20.dp),
                            small = RoundedCornerShape(20.dp)
                        )
                    ) {
                        DropdownMenu(
                            expanded = topActionsMenuExpanded,
                            onDismissRequest = { onTopActionsMenuExpandedChange(false) },
                            offset = DpOffset(x = 48.dp, y = 6.dp),
                            modifier = Modifier
                                .widthIn(min = 220.dp, max = 260.dp)
                                .shadow(10.dp, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                        ) {
                            if (isKeePassDatabaseView) {
                                DropdownMenuItem(
                                    text = {
                                        Text("${stringResource(R.string.refresh)} ${stringResource(R.string.filter_keepass)}")
                                    },
                                    leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) },
                                    onClick = {
                                        onTopActionsMenuExpandedChange(false)
                                        viewModel.refreshKeePassFromSourceForCurrentContext()
                                    }
                                )
                            }
                            if (selectedBitwardenVaultId != null) {
                                DropdownMenuItem(
                                    text = { Text("同步bitwarden数据库") },
                                    leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) },
                                    enabled = !isTopBarSyncing,
                                    onClick = {
                                        if (isTopBarSyncing) return@DropdownMenuItem
                                        val vaultId = selectedBitwardenVaultId
                                            ?: return@DropdownMenuItem
                                        onTopActionsMenuExpandedChange(false)
                                        bitwardenViewModel.requestManualSync(vaultId)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.display_options_menu_title)) },
                                leadingIcon = { Icon(Icons.Default.DashboardCustomize, contentDescription = null) },
                                onClick = {
                                    onTopActionsMenuExpandedChange(false)
                                    onShowDisplayOptionsSheetChange(true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_account_title)) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                onClick = {
                                    onTopActionsMenuExpandedChange(false)
                                    onOpenCommonAccountTemplates()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.timeline_title)) },
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                onClick = {
                                    onTopActionsMenuExpandedChange(false)
                                    onOpenHistory()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.timeline_trash_title)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    onTopActionsMenuExpandedChange(false)
                                    onOpenTrash()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.archive_page_title)) },
                                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                onClick = {
                                    onTopActionsMenuExpandedChange(false)
                                    viewModel.setCategoryFilter(CategoryFilter.Archived)
                                }
                            )
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        val unifiedSelectedFilter = when (val filter = currentFilter) {
            is CategoryFilter.All -> UnifiedCategoryFilterSelection.All
            is CategoryFilter.Archived -> UnifiedCategoryFilterSelection.All
            is CategoryFilter.Local -> UnifiedCategoryFilterSelection.Local
            is CategoryFilter.LocalOnly -> UnifiedCategoryFilterSelection.Local
            is CategoryFilter.Starred -> UnifiedCategoryFilterSelection.Starred
            is CategoryFilter.Uncategorized -> UnifiedCategoryFilterSelection.Uncategorized
            is CategoryFilter.LocalStarred -> UnifiedCategoryFilterSelection.LocalStarred
            is CategoryFilter.LocalUncategorized -> UnifiedCategoryFilterSelection.LocalUncategorized
            is CategoryFilter.Custom -> UnifiedCategoryFilterSelection.Custom(filter.categoryId)
            is CategoryFilter.BitwardenVault -> UnifiedCategoryFilterSelection.BitwardenVaultFilter(filter.vaultId)
            is CategoryFilter.BitwardenFolderFilter -> UnifiedCategoryFilterSelection.BitwardenFolderFilter(filter.vaultId, filter.folderId)
            is CategoryFilter.BitwardenVaultStarred -> UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter(filter.vaultId)
            is CategoryFilter.BitwardenVaultUncategorized -> UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter(filter.vaultId)
            is CategoryFilter.KeePassDatabase -> UnifiedCategoryFilterSelection.KeePassDatabaseFilter(filter.databaseId)
            is CategoryFilter.KeePassGroupFilter -> UnifiedCategoryFilterSelection.KeePassGroupFilter(filter.databaseId, filter.groupPath)
            is CategoryFilter.KeePassDatabaseStarred -> UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter(filter.databaseId)
            is CategoryFilter.KeePassDatabaseUncategorized -> UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter(filter.databaseId)
        }
        if (isCategorySheetVisible && !isArchiveView) {
            when (appSettings.categorySelectionUiMode) {
                CategorySelectionUiMode.BOTTOM_SHEET -> UnifiedCategoryFilterBottomSheet(
                    visible = true,
                    onDismiss = { onCategorySheetVisibleChange(false) },
                    selected = unifiedSelectedFilter,
                    showLocalOnlyQuickFilter = true,
                    isLocalOnlyQuickFilterSelected = currentFilter is CategoryFilter.LocalOnly,
                    onSelectLocalOnlyQuickFilter = { viewModel.setCategoryFilter(CategoryFilter.LocalOnly) },
                    onSelect = { selection ->
                        when (selection) {
                            is UnifiedCategoryFilterSelection.All -> viewModel.setCategoryFilter(CategoryFilter.All)
                            is UnifiedCategoryFilterSelection.Local -> viewModel.setCategoryFilter(CategoryFilter.Local)
                            is UnifiedCategoryFilterSelection.Starred -> viewModel.setCategoryFilter(CategoryFilter.Starred)
                            is UnifiedCategoryFilterSelection.Uncategorized -> viewModel.setCategoryFilter(CategoryFilter.Uncategorized)
                            is UnifiedCategoryFilterSelection.LocalStarred -> viewModel.setCategoryFilter(CategoryFilter.LocalStarred)
                            is UnifiedCategoryFilterSelection.LocalUncategorized -> viewModel.setCategoryFilter(CategoryFilter.LocalUncategorized)
                            is UnifiedCategoryFilterSelection.Custom -> viewModel.setCategoryFilter(CategoryFilter.Custom(selection.categoryId))
                            is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> viewModel.setCategoryFilter(CategoryFilter.BitwardenVault(selection.vaultId))
                            is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> viewModel.setCategoryFilter(CategoryFilter.BitwardenFolderFilter(selection.folderId, selection.vaultId))
                            is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> viewModel.setCategoryFilter(CategoryFilter.BitwardenVaultStarred(selection.vaultId))
                            is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> viewModel.setCategoryFilter(CategoryFilter.BitwardenVaultUncategorized(selection.vaultId))
                            is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> viewModel.setCategoryFilter(CategoryFilter.KeePassDatabase(selection.databaseId))
                            is UnifiedCategoryFilterSelection.KeePassGroupFilter -> viewModel.setCategoryFilter(CategoryFilter.KeePassGroupFilter(selection.databaseId, selection.groupPath))
                            is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> viewModel.setCategoryFilter(CategoryFilter.KeePassDatabaseStarred(selection.databaseId))
                            is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> viewModel.setCategoryFilter(CategoryFilter.KeePassDatabaseUncategorized(selection.databaseId))
                        }
                    },
                    launchAnchorBounds = categoryPillBoundsInWindow,
                    categories = categories,
                    keepassDatabases = keepassDatabases,
                    bitwardenVaults = bitwardenVaults,
                    getBitwardenFolders = viewModel::getBitwardenFolders,
                    getKeePassGroups = localKeePassViewModel::getGroups,
                    onVerifyMasterPassword = { input ->
                        takagi.ru.monica.security.SecurityManager(context).verifyMasterPassword(input)
                    },
                    onRequestBiometricVerify = if (canUseBiometric) {
                        { onSuccess, onError ->
                            val hostActivity = activity
                            if (hostActivity == null) {
                                onError(context.getString(R.string.biometric_not_available))
                            } else {
                                biometricHelper.authenticate(
                                    activity = hostActivity,
                                    title = context.getString(R.string.verify_identity),
                                    subtitle = context.getString(R.string.verify_to_delete),
                                    onSuccess = { onSuccess() },
                                    onError = { error -> onError(error) },
                                    onFailed = {}
                                )
                            }
                        }
                    } else {
                        null
                    },
                    onCreateCategoryWithName = { name -> viewModel.addCategory(name) },
                    onCreateBitwardenFolder = { vaultId, name ->
                        coroutineScope.launch {
                            val result = bitwardenRepository.createFolder(vaultId, name)
                            result.exceptionOrNull()?.let { error ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onRenameBitwardenFolder = { vaultId, folderId, newName ->
                        coroutineScope.launch {
                            val result = bitwardenRepository.renameFolder(vaultId, folderId, newName)
                            result.exceptionOrNull()?.let { error ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onDeleteBitwardenFolder = { vaultId, folderId ->
                        coroutineScope.launch {
                            val result = bitwardenRepository.deleteFolder(vaultId, folderId)
                            result.exceptionOrNull()?.let { error ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onRenameCategory = onRenameCategory,
                    onDeleteCategory = onDeleteCategory,
                    onCreateKeePassGroup = { databaseId, parentPath, name ->
                        localKeePassViewModel.createGroup(
                            databaseId = databaseId,
                            groupName = name,
                            parentPath = parentPath
                        ) { result ->
                            result.exceptionOrNull()?.let { error ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onRenameKeePassGroup = { databaseId, groupPath, newName ->
                        localKeePassViewModel.renameGroup(
                            databaseId = databaseId,
                            groupPath = groupPath,
                            newName = newName
                        ) { result ->
                            result.exceptionOrNull()?.let { error ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onDeleteKeePassGroup = { databaseId, groupPath ->
                        localKeePassViewModel.deleteGroup(
                            databaseId = databaseId,
                            groupPath = groupPath
                        ) { result ->
                            result.exceptionOrNull()?.let { error ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
                CategorySelectionUiMode.CHIP_MENU -> Unit
            }
        }

        if (showDisplayOptionsSheet) {
            PasswordDisplayOptionsSheet(
                stackCardMode = stackCardMode,
                groupMode = groupMode,
                passwordCardDisplayMode = passwordCardDisplayMode,
                onDismiss = { onShowDisplayOptionsSheetChange(false) },
                onStackCardModeSelected = { mode ->
                    settingsViewModel.updateStackCardMode(mode.name)
                },
                onGroupModeSelected = { modeKey ->
                    settingsViewModel.updatePasswordGroupMode(modeKey)
                },
                onPasswordCardDisplayModeSelected = { mode ->
                    settingsViewModel.updatePasswordCardDisplayMode(mode)
                }
            )
        }

        if (showCreateCategoryDialog) {
            CreateCategoryDialog(
                visible = true,
                onDismiss = { showCreateCategoryDialog = false },
                categories = categories,
                keepassDatabases = keepassDatabases,
                bitwardenVaults = bitwardenVaults,
                getKeePassGroups = localKeePassViewModel::getGroups,
                onCreateCategoryWithName = { name -> viewModel.addCategory(name) },
                onCreateBitwardenFolder = { vaultId, name ->
                    coroutineScope.launch {
                        val result = bitwardenRepository.createFolder(vaultId, name)
                        result.exceptionOrNull()?.let { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onCreateKeePassGroup = { databaseId, parentPath, name ->
                    localKeePassViewModel.createGroup(
                        databaseId = databaseId,
                        groupName = name,
                        parentPath = parentPath
                    ) { result ->
                        result.exceptionOrNull()?.let { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        }
    }
}
