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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * TOTP验证码卡片组件
 * 统一使用Material Design 3主题颜色
 */
@Composable
fun TotpCard(
    state: TotpItemState,
    onCopyCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    TotpCardUnified(state, onCopyCode, modifier)
}

/**
 * 统一的TOTP卡片设计
 * 使用Material Design 3主题颜色
 */
@Composable
private fun TotpCardUnified(
    state: TotpItemState,
    onCopyCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpiringSoon = state.remainingSeconds < 5
    
    // 使用MaterialTheme主题颜色
    val titleColor = MaterialTheme.colorScheme.onSurface
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val codeColor = if (isExpiringSoon) 
        MaterialTheme.colorScheme.error 
        else MaterialTheme.colorScheme.primary
    val timerColor = if (isExpiringSoon) 
        MaterialTheme.colorScheme.error 
        else MaterialTheme.colorScheme.secondary
    
    // 平滑的进度动画 - 匀速运动
    val animatedProgress by animateFloatAsState(
        targetValue = 1f - state.progress,
        animationSpec = tween(
            durationMillis = 1000,
            easing = LinearEasing
        ),
        label = "progressAnimation"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable { onCopyCode() }
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            // 标题信息（居左）
            if (state.totpData.issuer.isNotBlank()) {
                Text(
                    text = state.totpData.issuer,
                    color = titleColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            if (state.totpData.accountName.isNotBlank()) {
                Text(
                    text = state.totpData.accountName,
                    color = subtitleColor,
                    fontSize = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // 验证码（居左，大字体）
            Text(
                text = formatCode(state.code),
                color = codeColor,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 8.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 倒计时信息（居左）
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = timerColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 3.dp
                    )
                    
                    Text(
                        text = state.remainingSeconds.toString(),
                        color = timerColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = stringResource(R.string.totp_seconds_refresh),
                    color = subtitleColor,
                    fontSize = 14.sp
                )
            }
        }
    }
}


/**
 * 格式化验证码（添加空格分隔）
 * 例如：123456 -> 123 456
 */
private fun formatCode(code: String): String {
    return when {
        code.length == 6 -> "${code.substring(0, 3)} ${code.substring(3)}"
        code.length == 8 -> "${code.substring(0, 4)} ${code.substring(4)}"
        else -> code
    }
}
