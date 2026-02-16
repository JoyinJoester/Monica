package takagi.ru.monica.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.ui.components.M3IdentityVerifyDialog
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.ui.haptic.rememberHapticFeedback
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.utils.ClipboardUtils
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.CategoryFilter
import takagi.ru.monica.data.Category

import androidx.compose.ui.unit.Velocity
import takagi.ru.monica.util.VibrationPatterns
import androidx.activity.compose.BackHandler
import androidx.fragment.app.FragmentActivity

@OptIn(ExperimentalMaterial3Api::class)  
@Composable
fun PasswordListScreen(
    viewModel: PasswordViewModel,
    onAddPassword: () -> Unit,
    onEditPassword: (Long) -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    hideTopBar: Boolean = false
) {
    val context = LocalContext.current
    val clipboardUtils = remember { ClipboardUtils(context) }
    val passwordEntries by viewModel.passwordEntries.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val listState = rememberLazyListState()
    val haptic = rememberHapticFeedback()
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val settingsManager = remember { SettingsManager(context) }
    val appSettings by settingsManager.settingsFlow.collectAsState(
        initial = AppSettings(biometricEnabled = false)
    )
    val biometricHelper = remember { BiometricHelper(context) }
    val activity = context as? FragmentActivity
    
    var searchExpanded by remember { mutableStateOf(false) }
    // 使用带阻尼的偏移量
    var currentOffset by remember { mutableFloatStateOf(0f) }
    val triggerDistance = remember(density) { with(density) { 72.dp.toPx() } }
    
    // 震动服务
    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }
    
    // 记录是否已经震动过，避免重复震动
    var hasVibrated by remember { mutableStateOf(false) }

    // 记录本次交互是否发生过内容滚动（用于 Stop-at-Top 逻辑）
    // 严格模式：只有手势开始时列表就在顶部，才允许触发下拉
    var canTriggerPullToSearch by remember { mutableStateOf(false) }
    
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 如果正在向上滑动(available.y < 0)且有偏移量，先消耗偏移量
                if (currentOffset > 0 && available.y < 0) {
                    val newOffset = (currentOffset + available.y).coerceAtLeast(0f)
                    val consumed = currentOffset - newOffset
                    currentOffset = newOffset
                    return Offset(0f, -consumed)
                }
                return Offset.Zero
            }
            
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // android.util.Log.d("PullToSearch", "onPostScroll: available=$available, source=$source, currentOffset=$currentOffset, searchExpanded=$searchExpanded")
                
                // Allow both UserInput and potentially other sources if needed, but usually UserInput is correct for drag.
                // We will relax the check slightly or just debug.
                // 只有在没有滚动过内容的情况下（即一开始就在顶部），才允许触发下拉搜索
                if (!searchExpanded && available.y > 0 && canTriggerPullToSearch) {
                     // Check if we are really dragging (sometimes fling comes here too, but we only want drag usually)
                     // However, letting fling contribute to 'pull' might feel weird, but let's test.
                     // Strict check: source == NestedScrollSource.UserInput
                     
                    if (source == NestedScrollSource.UserInput) {
                        // 添加阻尼感 (0.5f 系数)
                        val delta = available.y * 0.5f
                        val newOffset = (currentOffset + delta)
                        val oldOffset = currentOffset
                        currentOffset = newOffset
                        
                        android.util.Log.d("PullToSearch", "Pulling: old=$oldOffset, new=$newOffset, trigger=$triggerDistance")

                        // 触发界限检测
                        if (oldOffset < triggerDistance && newOffset >= triggerDistance && !hasVibrated) {
                            hasVibrated = true
                            android.util.Log.d("PullToSearch", "Vibration Triggered!")
                            // 播放轻微的机械感震动 (Tick)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                 vibrator?.vibrate(android.os.VibrationEffect.createWaveform(takagi.ru.monica.util.VibrationPatterns.TICK, -1))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator?.vibrate(20) // Fallback
                            }
                        } else if (newOffset < triggerDistance) {
                            hasVibrated = false
                        }
                        
                        return available // 消费掉所有滚动
                    }
                }
                return Offset.Zero
            }
            
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentOffset >= triggerDistance) {
                    searchExpanded = true
                    hasVibrated = false // 重置状态
                }
                // 且无论如何松手后都要弹回去
                androidx.compose.animation.core.Animatable(currentOffset).animateTo(0f) {
                    currentOffset = value
                }
                return super.onPreFling(available)
            }
        }
    }
    
    // 监听搜索展开，处理键盘
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            // 给一点点延迟确保UI布局完成
            kotlinx.coroutines.delay(100)
            focusRequester.requestFocus()
            keyboardController?.show()
            currentOffset = 0f
        }
    }
    
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            searchExpanded = true
        }
    }
    
    var showLogoutDialog by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var batchPasswordInput by remember { mutableStateOf("") }
    var batchPasswordError by remember { mutableStateOf(false) }
    
    // 分组模式: "none" 不分组, "website" 按网站分组, "title" 按标题分组
    var groupMode by remember { mutableStateOf("none") }
    
    val scope = rememberCoroutineScope()
    val categories by viewModel.categories.collectAsState()
    val currentFilter by viewModel.categoryFilter.collectAsState()
    
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showEditCategoryDialog by remember { mutableStateOf<Category?>(null) }
    var categoryNameInput by remember { mutableStateOf("") }
    
    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedItems = setOf()
    }
    Scaffold(
        topBar = {
            if (!hideTopBar) {
                TopAppBar(
                    title = { 
                        if (selectionMode) {
                            Text(context.getString(R.string.selected_items, selectedItems.size))
                        } else {
                            Text(
                                text = when(currentFilter) {
                                    is CategoryFilter.All -> context.getString(R.string.filter_all)
                                    is CategoryFilter.Local -> context.getString(R.string.filter_monica)
                                    is CategoryFilter.LocalOnly -> context.getString(R.string.filter_local_only)
                                    is CategoryFilter.Starred -> context.getString(R.string.filter_starred)
                                    is CategoryFilter.Uncategorized -> context.getString(R.string.filter_uncategorized)
                                    is CategoryFilter.LocalStarred -> "${context.getString(R.string.filter_monica)} · ${context.getString(R.string.filter_starred)}"
                                    is CategoryFilter.LocalUncategorized -> "${context.getString(R.string.filter_monica)} · ${context.getString(R.string.filter_uncategorized)}"
                                    is CategoryFilter.Custom -> categories.find { it.id == (currentFilter as CategoryFilter.Custom).categoryId }?.name ?: context.getString(R.string.unknown_category)
                                    is CategoryFilter.KeePassDatabase -> "KeePass"
                                    is CategoryFilter.KeePassGroupFilter -> (currentFilter as CategoryFilter.KeePassGroupFilter).groupPath.substringAfterLast('/')
                                    is CategoryFilter.KeePassDatabaseStarred -> "KeePass · ${context.getString(R.string.filter_starred)}"
                                    is CategoryFilter.KeePassDatabaseUncategorized -> "KeePass · ${context.getString(R.string.filter_uncategorized)}"
                                    is CategoryFilter.BitwardenVault -> "Bitwarden"
                                    is CategoryFilter.BitwardenFolderFilter -> "Bitwarden"
                                    is CategoryFilter.BitwardenVaultStarred -> "Bitwarden · ${context.getString(R.string.filter_starred)}"
                                    is CategoryFilter.BitwardenVaultUncategorized -> "Bitwarden · ${context.getString(R.string.filter_uncategorized)}"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    navigationIcon = {
                        if (selectionMode) {
                            IconButton(onClick = {
                                selectionMode = false
                                selectedItems = setOf()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = context.getString(R.string.exit_selection_mode))
                            }
                        }
                    },
                    actions = {
                        if (selectionMode) {
                            // 全选/取消全选
                            IconButton(onClick = {
                                selectedItems = if (selectedItems.size == passwordEntries.size) {
                                    setOf()
                                } else {
                                    passwordEntries.map { it.id }.toSet()
                                }
                            }) {
                                Icon(
                                    if (selectedItems.size == passwordEntries.size) 
                                        Icons.Default.CheckCircle 
                                    else 
                                        Icons.Default.CheckCircleOutline,
                                    contentDescription = context.getString(R.string.select_all)
                                )
                            }
                            // 删除
                            IconButton(
                                onClick = { showDeleteConfirmDialog = true },
                                enabled = selectedItems.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = context.getString(R.string.delete),
                                    tint = if (selectedItems.isNotEmpty()) 
                                        MaterialTheme.colorScheme.error 
                                    else 
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                        } else {
                            // 分组模式切换按钮
                            IconButton(onClick = {
                                groupMode = when (groupMode) {
                                    "none" -> "website"
                                    "website" -> "title"
                                    else -> "none"
                                }
                            }) {
                                Icon(
                                    when (groupMode) {
                                        "website" -> Icons.Default.Language  // 地球图标表示按网站
                                        "title" -> Icons.Default.Title       // 标题图标表示按标题
                                        else -> Icons.Default.ViewList       // 列表图标表示不分组
                                    },
                                    contentDescription = when (groupMode) {
                                        "website" -> context.getString(R.string.group_by_website)
                                        "title" -> context.getString(R.string.group_by_title)
                                        else -> context.getString(R.string.group_by_none)
                                    }
                                )
                            }
                            IconButton(onClick = onSettings) {
                                Icon(Icons.Default.Settings, contentDescription = context.getString(R.string.settings_title))
                            }
                            IconButton(onClick = { showLogoutDialog = true }) {
                                Icon(Icons.Default.ExitToApp, contentDescription = context.getString(R.string.logout))
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!hideTopBar) {
                FloatingActionButton(
                    onClick = onAddPassword,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = context.getString(R.string.add_password))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedVisibility(
                visible = searchExpanded || searchQuery.isNotBlank(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        if (!searchExpanded) {
                            searchExpanded = true
                        }
                        viewModel.updateSearchQuery(it)
                    },
                    label = { Text(context.getString(R.string.search_passwords)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = context.getString(R.string.search))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { 
                                viewModel.updateSearchQuery("")
                                searchExpanded = false
                                keyboardController?.hide()
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = context.getString(R.string.clear_search))
                            }
                        } else if (searchExpanded) {
                            IconButton(onClick = {
                                searchExpanded = false
                                keyboardController?.hide()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = context.getString(R.string.cancel))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
                )
            }
            
            // Password List
            if (passwordEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(searchExpanded) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    if (!searchExpanded && dragAmount > 0f) {
                                        // 阻尼效果
                                        val newOffset = currentOffset + dragAmount * 0.5f
                                        val oldOffset = currentOffset
                                        currentOffset = newOffset
                                        
                                        // 震动触发
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
                                        searchExpanded = true
                                        hasVibrated = false
                                    }
                                    // 回弹动画
                                    scope.launch {
                                        androidx.compose.animation.core.Animatable(currentOffset).animateTo(0f) {
                                            currentOffset = value
                                        }
                                    }
                                },
                                onDragCancel = { 
                                    scope.launch {
                                        androidx.compose.animation.core.Animatable(currentOffset).animateTo(0f) {
                                            currentOffset = value
                                        }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(0, currentOffset.toInt()) }
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (searchQuery.isEmpty()) 
                                context.getString(R.string.no_passwords_saved) 
                            else 
                                context.getString(R.string.no_passwords_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { androidx.compose.ui.unit.IntOffset(0, currentOffset.toInt()) } // 应用下拉偏移
                        .nestedScroll(nestedScrollConnection)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                // 只有当列表确实在顶部时（FirstVisibleItemIndex == 0 && Offset == 0），
                                // 我们才认为这是一个潜在的下拉搜索手势
                                val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                                canTriggerPullToSearch = isAtTop
                            }
                        },
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (groupMode) {
                        "website" -> {
                            // 按网站分组
                            val groupedByWebsite = passwordEntries.groupBy {
                                it.website.ifEmpty { context.getString(R.string.filter_uncategorized) }
                            }.toList().sortedBy { it.first }
                            
                            groupedByWebsite.forEach { (website, entries) ->
                                // 分组标题
                                item {
                                    Text(
                                        text = "$website (${entries.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                                
                                // 该分组的所有密码
                                items(entries) { entry ->
                                    PasswordEntryCard(
                                        entry = entry,
                                        isSelected = selectedItems.contains(entry.id),
                                        selectionMode = selectionMode,
                                        onCopyPassword = { password ->
                                            if (!selectionMode) {
                                                clipboardUtils.copyToClipboard(password, "Password")
                                            }
                                        },
                                        onCopyUsername = { username ->
                                            if (!selectionMode) {
                                                clipboardUtils.copyToClipboard(username, "Username")
                                            }
                                        },
                                        onCopyWebsite = { website ->
                                            if (!selectionMode) {
                                                clipboardUtils.copyToClipboard(website, "Website")
                                            }
                                        },
                                        onCopyNotes = { notes ->
                                            if (!selectionMode) {
                                                clipboardUtils.copyToClipboard(notes, "Notes")
                                            }
                                        },
                                        onEdit = { 
                                            if (!selectionMode) {
                                                onEditPassword(entry.id)
                                            }
                                        },
                                        onDelete = { 
                                            if (!selectionMode) {
                                                viewModel.deletePasswordEntry(entry)
                                            }
                                        },
                                        onClick = {
                                            if (selectionMode) {
                                                selectedItems = if (selectedItems.contains(entry.id)) {
                                                    selectedItems - entry.id
                                                } else {
                                                    selectedItems + entry.id
                                                }
                                            } else {
                                                onEditPassword(entry.id)
                                            }
                                        },
                                        onLongPress = {
                                            android.util.Log.d("PasswordList", "Long press detected on entry: ${entry.title}")
                                            if (!selectionMode) {
                                                selectionMode = true
                                                selectedItems = setOf(entry.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        
                        "title" -> {
                            // 按标题分组(取首字母或首字)
                            val groupedByTitle = passwordEntries.groupBy { entry ->
                                val firstChar = entry.title.firstOrNull()?.toString()?.uppercase() ?: "#"
                                when {
                                    firstChar.matches(Regex("[A-Z]")) -> firstChar
                                    firstChar.matches(Regex("[0-9]")) -> "#"
                                    else -> firstChar
                                }
                            }.toList().sortedBy { it.first }
                            
                            groupedByTitle.forEach { (letter, entries) ->
                                // 分组标题
                                item {
                                    Text(
                                        text = "$letter (${entries.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                                
                                // 该分组的所有密码
                                items(entries) { entry ->
                                    PasswordEntryCard(
                                        entry = entry,
                                        isSelected = selectedItems.contains(entry.id),
                                        selectionMode = selectionMode,
                                        onCopyPassword = { password ->
                                            if (!selectionMode) {
                                                clipboardUtils.copyToClipboard(password, "Password")
                                            }
                                        },
                                        onCopyUsername = { username ->
                                            if (!selectionMode) {
                                                clipboardUtils.copyToClipboard(username, "Username")
                                            }
                                        },
                                        onCopyWebsite = { website ->
                                            if (!selectionMode) {
                                                clipboardUtils.copyToClipboard(website, "Website")
                                            }
                                        },
                                        onCopyNotes = { notes ->
                                            if (!selectionMode) {
                                                clipboardUtils.copyToClipboard(notes, "Notes")
                                            }
                                        },
                                        onEdit = { 
                                            if (!selectionMode) {
                                                onEditPassword(entry.id)
                                            }
                                        },
                                        onDelete = { 
                                            if (!selectionMode) {
                                                viewModel.deletePasswordEntry(entry)
                                            }
                                        },
                                        onClick = {
                                            if (selectionMode) {
                                                selectedItems = if (selectedItems.contains(entry.id)) {
                                                    selectedItems - entry.id
                                                } else {
                                                    selectedItems + entry.id
                                                }
                                            } else {
                                                onEditPassword(entry.id)
                                            }
                                        },
                                        onLongPress = {
                                            android.util.Log.d("PasswordList", "Long press detected on entry: ${entry.title}")
                                            if (!selectionMode) {
                                                selectionMode = true
                                                selectedItems = setOf(entry.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        
                        else -> {
                            // 不分组,直接显示列表
                            items(passwordEntries) { entry ->
                                PasswordEntryCard(
                                    entry = entry,
                                    isSelected = selectedItems.contains(entry.id),
                                    selectionMode = selectionMode,
                                    onCopyPassword = { password ->
                                        if (!selectionMode) {
                                            clipboardUtils.copyToClipboard(password, "Password")
                                        }
                                    },
                                    onCopyUsername = { username ->
                                        if (!selectionMode) {
                                            clipboardUtils.copyToClipboard(username, "Username")
                                        }
                                    },
                                    onCopyWebsite = { website ->
                                        if (!selectionMode) {
                                            clipboardUtils.copyToClipboard(website, "Website")
                                        }
                                    },
                                    onCopyNotes = { notes ->
                                        if (!selectionMode) {
                                            clipboardUtils.copyToClipboard(notes, "Notes")
                                        }
                                    },
                                    onEdit = { 
                                        if (!selectionMode) {
                                            onEditPassword(entry.id)
                                        }
                                    },
                                    onDelete = { 
                                        if (!selectionMode) {
                                            viewModel.deletePasswordEntry(entry)
                                        }
                                    },
                                    onClick = {
                                        if (selectionMode) {
                                            selectedItems = if (selectedItems.contains(entry.id)) {
                                                selectedItems - entry.id
                                            } else {
                                                selectedItems + entry.id
                                            }
                                        } else {
                                            onEditPassword(entry.id)
                                        }
                                    },
                                    onLongPress = {
                                        android.util.Log.d("PasswordList", "Long press detected on entry: ${entry.title}")
                                        if (!selectionMode) {
                                            selectionMode = true
                                            selectedItems = setOf(entry.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
    
    // Batch Delete Confirmation Dialog with Password
    if (showDeleteConfirmDialog) {
        val batchBiometricAction = if (
            activity != null &&
            appSettings.biometricEnabled &&
            biometricHelper.isBiometricAvailable()
        ) {
            {
                biometricHelper.authenticate(
                    activity = activity,
                    title = context.getString(R.string.verify_identity),
                    subtitle = context.getString(R.string.verify_to_delete),
                    onSuccess = {
                        val deleteCount = selectedItems.size
                        passwordEntries.filter { selectedItems.contains(it.id) }
                            .forEach { viewModel.deletePasswordEntry(it) }
                        showDeleteConfirmDialog = false
                        selectionMode = false
                        selectedItems = setOf()
                        batchPasswordInput = ""
                        batchPasswordError = false
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.deleted_items, deleteCount),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
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
            title = context.getString(R.string.verify_identity),
            message = context.getString(R.string.batch_delete_passwords_message, selectedItems.size),
            passwordValue = batchPasswordInput,
            onPasswordChange = {
                batchPasswordInput = it
                batchPasswordError = false
            },
            onDismiss = {
                showDeleteConfirmDialog = false
                batchPasswordInput = ""
                batchPasswordError = false
            },
            onConfirm = {
                val securityManager = takagi.ru.monica.security.SecurityManager(context)
                if (securityManager.verifyMasterPassword(batchPasswordInput)) {
                    val deleteCount = selectedItems.size
                    passwordEntries.filter { selectedItems.contains(it.id) }
                        .forEach { viewModel.deletePasswordEntry(it) }
                    showDeleteConfirmDialog = false
                    selectionMode = false
                    selectedItems = setOf()
                    batchPasswordInput = ""
                    batchPasswordError = false
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.deleted_items, deleteCount),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    batchPasswordError = true
                }
            },
            confirmText = context.getString(R.string.delete),
            destructiveConfirm = true,
            isPasswordError = batchPasswordError,
            passwordErrorText = context.getString(R.string.current_password_incorrect),
            onBiometricClick = batchBiometricAction,
            biometricHintText = if (batchBiometricAction == null) {
                context.getString(R.string.biometric_not_available)
            } else {
                null
            }
        )
    }
    
    // Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(context.getString(R.string.logout)) },
            text = { Text(context.getString(R.string.logout_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text(context.getString(R.string.logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text(context.getString(R.string.new_category)) },
            text = {
                OutlinedTextField(
                    value = categoryNameInput,
                    onValueChange = { categoryNameInput = it },
                    label = { Text(context.getString(R.string.category_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (categoryNameInput.isNotBlank()) {
                        viewModel.addCategory(categoryNameInput)
                        categoryNameInput = ""
                        showAddCategoryDialog = false
                    }
                }) {
                    Text(context.getString(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }

    if (showEditCategoryDialog != null) {
        AlertDialog(
            onDismissRequest = { showEditCategoryDialog = null },
            title = { Text(context.getString(R.string.edit_category)) },
            text = {
                OutlinedTextField(
                    value = categoryNameInput,
                    onValueChange = { categoryNameInput = it },
                    label = { Text(context.getString(R.string.category_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (categoryNameInput.isNotBlank()) {
                        viewModel.updateCategory(showEditCategoryDialog!!.copy(name = categoryNameInput))
                        categoryNameInput = ""
                        showEditCategoryDialog = null
                    }
                }) {
                    Text(context.getString(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditCategoryDialog = null }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 加载应用图标
 * @param context Context
 * @param packageName 应用包名
 * @return 应用的Drawable图标,如果应用未安装或加载失败则返回null
 */
@Composable
fun rememberAppIcon(context: Context, packageName: String?): Drawable? {
    return remember(packageName) {
        android.util.Log.d("PasswordListScreen", "rememberAppIcon: packageName = $packageName")
        if (packageName.isNullOrEmpty()) {
            android.util.Log.d("PasswordListScreen", "rememberAppIcon: packageName is null or empty")
            null
        } else {
            try {
                val icon = context.packageManager.getApplicationIcon(packageName)
                android.util.Log.d("PasswordListScreen", "rememberAppIcon: Successfully loaded icon for $packageName")
                icon
            } catch (e: PackageManager.NameNotFoundException) {
                // 应用未安装
                android.util.Log.w("PasswordListScreen", "rememberAppIcon: App not found: $packageName", e)
                null
            } catch (e: OutOfMemoryError) {
                // 内存不足
                android.util.Log.e("PasswordListScreen", "rememberAppIcon: OOM for $packageName", e)
                null
            } catch (e: Exception) {
                // 其他错误
                android.util.Log.e("PasswordListScreen", "rememberAppIcon: Error loading icon for $packageName", e)
                null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PasswordEntryCard(
    entry: PasswordEntry,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onCopyPassword: (String) -> Unit,
    onCopyUsername: (String) -> Unit,
    onCopyWebsite: (String) -> Unit,
    onCopyNotes: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit = {},
    onLongPress: () -> Unit = {},
    iconCardsEnabled: Boolean = false,
    passwordCardDisplayMode: takagi.ru.monica.data.PasswordCardDisplayMode = takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        color = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧:复选框(选择模式)+ 标题区域(可点击展开)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (selectionMode) {
                                onClick()
                            } else {
                                expanded = !expanded
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 选择模式下显示复选框
                    if (selectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    
                    // 应用图标或默认密钥图标
                    if (iconCardsEnabled) {
                        val simpleIcon = if (entry.customIconType == takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_SIMPLE) {
                            takagi.ru.monica.ui.icons.rememberSimpleIconBitmap(
                                slug = entry.customIconValue,
                                tintColor = MaterialTheme.colorScheme.primary,
                                enabled = true
                            )
                        } else {
                            null
                        }
                        val uploadedIcon = if (entry.customIconType == takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_UPLOADED) {
                            takagi.ru.monica.ui.icons.rememberUploadedPasswordIcon(entry.customIconValue)
                        } else {
                            null
                        }
                        val appIcon = rememberAppIcon(context, entry.appPackageName)
                        val favicon = if (entry.website.isNotBlank()) {
                            takagi.ru.monica.autofill.ui.rememberFavicon(url = entry.website, enabled = true)
                        } else {
                            null
                        }

                        if (simpleIcon != null) {
                            Image(
                                bitmap = simpleIcon,
                                contentDescription = "Simple Icon",
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(end = 12.dp)
                            )
                        } else if (uploadedIcon != null) {
                            Image(
                                bitmap = uploadedIcon,
                                contentDescription = "Uploaded Icon",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .padding(end = 12.dp)
                            )
                        } else if (favicon != null) {
                            Image(
                                bitmap = favicon,
                                contentDescription = "Website Icon",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .padding(end = 12.dp)
                            )
                        } else if (appIcon != null) {
                            Image(
                                painter = rememberDrawablePainter(drawable = appIcon),
                                contentDescription = "App Icon",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .padding(end = 12.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Password Icon",
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(end = 12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        // 如果禁用图标，直接显示默认密钥图标
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Password Icon",
                            modifier = Modifier
                                .size(40.dp)
                                .padding(end = 12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (passwordCardDisplayMode == takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL && entry.website.isNotEmpty()) {
                            Text(
                                text = entry.website,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (passwordCardDisplayMode != takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_ONLY && entry.username.isNotEmpty()) {
                            Text(
                                text = entry.username,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    // 展开/收起图标
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) context.getString(R.string.collapse) else context.getString(R.string.expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                // 右侧:菜单按钮(独立,不在可点击区域内)
                if (!selectionMode) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = context.getString(R.string.menu)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.edit)) },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.delete)) },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.multi_select)) },
                                onClick = {
                                    showMenu = false
                                    onLongPress()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.CheckBox, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            }
            
            if (expanded && !selectionMode) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Website with copy button
                if (passwordCardDisplayMode == takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL && entry.website.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = context.getString(R.string.website),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = entry.website,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(onClick = { onCopyWebsite(entry.website) }) {
                            Icon(Icons.Default.FileCopy, contentDescription = context.getString(R.string.copy))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Username
                if (passwordCardDisplayMode != takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_ONLY && entry.username.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = context.getString(R.string.username),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = entry.username,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(onClick = { onCopyUsername(entry.username) }) {
                            Icon(Icons.Default.FileCopy, contentDescription = context.getString(R.string.copy_username))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Password
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = context.getString(R.string.password),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = context.getString(R.string.password_hidden),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    IconButton(onClick = { onCopyPassword(entry.password) }) {
                        Icon(Icons.Default.FileCopy, contentDescription = context.getString(R.string.copy_password))
                    }
                }
                
                // Notes
                if (passwordCardDisplayMode == takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL && entry.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = context.getString(R.string.notes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = entry.notes,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(onClick = { onCopyNotes(entry.notes) }) {
                            Icon(Icons.Default.FileCopy, contentDescription = context.getString(R.string.copy))
                        }
                    }
                }
            }
        }
    }
    
    // Delete Confirmation Dialog with Password
    if (showDeleteDialog) {
        M3IdentityVerifyDialog(
            title = context.getString(R.string.delete_password_title),
            message = context.getString(R.string.delete_password_message, entry.title),
            passwordValue = passwordInput,
            onPasswordChange = {
                passwordInput = it
                passwordError = false
            },
            onDismiss = {
                showDeleteDialog = false
                passwordInput = ""
                passwordError = false
            },
            onConfirm = {
                val securityManager = takagi.ru.monica.security.SecurityManager(context)
                if (securityManager.verifyMasterPassword(passwordInput)) {
                    onDelete()
                    showDeleteDialog = false
                    passwordInput = ""
                    passwordError = false
                } else {
                    passwordError = true
                }
            },
            confirmText = context.getString(R.string.delete),
            destructiveConfirm = true,
            isPasswordError = passwordError,
            passwordErrorText = context.getString(R.string.current_password_incorrect),
            onBiometricClick = null,
            biometricHintText = context.getString(R.string.biometric_not_available)
        )
    }
}
