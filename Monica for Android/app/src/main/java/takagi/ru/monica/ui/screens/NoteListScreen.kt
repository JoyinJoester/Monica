package takagi.ru.monica.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.Category
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.KeePassStorageLocation
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.viewmodel.NoteViewModel
import takagi.ru.monica.viewmodel.NoteDraftStorageTarget
import takagi.ru.monica.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.utils.SettingsManager
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import takagi.ru.monica.ui.components.M3IdentityVerifyDialog
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.components.SyncStatusIcon
import takagi.ru.monica.ui.components.UnifiedCategoryFilterBottomSheet
import takagi.ru.monica.ui.components.UnifiedCategoryFilterSelection
import takagi.ru.monica.ui.components.UnifiedMoveCategoryTarget
import takagi.ru.monica.ui.components.UnifiedMoveToCategoryBottomSheet
import takagi.ru.monica.ui.components.PullActionVisualState
import takagi.ru.monica.ui.components.PullGestureIndicator
import takagi.ru.monica.bitwarden.sync.SyncStatus
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch
import takagi.ru.monica.util.VibrationPatterns
import takagi.ru.monica.utils.SavedCategoryFilterState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteListScreen(
    viewModel: NoteViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateToAddNote: (Long?) -> Unit,
    securityManager: SecurityManager,
    onSelectionModeChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val settings by settingsViewModel.settings.collectAsState()
    val isGridLayout = settings.noteGridLayout
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedNoteIds by remember { mutableStateOf(setOf<Long>()) }
    var isCategorySheetVisible by remember { mutableStateOf(false) }
    var categoryPillBoundsInWindow by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryNameInput by rememberSaveable { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBatchMoveCategoryDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var masterPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    
    // 防止重复点击
    var isNavigating by remember { mutableStateOf(false) }

    LaunchedEffect(isSelectionMode) {
        onSelectionModeChange(isSelectionMode)
    }

    DisposableEffect(Unit) {
        onDispose {
            onSelectionModeChange(false)
        }
    }
    
    val context = LocalContext.current
    val database = remember { PasswordDatabase.getDatabase(context) }
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    val categories by database.categoryDao().getAllCategories().collectAsState(initial = emptyList())
    val keepassDatabases by database.localKeePassDatabaseDao().getAllDatabases().collectAsState(initial = emptyList())
    val bitwardenRepository = remember { BitwardenRepository.getInstance(context) }
    var bitwardenVaults by remember { mutableStateOf<List<BitwardenVault>>(emptyList()) }
    val keePassService = remember {
        takagi.ru.monica.utils.KeePassKdbxService(
            context,
            database.localKeePassDatabaseDao(),
            securityManager
        )
    }
    val keepassGroupFlows = remember {
        mutableMapOf<Long, kotlinx.coroutines.flow.MutableStateFlow<List<takagi.ru.monica.utils.KeePassGroupInfo>>>()
    }
    val getKeePassGroups: (Long) -> kotlinx.coroutines.flow.Flow<List<takagi.ru.monica.utils.KeePassGroupInfo>> = remember {
        { databaseId ->
            val flow = keepassGroupFlows.getOrPut(databaseId) {
                kotlinx.coroutines.flow.MutableStateFlow(emptyList())
            }
            if (flow.value.isEmpty()) {
                scope.launch {
                    flow.value = keePassService.listGroups(databaseId).getOrDefault(emptyList())
                }
            }
            flow
        }
    }
    val biometricHelper = remember { BiometricHelper(context) }
    val canUseBiometric = settings.biometricEnabled && biometricHelper.isBiometricAvailable()
    LaunchedEffect(Unit) {
        bitwardenVaults = bitwardenRepository.getAllVaults()
    }
    
    val notes by viewModel.allNotes.collectAsState(initial = emptyList())
    var selectedCategoryFilter by remember { mutableStateOf<NoteCategoryFilter>(NoteCategoryFilter.All) }
    val savedCategoryFilterState by settingsManager
        .categoryFilterStateFlow(SettingsManager.CategoryFilterScope.NOTE)
        .collectAsState(initial = SavedCategoryFilterState())
    var hasRestoredCategoryFilter by remember { mutableStateOf(false) }

    LaunchedEffect(savedCategoryFilterState, hasRestoredCategoryFilter) {
        if (hasRestoredCategoryFilter) return@LaunchedEffect
        selectedCategoryFilter = decodeNoteCategoryFilter(savedCategoryFilterState)
        hasRestoredCategoryFilter = true
    }

    LaunchedEffect(selectedCategoryFilter) {
        viewModel.setDraftStorageTarget(selectedCategoryFilter.toDraftStorageTarget())
        if (hasRestoredCategoryFilter) {
            settingsManager.updateCategoryFilterState(
                scope = SettingsManager.CategoryFilterScope.NOTE,
                state = encodeNoteCategoryFilter(selectedCategoryFilter)
            )
        }
    }

    val title = when (val filter = selectedCategoryFilter) {
        NoteCategoryFilter.All -> stringResource(R.string.filter_all)
        NoteCategoryFilter.Local -> stringResource(R.string.filter_monica)
        NoteCategoryFilter.Starred -> stringResource(R.string.filter_starred)
        NoteCategoryFilter.Uncategorized -> stringResource(R.string.filter_uncategorized)
        NoteCategoryFilter.LocalStarred -> "${stringResource(R.string.filter_monica)} · ${stringResource(R.string.filter_starred)}"
        NoteCategoryFilter.LocalUncategorized -> "${stringResource(R.string.filter_monica)} · ${stringResource(R.string.filter_uncategorized)}"
        is NoteCategoryFilter.Custom -> categories.find { it.id == filter.categoryId }?.name
            ?: stringResource(R.string.unknown_category)
        is NoteCategoryFilter.BitwardenVault -> "Bitwarden"
        is NoteCategoryFilter.BitwardenFolderFilter -> "Bitwarden"
        is NoteCategoryFilter.BitwardenVaultStarred -> "${stringResource(R.string.filter_bitwarden)} · ${stringResource(R.string.filter_starred)}"
        is NoteCategoryFilter.BitwardenVaultUncategorized -> "${stringResource(R.string.filter_bitwarden)} · ${stringResource(R.string.filter_uncategorized)}"
        is NoteCategoryFilter.KeePassDatabase -> keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"
        is NoteCategoryFilter.KeePassGroupFilter -> filter.groupPath.substringAfterLast('/')
        is NoteCategoryFilter.KeePassDatabaseStarred -> "${keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"} · ${stringResource(R.string.filter_starred)}"
        is NoteCategoryFilter.KeePassDatabaseUncategorized -> "${keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"} · ${stringResource(R.string.filter_uncategorized)}"
    }
    val isBitwardenDatabaseView = when (selectedCategoryFilter) {
        is NoteCategoryFilter.BitwardenVault,
        is NoteCategoryFilter.BitwardenFolderFilter,
        is NoteCategoryFilter.BitwardenVaultStarred,
        is NoteCategoryFilter.BitwardenVaultUncategorized -> true
        else -> false
    }
    
    // 过滤笔记
    val filteredNotes = remember(notes, searchQuery, selectedCategoryFilter) {
        val categoryFiltered = when (val filter = selectedCategoryFilter) {
            NoteCategoryFilter.All -> notes
            NoteCategoryFilter.Local -> notes.filter { it.bitwardenVaultId == null && it.keepassDatabaseId == null }
            NoteCategoryFilter.Starred -> notes.filter { it.isFavorite }
            NoteCategoryFilter.Uncategorized -> notes.filter { it.categoryId == null }
            NoteCategoryFilter.LocalStarred -> notes.filter {
                it.bitwardenVaultId == null && it.keepassDatabaseId == null && it.isFavorite
            }
            NoteCategoryFilter.LocalUncategorized -> notes.filter {
                it.bitwardenVaultId == null && it.keepassDatabaseId == null && it.categoryId == null
            }
            is NoteCategoryFilter.Custom -> notes.filter { it.categoryId == filter.categoryId }
            is NoteCategoryFilter.BitwardenVault -> notes.filter { it.bitwardenVaultId == filter.vaultId }
            is NoteCategoryFilter.BitwardenFolderFilter -> notes.filter { it.bitwardenFolderId == filter.folderId }
            is NoteCategoryFilter.BitwardenVaultStarred -> notes.filter { it.bitwardenVaultId == filter.vaultId && it.isFavorite }
            is NoteCategoryFilter.BitwardenVaultUncategorized -> notes.filter { it.bitwardenVaultId == filter.vaultId && it.bitwardenFolderId == null }
            is NoteCategoryFilter.KeePassDatabase -> notes.filter { it.keepassDatabaseId == filter.databaseId }
            is NoteCategoryFilter.KeePassGroupFilter -> notes.filter {
                it.keepassDatabaseId == filter.databaseId && it.keepassGroupPath == filter.groupPath
            }
            is NoteCategoryFilter.KeePassDatabaseStarred -> notes.filter {
                it.keepassDatabaseId == filter.databaseId && it.isFavorite
            }
            is NoteCategoryFilter.KeePassDatabaseUncategorized -> notes.filter {
                it.keepassDatabaseId == filter.databaseId && it.keepassGroupPath.isNullOrBlank()
            }
        }
        if (searchQuery.isBlank()) {
            categoryFiltered
        } else {
            categoryFiltered.filter {
                it.notes.contains(searchQuery, ignoreCase = true) ||
                    it.title.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // 删除实际执行
    fun performDelete() {
        val notesToDelete = notes.filter { it.id in selectedNoteIds }
        viewModel.deleteNotes(notesToDelete)
        isSelectionMode = false
        selectedNoteIds = emptySet()
        showDeleteDialog = false
        showPasswordDialog = false
        masterPassword = ""
        passwordError = false
    }

    fun performBatchMove(target: UnifiedMoveCategoryTarget) {
        scope.launch {
            val selectedItems = notes.filter { selectedNoteIds.contains(it.id) }
            var movedCount = 0
            var failedCount = 0

            selectedItems.forEach { item ->
                val targetCategoryId = when (target) {
                    UnifiedMoveCategoryTarget.Uncategorized -> null
                    is UnifiedMoveCategoryTarget.MonicaCategory -> target.categoryId
                    else -> item.categoryId
                }
                val targetKeepassDatabaseId = when (target) {
                    UnifiedMoveCategoryTarget.Uncategorized -> null
                    is UnifiedMoveCategoryTarget.MonicaCategory -> null
                    is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> null
                    is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> null
                    is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> target.databaseId
                    is UnifiedMoveCategoryTarget.KeePassGroupTarget -> target.databaseId
                }
                val targetKeepassGroupPath = when (target) {
                    is UnifiedMoveCategoryTarget.KeePassGroupTarget -> target.groupPath
                    is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> null
                    else -> null
                }
                val targetBitwardenVaultId = when (target) {
                    is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> target.vaultId
                    is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> target.vaultId
                    else -> null
                }
                val targetBitwardenFolderId = when (target) {
                    is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> target.folderId
                    else -> null
                }

                val moved = viewModel.moveNoteToStorage(
                    item = item,
                    categoryId = targetCategoryId,
                    keepassDatabaseId = targetKeepassDatabaseId,
                    keepassGroupPath = targetKeepassGroupPath,
                    bitwardenVaultId = targetBitwardenVaultId,
                    bitwardenFolderId = targetBitwardenFolderId
                )
                if (moved) movedCount++ else failedCount++
            }

            val baseMessage = context.getString(R.string.selected_items, movedCount)
            val toastMessage = if (failedCount > 0) "$baseMessage，失败$failedCount" else baseMessage
            android.widget.Toast.makeText(context, toastMessage, android.widget.Toast.LENGTH_SHORT).show()

            showBatchMoveCategoryDialog = false
            isSelectionMode = false
            selectedNoteIds = emptySet()
        }
    }

    Scaffold(
        topBar = {
            // M3E 风格顶栏（保持与其他页面一致）
            ExpressiveTopBar(
                title = title,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                isSearchExpanded = isSearchExpanded,
                onSearchExpandedChange = { isSearchExpanded = it },
                searchHint = stringResource(R.string.search),
                onActionPillBoundsChanged = { bounds -> categoryPillBoundsInWindow = bounds },
                actions = {
                    IconButton(onClick = { isCategorySheetVisible = true }) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = stringResource(R.string.category),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // 布局切换按钮
                    IconButton(onClick = { settingsViewModel.updateNoteGridLayout(!isGridLayout) }) {
                        Icon(
                            imageVector = if (isGridLayout) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = if (isGridLayout) {
                                stringResource(R.string.switch_to_list)
                            } else {
                                stringResource(R.string.switch_to_grid)
                            },
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // 搜索按钮
                    IconButton(onClick = { isSearchExpanded = true }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )

            val selectedUnifiedFilter = when (val filter = selectedCategoryFilter) {
                NoteCategoryFilter.All -> UnifiedCategoryFilterSelection.All
                NoteCategoryFilter.Local -> UnifiedCategoryFilterSelection.Local
                NoteCategoryFilter.Starred -> UnifiedCategoryFilterSelection.Starred
                NoteCategoryFilter.Uncategorized -> UnifiedCategoryFilterSelection.Uncategorized
                NoteCategoryFilter.LocalStarred -> UnifiedCategoryFilterSelection.LocalStarred
                NoteCategoryFilter.LocalUncategorized -> UnifiedCategoryFilterSelection.LocalUncategorized
                is NoteCategoryFilter.Custom -> UnifiedCategoryFilterSelection.Custom(filter.categoryId)
                is NoteCategoryFilter.BitwardenVault -> UnifiedCategoryFilterSelection.BitwardenVaultFilter(filter.vaultId)
                is NoteCategoryFilter.BitwardenFolderFilter -> UnifiedCategoryFilterSelection.BitwardenFolderFilter(filter.vaultId, filter.folderId)
                is NoteCategoryFilter.BitwardenVaultStarred -> UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter(filter.vaultId)
                is NoteCategoryFilter.BitwardenVaultUncategorized -> UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter(filter.vaultId)
                is NoteCategoryFilter.KeePassDatabase -> UnifiedCategoryFilterSelection.KeePassDatabaseFilter(filter.databaseId)
                is NoteCategoryFilter.KeePassGroupFilter -> UnifiedCategoryFilterSelection.KeePassGroupFilter(filter.databaseId, filter.groupPath)
                is NoteCategoryFilter.KeePassDatabaseStarred -> UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter(filter.databaseId)
                is NoteCategoryFilter.KeePassDatabaseUncategorized -> UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter(filter.databaseId)
            }
            UnifiedCategoryFilterBottomSheet(
                visible = isCategorySheetVisible,
                onDismiss = { isCategorySheetVisible = false },
                selected = selectedUnifiedFilter,
                onSelect = { selection ->
                    selectedCategoryFilter = when (selection) {
                        is UnifiedCategoryFilterSelection.All -> NoteCategoryFilter.All
                        is UnifiedCategoryFilterSelection.Local -> NoteCategoryFilter.Local
                        is UnifiedCategoryFilterSelection.Starred -> NoteCategoryFilter.Starred
                        is UnifiedCategoryFilterSelection.Uncategorized -> NoteCategoryFilter.Uncategorized
                        is UnifiedCategoryFilterSelection.LocalStarred -> NoteCategoryFilter.LocalStarred
                        is UnifiedCategoryFilterSelection.LocalUncategorized -> NoteCategoryFilter.LocalUncategorized
                        is UnifiedCategoryFilterSelection.Custom -> NoteCategoryFilter.Custom(selection.categoryId)
                        is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> NoteCategoryFilter.BitwardenVault(selection.vaultId)
                        is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> NoteCategoryFilter.BitwardenFolderFilter(selection.folderId, selection.vaultId)
                        is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> NoteCategoryFilter.BitwardenVaultStarred(selection.vaultId)
                        is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> NoteCategoryFilter.BitwardenVaultUncategorized(selection.vaultId)
                        is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> NoteCategoryFilter.KeePassDatabase(selection.databaseId)
                        is UnifiedCategoryFilterSelection.KeePassGroupFilter -> NoteCategoryFilter.KeePassGroupFilter(selection.databaseId, selection.groupPath)
                        is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> NoteCategoryFilter.KeePassDatabaseStarred(selection.databaseId)
                        is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> NoteCategoryFilter.KeePassDatabaseUncategorized(selection.databaseId)
                    }
                    when (selection) {
                        is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> viewModel.syncKeePassNotes(selection.databaseId)
                        is UnifiedCategoryFilterSelection.KeePassGroupFilter -> viewModel.syncKeePassNotes(selection.databaseId)
                        else -> Unit
                    }
                },
                launchAnchorBounds = categoryPillBoundsInWindow,
                categories = categories,
                keepassDatabases = keepassDatabases,
                bitwardenVaults = bitwardenVaults,
                getBitwardenFolders = { vaultId -> database.bitwardenFolderDao().getFoldersByVaultFlow(vaultId) },
                getKeePassGroups = getKeePassGroups,
                onCreateCategory = { showAddCategoryDialog = true },
                onVerifyMasterPassword = { input ->
                    securityManager.verifyMasterPassword(input)
                },
                onCreateCategoryWithName = { name ->
                    scope.launch {
                        database.categoryDao().insert(Category(name = name))
                    }
                },
                onCreateBitwardenFolder = { vaultId, name ->
                    scope.launch {
                        bitwardenRepository.createFolder(vaultId, name)
                    }
                },
                onRenameBitwardenFolder = { vaultId, folderId, newName ->
                    scope.launch {
                        bitwardenRepository.renameFolder(vaultId, folderId, newName)
                    }
                },
                onDeleteBitwardenFolder = { vaultId, folderId ->
                    scope.launch {
                        bitwardenRepository.deleteFolder(vaultId, folderId)
                    }
                },
                onCreateKeePassGroup = { databaseId, parentPath, name ->
                    scope.launch {
                        val result = keePassService.createGroup(
                            databaseId = databaseId,
                            groupName = name,
                            parentPath = parentPath
                        )
                        if (result.isSuccess) {
                            val flow = keepassGroupFlows.getOrPut(databaseId) {
                                kotlinx.coroutines.flow.MutableStateFlow(emptyList())
                            }
                            flow.value = keePassService.listGroups(databaseId).getOrDefault(emptyList())
                        }
                    }
                },
                onRenameKeePassGroup = { databaseId, groupPath, newName ->
                    scope.launch {
                        val result = keePassService.renameGroup(
                            databaseId = databaseId,
                            groupPath = groupPath,
                            newName = newName
                        )
                        if (result.isSuccess) {
                            val flow = keepassGroupFlows.getOrPut(databaseId) {
                                kotlinx.coroutines.flow.MutableStateFlow(emptyList())
                            }
                            flow.value = keePassService.listGroups(databaseId).getOrDefault(emptyList())
                        }
                    }
                },
                onDeleteKeePassGroup = { databaseId, groupPath ->
                    scope.launch {
                        val result = keePassService.deleteGroup(
                            databaseId = databaseId,
                            groupPath = groupPath
                        )
                        if (result.isSuccess) {
                            val flow = keepassGroupFlows.getOrPut(databaseId) {
                                kotlinx.coroutines.flow.MutableStateFlow(emptyList())
                            }
                            flow.value = keePassService.listGroups(databaseId).getOrDefault(emptyList())
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    NoteSelectionActionBar(
                        modifier = Modifier.wrapContentWidth(),
                        selectedCount = selectedNoteIds.size,
                        onExit = {
                            isSelectionMode = false
                            selectedNoteIds = emptySet()
                        },
                        onSelectAll = {
                            selectedNoteIds = if (selectedNoteIds.size == filteredNotes.size) {
                                emptySet()
                            } else {
                                filteredNotes.map { it.id }.toSet()
                            }
                        },
                        onMoveToCategory = { showBatchMoveCategoryDialog = true },
                        onDelete = { showDeleteDialog = true }
                    )
                }
            }
        },
        floatingActionButton = {} // FAB moved to SwipeableAddFab in SimpleMainScreen
    ) { paddingValues ->
        // 重置导航状态
        LaunchedEffect(Unit) {
            isNavigating = false
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(R.string.delete)) },
                text = { Text(stringResource(R.string.notes_delete_selected_confirm, selectedNoteIds.size)) },
                confirmButton = {
                    TextButton(onClick = { 
                        showDeleteDialog = false
                        showPasswordDialog = true
                    }) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showAddCategoryDialog) {
            AlertDialog(
                onDismissRequest = { showAddCategoryDialog = false },
                title = { Text(stringResource(R.string.new_category)) },
                text = {
                    OutlinedTextField(
                        value = categoryNameInput,
                        onValueChange = { categoryNameInput = it },
                        label = { Text(stringResource(R.string.category_name)) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val name = categoryNameInput.trim()
                            if (name.isBlank()) return@TextButton
                            scope.launch {
                                database.categoryDao().insert(Category(name = name))
                                categoryNameInput = ""
                                showAddCategoryDialog = false
                            }
                        }
                    ) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showAddCategoryDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showPasswordDialog) {
            val activity = context as? FragmentActivity
            val biometricAction = if (activity != null && canUseBiometric) {
                {
                    biometricHelper.authenticate(
                        activity = activity,
                        title = context.getString(R.string.verify_identity),
                        subtitle = context.getString(R.string.verify_to_delete),
                        onSuccess = { performDelete() },
                        onError = { error ->
                            android.widget.Toast.makeText(
                                context,
                                error,
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        onFailed = {}
                    )
                }
            } else {
                null
            }
            M3IdentityVerifyDialog(
                title = stringResource(R.string.verify_identity),
                message = stringResource(R.string.notes_delete_selected_confirm, selectedNoteIds.size),
                passwordValue = masterPassword,
                onPasswordChange = {
                    masterPassword = it
                    passwordError = false
                },
                onDismiss = {
                    showPasswordDialog = false
                    masterPassword = ""
                    passwordError = false
                },
                onConfirm = {
                    if (securityManager.verifyMasterPassword(masterPassword)) {
                        performDelete()
                    } else {
                        passwordError = true
                    }
                },
                confirmText = stringResource(R.string.delete),
                destructiveConfirm = true,
                isPasswordError = passwordError,
                passwordErrorText = stringResource(R.string.current_password_incorrect),
                onBiometricClick = biometricAction,
                biometricHintText = if (biometricAction == null) {
                    context.getString(R.string.biometric_not_available)
                } else {
                    null
                }
            )
        }

        UnifiedMoveToCategoryBottomSheet(
            visible = showBatchMoveCategoryDialog,
            onDismiss = { showBatchMoveCategoryDialog = false },
            categories = categories,
            keepassDatabases = keepassDatabases,
            bitwardenVaults = bitwardenVaults,
            getBitwardenFolders = { vaultId -> database.bitwardenFolderDao().getFoldersByVaultFlow(vaultId) },
            getKeePassGroups = getKeePassGroups,
            onTargetSelected = ::performBatchMove
        )

        NoteListContent(
            notes = filteredNotes,
            isGridLayout = isGridLayout,
            isSearchExpanded = isSearchExpanded,
            onRequestExpandSearch = { isSearchExpanded = true },
            isBitwardenDatabaseView = isBitwardenDatabaseView,
            bitwardenRepository = bitwardenRepository,
            isSelectionMode = isSelectionMode,
            selectedNoteIds = selectedNoteIds,
            onNoteClick = { noteId ->
                if (isSelectionMode) {
                    selectedNoteIds = if (selectedNoteIds.contains(noteId)) {
                        selectedNoteIds - noteId
                    } else {
                        selectedNoteIds + noteId
                    }
                    if (selectedNoteIds.isEmpty()) {
                        isSelectionMode = false
                    }
                } else {
                    onNavigateToAddNote(noteId)
                }
            },
            onNoteLongClick = { noteId ->
                if (!isSelectionMode) {
                    isSelectionMode = true
                    selectedNoteIds = setOf(noteId)
                }
            },
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteListContent(
    notes: List<SecureItem>,
    isGridLayout: Boolean,
    isSearchExpanded: Boolean,
    onRequestExpandSearch: () -> Unit,
    isBitwardenDatabaseView: Boolean,
    bitwardenRepository: BitwardenRepository,
    isSelectionMode: Boolean,
    selectedNoteIds: Set<Long>,
    onNoteClick: (Long) -> Unit,
    onNoteLongClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val gridState = rememberLazyStaggeredGridState()
    var currentOffset by remember { mutableFloatStateOf(0f) }
    val searchTriggerDistance = remember(density, isBitwardenDatabaseView) {
        with(density) { (if (isBitwardenDatabaseView) 40.dp else 72.dp).toPx() }
    }
    val syncTriggerDistance = remember(density) { with(density) { 72.dp.toPx() } }
    val maxDragDistance = remember(density) { with(density) { 100.dp.toPx() } }
    val syncHoldMillis = 500L
    var hasVibrated by remember { mutableStateOf(false) }
    var hasSyncStageVibrated by remember { mutableStateOf(false) }
    var syncHintArmed by remember { mutableStateOf(false) }
    var isBitwardenSyncing by remember { mutableStateOf(false) }
    var lockPullUntilSyncFinished by remember { mutableStateOf(false) }
    var canRunBitwardenSync by remember { mutableStateOf(false) }
    var showSyncFeedback by remember { mutableStateOf(false) }
    var syncFeedbackMessage by remember { mutableStateOf("") }
    var syncFeedbackIsSuccess by remember { mutableStateOf(false) }
    var canTriggerPullToSearch by remember { mutableStateOf(false) }
    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }

    suspend fun resolveSyncableVaultId(): Long? {
        val activeVault = bitwardenRepository.getActiveVault() ?: run {
            canRunBitwardenSync = false
            return null
        }
        val unlocked = bitwardenRepository.isVaultUnlocked(activeVault.id)
        canRunBitwardenSync = unlocked
        return if (unlocked) activeVault.id else null
    }

    fun vibratePullThreshold(isSyncStage: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (isSyncStage && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                vibrator?.vibrate(
                    android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_DOUBLE_CLICK)
                )
            } else {
                vibrator?.vibrate(android.os.VibrationEffect.createWaveform(VibrationPatterns.TICK, -1))
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(if (isSyncStage) 36 else 20)
        }
    }

    fun updatePullThresholdHaptics(oldOffset: Float, newOffset: Float) {
        if (oldOffset < searchTriggerDistance && newOffset >= searchTriggerDistance && !hasVibrated) {
            hasVibrated = true
            vibratePullThreshold(isSyncStage = false)
        } else if (newOffset < searchTriggerDistance) {
            hasVibrated = false
        }

        if (!isBitwardenDatabaseView) {
            hasSyncStageVibrated = false
            return
        }

        if (oldOffset < syncTriggerDistance && newOffset >= syncTriggerDistance && !hasSyncStageVibrated) {
            hasSyncStageVibrated = true
            vibratePullThreshold(isSyncStage = true)
        } else if (newOffset < syncTriggerDistance) {
            hasSyncStageVibrated = false
        }
    }

    suspend fun collapsePullOffsetSmoothly() {
        if (currentOffset <= 0.5f) {
            currentOffset = 0f
            return
        }
        Animatable(currentOffset).animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = 180,
                easing = androidx.compose.animation.core.LinearOutSlowInEasing
            )
        ) {
            currentOffset = value
        }
    }

    fun onPullRelease(): Boolean {
        if (isBitwardenDatabaseView && syncHintArmed && !isBitwardenSyncing) {
            syncHintArmed = false
            isBitwardenSyncing = true
            lockPullUntilSyncFinished = true
            currentOffset = syncTriggerDistance
            scope.launch {
                val vaultId = resolveSyncableVaultId()
                if (vaultId == null) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.pull_sync_requires_bitwarden_login),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    isBitwardenSyncing = false
                    lockPullUntilSyncFinished = false
                    hasVibrated = false
                    hasSyncStageVibrated = false
                    collapsePullOffsetSmoothly()
                    return@launch
                }

                val syncResult = bitwardenRepository.sync(vaultId)
                when (syncResult) {
                    is BitwardenRepository.SyncResult.Success -> {
                        syncFeedbackIsSuccess = true
                        syncFeedbackMessage = context.getString(R.string.pull_sync_success)
                        showSyncFeedback = true
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.pull_sync_success),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    is BitwardenRepository.SyncResult.Error -> {
                        syncFeedbackIsSuccess = false
                        syncFeedbackMessage = context.getString(R.string.sync_status_failed_full)
                        showSyncFeedback = true
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.sync_status_failed_full) + ": " + syncResult.message,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    is BitwardenRepository.SyncResult.EmptyVaultBlocked -> {
                        syncFeedbackIsSuccess = false
                        syncFeedbackMessage = context.getString(R.string.sync_status_failed_full)
                        showSyncFeedback = true
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.sync_status_failed_full) + ": " + syncResult.reason,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                isBitwardenSyncing = false
                lockPullUntilSyncFinished = false
                hasVibrated = false
                hasSyncStageVibrated = false
                collapsePullOffsetSmoothly()
                kotlinx.coroutines.delay(1400)
                showSyncFeedback = false
            }
            return true
        }

        if (currentOffset >= searchTriggerDistance) {
            onRequestExpandSearch()
            hasVibrated = false
        }
        return false
    }

    LaunchedEffect(isBitwardenDatabaseView) {
        if (isBitwardenDatabaseView) {
            resolveSyncableVaultId()
        } else {
            canRunBitwardenSync = false
            syncHintArmed = false
            isBitwardenSyncing = false
            lockPullUntilSyncFinished = false
            showSyncFeedback = false
            currentOffset = 0f
            hasVibrated = false
            hasSyncStageVibrated = false
        }
    }

    LaunchedEffect(currentOffset >= syncTriggerDistance, isBitwardenDatabaseView, isBitwardenSyncing) {
        if (isBitwardenDatabaseView && currentOffset >= syncTriggerDistance && !isBitwardenSyncing) {
            resolveSyncableVaultId()
        }
    }

    LaunchedEffect(currentOffset, isBitwardenDatabaseView, canRunBitwardenSync, isBitwardenSyncing) {
        if (isBitwardenDatabaseView && currentOffset >= syncTriggerDistance && canRunBitwardenSync && !isBitwardenSyncing) {
            kotlinx.coroutines.delay(syncHoldMillis)
            if (isBitwardenDatabaseView && currentOffset >= syncTriggerDistance && canRunBitwardenSync && !isBitwardenSyncing) {
                syncHintArmed = true
            }
        } else {
            syncHintArmed = false
        }
    }

    // NestedScrollConnection 处理下拉搜索手势
    val nestedScrollConnection = remember(isBitwardenDatabaseView) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 如果正在向上滑动且有偏移量，先消耗偏移量
                if (lockPullUntilSyncFinished) {
                    return available
                }
                if (currentOffset > 0 && available.y < 0) {
                    val newOffset = (currentOffset + available.y).coerceAtLeast(0f)
                    val consumed = currentOffset - newOffset
                    currentOffset = newOffset
                    return Offset(0f, -consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (lockPullUntilSyncFinished) {
                    return available
                }
                if (!isSearchExpanded && available.y > 0 && canTriggerPullToSearch) {
                    if (source == NestedScrollSource.UserInput) {
                        val delta = available.y * 0.5f
                        val newOffset = (currentOffset + delta).coerceAtMost(maxDragDistance)
                        val oldOffset = currentOffset
                        currentOffset = newOffset
                        updatePullThresholdHaptics(oldOffset = oldOffset, newOffset = newOffset)
                        return available
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val syncStarted = onPullRelease()
                if (!syncStarted && !lockPullUntilSyncFinished) {
                    collapsePullOffsetSmoothly()
                }
                return Velocity.Zero
            }
        }
    }

    // 空状态下的下拉手势处理
    val emptyStateGestureModifier = Modifier
        .offset { IntOffset(0, currentOffset.toInt()) }
        .pointerInput(isSearchExpanded) {
            detectVerticalDragGestures(
                onVerticalDrag = { _, dragAmount ->
                    if (!isSearchExpanded && dragAmount > 0f) {
                        val newOffset = (currentOffset + dragAmount * 0.5f).coerceAtMost(maxDragDistance)
                        val oldOffset = currentOffset
                        currentOffset = newOffset
                        updatePullThresholdHaptics(oldOffset = oldOffset, newOffset = newOffset)
                    }
                },
                onDragEnd = {
                    val syncStarted = onPullRelease()
                    if (!syncStarted && !lockPullUntilSyncFinished) {
                        scope.launch { collapsePullOffsetSmoothly() }
                    }
                },
                onDragCancel = {
                    if (!lockPullUntilSyncFinished) {
                        scope.launch { collapsePullOffsetSmoothly() }
                    }
                }
            )
        }

    val searchProgress = (currentOffset / searchTriggerDistance).coerceIn(0f, 1f)
    val syncProgress = ((currentOffset - searchTriggerDistance) / (syncTriggerDistance - searchTriggerDistance))
        .coerceIn(0f, 1f)
    val pullVisualState = when {
        isBitwardenDatabaseView && isBitwardenSyncing -> PullActionVisualState.SYNCING
        isBitwardenDatabaseView && showSyncFeedback -> PullActionVisualState.SYNC_DONE
        isBitwardenDatabaseView && syncHintArmed -> PullActionVisualState.SYNC_READY
        currentOffset >= searchTriggerDistance -> PullActionVisualState.SEARCH_READY
        else -> PullActionVisualState.IDLE
    }
    val pullHintText = when (pullVisualState) {
        PullActionVisualState.SYNCING -> stringResource(R.string.pull_syncing_bitwarden)
        PullActionVisualState.SYNC_DONE -> syncFeedbackMessage.ifBlank {
            if (syncFeedbackIsSuccess) {
                stringResource(R.string.pull_sync_success)
            } else {
                stringResource(R.string.sync_status_failed_full)
            }
        }
        PullActionVisualState.SYNC_READY -> stringResource(R.string.pull_release_to_sync_bitwarden)
        PullActionVisualState.SEARCH_READY -> if (isBitwardenDatabaseView) {
            stringResource(R.string.pull_release_to_search)
        } else {
            null
        }
        PullActionVisualState.IDLE -> null
    }
    val shouldPinIndicator = isBitwardenDatabaseView && (
        syncHintArmed || isBitwardenSyncing || showSyncFeedback
    )
    val revealHeightTarget = with(density) {
        if (isBitwardenDatabaseView) {
            val pullHeight = currentOffset.toDp().coerceIn(0.dp, 112.dp)
            if (shouldPinIndicator) {
                maxOf(pullHeight, 92.dp)
            } else {
                pullHeight
            }
        } else {
            0.dp
        }
    }
    val revealHeight by animateDpAsState(
        targetValue = revealHeightTarget,
        animationSpec = tween(durationMillis = 220),
        label = "note_pull_reveal_height"
    )
    val showPullIndicator = pullHintText != null && revealHeight > 0.5.dp
    val contentPullOffset = if (isBitwardenDatabaseView) {
        (currentOffset * 0.28f).toInt()
    } else {
        currentOffset.toInt()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(revealHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (showPullIndicator) {
                PullGestureIndicator(
                    state = pullVisualState,
                    searchProgress = searchProgress,
                    syncProgress = syncProgress,
                    text = pullHintText ?: "",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp)
                )
            }
        }
        if (showPullIndicator) {
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .offset { IntOffset(0, contentPullOffset) }
                    .then(emptyStateGestureModifier),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Note,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.no_results),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            if (isGridLayout) {
                // 瀑布流网格布局
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, contentPullOffset) }
                        .nestedScroll(nestedScrollConnection)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                val isAtTop = gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
                                canTriggerPullToSearch = isAtTop
                            }
                        },
                    state = gridState
                ) {
                    items(notes, key = { it.id }) { note ->
                        ExpressiveNoteCard(
                            note = note,
                            isSelected = selectedNoteIds.contains(note.id),
                            isGridMode = true,
                            onClick = { onNoteClick(note.id) },
                            onLongClick = { onNoteLongClick(note.id) }
                        )
                    }
                }
            } else {
                // 单列列表布局
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, contentPullOffset) }
                        .nestedScroll(nestedScrollConnection)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                                canTriggerPullToSearch = isAtTop
                            }
                        },
                    state = listState
                ) {
                    items(notes, key = { it.id }) { note ->
                        ExpressiveNoteCard(
                            note = note,
                            isSelected = selectedNoteIds.contains(note.id),
                            isGridMode = false,
                            onClick = { onNoteClick(note.id) },
                            onLongClick = { onNoteLongClick(note.id) }
                        )
                    }
                    // 底部留白，防止被FAB遮挡
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun NoteSelectionActionBar(
    modifier: Modifier = Modifier,
    selectedCount: Int,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onMoveToCategory: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(
                    text = selectedCount.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            NoteActionIcon(
                icon = Icons.Outlined.CheckCircle,
                contentDescription = stringResource(id = R.string.select_all),
                onClick = onSelectAll
            )

            NoteActionIcon(
                icon = Icons.Default.Folder,
                contentDescription = stringResource(id = R.string.move_to_category),
                onClick = onMoveToCategory
            )

            NoteActionIcon(
                icon = Icons.Default.Delete,
                contentDescription = stringResource(id = R.string.delete),
                onClick = onDelete
            )

            Spacer(modifier = Modifier.width(4.dp))

            NoteActionIcon(
                icon = Icons.Default.Close,
                contentDescription = stringResource(id = R.string.close),
                onClick = onExit
            )
        }
    }
}

@Composable
private fun NoteActionIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * M3E 风格的笔记卡片
 * 更具表达力的设计，包含图标、更好的排版和视觉层次
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpressiveNoteCard(
    note: SecureItem,
    isSelected: Boolean,
    isGridMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val hasImageAttachment = remember(note.imagePaths) {
        hasNoteImageAttachment(note.imagePaths)
    }

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    
    val secondaryContentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 头部：图标 + 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 笔记图标背景
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 标题
                Text(
                    text = note.title.ifEmpty { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (isGridMode) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor,
                    modifier = Modifier.weight(1f)
                )

                if (hasImageAttachment) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = stringResource(R.string.note_has_image),
                                modifier = Modifier.size(12.dp),
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.section_photos),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                        }
                    }
                }
            }
            
            // 内容预览
            if (note.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = note.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (isGridMode) 6 else 3,
                    overflow = TextOverflow.Ellipsis,
                    color = secondaryContentColor,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
            }
            
            // 底部：日期 + 同步状态 + 安全标识
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryContentColor.copy(alpha = 0.8f)
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Bitwarden 同步状态指示器
                    if (note.bitwardenVaultId != null) {
                        val syncStatus = when (note.syncStatus) {
                            "PENDING" -> SyncStatus.PENDING
                            "SYNCING" -> SyncStatus.SYNCING
                            "SYNCED" -> SyncStatus.SYNCED
                            "FAILED" -> SyncStatus.FAILED
                            "CONFLICT" -> SyncStatus.CONFLICT
                            else -> if (note.bitwardenLocalModified) SyncStatus.PENDING else SyncStatus.SYNCED
                        }
                        SyncStatusIcon(
                            status = syncStatus,
                            size = 14.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    // 安全标识小图标
                    if (hasImageAttachment) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = stringResource(R.string.note_has_image),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // 安全标识小图标
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = stringResource(R.string.encrypted_storage),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// 保留旧的 NoteCard 以防其他地方引用（未来可删除）
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
@Deprecated("Use ExpressiveNoteCard instead", ReplaceWith("ExpressiveNoteCard"))
fun NoteCard(
    note: SecureItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ExpressiveNoteCard(
        note = note,
        isSelected = isSelected,
        isGridMode = true,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

private fun hasNoteImageAttachment(imagePaths: String): Boolean {
    if (imagePaths.isBlank()) return false
    return try {
        Json.decodeFromString<List<String>>(imagePaths).any { it.isNotBlank() }
    } catch (_: Exception) {
        imagePaths.isNotBlank()
    }
}

private sealed interface NoteCategoryFilter {
    data object All : NoteCategoryFilter
    data object Local : NoteCategoryFilter
    data object Starred : NoteCategoryFilter
    data object Uncategorized : NoteCategoryFilter
    data object LocalStarred : NoteCategoryFilter
    data object LocalUncategorized : NoteCategoryFilter
    data class Custom(val categoryId: Long) : NoteCategoryFilter
    data class BitwardenVault(val vaultId: Long) : NoteCategoryFilter
    data class BitwardenFolderFilter(val folderId: String, val vaultId: Long) : NoteCategoryFilter
    data class BitwardenVaultStarred(val vaultId: Long) : NoteCategoryFilter
    data class BitwardenVaultUncategorized(val vaultId: Long) : NoteCategoryFilter
    data class KeePassDatabase(val databaseId: Long) : NoteCategoryFilter
    data class KeePassGroupFilter(val databaseId: Long, val groupPath: String) : NoteCategoryFilter
    data class KeePassDatabaseStarred(val databaseId: Long) : NoteCategoryFilter
    data class KeePassDatabaseUncategorized(val databaseId: Long) : NoteCategoryFilter
}

private fun NoteCategoryFilter.toDraftStorageTarget(): NoteDraftStorageTarget = when (this) {
    NoteCategoryFilter.All,
    NoteCategoryFilter.Local,
    NoteCategoryFilter.Starred,
    NoteCategoryFilter.Uncategorized,
    NoteCategoryFilter.LocalStarred,
    NoteCategoryFilter.LocalUncategorized -> NoteDraftStorageTarget()
    is NoteCategoryFilter.Custom -> NoteDraftStorageTarget(categoryId = categoryId)
    is NoteCategoryFilter.BitwardenVault -> NoteDraftStorageTarget(bitwardenVaultId = vaultId)
    is NoteCategoryFilter.BitwardenFolderFilter -> NoteDraftStorageTarget(
        bitwardenVaultId = vaultId,
        bitwardenFolderId = folderId
    )
    is NoteCategoryFilter.BitwardenVaultStarred -> NoteDraftStorageTarget(bitwardenVaultId = vaultId)
    is NoteCategoryFilter.BitwardenVaultUncategorized -> NoteDraftStorageTarget(bitwardenVaultId = vaultId)
    is NoteCategoryFilter.KeePassDatabase -> NoteDraftStorageTarget(keepassDatabaseId = databaseId)
    is NoteCategoryFilter.KeePassGroupFilter -> NoteDraftStorageTarget(keepassDatabaseId = databaseId)
    is NoteCategoryFilter.KeePassDatabaseStarred -> NoteDraftStorageTarget(keepassDatabaseId = databaseId)
    is NoteCategoryFilter.KeePassDatabaseUncategorized -> NoteDraftStorageTarget(keepassDatabaseId = databaseId)
}

private fun encodeNoteCategoryFilter(filter: NoteCategoryFilter): SavedCategoryFilterState = when (filter) {
    NoteCategoryFilter.All -> SavedCategoryFilterState(type = "all")
    NoteCategoryFilter.Local -> SavedCategoryFilterState(type = "local")
    NoteCategoryFilter.Starred -> SavedCategoryFilterState(type = "starred")
    NoteCategoryFilter.Uncategorized -> SavedCategoryFilterState(type = "uncategorized")
    NoteCategoryFilter.LocalStarred -> SavedCategoryFilterState(type = "local_starred")
    NoteCategoryFilter.LocalUncategorized -> SavedCategoryFilterState(type = "local_uncategorized")
    is NoteCategoryFilter.Custom -> SavedCategoryFilterState(type = "custom", primaryId = filter.categoryId)
    is NoteCategoryFilter.BitwardenVault -> SavedCategoryFilterState(type = "bitwarden_vault", primaryId = filter.vaultId)
    is NoteCategoryFilter.BitwardenFolderFilter -> SavedCategoryFilterState(type = "bitwarden_folder", primaryId = filter.vaultId, text = filter.folderId)
    is NoteCategoryFilter.BitwardenVaultStarred -> SavedCategoryFilterState(type = "bitwarden_vault_starred", primaryId = filter.vaultId)
    is NoteCategoryFilter.BitwardenVaultUncategorized -> SavedCategoryFilterState(type = "bitwarden_vault_uncategorized", primaryId = filter.vaultId)
    is NoteCategoryFilter.KeePassDatabase -> SavedCategoryFilterState(type = "keepass_database", primaryId = filter.databaseId)
    is NoteCategoryFilter.KeePassGroupFilter -> SavedCategoryFilterState(type = "keepass_group", primaryId = filter.databaseId, text = filter.groupPath)
    is NoteCategoryFilter.KeePassDatabaseStarred -> SavedCategoryFilterState(type = "keepass_database_starred", primaryId = filter.databaseId)
    is NoteCategoryFilter.KeePassDatabaseUncategorized -> SavedCategoryFilterState(type = "keepass_database_uncategorized", primaryId = filter.databaseId)
}

private fun decodeNoteCategoryFilter(state: SavedCategoryFilterState): NoteCategoryFilter {
    return when (state.type) {
        "all" -> NoteCategoryFilter.All
        "local" -> NoteCategoryFilter.Local
        "starred" -> NoteCategoryFilter.Starred
        "uncategorized" -> NoteCategoryFilter.Uncategorized
        "local_starred" -> NoteCategoryFilter.LocalStarred
        "local_uncategorized" -> NoteCategoryFilter.LocalUncategorized
        "custom" -> state.primaryId?.let { NoteCategoryFilter.Custom(it) } ?: NoteCategoryFilter.All
        "bitwarden_vault" -> state.primaryId?.let { NoteCategoryFilter.BitwardenVault(it) } ?: NoteCategoryFilter.All
        "bitwarden_folder" -> {
            val vaultId = state.primaryId
            val folderId = state.text
            if (vaultId != null && !folderId.isNullOrBlank()) NoteCategoryFilter.BitwardenFolderFilter(folderId, vaultId) else NoteCategoryFilter.All
        }
        "bitwarden_vault_starred" -> state.primaryId?.let { NoteCategoryFilter.BitwardenVaultStarred(it) } ?: NoteCategoryFilter.All
        "bitwarden_vault_uncategorized" -> state.primaryId?.let { NoteCategoryFilter.BitwardenVaultUncategorized(it) } ?: NoteCategoryFilter.All
        "keepass_database" -> state.primaryId?.let { NoteCategoryFilter.KeePassDatabase(it) } ?: NoteCategoryFilter.All
        "keepass_group" -> {
            val databaseId = state.primaryId
            val groupPath = state.text
            if (databaseId != null && !groupPath.isNullOrBlank()) NoteCategoryFilter.KeePassGroupFilter(databaseId, groupPath) else NoteCategoryFilter.All
        }
        "keepass_database_starred" -> state.primaryId?.let { NoteCategoryFilter.KeePassDatabaseStarred(it) } ?: NoteCategoryFilter.All
        "keepass_database_uncategorized" -> state.primaryId?.let { NoteCategoryFilter.KeePassDatabaseUncategorized(it) } ?: NoteCategoryFilter.All
        else -> NoteCategoryFilter.All
    }
}

@Composable
private fun NoteFilterSheetItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    badge: (@Composable () -> Unit)? = null,
    trailingMenu: (@Composable () -> Unit)? = null
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = containerColor,
            headlineColor = contentColor,
            leadingIconColor = contentColor,
            trailingIconColor = contentColor
        ),
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = { Icon(icon, contentDescription = null) },
        supportingContent = badge,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (selected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = contentColor)
                }
                trailingMenu?.invoke()
            }
        }
    )
}
