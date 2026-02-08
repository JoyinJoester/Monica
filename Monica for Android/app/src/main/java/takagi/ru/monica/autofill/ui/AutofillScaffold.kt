package takagi.ru.monica.autofill.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import takagi.ru.monica.R

/**
 * 自动填充布局骨架
 * 参考 Keyguard 的 ExtensionScaffold 设计
 * 
 * 顶部: 深色背景的 Header 区域（surfaceVariant）
 * 底部: 圆角背景色的内容区域
 * 
 * @param topBar 顶部栏内容
 * @param content 主内容区域
 */
@Composable
fun AutofillScaffold(
    topBar: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .statusBarsPadding()
    ) {
        // 顶部区域 - 深色背景
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            topBar()
        }
        
        // 内容区域 - 圆角顶部，参考 Keyguard 的 extraLarge 圆角
        val shape = RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )
        Box(
            modifier = Modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            content()
        }
    }
}

/**
 * AutofillScaffold 的简化版本
 * 自动显示应用图标和名称，适用于添加密码页面
 * 
 * @param packageName 应用包名
 * @param webDomain 网站域名
 * @param appIcon 应用图标
 * @param appName 应用名称
 * @param showClose 是否显示关闭按钮
 * @param onClose 关闭按钮回调
 */
@Composable
fun AutofillScaffold(
    packageName: String?,
    webDomain: String?,
    appIcon: Drawable? = null,
    appName: String? = null,
    showClose: Boolean = false,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AutofillScaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppInfo(
                    packageName = packageName,
                    webDomain = webDomain,
                    appIcon = appIcon,
                    appName = appName
                )
                
                if (showClose && onClose != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    TextButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.close),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        content = content
    )
}

/**
 * 自动填充顶部信息栏
 * 参考 Keyguard 的 AutofillActivity 顶部设计
 * 
 * 显示：应用/网站信息 + 关闭按钮
 */
@Composable
fun AutofillHeader(
    title: String,
    username: String? = null,
    password: String? = null,
    applicationId: String? = null,
    webDomain: String? = null,
    appIcon: android.graphics.drawable.Drawable? = null,
    appName: String? = null,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // 主行: AppInfo + 关闭按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧: AppInfo 组件
            Column(
                modifier = Modifier.weight(1f)
            ) {
                AppInfo(
                    packageName = applicationId,
                    webDomain = webDomain,
                    appIcon = appIcon,
                    appName = appName
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 右侧: 关闭按钮
            TextButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.close),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // 如果有捕获的凭据信息，显示预览（仅在保存模式下）
        if (!username.isNullOrEmpty() || !password.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // 用户名
            if (!username.isNullOrEmpty()) {
                TwoColumnRow(
                    title = stringResource(R.string.autofill_username),
                    value = username
                )
            }
            
            // 密码（彩色显示）
            if (!password.isNullOrEmpty()) {
                TwoColumnRow(
                    title = stringResource(R.string.autofill_password),
                    value = colorizePassword(password)
                )
            }
        }
    }
}

/**
 * 两列布局行
 */
@Composable
private fun TwoColumnRow(
    title: String,
    value: CharSequence,
    isSecondary: Boolean = false
) {
    val contentColor = if (isSecondary) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val textStyle = if (isSecondary) {
        MaterialTheme.typography.bodySmall
    } else {
        MaterialTheme.typography.bodyMedium
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = title,
            style = textStyle,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.widthIn(max = 80.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        when (value) {
            is String -> Text(
                text = value,
                style = textStyle,
                color = contentColor,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            is AnnotatedString -> Text(
                text = value,
                style = textStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 密码彩色化显示
 * 数字使用主色，特殊字符使用第三色，字母使用普通颜色
 * 参考 Keyguard 的密码显示风格
 */
@Composable
fun colorizePassword(password: String): AnnotatedString {
    val digitColor = MaterialTheme.colorScheme.primary
    val specialColor = MaterialTheme.colorScheme.tertiary
    val normalColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    return buildAnnotatedString {
        password.forEach { char ->
            val color = when {
                char.isDigit() -> digitColor
                !char.isLetterOrDigit() -> specialColor
                else -> normalColor
            }
            withStyle(SpanStyle(color = color)) {
                append(char)
            }
        }
    }
}

/**
 * 密码彩色化显示（别名，保持兼容性）
 */
@Composable
fun colorizePasswordString(password: String): AnnotatedString {
    return colorizePassword(password)
}

/**
 * AppInfo 组件
 * 参考 Keyguard 的 AppInfo 设计
 * 
 * 显示应用图标和名称/包名，或网站域名
 */
@Composable
fun AppInfo(
    packageName: String?,
    webDomain: String?,
    appIcon: Drawable? = null,
    appName: String? = null
) {
    val context = LocalContext.current
    
    // 尝试获取应用图标和名称
    val (resolvedIcon, resolvedName) = remember(packageName, appIcon, appName) {
        if (appIcon != null && appName != null) {
            appIcon to appName
        } else if (packageName != null) {
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val icon = pm.getApplicationIcon(appInfo)
                val name = pm.getApplicationLabel(appInfo).toString()
                icon to name
            } catch (e: Exception) {
                null to null
            }
        } else {
            null to null
        }
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 应用图标或首字母头像
        // 优先使用 Favicon (如果未来支持)，目前使用 App Icon
        if (resolvedIcon != null) {
            Image(
                painter = rememberDrawablePainter(drawable = resolvedIcon),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            // 首字母头像
            // 优先使用 webDomain 的首字母
            val displayText = webDomain ?: resolvedName ?: packageName?.substringAfterLast('.') ?: stringResource(R.string.app_name)
            val initial = displayText.firstOrNull()?.uppercaseChar() ?: 'M'
            
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initial.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            // "为...自动填充" 标签
            Text(
                text = stringResource(R.string.autofill_for_app),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            
            // 标题优先级：WebDomain > AppName > PackageName
            val mainTitle = webDomain ?: resolvedName ?: packageName?.substringAfterLast('.')?.replaceFirstChar { it.uppercaseChar() } ?: stringResource(R.string.app_name)
            
            // 应用名称
            Text(
                text = mainTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // 副标题：如果有 WebDomain，则显示 AppName/PackageName 作为来源
            if (webDomain != null) {
                val subTitle = resolvedName ?: packageName
                if (subTitle != null) {
                    Text(
                        text = stringResource(R.string.autofill_source_with_value, subTitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                }
            } else if (packageName != null && resolvedName != null && packageName != resolvedName) {
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
            }
        }
    }
}
