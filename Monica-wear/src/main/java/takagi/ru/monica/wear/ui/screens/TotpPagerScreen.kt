package takagi.ru.monica.wear.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allItems by viewModel.allTotpItems.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    // 搜索状态
    var showSearch by remember { mutableStateOf(false) }
    
    // 启动TOTP更新
    LaunchedEffect(Unit) {
        viewModel.startTotpUpdates()
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
            allItems.isEmpty() -> {
                // 空状态（支持下滑打开设置）
                var dragOffset by remember { mutableStateOf(0f) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    // 下滑打开设置
                                    if (dragOffset > 50f) {
                                        onShowSettings()
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
    }
}

/**
 * TOTP分页器组件（带搜索功能）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TotpPagerWithSearch(
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
                                    dragOffset < -100 -> onSwipeDown() // 下滑
                                    dragOffset > 100 -> onSwipeUp()    // 上滑
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
        
        // 搜索覆盖层
        if (showSearch) {
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
            text = "暂无验证器",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "请在设置中同步数据",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
