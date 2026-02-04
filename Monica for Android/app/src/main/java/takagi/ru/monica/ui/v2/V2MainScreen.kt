package takagi.ru.monica.ui.v2

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.V2BottomNavTab
import takagi.ru.monica.ui.screens.GeneratorScreen
import takagi.ru.monica.ui.screens.SettingsScreen
import takagi.ru.monica.viewmodel.GeneratorViewModel
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel

/**
 * V2 底部导航项
 * 
 * 简化导航结构：
 * - 密码库：Bitwarden 风格的统一列表
 * - Send：安全分享
 * - 生成器：密码生成
 * - 设置：复用 V1 设置（包含同步与备份）
 */
sealed class V2NavItem(
    val tab: V2BottomNavTab?,
    val icon: ImageVector,
    val labelRes: Int,
    val shortLabelRes: Int
) {
    val key: String = tab?.name ?: "V2_SETTINGS"
    
    // object Vault : V2NavItem(
    //     V2BottomNavTab.VAULT,
    //     Icons.Default.Shield,
    //     R.string.nav_v2_vault,
    //     R.string.nav_v2_vault_short
    // ) - Removed
    
    object Send : V2NavItem(
        V2BottomNavTab.SEND,
        Icons.Default.Send,
        R.string.nav_v2_send,
        R.string.nav_v2_send_short
    )
    
    object Generator : V2NavItem(
        V2BottomNavTab.GENERATOR,
        Icons.Default.AutoAwesome,
        R.string.nav_generator,
        R.string.nav_generator_short
    )
    
    object Settings : V2NavItem(
        null,
        Icons.Default.Settings,
        R.string.nav_settings,
        R.string.nav_settings_short
    )
    
    companion object {
        // 移除 Sync，同步功能在设置->同步与备份中
        val defaultTabs: List<V2NavItem> = listOf(Send, Generator, Settings)
    }
}

/**
 * V2 多源密码库主屏幕
 * 
 * 采用 Bitwarden 风格的导航结构：
 * - 密码库（Vault）：统一的多源密码列表
 * - 发送（Send）：安全分享功能
 * - 生成器（Generator）：复用 V1 生成器
 * - 设置（Settings）：复用 V1 设置（包含同步与备份）
 */
@Composable
fun V2MainScreen(
    settingsViewModel: SettingsViewModel,
    passwordViewModel: PasswordViewModel,
    generatorViewModel: GeneratorViewModel = viewModel(),
    v2ViewModel: V2ViewModel = viewModel(),
    onNavigateToResetPassword: () -> Unit = {},
    onNavigateToSecurityQuestions: () -> Unit = {},
    onNavigateToSyncBackup: () -> Unit = {},
    onNavigateToAutofill: () -> Unit = {},
    onNavigateToPasskeySettings: () -> Unit = {},
    onNavigateToBottomNavSettings: () -> Unit = {},
    onNavigateToColorScheme: () -> Unit = {},
    onSecurityAnalysis: () -> Unit = {},
    onNavigateToDeveloperSettings: () -> Unit = {},
    onNavigateToPermissionManagement: () -> Unit = {},
    onNavigateToMonicaPlus: () -> Unit = {},
    onNavigateToExtensions: () -> Unit = {},
    onNavigateToBitwardenLogin: () -> Unit = {},
    onNavigateToKeePassImport: () -> Unit = {},
    onNavigateToWebDAVSettings: () -> Unit = {},
    onNavigateToAddEntry: (String) -> Unit = {}, // 导航到添加条目页面，参数为类型
    onNavigateToEntryDetail: (String, Long) -> Unit = { _, _ -> }, // 导航到条目详情页面 (类型, ID)
    onClearAllData: (Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 双击返回退出
    var backPressedOnce by remember { mutableStateOf(false) }
    
    BackHandler(enabled = true) {
        if (backPressedOnce) {
            (context as? android.app.Activity)?.finish()
        } else {
            backPressedOnce = true
            Toast.makeText(
                context,
                context.getString(R.string.press_back_again_to_exit),
                Toast.LENGTH_SHORT
            ).show()
            
            scope.launch {
                delay(2000)
                backPressedOnce = false
            }
        }
    }
    
    // 当前选中的 Tab - 使用完全安全的方式
    val tabs: List<V2NavItem> = remember { V2NavItem.defaultTabs }
    var selectedTabKey by rememberSaveable { mutableStateOf(V2NavItem.Send.key) }
    
    // 直接使用非空的 tabs 查找，完全避免 NPE
    val currentTab: V2NavItem = remember(selectedTabKey, tabs) {
        tabs.find { it.key == selectedTabKey } ?: V2NavItem.Send
    }
    
    // 如果 key 无效，重置为 Vault
    LaunchedEffect(selectedTabKey, tabs) {
        val isValidKey = tabs.any { it.key == selectedTabKey }
        if (!isValidKey) {
            selectedTabKey = V2NavItem.Send.key
        }
    }
    
    Scaffold(
        bottomBar = {
            NavigationBar(
                tonalElevation = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEach { item ->
                    val label = stringResource(item.shortLabelRes)
                    NavigationBarItem(
                        icon = {
                            Icon(item.icon, contentDescription = label)
                        },
                        label = {
                            Text(label)
                        },
                        selected = item.key == currentTab.key,
                        onClick = { selectedTabKey = item.key }
                    )
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "V2TabContent",
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { tab ->
            when (tab) {
                /* V2NavItem.Vault -> {
                    V2VaultScreen(
                        viewModel = v2ViewModel,
                        onNavigateToBitwardenLogin = onNavigateToBitwardenLogin,
                        onNavigateToAddEntry = onNavigateToAddEntry,
                        onItemClick = { type, id -> 
                            // 根据类型导航到对应的 V1 编辑页面
                            onNavigateToEntryDetail(type.name, id)
                        }
                    )
                } */
                
                V2NavItem.Send -> {
                    V2SendScreen()
                }
                
                V2NavItem.Generator -> {
                    // 复用 V1 生成器页面
                    GeneratorScreen(
                        onNavigateBack = { selectedTabKey = V2NavItem.Send.key },
                        passwordViewModel = passwordViewModel,
                        viewModel = generatorViewModel
                    )
                }
                
                V2NavItem.Settings -> {
                    // 复用 V1 设置页面
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateBack = { selectedTabKey = V2NavItem.Send.key },
                        onResetPassword = onNavigateToResetPassword,
                        onSecurityQuestions = onNavigateToSecurityQuestions,
                        onNavigateToSyncBackup = onNavigateToSyncBackup,
                        onNavigateToAutofill = onNavigateToAutofill,
                        onNavigateToPasskeySettings = onNavigateToPasskeySettings,
                        onNavigateToBottomNavSettings = onNavigateToBottomNavSettings,
                        onNavigateToColorScheme = onNavigateToColorScheme,
                        onSecurityAnalysis = onSecurityAnalysis,
                        onNavigateToDeveloperSettings = onNavigateToDeveloperSettings,
                        onNavigateToPermissionManagement = onNavigateToPermissionManagement,
                        onNavigateToMonicaPlus = onNavigateToMonicaPlus,
                        onNavigateToExtensions = onNavigateToExtensions,
                        onClearAllData = onClearAllData,
                        showTopBar = false  // V2 底部导航已包含设置入口，隐藏顶栏
                    )
                }
                
                else -> {
                    // 处理其他情况（不应该发生）
                    V2VaultScreen(
                        viewModel = v2ViewModel,
                        onNavigateToBitwardenLogin = onNavigateToBitwardenLogin,
                        onNavigateToAddEntry = onNavigateToAddEntry,
                        onItemClick = { type, id -> 
                            onNavigateToEntryDetail(type.name, id)
                        }
                    )
                }
            }
        }
    }
}
