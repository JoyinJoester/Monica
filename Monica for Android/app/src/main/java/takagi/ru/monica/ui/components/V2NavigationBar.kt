package takagi.ru.monica.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

/**
 * V2 导航栏固定项（位置0、2、3、4）
 */
enum class V2NavItem(
    val key: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    VAULT("vault", R.string.nav_v2_vault, Icons.Default.Shield),
    SEND("send", R.string.nav_v2_send, Icons.AutoMirrored.Filled.Send),
    GENERATOR("generator", R.string.nav_generator, Icons.Default.AutoAwesome),
    SETTINGS("settings", R.string.nav_settings, Icons.Default.Settings)
}

/**
 * 底栏第2项的动态内容类型
 */
enum class RecentSubPage(
    val key: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    PASSWORDS("passwords", R.string.nav_passwords, Icons.Default.Lock),
    AUTHENTICATOR("authenticator", R.string.nav_authenticator, Icons.Default.Security),
    CARD_WALLET("card_wallet", R.string.nav_card_wallet, Icons.Default.Wallet),
    NOTES("notes", R.string.nav_notes, Icons.AutoMirrored.Filled.Note),
    PASSKEY("passkey", R.string.nav_passkey, Icons.Default.Key),
    TIMELINE("timeline", R.string.nav_timeline, Icons.Default.AccountTree)
}

/**
 * V2 导航栏选中位置（0-4）
 */
enum class V2NavPosition {
    VAULT,      // 位置0：库首页
    DYNAMIC,    // 位置1：动态内容（密码/验证器/卡包等）
    SEND,       // 位置2：发送
    GENERATOR,  // 位置3：生成
    SETTINGS    // 位置4：设置
}

/**
 * V2 导航栏 - 使用Material 3标准NavigationBar
 * 
 * @param selectedPosition 当前选中的位置（0-4）
 * @param dynamicContent 第2项显示的动态内容
 * @param onPositionSelected 位置选中回调
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun V2NavigationBar(
    modifier: Modifier = Modifier,
    selectedPosition: V2NavPosition,
    dynamicContent: RecentSubPage?,
    onPositionSelected: (V2NavPosition) -> Unit
) {
    NavigationBar(
        modifier = modifier,
        tonalElevation = 0.dp,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        // 位置0：库
        NavigationBarItem(
            icon = { Icon(V2NavItem.VAULT.icon, contentDescription = null) },
            label = { Text(stringResource(V2NavItem.VAULT.labelRes)) },
            selected = selectedPosition == V2NavPosition.VAULT,
            onClick = { onPositionSelected(V2NavPosition.VAULT) }
        )
        
        // 位置1：动态内容（如果有）
        if (dynamicContent != null) {
            NavigationBarItem(
                icon = { 
                    Box {
                        Icon(dynamicContent.icon, contentDescription = null)
                        // 小圆点标记表示这是动态项
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = (-4).dp, y = (-2).dp)
                                .size(6.dp)
                                .background(
                                    MaterialTheme.colorScheme.tertiary,
                                    CircleShape
                                )
                        )
                    }
                },
                label = { Text(stringResource(dynamicContent.labelRes)) },
                selected = selectedPosition == V2NavPosition.DYNAMIC,
                onClick = { onPositionSelected(V2NavPosition.DYNAMIC) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            )
        }
        
        // 位置2：发送
        NavigationBarItem(
            icon = { Icon(V2NavItem.SEND.icon, contentDescription = null) },
            label = { Text(stringResource(V2NavItem.SEND.labelRes)) },
            selected = selectedPosition == V2NavPosition.SEND,
            onClick = { onPositionSelected(V2NavPosition.SEND) }
        )
        
        // 位置3：生成
        NavigationBarItem(
            icon = { Icon(V2NavItem.GENERATOR.icon, contentDescription = null) },
            label = { Text(stringResource(V2NavItem.GENERATOR.labelRes)) },
            selected = selectedPosition == V2NavPosition.GENERATOR,
            onClick = { onPositionSelected(V2NavPosition.GENERATOR) }
        )
        
        // 位置4：设置
        NavigationBarItem(
            icon = { Icon(V2NavItem.SETTINGS.icon, contentDescription = null) },
            label = { Text(stringResource(V2NavItem.SETTINGS.labelRes)) },
            selected = selectedPosition == V2NavPosition.SETTINGS,
            onClick = { onPositionSelected(V2NavPosition.SETTINGS) }
        )
    }
}
