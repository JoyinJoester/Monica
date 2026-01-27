package takagi.ru.monica.ui.gestures

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * Phase 9: 下拉刷新组件
 * 
 * Material 3 风格的下拉刷新交互
 * 
 * ## 特性
 * - Material 3 设计规范
 * - 平滑的拉动效果
 * - 自动触发刷新
 * - 完成后自动收起
 * 
 * ## 使用示例
 * ```kotlin
 * PullToRefresh(
 *     isRefreshing = viewModel.isRefreshing,
 *     onRefresh = { viewModel.refresh() }
 * ) {
 *     LazyColumn {
 *         items(items) { item ->
 *             ItemCard(item)
 *         }
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()
    
    // 触发刷新
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            onRefresh()
        }
    }
    
    // 刷新完成后重置状态
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            pullToRefreshState.startRefresh()
        } else {
            pullToRefreshState.endRefresh()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
        content()
        
        if (enabled) {
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
