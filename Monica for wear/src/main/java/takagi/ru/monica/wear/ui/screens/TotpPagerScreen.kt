package takagi.ru.monica.wear.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import takagi.ru.monica.wear.R
import takagi.ru.monica.wear.ui.components.SearchOverlay
import takagi.ru.monica.wear.ui.components.TotpCard
import takagi.ru.monica.wear.viewmodel.SettingsViewModel
import takagi.ru.monica.wear.viewmodel.TotpItemState
import takagi.ru.monica.wear.viewmodel.TotpViewModel
import kotlin.math.abs
import kotlin.math.absoluteValue

/**
 * TOTP分页浏览屏幕 - Wear OS M3E 设计
 * 自动适配各种屏幕尺寸
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TotpPagerScreen(
    viewModel: TotpViewModel,
    settingsViewModel: SettingsViewModel,
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allItems by viewModel.allTotpItems.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isWebDavConfigured by viewModel.isWebDavConfigured.collectAsState()
    
    var showSearch by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { allItems.size })
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        viewModel.startTotpUpdates()
        viewModel.checkWebDavConfig()
    }
    
    DisposableEffect(Unit) {
        onDispose { viewModel.stopTotpUpdates() }
    }
    
    // 入场动画
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(500)) + 
                scaleIn(initialScale = 0.9f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when {
                isLoading -> {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val indicatorSize = maxWidth * 0.2f
                        CircularProgressIndicator(
                            modifier = Modifier.size(indicatorSize)
                        )
                    }
                }
                allItems.isEmpty() && !isWebDavConfigured -> {
                    DraggableEmptyState(
                        content = { WebDavEmptyState(onGoToSettings = onShowSettings) },
                        onSwipeUp = { showSearch = true },
                        onSwipeDown = onShowSettings
                    )
                }
                allItems.isEmpty() -> {
                    DraggableEmptyState(
                        content = { EmptyState() },
                        onSwipeUp = { showSearch = true },
                        onSwipeDown = onShowSettings
                    )
                }
                else -> {
                    TotpPagerContent(
                        viewModel = viewModel,
                        settingsViewModel = settingsViewModel,
                        pagerState = pagerState,
                        allItems = allItems,
                        searchResults = searchResults,
                        searchQuery = searchQuery,
                        showSearch = showSearch,
                        onSwipeUp = { showSearch = true },
                        onSwipeDown = onShowSettings,
                        onSearchDismiss = {
                            showSearch = false
                            viewModel.clearSearch()
                        }
                    )
                }
            }
            
            // 搜索覆盖层
            AnimatedVisibility(
                visible = showSearch,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                SearchOverlay(
                    searchQuery = searchQuery,
                    searchResults = if (searchQuery.isBlank()) allItems else searchResults,
                    onSearchQueryChange = { viewModel.searchTotpItems(it) },
                    onNavigateToItem = { item ->
                        val targetIndex = allItems.indexOfFirst { it.item.id == item.item.id }
                        if (targetIndex >= 0) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(targetIndex)
                            }
                        }
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
                                Toast.makeText(context, context.getString(R.string.totp_update_success), Toast.LENGTH_SHORT).show()
                                onResult(true, null)
                            } else {
                                Toast.makeText(context, error ?: context.getString(R.string.totp_update_failed), Toast.LENGTH_SHORT).show()
                                onResult(false, error)
                            }
                        }
                    },
                    onDeleteTotp = { item ->
                        viewModel.deleteTotpItem(item) { success, error ->
                            if (success) {
                                Toast.makeText(context, context.getString(R.string.totp_delete_success), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, error ?: context.getString(R.string.totp_delete_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * 可拖拽的空状态容器
 */
@Composable
private fun DraggableEmptyState(
    content: @Composable () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit
) {
    var dragOffset by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        when {
                            dragOffset < -50f -> onSwipeUp()
                            dragOffset > 50f -> onSwipeDown()
                        }
                        dragOffset = 0f
                    },
                    onVerticalDrag = { _, dragAmount -> dragOffset += dragAmount }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * TOTP 分页器内容 - 带页面切换动画
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TotpPagerContent(
    viewModel: TotpViewModel,
    settingsViewModel: SettingsViewModel,
    pagerState: PagerState,
    allItems: List<TotpItemState>,
    searchResults: List<TotpItemState>,
    searchQuery: String,
    showSearch: Boolean,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onSearchDismiss: () -> Unit
) {
    var dragOffset by remember { mutableStateOf(0f) }
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 1f - pageOffset.coerceIn(0f, 1f) * 0.3f
                        scaleX = 1f - pageOffset.coerceIn(0f, 1f) * 0.1f
                        scaleY = 1f - pageOffset.coerceIn(0f, 1f) * 0.1f
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                when {
                                    dragOffset < -80 -> onSwipeUp()
                                    dragOffset > 80 -> onSwipeDown()
                                }
                                dragOffset = 0f
                            },
                            onVerticalDrag = { _, dragAmount -> dragOffset += dragAmount }
                        )
                    }
            ) {
                TotpCard(
                    state = allItems[page],
                    onCopyCode = { viewModel.copyCode(allItems[page].code) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        // 页面指示器 - 自适应尺寸
        if (allItems.size > 1) {
            val dotSize = screenWidth * 0.025f
            val dotSpacing = screenWidth * 0.015f
            
            AdaptivePageIndicator(
                pageCount = allItems.size,
                currentPage = pagerState.currentPage,
                dotSize = dotSize,
                dotSpacing = dotSpacing,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = screenWidth * 0.06f)
            )
        }
    }
}

/**
 * 自适应页面指示器
 */
@Composable
private fun AdaptivePageIndicator(
    pageCount: Int,
    currentPage: Int,
    dotSize: androidx.compose.ui.unit.Dp,
    dotSpacing: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.3f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "indicatorScale"
            )
            val alpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.4f,
                animationSpec = tween(200),
                label = "indicatorAlpha"
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

/**
 * 空状态组件 - 自适应尺寸
 */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val screenWidth = maxWidth
        val titleSize = (screenWidth.value * 0.07f).sp
        val subtitleSize = (screenWidth.value * 0.055f).sp
        val padding = screenWidth * 0.08f
        val spacing = screenWidth * 0.03f
        
        Column(
            modifier = Modifier.padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.totp_empty_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = titleSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(spacing))
            Text(
                text = stringResource(R.string.totp_empty_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = subtitleSize,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 未绑定 WebDAV 空状态组件 - 自适应尺寸
 */
@Composable
private fun WebDavEmptyState(
    onGoToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        
        // 自适应尺寸计算
        val iconSize = screenWidth * 0.2f
        val titleSize = (screenWidth.value * 0.08f).sp
        val subtitleSize = (screenWidth.value * 0.055f).sp
        val padding = screenWidth * 0.1f
        val spacing = screenHeight * 0.03f
        val buttonHeight = screenHeight * 0.12f
        
        Column(
            modifier = Modifier.padding(horizontal = padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(spacing))
            Text(
                text = stringResource(R.string.webdav_empty_title),
                fontSize = titleSize,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(spacing * 0.5f))
            Text(
                text = stringResource(R.string.webdav_empty_subtitle),
                fontSize = subtitleSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(spacing))
            Button(
                onClick = onGoToSettings,
                modifier = Modifier.height(buttonHeight)
            ) {
                Text(
                    text = stringResource(R.string.webdav_empty_button),
                    fontSize = subtitleSize
                )
            }
        }
    }
}
