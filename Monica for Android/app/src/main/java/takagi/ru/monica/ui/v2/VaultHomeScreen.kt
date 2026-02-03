package takagi.ru.monica.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.viewmodel.NoteViewModel

/**
 * 库主页 - 分类导航页面
 * 
 * 显示 Bitwarden 状态、各类型的条目统计，点击跳转到对应的底栏页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultHomeScreen(
    modifier: Modifier = Modifier,
    passwordViewModel: PasswordViewModel,
    bankCardViewModel: BankCardViewModel,
    documentViewModel: DocumentViewModel,
    noteViewModel: NoteViewModel,
    v2ViewModel: V2ViewModel = viewModel(),
    onNavigateToPasswords: () -> Unit = {},
    onNavigateToCardWallet: () -> Unit = {}, // 保持兼容，虽然现在可能拆分了，但底栏Tab还是同一个
    onNavigateToNotes: () -> Unit = {},
    onNavigateToPasskey: () -> Unit = {},
    onNavigateToTimeline: () -> Unit = {},
    onNavigateToPasswordDetail: (Long) -> Unit = {},
    onNavigateToBankCardDetail: (Long) -> Unit = {},
    onNavigateToNoteDetail: (Long) -> Unit = {}
) {
    // 收集各类条目数量
    val passwords by passwordViewModel.passwordEntries.collectAsState()
    val bankCards by bankCardViewModel.allCards.collectAsState(initial = emptyList())
    val documents by documentViewModel.allDocuments.collectAsState(initial = emptyList())
    val notes by noteViewModel.allNotes.collectAsState(initial = emptyList())
    
    // 收集 Bitwarden 状态
    val dataSources by v2ViewModel.dataSources.collectAsState()
    val syncState by v2ViewModel.syncState.collectAsState()
    val bitwardenSource = dataSources.find { it.id == "bitwarden" }
    
    // 计算各类型数量
    val loginCount = passwords.size
    val cardCount = bankCards.size
    val documentCount = documents.size
    val noteCount = notes.size

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ExpressiveTopBar(
            title = stringResource(R.string.nav_v2_vault),
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            isSearchExpanded = isSearchExpanded,
            onSearchExpandedChange = { isSearchExpanded = it },
            searchHint = stringResource(R.string.search_passwords_hint),
            actions = {
                IconButton(onClick = { v2ViewModel.refresh() }) {
                    Icon(Icons.Default.Sync, contentDescription = "刷新")
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Bitwarden 状态卡片
            item(key = "status_card") {
                VaultStatusCard(
                    bitwardenSource = bitwardenSource,
                    syncState = syncState,
                    onConnect = { v2ViewModel.connectBitwarden() },
                    onSync = { v2ViewModel.syncDataSource("bitwarden") }
                )
            }
            
            // 2. 分类标题
            item(key = "categories_header") {
                Text(
                    text = "分类",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }

            // 3. 分类列表 - 扁平化处理
            item(key = "nav_login") {
                VaultNavigationItem(
                    icon = Icons.Default.Language,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "登录",
                    subtitle = "$loginCount 个帐号",
                    onClick = onNavigateToPasswords
                )
            }
            
            item(key = "nav_cards") {
                VaultNavigationItem(
                    icon = Icons.Default.CreditCard,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    title = "银行卡",
                    subtitle = "$cardCount 张卡片",
                    onClick = onNavigateToCardWallet
                )
            }
            
            item(key = "nav_docs") {
                VaultNavigationItem(
                    icon = Icons.Default.Badge,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    title = "证件",
                    subtitle = "$documentCount 个证件",
                    onClick = onNavigateToCardWallet
                )
            }
            
            item(key = "nav_notes") {
                VaultNavigationItem(
                    icon = Icons.Outlined.Description,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    title = "安全笔记",
                    subtitle = "$noteCount 条笔记",
                    onClick = onNavigateToNotes
                )
            }
            
            item(key = "nav_passkey") {
                VaultNavigationItem(
                    icon = Icons.Default.Key,
                    iconTint = MaterialTheme.colorScheme.error,
                    title = "通行密钥",
                    subtitle = "管理 Passkey",
                    onClick = onNavigateToPasskey
                )
            }
            
            item(key = "nav_trash") {
                VaultNavigationItem(
                    icon = Icons.Default.Delete,
                    iconTint = MaterialTheme.colorScheme.outline,
                    title = "回收站",
                    subtitle = "已删除的项目",
                    onClick = onNavigateToTimeline
                )
            }
            
            // 底部间距
            item(key = "spacer") {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

/**
 * 密码库状态卡片
 */
@Composable
private fun VaultStatusCard(
    bitwardenSource: V2ViewModel.DataSourceState?,
    syncState: V2SyncState,
    onConnect: () -> Unit,
    onSync: () -> Unit
) {
    val isConnected = bitwardenSource?.isConnected == true
    val isSyncing = syncState is V2SyncState.Syncing && syncState.sourceId == "bitwarden"
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp), // 更圆润的角
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 状态图标
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = if (isConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isConnected) Icons.Default.CloudQueue else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (isConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isConnected) "Bitwarden 已连接" else "Bitwarden 未连接",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface 
                    )
                    
                    if (isConnected && bitwardenSource != null) {
                         val lastSync = bitwardenSource.lastSyncTime
                         val timeText = if (lastSync != null && lastSync > 0) {
                             java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSync))
                         } else {
                             "从未同步"
                         }
                         Text(
                            text = "上次同步: $timeText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    } else {
                        Text(
                            text = "点击连接以同步您的密码",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 操作按钮
                if (isConnected) {
                    IconButton(
                        onClick = onSync,
                        enabled = !isSyncing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "同步",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("连接")
                    }
                }
            }
            
            // 如果已连接，显示统计信息
            if (isConnected && bitwardenSource != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)) // 半透明背景
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "云端条目",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "${bitwardenSource.itemCount}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "状态",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "已同步", // 这里可以根据 actual logic 判断
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * 导航条目 (替代原 CategoryRow)
 */
@Composable
private fun VaultNavigationItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow, // 浅色背景
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconTint.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 文本
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 箭头
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
