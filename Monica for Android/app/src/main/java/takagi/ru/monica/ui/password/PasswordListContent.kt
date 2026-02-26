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
import androidx.compose.foundation.horizontalScroll
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
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.utils.decodeKeePassPathForDisplay
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PasswordListContent(
    viewModel: PasswordViewModel,
    settingsViewModel: SettingsViewModel,
    securityManager: SecurityManager,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    bitwardenVaults: List<takagi.ru.monica.data.bitwarden.BitwardenVault>,
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    groupMode: String = "none",
    stackCardMode: StackCardMode,
    onRenameCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onPasswordClick: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onSelectionModeChange: (
        isSelectionMode: Boolean,
        selectedCount: Int,
        onExit: () -> Unit,
        onSelectAll: () -> Unit,
        onFavorite: () -> Unit,
        onMoveToCategory: () -> Unit,
        onDelete: () -> Unit
    ) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val passwordEntries by viewModel.passwordEntries.collectAsState()
    val allPasswords by viewModel.allPasswords.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val currentFilter by viewModel.categoryFilter.collectAsState()
    // settings
    val appSettings by settingsViewModel.settings.collectAsState()

    // "仅本地" 的核心目标是给用户看待上传清单，不应该出现堆叠容器。
    // 因此这里强制扁平展示，仅在该筛选下生效，不影响其他页面。
    val isLocalOnlyView = currentFilter is CategoryFilter.LocalOnly
    // Bitwarden pages use pull-to-search only; disable pull-to-sync behavior.
    val isBitwardenDatabaseView = false && when (currentFilter) {
        is CategoryFilter.BitwardenVault,
        is CategoryFilter.BitwardenFolderFilter,
        is CategoryFilter.BitwardenVaultStarred,
        is CategoryFilter.BitwardenVaultUncategorized -> true
        else -> false
    }
    val selectedBitwardenVaultId = when (val filter = currentFilter) {
        is CategoryFilter.BitwardenVault -> filter.vaultId
        is CategoryFilter.BitwardenFolderFilter -> filter.vaultId
        is CategoryFilter.BitwardenVaultStarred -> filter.vaultId
        is CategoryFilter.BitwardenVaultUncategorized -> filter.vaultId
        else -> null
    }
    val bitwardenViewModel: takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel = viewModel()
    val bitwardenSyncStatusByVault by bitwardenViewModel.syncStatusByVault.collectAsState()
    val isTopBarSyncing = selectedBitwardenVaultId?.let { vaultId ->
        bitwardenSyncStatusByVault[vaultId]?.isRunning == true
    } == true
    val effectiveGroupMode = if (isLocalOnlyView) "none" else groupMode
    val effectiveStackCardMode = if (isLocalOnlyView) StackCardMode.ALWAYS_EXPANDED else stackCardMode
    
    // 选择模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedPasswords by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showMoveToCategoryDialog by remember { mutableStateOf(false) }
    
    // 详情对话框状态
    var showDetailDialog by remember { mutableStateOf(false) }
    var selectedPasswordForDetail by remember { mutableStateOf<takagi.ru.monica.data.PasswordEntry?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? FragmentActivity
    val biometricHelper = remember { BiometricHelper(context) }
    val canUseBiometric = activity != null && appSettings.biometricEnabled && biometricHelper.isBiometricAvailable()
    val database = remember { takagi.ru.monica.data.PasswordDatabase.getDatabase(context) }
    val bitwardenRepository = remember { takagi.ru.monica.bitwarden.repository.BitwardenRepository.getInstance(context) }

    // Display options menu state (moved here)
    var displayMenuExpanded by remember { mutableStateOf(false) }
    // Search state hoisted for morphing animation
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }

    // 如果搜索框展开，按返回键关闭搜索框
    val focusManager = LocalFocusManager.current
    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
        viewModel.updateSearchQuery("")
        focusManager.clearFocus()
    }

    // Handle back press for selection mode
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedPasswords = setOf()
    }
    // Category sheet state
    var isCategorySheetVisible by rememberSaveable { mutableStateOf(false) }
    var categoryPillBoundsInWindow by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    
    // 添加触觉反馈
    val haptic = rememberHapticFeedback()
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    // Pull-to-search/sync state (shared implementation)
    val triggerDistance = remember(density) { with(density) { 40.dp.toPx() } }
    val syncTriggerDistance = remember(density) { with(density) { 72.dp.toPx() } }
    val maxDragDistance = remember(density) { with(density) { 100.dp.toPx() } }
    val pullAction = rememberPullActionState(
        isBitwardenDatabaseView = isBitwardenDatabaseView,
        isSearchExpanded = isSearchExpanded,
        searchTriggerDistance = triggerDistance,
        syncTriggerDistance = syncTriggerDistance,
        maxDragDistance = maxDragDistance,
        bitwardenRepository = bitwardenRepository,
        onSearchTriggered = { isSearchExpanded = true }
    )
    
    // 添加单项删除对话框状态
    var itemToDelete by remember { mutableStateOf<takagi.ru.monica.data.PasswordEntry?>(null) }
    var singleItemPasswordInput by remember { mutableStateOf("") }
    var showSingleItemPasswordVerify by remember { mutableStateOf(false) }
    
    // 添加已删除项ID集合（用于在验证前隐藏项）
    var deletedItemIds by remember { mutableStateOf(setOf<Long>()) }
    
    // 堆叠展开状态 - 记录哪些分组已展开
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }
    var quickFilterFavorite by rememberSaveable { mutableStateOf(false) }
    var quickFilter2fa by rememberSaveable { mutableStateOf(false) }
    var quickFilterNotes by rememberSaveable { mutableStateOf(false) }
    var quickFilterUncategorized by rememberSaveable { mutableStateOf(false) }
    var quickFilterLocalOnly by rememberSaveable { mutableStateOf(false) }
    val configuredQuickFilterItems = appSettings.passwordListQuickFilterItems
    val quickFolderStyle = appSettings.passwordListQuickFolderStyle
    var quickFolderRootKey by rememberSaveable { mutableStateOf(QUICK_FOLDER_ROOT_ALL) }
    val outsideTapInteractionSource = remember { MutableInteractionSource() }
    val canCollapseExpandedGroups = effectiveStackCardMode == StackCardMode.AUTO && expandedGroups.isNotEmpty()
    
    // 当分组模式改变时,重置展开状态
    LaunchedEffect(effectiveGroupMode, effectiveStackCardMode) {
        expandedGroups = setOf()
    }

    LaunchedEffect(appSettings.passwordListQuickFiltersEnabled) {
        if (!appSettings.passwordListQuickFiltersEnabled) {
            quickFilterFavorite = false
            quickFilter2fa = false
            quickFilterNotes = false
            quickFilterUncategorized = false
            quickFilterLocalOnly = false
        }
    }

    LaunchedEffect(configuredQuickFilterItems) {
        if (takagi.ru.monica.data.PasswordListQuickFilterItem.FAVORITE !in configuredQuickFilterItems) {
            quickFilterFavorite = false
        }
        if (takagi.ru.monica.data.PasswordListQuickFilterItem.TWO_FA !in configuredQuickFilterItems) {
            quickFilter2fa = false
        }
        if (takagi.ru.monica.data.PasswordListQuickFilterItem.NOTES !in configuredQuickFilterItems) {
            quickFilterNotes = false
        }
        if (takagi.ru.monica.data.PasswordListQuickFilterItem.UNCATEGORIZED !in configuredQuickFilterItems) {
            quickFilterUncategorized = false
        }
        if (takagi.ru.monica.data.PasswordListQuickFilterItem.LOCAL_ONLY !in configuredQuickFilterItems) {
            quickFilterLocalOnly = false
        }
    }

    LaunchedEffect(currentFilter) {
        currentFilter.toQuickFolderRootKeyOrNull()?.let { key ->
            quickFolderRootKey = key
        }
    }

    val quickFolderNodes = remember(categories) {
        buildPasswordQuickFolderNodes(categories)
    }
    val quickFolderNodeByPath = remember(quickFolderNodes) {
        quickFolderNodes.associateBy { it.path }
    }
    val quickFolderCurrentPath = remember(currentFilter, quickFolderNodes) {
        when (val filter = currentFilter) {
            is CategoryFilter.Custom -> quickFolderNodes
                .firstOrNull { it.category.id == filter.categoryId }
                ?.path

            else -> null
        }
    }
    val quickFolderRootFilter = remember(quickFolderRootKey) {
        quickFolderRootKey.toQuickFolderRootFilter()
    }
    val quickFolderPasswordCountByCategoryId = remember(allPasswords) {
        allPasswords
            .asSequence()
            .mapNotNull { entry ->
                entry.categoryId?.let { categoryId -> categoryId to entry }
            }
            .groupingBy { (categoryId, _) -> categoryId }
            .eachCount()
    }
    val quickFolderShortcuts = remember(
        appSettings.passwordListQuickFoldersEnabled,
        quickFolderStyle,
        currentFilter,
        quickFolderCurrentPath,
        quickFolderNodes,
        quickFolderNodeByPath,
        quickFolderRootFilter,
        quickFolderPasswordCountByCategoryId
    ) {
        if (!appSettings.passwordListQuickFoldersEnabled || !currentFilter.supportsQuickFolders()) {
            emptyList()
        } else {
            val shortcuts = mutableListOf<PasswordQuickFolderShortcut>()
            if (quickFolderStyle == takagi.ru.monica.data.PasswordListQuickFolderStyle.CLASSIC &&
                currentFilter is CategoryFilter.Custom
            ) {
                val parentPath = quickFolderCurrentPath?.let(::passwordQuickFolderParentPath)
                val parentTarget = if (parentPath != null) {
                    quickFolderNodeByPath[parentPath]?.category?.let { CategoryFilter.Custom(it.id) }
                        ?: quickFolderRootFilter
                } else {
                    quickFolderRootFilter
                }
                shortcuts += PasswordQuickFolderShortcut(
                    key = "back_${quickFolderCurrentPath.orEmpty()}",
                    title = context.getString(R.string.password_list_quick_folder_back),
                    subtitle = context.getString(R.string.password_list_quick_folder_back_subtitle),
                    isBack = true,
                    targetFilter = parentTarget,
                    passwordCount = null
                )
            }

            val targetParentPath = quickFolderCurrentPath
            val children = quickFolderNodes.filter { node ->
                node.parentPath == targetParentPath
            }
            children.forEach { node ->
                shortcuts += PasswordQuickFolderShortcut(
                    key = "folder_${node.category.id}_${node.path}",
                    title = node.displayName,
                    subtitle = "",
                    isBack = false,
                    targetFilter = CategoryFilter.Custom(node.category.id),
                    passwordCount = quickFolderPasswordCountByCategoryId[node.category.id] ?: 0
                )
            }
            shortcuts
        }
    }
    val quickFolderBreadcrumbs = remember(
        appSettings.passwordListQuickFoldersEnabled,
        quickFolderStyle,
        currentFilter,
        quickFolderCurrentPath,
        quickFolderNodeByPath,
        quickFolderRootFilter
    ) {
        if (!appSettings.passwordListQuickFoldersEnabled ||
            quickFolderStyle != takagi.ru.monica.data.PasswordListQuickFolderStyle.M3_CARD ||
            !currentFilter.supportsQuickFolders()
        ) {
            emptyList()
        } else {
            val crumbs = mutableListOf<PasswordQuickFolderBreadcrumb>()
            crumbs += PasswordQuickFolderBreadcrumb(
                key = "root",
                title = context.getString(R.string.password_list_quick_folder_root_label),
                targetFilter = quickFolderRootFilter,
                isCurrent = quickFolderCurrentPath == null
            )
            val path = quickFolderCurrentPath
            if (!path.isNullOrBlank()) {
                var cumulative = ""
                val parts = path.split("/").filter { it.isNotBlank() }
                parts.forEachIndexed { index, part ->
                    cumulative = if (cumulative.isBlank()) part else "$cumulative/$part"
                    val targetFilter = quickFolderNodeByPath[cumulative]
                        ?.category
                        ?.let { CategoryFilter.Custom(it.id) }
                    if (targetFilter != null) {
                        crumbs += PasswordQuickFolderBreadcrumb(
                            key = "path_$cumulative",
                            title = part,
                            targetFilter = targetFilter,
                            isCurrent = index == parts.lastIndex
                        )
                    }
                }
            }
            crumbs
        }
    }
    
    val visiblePasswordEntries = remember(
        passwordEntries,
        deletedItemIds,
        appSettings.passwordListQuickFiltersEnabled,
        configuredQuickFilterItems,
        quickFilterFavorite,
        quickFilter2fa,
        quickFilterNotes,
        quickFilterUncategorized,
        quickFilterLocalOnly
    ) {
        var filtered = passwordEntries.filter { it.id !in deletedItemIds }
        if (appSettings.passwordListQuickFiltersEnabled) {
            if (quickFilterFavorite && takagi.ru.monica.data.PasswordListQuickFilterItem.FAVORITE in configuredQuickFilterItems) {
                filtered = filtered.filter { it.isFavorite }
            }
            if (quickFilter2fa && takagi.ru.monica.data.PasswordListQuickFilterItem.TWO_FA in configuredQuickFilterItems) {
                filtered = filtered.filter { it.authenticatorKey.isNotBlank() }
            }
            if (quickFilterNotes && takagi.ru.monica.data.PasswordListQuickFilterItem.NOTES in configuredQuickFilterItems) {
                filtered = filtered.filter { it.notes.isNotBlank() }
            }
            if (quickFilterUncategorized && takagi.ru.monica.data.PasswordListQuickFilterItem.UNCATEGORIZED in configuredQuickFilterItems) {
                filtered = filtered.filter { it.categoryId == null }
            }
            if (quickFilterLocalOnly && takagi.ru.monica.data.PasswordListQuickFilterItem.LOCAL_ONLY in configuredQuickFilterItems) {
                filtered = filtered.filter {
                    it.keepassDatabaseId == null && it.bitwardenVaultId == null
                }
            }
        }
        filtered
    }

    LaunchedEffect(visiblePasswordEntries) {
        if (selectedPasswords.isEmpty()) return@LaunchedEffect
        val visibleIds = visiblePasswordEntries.map { it.id }.toSet()
        selectedPasswords = selectedPasswords.intersect(visibleIds)
    }
    
    // 根据分组模式对密码进行分组
    val groupedPasswords = remember(visiblePasswordEntries, effectiveGroupMode, effectiveStackCardMode) {
        val filteredEntries = visiblePasswordEntries
        
        // 步骤1: 先按"除密码外的信息"合并；始终展开模式下跳过合并，逐条显示
        val mergedByInfo = if (effectiveStackCardMode == StackCardMode.ALWAYS_EXPANDED) {
            filteredEntries.sortedBy { it.sortOrder }.map { listOf(it) }
        } else {
            filteredEntries
                .groupBy { getPasswordInfoKey(it) }
                .map { (_, entries) -> 
                    // 如果有多个密码,保留所有但标记为合并组
                    entries.sortedBy { it.sortOrder }
                }
        }
        
        // 步骤2: 再按显示模式分组
        val groupedAndSorted = if (isLocalOnlyView) {
            // 本筛选是“待上传清单”，直接扁平显示，禁止堆叠/二次分组。
            filteredEntries
                .sortedBy { it.sortOrder }
                .associate { entry -> "entry_${entry.id}" to listOf(entry) }
        } else {
            when (effectiveGroupMode) {
                "title" -> {
                    // 按完整标题分组
                    mergedByInfo
                        .groupBy { entries -> entries.first().title.ifBlank { context.getString(R.string.untitled) } }
                        .mapValues { (_, groups) -> groups.flatten() }
                        .toList()
                        .sortedWith(compareByDescending<Pair<String, List<takagi.ru.monica.data.PasswordEntry>>> { (_, passwords) ->
                            // 计算卡片类型优先级
                            val infoKeyGroups = passwords.groupBy { getPasswordInfoKey(it) }
                            val cardType = when {
                                // 堆叠卡片: 多个不同信息的密码
                                infoKeyGroups.size > 1 -> 3
                                // 多密码卡片: 除密码外信息相同的多个密码
                                infoKeyGroups.size == 1 && passwords.size > 1 -> 2
                                // 单密码卡片: 只有一个密码
                                else -> 1
                            }
                            
                            // 计算收藏优先级 (收藏状态为主要优先级)
                            val anyFavorited = passwords.any { it.isFavorite }
                            val favoriteBonus = if (anyFavorited) 10 else 0
                            
                            // 组合分数: 收藏状态(主要) + 卡片类型(次要)
                            favoriteBonus.toDouble() + cardType.toDouble()
                        }.thenBy { (title, _) ->
                            // 同优先级内按标题排序
                            title
                        })
                        .toMap()
                }
                
                else -> {
                    // 按所选维度分组，并按优先级排序
                    mergedByInfo
                        .groupBy { entries -> getGroupKeyForMode(entries.first(), effectiveGroupMode) }
                        .mapValues { (_, groups) -> groups.flatten() }
                        .toList()
                        .sortedWith(compareByDescending<Pair<String, List<takagi.ru.monica.data.PasswordEntry>>> { (_, passwords) ->
                            // 计算卡片类型优先级
                            val infoKeyGroups = passwords.groupBy { getPasswordInfoKey(it) }
                            val cardType = when {
                                // 堆叠卡片: 多个不同信息的密码
                                infoKeyGroups.size > 1 -> 3
                                // 多密码卡片: 除密码外信息相同的多个密码
                                infoKeyGroups.size == 1 && passwords.size > 1 -> 2
                                // 单密码卡片: 只有一个密码
                                else -> 1
                            }
                            
                            // 计算收藏优先级
                            val favoriteCount = passwords.count { it.isFavorite }
                            val totalCount = passwords.size
                            
                            // 计算收藏优先级 (收藏状态为主要优先级)
                            val anyFavorited = passwords.any { it.isFavorite }
                            val favoriteBonus = if (anyFavorited) 10 else 0
                            
                            // 组合分数: 收藏状态(主要) + 卡片类型(次要)
                            favoriteBonus.toDouble() + cardType.toDouble()
                        }.thenBy { (_, passwords) ->
                            // 同优先级内按第一个卡片的 sortOrder 排序
                            passwords.firstOrNull()?.sortOrder ?: Int.MAX_VALUE
                        })
                        .toMap()
                }
            }
        }

        // 如果是始终展开模式，强制拆分为单项列表，但保持排序顺序
        if (effectiveStackCardMode == StackCardMode.ALWAYS_EXPANDED) {
            groupedAndSorted.values.flatten()
                .map { entry -> 
                    // 使用唯一ID作为键，确保LazyColumn正确渲染
                    "entry_${entry.id}" to listOf(entry)
                }
                .toMap()
        } else {
            groupedAndSorted
        }
    }
    
    // 定义回调函数
    val exitSelection = {
        isSelectionMode = false
        selectedPasswords = setOf()
    }
    
    val selectAll = {
        selectedPasswords = if (selectedPasswords.size == visiblePasswordEntries.size) {
            setOf()
        } else {
            visiblePasswordEntries.map { it.id }.toSet()
        }
    }
    
    val favoriteSelected = {
        // 智能批量收藏/取消收藏
        coroutineScope.launch {
            val selectedEntries = passwordEntries.filter { selectedPasswords.contains(it.id) }
            
            // 检查是否所有选中的密码都已收藏
            val allFavorited = selectedEntries.all { it.isFavorite }
            
            // 如果全部已收藏,则取消收藏;否则全部设为收藏
            val newFavoriteState = !allFavorited
            
            selectedEntries.forEach { entry ->
                viewModel.toggleFavorite(entry.id, newFavoriteState)
            }
            
            // 显示提示
            val message = if (newFavoriteState) {
                context.getString(R.string.batch_favorited, selectedEntries.size)
            } else {
                context.getString(R.string.batch_unfavorited, selectedEntries.size)
            }
            
            android.widget.Toast.makeText(
                context,
                message,
                android.widget.Toast.LENGTH_SHORT
            ).show()
            
            // 退出选择模式
            isSelectionMode = false
            selectedPasswords = setOf()
        }
    }
    
    val moveToCategory = {
        showMoveToCategoryDialog = true
    }
    
    val deleteSelected = {
        showBatchDeleteDialog = true
    }
    
    // 通知父组件选择模式状态变化
    LaunchedEffect(isSelectionMode, selectedPasswords.size) {
        onSelectionModeChange(
            isSelectionMode,
            selectedPasswords.size,
            exitSelection,
            selectAll,
            favoriteSelected,
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
        getKeePassGroups = localKeePassViewModel::getGroups,
        allowCopy = true,
        onTargetSelected = { target, action ->
            val selectedIds = selectedPasswords.toList()
            val selectedEntries = passwordEntries.filter { it.id in selectedPasswords }
            if (action == UnifiedMoveAction.COPY) {
                selectedEntries.forEach { entry ->
                    val copiedEntry = when (target) {
                        UnifiedMoveCategoryTarget.Uncategorized -> entry.copy(
                            id = 0,
                            createdAt = Date(),
                            updatedAt = Date(),
                            categoryId = null,
                            keepassDatabaseId = null,
                            keepassGroupPath = null,
                            bitwardenVaultId = null,
                            bitwardenCipherId = null,
                            bitwardenFolderId = null,
                            bitwardenRevisionDate = null,
                            bitwardenLocalModified = false,
                            isDeleted = false,
                            deletedAt = null
                        )
                        is UnifiedMoveCategoryTarget.MonicaCategory -> entry.copy(
                            id = 0,
                            createdAt = Date(),
                            updatedAt = Date(),
                            categoryId = target.categoryId,
                            keepassDatabaseId = null,
                            keepassGroupPath = null,
                            bitwardenVaultId = null,
                            bitwardenCipherId = null,
                            bitwardenFolderId = null,
                            bitwardenRevisionDate = null,
                            bitwardenLocalModified = false,
                            isDeleted = false,
                            deletedAt = null
                        )
                        is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> entry.copy(
                            id = 0,
                            createdAt = Date(),
                            updatedAt = Date(),
                            categoryId = null,
                            keepassDatabaseId = null,
                            keepassGroupPath = null,
                            bitwardenVaultId = target.vaultId,
                            bitwardenCipherId = null,
                            bitwardenFolderId = "",
                            bitwardenRevisionDate = null,
                            bitwardenLocalModified = false,
                            isDeleted = false,
                            deletedAt = null
                        )
                        is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> entry.copy(
                            id = 0,
                            createdAt = Date(),
                            updatedAt = Date(),
                            categoryId = null,
                            keepassDatabaseId = null,
                            keepassGroupPath = null,
                            bitwardenVaultId = target.vaultId,
                            bitwardenCipherId = null,
                            bitwardenFolderId = target.folderId,
                            bitwardenRevisionDate = null,
                            bitwardenLocalModified = false,
                            isDeleted = false,
                            deletedAt = null
                        )
                        is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> entry.copy(
                            id = 0,
                            createdAt = Date(),
                            updatedAt = Date(),
                            categoryId = null,
                            keepassDatabaseId = target.databaseId,
                            keepassGroupPath = null,
                            bitwardenVaultId = null,
                            bitwardenCipherId = null,
                            bitwardenFolderId = null,
                            bitwardenRevisionDate = null,
                            bitwardenLocalModified = false,
                            isDeleted = false,
                            deletedAt = null
                        )
                        is UnifiedMoveCategoryTarget.KeePassGroupTarget -> entry.copy(
                            id = 0,
                            createdAt = Date(),
                            updatedAt = Date(),
                            categoryId = null,
                            keepassDatabaseId = target.databaseId,
                            keepassGroupPath = target.groupPath,
                            bitwardenVaultId = null,
                            bitwardenCipherId = null,
                            bitwardenFolderId = null,
                            bitwardenRevisionDate = null,
                            bitwardenLocalModified = false,
                            isDeleted = false,
                            deletedAt = null
                        )
                    }
                    viewModel.addPasswordEntry(copiedEntry)
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.selected_items, selectedEntries.size),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                when (target) {
                    UnifiedMoveCategoryTarget.Uncategorized -> {
                        viewModel.movePasswordsToCategory(selectedIds, null)
                        Toast.makeText(context, context.getString(R.string.category_none), Toast.LENGTH_SHORT).show()
                    }
                    is UnifiedMoveCategoryTarget.MonicaCategory -> {
                        viewModel.movePasswordsToCategory(selectedIds, target.categoryId)
                        val name = categories.find { it.id == target.categoryId }?.name ?: ""
                        Toast.makeText(context, "${context.getString(R.string.move_to_category)} $name", Toast.LENGTH_SHORT).show()
                    }
                    is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> {
                        viewModel.movePasswordsToBitwardenFolder(selectedIds, target.vaultId, "")
                        Toast.makeText(context, context.getString(R.string.filter_bitwarden), Toast.LENGTH_SHORT).show()
                    }
                    is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> {
                        viewModel.movePasswordsToBitwardenFolder(selectedIds, target.vaultId, target.folderId)
                        Toast.makeText(context, context.getString(R.string.filter_bitwarden), Toast.LENGTH_SHORT).show()
                    }
                    is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> {
                        coroutineScope.launch {
                            try {
                                val result = localKeePassViewModel.addPasswordEntriesToKdbx(
                                    databaseId = target.databaseId,
                                    entries = selectedEntries,
                                    decryptPassword = { encrypted -> securityManager.decryptData(encrypted) ?: "" }
                                )
                                if (result.isSuccess) {
                                    viewModel.movePasswordsToKeePassDatabase(selectedIds, target.databaseId)
                                    Toast.makeText(
                                        context,
                                        "${context.getString(R.string.move_to_category)} ${keepassDatabases.find { it.id == target.databaseId }?.name ?: "KeePass"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.webdav_operation_failed, result.exceptionOrNull()?.message ?: ""),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.webdav_operation_failed, e.message ?: ""),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    is UnifiedMoveCategoryTarget.KeePassGroupTarget -> {
                        coroutineScope.launch {
                            try {
                                val result = localKeePassViewModel.addPasswordEntriesToKdbx(
                                    databaseId = target.databaseId,
                                    entries = selectedEntries,
                                    decryptPassword = { encrypted -> securityManager.decryptData(encrypted) ?: "" }
                                )
                                if (result.isSuccess) {
                                    viewModel.movePasswordsToKeePassGroup(selectedIds, target.databaseId, target.groupPath)
                                    Toast.makeText(
                                        context,
                                        "${context.getString(R.string.move_to_category)} ${decodeKeePassPathForDisplay(target.groupPath)}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.webdav_operation_failed, result.exceptionOrNull()?.message ?: ""),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.webdav_operation_failed, e.message ?: ""),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
            showMoveToCategoryDialog = false
            isSelectionMode = false
            selectedPasswords = emptySet()
        }
    )

    // Display options/search state moved to top
    
    Column {
        // M3E Top Bar with integrated search - 始终显示
        val title = when(val filter = currentFilter) {
            is CategoryFilter.All -> stringResource(R.string.filter_all)
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
                onSearchQueryChange = viewModel::updateSearchQuery,
                isSearchExpanded = isSearchExpanded,
                onSearchExpandedChange = { isSearchExpanded = it },
                searchHint = stringResource(R.string.search_passwords_hint),
                onActionPillBoundsChanged = { bounds -> categoryPillBoundsInWindow = bounds },
                actions = {
                    // 1. Category Folder Trigger
                    IconButton(onClick = { isCategorySheetVisible = true }) {
                         Icon(
                            imageVector = Icons.Default.Folder, // Or CreateNewFolder
                            contentDescription = stringResource(R.string.category),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 2. Display Options Trigger
                    IconButton(onClick = { displayMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.DashboardCustomize,
                            contentDescription = stringResource(R.string.display_options_menu_title),
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

                    // 3. Search Trigger (放在最右边)
                    IconButton(onClick = { isSearchExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            val unifiedSelectedFilter = when (val filter = currentFilter) {
                is CategoryFilter.All -> UnifiedCategoryFilterSelection.All
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
            UnifiedCategoryFilterBottomSheet(
                visible = isCategorySheetVisible,
                onDismiss = { isCategorySheetVisible = false },
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
                    SecurityManager(context).verifyMasterPassword(input)
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

    // Display Options Bottom Sheet
    if (displayMenuExpanded) {
        ModalBottomSheet(
            onDismissRequest = { displayMenuExpanded = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                 modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding()
            ) {
                 Text(
                    text = stringResource(R.string.display_options_menu_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )

                // Stack Mode Section
                Text(
                    text = stringResource(R.string.stack_mode_menu_title),
                     style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                     modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                val stackModes = listOf(
                    StackCardMode.AUTO,
                    StackCardMode.ALWAYS_EXPANDED
                )

                stackModes.forEach { mode ->
                    val selected = mode == stackCardMode
                    val (modeTitle, desc, icon) = when (mode) {
                        StackCardMode.AUTO -> Triple(
                            stringResource(R.string.stack_mode_auto),
                            stringResource(R.string.stack_mode_auto_desc),
                            Icons.Default.AutoAwesome
                        )
                        StackCardMode.ALWAYS_EXPANDED -> Triple(
                            stringResource(R.string.stack_mode_expand),
                            stringResource(R.string.stack_mode_expand_desc),
                            Icons.Default.UnfoldMore
                        )
                    }

                    takagi.ru.monica.ui.components.SettingsOptionItem(
                        title = modeTitle,
                        description = desc,
                        icon = icon,
                        selected = selected,
                        onClick = {
                            settingsViewModel.updateStackCardMode(mode.name)
                            displayMenuExpanded = false
                        }
                    )
                }

                 HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // Group Mode Section
                Text(
                    text = stringResource(R.string.group_mode_menu_title),
                     style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                     modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                val groupModes = listOf(
                    "smart" to Triple(
                        stringResource(R.string.group_mode_smart),
                        stringResource(R.string.group_mode_smart_desc),
                        Icons.Default.DashboardCustomize
                    ),
                    "note" to Triple(
                        stringResource(R.string.group_mode_note),
                        stringResource(R.string.group_mode_note_desc),
                        Icons.Default.Description
                    ),
                    "website" to Triple(
                        stringResource(R.string.group_mode_website),
                        stringResource(R.string.group_mode_website_desc),
                        Icons.Default.Language
                    ),
                    "app" to Triple(
                        stringResource(R.string.group_mode_app),
                        stringResource(R.string.group_mode_app_desc),
                        Icons.Default.Apps
                    ),
                    "title" to Triple(
                        stringResource(R.string.group_mode_title),
                        stringResource(R.string.group_mode_title_desc),
                        Icons.Default.Title
                    )
                )

                groupModes.forEach { (modeKey, meta) ->
                    val selected = groupMode == modeKey
                    val (modeTitle, desc, icon) = meta

                    takagi.ru.monica.ui.components.SettingsOptionItem(
                        title = modeTitle,
                        description = desc,
                        icon = icon,
                        selected = selected,
                        onClick = {
                            settingsViewModel.updatePasswordGroupMode(modeKey)
                            displayMenuExpanded = false
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // Password Card Display Mode Section
                Text(
                    text = stringResource(R.string.password_card_display_mode_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                val displayModes = listOf(
                    takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL,
                    takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_USERNAME,
                    takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_ONLY
                )

                displayModes.forEach { mode ->
                    val selected = mode == appSettings.passwordCardDisplayMode
                    val (modeTitle, desc, icon) = when (mode) {
                        takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL -> Triple(
                            stringResource(R.string.display_mode_all),
                            stringResource(R.string.display_mode_all_desc),
                            Icons.Default.Visibility
                        )
                        takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_USERNAME -> Triple(
                            stringResource(R.string.display_mode_title_username),
                            stringResource(R.string.display_mode_title_username_desc),
                            Icons.Default.Person
                        )
                        takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_ONLY -> Triple(
                            stringResource(R.string.display_mode_title_only),
                            stringResource(R.string.display_mode_title_only_desc),
                            Icons.Default.Title
                        )
                    }

                    takagi.ru.monica.ui.components.SettingsOptionItem(
                        title = modeTitle,
                        description = desc,
                        icon = icon,
                        selected = selected,
                        onClick = {
                            settingsViewModel.updatePasswordCardDisplayMode(mode)
                            displayMenuExpanded = false
                        }
                    )
                }
            }
        }
    }

        // 密码列表 - 使用堆叠分组视图
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    enabled = canCollapseExpandedGroups,
                    interactionSource = outsideTapInteractionSource,
                    indication = null
                ) {
                    expandedGroups = emptySet()
                }
        ) {
            val searchProgress = (pullAction.currentOffset / triggerDistance).coerceIn(0f, 1f)
            val syncProgress = ((pullAction.currentOffset - triggerDistance) / (syncTriggerDistance - triggerDistance))
                .coerceIn(0f, 1f)
            val pullVisualState = when {
                isBitwardenDatabaseView && pullAction.isBitwardenSyncing -> PullActionVisualState.SYNCING
                isBitwardenDatabaseView && pullAction.showSyncFeedback -> PullActionVisualState.SYNC_DONE
                isBitwardenDatabaseView && pullAction.syncHintArmed -> PullActionVisualState.SYNC_READY
                pullAction.currentOffset >= triggerDistance -> PullActionVisualState.SEARCH_READY
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
                PullActionVisualState.SEARCH_READY -> if (isBitwardenDatabaseView) {
                    stringResource(R.string.pull_release_to_search)
                } else {
                    null
                }
                PullActionVisualState.IDLE -> null
            }
            val shouldPinIndicator = isBitwardenDatabaseView && (
                pullAction.syncHintArmed || pullAction.isBitwardenSyncing || pullAction.showSyncFeedback
            )
            val revealHeightTarget = with(density) {
                if (isBitwardenDatabaseView) {
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
                label = "pull_reveal_height"
            )
            val showPullIndicator = pullHintText != null && revealHeight > 0.5.dp
            val contentPullOffset = if (isBitwardenDatabaseView) {
                // Keep sync indicator layout, but move list slightly to avoid sticky drag feel.
                (pullAction.currentOffset * 0.28f).toInt()
            } else {
                pullAction.currentOffset.toInt()
            }

            Column(modifier = Modifier.fillMaxSize()) {
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

                val hasVisibleQuickFilters =
                    appSettings.passwordListQuickFiltersEnabled && configuredQuickFilterItems.isNotEmpty()
                val showPinnedQuickFolderPathBanner = quickFolderBreadcrumbs.isNotEmpty()
                val hasScrollableHeaderContent =
                    hasVisibleQuickFilters || quickFolderShortcuts.isNotEmpty() || showPinnedQuickFolderPathBanner

                if (showPinnedQuickFolderPathBanner) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            quickFolderBreadcrumbs.forEachIndexed { index, crumb ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            color = if (crumb.isCurrent) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.68f)
                                            }
                                        )
                                        .clickable(enabled = !crumb.isCurrent) {
                                            if (currentFilter != crumb.targetFilter) {
                                                viewModel.setCategoryFilter(crumb.targetFilter)
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = crumb.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (crumb.isCurrent) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        }
                                    )
                                }
                                if (index != quickFolderBreadcrumbs.lastIndex) {
                                    Text(
                                        text = ">",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (visiblePasswordEntries.isEmpty() && searchQuery.isEmpty() && !hasScrollableHeaderContent) {
                    // Empty state with pull-to-search
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .offset { androidx.compose.ui.unit.IntOffset(0, contentPullOffset) }
                            .pointerInput(isBitwardenDatabaseView) {
                                detectDragGesturesAfterLongPress(
                                    onDrag = { _, _ -> } // Consume long press to prevent issues
                                )
                            }
                            .pointerInput(isBitwardenDatabaseView) {
                                 detectVerticalDragGestures(
                                    onVerticalDrag = { _, dragAmount ->
                                        pullAction.onVerticalDrag(dragAmount)
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
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                             Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.no_passwords_saved),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = androidx.compose.foundation.lazy.rememberLazyListState(),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .offset { androidx.compose.ui.unit.IntOffset(0, contentPullOffset) }
                            .nestedScroll(pullAction.nestedScrollConnection),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                    if (hasVisibleQuickFilters) {
                        item(key = "quick_filters") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(top = 2.dp, bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                configuredQuickFilterItems.forEach { item ->
                                    when (item) {
                                        takagi.ru.monica.data.PasswordListQuickFilterItem.FAVORITE -> {
                                            FilterChip(
                                                selected = quickFilterFavorite,
                                                onClick = { quickFilterFavorite = !quickFilterFavorite },
                                                label = { Text(text = stringResource(R.string.password_list_quick_filter_favorite)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = if (quickFilterFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                        contentDescription = null
                                                    )
                                                }
                                            )
                                        }

                                        takagi.ru.monica.data.PasswordListQuickFilterItem.TWO_FA -> {
                                            FilterChip(
                                                selected = quickFilter2fa,
                                                onClick = { quickFilter2fa = !quickFilter2fa },
                                                label = { Text(text = stringResource(R.string.password_list_quick_filter_2fa)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Security,
                                                        contentDescription = null
                                                    )
                                                }
                                            )
                                        }

                                        takagi.ru.monica.data.PasswordListQuickFilterItem.NOTES -> {
                                            FilterChip(
                                                selected = quickFilterNotes,
                                                onClick = { quickFilterNotes = !quickFilterNotes },
                                                label = { Text(text = stringResource(R.string.password_list_quick_filter_notes)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Description,
                                                        contentDescription = null
                                                    )
                                                }
                                            )
                                        }

                                        takagi.ru.monica.data.PasswordListQuickFilterItem.UNCATEGORIZED -> {
                                            FilterChip(
                                                selected = quickFilterUncategorized,
                                                onClick = { quickFilterUncategorized = !quickFilterUncategorized },
                                                label = { Text(text = stringResource(R.string.password_list_quick_filter_uncategorized)) }
                                            )
                                        }

                                        takagi.ru.monica.data.PasswordListQuickFilterItem.LOCAL_ONLY -> {
                                            FilterChip(
                                                selected = quickFilterLocalOnly,
                                                onClick = { quickFilterLocalOnly = !quickFilterLocalOnly },
                                                label = { Text(text = stringResource(R.string.password_list_quick_filter_local_only)) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (quickFolderShortcuts.isNotEmpty()) {
                        val quickFolderUseM3CardStyle =
                            quickFolderStyle == takagi.ru.monica.data.PasswordListQuickFolderStyle.M3_CARD
                        item(key = "quick_folder_shortcuts") {
                            if (quickFolderUseM3CardStyle) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp, bottom = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    quickFolderShortcuts.forEach { shortcut ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (currentFilter != shortcut.targetFilter) {
                                                        viewModel.setCategoryFilter(shortcut.targetFilter)
                                                    }
                                                },
                                            colors = CardDefaults.cardColors()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(24.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = shortcut.title,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = stringResource(
                                                            R.string.password_list_quick_folder_count,
                                                            shortcut.passwordCount ?: 0
                                                        ),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(top = 2.dp, bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    quickFolderShortcuts.forEach { shortcut ->
                                        Card(
                                            modifier = Modifier
                                                .size(width = 182.dp, height = 74.dp)
                                                .clickable {
                                                    if (currentFilter != shortcut.targetFilter) {
                                                        viewModel.setCategoryFilter(shortcut.targetFilter)
                                                    }
                                                },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (shortcut.isBack) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
                                                }
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (shortcut.isBack) {
                                                        Icons.AutoMirrored.Filled.KeyboardArrowLeft
                                                    } else {
                                                        Icons.Default.Folder
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier.size(22.dp),
                                                    tint = if (shortcut.isBack) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    if (!shortcut.isBack) {
                                                        Text(
                                                            text = stringResource(
                                                                R.string.password_list_quick_folder_count,
                                                                shortcut.passwordCount ?: 0
                                                            ),
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Text(
                                                        text = shortcut.title,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (shortcut.isBack && shortcut.subtitle.isNotBlank()) {
                                                        Text(
                                                            text = shortcut.subtitle,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (visiblePasswordEntries.isEmpty() && searchQuery.isEmpty()) {
                        item(key = "empty_state_with_quick_headers") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 84.dp, bottom = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.no_passwords_saved),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                    groupedPasswords.forEach { (groupKey, passwords) ->
                    val isExpanded = when (effectiveStackCardMode) {
                        StackCardMode.AUTO -> expandedGroups.contains(groupKey)
                        StackCardMode.ALWAYS_EXPANDED -> true
                    }

                    item(key = "group_$groupKey") {
                        StackedPasswordGroup(
                            website = groupKey,
                            passwords = passwords,
                            isExpanded = isExpanded,
                            stackCardMode = effectiveStackCardMode,
                            swipedItemId = itemToDelete?.id,
                            onToggleExpand = {
                                if (effectiveStackCardMode == StackCardMode.AUTO) {
                                    expandedGroups = if (expandedGroups.contains(groupKey)) {
                                        expandedGroups - groupKey
                                    } else {
                                        expandedGroups + groupKey
                                    }
                                }
                            },
                        onPasswordClick = { password ->
                            if (isSelectionMode) {
                                // 选择模式：切换选择状态
                                selectedPasswords = if (selectedPasswords.contains(password.id)) {
                                    selectedPasswords - password.id
                                } else {
                                    selectedPasswords + password.id
                                }
                            } else {
                                // 普通模式：显示详情页面
                                onPasswordClick(password)
                            }
                        },
                        onSwipeLeft = { password ->
                            // 防止连续滑动导致 itemToDelete 被覆盖
                            if (itemToDelete == null) {
                                // 左滑删除
                                haptic.performWarning()
                                itemToDelete = password
                                deletedItemIds = deletedItemIds + password.id
                            }
                        },
                        onSwipeRight = { password ->
                            // 右滑选择
                            haptic.performSuccess()
                            if (!isSelectionMode) {
                                isSelectionMode = true
                            }
                            selectedPasswords = if (selectedPasswords.contains(password.id)) {
                                selectedPasswords - password.id
                            } else {
                                selectedPasswords + password.id
                            }
                        },
                        onGroupSwipeRight = { groupPasswords ->
                            // 右滑选择整组
                            haptic.performSuccess()
                            if (!isSelectionMode) {
                                isSelectionMode = true
                            }
                            
                            // 检查是否整组都已选中
                            val allSelected = groupPasswords.all { selectedPasswords.contains(it.id) }
                            
                            selectedPasswords = if (allSelected) {
                                // 如果全选了，则取消全选
                                selectedPasswords - groupPasswords.map { it.id }.toSet()
                            } else {
                                // 否则全选（补齐未选中的）
                                selectedPasswords + groupPasswords.map { it.id }
                            }
                        },
                        onToggleFavorite = { password ->
                            viewModel.toggleFavorite(password.id, !password.isFavorite)
                        },
                        onToggleGroupFavorite = {
                            // 智能切换整组收藏状态
                            coroutineScope.launch {
                                val allFavorited = passwords.all { it.isFavorite }
                                val newState = !allFavorited
                                
                                passwords.forEach { password ->
                                    viewModel.toggleFavorite(password.id, newState)
                                }
                                
                                val message = if (newState) {
                                    context.getString(R.string.group_favorited, passwords.size)
                                } else {
                                    context.getString(R.string.group_unfavorited, passwords.size)
                                }
                                
                                android.widget.Toast.makeText(
                                    context,
                                    message,
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onToggleGroupCover = { password ->
                            // 切换封面状态并移到顶部
                            coroutineScope.launch {
                                val websiteKey = password.website.ifBlank { context.getString(R.string.filter_uncategorized) }
                                val newCoverState = !password.isGroupCover
                                
                                if (newCoverState) {
                                    // 设置为封面时,同时移到组内第一位
                                    val groupPasswords = passwords
                                    val currentIndex = groupPasswords.indexOfFirst { it.id == password.id }
                                    
                                    if (currentIndex > 0) {
                                        // 需要移动到顶部
                                        val reordered = groupPasswords.toMutableList()
                                        val item = reordered.removeAt(currentIndex)
                                        reordered.add(0, item) // 移到第一位
                                        
                                        // 更新sortOrder
                                        val allPasswords = passwordEntries
                                        val firstItemInGroup = allPasswords.firstOrNull {
                                            it.website.ifBlank { context.getString(R.string.filter_uncategorized) } == websiteKey
                                        } ?: return@launch
                                        val startSortOrder = allPasswords.indexOf(firstItemInGroup)
                                        
                                        viewModel.updateSortOrders(
                                            reordered.mapIndexed { idx, entry -> 
                                                entry.id to (startSortOrder + idx)
                                            }
                                        )
                                    }
                                }
                                
                                // 设置/取消封面
                                viewModel.toggleGroupCover(password.id, websiteKey, newCoverState)
                            }
                        },
                        isSelectionMode = isSelectionMode,
                        selectedPasswords = selectedPasswords,
                        onToggleSelection = { id ->
                            selectedPasswords = if (selectedPasswords.contains(id)) {
                                selectedPasswords - id
                            } else {
                                selectedPasswords + id
                            }
                        },
                        onOpenMultiPasswordDialog = { passwords ->
                            // 导航到详情页面 (现在详情页面支持多密码)
                            onPasswordClick(passwords.first())
                        },
                        onLongClick = { password ->
                            // 长按进入多选模式
                            haptic.performLongPress()
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedPasswords = setOf(password.id)
                            }
                        },
                        iconCardsEnabled = appSettings.iconCardsEnabled && appSettings.passwordPageIconEnabled,
                        unmatchedIconHandlingStrategy = appSettings.unmatchedIconHandlingStrategy,
                        passwordCardDisplayMode = appSettings.passwordCardDisplayMode,
                        passwordCardDisplayFields = appSettings.passwordCardDisplayFields,
                        showAuthenticator = appSettings.passwordCardShowAuthenticator,
                        hideOtherContentWhenAuthenticator = appSettings.passwordCardHideOtherContentWhenAuthenticator,
                        totpTimeOffsetSeconds = appSettings.totpTimeOffset,
                        smoothAuthenticatorProgress = appSettings.validatorSmoothProgress,
                        enableSharedBounds = !isLocalOnlyView
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
    
    // 批量删除验证对话框（统一 M3 身份验证弹窗）
    if (showBatchDeleteDialog) {
        val biometricAction = if (canUseBiometric) {
            {
                val hostActivity = activity
                if (hostActivity == null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.biometric_not_available),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    biometricHelper.authenticate(
                        activity = hostActivity,
                        title = context.getString(R.string.verify_identity),
                        subtitle = context.getString(R.string.verify_to_delete),
                        onSuccess = {
                            coroutineScope.launch {
                                val toDelete = passwordEntries.filter { selectedPasswords.contains(it.id) }
                                toDelete.forEach { viewModel.deletePasswordEntry(it) }
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.deleted_items, toDelete.size),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                isSelectionMode = false
                                selectedPasswords = setOf()
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
            }
        } else {
            null
        }
        M3IdentityVerifyDialog(
            title = stringResource(R.string.verify_identity),
            message = stringResource(R.string.batch_delete_passwords_message, selectedPasswords.size),
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
                        val toDelete = passwordEntries.filter { selectedPasswords.contains(it.id) }
                        toDelete.forEach { viewModel.deletePasswordEntry(it) }
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.deleted_items, toDelete.size),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        isSelectionMode = false
                        selectedPasswords = setOf()
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
    

    
    

    // 单项删除确认对话框(支持指纹和密码验证)
    itemToDelete?.let { item ->
        DeleteConfirmDialog(
            itemTitle = item.title,
            itemType = stringResource(R.string.item_type_password),
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
                coroutineScope.launch {
                    viewModel.deletePasswordEntry(item)
                    Toast.makeText(
                        context,
                        context.getString(R.string.deleted),
                        Toast.LENGTH_SHORT
                    ).show()
                    itemToDelete = null
                }
            }
        )
    }
    
    // 单项删除密码验证
    if (showSingleItemPasswordVerify && itemToDelete != null) {
        val pendingDeleteItem = itemToDelete ?: return
        LaunchedEffect(pendingDeleteItem.id, showSingleItemPasswordVerify) {
            val securityManager = takagi.ru.monica.security.SecurityManager(context)
            if (securityManager.verifyMasterPassword(singleItemPasswordInput)) {
                // 密码正确，执行真实删除
                viewModel.deletePasswordEntry(pendingDeleteItem)
                
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
                deletedItemIds = deletedItemIds - pendingDeleteItem.id
                
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
}
}
}

private const val QUICK_FOLDER_ROOT_ALL = "all"
private const val QUICK_FOLDER_ROOT_LOCAL = "local"
private const val QUICK_FOLDER_ROOT_STARRED = "starred"
private const val QUICK_FOLDER_ROOT_UNCATEGORIZED = "uncategorized"
private const val QUICK_FOLDER_ROOT_LOCAL_STARRED = "local_starred"
private const val QUICK_FOLDER_ROOT_LOCAL_UNCATEGORIZED = "local_uncategorized"

private data class PasswordQuickFolderNode(
    val category: Category,
    val path: String,
    val parentPath: String?,
    val displayName: String
)

private data class PasswordQuickFolderShortcut(
    val key: String,
    val title: String,
    val subtitle: String,
    val isBack: Boolean,
    val targetFilter: CategoryFilter,
    val passwordCount: Int?
)

private data class PasswordQuickFolderBreadcrumb(
    val key: String,
    val title: String,
    val targetFilter: CategoryFilter,
    val isCurrent: Boolean
)

private fun normalizePasswordQuickFolderPath(path: String): String {
    return path
        .split("/")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString("/")
}

private fun passwordQuickFolderParentPath(path: String): String? {
    val normalized = normalizePasswordQuickFolderPath(path)
    if (!normalized.contains('/')) return null
    return normalized.substringBeforeLast('/').ifBlank { null }
}

private fun buildPasswordQuickFolderNodes(categories: List<Category>): List<PasswordQuickFolderNode> {
    return categories
        .sortedWith(
            compareBy<Category>({ it.sortOrder }, { it.id })
        )
        .mapNotNull { category ->
            val normalizedPath = normalizePasswordQuickFolderPath(category.name)
            if (normalizedPath.isBlank()) {
                null
            } else {
                PasswordQuickFolderNode(
                    category = category,
                    path = normalizedPath,
                    parentPath = passwordQuickFolderParentPath(normalizedPath),
                    displayName = normalizedPath.substringAfterLast('/')
                )
            }
        }
        .distinctBy { it.path }
}

private fun CategoryFilter.supportsQuickFolders(): Boolean = when (this) {
    is CategoryFilter.All,
    is CategoryFilter.Local,
    is CategoryFilter.Starred,
    is CategoryFilter.Uncategorized,
    is CategoryFilter.LocalStarred,
    is CategoryFilter.LocalUncategorized,
    is CategoryFilter.Custom -> true

    else -> false
}

private fun CategoryFilter.toQuickFolderRootKeyOrNull(): String? = when (this) {
    is CategoryFilter.All -> QUICK_FOLDER_ROOT_ALL
    is CategoryFilter.Local -> QUICK_FOLDER_ROOT_LOCAL
    is CategoryFilter.Starred -> QUICK_FOLDER_ROOT_STARRED
    is CategoryFilter.Uncategorized -> QUICK_FOLDER_ROOT_UNCATEGORIZED
    is CategoryFilter.LocalStarred -> QUICK_FOLDER_ROOT_LOCAL_STARRED
    is CategoryFilter.LocalUncategorized -> QUICK_FOLDER_ROOT_LOCAL_UNCATEGORIZED
    else -> null
}

private fun String.toQuickFolderRootFilter(): CategoryFilter = when (this) {
    QUICK_FOLDER_ROOT_LOCAL -> CategoryFilter.Local
    QUICK_FOLDER_ROOT_STARRED -> CategoryFilter.Starred
    QUICK_FOLDER_ROOT_UNCATEGORIZED -> CategoryFilter.Uncategorized
    QUICK_FOLDER_ROOT_LOCAL_STARRED -> CategoryFilter.LocalStarred
    QUICK_FOLDER_ROOT_LOCAL_UNCATEGORIZED -> CategoryFilter.LocalUncategorized
    else -> CategoryFilter.All
}
