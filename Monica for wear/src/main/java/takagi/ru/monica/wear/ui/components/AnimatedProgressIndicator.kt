package takagi.ru.monica.wear.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import takagi.ru.monica.wear.viewmodel.ColorScheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * 经典圆环进度条 - 简洁风格
 */
@Composable
fun ClassicCircularProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    
    Canvas(modifier = modifier.size(120.dp)) {
        val strokeWidth = 8.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val topLeft = Offset(
            (size.width - radius * 2) / 2,
            (size.height - radius * 2) / 2
        )
        
        // 背景圆环
        drawArc(
            color = surfaceColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        
        // 进度圆环
        drawArc(
            color = primaryColor,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = topLeft,
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

/**
 * 现代波浪进度条 - Pixel Watch风格
 * 稳定的粗线条圆环，轻微的呼吸效果
 */
@Composable
fun ModernWaveProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    
    // 轻微的呼吸效果（透明度变化）
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheAlpha"
    )
    
    Canvas(modifier = modifier.size(120.dp)) {
        val strokeWidth = 10.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val centerX = size.width / 2
        val centerY = size.height / 2
        val topLeft = Offset(centerX - radius, centerY - radius)
        
        // 背景圆环
        drawArc(
            color = surfaceColor.copy(alpha = 0.3f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        
        // 进度圆环 - 带轻微呼吸效果
        drawArc(
            color = primaryColor.copy(alpha = alpha),
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = topLeft,
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

/**
 * 动感多层进度条 - Material 3 Expressive风格
 */
@Composable
fun DynamicMultiLayerProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Canvas(modifier = modifier.size(120.dp)) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        // 外层光晕
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.3f),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = size.minDimension / 2
            ),
            radius = size.minDimension / 2 * pulseScale,
            center = Offset(centerX, centerY)
        )
        
        // 三层进度环
        val layers = listOf(
            Triple(tertiaryColor, 0.7f, 6.dp.toPx()),
            Triple(secondaryColor, 0.85f, 8.dp.toPx()),
            Triple(primaryColor, 1.0f, 10.dp.toPx())
        )
        
        layers.forEachIndexed { index, (color, radiusMultiplier, strokeWidth) ->
            val radius = (size.minDimension / 2 - 16.dp.toPx()) * radiusMultiplier
            val topLeft = Offset(centerX - radius, centerY - radius)
            
            // 每层有不同的旋转偏移
            val angleOffset = rotation * (index + 1) / layers.size
            
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        color.copy(alpha = 0.3f),
                        color,
                        color.copy(alpha = 0.3f)
                    ),
                    center = Offset(centerX, centerY)
                ),
                startAngle = -90f + angleOffset,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        
        // 中心脉冲点
        drawCircle(
            color = primaryColor,
            radius = 8.dp.toPx() * pulseScale,
            center = Offset(centerX, centerY)
        )
    }
}

