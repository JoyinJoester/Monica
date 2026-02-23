package takagi.ru.monica.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.fragment.app.FragmentActivity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import takagi.ru.monica.R
import takagi.ru.monica.data.BottomNavContentTab
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.model.PasskeyBindingCodec
import takagi.ru.monica.data.model.TimelineEvent
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel
import takagi.ru.monica.viewmodel.TotpViewModel
import takagi.ru.monica.viewmodel.CategoryFilter
import takagi.ru.monica.data.Category
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.viewmodel.GeneratorViewModel
import takagi.ru.monica.viewmodel.GeneratorType
import takagi.ru.monica.viewmodel.NoteViewModel
import takagi.ru.monica.viewmodel.PasskeyViewModel
import takagi.ru.monica.viewmodel.TimelineViewModel
import takagi.ru.monica.ui.screens.SettingsScreen
import takagi.ru.monica.ui.screens.GeneratorScreen  // 添加生成器页面导入
import takagi.ru.monica.ui.screens.NoteListScreen
import takagi.ru.monica.ui.screens.NoteListContent
import takagi.ru.monica.ui.screens.PasswordDetailScreen
import takagi.ru.monica.ui.screens.SendScreen
import takagi.ru.monica.ui.screens.CardWalletScreen
import takagi.ru.monica.ui.screens.CardWalletTab
import takagi.ru.monica.ui.screens.BankCardDetailScreen
import takagi.ru.monica.ui.screens.DocumentDetailScreen
import takagi.ru.monica.ui.screens.TimelineScreen
import takagi.ru.monica.ui.screens.PasskeyListScreen
import takagi.ru.monica.ui.gestures.SwipeActions
import takagi.ru.monica.ui.haptic.rememberHapticFeedback
import kotlin.math.absoluteValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import takagi.ru.monica.ui.components.QrCodeDialog
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.components.DraggableBottomNavScaffold
import takagi.ru.monica.ui.components.SwipeableAddFab
import takagi.ru.monica.ui.components.DraggableNavItem
import takagi.ru.monica.ui.components.QuickActionItem
import takagi.ru.monica.ui.components.QuickAddCallback
import takagi.ru.monica.ui.components.SyncStatusIcon
import takagi.ru.monica.ui.components.M3IdentityVerifyDialog
import takagi.ru.monica.ui.components.UnifiedCategoryFilterBottomSheet
import takagi.ru.monica.ui.components.UnifiedCategoryFilterSelection
import takagi.ru.monica.ui.components.UnifiedMoveAction
import takagi.ru.monica.ui.components.UnifiedMoveCategoryTarget
import takagi.ru.monica.ui.components.UnifiedMoveToCategoryBottomSheet
import takagi.ru.monica.ui.common.dialog.DeleteConfirmDialog
import takagi.ru.monica.ui.common.layout.DetailPane
import takagi.ru.monica.ui.common.layout.InspectorRow
import takagi.ru.monica.ui.common.layout.ListPane
import takagi.ru.monica.ui.common.pull.PullActionVisualState
import takagi.ru.monica.ui.common.pull.PullGestureIndicator
import takagi.ru.monica.ui.common.pull.rememberPullActionState
import takagi.ru.monica.ui.common.selection.CategoryListItem
import takagi.ru.monica.ui.common.selection.SelectionActionBar
import takagi.ru.monica.ui.common.selection.SelectionModeTopBar
import takagi.ru.monica.ui.main.navigation.BottomNavItem
import takagi.ru.monica.ui.main.navigation.fullLabelRes
import takagi.ru.monica.ui.main.navigation.indexToDefaultTabKey
import takagi.ru.monica.ui.main.navigation.shortLabelRes
import takagi.ru.monica.ui.main.navigation.toBottomNavItem
import takagi.ru.monica.ui.main.layout.AdaptiveMainScaffold
import takagi.ru.monica.ui.password.buildAdditionalInfoPreview
import takagi.ru.monica.ui.password.MultiPasswordEntryCard
import takagi.ru.monica.ui.password.StackedPasswordGroup
import takagi.ru.monica.ui.password.PasswordEntryCard
import takagi.ru.monica.ui.password.StackCardMode
import takagi.ru.monica.ui.password.getGroupKeyForMode
import takagi.ru.monica.ui.password.getPasswordGroupTitle
import takagi.ru.monica.ui.password.getPasswordInfoKey
import takagi.ru.monica.data.bitwarden.BitwardenPendingOperation
import takagi.ru.monica.data.bitwarden.BitwardenSend
import takagi.ru.monica.bitwarden.sync.SyncStatus
import takagi.ru.monica.security.SecurityManager
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import takagi.ru.monica.ui.screens.AddEditPasswordScreen
import takagi.ru.monica.ui.screens.AddEditTotpScreen
import takagi.ru.monica.ui.screens.AddEditBankCardScreen
import takagi.ru.monica.ui.screens.AddEditDocumentScreen
import takagi.ru.monica.ui.screens.AddEditNoteScreen
import takagi.ru.monica.ui.screens.AddEditSendScreen
import takagi.ru.monica.ui.theme.MonicaTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TotpListContent(
    viewModel: takagi.ru.monica.viewmodel.TotpViewModel,
    passwordViewModel: PasswordViewModel,
    onTotpClick: (Long) -> Unit,
    onDeleteTotp: (takagi.ru.monica.data.SecureItem) -> Unit,
    onQuickScanTotp: () -> Unit,
    onSelectionModeChange: (
        isSelectionMode: Boolean,
        selectedCount: Int,
        onExit: () -> Unit,
        onSelectAll: () -> Unit,
        onMoveToCategory: () -> Unit,
        onDelete: () -> Unit
    ) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitwardenRepository = remember { takagi.ru.monica.bitwarden.repository.BitwardenRepository.getInstance(context) }
    val database = remember { takagi.ru.monica.data.PasswordDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val keepassDatabases by database.localKeePassDatabaseDao().getAllDatabases().collectAsState(initial = emptyList())
    var bitwardenVaults by remember { mutableStateOf<List<takagi.ru.monica.data.bitwarden.BitwardenVault>>(emptyList()) }
    val keePassService = remember {
        takagi.ru.monica.utils.KeePassKdbxService(
            context,
            database.localKeePassDatabaseDao(),
            SecurityManager(context)
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
    val totpItems by viewModel.totpItems.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val passwords by passwordViewModel.allPasswords.collectAsState(initial = emptyList())
    val passwordMap = remember(passwords) { passwords.associateBy { it.id } }
    val haptic = rememberHapticFeedback()
    val focusManager = LocalFocusManager.current
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    
    // 分类选择状态
    var isCategorySheetVisible by rememberSaveable { mutableStateOf(false) }
    var categoryPillBoundsInWindow by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val categories by viewModel.categories.collectAsState()
    val currentFilter by viewModel.categoryFilter.collectAsState()
    LaunchedEffect(Unit) {
        bitwardenVaults = bitwardenRepository.getAllVaults()
    }

    // 如果搜索框展开，按返回键关闭搜索框
    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
        viewModel.updateSearchQuery("")
        focusManager.clearFocus()
    }

    // Pull-to-search state
    val density = androidx.compose.ui.platform.LocalDensity.current
    val isBitwardenDatabaseView = when (currentFilter) {
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVault,
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenFolderFilter,
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultStarred,
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultUncategorized -> true
        else -> false
    }
    val selectedBitwardenVaultId = when (val filter = currentFilter) {
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVault -> filter.vaultId
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenFolderFilter -> filter.vaultId
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultStarred -> filter.vaultId
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultUncategorized -> filter.vaultId
        else -> null
    }
    val bitwardenViewModel: takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel = viewModel()
    val bitwardenSyncStatusByVault by bitwardenViewModel.syncStatusByVault.collectAsState()
    val isTopBarSyncing = selectedBitwardenVaultId?.let { vaultId ->
        bitwardenSyncStatusByVault[vaultId]?.isRunning == true
    } == true
    // Verifier page uses plain pull-to-search only; disable pull-to-sync UX here.
    val enableBitwardenPullSync = false
    val searchTriggerDistance = remember(density) {
        with(density) { 72.dp.toPx() }
    }
    val syncTriggerDistance = remember(density) { with(density) { 72.dp.toPx() } }
    val maxDragDistance = remember(density) { with(density) { 100.dp.toPx() } }
    val lazyListState = rememberLazyListState()
    val pullAction = rememberPullActionState(
        isBitwardenDatabaseView = enableBitwardenPullSync,
        isSearchExpanded = isSearchExpanded,
        searchTriggerDistance = searchTriggerDistance,
        syncTriggerDistance = syncTriggerDistance,
        maxDragDistance = maxDragDistance,
        bitwardenRepository = bitwardenRepository,
        onSearchTriggered = { isSearchExpanded = true }
    )
    
    // 选择模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showMoveToCategoryDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryNameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val settingsManager = remember { takagi.ru.monica.utils.SettingsManager(context) }
    val appSettings by settingsManager.settingsFlow.collectAsState(initial = takagi.ru.monica.data.AppSettings())
    val activity = context as? FragmentActivity
    val biometricHelper = remember { BiometricHelper(context) }
    val canUseBiometric = activity != null && appSettings.biometricEnabled && biometricHelper.isBiometricAvailable()
    val sharedTickSeconds by produceState(initialValue = System.currentTimeMillis() / 1000) {
        while (true) {
            value = System.currentTimeMillis() / 1000
            delay(1000)
        }
    }
    
    // 添加单项删除对话框状态
    var itemToDelete by remember { mutableStateOf<takagi.ru.monica.data.SecureItem?>(null) }
    var singleItemPasswordInput by remember { mutableStateOf("") }
    var showSingleItemPasswordVerify by remember { mutableStateOf(false) }
    
    // 待删除项ID集合（用于隐藏即将删除的项）
    var deletedItemIds by remember { mutableStateOf(setOf<Long>()) }
    
    // QR码显示状态
    var itemToShowQr by remember { mutableStateOf<takagi.ru.monica.data.SecureItem?>(null) }
    
    // 过滤掉待删除的项
    val filteredTotpItems = remember(totpItems, deletedItemIds) {
        totpItems.filter { it.id !in deletedItemIds }
    }
    
    // 定义回调函数
    val exitSelection = {
        isSelectionMode = false
        selectedItems = setOf()
    }
    
    val selectAll = {
        selectedItems = if (selectedItems.size == filteredTotpItems.size) {
            setOf()
        } else {
            filteredTotpItems.map { it.id }.toSet()
        }
    }
    
    val deleteSelected = {
        showBatchDeleteDialog = true
    }

    val moveToCategory = {
        showMoveToCategoryDialog = true
    }
    
    // 通知父组件选择模式状态变化
    LaunchedEffect(isSelectionMode, selectedItems.size) {
        onSelectionModeChange(
            isSelectionMode,
            selectedItems.size,
            exitSelection,
            selectAll,
            moveToCategory,
            deleteSelected
        )
    }

    UnifiedMoveToCategoryBottomSheet(
        visible = showMoveToCategoryDialog,
        onDismiss = { showMoveToCategoryDialog = false },
        categories = categories,
        keepassDatabases = keepassDatabases,
        bitwardenVaults = bitwardenVaults,
        getBitwardenFolders = { vaultId -> database.bitwardenFolderDao().getFoldersByVaultFlow(vaultId) },
        getKeePassGroups = getKeePassGroups,
        allowCopy = true,
        onTargetSelected = { target, action ->
            val movableIds = selectedItems.filter { it > 0L }
            if (action == UnifiedMoveAction.COPY) {
                val selectedTotpItems = totpItems.filter { it.id in movableIds }
                selectedTotpItems.forEach { item ->
                    val totpData = runCatching { Json.decodeFromString<TotpData>(item.itemData) }.getOrNull() ?: return@forEach
                    val detachedTotpData = totpData.copy(
                        boundPasswordId = null,
                        categoryId = null,
                        keepassDatabaseId = null
                    )
                    val targetCategoryId = when (target) {
                        UnifiedMoveCategoryTarget.Uncategorized -> null
                        is UnifiedMoveCategoryTarget.MonicaCategory -> target.categoryId
                        else -> null
                    }
                    val targetKeepassDatabaseId = when (target) {
                        is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> target.databaseId
                        is UnifiedMoveCategoryTarget.KeePassGroupTarget -> target.databaseId
                        else -> null
                    }
                    val targetBitwardenVaultId = when (target) {
                        is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> target.vaultId
                        is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> target.vaultId
                        else -> null
                    }
                    val targetBitwardenFolderId = when (target) {
                        is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> target.folderId
                        is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> ""
                        else -> null
                    }
                    viewModel.saveTotpItem(
                        id = null,
                        title = item.title,
                        notes = item.notes,
                        totpData = detachedTotpData,
                        isFavorite = item.isFavorite,
                        categoryId = targetCategoryId,
                        keepassDatabaseId = targetKeepassDatabaseId,
                        bitwardenVaultId = targetBitwardenVaultId,
                        bitwardenFolderId = targetBitwardenFolderId
                    )
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.selected_items, selectedTotpItems.size),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                when (target) {
                    UnifiedMoveCategoryTarget.Uncategorized -> {
                        viewModel.moveToCategory(movableIds, null)
                        Toast.makeText(context, context.getString(R.string.category_none), Toast.LENGTH_SHORT).show()
                    }
                    is UnifiedMoveCategoryTarget.MonicaCategory -> {
                        viewModel.moveToCategory(movableIds, target.categoryId)
                        val name = categories.find { it.id == target.categoryId }?.name ?: ""
                        Toast.makeText(context, "${context.getString(R.string.move_to_category)} $name", Toast.LENGTH_SHORT).show()
                    }
                    is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> {
                        viewModel.moveToBitwardenFolder(movableIds, target.vaultId, "")
                        Toast.makeText(context, context.getString(R.string.filter_bitwarden), Toast.LENGTH_SHORT).show()
                    }
                    is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> {
                        viewModel.moveToBitwardenFolder(movableIds, target.vaultId, target.folderId)
                        Toast.makeText(context, context.getString(R.string.filter_bitwarden), Toast.LENGTH_SHORT).show()
                    }
                    is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> {
                        viewModel.moveToKeePassDatabase(movableIds, target.databaseId)
                        val name = keepassDatabases.find { it.id == target.databaseId }?.name ?: "KeePass"
                        Toast.makeText(context, "${context.getString(R.string.move_to_category)} $name", Toast.LENGTH_SHORT).show()
                    }
                    is UnifiedMoveCategoryTarget.KeePassGroupTarget -> {
                        viewModel.moveToKeePassGroup(movableIds, target.databaseId, target.groupPath)
                        val groupName = target.groupPath.substringAfterLast('/')
                        Toast.makeText(context, "${context.getString(R.string.move_to_category)} $groupName", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            showMoveToCategoryDialog = false
            isSelectionMode = false
            selectedItems = emptySet()
        }
    )

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
                TextButton(onClick = {
                    if (categoryNameInput.isNotBlank()) {
                        passwordViewModel.addCategory(categoryNameInput)
                        categoryNameInput = ""
                        showAddCategoryDialog = false
                    }
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column {
        // M3E Top Bar with integrated search - 根据当前分类过滤器动态显示标题
        val title = when (val filter = currentFilter) {
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.All -> stringResource(R.string.nav_passkey)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.Local -> stringResource(R.string.filter_monica)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.Starred -> stringResource(R.string.filter_starred)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.Uncategorized -> stringResource(R.string.filter_uncategorized)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.LocalStarred -> "${stringResource(R.string.filter_monica)} · ${stringResource(R.string.filter_starred)}"
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.LocalUncategorized -> "${stringResource(R.string.filter_monica)} · ${stringResource(R.string.filter_uncategorized)}"
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.Custom -> categories.find { it.id == filter.categoryId }?.name ?: stringResource(R.string.unknown_category)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabase -> keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassGroupFilter -> filter.groupPath.substringAfterLast('/')
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseStarred -> "${keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"} · ${stringResource(R.string.filter_starred)}"
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseUncategorized -> "${keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"} · ${stringResource(R.string.filter_uncategorized)}"
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVault -> stringResource(R.string.filter_bitwarden)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenFolderFilter -> stringResource(R.string.filter_bitwarden)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultStarred -> "${stringResource(R.string.filter_bitwarden)} · ${stringResource(R.string.filter_starred)}"
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultUncategorized -> "${stringResource(R.string.filter_bitwarden)} · ${stringResource(R.string.filter_uncategorized)}"
        }

        ExpressiveTopBar(
            title = title,
            searchQuery = searchQuery,
            onSearchQueryChange = viewModel::updateSearchQuery,
            isSearchExpanded = isSearchExpanded,
            onSearchExpandedChange = { isSearchExpanded = it },
            searchHint = stringResource(R.string.search_authenticator),
            onActionPillBoundsChanged = { bounds -> categoryPillBoundsInWindow = bounds },
            actions = {
                // 分类选择按钮
                IconButton(onClick = { isCategorySheetVisible = true }) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = stringResource(R.string.category),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 快速扫码按钮
                IconButton(onClick = onQuickScanTotp) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = stringResource(R.string.quick_action_scan_qr),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectedBitwardenVaultId != null) {
                    IconButton(
                        onClick = {
                            if (isTopBarSyncing) return@IconButton
                            val vaultId = selectedBitwardenVaultId ?: return@IconButton
                            bitwardenViewModel.requestManualSync(vaultId)
                        },
                        enabled = !isTopBarSyncing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = stringResource(R.string.refresh),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 搜索按钮
                IconButton(onClick = { isSearchExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
        
        val totpSelectedFilter = when (val filter = currentFilter) {
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.All -> UnifiedCategoryFilterSelection.All
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.Local -> UnifiedCategoryFilterSelection.Local
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.Starred -> UnifiedCategoryFilterSelection.Starred
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.Uncategorized -> UnifiedCategoryFilterSelection.Uncategorized
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.LocalStarred -> UnifiedCategoryFilterSelection.LocalStarred
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.LocalUncategorized -> UnifiedCategoryFilterSelection.LocalUncategorized
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.Custom -> UnifiedCategoryFilterSelection.Custom(filter.categoryId)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabase -> UnifiedCategoryFilterSelection.KeePassDatabaseFilter(filter.databaseId)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassGroupFilter -> UnifiedCategoryFilterSelection.KeePassGroupFilter(filter.databaseId, filter.groupPath)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseStarred -> UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter(filter.databaseId)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseUncategorized -> UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter(filter.databaseId)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVault -> UnifiedCategoryFilterSelection.BitwardenVaultFilter(filter.vaultId)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenFolderFilter -> UnifiedCategoryFilterSelection.BitwardenFolderFilter(filter.vaultId, filter.folderId)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultStarred -> UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter(filter.vaultId)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultUncategorized -> UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter(filter.vaultId)
        }
        UnifiedCategoryFilterBottomSheet(
            visible = isCategorySheetVisible,
            onDismiss = { isCategorySheetVisible = false },
            selected = totpSelectedFilter,
            onSelect = { selection ->
                when (selection) {
                    is UnifiedCategoryFilterSelection.All -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.All)
                    is UnifiedCategoryFilterSelection.Local -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.Local)
                    is UnifiedCategoryFilterSelection.Starred -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.Starred)
                    is UnifiedCategoryFilterSelection.Uncategorized -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.Uncategorized)
                    is UnifiedCategoryFilterSelection.LocalStarred -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.LocalStarred)
                    is UnifiedCategoryFilterSelection.LocalUncategorized -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.LocalUncategorized)
                    is UnifiedCategoryFilterSelection.Custom -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.Custom(selection.categoryId))
                    is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabase(selection.databaseId))
                    is UnifiedCategoryFilterSelection.KeePassGroupFilter -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassGroupFilter(selection.databaseId, selection.groupPath))
                    is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseStarred(selection.databaseId))
                    is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseUncategorized(selection.databaseId))
                    is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVault(selection.vaultId))
                    is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenFolderFilter(selection.folderId, selection.vaultId))
                    is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultStarred(selection.vaultId))
                    is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultUncategorized(selection.vaultId))
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
                SecurityManager(context).verifyMasterPassword(input)
            },
            onCreateCategoryWithName = { name -> passwordViewModel.addCategory(name) },
            onCreateBitwardenFolder = { vaultId, name ->
                scope.launch {
                    val result = bitwardenRepository.createFolder(vaultId, name)
                    result.exceptionOrNull()?.let { error ->
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.webdav_operation_failed,
                                error.message ?: ""
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onRenameBitwardenFolder = { vaultId, folderId, newName ->
                scope.launch {
                    val result = bitwardenRepository.renameFolder(vaultId, folderId, newName)
                    result.exceptionOrNull()?.let { error ->
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.webdav_operation_failed,
                                error.message ?: ""
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onDeleteBitwardenFolder = { vaultId, folderId ->
                scope.launch {
                    val result = bitwardenRepository.deleteFolder(vaultId, folderId)
                    result.exceptionOrNull()?.let { error ->
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.webdav_operation_failed,
                                error.message ?: ""
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
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
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.webdav_operation_failed,
                                result.exceptionOrNull()?.message ?: ""
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
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
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.webdav_operation_failed,
                                result.exceptionOrNull()?.message ?: ""
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
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
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.webdav_operation_failed,
                                result.exceptionOrNull()?.message ?: ""
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
        
        // 统一进度条 - 在顶栏下方显示
        if (appSettings.validatorUnifiedProgressBar == takagi.ru.monica.data.UnifiedProgressBarMode.ENABLED && 
            filteredTotpItems.isNotEmpty()) {
            takagi.ru.monica.ui.components.UnifiedProgressBar(
                style = appSettings.validatorProgressBarStyle,
                currentSeconds = sharedTickSeconds,
                period = 30,
                smoothProgress = appSettings.validatorSmoothProgress,
                timeOffset = (appSettings.totpTimeOffset * 1000).toLong() // 传递时间偏移(毫秒)
            )
        }

        val searchProgress = (pullAction.currentOffset / searchTriggerDistance).coerceIn(0f, 1f)
        val syncProgress = ((pullAction.currentOffset - searchTriggerDistance) / (syncTriggerDistance - searchTriggerDistance))
            .coerceIn(0f, 1f)
        val pullVisualState = when {
            enableBitwardenPullSync && pullAction.isBitwardenSyncing -> PullActionVisualState.SYNCING
            enableBitwardenPullSync && pullAction.showSyncFeedback -> PullActionVisualState.SYNC_DONE
            enableBitwardenPullSync && pullAction.syncHintArmed -> PullActionVisualState.SYNC_READY
            pullAction.currentOffset >= searchTriggerDistance -> PullActionVisualState.SEARCH_READY
            else -> PullActionVisualState.IDLE
        }
        val pullHintText = when (pullVisualState) {
            PullActionVisualState.SYNCING -> stringResource(R.string.pull_syncing_bitwarden)
            PullActionVisualState.SYNC_DONE -> pullAction.syncFeedbackMessage.ifBlank {
                if (pullAction.syncFeedbackIsSuccess) {
                    stringResource(R.string.pull_sync_success)
                } else {
                    stringResource(R.string.sync_status_failed_full)
                }
            }
            PullActionVisualState.SYNC_READY -> stringResource(R.string.pull_release_to_sync_bitwarden)
            PullActionVisualState.SEARCH_READY -> if (enableBitwardenPullSync) {
                stringResource(R.string.pull_release_to_search)
            } else {
                null
            }
            PullActionVisualState.IDLE -> null
        }
        val shouldPinIndicator = enableBitwardenPullSync && (
            pullAction.syncHintArmed || pullAction.isBitwardenSyncing || pullAction.showSyncFeedback
        )
        val revealHeightTarget = with(density) {
            if (enableBitwardenPullSync) {
                val pullHeight = pullAction.currentOffset.toDp().coerceIn(0.dp, 112.dp)
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
            label = "totp_pull_reveal_height"
        )
        val showPullIndicator = pullHintText != null && revealHeight > 0.5.dp
        val contentPullOffset = if (enableBitwardenPullSync) 0 else pullAction.currentOffset.toInt()

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

        // TOTP列表
        if (filteredTotpItems.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { androidx.compose.ui.unit.IntOffset(0, contentPullOffset) }
                    .nestedScroll(pullAction.nestedScrollConnection)
                    .pointerInput(isSearchExpanded) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                if (!isSearchExpanded) pullAction.onVerticalDrag(dragAmount)
                            },
                            onDragEnd = {
                                pullAction.onDragEnd()
                            },
                            onDragCancel = {
                                pullAction.onDragCancel()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.no_authenticators_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.no_authenticators_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // 可拖动排序的列表状态
            // 用于拖动排序的本地列表状态
            var localTotpItems by remember(filteredTotpItems) { 
                mutableStateOf(filteredTotpItems) 
            }
            
            // 当筛选后的列表变化时同步
            LaunchedEffect(filteredTotpItems) {
                localTotpItems = filteredTotpItems
            }
            
            val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
                // 只在多选模式下允许排序
                if (isSelectionMode) {
                    localTotpItems = localTotpItems.toMutableList().apply {
                        add(to.index, removeAt(from.index))
                    }
                }
            }
            
            // 当拖动结束时保存新顺序
            LaunchedEffect(reorderableLazyListState.isAnyItemDragging) {
                if (!reorderableLazyListState.isAnyItemDragging && isSelectionMode) {
                    // 拖动结束，保存新顺序到数据库
                    val newOrders = localTotpItems.mapIndexed { index, item ->
                        item.id to index
                    }
                    if (newOrders.isNotEmpty()) {
                        viewModel.updateSortOrders(newOrders)
                    }
                }
            }
            
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { androidx.compose.ui.unit.IntOffset(0, contentPullOffset) }
                    .nestedScroll(pullAction.nestedScrollConnection),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = localTotpItems,
                    key = { it.id }
                ) { item ->
                    ReorderableItem(
                        reorderableLazyListState,
                        key = item.id,
                        enabled = isSelectionMode
                    ) { isDragging ->
                        val elevation by animateDpAsState(
                            if (isDragging) 8.dp else 0.dp,
                            label = "drag_elevation"
                        )
                        
                        // 在多选模式下使用拖动手柄
                        val dragModifier = if (isSelectionMode) {
                            Modifier.longPressDraggableHandle(
                                onDragStarted = {
                                    haptic.performLongPress()
                                },
                                onDragStopped = {
                                    haptic.performSuccess()
                                }
                            )
                        } else {
                            Modifier
                        }
                        
                        // 用 SwipeActions 包裹 TOTP 卡片（多选模式或拖动时禁用滑动）
                        takagi.ru.monica.ui.gestures.SwipeActions(
                            onSwipeLeft = {
                                // 左滑删除
                                haptic.performWarning()
                                itemToDelete = item
                                deletedItemIds = deletedItemIds + item.id
                            },
                            onSwipeRight = {
                                // 右滑选择
                                haptic.performSuccess()
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                }
                                selectedItems = if (selectedItems.contains(item.id)) {
                                    selectedItems - item.id
                                } else {
                                    selectedItems + item.id
                                }
                            },
                            isSwiped = itemToDelete?.id == item.id,
                            enabled = !isDragging && !isSelectionMode // 多选模式下禁用滑动，让拖动手势生效
                        ) {
                            // 包装卡片以支持拖动
                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        shadowElevation = elevation.toPx()
                                    }
                                    .then(dragModifier)
                            ) {
                                TotpItemCard(
                                    item = item,
                                    onEdit = { onTotpClick(item.id) },
                                    onToggleSelect = {
                                        selectedItems = if (selectedItems.contains(item.id)) {
                                            selectedItems - item.id
                                        } else {
                                            selectedItems + item.id
                                        }
                                    },
                                    onDelete = {
                                        haptic.performWarning()
                                        itemToDelete = item
                                        deletedItemIds = deletedItemIds + item.id
                                    },
                                    onToggleFavorite = { id, isFavorite ->
                                        viewModel.toggleFavorite(id, isFavorite)
                                    },
                                    onGenerateNext = { id ->
                                        viewModel.incrementHotpCounter(id)
                                    },
                                    onMoveUp = null, // 使用拖动排序替代
                                    onMoveDown = null, // 使用拖动排序替代
                                    onShowQrCode = {
                                        itemToShowQr = item
                                    },
                                    onLongClick = {
                                        // 长按进入多选模式
                                        haptic.performLongPress()
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedItems = setOf(item.id)
                                        }
                                    },
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedItems.contains(item.id),
                                    sharedTickSeconds = sharedTickSeconds,
                                    appSettings = appSettings
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
    
    // QR码对话框
    itemToShowQr?.let { item ->
        QrCodeDialog(
            item = item,
            onDismiss = { itemToShowQr = null }
        )
    }
    
    // 单项删除确认对话框(支持指纹和密码验证)
    itemToDelete?.let { item ->
        DeleteConfirmDialog(
            itemTitle = item.title,
            itemType = stringResource(R.string.item_type_authenticator),
            biometricEnabled = appSettings.biometricEnabled,
            onDismiss = {
                // 取消删除，恢复卡片显示
                deletedItemIds = deletedItemIds - item.id
                itemToDelete = null
            },
            onConfirmWithPassword = { password ->
                singleItemPasswordInput = password
                showSingleItemPasswordVerify = true
            },
            onConfirmWithBiometric = {
                // 指纹验证成功，直接删除
                onDeleteTotp(item)
                Toast.makeText(
                    context,
                    context.getString(R.string.deleted),
                    Toast.LENGTH_SHORT
                ).show()
                itemToDelete = null
            }
        )
    }
    
    // 单项删除密码验证
    if (showSingleItemPasswordVerify && itemToDelete != null) {
        LaunchedEffect(Unit) {
            val securityManager = takagi.ru.monica.security.SecurityManager(context)
            if (securityManager.verifyMasterPassword(singleItemPasswordInput)) {
                // 密码正确，删除 TOTP
                onDeleteTotp(itemToDelete!!)
                
                Toast.makeText(
                    context,
                    context.getString(R.string.deleted),
                    Toast.LENGTH_SHORT
                ).show()
                
                // 清理状态（保持在 deletedItemIds 中，因为已真实删除）
                itemToDelete = null
                singleItemPasswordInput = ""
                showSingleItemPasswordVerify = false
            } else {
                // 密码错误，恢复卡片显示
                deletedItemIds = deletedItemIds - itemToDelete!!.id
                
                Toast.makeText(
                    context,
                    context.getString(R.string.current_password_incorrect),
                    Toast.LENGTH_SHORT
                ).show()
                
                // 重置状态
                itemToDelete = null
                singleItemPasswordInput = ""
                showSingleItemPasswordVerify = false
            }
        }
    }
    
    // 批量删除验证对话框（统一 M3 身份验证弹窗）
    if (showBatchDeleteDialog) {
        val biometricAction = if (canUseBiometric) {
            {
                biometricHelper.authenticate(
                    activity = activity!!,
                    title = context.getString(R.string.verify_identity),
                    subtitle = context.getString(R.string.verify_to_delete),
                    onSuccess = {
                        coroutineScope.launch {
                            val toDelete = totpItems.filter { selectedItems.contains(it.id) }
                            toDelete.forEach { onDeleteTotp(it) }
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.deleted_items, toDelete.size),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            isSelectionMode = false
                            selectedItems = setOf()
                            passwordInput = ""
                            passwordError = false
                            showBatchDeleteDialog = false
                        }
                    },
                    onError = { error ->
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    },
                    onFailed = {}
                )
            }
        } else {
            null
        }
        M3IdentityVerifyDialog(
            title = stringResource(R.string.verify_identity),
            message = stringResource(R.string.batch_delete_totp_message, selectedItems.size),
            passwordValue = passwordInput,
            onPasswordChange = {
                passwordInput = it
                passwordError = false
            },
            onDismiss = {
                showBatchDeleteDialog = false
                passwordInput = ""
                passwordError = false
            },
            onConfirm = {
                if (SecurityManager(context).verifyMasterPassword(passwordInput)) {
                    coroutineScope.launch {
                        val toDelete = totpItems.filter { selectedItems.contains(it.id) }
                        toDelete.forEach { onDeleteTotp(it) }
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.deleted_items, toDelete.size),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        isSelectionMode = false
                        selectedItems = setOf()
                        passwordInput = ""
                        passwordError = false
                        showBatchDeleteDialog = false
                    }
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
}

/**
 * TOTP项卡片
 */

