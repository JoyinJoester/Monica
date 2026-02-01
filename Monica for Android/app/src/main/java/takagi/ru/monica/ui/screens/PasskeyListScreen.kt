package takagi.ru.monica.ui.screens

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.viewmodel.PasskeyViewModel

/**
 * Passkey 列表屏幕
 * 
 * 与密码列表页面保持完全一致的设计风格：
 * - ExpressiveTopBar 顶栏（大标题 + 胶囊形搜索按钮）
 * - 下拉触发搜索
 * - Android 14+ 完整功能支持
 * - Android 14 以下版本仅查看模式
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PasskeyListScreen(
    viewModel: PasskeyViewModel,
    onPasskeyClick: (PasskeyEntry) -> Unit = {},
    hideTopBar: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // 收集状态
    val passkeys by viewModel.filteredPasskeys.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val groupedPasskeys by viewModel.groupedPasskeys.collectAsState()
    
    // 是否完全支持 Passkey
    val isFullySupported = viewModel.isPasskeyFullySupported
    
    // 列表状态
    val listState = rememberLazyListState()
    
    // 搜索栏展开状态
    var isSearchExpanded by remember { mutableStateOf(false) }
    
    // 下拉搜索相关
    var currentOffset by remember { mutableFloatStateOf(0f) }
    val triggerDistance = remember(density) { with(density) { 72.dp.toPx() } }
    var hasVibrated by remember { mutableStateOf(false) }
    var canTriggerPullToSearch by remember { mutableStateOf(false) }
    
    // 震动服务
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }
    
    // 嵌套滚动连接（下拉触发搜索）
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (currentOffset > 0 && available.y < 0) {
                    val newOffset = (currentOffset + available.y).coerceAtLeast(0f)
                    val consumed = currentOffset - newOffset
                    currentOffset = newOffset
                    return Offset(0f, -consumed)
                }
                return Offset.Zero
            }
            
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!isSearchExpanded && available.y > 0 && canTriggerPullToSearch) {
                    if (source == NestedScrollSource.UserInput) {
                        val delta = available.y * 0.5f
                        val newOffset = currentOffset + delta
                        val oldOffset = currentOffset
                        currentOffset = newOffset
                        
                        if (oldOffset < triggerDistance && newOffset >= triggerDistance && !hasVibrated) {
                            hasVibrated = true
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                }
                return Offset.Zero
            }
            
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentOffset >= triggerDistance) {
                    isSearchExpanded = true
                    hasVibrated = false
                }
                androidx.compose.animation.core.Animatable(currentOffset).animateTo(0f) {
                    currentOffset = value
                }
                return super.onPreFling(available)
            }
        }
    }
    
    // 监听搜索查询变化
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            isSearchExpanded = true
        }
    }
    
    // 显示版本警告
    var showVersionWarning by remember { mutableStateOf(!isFullySupported) }
    
    Column(modifier = modifier.fillMaxSize()) {
        // ExpressiveTopBar（与密码列表完全一致）
        if (!hideTopBar) {
            ExpressiveTopBar(
                title = stringResource(R.string.passkey_title),
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                isSearchExpanded = isSearchExpanded,
                onSearchExpandedChange = { isSearchExpanded = it },
                searchHint = stringResource(R.string.passkey_search_placeholder),
                actions = {
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
        }
        
        // 版本兼容性警告
        AnimatedVisibility(
            visible = showVersionWarning && !isFullySupported,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            VersionWarningBanner(
                androidVersion = viewModel.androidVersion,
                onDismiss = { showVersionWarning = false }
            )
        }
        
        // 主内容
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (passkeys.isEmpty()) {
            // 空状态（与密码列表一致）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isSearchExpanded) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                if (!isSearchExpanded && dragAmount > 0f) {
                                    val newOffset = currentOffset + dragAmount * 0.5f
                                    val oldOffset = currentOffset
                                    currentOffset = newOffset
                                    
                                    if (oldOffset < triggerDistance && newOffset >= triggerDistance && !hasVibrated) {
                                        hasVibrated = true
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                    modifier = Modifier.offset { IntOffset(0, currentOffset.toInt()) }
                ) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (searchQuery.isEmpty())
                            stringResource(R.string.passkey_empty_title)
                        else
                            stringResource(R.string.passkey_no_search_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    if (searchQuery.isEmpty() && isFullySupported) {
                        Text(
                            text = stringResource(R.string.passkey_empty_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp)
                        )
                    }
                }
            }
        } else {
            // Passkey 列表（与密码列表一致的交互）
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, currentOffset.toInt()) }
                    .nestedScroll(nestedScrollConnection)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                            canTriggerPullToSearch = isAtTop
                        }
                    },
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 100.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedPasskeys.forEach { (rpId, passkeyList) ->
                    // 分组标题
                    stickyHeader(key = "header_$rpId") {
                        PasskeyGroupHeader(
                            rpId = rpId,
                            rpName = passkeyList.firstOrNull()?.rpName ?: rpId,
                            count = passkeyList.size
                        )
                    }
                    
                    // 分组内的 Passkey 条目
                    items(
                        items = passkeyList,
                        key = { it.credentialId }
                    ) { passkey ->
                        PasskeyListItem(
                            passkey = passkey,
                            onClick = { onPasskeyClick(passkey) },
                            onDelete = { viewModel.deletePasskey(passkey) },
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            }
        }
    }
}

/**
 * 版本警告横幅
 */
@Composable
private fun VersionWarningBanner(
    androidVersion: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.passkey_version_warning_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.passkey_version_warning_message, androidVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Passkey 分组标题
 */
@Composable
private fun PasskeyGroupHeader(
    rpId: String,
    rpName: String,
    count: Int
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rpName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rpName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = rpId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = count.toString(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Passkey 列表项（与密码列表风格完全一致 - M3 Expressive 设计）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasskeyListItem(
    passkey: PasskeyEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { 
                Text(
                    stringResource(R.string.passkey_delete_title),
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Text(
                    stringResource(R.string.passkey_delete_message, passkey.rpName, passkey.userDisplayName.ifBlank { passkey.userName })
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：头像 + 标题区域（可点击展开）
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Passkey 图标
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = passkey.userDisplayName.ifBlank { passkey.userName },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = passkey.rpName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // 展开/收起图标
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                // 右侧：菜单按钮
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "菜单"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.passkey_view_details)) },
                            onClick = {
                                showMenu = false
                                expanded = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Info, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
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
                    }
                }
            }
            
            // 展开内容
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    
                    // 域名
                    DetailRow(
                        label = stringResource(R.string.passkey_rp_id),
                        value = passkey.rpId,
                        icon = Icons.Default.Language
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 用户名
                    DetailRow(
                        label = stringResource(R.string.passkey_username),
                        value = passkey.userName,
                        icon = Icons.Default.Person
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 创建时间
                    DetailRow(
                        label = stringResource(R.string.passkey_created_at),
                        value = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(passkey.createdAt)),
                        icon = Icons.Default.Schedule
                    )
                    
                    if (passkey.lastUsedAt > passkey.createdAt) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 最后使用时间
                        DetailRow(
                            label = stringResource(R.string.passkey_last_used),
                            value = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(passkey.lastUsedAt)),
                            icon = Icons.Default.History
                        )
                    }
                    
                    if (passkey.useCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 使用次数
                        DetailRow(
                            label = stringResource(R.string.passkey_use_count),
                            value = passkey.useCount.toString(),
                            icon = Icons.Default.Numbers
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 删除按钮
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.passkey_delete_button))
                    }
                }
            }
        }
    }
}

/**
 * 详情行组件
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
