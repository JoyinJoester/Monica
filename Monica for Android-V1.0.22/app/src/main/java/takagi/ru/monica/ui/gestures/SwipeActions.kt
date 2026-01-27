package takagi.ru.monica.ui.gestures

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import kotlin.math.abs

/**
 * 双向滑动组件 - 优化版
 * 
 * 特性：
 * - 平滑的圆角过渡
 * - 弹性回弹动画（Q弹效果）
 * - 60fps 流畅表现
 * - 自然的颜色过渡
 * - 自定义弹簧物理模型
 * - 防误触：滑动超过50%宽度才触发
 * 
 * 左滑：删除操作（红色背景）- 需超过50%卡片宽度
 * 右滑：选择操作（蓝色背景）- 需超过50%卡片宽度
 * 
 * @param onSwipeLeft 左滑回调（删除）
 * @param onSwipeRight 右滑回调（选择）
 * @param enabled 是否启用滑动
 * @param content 内容
 */
@Composable
fun SwipeActions(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // 使用非动画状态记录实时拖动偏移，避免高频创建协程
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // 仅用于回弹动画的 Animatable
    val animatableOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    
    // 卡片宽度
    var cardWidth by remember { mutableFloatStateOf(0f) }
    val maxSwipeDistance = 300f
    
    // 弹性物理模型
    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = 0.2f
    )
    
    val quickSpringSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessHigh,
        visibilityThreshold = 0.2f
    )
    
    // 计算当前显示的总偏移量
    val totalOffset = dragOffset + animatableOffset.value
    
    val swipeThreshold = remember(cardWidth) {
        if (cardWidth > 0f) cardWidth * 0.2f else 60f
    }
    
    // 背景透明度和图标缩放
    val backgroundAlpha = (abs(totalOffset) / swipeThreshold).coerceIn(0f, 1f)
    val iconScale = 0.8f + ((abs(totalOffset) / swipeThreshold).coerceIn(0f, 1.2f) * 0.4f)
    
    // 右滑遮罩透明度
    val cardTintAlpha = if (totalOffset > 0) (totalOffset / swipeThreshold).coerceIn(0f, 0.6f) else 0f
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        // 左侧背景
        if (totalOffset > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth().matchParentSize(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = backgroundAlpha),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(contentAlignment = Alignment.CenterStart) {
                    Row(
                        modifier = Modifier.padding(start = 24.dp).graphicsLayer {
                            translationX = (totalOffset * 0.3f).coerceIn(0f, 40f)
                            alpha = backgroundAlpha
                        },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
                        Text(stringResource(R.string.swipe_action_select), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
        
        // 右侧背景
        if (totalOffset < 0) {
            Surface(
                modifier = Modifier.fillMaxWidth().matchParentSize(),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = backgroundAlpha),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(contentAlignment = Alignment.CenterEnd) {
                    Row(
                        modifier = Modifier.padding(end = 24.dp).graphicsLayer {
                            translationX = (totalOffset * 0.3f).coerceIn(-40f, 0f)
                            alpha = backgroundAlpha
                        },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
                        Text(stringResource(R.string.swipe_action_delete), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
        
        // 前景内容
        Box(modifier = Modifier.fillMaxWidth()) {
            if (cardTintAlpha > 0f) {
                Surface(
                    modifier = Modifier.fillMaxSize().graphicsLayer { translationX = totalOffset; alpha = cardTintAlpha },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {}
            }
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = totalOffset
                        shadowElevation = (abs(totalOffset) / 100f).coerceIn(0f, 8f)
                    }
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { if (cardWidth == 0f) cardWidth = size.width.toFloat() },
                            onDragEnd = {
                                scope.launch {
                                    // 将实时状态转移到 Animatable 中处理动画
                                    animatableOffset.snapTo(dragOffset)
                                    dragOffset = 0f
                                    
                                    val dynamicThreshold = cardWidth * 0.2f
                                    if (animatableOffset.value < -dynamicThreshold) {
                                        animatableOffset.animateTo(-cardWidth, tween(300, easing = FastOutSlowInEasing))
                                        onSwipeLeft()
                                    } else if (animatableOffset.value > dynamicThreshold) {
                                        animatableOffset.animateTo(0f, springSpec)
                                        onSwipeRight()
                                    } else {
                                        animatableOffset.animateTo(0f, quickSpringSpec)
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    animatableOffset.snapTo(dragOffset)
                                    dragOffset = 0f
                                    animatableOffset.animateTo(0f, quickSpringSpec)
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                // 高频拖动：仅更新 FloatState，不创建协程
                                val current = dragOffset
                                val resistance = when {
                                    abs(current) > maxSwipeDistance -> 0.1f
                                    abs(current) > maxSwipeDistance * 0.8f -> 0.5f
                                    else -> 1f
                                }
                                dragOffset = (current + dragAmount * resistance)
                                    .coerceIn(-maxSwipeDistance * 1.2f, maxSwipeDistance * 1.2f)
                            }
                        )
                    },
                shape = RoundedCornerShape(16.dp)
            ) {
                content()
            }
        }
    }
}
