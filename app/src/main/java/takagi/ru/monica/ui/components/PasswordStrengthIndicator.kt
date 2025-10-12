package takagi.ru.monica.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.utils.PasswordStrengthAnalyzer

/**
 * 密码强度指示器组件
 * 
 * 可视化显示密码强度，包括进度条、分数、等级和颜色指示。
 * 
 * ## 显示内容
 * - 📊 进度条 (0-100%)
 * - 🎯 强度分数 (0-100分)
 * - 🏷️ 强度等级 (非常弱/弱/一般/强/非常强)
 * - 🎨 颜色指示 (红→橙→黄→浅绿→绿)
 * 
 * ## 使用示例
 * ```kotlin
 * var password by remember { mutableStateOf("") }
 * val strength = PasswordStrengthAnalyzer.calculateStrength(password)
 * 
 * PasswordStrengthIndicator(
 *     strength = strength,
 *     showScore = true,
 *     modifier = Modifier.fillMaxWidth()
 * )
 * ```
 * 
 * @param strength 密码强度分数 (0-100)
 * @param showScore 是否显示数字分数（默认 true）
 * @param modifier 修饰符
 */
@Composable
fun PasswordStrengthIndicator(
    strength: Int,
    showScore: Boolean = true,
    modifier: Modifier = Modifier
) {
    // 强度等级和颜色
    val level = PasswordStrengthAnalyzer.getStrengthLevel(strength)
    val levelText = PasswordStrengthAnalyzer.getStrengthLevelText(level)
    val color = getStrengthColor(level)
    
    // 进度动画
    val animatedProgress by animateFloatAsState(
        targetValue = strength / 100f,
        animationSpec = tween(durationMillis = 300),
        label = "strength_progress"
    )
    
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 进度条
        LinearProgressIndicator(
            progress = animatedProgress,
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )
        
        // 强度信息
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 等级文本
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "密码强度:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = levelText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = color
                )
            }
            
            // 分数
            if (showScore) {
                Text(
                    text = "$strength/100",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = color
                )
            }
        }
    }
}

/**
 * 获取强度对应的颜色
 * 
 * @param level 强度等级
 * @return 对应的颜色
 */
private fun getStrengthColor(level: PasswordStrengthAnalyzer.StrengthLevel): Color {
    return when (level) {
        PasswordStrengthAnalyzer.StrengthLevel.VERY_WEAK -> Color(0xFFD32F2F) // 红色
        PasswordStrengthAnalyzer.StrengthLevel.WEAK -> Color(0xFFFF9800) // 橙色
        PasswordStrengthAnalyzer.StrengthLevel.FAIR -> Color(0xFFFFC107) // 琥珀色
        PasswordStrengthAnalyzer.StrengthLevel.STRONG -> Color(0xFF8BC34A) // 浅绿色
        PasswordStrengthAnalyzer.StrengthLevel.VERY_STRONG -> Color(0xFF4CAF50) // 绿色
    }
}

/**
 * 密码强度建议列表组件
 * 
 * 显示密码改进建议列表。
 * 
 * @param suggestions 建议列表
 * @param modifier 修饰符
 */
@Composable
fun PasswordSuggestionsList(
    suggestions: List<String>,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return
    
    Column(
        modifier = modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "改进建议:",
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        suggestions.forEach { suggestion ->
            val isWarning = suggestion.startsWith("⚠️")
            val color = if (isWarning) {
                Color(0xFFD32F2F) // 红色警告
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 密码强度卡片组件（紧凑版）
 * 
 * 包含强度指示器和建议的完整卡片。
 * 
 * @param password 待分析的密码
 * @param modifier 修饰符
 */
@Composable
fun PasswordStrengthCard(
    password: String,
    modifier: Modifier = Modifier
) {
    if (password.isEmpty()) return
    
    val strength = PasswordStrengthAnalyzer.calculateStrength(password)
    val suggestions = PasswordStrengthAnalyzer.getSuggestions(password)
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 强度指示器
            PasswordStrengthIndicator(
                strength = strength,
                showScore = true
            )
            
            // 建议列表
            if (suggestions.isNotEmpty()) {
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                PasswordSuggestionsList(suggestions = suggestions)
            }
        }
    }
}
