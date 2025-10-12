package takagi.ru.monica.ui.gestures

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Phase 9: 侧滑删除组件
 * 
 * 实现Material Design的侧滑删除交互
 * 
 * ## 特性
 * - 左滑显示删除选项
 * - 滑动阈值触发删除
 * - 平滑动画效果
 * - 可自定义背景颜色和图标
 * 
 * ## 使用示例
 * ```kotlin
 * SwipeToDismiss(
 *     onDismiss = { viewModel.deleteItem(item.id) }
 * ) {
 *     ListItem(item)
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismiss(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.errorContainer,
    contentColor: Color = MaterialTheme.colorScheme.onErrorContainer,
    icon: ImageVector = Icons.Default.Delete,
    iconDescription: String = "删除",
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )
    
    var isItemVisible by remember { mutableStateOf(true) }
    
    AnimatedVisibility(
        visible = isItemVisible,
        exit = shrinkVertically(
            animationSpec = tween(durationMillis = 300),
            shrinkTowards = Alignment.Top
        ) + fadeOut(animationSpec = tween(durationMillis = 300))
    ) {
        SwipeToDismissBox(
            state = dismissState,
            modifier = modifier,
            enableDismissFromStartToEnd = false,
            enableDismissFromEndToStart = enabled,
            backgroundContent = {
                // 背景（删除按钮）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = iconDescription,
                        tint = contentColor,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            },
            content = {
                // 前景（列表项内容）
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                        4.dp
                    } else {
                        0.dp
                    }
                ) {
                    content()
                }
            }
        )
    }
    
    // 当侧滑删除完成后，隐藏项目
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            kotlinx.coroutines.delay(300)  // 等待动画完成
            isItemVisible = false
        }
    }
}

/**
 * 侧滑操作组件（支持多个操作）
 * 
 * 比 SwipeToDismiss 更灵活，支持多个侧滑操作
 * 
 * ## 使用示例
 * ```kotlin
 * SwipeableAction(
 *     actions = listOf(
 *         SwipeAction(
 *             icon = Icons.Default.Delete,
 *             color = Color.Red,
 *             onClick = { viewModel.delete(item) }
 *         ),
 *         SwipeAction(
 *             icon = Icons.Default.Archive,
 *             color = Color.Blue,
 *             onClick = { viewModel.archive(item) }
 *         )
 *     )
 * ) {
 *     ListItem(item)
 * }
 * ```
 */
@Composable
fun SwipeableAction(
    @Suppress("UNUSED_PARAMETER") actions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // TODO: 实现多操作侧滑（Phase 9.4+）
    Box(modifier = modifier) {
        content()
    }
}

/**
 * 侧滑操作数据类
 * 
 * @param icon 操作图标
 * @param color 背景颜色
 * @param contentColor 内容颜色
 * @param description 操作描述（无障碍）
 * @param onClick 点击回调
 */
data class SwipeAction(
    val icon: ImageVector,
    val color: Color,
    val contentColor: Color = Color.White,
    val description: String = "",
    val onClick: () -> Unit
)
