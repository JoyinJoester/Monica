package takagi.ru.monica.ui.v2

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

/**
 * 顶栏三态枚举
 */
enum class TopBarState {
    BUTTON,     // 收缩成单个按钮
    CAPSULE,    // 胶囊形态（显示筛选 chips）
    SEARCH      // 搜索框展开
}

/**
 * V2 可折叠顶栏组件
 * 
 * 支持三态切换：
 * - BUTTON: 最小化状态，只显示一个图标按钮
 * - CAPSULE: 胶囊状态，显示类型筛选 chips
 * - SEARCH: 搜索状态，展开搜索框
 * 
 * 交互方式：
 * - 点击按钮 → 展开到胶囊
 * - 左滑按钮 → 展开到胶囊
 * - 左滑胶囊 → 展开到搜索框
 * - 点击搜索图标 → 展开到搜索框
 * - 点击关闭/右滑 → 收缩到上一状态
 * 
 * @param state 当前顶栏状态
 * @param onStateChange 状态变化回调
 * @param searchQuery 搜索查询文本
 * @param onSearchQueryChange 搜索文本变化回调
 * @param selectedFilter 当前选中的筛选器
 * @param onFilterSelected 筛选器选择回调
 * @param modifier Modifier
 */
@Composable
fun V2CollapsibleTopBar(
    state: TopBarState,
    onStateChange: (TopBarState) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: V2VaultFilter,
    onFilterSelected: (V2VaultFilter) -> Unit,
    showFilterChips: Boolean = true,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    
    // 搜索框聚焦处理
    LaunchedEffect(state) {
        if (state == TopBarState.SEARCH) {
            kotlinx.coroutines.delay(100)
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (state == TopBarState.BUTTON) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (state) {
                TopBarState.BUTTON -> {
                    // 收缩按钮状态
                    CollapsedButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onStateChange(TopBarState.CAPSULE)
                        },
                        onSwipeLeft = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onStateChange(TopBarState.CAPSULE)
                        }
                    )
                }
                
                TopBarState.CAPSULE -> {
                    // 胶囊状态
                    CapsuleBar(
                        selectedFilter = selectedFilter,
                        onFilterSelected = onFilterSelected,
                        showFilterChips = showFilterChips,
                        onSearchClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onStateChange(TopBarState.SEARCH)
                        },
                        onCollapse = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onStateChange(TopBarState.BUTTON)
                        },
                        onSwipeLeft = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onStateChange(TopBarState.SEARCH)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                TopBarState.SEARCH -> {
                    // 搜索框状态
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onClose = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onSearchQueryChange("")
                            onStateChange(TopBarState.CAPSULE)
                        },
                        focusRequester = focusRequester,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 收缩按钮（BUTTON 状态）
 */
@Composable
private fun CollapsedButton(
    onClick: () -> Unit,
    onSwipeLeft: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 50.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragOffset = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount.x
                    },
                    onDragEnd = {
                        if (dragOffset < -swipeThreshold) {
                            onSwipeLeft()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.FilterList,
            contentDescription = "展开筛选器",
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * 胶囊栏（CAPSULE 状态）
 */
@Composable
private fun CapsuleBar(
    selectedFilter: V2VaultFilter,
    onFilterSelected: (V2VaultFilter) -> Unit,
    showFilterChips: Boolean,
    onSearchClick: () -> Unit,
    onCollapse: () -> Unit,
    onSwipeLeft: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 60.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragOffset = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount.x
                    },
                    onDragEnd = {
                        if (dragOffset < -swipeThreshold) {
                            // 左滑展开搜索
                            onSwipeLeft()
                        } else if (dragOffset > swipeThreshold) {
                            // 右滑收缩
                            onCollapse()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f }
                )
            }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 收缩按钮
        IconButton(
            onClick = onCollapse,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "收缩",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // 筛选 chips（可选）
        if (showFilterChips) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                V2VaultFilter.entries.take(4).forEach { filter ->
                    FilterChipMini(
                        selected = filter == selectedFilter,
                        onClick = { onFilterSelected(filter) },
                        icon = filter.icon,
                        label = stringResource(filter.labelRes),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        
        // 搜索按钮
        IconButton(
            onClick = onSearchClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "搜索",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 迷你筛选 Chip
 */
@Composable
private fun FilterChipMini(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        animationSpec = tween(150),
        label = "chipBg"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (selected) 
            MaterialTheme.colorScheme.onPrimaryContainer 
        else 
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(150),
        label = "chipContent"
    )
    
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 搜索栏（SEARCH 状态）
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 搜索图标
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
        )
        
        // 搜索输入框
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .focusRequester(focusRequester),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize
            ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.v2_search_placeholder),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    innerTextField()
                }
            }
        )
        
        // 清除/关闭按钮
        IconButton(
            onClick = {
                if (query.isNotEmpty()) {
                    onQueryChange("")
                } else {
                    onClose()
                }
            },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (query.isNotEmpty()) Icons.Default.Clear else Icons.Default.Close,
                contentDescription = if (query.isNotEmpty()) "清除" else "关闭",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * V2 顶栏容器（带标题和操作按钮）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2TopBarContainer(
    topBarState: TopBarState,
    onTopBarStateChange: (TopBarState) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: V2VaultFilter,
    onFilterSelected: (V2VaultFilter) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Monica"
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 顶部标题栏（仅在非搜索状态显示）
        AnimatedVisibility(
            visible = topBarState != TopBarState.SEARCH,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置"
                    )
                }
            }
        }
        
        // 可折叠顶栏
        V2CollapsibleTopBar(
            state = topBarState,
            onStateChange = onTopBarStateChange,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            selectedFilter = selectedFilter,
            onFilterSelected = onFilterSelected
        )
    }
}
