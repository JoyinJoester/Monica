package takagi.ru.monica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

/**
 * 自定义数字键盘组件
 * 用于输入纯数字密码
 */
@Composable
fun NumericKeyboard(
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onConfirmClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 第一行: 1 2 3
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NumericKey("1", Modifier.weight(1f)) { onNumberClick("1") }
            NumericKey("2", Modifier.weight(1f)) { onNumberClick("2") }
            NumericKey("3", Modifier.weight(1f)) { onNumberClick("3") }
        }
        
        // 第二行: 4 5 6
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NumericKey("4", Modifier.weight(1f)) { onNumberClick("4") }
            NumericKey("5", Modifier.weight(1f)) { onNumberClick("5") }
            NumericKey("6", Modifier.weight(1f)) { onNumberClick("6") }
        }
        
        // 第三行: 7 8 9
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NumericKey("7", Modifier.weight(1f)) { onNumberClick("7") }
            NumericKey("8", Modifier.weight(1f)) { onNumberClick("8") }
            NumericKey("9", Modifier.weight(1f)) { onNumberClick("9") }
        }
        
        // 第四行: 删除 0 确认
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 删除键
            IconKey(
                icon = { 
                    Icon(
                        Icons.Default.Backspace,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                },
                modifier = Modifier.weight(1f),
                backgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                onClick = onDeleteClick
            )
            
            // 0
            NumericKey("0", Modifier.weight(1f)) { onNumberClick("0") }
            
            // 确认键（如果提供了回调）
            if (onConfirmClick != null) {
                IconKey(
                    icon = { 
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "确认",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    onClick = onConfirmClick
                )
            } else {
                // 占位空白
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * 数字按键
 */
@Composable
private fun NumericKey(
    number: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        shape = CircleShape
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = number,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 图标按键（删除、确认等）
 */
@Composable
private fun IconKey(
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        color = backgroundColor,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        shape = CircleShape
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            icon()
        }
    }
}

