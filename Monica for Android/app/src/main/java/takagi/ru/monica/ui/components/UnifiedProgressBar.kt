package takagi.ru.monica.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import takagi.ru.monica.data.ProgressBarStyle
import kotlin.math.PI
import kotlin.math.sin

/**
 * M3E 表达力设计的统一进度条组件
 * 放置在顶栏下方，显示30秒周期的倒计时
 */
@Composable
fun UnifiedProgressBar(
    modifier: Modifier = Modifier,
    style: ProgressBarStyle = ProgressBarStyle.LINEAR,
    currentSeconds: Long = System.currentTimeMillis() / 1000,
    period: Int = 30,
    smoothProgress: Boolean = true,
    timeOffset: Long = 0 // 新增：时间偏移量（毫秒）以同步 TOTP 生成
) {
    // 平滑进度：使用毫秒级更新
    var currentMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(smoothProgress) {
        if (smoothProgress) {
            while (true) {
                currentMillis = System.currentTimeMillis()
                delay(16) // ~60fps
            }
        }
    }
    
    // 根据是否平滑来计算进度
    // TOTP 周期是从 Unix 纪元开始计算的，需要精确对齐
    val periodMs = period * 1000L
    val (remainingSeconds, progress) = if (smoothProgress) {
        // 应用时间偏移
        val correctedMillis = currentMillis + timeOffset
        val elapsedInPeriodMs = correctedMillis % periodMs
        val remainingMs = periodMs - elapsedInPeriodMs
        val remaining = ((remainingMs + 999) / 1000).toInt()  // 向上取整，用于颜色判断
        val prog = elapsedInPeriodMs.toFloat() / periodMs
        remaining to prog
    } else {
        // 非平滑模式下也要应用时间偏移（注意：非平滑模式 currentSeconds 是秒，timeOffset 是毫秒，需要转换）
        val correctedSeconds = currentSeconds + (timeOffset / 1000)
        val elapsedInPeriod = (correctedSeconds % period).toInt()
        val remaining = period - elapsedInPeriod
        val prog = elapsedInPeriod.toFloat() / period
        remaining to prog
    }
    
    // 根据剩余时间确定颜色
    val progressColor = when {
        remainingSeconds <= 5 -> MaterialTheme.colorScheme.error
        remainingSeconds <= 10 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 进度条区域
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
        ) {
            when (style) {
                ProgressBarStyle.WAVE -> WaveProgressBar(
                    progress = progress,
                    progressColor = progressColor,
                    trackColor = trackColor,
                    smoothProgress = smoothProgress,
                    modifier = Modifier.fillMaxSize()
                )
                ProgressBarStyle.LINEAR -> LinearProgressBar(
                    progress = progress,
                    progressColor = progressColor,
                    trackColor = trackColor,
                    smoothProgress = smoothProgress,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 倒计时显示
        Text(
            text = "${remainingSeconds}s",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = progressColor
        )
    }
}

/**
 * 线形进度条 - M3E 断开风格
 * 已加载和未加载部分断开，带圆角
 */
@Composable
private fun LinearProgressBar(
    progress: Float,
    progressColor: Color,
    trackColor: Color,
    smoothProgress: Boolean = true,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = if (smoothProgress) 50 else 200, 
            easing = FastOutSlowInEasing
        ),
        label = "linear_progress"
    )
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        if (width <= 0f || height <= 0f) return@Canvas
        
        val strokeWidth = height * 0.6f
        val centerY = height / 2f
        val progressWidth = width * animatedProgress
        val gap = 8.dp.toPx()
        val dotRadius = strokeWidth / 2f
        
        // 1. 绘制已加载部分（左侧）
        if (progressWidth > strokeWidth) {
            drawLine(
                color = progressColor,
                start = Offset(strokeWidth / 2f, centerY),
                end = Offset(progressWidth, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        
        // 2. 绘制未加载部分的轨道（右侧直线）
        val trackStartX = progressWidth + gap
        if (trackStartX < width - strokeWidth / 2f) {
            drawLine(
                color = trackColor,
                start = Offset(trackStartX, centerY),
                end = Offset(width - strokeWidth / 2f, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 波浪形进度条 - M3E 表达力设计（官方波浪线条样式）
 * 已加载部分是波浪形，未加载部分是直线，两者断开
 */
@Composable
private fun WaveProgressBar(
    progress: Float,
    progressColor: Color,
    trackColor: Color,
    smoothProgress: Boolean = true,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = if (smoothProgress) 50 else 300, 
            easing = FastOutSlowInEasing
        ),
        label = "wave_progress"
    )
    
    // 波浪动画 - 让波浪缓慢流动
    val waveTransition = rememberInfiniteTransition(label = "wave_animation")
    val wavePhase by waveTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        // 防止尺寸为0时绘制
        if (width <= 0f || height <= 0f) return@Canvas
        
        val strokeWidth = height * 0.5f  // 线条粗细
        val centerY = height / 2f
        val amplitude = height * 0.25f   // 波浪振幅
        val wavelength = 35.dp.toPx()    // 波长
        
        // 防止波长为0
        if (wavelength <= 0f) return@Canvas
        
        val progressWidth = width * animatedProgress
        val gap = 8.dp.toPx()  // 断开的间隙大小
        
        // 1. 绘制未加载部分的轨道（直线，从进度结束+间隙开始）
        val trackStartX = progressWidth + gap
        if (trackStartX < width) {
            drawLine(
                color = trackColor,
                start = Offset(trackStartX, centerY),
                end = Offset(width, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        
        // 2. 绘制已加载部分（波浪形线条）
        if (progressWidth > strokeWidth) {
            val progressPath = Path().apply {
                var x = 0f
                val startY = centerY + amplitude * sin(wavePhase)
                moveTo(0f, startY)
                
                while (x <= progressWidth) {
                    val phase = (x / wavelength) * 2f * PI.toFloat() + wavePhase
                    val y = centerY + amplitude * sin(phase)
                    lineTo(x, y)
                    x += 2f
                }
            }
            
            drawPath(
                path = progressPath,
                color = progressColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )
        }
    }
}

/**
 * 紧凑版统一进度条 - 用于空间有限的场景
 */
@Composable
fun CompactUnifiedProgressBar(
    modifier: Modifier = Modifier,
    style: ProgressBarStyle = ProgressBarStyle.LINEAR,
    currentSeconds: Long = System.currentTimeMillis() / 1000,
    period: Int = 30,
    smoothProgress: Boolean = true,
    timeOffset: Long = 0 // 新增：时间偏移量
) {
    // 平滑进度：使用毫秒级更新
    var currentMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(smoothProgress) {
        if (smoothProgress) {
            while (true) {
                currentMillis = System.currentTimeMillis()
                delay(16) // ~60fps
            }
        }
    }
    
    val (remainingSeconds, progress) = if (smoothProgress) {
        val correctedMillis = currentMillis + timeOffset
        val periodMs = period * 1000L
        val elapsedInPeriodMs = correctedMillis % periodMs
        val remainingMs = periodMs - elapsedInPeriodMs
        val remaining = ((remainingMs + 999) / 1000).toInt()
        val prog = elapsedInPeriodMs.toFloat() / periodMs
        remaining to prog
    } else {
        val correctedSeconds = currentSeconds + (timeOffset / 1000)
        val elapsedInPeriod = (correctedSeconds % period).toInt()
        val remaining = period - elapsedInPeriod
        val prog = elapsedInPeriod.toFloat() / period
        remaining to prog
    }
    
    val progressColor = when {
        remainingSeconds <= 5 -> MaterialTheme.colorScheme.error
        remainingSeconds <= 10 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .padding(horizontal = 16.dp)
    ) {
        when (style) {
            ProgressBarStyle.WAVE -> WaveProgressBar(
                progress = progress,
                progressColor = progressColor,
                trackColor = trackColor,
                smoothProgress = smoothProgress,
                modifier = Modifier.fillMaxSize()
            )
            ProgressBarStyle.LINEAR -> LinearProgressBar(
                progress = progress,
                progressColor = progressColor,
                trackColor = trackColor,
                smoothProgress = smoothProgress,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
