package takagi.ru.monica.wear.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 带淡入淡出和缩放效果的动画文字
 */
@Composable
fun AnimatedText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Normal
) {
    var previousText by remember { mutableStateOf(text) }
    val animationSpec = tween<Float>(300, easing = FastOutSlowInEasing)
    
    LaunchedEffect(text) {
        if (text != previousText) {
            previousText = text
        }
    }
    
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            fadeIn(animationSpec) + scaleIn(
                animationSpec,
                initialScale = 0.8f,
                transformOrigin = TransformOrigin.Center
            ) togetherWith fadeOut(animationSpec) + scaleOut(
                animationSpec,
                targetScale = 0.8f,
                transformOrigin = TransformOrigin.Center
            )
        },
        label = "textAnimation"
    ) { targetText ->
        Text(
            text = targetText,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier
        )
    }
}

/**
 * TOTP代码专用动画文字 - 大尺寸,带高亮效果
 */
@Composable
fun TotpCodeAnimatedText(
    code: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "codeGlow")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    AnimatedContent(
        targetState = code,
        transitionSpec = {
            fadeIn(tween(200)) + scaleIn(
                tween(200),
                initialScale = 0.9f
            ) togetherWith fadeOut(tween(200)) + scaleOut(
                tween(200),
                targetScale = 1.1f
            )
        },
        label = "totpCodeAnimation"
    ) { targetCode ->
        Text(
            text = targetCode,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            modifier = modifier,
            letterSpacing = 4.sp
        )
    }
}

/**
 * 倒计时动画文字 - 带颜色变化
 */
@Composable
fun CountdownAnimatedText(
    seconds: Int,
    modifier: Modifier = Modifier
) {
    val color = when {
        seconds <= 5 -> MaterialTheme.colorScheme.error
        seconds <= 10 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    
    val scale by animateFloatAsState(
        targetValue = if (seconds <= 5) 1.2f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "countdownScale"
    )
    
    AnimatedContent(
        targetState = seconds,
        transitionSpec = {
            slideInVertically { height -> -height } + fadeIn() togetherWith
                    slideOutVertically { height -> height } + fadeOut()
        },
        label = "countdownAnimation"
    ) { targetSeconds ->
        Text(
            text = "${targetSeconds}s",
            fontSize = (20 * scale).sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = modifier
        )
    }
}

/**
 * 标题动画文字 - 滑入效果
 */
@Composable
fun TitleAnimatedText(
    title: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp
) {
    AnimatedContent(
        targetState = title,
        transitionSpec = {
            slideInHorizontally { width -> width } + fadeIn() togetherWith
                    slideOutHorizontally { width -> -width } + fadeOut()
        },
        label = "titleAnimation"
    ) { targetTitle ->
        Text(
            text = targetTitle,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier
        )
    }
}

/**
 * 脉冲效果文字 - 用于强调
 */
@Composable
fun PulseText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    Text(
        text = text,
        fontSize = fontSize * scale,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}
