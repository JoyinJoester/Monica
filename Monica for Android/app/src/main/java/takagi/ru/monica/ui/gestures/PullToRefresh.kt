package takagi.ru.monica.ui.gestures

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
    if (!enabled) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
    } else {
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier.fillMaxSize(),
            content = {
                content()
            }
        )
    }
}
