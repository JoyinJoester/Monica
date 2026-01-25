package takagi.ru.monica.autofill.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.PasswordEntry

/**
 * 密码列表项操作类型
 */
sealed class PasswordItemAction {
    /** 自动填充（不保存URI） */
    data class Autofill(val password: PasswordEntry) : PasswordItemAction()
    
    /** 自动填充并保存应用或网站信息 */
    data class AutofillAndSaveUri(val password: PasswordEntry) : PasswordItemAction()
    
    /** 复制用户名 */
    data class CopyUsername(val password: PasswordEntry) : PasswordItemAction()
    
    /** 复制密码 */
    data class CopyPassword(val password: PasswordEntry) : PasswordItemAction()
    
    /** 查看详情 */
    data class ViewDetails(val password: PasswordEntry) : PasswordItemAction()
}

/**
 * 全新设计的密码列表项
 * 
 * 设计特点:
 * - 更大的触摸区域 (最小56dp高度)
 * - 清晰的视觉层级
 * - 美观的图标展示
 * - 支持 Dropdown 菜单选择操作
 * 
 * @param password 密码条目
 * @param showDropdownMenu 是否显示 Dropdown 菜单模式
 * @param onAction 操作回调（Dropdown模式）
 * @param onItemClick 点击回调（简单模式）
 * @param modifier 修饰符
 */
@Composable
fun PasswordListItem(
    password: PasswordEntry,
    showDropdownMenu: Boolean = false,
    onAction: ((PasswordItemAction) -> Unit)? = null,
    onItemClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                if (showDropdownMenu && onAction != null) {
                    expanded = true
                } else {
                    onItemClick?.invoke()
                }
            },
        color = MaterialTheme.colorScheme.surface
    ) {
        Box {
            Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 图标区域
            AppIconOrFallback(
                password = password,
                modifier = Modifier.size(48.dp)
            )
            
            // 文本信息区域
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // 标题 (优先显示title,其次username)
                Text(
                    text = password.title.ifEmpty { password.username },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // 用户名 (如果有title则显示username)
                if (password.title.isNotEmpty() && password.username.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = password.username,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
            
            // Dropdown 菜单
            if (showDropdownMenu && onAction != null) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    // 自动填充
                    DropdownMenuItem(
                        text = { Text("自动填充") },
                        leadingIcon = {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            onAction(PasswordItemAction.Autofill(password))
                        }
                    )
                    
                    // 自动填充并保存URI
                    DropdownMenuItem(
                        text = { Text("自动填充并保存URI") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Save, contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            onAction(PasswordItemAction.AutofillAndSaveUri(password))
                        }
                    )
                    
                    HorizontalDivider()
                    
                    // 查看详情
                    DropdownMenuItem(
                        text = { Text("查看详情") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Info, contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            onAction(PasswordItemAction.ViewDetails(password))
                        }
                    )
                }
            }
        }
    }
}

/**
 * 智能图标组件
 * 
 * 显示逻辑:
 * 1. 如果有应用包名,尝试加载应用图标
 * 2. 如果没有应用图标,显示首字母头像
 * 3. 如果都没有,显示默认钥匙图标
 */
@Composable
private fun AppIconOrFallback(
    password: PasswordEntry,
    modifier: Modifier = Modifier
) {
    android.util.Log.d("PasswordListItem", "AppIconOrFallback: title=${password.title}, appPackageName=${password.appPackageName}")
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when {
            // 尝试加载应用图标
            !password.appPackageName.isNullOrBlank() -> {
                android.util.Log.d("PasswordListItem", "AppIconOrFallback: trying to load app icon for ${password.appPackageName}")
                val icon = rememberAppIcon(password.appPackageName)
                android.util.Log.d("PasswordListItem", "AppIconOrFallback: icon loaded = ${icon != null}")
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    // 降级到首字母头像
                    InitialsAvatar(text = password.title.ifEmpty { password.username })
                }
            }
            // 显示首字母头像
            password.title.isNotEmpty() || password.username.isNotEmpty() -> {
                InitialsAvatar(text = password.title.ifEmpty { password.username })
            }
            // 默认钥匙图标
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * 首字母头像
 * 
 * 显示文本的第一个字符作为头像
 * 使用Material You配色方案
 */
@Composable
private fun InitialsAvatar(
    text: String,
    modifier: Modifier = Modifier
) {
    val initial = text.firstOrNull()?.uppercase() ?: "?"
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/**
 * 应用图标组件 (保持向后兼容)
 * 
 * @param packageName 应用包名
 * @param modifier 修饰符
 */
@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier
) {
    val icon = rememberAppIcon(packageName)
    
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(12.dp))
        )
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 建议密码列表项 - 高亮样式
 * 
 * 用于显示匹配当前上下文的建议密码，使用强调色背景
 */
@Composable
fun SuggestedPasswordListItem(
    password: PasswordEntry,
    onAction: (PasswordItemAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { expanded = true },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 图标区域
                AppIconOrFallback(
                    password = password,
                    modifier = Modifier.size(44.dp)
                )
                
                // 文本信息区域
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = password.title.ifEmpty { password.username },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (password.title.isNotEmpty() && password.username.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = password.username,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            // Dropdown 菜单 - 参考 Keyguard 样式
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // 自动填充
                DropdownMenuItem(
                    text = { Text("自动填充") },
                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onAction(PasswordItemAction.Autofill(password))
                    }
                )
                
                // 自动填充并保存应用或网站信息
                DropdownMenuItem(
                    text = { Text("自动填充并保存应用或网站信息") },
                    leadingIcon = { Icon(Icons.Outlined.Save, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onAction(PasswordItemAction.AutofillAndSaveUri(password))
                    }
                )
                
                HorizontalDivider()
                
                // 复制用户名
                DropdownMenuItem(
                    text = { 
                        Column {
                            Text("复制用户名")
                            Text(
                                text = password.username,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onAction(PasswordItemAction.CopyUsername(password))
                    }
                )
                
                // 复制密码
                DropdownMenuItem(
                    text = { Text("复制密码") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onAction(PasswordItemAction.CopyPassword(password))
                    }
                )
                
                HorizontalDivider()
                
                // 查看详情
                DropdownMenuItem(
                    text = { Text("查看详情") },
                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    trailingIcon = { 
                        Icon(
                            Icons.Default.KeyboardArrowRight, 
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        ) 
                    },
                    onClick = {
                        expanded = false
                        onAction(PasswordItemAction.ViewDetails(password))
                    }
                )
            }
        }
    }
}
