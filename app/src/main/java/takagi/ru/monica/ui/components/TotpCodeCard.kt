package takagi.ru.monica.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import takagi.ru.monica.R
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.util.TotpGenerator
import kotlinx.serialization.json.Json

/**
 * TOTP验证码卡片
 * 显示实时生成的6位验证码和倒计时
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotpCodeCard(
    item: SecureItem,
    onClick: () -> Unit,
    onCopyCode: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    onToggleFavorite: ((Long, Boolean) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    // 解析TOTP数据
    val totpData = try {
        Json.decodeFromString<TotpData>(item.itemData)
    } catch (e: Exception) {
        TotpData(secret = "")
    }
    
    // 实时更新验证码
    var currentCode by remember { mutableStateOf("") }
    var remainingSeconds by remember { mutableIntStateOf(30) }
    var progress by remember { mutableFloatStateOf(0f) }
    
    // 每秒更新
    LaunchedEffect(Unit) {
        while (true) {
            currentCode = TotpGenerator.generateTotp(
                secret = totpData.secret,
                period = totpData.period,
                digits = totpData.digits,
                algorithm = totpData.algorithm
            )
            remainingSeconds = TotpGenerator.getRemainingSeconds(totpData.period)
            progress = TotpGenerator.getProgress(totpData.period)
            delay(1000)
        }
    }
    
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 标题和菜单
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (totpData.issuer.isNotBlank()) {
                        Text(
                            text = totpData.issuer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (totpData.accountName.isNotBlank()) {
                        Text(
                            text = totpData.accountName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isFavorite) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "收藏",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    
                    // 菜单按钮
                    if (onDelete != null) {
                        var expanded by remember { mutableStateOf(false) }
                        
                        Box {
                            IconButton(onClick = { expanded = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "更多"
                                )
                            }
                            
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                // 收藏选项
                                if (onToggleFavorite != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(if (item.isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites)) },
                                        onClick = {
                                            expanded = false
                                            onToggleFavorite(item.id, !item.isFavorite)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (item.isFavorite) Icons.Default.FavoriteBorder else Icons.Default.Favorite,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                }
                                
                                // 上移选项
                                if (onMoveUp != null) {
                                    DropdownMenuItem(
                                        text = { Text("上移") },
                                        onClick = {
                                            expanded = false
                                            onMoveUp()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.KeyboardArrowUp,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                                
                                // 下移选项
                                if (onMoveDown != null) {
                                    DropdownMenuItem(
                                        text = { Text("下移") },
                                        onClick = {
                                            expanded = false
                                            onMoveDown()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.KeyboardArrowDown,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                                
                                DropdownMenuItem(
                                    text = { Text("删除") },
                                    onClick = {
                                        expanded = false
                                        onDelete()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 验证码显示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 验证码（等宽字体）
                Text(
                    text = formatTotpCode(currentCode),
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (remainingSeconds <= 5) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                
                // 复制按钮
                IconButton(
                    onClick = { onCopyCode(currentCode) }
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制验证码"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 进度条和倒计时
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp),
                    color = if (remainingSeconds <= 5) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "${remainingSeconds}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (remainingSeconds <= 5) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * 格式化TOTP验证码（添加空格分隔）
 * 例如: 123456 -> 123 456
 */
private fun formatTotpCode(code: String): String {
    return when (code.length) {
        6 -> "${code.substring(0, 3)} ${code.substring(3)}"
        8 -> "${code.substring(0, 4)} ${code.substring(4)}"
        else -> code
    }
}
