package takagi.ru.monica.wear.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.wear.R
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextAlign
import takagi.ru.monica.wear.ui.components.TotpCard
import takagi.ru.monica.wear.ui.components.SearchOverlay
import takagi.ru.monica.wear.viewmodel.TotpViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * TOTP分页浏览屏幕
 * 使用 HorizontalPager 实现分页，每页显示一个验证器
 * 支持手势：
 * - 左右滑动：切换验证器
 * - 下滑：打开搜索
 * - 上滑：打开设置
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TotpPagerScreen(
    viewModel: TotpViewModel,
    settingsViewModel: takagi.ru.monica.wear.viewmodel.SettingsViewModel,
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allItems by viewModel.allTotpItems.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isWebDavConfigured by viewModel.isWebDavConfigured.collectAsState()
    
    // 搜索状态
    var showSearch by remember { mutableStateOf(false) }
    
    // 启动TOTP更新
    LaunchedEffect(Unit) {
        viewModel.startTotpUpdates()
        viewModel.checkWebDavConfig()
    }
    
    // 停止TOTP更新（当组件销毁时）
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopTotpUpdates()
        }
    }
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        when {
            isLoading -> {
                // 加载中
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            allItems.isEmpty() && !isWebDavConfigured -> {
                // 未绑定 WebDAV 且无数据的空状态（支持上滑打开搜索/添加，下滑打开设置）
                var dragOffset by remember { mutableStateOf(0f) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    when {
                                        dragOffset < -50f -> showSearch = true  // 上滑打开搜索
                                        dragOffset > 50f -> onShowSettings()    // 下滑打开设置
                                    }
                                    dragOffset = 0f
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    dragOffset += dragAmount
                                }
                            )
                        }
                ) {
                    WebDavEmptyState(
                        onGoToSettings = onShowSettings,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            allItems.isEmpty() -> {
                // 已配置 WebDAV 但暂无数据的空状态（支持上滑打开搜索/添加，下滑打开设置）
                var dragOffset by remember { mutableStateOf(0f) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    when {
                                        dragOffset < -50f -> showSearch = true  // 上滑打开搜索
                                        dragOffset > 50f -> onShowSettings()    // 下滑打开设置
                                    }
                                    dragOffset = 0f
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    dragOffset += dragAmount
                                }
                            )
                        }
                ) {
                    EmptyState(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            else -> {
                // TOTP分页器（带搜索功能）
                TotpPagerWithSearch(
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel,
                    allItems = allItems,
                    searchResults = searchResults,
                    searchQuery = searchQuery,
                    showSearch = showSearch,
                    onCopyCode = { code -> viewModel.copyCode(code) },
                    onSwipeDown = { showSearch = true },
                    onSwipeUp = onShowSettings,
                    onSearchQueryChange = { query -> viewModel.searchTotpItems(query) },
                    onSearchDismiss = {
                        showSearch = false
                        viewModel.clearSearch()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        // 搜索覆盖层 - 在所有状态下都可用（包括空状态）
        AnimatedVisibility(
            visible = showSearch,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            SearchOverlay(
                searchQuery = searchQuery,
                searchResults = if (searchQuery.isBlank()) allItems else searchResults,
                onSearchQueryChange = { query -> viewModel.searchTotpItems(query) },
                onResultClick = { selectedItem ->
                    // 关闭搜索，如果有数据会自动显示
                    showSearch = false
                    viewModel.clearSearch()
                },
                onDismiss = {
                    showSearch = false
                    viewModel.clearSearch()
                },
                onAddTotp = { secret, issuer, accountName, onResult ->
                    settingsViewModel.addTotpItem(secret, issuer, accountName, onResult)
                },
                onEditTotp = { item, secret, issuer, accountName, onResult ->
                    viewModel.updateTotpItem(item, secret, issuer, accountName) { success, error ->
                        if (success) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.totp_update_success),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            onResult(true, null)
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                error ?: context.getString(R.string.totp_update_failed),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            onResult(false, error)
                        }
                    }
                },
                onDeleteTotp = { item ->
                    viewModel.deleteTotpItem(item) { success, error ->
                        if (success) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.totp_delete_success),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                error ?: context.getString(R.string.totp_delete_failed),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * TOTP分页器组件（带搜索功能）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TotpPagerWithSearch(
    viewModel: TotpViewModel,
    settingsViewModel: takagi.ru.monica.wear.viewmodel.SettingsViewModel,
    allItems: List<takagi.ru.monica.wear.viewmodel.TotpItemState>,
    searchResults: List<takagi.ru.monica.wear.viewmodel.TotpItemState>,
    searchQuery: String,
    showSearch: Boolean,
    onCopyCode: (String) -> Unit,
    onSwipeDown: () -> Unit,
    onSwipeUp: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { allItems.size })
    val coroutineScope = rememberCoroutineScope()
    var dragOffset by remember { mutableStateOf(0f) }
    
    Box(modifier = modifier) {
        // 分页器
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = allItems[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                // 检测手势方向
                                when {
                                    dragOffset < -100 -> onSwipeDown() // 上滑 (负值) -> 打开搜索
                                    dragOffset > 100 -> onSwipeUp()    // 下滑 (正值) -> 打开设置
                                }
                                dragOffset = 0f
                            },
                            onVerticalDrag = { _, dragAmount ->
                                dragOffset += dragAmount
                            }
                        )
                    }
            ) {
                TotpCard(
                    state = item,
                    onCopyCode = { onCopyCode(item.code) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        // 页面指示器（圆点）
        if (allItems.size > 1) {
            PageIndicator(
                pageCount = allItems.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
        
        // 搜索覆盖层 (带动画) - 用于有数据时的搜索和跳转
        AnimatedVisibility(
            visible = showSearch,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            SearchOverlay(
                searchQuery = searchQuery,
                searchResults = if (searchQuery.isBlank()) allItems else searchResults,
                onSearchQueryChange = onSearchQueryChange,
                onResultClick = { selectedItem ->
                    // 在完整列表中找到选中项的索引
                    val targetIndex = allItems.indexOfFirst { it.item.id == selectedItem.item.id }
                    if (targetIndex >= 0) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetIndex)
                        }
                    }
                    onSearchDismiss()
                },
                onDismiss = onSearchDismiss,
                onAddTotp = { secret, issuer, accountName, onResult ->
                    // 调用SettingsViewModel添加TOTP
                    settingsViewModel.addTotpItem(secret, issuer, accountName, onResult)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 页面指示器（圆点）
 */
@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            Box(
                modifier = Modifier
                    .size(if (isSelected) 8.dp else 6.dp)
                    .padding(2.dp)
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    drawCircle(
                        color = if (isSelected) {
                            androidx.compose.ui.graphics.Color.White
                        } else {
                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f)
                        }
                    )
                }
            }
        }
    }
}

/**
 * 空状态组件
 */
@Composable
private fun EmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.totp_empty_title),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.totp_empty_subtitle),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * 未绑定 WebDAV 空状态组件
 */
@Composable
private fun WebDavEmptyState(
    onGoToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.webdav_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.webdav_empty_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onGoToSettings) {
            Text(stringResource(R.string.webdav_empty_button))
        }
    }
}
