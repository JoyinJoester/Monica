package takagi.ru.monica.autofill_ng.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.primaryLinkedAppPackageName
import takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_NONE
import takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_SIMPLE
import takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_UPLOADED
import takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_EMOJI
import takagi.ru.monica.ui.icons.EmojiIconText
import takagi.ru.monica.ui.icons.rememberAutoMatchedSimpleIcon
import takagi.ru.monica.ui.icons.rememberSimpleIconBitmap
import takagi.ru.monica.ui.icons.rememberUploadedPasswordIcon

/**
 * 密码列表项操作类型
 */
sealed class PasswordItemAction {
    /** 自动填充（不保存URI） */
    data class Autofill(val password: PasswordEntry) : PasswordItemAction()
    
    /** User explicitly chooses accessibility regardless of picker entry point. */
    data class AccessibilityFill(val password: PasswordEntry) : PasswordItemAction()

    /** 自动填充并保存应用或网站信息 */
    data class AutofillAndSaveUri(val password: PasswordEntry) : PasswordItemAction()

    /** 仅填充用户名 */
    data class FillUsername(val password: PasswordEntry) : PasswordItemAction()

    /** 仅填充密码 */
    data class FillPassword(val password: PasswordEntry) : PasswordItemAction()

    /** 填充 2FA 验证码 */
    data class FillTotp(val password: PasswordEntry) : PasswordItemAction()
    
    /** 复制用户名 */
    data class CopyUsername(val password: PasswordEntry) : PasswordItemAction()
    
    /** 复制密码 */
    data class CopyPassword(val password: PasswordEntry) : PasswordItemAction()
    
    /** 复制账号，稍后复制密码 */
    data class SmartCopyUsernameFirst(val password: PasswordEntry) : PasswordItemAction()
    
    /** 复制密码，稍后复制账号 */
    data class SmartCopyPasswordFirst(val password: PasswordEntry) : PasswordItemAction()
    
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
 * - 支持 M3E 居中操作弹窗
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
    credentialLabel: String? = null,
    displayUsernameOverride: String? = null,
    showDropdownMenu: Boolean = false,
    iconCardsEnabled: Boolean = false,
    showSmartCopyOptions: Boolean = false,
    onPrepareAutofill: ((PasswordEntry) -> Unit)? = null,
    onAction: ((PasswordItemAction) -> Unit)? = null,
    onItemClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayTitle = password.title.ifEmpty { password.username }.toSafeComposeText()
    val displayUsername = (displayUsernameOverride ?: password.username).toSafeComposeText()
    val displaySubtitle = listOfNotNull(displayUsername.takeIf { it.isNotEmpty() }, credentialLabel).joinToString(" · ")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                if (showDropdownMenu && onAction != null) {
                    onPrepareAutofill?.invoke(password)
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
                iconCardsEnabled = iconCardsEnabled,
                modifier = Modifier.size(48.dp)
            )
            
            // 文本信息区域
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // 标题 (优先显示title,其次username)
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // 用户名 (如果有title则显示username)
                if (displaySubtitle.isNotEmpty()) {
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
                            text = displaySubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
            
            // Shared centered action dialog
            if (showDropdownMenu && onAction != null) {
                if (expanded) {
                    PasswordActionDialog(password, displayUsername, credentialLabel,
                        showSmartCopyOptions, onDismiss = { expanded = false }, onAction = onAction)
                }
            }
        }
    }
}

/**
 * 智能图标组件
 * 
 * 显示逻辑:
 * 1. 图标开关关闭时，始终显示默认钥匙图标
 * 2. 图标开关开启时，优先级与主应用密码列表一致
 *    SIMPLE_ICON -> UPLOADED -> AutoMatched -> Favicon -> AppIcon(app-only) -> DefaultKey
 */
@Composable
private fun AppIconOrFallback(
    password: PasswordEntry,
    iconCardsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (!iconCardsEnabled) {
            DefaultKeyIcon()
        } else {
            val simpleIcon = if (password.customIconType == PASSWORD_ICON_TYPE_SIMPLE) {
                rememberSimpleIconBitmap(
                    slug = password.customIconValue,
                    tintColor = MaterialTheme.colorScheme.primary,
                    enabled = true
                )
            } else {
                null
            }

            val uploadedIcon = if (password.customIconType == PASSWORD_ICON_TYPE_UPLOADED) {
                rememberUploadedPasswordIcon(password.customIconValue)
            } else {
                null
            }
            val emojiIcon = password.customIconValue.takeIf {
                password.customIconType == PASSWORD_ICON_TYPE_EMOJI
            }

            val primaryAppPackageName = password.primaryLinkedAppPackageName()
            val autoMatchedSimpleIcon = rememberAutoMatchedSimpleIcon(
                website = password.website,
                title = password.title,
                appPackageName = primaryAppPackageName,
                tintColor = MaterialTheme.colorScheme.primary,
                enabled = password.customIconType == PASSWORD_ICON_TYPE_NONE
            )

            val favicon = if (password.website.isNotBlank()) {
                rememberFavicon(
                    url = password.website,
                    enabled = autoMatchedSimpleIcon.resolved && autoMatchedSimpleIcon.slug == null
                )
            } else {
                null
            }

            val appIcon = if (
                primaryAppPackageName.isNotBlank() &&
                !isWebAddress(password.website)
            ) {
                rememberAppIcon(primaryAppPackageName)
            } else {
                null
            }

            when {
                emojiIcon != null -> {
                    EmojiIconText(
                        emoji = emojiIcon,
                        size = 26.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                simpleIcon != null -> {
                    Image(
                        bitmap = simpleIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                uploadedIcon != null -> {
                    Image(
                        bitmap = uploadedIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                autoMatchedSimpleIcon.bitmap != null -> {
                    Image(
                        bitmap = autoMatchedSimpleIcon.bitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                favicon != null -> {
                    Image(
                        bitmap = favicon,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                appIcon != null -> {
                    Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                else -> {
                    DefaultKeyIcon()
                }
            }
        }
    }
}

@Composable
private fun DefaultKeyIcon(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
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
    credentialLabel: String? = null,
    displayUsernameOverride: String? = null,
    iconCardsEnabled: Boolean = false,
    showSmartCopyOptions: Boolean = false,
    onPrepareAutofill: ((PasswordEntry) -> Unit)? = null,
    onAction: (PasswordItemAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayTitle = password.title.ifEmpty { password.username }.toSafeComposeText()
    val displayUsername = (displayUsernameOverride ?: password.username).toSafeComposeText()
    val displaySubtitle = listOfNotNull(displayUsername.takeIf { it.isNotEmpty() }, credentialLabel).joinToString(" · ")
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable {
                onPrepareAutofill?.invoke(password)
                expanded = true
            },
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
                    iconCardsEnabled = iconCardsEnabled,
                    modifier = Modifier.size(44.dp)
                )
                
                // 文本信息区域
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (displaySubtitle.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = displaySubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            // Shared centered action dialog
            if (expanded) {
                PasswordActionDialog(password, displayUsername, credentialLabel,
                    showSmartCopyOptions, onDismiss = { expanded = false }, onAction = onAction)
            }
        }
    }
}



