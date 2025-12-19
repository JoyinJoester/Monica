package takagi.ru.monica.ui.gestures

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    
    // 卡片宽度（动态获取）
    var cardWidth by remember { mutableFloatStateOf(0f) }
    
    // 最大滑动距离（用于显示效果）
    val maxSwipeDistance = 300f
    
    // 自定义弹簧物理模型 - Q弹效果
    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,  // 中等弹性
        stiffness = Spring.StiffnessMedium,               // 中等刚度
        visibilityThreshold = 0.2f                        // 精确停止（更容易触发）
    )
    
    // 快速回弹动画（用于取消操作）
    val quickSpringSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,     // 低弹性
        stiffness = Spring.StiffnessHigh,                 // 高刚度
        visibilityThreshold = 0.2f
    )
    
    // 动态计算滑动阈值（基于卡片宽度）
    val swipeThreshold = remember(cardWidth) {
        if (cardWidth > 0f) cardWidth * 0.2f else 60f  // 20% 卡片宽度或默认 60px
    }
    
    // 计算背景透明度（渐进式显示）
    val backgroundAlpha = remember(offsetX, swipeThreshold) {
        (abs(offsetX) / swipeThreshold).coerceIn(0f, 1f)
    }
    
    // 计算图标缩放（动态缩放效果）
    val iconScale = remember(offsetX, swipeThreshold) {
        val progress = (abs(offsetX) / swipeThreshold).coerceIn(0f, 1.2f)
        0.8f + (progress * 0.4f) // 0.8 -> 1.2
    }
    
    // 🎨 右滑时卡片渐变色（跟随滑动距离）
    val cardTintAlpha = remember(offsetX, swipeThreshold) {
        if (offsetX > 0) {
            // 右滑时，根据滑动进度渐变到主题色
            (offsetX / swipeThreshold).coerceIn(0f, 0.6f)
        } else {
            0f
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)) // 统一圆角，避免锯齿
    ) {
        // 左侧背景（右滑显示 - 选择）
        if (offsetX > 0) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .matchParentSize(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = backgroundAlpha),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart // 左对齐
                ) {
                    Row(
                        modifier = Modifier
                            .padding(start = 24.dp)
                            .graphicsLayer {
                                // 背景内容微动画（从左侧跟随）
                                translationX = (offsetX * 0.3f).coerceIn(0f, 40f)
                                alpha = backgroundAlpha
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "选择",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                        Text(
                            text = stringResource(R.string.swipe_action_select),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
        
        // 右侧背景（左滑显示 - 删除）
        if (offsetX < 0) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .matchParentSize(),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = backgroundAlpha),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd // 右对齐
                ) {
                    Row(
                        modifier = Modifier
                            .padding(end = 24.dp)
                            .graphicsLayer {
                                // 背景内容微动画（从右侧跟随）
                                translationX = (offsetX * 0.3f).coerceIn(-40f, 0f)
                                alpha = backgroundAlpha
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                        Text(
                            text = stringResource(R.string.swipe_action_delete),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
        
        // 前景内容
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 🎨 渐变色遮罩层（右滑时显示）
            if (cardTintAlpha > 0f) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = animatedOffset.value
                            alpha = cardTintAlpha
                        },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {}
            }
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = animatedOffset.value
                        // 添加微妙的阴影效果
                        shadowElevation = (abs(animatedOffset.value) / 100f).coerceIn(0f, 8f)
                    }
                    .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    
                    detectHorizontalDragGestures(
                        onDragStart = {
                            // 记录卡片宽度（首次滑动时获取）
                            if (cardWidth == 0f) {
                                cardWidth = size.width.toFloat()
                            }
                            android.util.Log.d("SwipeActions", "Drag started, cardWidth: $cardWidth")
                        },
                        onDragEnd = {
                            android.util.Log.d("SwipeActions", "Drag ended, offsetX: $offsetX, threshold: ${cardWidth * 0.5f}")
                            android.util.Log.d("SwipeActions", "Drag ended, offsetX: $offsetX, threshold: ${cardWidth * 0.2f}")
                            scope.launch {
                                // 动态计算阈值（20% 卡片宽度）
                                val dynamicThreshold = cardWidth * 0.2f
                                when {
                                    // 左滑超过50%宽度 - 触发删除
                                    offsetX < -dynamicThreshold -> {
                                        android.util.Log.d("SwipeActions", "Triggering LEFT swipe (delete)")
                                        // 使用快速弹簧动画滑出
                                        animatedOffset.animateTo(
                                            targetValue = -cardWidth,
                                            animationSpec = tween(
                                                durationMillis = 300,
                                                easing = FastOutSlowInEasing
                                            )
                                        )
                                        onSwipeLeft()
                                    }
                                    // 右滑超过50%宽度 - 触发选择
                                    offsetX > dynamicThreshold -> {
                                        android.util.Log.d("SwipeActions", "Triggering RIGHT swipe (select)")
                                        // Q弹回弹效果
                                        animatedOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = springSpec
                                        )
                                        onSwipeRight()
                                    }
                                    // 未达到50%阈值 - 操作无效，Q弹回弹
                                    else -> {
                                        android.util.Log.d("SwipeActions", "Swipe cancelled (not enough distance)")
                                        animatedOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = quickSpringSpec
                                        )
                                    }
                                }
                                offsetX = 0f
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                // 取消时快速回弹
                                animatedOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = quickSpringSpec
                                )
                                offsetX = 0f
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                // 添加阻尼效果，接近边界时减速
                                val currentOffset = animatedOffset.value
                                val resistance = when {
                                    abs(currentOffset) > maxSwipeDistance -> 0.1f  // 强阻尼
                                    abs(currentOffset) > maxSwipeDistance * 0.8f -> 0.5f  // 中阻尼
                                    else -> 1f  // 无阻尼
                                }
                                
                                val newOffset = (currentOffset + dragAmount * resistance)
                                    .coerceIn(-maxSwipeDistance * 1.2f, maxSwipeDistance * 1.2f)
                                
                                animatedOffset.snapTo(newOffset)
                                offsetX = newOffset
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            content()
        }
        }
    }
}
