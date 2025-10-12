package takagi.ru.monica.ui.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Phase 9: 动画工具类
 * 
 * 提供统一的动画规范和常用动画效果
 * 遵循 Material Design 3 Motion 规范
 * 
 * ## 动画时长标准
 * - SHORT (150ms): 小元素、图标、按钮状态
 * - MEDIUM (300ms): 列表项、卡片、对话框
 * - LONG (500ms): 页面转场、大型元素
 * 
 * ## 缓动函数
 * - FastOutSlowIn: 标准过渡（推荐）
 * - LinearOutSlowIn: 进入动画
 * - FastOutLinearIn: 退出动画
 * - EaseInOut: 循环动画
 * 
 * ## 使用示例
 * ```kotlin
 * // 列表项动画
 * AnimatedVisibility(
 *     visible = isVisible,
 *     enter = AnimationUtils.listItemEnterAnimation(index),
 *     exit = AnimationUtils.fadeOutAnimation()
 * ) {
 *     ListItem()
 * }
 * 
 * // 页面转场
 * AnimatedContent(
 *     targetState = currentPage,
 *     transitionSpec = AnimationUtils.pageTransitionSpec()
 * ) { page ->
 *     PageContent(page)
 * }
 * ```
 */
object AnimationUtils {
    
    // ==================== 动画时长常量 ====================
    
    /**
     * 短动画时长（150ms）
     * 适用于：图标旋转、按钮状态变化、小型元素
     */
    const val DURATION_SHORT = 150
    
    /**
     * 中等动画时长（300ms）
     * 适用于：列表项、卡片展开、对话框显示
     */
    const val DURATION_MEDIUM = 300
    
    /**
     * 长动画时长（500ms）
     * 适用于：页面转场、大型元素移动
     */
    const val DURATION_LONG = 500
    
    /**
     * 延迟基数（50ms）
     * 用于计算列表项交错动画的延迟
     */
    private const val STAGGER_DELAY_BASE = 50
    
    // ==================== 缓动函数 ====================
    
    /**
     * 标准过渡缓动（推荐）
     */
    val standardEasing = FastOutSlowInEasing
    
    /**
     * 进入动画缓动
     */
    val enterEasing = LinearOutSlowInEasing
    
    /**
     * 退出动画缓动
     */
    val exitEasing = FastOutLinearInEasing
    
    // ==================== 列表动画 ====================
    
    /**
     * 列表项进入动画（淡入 + 向上滑动 + 交错）
     * 
     * @param index 列表项索引，用于计算延迟
     * @param maxDelay 最大延迟时间（ms），避免列表过长时延迟过久
     * @return EnterTransition
     */
    fun listItemEnterAnimation(
        index: Int,
        maxDelay: Int = 300
    ): EnterTransition {
        val delay = (index * STAGGER_DELAY_BASE).coerceAtMost(maxDelay)
        
        return fadeIn(
            animationSpec = tween(
                durationMillis = DURATION_MEDIUM,
                delayMillis = delay,
                easing = enterEasing
            )
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = DURATION_MEDIUM,
                delayMillis = delay,
                easing = enterEasing
            ),
            initialOffsetY = { it / 4 }  // 从下方 1/4 处进入
        )
    }
    
    /**
     * 列表项退出动画（淡出 + 向左滑动）
     * 
     * @return ExitTransition
     */
    fun listItemExitAnimation(): ExitTransition {
        return fadeOut(
            animationSpec = tween(
                durationMillis = DURATION_SHORT,
                easing = exitEasing
            )
        ) + slideOutHorizontally(
            animationSpec = tween(
                durationMillis = DURATION_SHORT,
                easing = exitEasing
            ),
            targetOffsetX = { -it / 2 }  // 向左滑出一半
        )
    }
    
    // ==================== 页面转场动画 ====================
    
    /**
     * 页面转场动画规范
     * 
     * 进入页面：淡入 + 从右向左滑入
     * 退出页面：淡出 + 向左滑出
     * 
     * @return AnimatedContentTransitionScope
     */
    fun <S> pageTransitionSpec(): AnimatedContentTransitionScope<S>.() -> ContentTransform {
        return {
            fadeIn(
                animationSpec = tween(
                    durationMillis = DURATION_MEDIUM,
                    easing = enterEasing
                )
            ) + slideInHorizontally(
                animationSpec = tween(
                    durationMillis = DURATION_MEDIUM,
                    easing = enterEasing
                ),
                initialOffsetX = { it / 3 }  // 从右侧 1/3 处进入
            ) togetherWith fadeOut(
                animationSpec = tween(
                    durationMillis = DURATION_SHORT,
                    easing = exitEasing
                )
            ) + slideOutHorizontally(
                animationSpec = tween(
                    durationMillis = DURATION_SHORT,
                    easing = exitEasing
                ),
                targetOffsetX = { -it / 3 }  // 向左侧 1/3 处退出
            )
        }
    }
    
    /**
     * 对话框进入动画（淡入 + 缩放）
     * 
     * @return EnterTransition
     */
    fun dialogEnterAnimation(): EnterTransition {
        return fadeIn(
            animationSpec = tween(
                durationMillis = DURATION_MEDIUM,
                easing = enterEasing
            )
        ) + scaleIn(
            animationSpec = tween(
                durationMillis = DURATION_MEDIUM,
                easing = enterEasing
            ),
            initialScale = 0.9f
        )
    }
    
    /**
     * 对话框退出动画（淡出 + 缩放）
     * 
     * @return ExitTransition
     */
    fun dialogExitAnimation(): ExitTransition {
        return fadeOut(
            animationSpec = tween(
                durationMillis = DURATION_SHORT,
                easing = exitEasing
            )
        ) + scaleOut(
            animationSpec = tween(
                durationMillis = DURATION_SHORT,
                easing = exitEasing
            ),
            targetScale = 0.9f
        )
    }
    
    // ==================== 基础动画 ====================
    
    /**
     * 淡入动画
     * 
     * @param duration 动画时长
     * @return EnterTransition
     */
    fun fadeInAnimation(duration: Int = DURATION_MEDIUM): EnterTransition {
        return fadeIn(
            animationSpec = tween(
                durationMillis = duration,
                easing = enterEasing
            )
        )
    }
    
    /**
     * 淡出动画
     * 
     * @param duration 动画时长
     * @return ExitTransition
     */
    fun fadeOutAnimation(duration: Int = DURATION_SHORT): ExitTransition {
        return fadeOut(
            animationSpec = tween(
                durationMillis = duration,
                easing = exitEasing
            )
        )
    }
    
    /**
     * 展开动画（从上向下）
     * 
     * @return EnterTransition
     */
    fun expandVerticallyAnimation(): EnterTransition {
        return expandVertically(
            animationSpec = tween(
                durationMillis = DURATION_MEDIUM,
                easing = enterEasing
            ),
            expandFrom = Alignment.Top
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = DURATION_MEDIUM,
                easing = enterEasing
            )
        )
    }
    
    /**
     * 收起动画（从下向上）
     * 
     * @return ExitTransition
     */
    fun shrinkVerticallyAnimation(): ExitTransition {
        return shrinkVertically(
            animationSpec = tween(
                durationMillis = DURATION_SHORT,
                easing = exitEasing
            ),
            shrinkTowards = Alignment.Top
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = DURATION_SHORT,
                easing = exitEasing
            )
        )
    }
    
    // ==================== 加载动画组件 ====================
    
    /**
     * 骨架屏加载动画
     * 
     * 显示脉冲动画的矩形占位符，用于内容加载时的视觉反馈
     * 
     * @param modifier Modifier
     * @param count 骨架屏数量
     * @param itemHeight 每个骨架屏高度
     */
    @Composable
    fun LoadingSkeleton(
        modifier: Modifier = Modifier,
        count: Int = 5,
        itemHeight: Int = 80
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(count) { index ->
                ShimmerItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight.dp),
                    delay = index * 100
                )
            }
        }
    }
    
    /**
     * 单个闪烁项
     */
    @Composable
    private fun ShimmerItem(
        modifier: Modifier = Modifier,
        delay: Int = 0
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
        
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1000,
                    delayMillis = delay,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shimmerAlpha"
        )
        
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
                )
        )
    }
    
    // ==================== 状态动画组件 ====================
    
    /**
     * 成功动画组件
     * 
     * 显示勾选图标的放大动画
     */
    @Composable
    fun SuccessAnimation(
        visible: Boolean,
        modifier: Modifier = Modifier,
        onAnimationEnd: () -> Unit = {}
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(),
            exit = fadeOut(),
            modifier = modifier
        ) {
            // TODO: 添加勾选图标
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(1000)
                onAnimationEnd()
            }
        }
    }
    
    /**
     * 错误动画组件
     * 
     * 显示错误图标的抖动动画
     */
    @Composable
    fun ErrorAnimation(
        visible: Boolean,
        modifier: Modifier = Modifier
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioHighBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            ) + fadeIn(),
            exit = fadeOut(),
            modifier = modifier
        ) {
            // TODO: 添加错误图标
        }
    }
}
