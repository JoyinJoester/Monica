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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.PasswordEntry

/**
 * 全新设计的密码列表项
 * 
 * 设计特点:
 * - 更大的触摸区域 (最小56dp高度)
 * - 清晰的视觉层级
 * - 美观的图标展示
 * - 良好的间距和留白
 * 
 * @param password 密码条目
 * @param onItemClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun PasswordListItem(
    password: PasswordEntry,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick),
        color = MaterialTheme.colorScheme.surface
    ) {
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

