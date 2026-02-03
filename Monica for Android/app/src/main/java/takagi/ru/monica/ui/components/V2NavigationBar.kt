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
 * V2 导航栏固定项
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
 * 库的子页面类型
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
 * V2 导航栏 - 使用Material 3标准NavigationBar
 * 
 * @param selectedItem 当前选中的主导航项
 * @param selectedSubPage 当前正在查看的子页面（用于高亮）
 * @param lastSubPage 上次访问的子页面（用于显示快捷入口）
 * @param onItemSelected 主导航项点击回调
 * @param onSubPageSelected 子页面点击回调
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun V2NavigationBar(
    modifier: Modifier = Modifier,
    selectedItem: V2NavItem,
    selectedSubPage: RecentSubPage? = null,
    lastSubPage: RecentSubPage? = null,
    recentSubPages: List<RecentSubPage> = emptyList(),
    onItemSelected: (V2NavItem) -> Unit,
    onSubPageSelected: (RecentSubPage) -> Unit = {}
) {
    // 底栏显示的子页面：优先用当前选中的，否则用上次访问的
    val displaySubPage = selectedSubPage ?: lastSubPage
    val hasSubPage = displaySubPage != null
    // 子页面是否被选中（正在查看子页面内容）
    val isSubPageSelected = selectedItem == V2NavItem.VAULT && selectedSubPage != null
    
    NavigationBar(
        modifier = modifier,
        tonalElevation = 0.dp,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        // 库
        NavigationBarItem(
            icon = { Icon(V2NavItem.VAULT.icon, contentDescription = null) },
            label = { Text(stringResource(V2NavItem.VAULT.labelRes)) },
            selected = selectedItem == V2NavItem.VAULT && selectedSubPage == null,
            onClick = { onItemSelected(V2NavItem.VAULT) }
        )
        
        // 子页面快捷入口（显示上次访问的子页面）
        if (hasSubPage && displaySubPage != null) {
            NavigationBarItem(
                icon = { 
                    Box {
                        Icon(displaySubPage.icon, contentDescription = null)
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
                label = { Text(stringResource(displaySubPage.labelRes)) },
                selected = isSubPageSelected,
                onClick = { onSubPageSelected(displaySubPage) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            )
        }
        
        // 发送
        NavigationBarItem(
            icon = { Icon(V2NavItem.SEND.icon, contentDescription = null) },
            label = { Text(stringResource(V2NavItem.SEND.labelRes)) },
            selected = selectedItem == V2NavItem.SEND,
            onClick = { onItemSelected(V2NavItem.SEND) }
        )
        
        // 生成
        NavigationBarItem(
            icon = { Icon(V2NavItem.GENERATOR.icon, contentDescription = null) },
            label = { Text(stringResource(V2NavItem.GENERATOR.labelRes)) },
            selected = selectedItem == V2NavItem.GENERATOR,
            onClick = { onItemSelected(V2NavItem.GENERATOR) }
        )
        
        // 设置
        NavigationBarItem(
            icon = { Icon(V2NavItem.SETTINGS.icon, contentDescription = null) },
            label = { Text(stringResource(V2NavItem.SETTINGS.labelRes)) },
            selected = selectedItem == V2NavItem.SETTINGS,
            onClick = { onItemSelected(V2NavItem.SETTINGS) }
        )
    }
}
