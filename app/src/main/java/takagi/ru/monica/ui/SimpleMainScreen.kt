package takagi.ru.monica.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.viewmodel.GeneratorViewModel
import takagi.ru.monica.ui.screens.SettingsScreen
import takagi.ru.monica.ui.screens.GeneratorScreen  // 添加生成器页面导入
import takagi.ru.monica.ui.gestures.SwipeActions
import takagi.ru.monica.ui.haptic.rememberHapticFeedback
import kotlin.math.absoluteValue

import takagi.ru.monica.ui.components.QrCodeDialog

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
    onNavigateToAddPassword: (Long?) -> Unit,
    onNavigateToAddTotp: (Long?) -> Unit,
    onNavigateToAddBankCard: (Long?) -> Unit,
    onNavigateToAddDocument: (Long?) -> Unit,
    @Suppress("UNUSED_PARAMETER")
    onNavigateToDocumentDetail: (Long) -> Unit, // 保留以保持API兼容性，但当前未使用
    onNavigateToChangePassword: () -> Unit = {},
    onNavigateToSecurityQuestion: () -> Unit = {},
    onNavigateToExportData: () -> Unit = {},
    onNavigateToImportData: () -> Unit = {},
    onNavigateToWebDav: () -> Unit = {},
    onNavigateToAutofill: () -> Unit = {},
    onNavigateToBottomNavSettings: () -> Unit = {},
    onNavigateToColorScheme: () -> Unit = {},
    onSecurityAnalysis: () -> Unit = {},
    onNavigateToDeveloperSettings: () -> Unit = {},
    onNavigateToPermissionManagement: () -> Unit = {},
    onNavigateToMonicaPlus: () -> Unit = {},
    onClearAllData: (Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    initialTab: Int = 0
) {
    val defaultTabKey = remember(initialTab) { indexToDefaultTabKey(initialTab) }
    var selectedTabKey by rememberSaveable { mutableStateOf(defaultTabKey) }
    
    // 双击返回退出相关状态
    var backPressedOnce by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 处理返回键 - 需要按两次才能退出
    BackHandler {
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
    var onDeleteSelectedPasswords by remember { mutableStateOf({}) }
    
    // 密码分组模式: "website" 按网站分组, "title" 按标题分组
    var passwordGroupMode by rememberSaveable { mutableStateOf("website") }
    
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

    val appSettings by settingsViewModel.settings.collectAsState()
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

    LaunchedEffect(tabs) {
        if (tabs.none { it.key == selectedTabKey }) {
            selectedTabKey = tabs.first().key
        }
    }

    val currentTab = tabs.firstOrNull { it.key == selectedTabKey } ?: tabs.first()
    val currentTabLabel = stringResource(currentTab.fullLabelRes())

    Scaffold(
        topBar = {
            // 根据不同页面的选择模式显示对应的顶栏
            when {
                // 密码页面选择模式
                currentTab == BottomNavItem.Passwords && isPasswordSelectionMode -> {
                    SelectionModeTopBar(
                        selectedCount = selectedPasswordCount,
                        onExit = { onExitPasswordSelection() },
                        onSelectAll = { onSelectAllPasswords() },
                        onFavorite = { onFavoriteSelectedPasswords() },
                        onDelete = { onDeleteSelectedPasswords() }
                    )
                }
                // TOTP页面选择模式
                currentTab == BottomNavItem.Authenticator && isTotpSelectionMode -> {
                    SelectionModeTopBar(
                        selectedCount = selectedTotpCount,
                        onExit = { onExitTotpSelection() },
                        onSelectAll = { onSelectAllTotp() },
                        onDelete = { onDeleteSelectedTotp() }
                    )
                }
                // 证件页面选择模式
                currentTab == BottomNavItem.Documents && isDocumentSelectionMode -> {
                    SelectionModeTopBar(
                        selectedCount = selectedDocumentCount,
                        onExit = { onExitDocumentSelection() },
                        onSelectAll = { onSelectAllDocuments() },
                        onDelete = { onDeleteSelectedDocuments() }
                    )
                }
                // 银行卡页面选择模式
                currentTab == BottomNavItem.BankCards && isBankCardSelectionMode -> {
                    SelectionModeTopBar(
                        selectedCount = selectedBankCardCount,
                        onExit = { onExitBankCardSelection() },
                        onSelectAll = { onSelectAllBankCards() },
                        onDelete = { onDeleteSelectedBankCards() },
                        onFavorite = { onFavoriteBankCards() }  // 添加收藏按钮
                    )
                }
                // 生成器页面不需要顶栏
                currentTab == BottomNavItem.Generator -> {
                    // 不显示顶部栏
                }
                // 正常顶栏
                else -> {
                    TopAppBar(
                        title = {
                            Text(currentTabLabel)
                        },
                        actions = {
                            // 只在密码页面显示分组切换按钮
                            if (currentTab == BottomNavItem.Passwords) {
                                IconButton(onClick = {
                                    passwordGroupMode = when (passwordGroupMode) {
                                        "website" -> "title"
                                        else -> "website"
                                    }
                                }) {
                                    Icon(
                                        when (passwordGroupMode) {
                                            "website" -> Icons.Default.Language  // 地球图标表示按网站
                                            else -> Icons.Default.Title          // 标题图标表示按标题
                                        },
                                        contentDescription = when (passwordGroupMode) {
                                            "website" -> "按网站分组"
                                            else -> "按标题分组"
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            }
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
        floatingActionButton = {
            when (currentTab) {
                BottomNavItem.Passwords -> {
                    FloatingActionButton(
                        onClick = { onNavigateToAddPassword(null) }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                    }
                }

                BottomNavItem.Authenticator -> {
                    FloatingActionButton(
                        onClick = { onNavigateToAddTotp(null) }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                    }
                }

                BottomNavItem.Documents -> {
                    FloatingActionButton(
                        onClick = { onNavigateToAddDocument(null) }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                    }
                }

                BottomNavItem.BankCards -> {
                    FloatingActionButton(
                        onClick = { onNavigateToAddBankCard(null) }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                    }
                }

                BottomNavItem.Generator -> {
                    // 生成器页面不需要 FAB
                }

                BottomNavItem.Settings -> {}
            }
        }
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
                        groupMode = passwordGroupMode,
                        onPasswordClick = { password ->
                            onNavigateToAddPassword(password.id)
                        },
                        onNavigateToAddPassword = onNavigateToAddPassword,
                        onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onFavorite, onDelete ->
                            isPasswordSelectionMode = isSelectionMode
                            selectedPasswordCount = count
                            onExitPasswordSelection = onExit
                            onSelectAllPasswords = onSelectAll
                            onFavoriteSelectedPasswords = onFavorite
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
                        onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onDelete ->
                            isTotpSelectionMode = isSelectionMode
                            selectedTotpCount = count
                            onExitTotpSelection = onExit
                            onSelectAllTotp = onSelectAll
                            onDeleteSelectedTotp = onDelete
                        }
                    )
                }
                BottomNavItem.Documents -> {
                    // 文档页面
                    DocumentListContent(
                        viewModel = documentViewModel,
                        onDocumentClick = { documentId -> 
                            // 直接导航到编辑页面而不是详情页面
                            onNavigateToAddDocument(documentId)
                        },
                        onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onDelete ->
                            isDocumentSelectionMode = isSelectionMode
                            selectedDocumentCount = count
                            onExitDocumentSelection = onExit
                            onSelectAllDocuments = onSelectAll
                            onDeleteSelectedDocuments = onDelete
                        }
                    )
                }
                BottomNavItem.BankCards -> {
                    // 银行卡页面
                    BankCardListContent(
                        viewModel = bankCardViewModel,
                        onCardClick = { cardId ->
                            onNavigateToAddBankCard(cardId)
                        },
                        onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onDelete, onFavorite ->
                            isBankCardSelectionMode = isSelectionMode
                            selectedBankCardCount = count
                            onExitBankCardSelection = onExit
                            onSelectAllBankCards = onSelectAll
                            onDeleteSelectedBankCards = onDelete
                            onFavoriteBankCards = onFavorite  // 添加收藏回调
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
                BottomNavItem.Settings -> {
                    // 设置页面 - 使用完整的SettingsScreen
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateBack = {}, // 在主屏幕中不需要返回
                        onResetPassword = onNavigateToChangePassword,
                        onSecurityQuestions = onNavigateToSecurityQuestion,
                        onExportData = onNavigateToExportData,
                        onImportData = onNavigateToImportData,
                        onNavigateToWebDav = onNavigateToWebDav,
                        onNavigateToAutofill = onNavigateToAutofill,
                        onNavigateToBottomNavSettings = onNavigateToBottomNavSettings,
                        onNavigateToColorScheme = onNavigateToColorScheme,
                        onSecurityAnalysis = onSecurityAnalysis,
                        onNavigateToDeveloperSettings = onNavigateToDeveloperSettings,
                        onNavigateToPermissionManagement = onNavigateToPermissionManagement,
                        onNavigateToMonicaPlus = onNavigateToMonicaPlus,
                        onClearAllData = onClearAllData,
                        showTopBar = false  // 在标签页中不显示顶栏
                    )
                }
            }
        }
    }
}

/**
 * 密码列表内容
 */
@Composable
private fun PasswordListContent(
    viewModel: PasswordViewModel,
    groupMode: String = "none",
    onPasswordClick: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onNavigateToAddPassword: (Long?) -> Unit,
    onSelectionModeChange: (
        isSelectionMode: Boolean,
        selectedCount: Int,
        onExit: () -> Unit,
        onSelectAll: () -> Unit,
        onFavorite: () -> Unit,
        onDelete: () -> Unit
    ) -> Unit
) {
    val passwordEntries by viewModel.passwordEntries.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    // 选择模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedPasswords by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    
    // 详情对话框状态
    var showDetailDialog by remember { mutableStateOf(false) }
    var selectedPasswordForDetail by remember { mutableStateOf<takagi.ru.monica.data.PasswordEntry?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var showPasswordVerify by remember { mutableStateOf(false) }
    
    // 多密码详情对话框状态
    var showMultiPasswordDialog by remember { mutableStateOf(false) }
    var selectedMultiPasswords by remember { mutableStateOf<List<takagi.ru.monica.data.PasswordEntry>>(emptyList()) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // 添加触觉反馈
    val haptic = rememberHapticFeedback()
    
    // 添加单项删除对话框状态
    var itemToDelete by remember { mutableStateOf<takagi.ru.monica.data.PasswordEntry?>(null) }
    var singleItemPasswordInput by remember { mutableStateOf("") }
    var showSingleItemPasswordVerify by remember { mutableStateOf(false) }
    
    // 添加已删除项ID集合（用于在验证前隐藏项）
    var deletedItemIds by remember { mutableStateOf(setOf<Long>()) }
    
    // 堆叠展开状态 - 记录哪些分组已展开
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }
    
    // 当分组模式改变时,重置展开状态
    LaunchedEffect(groupMode) {
        expandedGroups = setOf()
    }
    
    // 根据分组模式对密码进行分组
    val groupedPasswords = remember(passwordEntries, deletedItemIds, groupMode) {
        val filteredEntries = passwordEntries.filter { it.id !in deletedItemIds }
        
        // 步骤1: 先按"除密码外的信息"合并
        val mergedByInfo = filteredEntries
            .groupBy { getPasswordInfoKey(it) }
            .map { (_, entries) -> 
                // 如果有多个密码,保留所有但标记为合并组
                entries.sortedBy { it.sortOrder }
            }
        
        // 步骤2: 再按显示模式分组
        when (groupMode) {
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
                // 按网站分组密码(默认),并按优先级排序
                mergedByInfo
                    .groupBy { entries -> entries.first().website.ifBlank { "未分类" } }
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
            deleteSelected
        )
    }

    Column {
        // 搜索框 - 非选择模式下显示
        if (!isSelectionMode) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text(stringResource(R.string.search_passwords_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp)
            )
        } else {
            // 选择模式下添加顶部间距,避免内容被顶栏遮挡
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 密码列表 - 使用堆叠分组视图
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            groupedPasswords.forEach { (groupKey, passwords) ->
                    item(key = "group_$groupKey") {
                        StackedPasswordGroup(
                            website = groupKey,
                            passwords = passwords,
                            isExpanded = expandedGroups.contains(groupKey),
                            onToggleExpand = {
                                expandedGroups = if (expandedGroups.contains(groupKey)) {
                                    expandedGroups - groupKey
                                } else {
                                    expandedGroups + groupKey
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
                                // 普通模式：显示详情对话框
                                selectedPasswordForDetail = password
                                showDetailDialog = true
                            }
                        },
                        onSwipeLeft = { password ->
                            // 左滑删除
                            haptic.performWarning()
                            itemToDelete = password
                            deletedItemIds = deletedItemIds + password.id
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
                            // 打开多密码详情对话框
                            showMultiPasswordDialog = true
                            selectedMultiPasswords = passwords
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
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
    
    // 密码详情对话框
    if (showDetailDialog && selectedPasswordForDetail != null) {
        takagi.ru.monica.ui.components.PasswordDetailDialog(
            passwordEntry = selectedPasswordForDetail!!,
            onDismiss = {
                showDetailDialog = false
                selectedPasswordForDetail = null
            },
            onEdit = {
                showDetailDialog = false
                onPasswordClick(selectedPasswordForDetail!!)
                selectedPasswordForDetail = null
            },
            onDelete = {
                coroutineScope.launch {
                    viewModel.deletePasswordEntry(selectedPasswordForDetail!!)
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.deleted_items, 1),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    showDetailDialog = false
                    selectedPasswordForDetail = null
                }
            },
            onAddPassword = {
                // 关闭详情对话框后，导航到编辑页面
                // 编辑页面会检测到要复制的密码ID并预填充信息
                val currentEntry = selectedPasswordForDetail!!
                showDetailDialog = false
                selectedPasswordForDetail = null
                // 使用负数ID表示"复制模式" - 导航到添加页面时会复制该密码的信息
                onNavigateToAddPassword(-currentEntry.id.toLong())
            }
        )
    }
    
    // 多密码详情对话框
    if (showMultiPasswordDialog && selectedMultiPasswords.isNotEmpty()) {
        takagi.ru.monica.ui.components.MultiPasswordDetailDialog(
            passwords = selectedMultiPasswords,
            onDismiss = {
                showMultiPasswordDialog = false
                selectedMultiPasswords = emptyList()
            },
            onAddPassword = {
                // 关闭多密码详情对话框，导航到添加页面（复制模式）
                val firstEntry = selectedMultiPasswords.first()
                showMultiPasswordDialog = false
                selectedMultiPasswords = emptyList()
                onNavigateToAddPassword(-firstEntry.id.toLong())
            },
            onEditPassword = { password ->
                // 关闭多密码详情对话框，导航到编辑页面
                showMultiPasswordDialog = false
                selectedMultiPasswords = emptyList()
                onPasswordClick(password)
            },
            onDeletePassword = { password ->
                // 删除单个密码
                coroutineScope.launch {
                    viewModel.deletePasswordEntry(password)
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.deleted_items, 1),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    // 从列表中移除已删除的密码
                    val updatedList = selectedMultiPasswords.filter { it.id != password.id }
                    if (updatedList.isEmpty()) {
                        // 如果删除后没有密码了，关闭对话框
                        showMultiPasswordDialog = false
                        selectedMultiPasswords = emptyList()
                    } else {
                        // 更新列表
                        selectedMultiPasswords = updatedList
                    }
                }
            },
            onToggleFavorite = { password ->
                // 切换收藏状态
                coroutineScope.launch {
                    viewModel.toggleFavorite(password.id, !password.isFavorite)
                    // 更新列表中的密码状态
                    selectedMultiPasswords = selectedMultiPasswords.map {
                        if (it.id == password.id) {
                            it.copy(isFavorite = !it.isFavorite)
                        } else {
                            it
                        }
                    }
                }
            }
        )
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
@Composable
private fun TotpListContent(
    viewModel: takagi.ru.monica.viewmodel.TotpViewModel,
    onTotpClick: (Long) -> Unit,
    onDeleteTotp: (takagi.ru.monica.data.SecureItem) -> Unit,
    onSelectionModeChange: (
        isSelectionMode: Boolean,
        selectedCount: Int,
        onExit: () -> Unit,
        onSelectAll: () -> Unit,
        onDelete: () -> Unit
    ) -> Unit
) {
    val totpItems by viewModel.totpItems.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val haptic = rememberHapticFeedback()
    
    // 选择模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var showPasswordVerify by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
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
        // 搜索框 - 非选择模式下显示
        if (!isSelectionMode) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text(stringResource(R.string.search_authenticator)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true,
                shape = RoundedCornerShape(28.dp)
            )
        } else {
            // 选择模式下添加顶部间距
            Spacer(modifier = Modifier.height(8.dp))
        }

        // TOTP列表
        if (filteredTotpItems.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier.fillMaxSize(),
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = filteredTotpItems,
                    key = { it.id }
                ) { item ->
                    val index = filteredTotpItems.indexOf(item)
                    
                    // 用 SwipeActions 包裹 TOTP 卡片
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
                        enabled = true // 多选模式下也可以滑动
                    ) {
                        TotpItemCard(
                            item = item,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedItems = if (selectedItems.contains(item.id)) {
                                        selectedItems - item.id
                                    } else {
                                        selectedItems + item.id
                                    }
                                } else {
                                    onTotpClick(item.id)
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
                            onMoveUp = if (index > 0) {
                                {
                                    // 交换当前项和上一项的sortOrder
                                    val currentItem = filteredTotpItems[index]
                                    val previousItem = filteredTotpItems[index - 1]
                                    viewModel.updateSortOrders(listOf(
                                        currentItem.id to (index - 1),
                                        previousItem.id to index
                                    ))
                                }
                            } else null,
                            onMoveDown = if (index < filteredTotpItems.size - 1) {
                                {
                                    // 交换当前项和下一项的sortOrder
                                    val currentItem = filteredTotpItems[index]
                                    val nextItem = filteredTotpItems[index + 1]
                                    viewModel.updateSortOrders(listOf(
                                        currentItem.id to (index + 1),
                                        nextItem.id to index
                                    ))
                                }
                            } else null,
                            onShowQrCode = {
                                itemToShowQr = item
                            },
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedItems.contains(item.id)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onGenerateNext: ((Long) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onShowQrCode: ((takagi.ru.monica.data.SecureItem) -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // 直接使用修改后的 TotpCodeCard 组件
    takagi.ru.monica.ui.components.TotpCodeCard(
        item = item,
        onClick = onClick,
        onCopyCode = { code ->
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("TOTP Code", code)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(context, "验证码已复制", android.widget.Toast.LENGTH_SHORT).show()
        },
        onDelete = onDelete,
        onToggleFavorite = onToggleFavorite,
        onGenerateNext = onGenerateNext,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onShowQrCode = onShowQrCode,
        isSelectionMode = isSelectionMode,
        isSelected = isSelected
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
    onToggleExpand: () -> Unit,
    onPasswordClick: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onSwipeLeft: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onSwipeRight: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onToggleFavorite: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onToggleGroupFavorite: () -> Unit,
    onToggleGroupCover: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    isSelectionMode: Boolean,
    selectedPasswords: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onOpenMultiPasswordDialog: (List<takagi.ru.monica.data.PasswordEntry>) -> Unit // 新增：打开多密码详情对话框
) {
    // 检查是否为多密码合并卡片(除密码外信息完全相同)
    val isMergedPasswordCard = passwords.size > 1 && 
        passwords.map { getPasswordInfoKey(it) }.distinct().size == 1
    
    // 如果是多密码合并卡片,直接显示为单卡片,不堆叠
    if (isMergedPasswordCard) {
        takagi.ru.monica.ui.gestures.SwipeActions(
            onSwipeLeft = { onSwipeLeft(passwords.first()) },
            onSwipeRight = { onSwipeRight(passwords.first()) },
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
                onLongClick = {},
                onToggleFavorite = { password -> onToggleFavorite(password) },
                onToggleGroupCover = null,
                isSelectionMode = isSelectionMode,
                selectedPasswords = selectedPasswords,
                canSetGroupCover = false,
                hasGroupCover = false,
                isInExpandedGroup = false
            )
        }
        return
    }
    
    // 否则使用原有的堆叠逻辑
    val isGroupFavorited = passwords.all { it.isFavorite }
    val hasGroupCover = passwords.any { it.isGroupCover }
    
    // 🎨 动画状态
    val expandProgress by animateFloatAsState(
        targetValue = if (isExpanded && passwords.size > 1) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "expand_animation"
    )
    
    val containerAlpha by animateFloatAsState(
        targetValue = if (isExpanded && passwords.size > 1) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "container_alpha"
    )
    
    // 🎯 下滑手势状态
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val haptic = rememberHapticFeedback()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isExpanded && passwords.size > 1) {
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
        if (!isExpanded && passwords.size > 1) {
            // 📚 堆叠视图 - 层级式布局
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 底层卡片阴影 - 3层堆叠效果
                val stackCount = passwords.size.coerceAtMost(3)
                for (i in (stackCount - 1) downTo 1) {
                    val offsetDp = (i * 4).dp
                    val scaleFactor = 1f - (i * 0.02f)
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = offsetDp, end = offsetDp, top = offsetDp)
                            .graphicsLayer {
                                scaleX = scaleFactor
                                scaleY = scaleFactor
                                alpha = 0.7f - (i * 0.2f)
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = (i * 1.5).dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Box(modifier = Modifier.height(76.dp))
                    }
                }
                
                // 顶层主卡片
                takagi.ru.monica.ui.gestures.SwipeActions(
                    onSwipeLeft = { onSwipeLeft(passwords.first()) },
                    onSwipeRight = { onSwipeRight(passwords.first()) },
                    enabled = true
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                shadowElevation = 6f
                            },
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 10.dp
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = if (selectedPasswords.contains(passwords.first().id)) {
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            CardDefaults.cardColors()
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleExpand() }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelectionMode) {
                                androidx.compose.material3.Checkbox(
                                    checked = selectedPasswords.contains(passwords.first().id),
                                    onCheckedChange = null
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
                    }
                }
            }
        } else {
            // 🎭 展开视图 - 统一的 Material 3 卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = swipeOffset * 0.5f
                    }
            ) {
                if (isExpanded && passwords.size > 1) {
                    // � 统一的卡片容器
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(containerAlpha),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 4.dp
                        )
                    ) {
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
                                // 🎴 按信息分组合并显示密码
                                val groupedByInfo = passwords.groupBy { getPasswordInfoKey(it) }
                                
                                groupedByInfo.values.forEachIndexed { groupIndex, passwordGroup ->
                                    val cardAlpha by animateFloatAsState(
                                        targetValue = 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        ),
                                        label = "card_${groupIndex}_alpha"
                                    )
                                    
                                    val cardOffset by animateDpAsState(
                                        targetValue = 0.dp,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        label = "card_${groupIndex}_offset"
                                    )
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer {
                                                alpha = cardAlpha
                                                translationY = cardOffset.toPx()
                                            }
                                    ) {
                                        takagi.ru.monica.ui.gestures.SwipeActions(
                                            onSwipeLeft = { onSwipeLeft(passwordGroup.first()) },
                                            onSwipeRight = { onSwipeRight(passwordGroup.first()) },
                                            enabled = true
                                        ) {
                                            // 如果只有一个密码,使用原来的 PasswordEntryCard
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
                                                    onLongClick = {},
                                                    onToggleFavorite = { onToggleFavorite(password) },
                                                    onToggleGroupCover = if (passwords.size > 1) {
                                                        { onToggleGroupCover(password) }
                                                    } else null,
                                                    isSelectionMode = isSelectionMode,
                                                    isSelected = selectedPasswords.contains(password.id),
                                                    canSetGroupCover = passwords.size > 1,
                                                    isInExpandedGroup = isExpanded && passwords.size > 1,
                                                    isSingleCard = false
                                                )
                                            } else {
                                                // 多个密码使用 MultiPasswordEntryCard
                                                MultiPasswordEntryCard(
                                                    passwords = passwordGroup,
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
                                                        { onOpenMultiPasswordDialog(passwordGroup) }
                                                    } else null,
                                                    onLongClick = {},
                                                    onToggleFavorite = { password -> onToggleFavorite(password) },
                                                    onToggleGroupCover = if (passwords.size > 1) {
                                                        { password -> onToggleGroupCover(password) }
                                                    } else null,
                                                    isSelectionMode = isSelectionMode,
                                                    selectedPasswords = selectedPasswords,
                                                    canSetGroupCover = passwords.size > 1,
                                                    hasGroupCover = hasGroupCover,
                                                    isInExpandedGroup = isExpanded && passwords.size > 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 单个或不展开时,也需要按信息分组
                    val groupedByInfo = passwords.groupBy { getPasswordInfoKey(it) }
                    
                    groupedByInfo.values.forEach { passwordGroup ->
                        takagi.ru.monica.ui.gestures.SwipeActions(
                            onSwipeLeft = { onSwipeLeft(passwordGroup.first()) },
                            onSwipeRight = { onSwipeRight(passwordGroup.first()) },
                            enabled = true
                        ) {
                            // 如果只有一个密码,使用原来的 PasswordEntryCard
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
                                    onLongClick = {},
                                    onToggleFavorite = { onToggleFavorite(password) },
                                    onToggleGroupCover = null,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedPasswords.contains(password.id),
                                    canSetGroupCover = false,
                                    isInExpandedGroup = false,
                                    isSingleCard = true
                                )
                            } else {
                                // 多个密码使用 MultiPasswordEntryCard
                                MultiPasswordEntryCard(
                                    passwords = passwordGroup,
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
                                        { onOpenMultiPasswordDialog(passwordGroup) }
                                    } else null,
                                    onLongClick = {},
                                    onToggleFavorite = { password -> onToggleFavorite(password) },
                                    onToggleGroupCover = null,
                                    isSelectionMode = isSelectionMode,
                                    selectedPasswords = selectedPasswords,
                                    canSetGroupCover = false,
                                    hasGroupCover = false,
                                    isInExpandedGroup = false
                                )
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
    isInExpandedGroup: Boolean = false
) {
    // 使用第一个条目的共同信息
    val firstEntry = passwords.first()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isSelectionMode && onCardClick != null) {
                    Modifier.clickable { onCardClick() }
                } else {
                    Modifier
                }
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
            if (firstEntry.website.isNotBlank()) {
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
            if (firstEntry.username.isNotBlank()) {
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
            if (additionalInfo.isNotEmpty()) {
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    isSingleCard: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                .clickable { onClick() } // 将 clickable 移到 Row 上
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
                if (entry.website.isNotBlank()) {
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
                if (entry.username.isNotBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            if (isSingleCard) 8.dp else 6.dp
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
                if (additionalInfo.isNotEmpty()) {
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

sealed class BottomNavItem(
    val contentTab: BottomNavContentTab?,
    val icon: ImageVector
) {
    val key: String = contentTab?.name ?: SETTINGS_TAB_KEY

    object Passwords : BottomNavItem(BottomNavContentTab.PASSWORDS, Icons.Default.Lock)
    object Authenticator : BottomNavItem(BottomNavContentTab.AUTHENTICATOR, Icons.Default.Security)
    object Documents : BottomNavItem(BottomNavContentTab.DOCUMENTS, Icons.Default.Description)
    object BankCards : BottomNavItem(BottomNavContentTab.BANK_CARDS, Icons.Default.CreditCard)
    object Generator : BottomNavItem(BottomNavContentTab.GENERATOR, Icons.Default.AutoAwesome)  // 添加生成器导航项
    object Settings : BottomNavItem(null, Icons.Default.Settings)
}

private fun BottomNavContentTab.toBottomNavItem(): BottomNavItem = when (this) {
    BottomNavContentTab.PASSWORDS -> BottomNavItem.Passwords
    BottomNavContentTab.AUTHENTICATOR -> BottomNavItem.Authenticator
    BottomNavContentTab.DOCUMENTS -> BottomNavItem.Documents
    BottomNavContentTab.BANK_CARDS -> BottomNavItem.BankCards
    BottomNavContentTab.GENERATOR -> BottomNavItem.Generator  // 添加生成器映射
}

private fun BottomNavItem.fullLabelRes(): Int = when (this) {
    BottomNavItem.Passwords -> R.string.nav_passwords
    BottomNavItem.Authenticator -> R.string.nav_authenticator
    BottomNavItem.Documents -> R.string.nav_documents
    BottomNavItem.BankCards -> R.string.nav_bank_cards
    BottomNavItem.Generator -> R.string.nav_generator  // 添加生成器标签资源
    BottomNavItem.Settings -> R.string.nav_settings
}

private fun BottomNavItem.shortLabelRes(): Int = when (this) {
    BottomNavItem.Passwords -> R.string.nav_passwords_short
    BottomNavItem.Authenticator -> R.string.nav_authenticator_short
    BottomNavItem.Documents -> R.string.nav_documents_short
    BottomNavItem.BankCards -> R.string.nav_bank_cards_short
    BottomNavItem.Generator -> R.string.nav_generator_short  // 添加生成器短标签资源
    BottomNavItem.Settings -> R.string.nav_settings_short
}

private fun indexToDefaultTabKey(index: Int): String = when (index) {
    0 -> BottomNavContentTab.PASSWORDS.name
    1 -> BottomNavContentTab.AUTHENTICATOR.name
    2 -> BottomNavContentTab.DOCUMENTS.name
    3 -> BottomNavContentTab.BANK_CARDS.name
    4 -> BottomNavContentTab.GENERATOR.name
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
    return "${entry.title}|${entry.website}|${entry.username}|${entry.notes}"
}
