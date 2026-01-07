package takagi.ru.monica.wear.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

import takagi.ru.monica.wear.R
import takagi.ru.monica.wear.viewmodel.TotpItemState

/**
 * TOTP验证码卡片组件 - Wear OS M3E 设计
 * 自适应屏幕尺寸，优化圆形屏幕布局
 */
@Composable
fun TotpCard(
    state: TotpItemState,
    onCopyCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val isExpiringSoon = state.remainingSeconds < 5
    
    // 验证码变化动画
    var previousCode by remember { mutableStateOf(state.code) }
    val codeChanged = previousCode != state.code
    LaunchedEffect(state.code) {
        previousCode = state.code
    }
    
    // 即将过期时的脉冲动画
    val pulseAlpha by animateFloatAsState(
        targetValue = if (isExpiringSoon) 0.7f else 1f,
        animationSpec = if (isExpiringSoon) {
            infiniteRepeatable(
                animation = tween(500),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(300)
        },
        label = "pulseAlpha"
    )
    
    // 颜色定义
    val titleColor = MaterialTheme.colorScheme.onSurface
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val codeColor = if (isExpiringSoon) 
        MaterialTheme.colorScheme.error 
    else 
        MaterialTheme.colorScheme.primary
    val timerColor = if (isExpiringSoon) 
        MaterialTheme.colorScheme.error 
    else 
        MaterialTheme.colorScheme.secondary
    
    // 平滑进度动画
    val animatedProgress by animateFloatAsState(
        targetValue = 1f - state.progress,
        animationSpec = tween(1000, easing = LinearEasing),
        label = "progressAnimation"
    )
    
    // 点击缩放动画
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )
    
    // 使用 BoxWithConstraints 自适应屏幕尺寸
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .scale(pressScale)
            .background(MaterialTheme.colorScheme.background)
            .clickable { 
                isPressed = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onCopyCode()
            },
        contentAlignment = Alignment.Center
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        
        // 自适应尺寸计算
        val padding = screenWidth * 0.08f
        val titleSize = (screenWidth.value * 0.08f).sp
        val subtitleSize = (screenWidth.value * 0.055f).sp
        val codeSize = (screenWidth.value * 0.16f).sp
        val letterSpacing = (screenWidth.value * 0.015f).sp
        val progressSize = screenWidth * 0.2f
        val progressStroke = screenWidth * 0.018f
        val timerTextSize = (screenWidth.value * 0.06f).sp
        val spacing = screenHeight * 0.03f
        val smallSpacing = screenHeight * 0.015f
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 发行方名称
            if (state.totpData.issuer.isNotBlank()) {
                Text(
                    text = state.totpData.issuer,
                    color = titleColor,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
            
            // 账户名
            if (state.totpData.accountName.isNotBlank()) {
                Spacer(modifier = Modifier.height(smallSpacing * 0.5f))
                Text(
                    text = state.totpData.accountName,
                    color = subtitleColor,
                    fontSize = subtitleSize,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
            
            Spacer(modifier = Modifier.height(spacing))
            
            // 验证码（带动画）
            AnimatedContent(
                targetState = state.code,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(300)) + 
                     scaleIn(initialScale = 0.8f, animationSpec = spring(
                         dampingRatio = Spring.DampingRatioMediumBouncy
                     )))
                        .togetherWith(fadeOut(animationSpec = tween(200)))
                },
                label = "codeAnimation"
            ) { code ->
                Text(
                    text = formatCode(code),
                    color = codeColor.copy(alpha = pulseAlpha),
                    fontSize = codeSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = letterSpacing
                )
            }
            
            Spacer(modifier = Modifier.height(spacing))
            
            // 倒计时进度条 - 自适应尺寸
            Box(
                modifier = Modifier.size(progressSize),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    color = timerColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = progressStroke
                )
                
                Text(
                    text = state.remainingSeconds.toString(),
                    color = timerColor,
                    fontSize = timerTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(smallSpacing))
            
            Text(
                text = stringResource(R.string.totp_seconds_refresh),
                color = subtitleColor,
                fontSize = (screenWidth.value * 0.045f).sp
            )
        }
    }
    
    // 重置按压状态
    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(150)
            isPressed = false
        }
    }
}

/**
 * 格式化验证码（添加空格分隔）
 */
private fun formatCode(code: String): String {
    return when {
        code.length == 6 -> "${code.substring(0, 3)} ${code.substring(3)}"
        code.length == 8 -> "${code.substring(0, 4)} ${code.substring(4)}"
        else -> code
    }
}
