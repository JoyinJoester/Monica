package takagi.ru.monica.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
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
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel
import takagi.ru.monica.viewmodel.TotpViewModel
import takagi.ru.monica.viewmodel.CategoryFilter
import takagi.ru.monica.data.Category
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.viewmodel.GeneratorViewModel
import takagi.ru.monica.viewmodel.NoteViewModel
import takagi.ru.monica.viewmodel.PasskeyViewModel
import takagi.ru.monica.ui.screens.SettingsScreen
import takagi.ru.monica.ui.screens.GeneratorScreen  // 添加生成器页面导入
import takagi.ru.monica.ui.screens.NoteListScreen
import takagi.ru.monica.ui.screens.NoteListContent
import takagi.ru.monica.ui.screens.CardWalletScreen
import takagi.ru.monica.ui.screens.CardWalletTab
import takagi.ru.monica.ui.screens.TimelineScreen
import takagi.ru.monica.ui.screens.PasskeyListScreen
import takagi.ru.monica.ui.gestures.SwipeActions
import takagi.ru.monica.ui.haptic.rememberHapticFeedback
import kotlin.math.absoluteValue

import takagi.ru.monica.ui.components.QrCodeDialog
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.components.DraggableBottomNavScaffold
import takagi.ru.monica.ui.components.SwipeableAddFab
import takagi.ru.monica.ui.components.DraggableNavItem
import takagi.ru.monica.ui.components.QuickActionItem
import takagi.ru.monica.ui.components.QuickAddCallback
import takagi.ru.monica.security.SecurityManager
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import takagi.ru.monica.ui.screens.AddEditPasswordScreen
import takagi.ru.monica.ui.screens.AddEditTotpScreen
import takagi.ru.monica.ui.screens.AddEditBankCardScreen
import takagi.ru.monica.ui.screens.AddEditDocumentScreen
import takagi.ru.monica.ui.screens.AddEditNoteScreen

@Composable
private fun SelectionActionBar(
    modifier: Modifier = Modifier,
    selectedCount: Int,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onFavorite: (() -> Unit)? = null,
    onMoveToCategory: (() -> Unit)? = null,
    onDelete: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 选中数量徽章
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

            ActionIcon(
                icon = Icons.Outlined.CheckCircle,
                contentDescription = stringResource(id = R.string.select_all),
                onClick = onSelectAll
            )

            onFavorite?.let {
                ActionIcon(
                    icon = Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(id = R.string.favorite),
                    onClick = it
                )
            }

            onMoveToCategory?.let {
                ActionIcon(
                    icon = Icons.Outlined.Label,
                    contentDescription = stringResource(id = R.string.move_to_category),
                    onClick = it
                )
            }

            ActionIcon(
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(id = R.string.delete),
                onClick = onDelete
            )

            Spacer(modifier = Modifier.width(4.dp))

            ActionIcon(
                icon = Icons.Default.Close,
                contentDescription = stringResource(id = R.string.close),
                onClick = onExit
            )
        }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
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
 * 带有底部导航的主屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleMainScreen(
    passwordViewModel: PasswordViewModel,
    settingsViewModel: SettingsViewModel,
    totpViewModel: takagi.ru.monica.viewmodel.TotpViewModel,
    bankCardViewModel: takagi.ru.monica.viewmodel.BankCardViewModel,
    documentViewModel: takagi.ru.monica.viewmodel.DocumentViewModel,
    generatorViewModel: GeneratorViewModel = viewModel(), // 添加GeneratorViewModel
    noteViewModel: NoteViewModel = viewModel(),
    passkeyViewModel: PasskeyViewModel,  // Passkey ViewModel
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    securityManager: SecurityManager,
    onNavigateToAddPassword: (Long?) -> Unit,
    onNavigateToAddTotp: (Long?) -> Unit,
    onNavigateToQuickTotpScan: () -> Unit,
    onNavigateToAddBankCard: (Long?) -> Unit,
    onNavigateToAddDocument: (Long?) -> Unit,
    onNavigateToAddNote: (Long?) -> Unit,
    onNavigateToPasswordDetail: (Long) -> Unit = {},
    onNavigateToBankCardDetail: (Long) -> Unit, // Add this
    onNavigateToDocumentDetail: (Long) -> Unit, // Keep this
    onNavigateToChangePassword: () -> Unit = {},
    onNavigateToSecurityQuestion: () -> Unit = {},
    onNavigateToSyncBackup: () -> Unit = {},
    onNavigateToAutofill: () -> Unit = {},
    onNavigateToPasskeySettings: () -> Unit = {},
    onNavigateToBottomNavSettings: () -> Unit = {},
    onNavigateToColorScheme: () -> Unit = {},
    onSecurityAnalysis: () -> Unit = {},
    onNavigateToDeveloperSettings: () -> Unit = {},
    onNavigateToPermissionManagement: () -> Unit = {},
    onNavigateToMonicaPlus: () -> Unit = {},
    onNavigateToExtensions: () -> Unit = {},
    onClearAllData: (Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    initialTab: Int = 0
) {

    
    // 双击返回退出相关状态
    var backPressedOnce by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 处理返回键 - 需要按两次才能退出
    // 只有在没有子页面（如添加页面）打开时才启用
    // FAB 展开状态由内部 SwipeableAddFab 管理，这里不需要干预，除非我们需要在 FAB 展开时拦截返回键
    // 目前 SwipeableAddFab 应该自己处理了返回键（如果有 BackHandler）
    // 为了安全起见，我们只在最外层处理
    BackHandler(enabled = true) {
        if (backPressedOnce) {
            // 第二次按返回键,退出应用
            (context as? android.app.Activity)?.finish()
        } else {
            // 第一次按返回键,显示提示
            backPressedOnce = true
            Toast.makeText(
                context,
                context.getString(R.string.press_back_again_to_exit),
                Toast.LENGTH_SHORT
            ).show()
            
            // 2秒后重置状态
            scope.launch {
                delay(2000)
                backPressedOnce = false
            }
        }
    }
    
    // 密码列表的选择模式状态
    var isPasswordSelectionMode by remember { mutableStateOf(false) }
    var selectedPasswordCount by remember { mutableIntStateOf(0) }
    var onExitPasswordSelection by remember { mutableStateOf({}) }
    var onSelectAllPasswords by remember { mutableStateOf({}) }
    var onFavoriteSelectedPasswords by remember { mutableStateOf({}) }
    var onMoveToCategoryPasswords by remember { mutableStateOf({}) }
    var onDeleteSelectedPasswords by remember { mutableStateOf({}) }
    
    val appSettings by settingsViewModel.settings.collectAsState()
    
    // 密码分组模式: smart(备注>网站>应用>标题), note, website, app, title
    // 从设置中读取，如果设置中没有则默认为 "smart"
    val passwordGroupMode = appSettings.passwordGroupMode


    // 堆叠卡片显示模式: 自动/始终展开（始终展开指逐条显示，不堆叠）
    // 从设置中读取，如果设置中没有则默认为 AUTO
    val stackCardModeKey = appSettings.stackCardMode
    val stackCardMode = remember(stackCardModeKey) {
        runCatching { StackCardMode.valueOf(stackCardModeKey) }.getOrDefault(StackCardMode.AUTO)
    }
    var displayMenuExpanded by remember { mutableStateOf(false) }
    
    // TOTP的选择模式状态
    var isTotpSelectionMode by remember { mutableStateOf(false) }
    var selectedTotpCount by remember { mutableIntStateOf(0) }
    var onExitTotpSelection by remember { mutableStateOf({}) }
    var onSelectAllTotp by remember { mutableStateOf({}) }
    var onDeleteSelectedTotp by remember { mutableStateOf({}) }
    
    // 证件的选择模式状态
    var isDocumentSelectionMode by remember { mutableStateOf(false) }
    var selectedDocumentCount by remember { mutableIntStateOf(0) }
    var onExitDocumentSelection by remember { mutableStateOf({}) }
    var onSelectAllDocuments by remember { mutableStateOf({}) }
    var onDeleteSelectedDocuments by remember { mutableStateOf({}) }
    
    // 银行卡的选择模式状态
    var isBankCardSelectionMode by remember { mutableStateOf(false) }
    var selectedBankCardCount by remember { mutableIntStateOf(0) }
    var onExitBankCardSelection by remember { mutableStateOf({}) }
    var onSelectAllBankCards by remember { mutableStateOf({}) }
    var onDeleteSelectedBankCards by remember { mutableStateOf({}) }
    var onFavoriteBankCards by remember { mutableStateOf({}) }  // 添加收藏回调

    // CardWallet state
    var cardWalletSubTab by rememberSaveable { mutableStateOf(CardWalletTab.BANK_CARDS) }

    val bottomNavVisibility = appSettings.bottomNavVisibility

    val dataTabItems = appSettings.bottomNavOrder
        .map { it.toBottomNavItem() }
        .filter { item ->
            val tab = item.contentTab
            tab == null || bottomNavVisibility.isVisible(tab)
        }

    val tabs = buildList {
        addAll(dataTabItems)
        add(BottomNavItem.Settings)
    }

    val defaultTabKey = remember(initialTab, tabs) { 
        if (initialTab == 0 && tabs.isNotEmpty()) {
            tabs.first().key
        } else {
            indexToDefaultTabKey(initialTab) 
        }
    }
    var selectedTabKey by rememberSaveable { mutableStateOf(defaultTabKey) }

    LaunchedEffect(tabs) {
        if (tabs.none { it.key == selectedTabKey }) {
            selectedTabKey = tabs.first().key
        }
    }

    val currentTab = tabs.firstOrNull { it.key == selectedTabKey } ?: tabs.first()
    val currentTabLabel = stringResource(currentTab.fullLabelRes())

    // 监听滚动以隐藏/显示 FAB
    var isFabVisible by remember { mutableStateOf(true) }
    
    // 如果设置中禁用了此功能，强制显示 FAB
    LaunchedEffect(appSettings.hideFabOnScroll) {
        if (!appSettings.hideFabOnScroll) {
            isFabVisible = true
        }
    }

    // 监听 FAB 展开状态，展开时禁用隐藏逻辑
    var isFabExpanded by remember { mutableStateOf(false) }
    // 使用 rememberUpdatedState 确保 currentTab 始终是最新的
    val currentTabState = rememberUpdatedState(currentTab)
    // 确保滚动监听器能获取到最新的设置值
    val hideFabOnScrollState = rememberUpdatedState(appSettings.hideFabOnScroll)

    // 检测是否有任何选择模式处于激活状态
    var isNoteSelectionMode by remember { mutableStateOf(false) }
    val isAnySelectionMode = isPasswordSelectionMode || isTotpSelectionMode || isDocumentSelectionMode || isBankCardSelectionMode || isNoteSelectionMode
    
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            // 使用 onPostScroll 代替 onPreScroll
            // 只有当子视图实际消费了滚动事件时（即真正滚动了内容），我们才根据方向判断显隐
            // 这样可以解决：
            // 1. 在页面顶部无法上滑时，FAB 不会错误隐藏
            // 2. 内容太少不足以滚动时，FAB 保持显示
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // 如果功能未开启，直接返回
                if (!hideFabOnScrollState.value) return Offset.Zero

                // 如果 FAB 已展开，不要隐藏它（防止在添加页面滚动时误触导致页面关闭）
                if (isFabExpanded) return Offset.Zero

                val tab = currentTabState.value
                if (tab == BottomNavItem.Passwords || 
                    tab == BottomNavItem.Authenticator || 
                    tab == BottomNavItem.CardWallet) {
                    
                    // consumed.y < 0 表示内容向上滚动（手指上滑，查看下方内容） -> 隐藏
                    if (consumed.y < -15f) {
                        isFabVisible = false
                    } 
                    // consumed.y > 0 表示内容向下滚动（手指下滑，回到顶部） -> 显示
                    // 注意：如果是 available.y > 0 但 consumed.y == 0，说明已经到顶滑不动了，
                    // 这种情况下我们也不隐藏（保持原状或强制显示），通常保持原状即可，
                    // 但为了体验，如果在顶部尝试下滑（即使没动），也可以强制显示
                    else if (consumed.y > 15f || (available.y > 0f && consumed.y == 0f)) {
                         isFabVisible = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    val categories by passwordViewModel.categories.collectAsState()
    val currentFilter by passwordViewModel.categoryFilter.collectAsState()
    val keepassDatabases by localKeePassViewModel.allDatabases.collectAsState()
    
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showEditCategoryDialog by remember { mutableStateOf<Category?>(null) }
    var categoryNameInput by remember { mutableStateOf("") }
    // 可拖拽导航栏模式开关 (将来可从设置中读取)
    val useDraggableNav = appSettings.useDraggableBottomNav
    
    // 构建导航项列表 (用于可拖拽导航栏)
    val draggableNavItems = remember(tabs, currentTab) {
        tabs.map { item ->
            DraggableNavItem(
                key = item.key,
                icon = item.icon,
                labelRes = item.shortLabelRes(),
                selected = item.key == currentTab.key,
                onClick = { selectedTabKey = item.key }
            )
        }
    }
    
    // 获取颜色（在 Composable 上下文中）
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    
    // 构建快捷操作列表
    val quickActions = remember(
        onNavigateToAddPassword,
        onNavigateToAddTotp,
        onNavigateToQuickTotpScan,
        onNavigateToAddBankCard,
        onNavigateToAddDocument,
        onNavigateToAddNote,
        onSecurityAnalysis,
        onNavigateToSyncBackup,
        tertiaryColor,
        secondaryColor
    ) {
        listOf(
            QuickActionItem(
                icon = Icons.Default.Lock,
                labelRes = R.string.quick_action_add_password,
                onClick = { onNavigateToAddPassword(null) }
            ),
            QuickActionItem(
                icon = Icons.Default.Security,
                labelRes = R.string.quick_action_add_totp,
                onClick = { onNavigateToAddTotp(null) }
            ),
            QuickActionItem(
                icon = Icons.Default.QrCodeScanner,
                labelRes = R.string.quick_action_scan_qr,
                onClick = onNavigateToQuickTotpScan
            ),
            QuickActionItem(
                icon = Icons.Default.CreditCard,
                labelRes = R.string.quick_action_add_card,
                onClick = { onNavigateToAddBankCard(null) }
            ),
            QuickActionItem(
                icon = Icons.Default.Badge,
                labelRes = R.string.quick_action_add_document,
                onClick = { onNavigateToAddDocument(null) }
            ),
            QuickActionItem(
                icon = Icons.Default.Note,
                labelRes = R.string.quick_action_add_note,
                onClick = { onNavigateToAddNote(null) }
            ),
            QuickActionItem(
                icon = Icons.Default.AutoAwesome,
                labelRes = R.string.quick_action_generator,
                onClick = { selectedTabKey = BottomNavItem.Generator.key }
            ),
            QuickActionItem(
                icon = Icons.Default.Shield,
                labelRes = R.string.quick_action_security,
                onClick = onSecurityAnalysis,
                tint = tertiaryColor
            ),
            QuickActionItem(
                icon = Icons.Default.CloudUpload,
                labelRes = R.string.quick_action_backup,
                onClick = onNavigateToSyncBackup,
                tint = secondaryColor
            ),
            QuickActionItem(
                icon = Icons.Default.Download,
                labelRes = R.string.quick_action_import,
                onClick = onNavigateToSyncBackup
            ),
            QuickActionItem(
                icon = Icons.Default.Settings,
                labelRes = R.string.quick_action_settings,
                onClick = { selectedTabKey = BottomNavItem.Settings.key }
            )
        )
    }
    
    // 根据设置选择导航模式
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        if (useDraggableNav) {
        // 使用可拖拽底部导航栏
        DraggableBottomNavScaffold(
            navItems = draggableNavItems,

            quickAddCallback = QuickAddCallback(
                onAddPassword = { title, username, password ->
                    passwordViewModel.quickAddPassword(title, username, password)
                },
                onAddTotp = { name, secret ->
                    totpViewModel.quickAddTotp(name, secret)
                },
                onAddBankCard = { name, number ->
                    bankCardViewModel.quickAddBankCard(name, number)
                },
                onAddNote = { title, content ->
                    noteViewModel.quickAddNote(title, content)
                }
            ),
            floatingActionButton = {}, // FAB 移至外层 Overlay
            content = { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (currentTab) {
                        BottomNavItem.Passwords -> {
                            PasswordListContent(
                                viewModel = passwordViewModel,
                                settingsViewModel = settingsViewModel,
                                securityManager = securityManager,
                                keepassDatabases = keepassDatabases,
                                localKeePassViewModel = localKeePassViewModel,
                                groupMode = passwordGroupMode,
                                stackCardMode = stackCardMode,
                                onCreateCategory = {
                                    categoryNameInput = ""
                                    showAddCategoryDialog = true
                                },
                                onRenameCategory = { category ->
                                    categoryNameInput = category.name
                                    showEditCategoryDialog = category
                                },
                                onDeleteCategory = { category ->
                                    passwordViewModel.deleteCategory(category)
                                },
                                onPasswordClick = { password ->
                                    onNavigateToAddPassword(password.id)
                                },
                                onNavigateToAddPassword = onNavigateToAddPassword,
                                onNavigateToPasswordDetail = onNavigateToPasswordDetail,
                                                                onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onFavorite, onMoveToCategory, onDelete ->
                                    isPasswordSelectionMode = isSelectionMode
                                    selectedPasswordCount = count
                                    onExitPasswordSelection = onExit
                                    onSelectAllPasswords = onSelectAll
                                    onFavoriteSelectedPasswords = onFavorite
                                    onMoveToCategoryPasswords = onMoveToCategory
                                    onDeleteSelectedPasswords = onDelete
                                }
                            )
                        }
                        BottomNavItem.Authenticator -> {
                            TotpListContent(
                                viewModel = totpViewModel,
                                onTotpClick = { totpId ->
                                    onNavigateToAddTotp(totpId)
                                },
                                onDeleteTotp = { totp ->
                                    totpViewModel.deleteTotpItem(totp)
                                },
                                onQuickScanTotp = onNavigateToQuickTotpScan,
                                onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onDelete ->
                                    isTotpSelectionMode = isSelectionMode
                                    selectedTotpCount = count
                                    onExitTotpSelection = onExit
                                    onSelectAllTotp = onSelectAll
                                    onDeleteSelectedTotp = onDelete
                                }
                            )
                        }
                        BottomNavItem.CardWallet -> {
                            CardWalletScreen(
                                bankCardViewModel = bankCardViewModel,
                                documentViewModel = documentViewModel,
                                currentTab = cardWalletSubTab,
                                onTabSelected = { cardWalletSubTab = it },
                                onCardClick = { cardId ->
                                    onNavigateToBankCardDetail(cardId)
                                },
                                onDocumentClick = { documentId ->
                                    onNavigateToDocumentDetail(documentId)
                                },
                                onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onDelete ->
                                    isDocumentSelectionMode = isSelectionMode
                                    selectedDocumentCount = count
                                    onExitDocumentSelection = onExit
                                    onSelectAllDocuments = onSelectAll
                                    onDeleteSelectedDocuments = onDelete
                                },
                                onBankCardSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onDelete, onFavorite ->
                                    isBankCardSelectionMode = isSelectionMode
                                    selectedBankCardCount = count
                                    onExitBankCardSelection = onExit
                                    onSelectAllBankCards = onSelectAll
                                    onDeleteSelectedBankCards = onDelete
                                    onFavoriteBankCards = onFavorite
                                }
                            )
                        }
                        BottomNavItem.Generator -> {
                            GeneratorScreen(
                                onNavigateBack = {},
                                viewModel = generatorViewModel,
                                passwordViewModel = passwordViewModel
                            )
                        }
                        BottomNavItem.Notes -> {
                            NoteListScreen(
                                viewModel = noteViewModel,
                                settingsViewModel = settingsViewModel,
                                onNavigateToAddNote = onNavigateToAddNote,
                                securityManager = securityManager,
                                onSelectionModeChange = { isSelectionMode ->
                                    isNoteSelectionMode = isSelectionMode
                                }
                            )
                        }
                        BottomNavItem.Timeline -> {
                            TimelineScreen()
                        }
                        BottomNavItem.Passkey -> {
                            PasskeyListScreen(
                                viewModel = passkeyViewModel,
                                onPasskeyClick = { /* TODO: 导航到详情页 */ }
                            )
                        }
                        BottomNavItem.Settings -> {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateBack = {},
                                onResetPassword = onNavigateToChangePassword,
                                onSecurityQuestions = onNavigateToSecurityQuestion,
                                onNavigateToSyncBackup = onNavigateToSyncBackup,
                                onNavigateToAutofill = onNavigateToAutofill,
                                onNavigateToPasskeySettings = onNavigateToPasskeySettings,
                                onNavigateToBottomNavSettings = onNavigateToBottomNavSettings,
                                onNavigateToColorScheme = onNavigateToColorScheme,
                                onSecurityAnalysis = onSecurityAnalysis,
                                onNavigateToDeveloperSettings = onNavigateToDeveloperSettings,
                                onNavigateToPermissionManagement = onNavigateToPermissionManagement,
                                onNavigateToMonicaPlus = onNavigateToMonicaPlus,
                                onNavigateToExtensions = onNavigateToExtensions,
                                onClearAllData = onClearAllData,
                                showTopBar = false
                            )
                        }
                    }

                    // Selection Action Bars
                    when {
                        currentTab == BottomNavItem.Passwords && isPasswordSelectionMode -> {
                            SelectionActionBar(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                                selectedCount = selectedPasswordCount,
                                onExit = onExitPasswordSelection,
                                onSelectAll = onSelectAllPasswords,
                                onFavorite = onFavoriteSelectedPasswords,
                                onMoveToCategory = onMoveToCategoryPasswords,
                                onDelete = onDeleteSelectedPasswords
                            )
                        }
                        currentTab == BottomNavItem.Authenticator && isTotpSelectionMode -> {
                            SelectionActionBar(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                                selectedCount = selectedTotpCount,
                                onExit = onExitTotpSelection,
                                onSelectAll = onSelectAllTotp,
                                onDelete = onDeleteSelectedTotp
                            )
                        }
                        currentTab == BottomNavItem.CardWallet && cardWalletSubTab == CardWalletTab.BANK_CARDS && isBankCardSelectionMode -> {
                            SelectionActionBar(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                                selectedCount = selectedBankCardCount,
                                onExit = onExitBankCardSelection,
                                onSelectAll = onSelectAllBankCards,
                                onFavorite = onFavoriteBankCards,
                                onDelete = onDeleteSelectedBankCards
                            )
                        }
                        currentTab == BottomNavItem.CardWallet && cardWalletSubTab == CardWalletTab.DOCUMENTS && isDocumentSelectionMode -> {
                            SelectionActionBar(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                                selectedCount = selectedDocumentCount,
                                onExit = onExitDocumentSelection,
                                onSelectAll = onSelectAllDocuments,
                                onDelete = onDeleteSelectedDocuments
                            )
                        }
                    }
                }
            }
        )
    } else {
        // 使用传统底部导航栏
    Scaffold(
        topBar = {
            // 顶部栏由各自页面内部控制（如 ExpressiveTopBar），这里保持为空以避免叠加
        },
        bottomBar = {
            NavigationBar(
                tonalElevation = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEach { item ->
                    val label = stringResource(item.shortLabelRes())
                    NavigationBarItem(
                        icon = { 
                            Icon(item.icon, contentDescription = label) 
                        },
                        label = { 
                            Text(label) 
                        },
                        selected = item.key == currentTab.key,
                        onClick = { selectedTabKey = item.key }
                    )
                }
            }
        },
        floatingActionButton = {} // FAB 移至外层 Overlay
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentTab) {
                BottomNavItem.Passwords -> {
                    // 密码页面 - 使用现有的密码列表
                    PasswordListContent(
                        viewModel = passwordViewModel,
                        settingsViewModel = settingsViewModel, // Pass SettingsViewModel
                        securityManager = securityManager,
                        keepassDatabases = keepassDatabases,
                        localKeePassViewModel = localKeePassViewModel,
                        groupMode = passwordGroupMode,
                        stackCardMode = stackCardMode,
                        onCreateCategory = {
                            categoryNameInput = ""
                            showAddCategoryDialog = true
                        },
                        onRenameCategory = { category ->
                            categoryNameInput = category.name
                            showEditCategoryDialog = category
                        },
                        onDeleteCategory = { category ->
                            passwordViewModel.deleteCategory(category)
                        },
                        onPasswordClick = { password ->
                            onNavigateToAddPassword(password.id)
                        },
                        onNavigateToAddPassword = onNavigateToAddPassword,
                        onNavigateToPasswordDetail = onNavigateToPasswordDetail,
                        onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onFavorite, onMoveToCategory, onDelete ->
                            isPasswordSelectionMode = isSelectionMode
                            selectedPasswordCount = count
                            onExitPasswordSelection = onExit
                            onSelectAllPasswords = onSelectAll
                            onFavoriteSelectedPasswords = onFavorite
                            onMoveToCategoryPasswords = onMoveToCategory
                            onDeleteSelectedPasswords = onDelete
                        }
                    )
                }
                BottomNavItem.Authenticator -> {
                    // TOTP验证器页面
                    TotpListContent(
                        viewModel = totpViewModel,
                        onTotpClick = { totpId ->
                            onNavigateToAddTotp(totpId)
                        },
                        onDeleteTotp = { totp ->
                            totpViewModel.deleteTotpItem(totp)
                        },
                        onQuickScanTotp = onNavigateToQuickTotpScan,
                        onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onDelete ->
                            isTotpSelectionMode = isSelectionMode
                            selectedTotpCount = count
                            onExitTotpSelection = onExit
                            onSelectAllTotp = onSelectAll
                            onDeleteSelectedTotp = onDelete
                        }
                    )
                }
                BottomNavItem.CardWallet -> {
                    // 卡包页面
                    CardWalletScreen(
                        bankCardViewModel = bankCardViewModel,
                        documentViewModel = documentViewModel,
                        currentTab = cardWalletSubTab,
                        onTabSelected = { cardWalletSubTab = it },
                        onCardClick = { cardId ->
                            onNavigateToBankCardDetail(cardId)
                        },
                        onDocumentClick = { documentId ->
                            onNavigateToDocumentDetail(documentId)
                        },
                        onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onDelete ->
                            isDocumentSelectionMode = isSelectionMode
                            selectedDocumentCount = count
                            onExitDocumentSelection = onExit
                            onSelectAllDocuments = onSelectAll
                            onDeleteSelectedDocuments = onDelete
                        },
                        onBankCardSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onDelete, onFavorite ->
                            isBankCardSelectionMode = isSelectionMode
                            selectedBankCardCount = count
                            onExitBankCardSelection = onExit
                            onSelectAllBankCards = onSelectAll
                            onDeleteSelectedBankCards = onDelete
                            onFavoriteBankCards = onFavorite
                        }
                    )
                }
                BottomNavItem.Generator -> {
                    // 生成器页面
                    GeneratorScreen(
                        onNavigateBack = {}, // 在主屏幕中不需要返回
                        viewModel = generatorViewModel, // 传递ViewModel
                        passwordViewModel = passwordViewModel // 传递 PasswordViewModel
                    )
                }
                BottomNavItem.Notes -> {
                    // 笔记页面
                    NoteListScreen(
                        viewModel = noteViewModel,
                        settingsViewModel = settingsViewModel,
                        onNavigateToAddNote = onNavigateToAddNote,
                        securityManager = securityManager,
                        onSelectionModeChange = { isSelectionMode ->
                            isNoteSelectionMode = isSelectionMode
                        }
                    )
                }
                BottomNavItem.Timeline -> {
                    // 时间线页面
                    TimelineScreen()
                }
                BottomNavItem.Passkey -> {
                    // 通行密钥页面
                    PasskeyListScreen(
                        viewModel = passkeyViewModel,
                        onPasskeyClick = { /* TODO: 导航到详情页 */ }
                    )
                }
                BottomNavItem.Settings -> {
                    // 设置页面 - 使用完整的SettingsScreen
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateBack = {}, // 在主屏幕中不需要返回
                        onResetPassword = onNavigateToChangePassword,
                        onSecurityQuestions = onNavigateToSecurityQuestion,
                        onNavigateToSyncBackup = onNavigateToSyncBackup,
                        onNavigateToAutofill = onNavigateToAutofill,
                        onNavigateToBottomNavSettings = onNavigateToBottomNavSettings,
                        onNavigateToColorScheme = onNavigateToColorScheme,
                        onSecurityAnalysis = onSecurityAnalysis,
                        onNavigateToDeveloperSettings = onNavigateToDeveloperSettings,
                        onNavigateToPermissionManagement = onNavigateToPermissionManagement,
                        onNavigateToMonicaPlus = onNavigateToMonicaPlus,
                        onNavigateToExtensions = onNavigateToExtensions,
                        onClearAllData = onClearAllData,
                        showTopBar = false  // 在标签页中不显示顶栏
                    )
                }
            }

            when {
                currentTab == BottomNavItem.Passwords && isPasswordSelectionMode -> {
                    SelectionActionBar(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 20.dp),
                        selectedCount = selectedPasswordCount,
                        onExit = onExitPasswordSelection,
                        onSelectAll = onSelectAllPasswords,
                        onFavorite = onFavoriteSelectedPasswords,
                        onMoveToCategory = onMoveToCategoryPasswords,
                        onDelete = onDeleteSelectedPasswords
                    )
                }

                currentTab == BottomNavItem.Authenticator && isTotpSelectionMode -> {
                    SelectionActionBar(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 20.dp),
                        selectedCount = selectedTotpCount,
                        onExit = onExitTotpSelection,
                        onSelectAll = onSelectAllTotp,
                        onDelete = onDeleteSelectedTotp
                    )
                }

                currentTab == BottomNavItem.CardWallet && cardWalletSubTab == CardWalletTab.BANK_CARDS && isBankCardSelectionMode -> {
                    SelectionActionBar(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 20.dp),
                        selectedCount = selectedBankCardCount,
                        onExit = onExitBankCardSelection,
                        onSelectAll = onSelectAllBankCards,
                        onFavorite = onFavoriteBankCards,
                        onDelete = onDeleteSelectedBankCards
                    )
                }

                currentTab == BottomNavItem.CardWallet && cardWalletSubTab == CardWalletTab.DOCUMENTS && isDocumentSelectionMode -> {
                    SelectionActionBar(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 20.dp),
                        selectedCount = selectedDocumentCount,
                        onExit = onExitDocumentSelection,
                        onSelectAll = onSelectAllDocuments,
                        onDelete = onDeleteSelectedDocuments
                    )
                }
            }
        }
    }
    }

    // 全局 FAB Overlay
    // 放在最外层 Box 中，覆盖在 Scaffold 之上，确保能展开到全屏
    // 仅在特定 Tab 显示，并且不在多选模式下显示
    val showFab = (currentTab == BottomNavItem.Passwords || currentTab == BottomNavItem.Authenticator || currentTab == BottomNavItem.CardWallet || currentTab == BottomNavItem.Notes) && !isAnySelectionMode
    
    AnimatedVisibility(
        visible = showFab && isFabVisible,
        enter = slideInHorizontally(initialOffsetX = { it * 2 }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it * 2 }) + fadeOut(),
        modifier = Modifier.fillMaxSize().zIndex(5f) // 确保 FAB 在最上层且能全屏展开
    ) {
        SwipeableAddFab(
            // 通过内部参数控制 FAB 位置，确保容器本身是全屏的
            // NavigationBar 高度约 80dp + 系统导航条高度 + 边距
            fabBottomOffset = 116.dp,
            modifier = Modifier,
            onExpandStateChanged = { expanded -> isFabExpanded = expanded },
            fabContent = { expand ->
            when (currentTab) {
                BottomNavItem.Passwords,
                BottomNavItem.Authenticator,
                BottomNavItem.CardWallet,
                BottomNavItem.Notes -> {
                     Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                else -> { /* 不显示 */ }
            }
        },
        expandedContent = { collapse ->
             Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding() // FIX: 防止内容被状态栏遮挡
            ) {
               when (currentTab) {
                    BottomNavItem.Passwords -> {
                        AddEditPasswordScreen(
                            viewModel = passwordViewModel,
                            totpViewModel = totpViewModel,
                            bankCardViewModel = bankCardViewModel,
                            localKeePassViewModel = localKeePassViewModel,
                            passwordId = null,
                            onNavigateBack = collapse
                        )
                    }
                    BottomNavItem.Authenticator -> {
                        AddEditTotpScreen(
                            totpId = null,
                            initialData = null,
                            initialTitle = "",
                            initialNotes = "",
                            passwordViewModel = passwordViewModel,
                            onSave = { title, notes, totpData ->
                                totpViewModel.saveTotpItem(
                                    id = null,
                                    title = title,
                                    notes = notes,
                                    totpData = totpData
                                )
                                collapse()
                            },
                            onNavigateBack = collapse,
                            onScanQrCode = {
                                collapse()
                                onNavigateToQuickTotpScan()
                            }
                        )
                    }
                    BottomNavItem.CardWallet -> {
                        if (cardWalletSubTab == CardWalletTab.BANK_CARDS) {
                            AddEditBankCardScreen(
                                viewModel = bankCardViewModel,
                                cardId = null,
                                onNavigateBack = collapse
                            )
                        } else {
                            AddEditDocumentScreen(
                                viewModel = documentViewModel,
                                documentId = null,
                                onNavigateBack = collapse
                            )
                        }
                    }
                    BottomNavItem.Notes -> {
                        AddEditNoteScreen(
                            noteId = -1L,
                            onNavigateBack = collapse,
                            viewModel = noteViewModel
                        )
                    }
                    else -> { /* Should not happen */ }
                }
            }
        }
    )
    } // End if (showFab)
    } // End Outer Box

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("新建分类") },
            text = {
                OutlinedTextField(
                    value = categoryNameInput,
                    onValueChange = { categoryNameInput = it },
                    label = { Text("分类名称") },
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
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showEditCategoryDialog != null) {
        AlertDialog(
            onDismissRequest = { showEditCategoryDialog = null },
            title = { Text("编辑分类") },
            text = {
                OutlinedTextField(
                    value = categoryNameInput,
                    onValueChange = { categoryNameInput = it },
                    label = { Text("分类名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (categoryNameInput.isNotBlank()) {
                        passwordViewModel.updateCategory(showEditCategoryDialog!!.copy(name = categoryNameInput))
                        categoryNameInput = ""
                        showEditCategoryDialog = null
                    }
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditCategoryDialog = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 密码列表内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordListContent(
    viewModel: PasswordViewModel,
    settingsViewModel: SettingsViewModel,
    securityManager: SecurityManager,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    groupMode: String = "none",
    stackCardMode: StackCardMode,
    onCreateCategory: () -> Unit,
    onRenameCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onPasswordClick: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onNavigateToAddPassword: (Long?) -> Unit,
    onNavigateToPasswordDetail: (Long) -> Unit,
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
    val searchQuery by viewModel.searchQuery.collectAsState()
    val categories by viewModel.categories.collectAsState()
    // settings
    val appSettings by settingsViewModel.settings.collectAsState()
    
    // 选择模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedPasswords by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showMoveToCategoryDialog by remember { mutableStateOf(false) }
    
    // 详情对话框状态
    var showDetailDialog by remember { mutableStateOf(false) }
    var selectedPasswordForDetail by remember { mutableStateOf<takagi.ru.monica.data.PasswordEntry?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var showPasswordVerify by remember { mutableStateOf(false) }
    
    
    val context = androidx.compose.ui.platform.LocalContext.current
    
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
    
    // 添加触觉反馈
    val haptic = rememberHapticFeedback()
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    // Pull-to-search state
    var currentOffset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val triggerDistance = remember(density) { with(density) { 40.dp.toPx() } }
    val maxDragDistance = remember(density) { with(density) { 100.dp.toPx() } }
    var hasVibrated by remember { mutableStateOf(false) }
    
    // Vibrator
    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }

    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (currentOffset > 0 && available.y < 0) {
                    val newOffset = (currentOffset + available.y).coerceAtLeast(0f)
                    val consumed = currentOffset - newOffset
                    currentOffset = newOffset
                    return androidx.compose.ui.geometry.Offset(0f, -consumed)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                 // Allow UserInput to trigger pull
                if (available.y > 0 && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                    val delta = available.y * 0.5f // Damping
                    val newOffset = (currentOffset + delta).coerceAtMost(maxDragDistance)
                    val oldOffset = currentOffset
                    currentOffset = newOffset
                    
                    if (oldOffset < triggerDistance && newOffset >= triggerDistance && !hasVibrated) {
                        hasVibrated = true
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                             vibrator?.vibrate(android.os.VibrationEffect.createWaveform(takagi.ru.monica.util.VibrationPatterns.TICK, -1))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(20)
                        }
                    } else if (newOffset < triggerDistance) {
                        hasVibrated = false
                    }
                    return available
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (currentOffset >= triggerDistance) {
                     isSearchExpanded = true
                     hasVibrated = false
                }
                androidx.compose.animation.core.Animatable(currentOffset).animateTo(0f) {
                    currentOffset = value
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
    
    // 添加单项删除对话框状态
    var itemToDelete by remember { mutableStateOf<takagi.ru.monica.data.PasswordEntry?>(null) }
    var singleItemPasswordInput by remember { mutableStateOf("") }
    var showSingleItemPasswordVerify by remember { mutableStateOf(false) }
    
    // 添加已删除项ID集合（用于在验证前隐藏项）
    var deletedItemIds by remember { mutableStateOf(setOf<Long>()) }
    
    // 堆叠展开状态 - 记录哪些分组已展开
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }
    
    // 当分组模式改变时,重置展开状态
    LaunchedEffect(groupMode, stackCardMode) {
        expandedGroups = setOf()
    }
    
    // 根据分组模式对密码进行分组
    val groupedPasswords = remember(passwordEntries, deletedItemIds, groupMode, stackCardMode) {
        val filteredEntries = passwordEntries.filter { it.id !in deletedItemIds }
        
        // 步骤1: 先按"除密码外的信息"合并；始终展开模式下跳过合并，逐条显示
        val mergedByInfo = if (stackCardMode == StackCardMode.ALWAYS_EXPANDED) {
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
        val groupedAndSorted = when (groupMode) {
            "title" -> {
                // 按完整标题分组
                mergedByInfo
                    .groupBy { entries -> entries.first().title.ifBlank { "未命名" } }
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
                    .groupBy { entries -> getGroupKeyForMode(entries.first(), groupMode) }
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

        // 如果是始终展开模式，强制拆分为单项列表，但保持排序顺序
        if (stackCardMode == StackCardMode.ALWAYS_EXPANDED) {
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
        selectedPasswords = if (selectedPasswords.size == passwordEntries.size) {
            setOf()
        } else {
            passwordEntries.map { it.id }.toSet()
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

    if (showMoveToCategoryDialog) {
        // 计算每个分类的密码数量
        val categoryCounts = remember(passwordEntries) {
            passwordEntries.groupingBy { it.categoryId }.eachCount()
        }

        AlertDialog(
            onDismissRequest = { showMoveToCategoryDialog = false },
            icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
            title = { 
                Text(
                    "移动到分类",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                ) 
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        val count = categoryCounts[null] ?: 0
                        Surface(
                            onClick = {
                                viewModel.movePasswordsToCategory(selectedPasswords.toList(), null)
                                showMoveToCategoryDialog = false
                                isSelectionMode = false
                                selectedPasswords = setOf()
                                Toast.makeText(context, "已移出分类", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.FolderOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        "无分类",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                if (count > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text(
                                            text = count.toString(),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                    items(categories) { category ->
                        val count = categoryCounts[category.id] ?: 0
                        Surface(
                            onClick = {
                                viewModel.movePasswordsToCategory(selectedPasswords.toList(), category.id)
                                showMoveToCategoryDialog = false
                                isSelectionMode = false
                                selectedPasswords = setOf()
                                Toast.makeText(context, "已移动到 ${category.name}", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        category.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                if (count > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text(
                                            text = count.toString(),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // KeePass 数据库部分
                    if (keepassDatabases.isNotEmpty()) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Text(
                                text = "KeePass 数据库",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        
                        items(keepassDatabases) { database ->
                            val count = passwordEntries.count { it.keepassDatabaseId == database.id }
                            Surface(
                                onClick = {
                                    // 获取选中的密码条目
                                    val selectedEntries = passwordEntries.filter { it.id in selectedPasswords }
                                    
                                    // 真正写入 kdbx 文件并更新数据库关联
                                    coroutineScope.launch {
                                        try {
                                            val result = localKeePassViewModel.addPasswordEntriesToKdbx(
                                                databaseId = database.id,
                                                entries = selectedEntries,
                                                decryptPassword = { encrypted -> securityManager.decryptData(encrypted) ?: "" }
                                            )
                                            
                                            if (result.isSuccess) {
                                                // 更新数据库关联
                                                viewModel.movePasswordsToKeePassDatabase(selectedPasswords.toList(), database.id)
                                                Toast.makeText(context, "已移动 ${result.getOrNull()} 条到 ${database.name}", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "移动失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "移动失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    
                                    showMoveToCategoryDialog = false
                                    isSelectionMode = false
                                    selectedPasswords = setOf()
                                    Toast.makeText(context, "已移动到 ${database.name}", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Key,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                database.name,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            if (database.isDefault) {
                                                Text(
                                                    "默认",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                            }
                                        }
                                    }
                                    if (count > 0) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Text(
                                                text = count.toString(),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveToCategoryDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Display options/search state moved to top
    
    Column {
        // M3E Top Bar with integrated search - 始终显示
        val currentFilter by viewModel.categoryFilter.collectAsState()
        val title = when(currentFilter) {
            is CategoryFilter.All -> stringResource(R.string.app_name)
            is CategoryFilter.Starred -> "标星"
            is CategoryFilter.Uncategorized -> "未分类"
            is CategoryFilter.Custom -> categories.find { it.id == (currentFilter as CategoryFilter.Custom).categoryId }?.name ?: "未知分类"
            is CategoryFilter.KeePassDatabase -> keepassDatabases.find { it.id == (currentFilter as CategoryFilter.KeePassDatabase).databaseId }?.name ?: "KeePass"
        }

            ExpressiveTopBar(
                title = title,
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                isSearchExpanded = isSearchExpanded,
                onSearchExpandedChange = { isSearchExpanded = it },
                searchHint = stringResource(R.string.search_passwords_hint),
                actions = {
                    // 1. Category Folder Trigger
                    IconButton(onClick = { isCategorySheetVisible = true }) {
                         Icon(
                            imageVector = Icons.Default.Folder, // Or CreateNewFolder
                            contentDescription = "分类",
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

                    // 3. Search Trigger (放在最右边)
                    IconButton(onClick = { isSearchExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            // Category Bottom Sheet
            if (isCategorySheetVisible) {
                ModalBottomSheet(
                    onDismissRequest = { isCategorySheetVisible = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                   val categoryList = viewModel.categories.collectAsState(initial = emptyList()).value
                   var expandedMenuId by remember { mutableStateOf<Long?>(null) }

                   Column(
                       modifier = Modifier
                           .fillMaxWidth()
                           .padding(horizontal = 20.dp, vertical = 16.dp)
                   ) {
                       Text(
                           text = "选择分类",
                           style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                       )

                        Spacer(modifier = Modifier.height(12.dp))

                           LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 10.dp)
                           ) {
                           item {
                               val selected = currentFilter is CategoryFilter.All
                               CategoryListItem(
                                   title = stringResource(R.string.category_all),
                                    icon = Icons.Default.List,
                                   selected = selected,
                                   onClick = {
                                       viewModel.setCategoryFilter(CategoryFilter.All)
                                   }
                               )
                           }
                           item {
                               val selected = currentFilter is CategoryFilter.Starred
                               CategoryListItem(
                                   title = "标星",
                                   icon = Icons.Outlined.CheckCircle,
                                   selected = selected,
                                   onClick = {
                                       viewModel.setCategoryFilter(CategoryFilter.Starred)
                                   }
                               )
                           }
                           item {
                               val selected = currentFilter is CategoryFilter.Uncategorized
                               CategoryListItem(
                                   title = "未分类",
                                   icon = Icons.Default.FolderOff,
                                   selected = selected,
                                   onClick = {
                                       viewModel.setCategoryFilter(CategoryFilter.Uncategorized)
                                   }
                               )
                           }
                           
                           // KeePass 数据库部分
                           if (keepassDatabases.isNotEmpty()) {
                               item {
                                   Spacer(modifier = Modifier.height(8.dp))
                                   Text(
                                       text = stringResource(R.string.local_keepass_database),
                                       style = MaterialTheme.typography.labelLarge,
                                       color = MaterialTheme.colorScheme.primary,
                                       modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                   )
                               }
                               
                               items(keepassDatabases, key = { "keepass_${it.id}" }) { database ->
                                   val selected = currentFilter is CategoryFilter.KeePassDatabase && 
                                       (currentFilter as CategoryFilter.KeePassDatabase).databaseId == database.id
                                   CategoryListItem(
                                       title = database.name,
                                       icon = Icons.Default.Key,
                                       selected = selected,
                                       onClick = {
                                           viewModel.setCategoryFilter(CategoryFilter.KeePassDatabase(database.id))
                                       },
                                       badge = {
                                           Text(
                                               text = if (database.storageLocation == takagi.ru.monica.data.KeePassStorageLocation.EXTERNAL) 
                                                   stringResource(R.string.external_storage) 
                                               else 
                                                   stringResource(R.string.internal_storage),
                                               style = MaterialTheme.typography.labelSmall,
                                               color = MaterialTheme.colorScheme.onSurfaceVariant
                                           )
                                       }
                                   )
                               }
                               
                               item {
                                   Spacer(modifier = Modifier.height(8.dp))
                               }
                           }
                           
                           items(categoryList, key = { it.id }) { category ->
                               val selected = currentFilter is CategoryFilter.Custom && (currentFilter as CategoryFilter.Custom).categoryId == category.id
                               CategoryListItem(
                                   title = category.name,
                                   icon = Icons.Default.Folder,
                                   selected = selected,
                                   onClick = {
                                        viewModel.setCategoryFilter(CategoryFilter.Custom(category.id))
                                   },
                                   menu = {
                                       IconButton(onClick = { expandedMenuId = category.id }) {
                                           Icon(Icons.Default.MoreVert, contentDescription = null)
                                       }
                                       DropdownMenu(
                                           expanded = expandedMenuId == category.id,
                                           onDismissRequest = { expandedMenuId = null },
                                           modifier = Modifier.clip(RoundedCornerShape(18.dp))
                                       ) {
                                           DropdownMenuItem(
                                               text = { Text("重命名") },
                                               leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                               onClick = {
                                                   expandedMenuId = null
                                                   onRenameCategory(category)
                                               }
                                           )
                                           DropdownMenuItem(
                                               text = { Text("删除") },
                                               leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                               onClick = {
                                                   expandedMenuId = null
                                                   onDeleteCategory(category)
                                               }
                                           )
                                       }
                                   }
                               )
                           }
                            item {
                                Spacer(modifier = Modifier.height(18.dp))

                                Button(
                                    onClick = onCreateCategory,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("新建分类")
                                }
                            }
                       }
                   }
                }
            }

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
        Box(modifier = Modifier.fillMaxSize()) {
            if (passwordEntries.isEmpty() && searchQuery.isEmpty()) {
                // Empty state with pull-to-search
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { androidx.compose.ui.unit.IntOffset(0, currentOffset.toInt()) }
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDrag = { _, _ -> } // Consume long press to prevent issues
                            )
                        }
                        .pointerInput(Unit) {
                             detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    if (dragAmount > 0) {
                                        val newOffset = (currentOffset + dragAmount * 0.5f).coerceAtMost(maxDragDistance)
                                        val oldOffset = currentOffset
                                        currentOffset = newOffset
                                        
                                        if (oldOffset < triggerDistance && newOffset >= triggerDistance && !hasVibrated) {
                                            hasVibrated = true
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                 vibrator?.vibrate(android.os.VibrationEffect.createWaveform(takagi.ru.monica.util.VibrationPatterns.TICK, -1))
                                            } else {
                                                @Suppress("DEPRECATION")
                                                vibrator?.vibrate(20)
                                            }
                                        } else if (newOffset < triggerDistance) {
                                           hasVibrated = false
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (currentOffset >= triggerDistance) {
                                        isSearchExpanded = true
                                        hasVibrated = false
                                    }
                                    coroutineScope.launch {
                                        androidx.compose.animation.core.Animatable(currentOffset).animateTo(0f) { currentOffset = value }
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        androidx.compose.animation.core.Animatable(currentOffset).animateTo(0f) { currentOffset = value }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset { androidx.compose.ui.unit.IntOffset(0, currentOffset.toInt()) }
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
                        .fillMaxSize()
                        .offset { androidx.compose.ui.unit.IntOffset(0, currentOffset.toInt()) }
                        .nestedScroll(nestedScrollConnection),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    groupedPasswords.forEach { (groupKey, passwords) ->
                    val isExpanded = when (stackCardMode) {
                        StackCardMode.AUTO -> expandedGroups.contains(groupKey)
                        StackCardMode.ALWAYS_EXPANDED -> true
                    }

                    item(key = "group_$groupKey") {
                        StackedPasswordGroup(
                            website = groupKey,
                            passwords = passwords,
                            isExpanded = isExpanded,
                            stackCardMode = stackCardMode,
                            swipedItemId = itemToDelete?.id,
                            onToggleExpand = {
                                if (stackCardMode == StackCardMode.AUTO) {
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
                                onNavigateToPasswordDetail(password.id)
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
                                val websiteKey = password.website.ifBlank { "未分类" }
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
                                        val firstItemInGroup = allPasswords.first { it.website.ifBlank { "未分类" } == websiteKey }
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
                            onNavigateToPasswordDetail(passwords.first().id)
                        },
                        onLongClick = { password ->
                            // 长按进入多选模式
                            haptic.performLongPress()
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedPasswords = setOf(password.id)
                            }
                        },
                        iconCardsEnabled = appSettings.iconCardsEnabled,
                        passwordCardDisplayMode = appSettings.passwordCardDisplayMode
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
            } // Close else
        } // Close Box
    } // Close PasswordListContent
    
    // 批量删除确认对话框
    if (showBatchDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showBatchDeleteDialog = false
                passwordInput = ""
            },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.batch_delete_passwords_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.batch_delete_passwords_message, selectedPasswords.size))
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.enter_master_password_confirm)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showPasswordVerify = true
                        showBatchDeleteDialog = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = passwordInput.isNotEmpty()
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showBatchDeleteDialog = false
                        passwordInput = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // 批量删除密码验证
    if (showPasswordVerify) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val securityManager = takagi.ru.monica.security.SecurityManager(context)
            if (securityManager.verifyMasterPassword(passwordInput)) {
                // 批量删除
                coroutineScope.launch {
                    val toDelete = passwordEntries.filter { selectedPasswords.contains(it.id) }
                    toDelete.forEach { viewModel.deletePasswordEntry(it) }
                    
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.deleted_items, toDelete.size),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    
                    // 退出选择模式
                    isSelectionMode = false
                    selectedPasswords = setOf()
                    passwordInput = ""
                    showPasswordVerify = false
                }
            } else {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.current_password_incorrect),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                showPasswordVerify = false
                showBatchDeleteDialog = true
            }
        }
    }
    

    
    

    // 单项删除确认对话框(支持指纹和密码验证)
    itemToDelete?.let { item ->
        DeleteConfirmDialog(
            itemTitle = item.title,
            itemType = "密码",
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
        LaunchedEffect(Unit) {
            val securityManager = takagi.ru.monica.security.SecurityManager(context)
            if (securityManager.verifyMasterPassword(singleItemPasswordInput)) {
                // 密码正确，执行真实删除
                viewModel.deletePasswordEntry(itemToDelete!!)
                
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
}
/**
 * TOTP列表内容
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TotpListContent(
    viewModel: takagi.ru.monica.viewmodel.TotpViewModel,
    onTotpClick: (Long) -> Unit,
    onDeleteTotp: (takagi.ru.monica.data.SecureItem) -> Unit,
    onQuickScanTotp: () -> Unit,
    onSelectionModeChange: (
        isSelectionMode: Boolean,
        selectedCount: Int,
        onExit: () -> Unit,
        onSelectAll: () -> Unit,
        onDelete: () -> Unit
    ) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val totpItems by viewModel.totpItems.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val haptic = rememberHapticFeedback()
    val focusManager = LocalFocusManager.current
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }

    // 如果搜索框展开，按返回键关闭搜索框
    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
        viewModel.updateSearchQuery("")
        focusManager.clearFocus()
    }

    // Pull-to-search state
    val density = androidx.compose.ui.platform.LocalDensity.current
    var currentOffset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val triggerDistance = remember(density) { with(density) { 40.dp.toPx() } }
    val maxDragDistance = remember(density) { with(density) { 100.dp.toPx() } }
    var hasVibrated by remember { mutableStateOf(false) }
    
    // Vibrator
    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }

    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (currentOffset > 0 && available.y < 0) {
                    val newOffset = (currentOffset + available.y).coerceAtLeast(0f)
                    val consumed = currentOffset - newOffset
                    currentOffset = newOffset
                    return androidx.compose.ui.geometry.Offset(0f, -consumed)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y > 0 && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                    val delta = available.y * 0.5f // Damping
                    val newOffset = (currentOffset + delta).coerceAtMost(maxDragDistance)
                    val oldOffset = currentOffset
                    currentOffset = newOffset
                    
                    if (oldOffset < triggerDistance && newOffset >= triggerDistance && !hasVibrated) {
                        hasVibrated = true
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                             vibrator?.vibrate(android.os.VibrationEffect.createWaveform(takagi.ru.monica.util.VibrationPatterns.TICK, -1))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(20)
                        }
                    } else if (newOffset < triggerDistance) {
                        hasVibrated = false
                    }
                    return available
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (currentOffset >= triggerDistance) {
                     isSearchExpanded = true
                     hasVibrated = false
                }
                androidx.compose.animation.core.Animatable(currentOffset).animateTo(0f) {
                    currentOffset = value
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
    
    // 选择模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var showPasswordVerify by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val settingsManager = remember { takagi.ru.monica.utils.SettingsManager(context) }
    val appSettings by settingsManager.settingsFlow.collectAsState(initial = takagi.ru.monica.data.AppSettings())
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
    
    // 通知父组件选择模式状态变化
    LaunchedEffect(isSelectionMode, selectedItems.size) {
        onSelectionModeChange(
            isSelectionMode,
            selectedItems.size,
            exitSelection,
            selectAll,
            deleteSelected
        )
    }

    Column {
        // M3E Top Bar with integrated search - 始终显示

        ExpressiveTopBar(
            title = "验证器", // Or stringResource(R.string.authenticator)
            searchQuery = searchQuery,
            onSearchQueryChange = viewModel::updateSearchQuery,
            isSearchExpanded = isSearchExpanded,
            onSearchExpandedChange = { isSearchExpanded = it },
            searchHint = stringResource(R.string.search_authenticator),
            actions = {
                // 快速扫码按钮
                IconButton(onClick = onQuickScanTotp) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "扫码添加",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 搜索按钮
                IconButton(onClick = { isSearchExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

        // TOTP列表
        if (filteredTotpItems.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { androidx.compose.ui.unit.IntOffset(0, currentOffset.toInt()) }
                    .nestedScroll(nestedScrollConnection),
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
            val lazyListState = rememberLazyListState()
            
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
                    .offset { androidx.compose.ui.unit.IntOffset(0, currentOffset.toInt()) }
                    .nestedScroll(nestedScrollConnection),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = localTotpItems,
                    key = { it.id }
                ) { item ->
                    val index = localTotpItems.indexOf(item)
                    
                    ReorderableItem(reorderableLazyListState, key = item.id) { isDragging ->
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
            itemType = "验证器",
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
    
    // 批量删除确认对话框
    if (showBatchDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showBatchDeleteDialog = false
                passwordInput = ""
            },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.batch_delete_totp_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.batch_delete_totp_message, selectedItems.size))
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.enter_master_password_confirm)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showPasswordVerify = true
                        showBatchDeleteDialog = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = passwordInput.isNotEmpty()
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showBatchDeleteDialog = false
                        passwordInput = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // 批量删除验证
    if (showPasswordVerify) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val securityManager = takagi.ru.monica.security.SecurityManager(context)
            if (securityManager.verifyMasterPassword(passwordInput)) {
                // 批量删除
                coroutineScope.launch {
                    val toDelete = totpItems.filter { selectedItems.contains(it.id) }
                    toDelete.forEach { onDeleteTotp(it) }
                    
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.deleted_items, toDelete.size),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    
                    // 退出选择模式
                    isSelectionMode = false
                    selectedItems = setOf()
                    passwordInput = ""
                    showPasswordVerify = false
                }
            } else {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.current_password_incorrect),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                showPasswordVerify = false
                showBatchDeleteDialog = true
            }
        }
    }
}

/**
 * TOTP项卡片
 */
@Composable
private fun TotpItemCard(
    item: takagi.ru.monica.data.SecureItem,
    onEdit: () -> Unit,
    onToggleSelect: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onGenerateNext: ((Long) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onShowQrCode: ((takagi.ru.monica.data.SecureItem) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    sharedTickSeconds: Long? = null,
    appSettings: takagi.ru.monica.data.AppSettings? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // 直接使用修改后的 TotpCodeCard 组件
    takagi.ru.monica.ui.components.TotpCodeCard(
        item = item,
        onCopyCode = { code ->
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("TOTP Code", code)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(context, "验证码已复制", android.widget.Toast.LENGTH_SHORT).show()
        },
        onToggleSelect = onToggleSelect,
        onDelete = onDelete,
        onToggleFavorite = onToggleFavorite,
        onGenerateNext = onGenerateNext,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onShowQrCode = onShowQrCode,
        onEdit = onEdit,
        onLongClick = onLongClick,
        isSelectionMode = isSelectionMode,
        isSelected = isSelected,
        sharedTickSeconds = sharedTickSeconds,
        appSettings = appSettings
    )
}

/**
 * 笔记列表
 */
/**
 * 银行卡列表内容
 */
@Composable
private fun BankCardListContent(
    viewModel: takagi.ru.monica.viewmodel.BankCardViewModel,
    onCardClick: (Long) -> Unit,
    onSelectionModeChange: (Boolean, Int, () -> Unit, () -> Unit, () -> Unit, () -> Unit) -> Unit  // 添加第6个参数：收藏
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var masterPassword by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val cards by viewModel.allCards.collectAsState(initial = emptyList())
    
    // 添加触觉反馈
    val haptic = rememberHapticFeedback()
    
    // 添加单项删除对话框状态
    var itemToDelete by remember { mutableStateOf<takagi.ru.monica.data.SecureItem?>(null) }
    var singleItemPasswordInput by remember { mutableStateOf("") }
    var showSingleItemPasswordVerify by remember { mutableStateOf(false) }
    
    // 添加已删除项ID集合（用于在验证前隐藏项）
    var deletedItemIds by remember { mutableStateOf(setOf<Long>()) }
    
    // 过滤掉已删除的项
    val visibleCards = remember(cards, deletedItemIds) {
        cards.filter { it.id !in deletedItemIds }
    }
    
    // 通知父组件选择模式状态变化
    LaunchedEffect(isSelectionMode, selectedItems.size) {
        if (isSelectionMode) {
            onSelectionModeChange(
                true,
                selectedItems.size,
                {
                    // 退出选择模式
                    isSelectionMode = false
                    selectedItems = emptySet()
                },
                {
                    // 全选/取消全选
                    selectedItems = if (selectedItems.size == cards.size) {
                        emptySet()
                    } else {
                        cards.map { it.id }.toSet()
                    }
                },
                {
                    // 批量删除
                    showPasswordDialog = true
                },
                {
                    // 批量收藏
                    scope.launch {
                        selectedItems.forEach { id ->
                            viewModel.toggleFavorite(id)
                        }
                        android.widget.Toast.makeText(
                            context,
                            "已更新 ${selectedItems.size} 张卡片的收藏状态",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        isSelectionMode = false
                        selectedItems = emptySet()
                    }
                }
            )
        } else {
            onSelectionModeChange(false, 0, {}, {}, {}, {})
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.batch_delete_bankcards_title)) },
            text = { Text(stringResource(R.string.batch_delete_bankcards_message, selectedItems.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        showPasswordDialog = true
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
    
    // 主密码验证对话框
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { 
                showPasswordDialog = false
                masterPassword = ""
            },
            title = { Text(stringResource(R.string.enter_master_password_confirm)) },
            text = {
                OutlinedTextField(
                    value = masterPassword,
                    onValueChange = { masterPassword = it },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            // TODO: 验证主密码
                            // 这里简化处理,直接删除
                            selectedItems.forEach { id ->
                                viewModel.deleteCard(id)
                            }
                            showPasswordDialog = false
                            masterPassword = ""
                            isSelectionMode = false
                            selectedItems = emptySet()
                        }
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showPasswordDialog = false
                        masterPassword = ""
                    }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
    
    if (cards.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_bank_cards_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = visibleCards,  // 使用过滤后的列表
                key = { it.id }
            ) { card ->
                val index = visibleCards.indexOf(card)
                
                takagi.ru.monica.ui.gestures.SwipeActions(
                    onSwipeLeft = {
                        // 左滑删除
                        haptic.performWarning()
                        itemToDelete = card
                        deletedItemIds = deletedItemIds + card.id
                    },
                    onSwipeRight = {
                        // 右滑选择
                        haptic.performSuccess()
                        if (!isSelectionMode) {
                            isSelectionMode = true
                        }
                        selectedItems = if (selectedItems.contains(card.id)) {
                            selectedItems - card.id
                        } else {
                            selectedItems + card.id
                        }
                    },
                    isSwiped = itemToDelete?.id == card.id,
                    enabled = true
                ) {
                    BankCardItemCard(
                        item = card,
                        onClick = { 
                            if (isSelectionMode) {
                                // 选择模式下点击切换选择状态
                                selectedItems = if (selectedItems.contains(card.id)) {
                                    selectedItems - card.id
                                } else {
                                    selectedItems + card.id
                                }
                            } else {
                                // 普通模式下打开详情
                                onCardClick(card.id)
                            }
                        },
                        onDelete = {
                            itemToDelete = card
                        },
                        onToggleFavorite = { id, _ ->
                            viewModel.toggleFavorite(id)
                        },
                        onMoveUp = if (index > 0 && !isSelectionMode) {
                            {
                                val currentItem = visibleCards[index]
                                val previousItem = visibleCards[index - 1]
                                viewModel.updateSortOrders(listOf(
                                    currentItem.id to (index - 1),
                                    previousItem.id to index
                                ))
                            }
                        } else null,
                        onMoveDown = if (index < visibleCards.size - 1 && !isSelectionMode) {
                            {
                                val currentItem = visibleCards[index]
                                val nextItem = visibleCards[index + 1]
                                viewModel.updateSortOrders(listOf(
                                    currentItem.id to (index + 1),
                                    nextItem.id to index
                                ))
                            }
                        } else null
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }    // 单项删除确认对话框(支持指纹和密码验证)
    itemToDelete?.let { item ->
        DeleteConfirmDialog(
            itemTitle = item.title,
            itemType = "银行卡",
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
                viewModel.deleteCard(item.id)
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
                // 密码正确，执行真实删除
                viewModel.deleteCard(itemToDelete!!.id)
                
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
}

@Composable
private fun BankCardItemCard(
    item: takagi.ru.monica.data.SecureItem,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onToggleFavorite: ((Long, Boolean) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    takagi.ru.monica.ui.components.BankCardCard(
        item = item,
        onClick = onClick,
        onDelete = onDelete,
        onToggleFavorite = onToggleFavorite,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown
    )
}

/**
 * 证件列表内容
 */
@Composable
private fun DocumentListContent(
    viewModel: takagi.ru.monica.viewmodel.DocumentViewModel,
    onDocumentClick: (Long) -> Unit,
    onSelectionModeChange: (Boolean, Int, () -> Unit, () -> Unit, () -> Unit) -> Unit
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var masterPassword by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = rememberHapticFeedback()
    
    val documents by viewModel.allDocuments.collectAsState(initial = emptyList())
    
    // 添加单项删除对话框状态
    var itemToDelete by remember { mutableStateOf<takagi.ru.monica.data.SecureItem?>(null) }
    var singleItemPasswordInput by remember { mutableStateOf("") }
    var showSingleItemPasswordVerify by remember { mutableStateOf(false) }
    
    // 待删除项ID集合（用于隐藏即将删除的项）
    var deletedItemIds by remember { mutableStateOf(setOf<Long>()) }
    
    // 过滤掉待删除的项
    val filteredDocuments = remember(documents, deletedItemIds) {
        documents.filter { it.id !in deletedItemIds }
    }
    
    // 通知父组件选择模式状态变化
    LaunchedEffect(isSelectionMode, selectedItems.size) {
        if (isSelectionMode) {
            onSelectionModeChange(
                true,
                selectedItems.size,
                {
                    isSelectionMode = false
                    selectedItems = emptySet()
                },
                {
                    selectedItems = if (selectedItems.size == filteredDocuments.size) {
                        emptySet()
                    } else {
                        filteredDocuments.map { it.id }.toSet()
                    }
                },
                {
                    showPasswordDialog = true
                }
            )
        } else {
            onSelectionModeChange(false, 0, {}, {}, {})
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.batch_delete_documents_title)) },
            text = { Text(stringResource(R.string.batch_delete_documents_message, selectedItems.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        showPasswordDialog = true
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
    
    // 主密码验证对话框
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { 
                showPasswordDialog = false
                masterPassword = ""
            },
            title = { Text(stringResource(R.string.enter_master_password_confirm)) },
            text = {
                OutlinedTextField(
                    value = masterPassword,
                    onValueChange = { masterPassword = it },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            // TODO: 验证主密码
                            // 这里简化处理,直接删除
                            selectedItems.forEach { id ->
                                viewModel.deleteDocument(id)
                            }
                            showPasswordDialog = false
                            masterPassword = ""
                            isSelectionMode = false
                            selectedItems = emptySet()
                        }
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showPasswordDialog = false
                        masterPassword = ""
                    }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
    
    if (filteredDocuments.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_documents_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = filteredDocuments,
                key = { it.id }
            ) { document ->
                val index = filteredDocuments.indexOf(document)
                
                // 用 SwipeActions 包裹文档卡片
                takagi.ru.monica.ui.gestures.SwipeActions(
                    onSwipeLeft = {
                        // 左滑删除
                        haptic.performWarning()
                        itemToDelete = document
                        deletedItemIds = deletedItemIds + document.id
                    },
                    onSwipeRight = {
                        // 右滑选择
                        haptic.performSuccess()
                        if (!isSelectionMode) {
                            isSelectionMode = true
                        }
                        selectedItems = if (selectedItems.contains(document.id)) {
                            selectedItems - document.id
                        } else {
                            selectedItems + document.id
                        }
                    },
                    isSwiped = itemToDelete?.id == document.id,
                    enabled = true // 多选模式下也可以滑动
                ) {
                    DocumentItemCard(
                        item = document,
                        onClick = {
                            if (isSelectionMode) {
                                selectedItems = if (selectedItems.contains(document.id)) {
                                    selectedItems - document.id
                                } else {
                                    selectedItems + document.id
                                }
                            } else {
                                onDocumentClick(document.id)
                            }
                        },
                        onDelete = {
                            haptic.performWarning()
                            itemToDelete = document
                            deletedItemIds = deletedItemIds + document.id
                        },
                        onToggleFavorite = { id, _ -> // isFavorite 参数未使用，因为直接调用 toggleFavorite
                            viewModel.toggleFavorite(id)
                        },
                        onMoveUp = if (index > 0) {
                            {
                                // 交换当前项和上一项的sortOrder
                                val currentItem = filteredDocuments[index]
                                val previousItem = filteredDocuments[index - 1]
                                viewModel.updateSortOrders(listOf(
                                    currentItem.id to (index - 1),
                                    previousItem.id to index
                                ))
                            }
                        } else null,
                        onMoveDown = if (index < filteredDocuments.size - 1) {
                            {
                                // 交换当前项和下一项的sortOrder
                                val currentItem = filteredDocuments[index]
                                val nextItem = filteredDocuments[index + 1]
                                viewModel.updateSortOrders(listOf(
                                    currentItem.id to (index + 1),
                                    nextItem.id to index
                                ))
                            }
                        } else null,
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedItems.contains(document.id)
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
    
    // 单项删除确认对话框(支持指纹和密码验证)
    itemToDelete?.let { item ->
        DeleteConfirmDialog(
            itemTitle = item.title,
            itemType = "证件",
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
                viewModel.deleteDocument(item.id)
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
                // 密码正确，删除文档
                viewModel.deleteDocument(itemToDelete!!.id)
                
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
}

/**
 * 堆叠密码卡片组
 * 
 * @param website 网站分组键（用于分组，内部从 passwords 获取实际值）
 * @param passwords 该组的密码条目列表
 * @param isExpanded 是否展开显示所有卡片
 * @param onToggleExpand 展开/收起切换回调
 * @param onPasswordClick 密码卡片点击回调
 * @param onLongClick 长按回调
 * @param onToggleFavorite 收藏切换回调
 * @param onToggleGroupFavorite 整组收藏切换回调
 * @param onToggleGroupCover 封面切换回调
 * @param isSelectionMode 是否处于选择模式
 * @param selectedPasswords 已选中的密码ID集合
 * @param onToggleSelection 选择切换回调
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StackedPasswordGroup(
    @Suppress("UNUSED_PARAMETER") website: String,
    passwords: List<takagi.ru.monica.data.PasswordEntry>,
    isExpanded: Boolean,
    stackCardMode: StackCardMode,
    onToggleExpand: () -> Unit,
    onPasswordClick: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onSwipeLeft: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onSwipeRight: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onGroupSwipeRight: (List<takagi.ru.monica.data.PasswordEntry>) -> Unit,
    onToggleFavorite: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onToggleGroupFavorite: () -> Unit,
    onToggleGroupCover: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    isSelectionMode: Boolean,
    selectedPasswords: Set<Long>,
    swipedItemId: Long? = null,
    onToggleSelection: (Long) -> Unit,
    onOpenMultiPasswordDialog: (List<takagi.ru.monica.data.PasswordEntry>) -> Unit,
    onLongClick: (takagi.ru.monica.data.PasswordEntry) -> Unit, // 新增：长按进入多选模式
    iconCardsEnabled: Boolean = false,
    passwordCardDisplayMode: takagi.ru.monica.data.PasswordCardDisplayMode = takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL
) {
    // 检查是否为多密码合并卡片(除密码外信息完全相同)
    val isMergedPasswordCard = passwords.size > 1 && 
        passwords.map { getPasswordInfoKey(it) }.distinct().size == 1
    
    // 如果选择“始终展开”，则直接平铺展示，不使用堆叠容器
    if (stackCardMode == StackCardMode.ALWAYS_EXPANDED) {
        passwords.forEach { password ->
            takagi.ru.monica.ui.gestures.SwipeActions(
                onSwipeLeft = { onSwipeLeft(password) },
                onSwipeRight = { onSwipeRight(password) },
                isSwiped = password.id == swipedItemId,
                enabled = true
            ) {
                PasswordEntryCard(
                    entry = password,
                    onClick = {
                        if (isSelectionMode) {
                            onToggleSelection(password.id)
                        } else {
                            onPasswordClick(password)
                        }
                    },
                    onLongClick = { onLongClick(password) },
                    onToggleFavorite = { onToggleFavorite(password) },
                    onToggleGroupCover = null,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedPasswords.contains(password.id),
                    canSetGroupCover = false,
                    isInExpandedGroup = false,
                    isSingleCard = true,
                    iconCardsEnabled = iconCardsEnabled,
                    passwordCardDisplayMode = passwordCardDisplayMode
                )
            }
        }
        return
    }

    // 单个密码直接显示，不堆叠 (且不是合并卡片)
    if (passwords.size == 1 && !isMergedPasswordCard) {
        val password = passwords.first()
        takagi.ru.monica.ui.gestures.SwipeActions(
            onSwipeLeft = { onSwipeLeft(password) },
            onSwipeRight = { onSwipeRight(password) },
            isSwiped = password.id == swipedItemId,
            enabled = true
        ) {
            PasswordEntryCard(
                entry = password,
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelection(password.id)
                    } else {
                        onPasswordClick(password)
                    }
                },
                onLongClick = { onLongClick(password) },
                onToggleFavorite = { onToggleFavorite(password) },
                onToggleGroupCover = null,
                isSelectionMode = isSelectionMode,
                isSelected = selectedPasswords.contains(password.id),
                canSetGroupCover = false,
                isInExpandedGroup = false,
                isSingleCard = true,
                iconCardsEnabled = iconCardsEnabled,
                passwordCardDisplayMode = passwordCardDisplayMode
            )
        }
        return
    }

    // 如果是多密码合并卡片,直接显示为单卡片,不堆叠
    if (isMergedPasswordCard) {
        takagi.ru.monica.ui.gestures.SwipeActions(
            onSwipeLeft = { onSwipeLeft(passwords.first()) },
            onSwipeRight = { onGroupSwipeRight(passwords) },
            isSwiped = passwords.first().id == swipedItemId,
            enabled = true
        ) {
            MultiPasswordEntryCard(
                passwords = passwords,
                onClick = { password ->
                    if (isSelectionMode) {
                        onToggleSelection(password.id)
                    } else {
                        // 点击密码按钮 → 进入编辑页面
                        onPasswordClick(password)
                    }
                },
                onCardClick = if (!isSelectionMode) {
                    // 点击卡片本身 → 打开多密码详情对话框
                    { onOpenMultiPasswordDialog(passwords) }
                } else null,
                onLongClick = { onLongClick(passwords.first()) },
                onToggleFavorite = { password -> onToggleFavorite(password) },
                onToggleGroupCover = null,
                isSelectionMode = isSelectionMode,
                selectedPasswords = selectedPasswords,
                canSetGroupCover = false,
                hasGroupCover = false,
                isInExpandedGroup = false,
                iconCardsEnabled = iconCardsEnabled,
                passwordCardDisplayMode = passwordCardDisplayMode
            )
        }
        return
    }
    
    // 否则使用原有的堆叠逻辑
    val isGroupFavorited = passwords.all { it.isFavorite }
    val hasGroupCover = passwords.any { it.isGroupCover }
    
    // 🎨 动画状态
    val effectiveExpanded = when (stackCardMode) {
        StackCardMode.AUTO -> isExpanded
        StackCardMode.ALWAYS_EXPANDED -> true
    }

    val expandProgress by animateFloatAsState(
        targetValue = if (effectiveExpanded && passwords.size > 1) 1f else 0f,
        animationSpec = tween(200),
        label = "expand_animation"
    )
    
    val containerAlpha by animateFloatAsState(
        targetValue = if (effectiveExpanded && passwords.size > 1) 1f else 0f,
        animationSpec = tween(200),
        label = "container_alpha"
    )
    
    // 🎯 下滑手势状态
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val haptic = rememberHapticFeedback()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (effectiveExpanded && passwords.size > 1) {
                    Modifier.pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { 
                                haptic.performLongPress()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                // 只允许向下滑动
                                if (dragAmount.y > 0) {
                                    swipeOffset = (swipeOffset + dragAmount.y).coerceAtMost(150f)
                                }
                            },
                            onDragEnd = {
                                // 如果下滑超过阈值，收起卡片组
                                if (swipeOffset > 80f) {
                                    haptic.performSuccess()
                                    onToggleExpand()
                                }
                                swipeOffset = 0f
                            },
                            onDragCancel = {
                                swipeOffset = 0f
                            }
                        )
                    }
                } else Modifier
            )
    ) {
        // 📚 堆叠背后的层级卡片 (仅在堆叠状态下可见，或动画过程中可见)
        val stackAlpha by animateFloatAsState(
            targetValue = if (effectiveExpanded) 0f else 1f,
            animationSpec = tween(200),
            label = "stack_alpha"
        )
        
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 背景堆叠层 (当 stackAlpha > 0 时显示)
            if (passwords.size > 1) {
                val stackCount = passwords.size.coerceAtMost(3)
                for (i in (stackCount - 1) downTo 1) {
                    val offsetDp = (i * 4).dp
                    val scaleFactor = 1f - (i * 0.02f)
                    val layerAlpha = (0.7f - (i * 0.2f)) * stackAlpha
                    
                    if (layerAlpha > 0.01f) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = offsetDp) // Padding top creates the vertical offset effect
                                .graphicsLayer {
                                    scaleX = scaleFactor
                                    scaleY = scaleFactor
                                    alpha = layerAlpha
                                    translationY = (i * 4).dp.toPx() * (1f - stackAlpha) // Optional: slide up when disappearing?
                                },
                            elevation = CardDefaults.cardElevation(defaultElevation = (i * 1.5).dp),
                            colors = CardDefaults.cardColors(), // Use default colors to match single cards
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Box(modifier = Modifier.height(76.dp))
                        }
                    }
                }
            }

            // 🎯 主卡片 (持续存在，内容和属性变化)
            val cardElevation by animateDpAsState(
                targetValue = if (effectiveExpanded) 4.dp else 6.dp,
                animationSpec = tween(200),
                label = "elevation"
            )
            val cardShape by animateDpAsState(
                targetValue = if (effectiveExpanded) 16.dp else 14.dp,
                animationSpec = tween(200),
                label = "shape"
            )
            
            val isSelected = selectedPasswords.contains(passwords.first().id)
            val cardColors = if (isSelected) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            } else {
                CardDefaults.cardColors()
            }
            
            takagi.ru.monica.ui.gestures.SwipeActions(
                onSwipeLeft = { 
                    if (!effectiveExpanded) onSwipeLeft(passwords.first()) 
                    // Expanded state swipe logic handled inside? Or disable swipe on container when expanded?
                },
                onSwipeRight = { 
                    if (!effectiveExpanded) onGroupSwipeRight(passwords)
                },
                isSwiped = passwords.first().id == swipedItemId,
                enabled = !effectiveExpanded // Disable swipe actions on the container when expanded
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = tween(200)
                        )
                        .then(
                            // 展开时的下滑手势
                            if (effectiveExpanded && passwords.size > 1) {
                                Modifier.pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { haptic.performLongPress() },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            if (dragAmount.y > 0) {
                                                swipeOffset = (swipeOffset + dragAmount.y).coerceAtMost(150f)
                                            }
                                        },
                                        onDragEnd = {
                                            if (swipeOffset > 80f) {
                                                haptic.performSuccess()
                                                onToggleExpand()
                                            }
                                            swipeOffset = 0f
                                        },
                                        onDragCancel = { swipeOffset = 0f }
                                    )
                                }
                            } else Modifier
                        )
                        .graphicsLayer {
                            // 下滑时的位移效果
                            if (effectiveExpanded) {
                                translationY = swipeOffset * 0.5f
                            }
                        },
                    shape = RoundedCornerShape(cardShape),
                    elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                    colors = cardColors
                ) {
                    // 内容切换：收起态(Header) vs 展开态(Column)
                    AnimatedContent(
                        targetState = effectiveExpanded,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(200)) togetherWith 
                            fadeOut(animationSpec = tween(200))
                        },
                        label = "content_switch"
                    ) { expanded ->
                        if (!expanded) {
                            // --- 收起状态的内容 ---
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleExpand() }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelectionMode) {
                                    androidx.compose.material3.Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onGroupSwipeRight(passwords) }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // 🏷️ 数量徽章
                                            Surface(
                                                shape = RoundedCornerShape(18.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shadowElevation = 2.dp
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.Layers,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Text(
                                                        text = "${passwords.size}",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                            
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = passwords.first().title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (passwords.first().website.isNotBlank()) {
                                                    Text(
                                                        text = passwords.first().website,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (!isSelectionMode && isGroupFavorited) {
                                                Icon(
                                                    Icons.Default.Favorite,
                                                    contentDescription = "已收藏",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            
                                            if (!isSelectionMode) {
                                                Icon(
                                                    Icons.Default.ExpandMore,
                                                    contentDescription = "展开",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            } else if (isGroupFavorited) {
                                                Icon(
                                                    Icons.Default.Favorite,
                                                    contentDescription = "已收藏",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // --- 展开状态的内容 ---
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // 📌 1. 顶部标题栏
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 左侧：密码数量标签
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Layers,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = stringResource(R.string.passwords_count, passwords.size),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    
                                    // 右侧：收起按钮
                                    FilledTonalIconButton(
                                        onClick = onToggleExpand,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ExpandLess,
                                            contentDescription = stringResource(R.string.collapse),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                
                                // 分隔线
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                
                                // 📦 2. 密码列表内容
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val groupedByInfo = passwords.groupBy { getPasswordInfoKey(it) }
                                    
                                    groupedByInfo.values.forEachIndexed { groupIndex, passwordGroup ->
                                        // 列表项动画
                                        val itemEnterDelay = groupIndex * 30
                                        var isVisible by remember { mutableStateOf(false) }
                                        LaunchedEffect(Unit) {
                                            isVisible = true
                                        }
                                        
                                        AnimatedVisibility(
                                            visible = isVisible,
                                            enter = fadeIn(tween(300, delayMillis = itemEnterDelay)) + 
                                                    androidx.compose.animation.slideInVertically(
                                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                                        initialOffsetY = { 50 } 
                                                    ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                             takagi.ru.monica.ui.gestures.SwipeActions(
                                                onSwipeLeft = { onSwipeLeft(passwordGroup.first()) },
                                                onSwipeRight = { onSwipeRight(passwordGroup.first()) },
                                                enabled = true
                                            ) {
                                                if (passwordGroup.size == 1) {
                                                    val password = passwordGroup.first()
                                                    PasswordEntryCard(
                                                        entry = password,
                                                        onClick = {
                                                            if (isSelectionMode) {
                                                                onToggleSelection(password.id)
                                                            } else {
                                                                onPasswordClick(password)
                                                            }
                                                        },
                                                        onLongClick = { onLongClick(password) },
                                                        onToggleFavorite = { onToggleFavorite(password) },
                                                        onToggleGroupCover = if (passwords.size > 1) {
                                                            { onToggleGroupCover(password) }
                                                        } else null,
                                                        isSelectionMode = isSelectionMode,
                                                        isSelected = selectedPasswords.contains(password.id),
                                                        canSetGroupCover = passwords.size > 1,
                                                        isInExpandedGroup = true, // We are inside the expanded container
                                                        isSingleCard = false,
                                                        iconCardsEnabled = iconCardsEnabled,
                                                        passwordCardDisplayMode = passwordCardDisplayMode
                                                    )
                                                } else {
                                                    MultiPasswordEntryCard(
                                                        passwords = passwordGroup,
                                                        onClick = { password ->
                                                            if (isSelectionMode) {
                                                                onToggleSelection(password.id)
                                                            } else {
                                                                onPasswordClick(password)
                                                            }
                                                        },
                                                        onCardClick = if (!isSelectionMode) {
                                                            { onOpenMultiPasswordDialog(passwordGroup) }
                                                        } else null,
                                                        onLongClick = { onLongClick(passwordGroup.first()) },
                                                        onToggleFavorite = { password -> onToggleFavorite(password) },
                                                        onToggleGroupCover = if (passwords.size > 1) {
                                                            { password -> onToggleGroupCover(password) }
                                                        } else null,
                                                        isSelectionMode = isSelectionMode,
                                                        selectedPasswords = selectedPasswords,
                                                        canSetGroupCover = passwords.size > 1,
                                                        hasGroupCover = hasGroupCover,
                                                        isInExpandedGroup = true, // We are inside the expanded container
                                                        iconCardsEnabled = iconCardsEnabled,
                                                        passwordCardDisplayMode = passwordCardDisplayMode
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
            }
        }
    }
}

@Composable
private fun DocumentItemCard(
    item: takagi.ru.monica.data.SecureItem,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onToggleFavorite: ((Long, Boolean) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false
) {
    takagi.ru.monica.ui.components.DocumentCard(
        item = item,
        onClick = onClick,
        onDelete = onDelete,
        onToggleFavorite = onToggleFavorite,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        isSelectionMode = isSelectionMode,
        isSelected = isSelected
    )
}

/**
 * 多密码合并卡片
 * 显示除密码外其它信息相同的多个密码条目
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun MultiPasswordEntryCard(
    passwords: List<takagi.ru.monica.data.PasswordEntry>,
    onClick: (takagi.ru.monica.data.PasswordEntry) -> Unit, // 点击密码按钮 → 进入编辑页面
    onCardClick: (() -> Unit)? = null, // 点击卡片本身 → 打开详情对话框
    onLongClick: () -> Unit = {},
    onToggleFavorite: ((takagi.ru.monica.data.PasswordEntry) -> Unit)? = null,
    onToggleGroupCover: ((takagi.ru.monica.data.PasswordEntry) -> Unit)? = null,
    isSelectionMode: Boolean = false,
    selectedPasswords: Set<Long> = emptySet(),
    canSetGroupCover: Boolean = false,
    hasGroupCover: Boolean = false,
    isInExpandedGroup: Boolean = false,
    iconCardsEnabled: Boolean = false,
    passwordCardDisplayMode: takagi.ru.monica.data.PasswordCardDisplayMode = takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL
) {
    // 使用第一个条目的共同信息
    val firstEntry = passwords.first()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onCardClick?.invoke() },
                onLongClick = onLongClick
            ),
        colors = if (passwords.any { selectedPasswords.contains(it.id) }) {
            androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            androidx.compose.material3.CardDefaults.cardColors()
        },
        elevation = if (isInExpandedGroup) {
            androidx.compose.material3.CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
        } else {
            androidx.compose.material3.CardDefaults.cardElevation(
                defaultElevation = 3.dp,
                pressedElevation = 6.dp
            )
        },
        shape = RoundedCornerShape(if (isInExpandedGroup) 12.dp else 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isInExpandedGroup) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 头部：标题和图标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon (NEW) - only show if enabled
                if (iconCardsEnabled) {
                    val appIcon = if (!firstEntry.appPackageName.isNullOrBlank()) {
                         takagi.ru.monica.autofill.ui.rememberAppIcon(firstEntry.appPackageName)
                    } else null
                    
                    val favicon = if (firstEntry.website.isNotBlank()) {
                        takagi.ru.monica.autofill.ui.rememberFavicon(url = firstEntry.website, enabled = true)
                    } else null
                    
                    if (appIcon != null) {
                         Image(
                            bitmap = appIcon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                         )
                         Spacer(modifier = Modifier.width(12.dp))
                    } else if (favicon != null) {
                         Image(
                            bitmap = favicon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                         )
                         Spacer(modifier = Modifier.width(12.dp))
                    }
                }

                // 标题
                Text(
                    text = firstEntry.title,
                    style = if (isInExpandedGroup) {
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // 图标区域
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 封面星星图标
                    if (!isSelectionMode && onToggleGroupCover != null) {
                        passwords.forEach { entry ->
                            if (entry.isGroupCover) {
                                IconButton(
                                    onClick = { onToggleGroupCover(entry) },
                                    modifier = Modifier.size(36.dp),
                                    enabled = canSetGroupCover
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = "取消封面",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                return@forEach // 只显示一个
                            }
                        }
                    } else if (isSelectionMode && passwords.any { it.isGroupCover }) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "封面",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // 收藏心形图标
                    if (!isSelectionMode && onToggleFavorite != null) {
                        // 检查是否所有密码都已收藏
                        val allFavorited = passwords.all { it.isFavorite }
                        val anyFavorited = passwords.any { it.isFavorite }
                        
                        IconButton(
                            onClick = { 
                                // 批量切换所有密码的收藏状态
                                passwords.forEach { entry ->
                                    onToggleFavorite(entry)
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (anyFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (allFavorited) "取消收藏" else "收藏",
                                tint = if (anyFavorited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else if (isSelectionMode && passwords.any { it.isFavorite }) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = stringResource(R.string.favorite),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            // 网站信息
            if (passwordCardDisplayMode == takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL && firstEntry.website.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isInExpandedGroup) 6.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(if (isInExpandedGroup) 16.dp else 18.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    Text(
                        text = firstEntry.website,
                        style = if (isInExpandedGroup) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // 用户名信息
            if (passwordCardDisplayMode != takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_ONLY && firstEntry.username.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isInExpandedGroup) 6.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(if (isInExpandedGroup) 16.dp else 18.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    Text(
                        text = firstEntry.username,
                        style = if (isInExpandedGroup) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // 附加信息预览
            val additionalInfo = buildAdditionalInfoPreview(firstEntry)
            if (passwordCardDisplayMode == takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL && additionalInfo.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(if (isInExpandedGroup) 8.dp else 10.dp),
                    color = if (isInExpandedGroup) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (isInExpandedGroup) 12.dp else 14.dp,
                                vertical = if (isInExpandedGroup) 8.dp else 10.dp
                            ),
                        horizontalArrangement = Arrangement.spacedBy(if (isInExpandedGroup) 16.dp else 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        additionalInfo.take(2).forEach { info ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(if (isInExpandedGroup) 4.dp else 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    info.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(if (isInExpandedGroup) 14.dp else 16.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = info.text,
                                    style = if (isInExpandedGroup) {
                                        MaterialTheme.typography.labelSmall
                                    } else {
                                        MaterialTheme.typography.labelMedium
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            
            // 密码按钮区域
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "密码:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    passwords.forEachIndexed { index, password ->
                        val isSelected = selectedPasswords.contains(password.id)
                        
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            onClick = { onClick(password) },
                            modifier = Modifier
                                .heightIn(min = 32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelectionMode) {
                                    androidx.compose.material3.Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                
                                Icon(
                                    Icons.Default.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    }
                                )
                                
                                Text(
                                    text = "密码${index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 密码条目卡片
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
private fun PasswordEntryCard(
    entry: takagi.ru.monica.data.PasswordEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onToggleFavorite: (() -> Unit)? = null,
    onToggleGroupCover: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    canSetGroupCover: Boolean = false,
    isInExpandedGroup: Boolean = false,
    isSingleCard: Boolean = false,
    iconCardsEnabled: Boolean = false,
    passwordCardDisplayMode: takagi.ru.monica.data.PasswordCardDisplayMode = takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL
) {
    val sharedTransitionScope = takagi.ru.monica.ui.LocalSharedTransitionScope.current
    val animatedVisibilityScope = takagi.ru.monica.ui.LocalAnimatedVisibilityScope.current
    var sharedModifier: Modifier = Modifier
    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            sharedModifier = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "password_card_${entry.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = androidx.compose.animation.SharedTransitionScope.ResizeMode.ScaleToBounds()
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(sharedModifier),
        colors = if (isSelected) {
            androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            androidx.compose.material3.CardDefaults.cardColors()
        },
        elevation = if (isSingleCard) {
            // 单卡片：更突出的阴影
            androidx.compose.material3.CardDefaults.cardElevation(
                defaultElevation = 3.dp,
                pressedElevation = 6.dp
            )
        } else if (isInExpandedGroup) {
            androidx.compose.material3.CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
        } else {
            androidx.compose.material3.CardDefaults.cardElevation()
        },
        shape = if (isSingleCard) {
            RoundedCornerShape(16.dp) // 单卡片：更圆润
        } else {
            RoundedCornerShape(12.dp)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(
                    if (isSingleCard) 20.dp else 16.dp // 单卡片：更大的padding
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 复选框
            if (isSelectionMode) {
                androidx.compose.material3.Checkbox(
                    checked = isSelected,
                    onCheckedChange = null
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            // Icon - only show if enabled
            if (iconCardsEnabled) {
                val appIcon = if (!entry.appPackageName.isNullOrBlank()) {
                     takagi.ru.monica.autofill.ui.rememberAppIcon(entry.appPackageName)
                } else null
                
                val favicon = if (entry.website.isNotBlank()) {
                    takagi.ru.monica.autofill.ui.rememberFavicon(url = entry.website, enabled = true)
                } else null
                
                if (appIcon != null) {
                     Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                     )
                     Spacer(modifier = Modifier.width(16.dp))
                } else if (favicon != null) {
                     Image(
                        bitmap = favicon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                     )
                     Spacer(modifier = Modifier.width(16.dp))
                } else {
                     // Key Icon
                     Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                     ) {
                         Box(contentAlignment = Alignment.Center) {
                             Icon(
                                Icons.Default.Key, 
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                             )
                         }
                     }
                     Spacer(modifier = Modifier.width(16.dp))
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(
                    if (isSingleCard) 8.dp else 6.dp // 单卡片：更大的间距
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 标题 - 优化样式
                    Text(
                        text = entry.title,
                        style = if (isSingleCard) {
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // 图标区域 - 优化布局
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 封面星星图标 - 仅在组内且非选择模式下显示
                        if (!isSelectionMode && onToggleGroupCover != null) {
                            IconButton(
                                onClick = onToggleGroupCover,
                                modifier = Modifier.size(36.dp),
                                enabled = canSetGroupCover
                            ) {
                                Icon(
                                    if (entry.isGroupCover) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = if (entry.isGroupCover) "取消封面" else "设为封面",
                                    tint = if (entry.isGroupCover) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else if (canSetGroupCover) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else if (isSelectionMode && entry.isGroupCover) {
                            // 选择模式下只显示封面图标
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "封面",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        // 收藏心形图标 - 非选择模式下可点击
                        if (!isSelectionMode && onToggleFavorite != null) {
                            IconButton(
                                onClick = onToggleFavorite,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    if (entry.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = stringResource(R.string.favorite),
                                    tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else if (isSelectionMode && entry.isFavorite) {
                            // 选择模式下只显示,不可点击
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = stringResource(R.string.favorite),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                // 网站信息 - 优化显示
                if (passwordCardDisplayMode == takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL && entry.website.isNotBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            if (isSingleCard) 8.dp else 6.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(if (isSingleCard) 18.dp else 16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                        Text(
                            text = entry.website,
                            style = if (isSingleCard) {
                                MaterialTheme.typography.bodyLarge
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // 用户名信息 - 优化显示
                if (passwordCardDisplayMode != takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_ONLY && entry.username.isNotBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            if (isSingleCard) 8.dp else 10.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(if (isSingleCard) 18.dp else 16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                        Text(
                            text = entry.username,
                            style = if (isSingleCard) {
                                MaterialTheme.typography.bodyLarge
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // 新字段预览 - 优化显示样式
                val additionalInfo = buildAdditionalInfoPreview(entry)
                if (passwordCardDisplayMode == takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL && additionalInfo.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(if (isSingleCard) 10.dp else 8.dp),
                        color = if (isSingleCard) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = if (isSingleCard) 14.dp else 12.dp,
                                    vertical = if (isSingleCard) 10.dp else 8.dp
                                ),
                            horizontalArrangement = Arrangement.spacedBy(
                                if (isSingleCard) 20.dp else 16.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            additionalInfo.take(2).forEach { info ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(
                                        if (isSingleCard) 6.dp else 4.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(
                                        info.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(if (isSingleCard) 16.dp else 14.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = info.text,
                                        style = if (isSingleCard) {
                                            MaterialTheme.typography.labelMedium
                                        } else {
                                            MaterialTheme.typography.labelSmall
                                        },
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

/**
 * 附加信息预览数据类
 * 
 * 用于在密码卡片上显示额外的预览信息。
 * 
 * @param icon Material Icon 图标
 * @param text 显示文本（已格式化或掩码处理）
 */
private data class AdditionalInfoItem(
    val icon: ImageVector,
    val text: String
)

/**
 * 构建附加信息预览列表
 * 
 * 根据优先级从密码条目中提取最重要的附加信息用于卡片预览。
 * 
 * ## 优先级顺序
 * 1. � **关联应用** (appName) - 显示应用图标和名称
 * 2. �📧 **邮箱** (email) - 最常用的登录凭证
 * 3. 📱 **手机号** (phone) - 账户绑定信息，自动格式化显示
 * 4. 💳 **信用卡号** (creditCard) - 支付信息，掩码显示仅后4位
 * 5. 📍 **城市** (city) - 地址信息代表
 * 
 * ## 显示规则
 * - 最多返回 **2项** 预览信息，避免卡片拥挤
 * - 使用 FieldValidation 工具类进行格式化和掩码处理
 * - 空字段自动跳过
 * 
 * ## 示例输出
 * ```
 * � 微信  �📧 user@example.com
 * 📱 +86 138 0013 8000  💳 •••• •••• •••• 1234
 * ```
 * 
 * @param entry 密码条目
 * @return 附加信息列表，最多2项
 */
private fun buildAdditionalInfoPreview(entry: takagi.ru.monica.data.PasswordEntry): List<AdditionalInfoItem> {
    val items = mutableListOf<AdditionalInfoItem>()
    
    // 1. 关联应用（最高优先级）
    if (entry.appName.isNotBlank()) {
        items.add(AdditionalInfoItem(
            icon = Icons.Default.Apps,
            text = entry.appName
        ))
    }
    
    // 2. 邮箱
    if (entry.email.isNotBlank() && items.size < 2) {
        items.add(AdditionalInfoItem(
            icon = Icons.Default.Email,
            text = entry.email
        ))
    }
    
    // 3. 手机号
    if (entry.phone.isNotBlank() && items.size < 2) {
        items.add(AdditionalInfoItem(
            icon = Icons.Default.Phone,
            text = takagi.ru.monica.utils.FieldValidation.formatPhone(entry.phone)
        ))
    }
    
    // 4. 信用卡号（掩码显示）
    if (entry.creditCardNumber.isNotBlank() && items.size < 2) {
        items.add(AdditionalInfoItem(
            icon = Icons.Default.CreditCard,
            text = takagi.ru.monica.utils.FieldValidation.maskCreditCard(entry.creditCardNumber)
        ))
    }
    
    // 5. 城市信息（如果还有空间）
    if (entry.city.isNotBlank() && items.size < 2) {
        items.add(AdditionalInfoItem(
            icon = Icons.Default.LocationOn,
            text = entry.city
        ))
    }
    
    return items
}

/**
 * 选择模式顶栏组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionModeTopBar(
    selectedCount: Int,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onFavorite: (() -> Unit)? = null,
    onMoveToCategory: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    TopAppBar(
        title = { 
            // 使用 Row 确保文本不会被截断
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.selected_items, selectedCount),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onExit) {
                Icon(
                    Icons.Default.Close, 
                    contentDescription = stringResource(R.string.exit_selection_mode)
                )
            }
        },
        actions = {
            // 使用 Row 确保图标按钮不会被挤压
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 全选/取消全选
                IconButton(onClick = onSelectAll) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = stringResource(R.string.select_all)
                    )
                }
                
                // 批量移动到分类
                if (onMoveToCategory != null) {
                    IconButton(
                        onClick = onMoveToCategory,
                        enabled = selectedCount > 0
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = "移动到分类",
                            tint = if (selectedCount > 0) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }

                // 批量收藏按钮 (仅部分列表显示)
                if (onFavorite != null) {
                    IconButton(
                        onClick = onFavorite,
                        enabled = selectedCount > 0
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = stringResource(R.string.batch_favorite),
                            tint = if (selectedCount > 0) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
                
                // 批量删除按钮
                IconButton(
                    onClick = onDelete,
                    enabled = selectedCount > 0
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.batch_delete),
                        tint = if (selectedCount > 0) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

private const val SETTINGS_TAB_KEY = "SETTINGS"

@Composable
private fun CategoryListItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    menu: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface

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
        leadingContent = {
            Icon(icon, contentDescription = null)
        },
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = badge,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (selected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = contentColor)
                }
                menu?.invoke()
            }
        }
    )
}

sealed class BottomNavItem(
    val contentTab: BottomNavContentTab?,
    val icon: ImageVector
) {
    val key: String = contentTab?.name ?: SETTINGS_TAB_KEY

    object Passwords : BottomNavItem(BottomNavContentTab.PASSWORDS, Icons.Default.Lock)
    object Authenticator : BottomNavItem(BottomNavContentTab.AUTHENTICATOR, Icons.Default.Security)
    object CardWallet : BottomNavItem(BottomNavContentTab.CARD_WALLET, Icons.Default.Wallet)
    object Generator : BottomNavItem(BottomNavContentTab.GENERATOR, Icons.Default.AutoAwesome)  // 添加生成器导航项
    object Notes : BottomNavItem(BottomNavContentTab.NOTES, Icons.Default.Note)
    object Timeline : BottomNavItem(BottomNavContentTab.TIMELINE, Icons.Default.AccountTree)  // 时间线导航（Git分支图标）
    object Passkey : BottomNavItem(BottomNavContentTab.PASSKEY, Icons.Default.Key)  // Passkey 通行密钥导航
    object Settings : BottomNavItem(null, Icons.Default.Settings)
}

private fun BottomNavContentTab.toBottomNavItem(): BottomNavItem = when (this) {
    BottomNavContentTab.PASSWORDS -> BottomNavItem.Passwords
    BottomNavContentTab.AUTHENTICATOR -> BottomNavItem.Authenticator
    BottomNavContentTab.CARD_WALLET -> BottomNavItem.CardWallet
    BottomNavContentTab.GENERATOR -> BottomNavItem.Generator  // 添加生成器映射
    BottomNavContentTab.NOTES -> BottomNavItem.Notes
    BottomNavContentTab.TIMELINE -> BottomNavItem.Timeline
    BottomNavContentTab.PASSKEY -> BottomNavItem.Passkey
}

private fun BottomNavItem.fullLabelRes(): Int = when (this) {
    BottomNavItem.Passwords -> R.string.nav_passwords
    BottomNavItem.Authenticator -> R.string.nav_authenticator
    BottomNavItem.CardWallet -> R.string.nav_card_wallet
    BottomNavItem.Generator -> R.string.nav_generator  // 添加生成器标签资源
    BottomNavItem.Notes -> R.string.nav_notes
    BottomNavItem.Timeline -> R.string.nav_timeline
    BottomNavItem.Passkey -> R.string.nav_passkey
    BottomNavItem.Settings -> R.string.nav_settings
}

private fun BottomNavItem.shortLabelRes(): Int = when (this) {
    BottomNavItem.Passwords -> R.string.nav_passwords_short
    BottomNavItem.Authenticator -> R.string.nav_authenticator_short
    BottomNavItem.CardWallet -> R.string.nav_card_wallet_short
    BottomNavItem.Generator -> R.string.nav_generator_short  // 添加生成器短标签资源
    BottomNavItem.Notes -> R.string.nav_notes_short
    BottomNavItem.Timeline -> R.string.nav_timeline_short
    BottomNavItem.Passkey -> R.string.nav_passkey_short
    BottomNavItem.Settings -> R.string.nav_settings_short
}

private fun indexToDefaultTabKey(index: Int): String = when (index) {
    0 -> BottomNavContentTab.PASSWORDS.name
    1 -> BottomNavContentTab.AUTHENTICATOR.name
    2 -> BottomNavContentTab.CARD_WALLET.name
    3 -> BottomNavContentTab.GENERATOR.name
    4 -> BottomNavContentTab.NOTES.name
    5 -> SETTINGS_TAB_KEY
    else -> BottomNavContentTab.PASSWORDS.name
}

/**
 * 支持指纹验证的删除确认对话框
 * 
 * @param itemTitle 要删除的项目标题
 * @param itemType 项目类型描述（如"密码"、"验证器"、"证件"）
 * @param onDismiss 取消删除的回调
 * @param onConfirmWithPassword 使用密码确认删除的回调
 * @param onConfirmWithBiometric 使用指纹确认删除的回调
 */
@Composable
private fun DeleteConfirmDialog(
    itemTitle: String,
    itemType: String = "项目",
    onDismiss: () -> Unit,
    onConfirmWithPassword: (String) -> Unit,
    onConfirmWithBiometric: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var passwordInput by remember { mutableStateOf("") }
    val biometricHelper = remember { BiometricHelper(context) }
    val isBiometricAvailable = remember { biometricHelper.isBiometricAvailable() }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("删除$itemType") },
        text = {
            Column {
                Text("确定要删除$itemType \"$itemTitle\" 吗？")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 密码输入框
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text(stringResource(R.string.enter_master_password_confirm)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // 指纹验证提示
                if (isBiometricAvailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "或使用指纹验证",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 指纹验证按钮
                if (isBiometricAvailable && activity != null) {
                    IconButton(
                        onClick = {
                            biometricHelper.authenticate(
                                activity = activity,
                                title = "验证身份",
                                subtitle = "确认删除$itemType",
                                description = "使用生物识别快速确认删除操作",
                                onSuccess = {
                                    onConfirmWithBiometric()
                                },
                                onError = { error ->
                                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                },
                                onFailed = {
                                    // 用户取消，不做任何操作
                                }
                            )
                        }
                    ) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = "使用指纹验证",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // 密码验证按钮
                TextButton(
                    onClick = {
                        if (passwordInput.isNotEmpty()) {
                            onConfirmWithPassword(passwordInput)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = passwordInput.isNotEmpty()
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * 为密码条目生成唯一键(除密码外的所有关键字段)
 * 用于将除密码外其它信息相同的条目合并显示
 */
private fun getPasswordInfoKey(entry: takagi.ru.monica.data.PasswordEntry): String {
    return "${entry.title}|${entry.website}|${entry.username}|${entry.notes}|${entry.appPackageName}|${entry.appName}"
}

/**
 * 生成密码分组标题,按备注>网站>应用>标题的优先顺序选择第一个非空字段
 */
private fun getGroupKeyForMode(entry: takagi.ru.monica.data.PasswordEntry, mode: String): String {
    val noteLabel = entry.notes
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
    val website = entry.website.trim()
    val appName = entry.appName.trim()
    val packageName = entry.appPackageName.trim()
    val title = entry.title.trim()
    val idKey = "id-${entry.id}"

    return when (mode) {
        // 只按备注；若备注为空则不分组（使用唯一键避免堆叠）
        "note" -> noteLabel.takeUnless { it.isNullOrEmpty() } ?: idKey

        // 只按网站；若网站为空则不分组
        "website" -> website.takeUnless { it.isEmpty() } ?: idKey

        // 只按应用；若应用名/包名都空则不分组
        "app" -> appName.takeUnless { it.isEmpty() }
            ?: packageName.takeUnless { it.isEmpty() }
            ?: idKey

        // 只按标题；若标题为空则不分组
        "title" -> title.takeUnless { it.isEmpty() } ?: idKey

        else -> {
            // smart: 备注 > 网站 > 应用 > 标题，若都空则不分组
            noteLabel.takeUnless { it.isNullOrEmpty() }
                ?: website.takeUnless { it.isEmpty() }
                ?: appName.takeUnless { it.isEmpty() }
                ?: packageName.takeUnless { it.isEmpty() }
                ?: title.takeUnless { it.isEmpty() }
                ?: idKey
        }
    }
}

private fun getPasswordGroupTitle(entry: takagi.ru.monica.data.PasswordEntry): String =
    getGroupKeyForMode(entry, "smart")

private enum class StackCardMode {
    AUTO,            // 根据卡片类型自动决定堆叠/展开
    ALWAYS_EXPANDED  // 默认展开所有堆叠组（逐条显示，不堆叠）
}
