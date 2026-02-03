package takagi.ru.monica.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.viewmodel.NoteViewModel

/**
 * 库主页 - M3E 设计
 * 
 * 显示：数据库、类型、文件夹
 * 不显示无文件夹的密码列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultHomeScreen(
    modifier: Modifier = Modifier,
    passwordViewModel: PasswordViewModel,
    bankCardViewModel: BankCardViewModel,
    documentViewModel: DocumentViewModel,
    noteViewModel: NoteViewModel,
    onNavigateToPasswords: () -> Unit = {},
    onNavigateToCardWallet: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToPasskey: () -> Unit = {},
    onNavigateToTimeline: () -> Unit = {},
    onNavigateToPasswordDetail: (Long) -> Unit = {},
    onNavigateToBankCardDetail: (Long) -> Unit = {},
    onNavigateToNoteDetail: (Long) -> Unit = {}
) {
    // 收集数据
    val passwords by passwordViewModel.passwordEntries.collectAsState()
    val bankCards by bankCardViewModel.allCards.collectAsState(initial = emptyList())
    val documents by documentViewModel.allDocuments.collectAsState(initial = emptyList())
    val notes by noteViewModel.allNotes.collectAsState(initial = emptyList())
    val categories by passwordViewModel.categories.collectAsState(initial = emptyList())
    
    // 计算各类型数量
    val loginCount = passwords.size
    val cardCount = bankCards.size
    val documentCount = documents.size
    val noteCount = notes.size
    val totalCount = loginCount + cardCount + documentCount + noteCount
    
    // 提取文件夹（分类）和数量
    val foldersWithCounts: List<Pair<String, Int>> = remember(passwords, categories) {
        categories.map { category: takagi.ru.monica.data.Category ->
            val count = passwords.count { pw -> pw.categoryId == category.id }
            category.name to count
        }.filter { pair -> pair.second > 0 } // 只显示有密码的分类
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶栏
        ExpressiveTopBar(
            title = stringResource(R.string.nav_v2_vault),
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            isSearchExpanded = isSearchExpanded,
            onSearchExpandedChange = { isSearchExpanded = it },
            searchHint = stringResource(R.string.search_passwords_hint)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ========== 数据库区域 ==========
            item(key = "section_databases") {
                SectionHeader(
                    title = "数据库",
                    icon = Icons.Outlined.Storage
                )
            }
            
            item(key = "db_monica") {
                DatabaseItem(
                    name = "Monica 本地",
                    icon = Icons.Filled.Home,
                    iconTint = MaterialTheme.colorScheme.primary,
                    itemCount = totalCount,
                    isConnected = true,
                    onClick = onNavigateToPasswords
                )
            }
            
            item(key = "db_keepass") {
                DatabaseItem(
                    name = "KeePass",
                    icon = Icons.Filled.Key,
                    iconTint = Color(0xFF4CAF50), // 绿色
                    itemCount = 0,
                    isConnected = false,
                    onClick = { /* TODO: 打开KeePass */ }
                )
            }
            
            item(key = "db_bitwarden") {
                DatabaseItem(
                    name = "Bitwarden",
                    icon = Icons.Filled.Cloud,
                    iconTint = Color(0xFF175DDC), // Bitwarden 蓝
                    itemCount = 0,
                    isConnected = false,
                    onClick = { /* TODO: 连接Bitwarden */ }
                )
            }
            
            // ========== 类型区域 ==========
            item(key = "section_types") {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = "类型",
                    icon = Icons.Outlined.Category
                )
            }
            
            item(key = "type_logins") {
                TypeItem(
                    name = "登录",
                    icon = Icons.Filled.Person,
                    iconTint = MaterialTheme.colorScheme.primary,
                    count = loginCount,
                    onClick = onNavigateToPasswords
                )
            }
            
            item(key = "type_cards") {
                TypeItem(
                    name = "银行卡",
                    icon = Icons.Filled.CreditCard,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    count = cardCount,
                    onClick = onNavigateToCardWallet
                )
            }
            
            item(key = "type_documents") {
                TypeItem(
                    name = "证件",
                    icon = Icons.Filled.Badge,
                    iconTint = Color(0xFFFF9800), // 橙色
                    count = documentCount,
                    onClick = onNavigateToCardWallet
                )
            }
            
            item(key = "type_notes") {
                TypeItem(
                    name = "安全笔记",
                    icon = Icons.Outlined.Description,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    count = noteCount,
                    onClick = onNavigateToNotes
                )
            }
            
            item(key = "type_passkey") {
                TypeItem(
                    name = "通行密钥",
                    icon = Icons.Filled.Fingerprint,
                    iconTint = MaterialTheme.colorScheme.error,
                    count = 0,
                    onClick = onNavigateToPasskey
                )
            }
            
            // ========== 文件夹区域 ==========
            // 文件夹标题
            if (foldersWithCounts.isNotEmpty()) {
                item(key = "section_folders") {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionHeader(
                        title = "文件夹",
                        icon = Icons.Outlined.Folder
                    )
                }
            }
            
            // 文件夹列表
            items(
                items = foldersWithCounts,
                key = { pair: Pair<String, Int> -> "folder_${pair.first}" }
            ) { pair: Pair<String, Int> ->
                FolderItem(
                    name = pair.first,
                    count = pair.second,
                    onClick = { 
                        // TODO: 导航到该文件夹的密码列表
                        onNavigateToPasswords()
                    }
                )
            }
            
            // 底部间距
            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

/**
 * 区域标题
 */
@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 数据库条目
 */
@Composable
private fun DatabaseItem(
    name: String,
    icon: ImageVector,
    iconTint: Color,
    itemCount: Int,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconTint.copy(alpha = 0.15f),
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
            
            // 名称和状态
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 连接状态指示器
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isConnected) Color(0xFF4CAF50) 
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isConnected) "$itemCount 个条目" else "未连接",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

/**
 * 类型条目
 */
@Composable
private fun TypeItem(
    name: String,
    icon: ImageVector,
    iconTint: Color,
    count: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = iconTint.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 名称
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            
            // 数量
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 文件夹条目
 */
@Composable
private fun FolderItem(
    name: String,
    count: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 文件夹图标
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 名称
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            
            // 数量
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
