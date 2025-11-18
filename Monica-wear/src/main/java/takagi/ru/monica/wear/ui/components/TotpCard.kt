package takagi.ru.monica.wear.ui.components

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import takagi.ru.monica.wear.viewmodel.TotpItemState

/**
 * TOTP验证码卡片组件 - 简洁设计
 * 固定深色背景，标题居左，只有字体颜色变化
 */
@Composable
fun TotpCard(
    state: TotpItemState,
    onCopyCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpiringSoon = state.remainingSeconds < 5
    
    // 字体颜色
    val titleColor = Color.White
    val subtitleColor = Color.White.copy(alpha = 0.6f)
    val codeColor = if (isExpiringSoon) Color(0xFFEF4444) else Color(0xFF60A5FA)
    val timerColor = if (isExpiringSoon) Color(0xFFEF4444) else Color(0xFF6366F1)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1B21))  // 固定深色背景
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
                        progress = { 1f - state.progress },
                        modifier = Modifier.fillMaxSize(),
                        color = timerColor,
                        trackColor = Color.White.copy(alpha = 0.1f),
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
                    text = "秒后刷新",
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
