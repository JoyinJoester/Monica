package takagi.ru.monica.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.abs

/**
 * 可滑动的 FAB 组件 -> 容器变换 FAB (Container Transform)
 * 
 * 实现方案：Material Design Container Transform
 * - 点击 FAB，平滑过渡展开为全屏页面
 * - 动画包含：尺寸、圆角、位置、颜色
 * - 移除之前的拖拽逻辑，改为纯点击触发
 */
@Composable
fun SwipeableAddFab(
    modifier: Modifier = Modifier,
    fabContent: @Composable (expand: () -> Unit) -> Unit,
    expandedContent: @Composable (onCollapse: () -> Unit) -> Unit,
    fabBottomOffset: androidx.compose.ui.unit.Dp = 0.dp, // 新增参数：FAB 距离底部的额外偏移
    onFabClickOverride: (() -> Unit)? = null,
    onExpandStateChanged: (Boolean) -> Unit = {}
) {
    // 状态定义
    var isExpanded by remember { mutableStateOf(false) }
    
    // 动画状态：0f = 收起 (FAB), 1f = 展开 (全屏)
    // 使用 Animatable 以便精细控制手势过程
    val expandProgress = remember { Animatable(0f) }
    
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 展开动作
    val expandAction: () -> Unit = {
        scope.launch {
            // 使用 FastOutSlowInEasing (标准 Material 强调运动)
            // 持续时间延长到 500ms，让变形过程清晰可见
            expandProgress.animateTo(
                targetValue = 1f, 
                animationSpec = tween(
                    durationMillis = 500,
                    easing = FastOutSlowInEasing
                )
            )
            isExpanded = true
            onExpandStateChanged(true)
        }
    }
    
    // FAB 的初始位置（用于计算展开动画的起始点）
    // 注意：这里我们假设 FAB 位于右下角，通过 Padding 控制
    // 实际场景中，我们通过 BoxWithConstraints 获取屏幕尺寸来计算动态偏移

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().zIndex(if (isExpanded || expandProgress.value > 0f) 100f else 0f)
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val screenWidthPx = with(density) { screenWidth.toPx() }
        val screenHeightPx = with(density) { screenHeight.toPx() }
        
        // FAB 参数
        val fabSize = 56.dp
        val fabMargin = 16.dp
        val fabSizePx = with(density) { fabSize.toPx() }
        val fabMarginPx = with(density) { fabMargin.toPx() }
        val fabBottomOffsetPx = with(density) { fabBottomOffset.toPx() }
        
        // 计算 FAB 中心点相对于屏幕右下角的偏移
        // 初始状态下，FAB 在右下角
        // 展开状态下，Box 占满全屏
        
        // 我们通过 modifier.offset 来控制位置
        // 收起时：offset 指向右下角
        // 展开时：offset 为 (0,0)
        
        // 动画逻辑：
        // 1. 大小：从 fabSize -> fillMaxSize
        // 2. 圆角：从 50% (fabSize/2) -> 0dp (或小圆角)
        // 3. 位置：从 右下角 -> (0,0)
        
        // 当前进度对应的尺寸
        // 使用 lerp 插值
        val currentWidth = androidx.compose.ui.unit.lerp(fabSize, screenWidth, expandProgress.value)
        val currentHeight = androidx.compose.ui.unit.lerp(fabSize, screenHeight, expandProgress.value)
        
        // 当前进度对应的圆角
        // FAB 是 16.dp (M3 Standard Rounded Rect)
        // 全屏时是 0dp
        val currentCornerRadius = androidx.compose.ui.unit.lerp(16.dp, 0.dp, expandProgress.value)
        
        // 当前进度对应的位置偏移 (相对于左上角)
        // 目标位置 (展开): (0, 0)
        // 起始位置 (收起): (screenWidth - fabSize - fabMargin, screenHeight - fabSize - fabMargin)
        // 背景遮罩 (Scrim)
        // 当开始展开时显示，颜色变深
        if (expandProgress.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f * expandProgress.value))
                    .pointerInput(Unit) {
                        // 拦截点击和拖拽，防止透传到下面
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                    .clickable(enabled = isExpanded) {
                        scope.launch {
                            expandProgress.animateTo(0f)
                            isExpanded = false
                            onExpandStateChanged(false)
                        }
                    }
            )
        }

        val startX = screenWidthPx - fabSizePx - fabMarginPx
        val startY = screenHeightPx - fabSizePx - fabMarginPx - fabBottomOffsetPx // 减去额外偏移
        
        // 插值计算当前 Offset
        val currentX = startX * (1f - expandProgress.value)
        val currentY = startY * (1f - expandProgress.value)
        
        // 手势处理 -> 移除
        
        // 处理返回键：如果已展开，拦截返回键收起
        BackHandler(enabled = isExpanded) {
            scope.launch {
                expandProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 450, // 收起稍微快一点
                        easing = FastOutSlowInEasing
                    )
                )
                isExpanded = false
                onExpandStateChanged(false)
            }
        }
        
        // 颜色插值：从 PrimaryContainer (FAB 默认色) -> Surface (页面背景色)
        // 假设 FAB 用 PrimaryContainer (Material3 默认)。页面用 Surface。
        val fabColor = MaterialTheme.colorScheme.primaryContainer
        val pageColor = MaterialTheme.colorScheme.surface
        
        // 手动插值计算当前背景色
        val currentColor = androidx.compose.ui.graphics.lerp(fabColor, pageColor, expandProgress.value)

        Box(
            modifier = Modifier
                .offset { IntOffset(currentX.roundToInt(), currentY.roundToInt()) }
                .size(currentWidth, currentHeight)
                .shadow(
                    elevation = 6.dp + (10.dp * expandProgress.value),
                    shape = RoundedCornerShape(currentCornerRadius)
                )
                .clip(RoundedCornerShape(currentCornerRadius))
                .background(currentColor) // 使用插值后的颜色
                // 点击 FAB 触发展开
                // 注意：当展开后，这个 Box 会占满全屏，我们需要确保它不拦截页面内部的点击
                // 但在收起状态 (FAB)，它需要响应点击
                .clickable(
                    enabled = !isExpanded,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { 
                    if (onFabClickOverride != null) {
                        onFabClickOverride()
                    } else {
                        expandAction()
                    }
                }
        ) {
            // 内容容器
            // 1. FAB 内容 (图标)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(1f - expandProgress.value), // 展开时渐隐
                contentAlignment = Alignment.Center
            ) {
                // 只有在未完全展开时才显示 FAB 内容，避免点击穿透问题
                if (expandProgress.value < 0.8f) {
                    fabContent {
                        if (onFabClickOverride != null) {
                            onFabClickOverride()
                        } else {
                            expandAction()
                        }
                    }
                }
            }
            
            // 2. 展开后的页面内容
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 内容的 Fade In 稍微延迟一点，让容器先变大
                    // 比如进度到 0.3 以后才开始显示内容
                    .alpha((expandProgress.value - 0.2f).coerceAtLeast(0f) / 0.8f), 
            ) {
                // 只有在开始展开后才渲染内容，优化性能
                if (expandProgress.value > 0.01f) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        expandedContent(onCollapse = {
                            scope.launch {
                                expandProgress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = 450,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                                isExpanded = false
                                onExpandStateChanged(false)
                            }
                        })
                        
                        // FIX: 当正在收缩动画时 (!isExpanded)，覆盖一层消费点击事件的透明层
                        // 防止点击到已经开始渐隐的表单内容 (例如触发输入法)
                        if (!isExpanded) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
