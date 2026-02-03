package takagi.ru.monica.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.viewmodel.NoteViewModel

/**
 * 统一条目类型
 */
private enum class VaultEntryType { LOGIN, CARD, DOCUMENT, NOTE }

/**
 * 统一条目数据类
 */
private data class UnifiedVaultEntry(
    val id: Long,
    val title: String,
    val subtitle: String,
    val type: VaultEntryType,
    val icon: ImageVector
)

/**
 * 库主页 - 分类导航页面
 * 
 * 显示各类型的条目统计，点击跳转到对应的底栏页面
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
    // 收集各类条目数量
    val passwords by passwordViewModel.passwordEntries.collectAsState()
    val bankCards by bankCardViewModel.allCards.collectAsState(initial = emptyList())
    val documents by documentViewModel.allDocuments.collectAsState(initial = emptyList())
    val notes by noteViewModel.allNotes.collectAsState(initial = emptyList())
    
    // 计算各类型数量
    val loginCount = passwords.size
    val cardCount = bankCards.size
    val documentCount = documents.size
    val noteCount = notes.size
    
    // 合并所有条目用于"无文件夹"显示
    val allEntries = remember(passwords, bankCards, documents, notes) {
        val entries = mutableListOf<UnifiedVaultEntry>()
        
        // 密码条目 (PasswordEntry)
        passwords.forEach { entry ->
            entries.add(UnifiedVaultEntry(
                id = entry.id,
                title = entry.title,
                subtitle = entry.username.orEmpty(),
                type = VaultEntryType.LOGIN,
                icon = Icons.Default.Language
            ))
        }
        
        // 银行卡 (SecureItem)
        bankCards.forEach { card ->
            entries.add(UnifiedVaultEntry(
                id = card.id,
                title = card.title,
                subtitle = "",
                type = VaultEntryType.CARD,
                icon = Icons.Default.CreditCard
            ))
        }
        
        // 笔记 (SecureItem)
        notes.forEach { note ->
            entries.add(UnifiedVaultEntry(
                id = note.id,
                title = note.title,
                subtitle = "",
                type = VaultEntryType.NOTE,
                icon = Icons.Outlined.Description
            ))
        }
        
        entries.sortedBy { it.title.lowercase() }
    }
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            ExpressiveTopBar(
                title = stringResource(R.string.nav_v2_vault),
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                isSearchExpanded = isSearchExpanded,
                onSearchExpandedChange = { isSearchExpanded = it },
                searchHint = stringResource(R.string.search_passwords_hint),
                actions = {
                    // 可以添加更多操作按钮
                }
            )
        },
        floatingActionButton = {
            // 暂时不需要 FAB，条目创建在各自页面
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 类型区域标题
            item {
                Text(
                    text = "类型（5）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            
            // 类型卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column {
                        // 登录
                        CategoryRow(
                            icon = Icons.Default.Language,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "登录",
                            count = loginCount,
                            onClick = onNavigateToPasswords,
                            showDivider = true
                        )
                        
                        // 卡包（银行卡 + 证件）
                        CategoryRow(
                            icon = Icons.Default.Wallet,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            title = "卡包",
                            count = cardCount + documentCount,
                            onClick = onNavigateToCardWallet,
                            showDivider = true
                        )
                        
                        // 安全笔记
                        CategoryRow(
                            icon = Icons.Outlined.Description,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            title = "安全笔记",
                            count = noteCount,
                            onClick = onNavigateToNotes,
                            showDivider = true
                        )
                        
                        // 通行密钥 (SSH 密钥的位置)
                        CategoryRow(
                            icon = Icons.Default.Key,
                            iconTint = MaterialTheme.colorScheme.error,
                            title = "通行密钥",
                            count = 0, // TODO: 从 PasskeyViewModel 获取
                            onClick = onNavigateToPasskey,
                            showDivider = true
                        )
                        
                        // 回收站
                        CategoryRow(
                            icon = Icons.Default.Delete,
                            iconTint = MaterialTheme.colorScheme.outline,
                            title = "回收站",
                            count = null, // 不显示数量
                            onClick = onNavigateToTimeline,
                            showDivider = false
                        )
                    }
                }
            }
            
            // 无文件夹区域标题
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "无文件夹（${allEntries.size}）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            
            // 条目列表卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column {
                        val filteredEntries = if (searchQuery.isBlank()) {
                            allEntries
                        } else {
                            allEntries.filter { 
                                it.title.contains(searchQuery, ignoreCase = true) ||
                                it.subtitle.contains(searchQuery, ignoreCase = true)
                            }
                        }
                        
                        filteredEntries.forEachIndexed { index, entry ->
                            EntryRow(
                                icon = entry.icon,
                                title = entry.title,
                                subtitle = entry.subtitle,
                                onClick = {
                                    when (entry.type) {
                                        VaultEntryType.LOGIN -> onNavigateToPasswordDetail(entry.id)
                                        VaultEntryType.CARD -> onNavigateToBankCardDetail(entry.id)
                                        VaultEntryType.DOCUMENT -> onNavigateToBankCardDetail(entry.id)
                                        VaultEntryType.NOTE -> onNavigateToNoteDetail(entry.id)
                                    }
                                },
                                showDivider = index < filteredEntries.size - 1
                            )
                        }
                        
                        if (filteredEntries.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isBlank()) "暂无条目" else "未找到匹配项",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // 底部间距
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

/**
 * 分类行
 */
@Composable
private fun CategoryRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    count: Int?,
    onClick: () -> Unit,
    showDivider: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 标题
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            
            // 数量
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * 条目行
 */
@Composable
private fun EntryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showDivider: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 标题和副标题
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // 更多按钮
            IconButton(
                onClick = { /* TODO: 显示更多选项 */ },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}
