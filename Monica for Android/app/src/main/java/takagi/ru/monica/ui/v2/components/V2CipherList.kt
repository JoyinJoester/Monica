package takagi.ru.monica.ui.v2.components

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.ui.v2.V2ViewModel.UnifiedEntry
import takagi.ru.monica.ui.v2.V2VaultFilter
import takagi.ru.monica.ui.v2.V2VaultSource

/**
 * V2 Cipher 条目列表
 * 
 * 支持多源显示、类型区分、快捷操作
 */
@Composable
fun V2CipherList(
    entries: List<UnifiedEntry>,
    modifier: Modifier = Modifier,
    onEntryClick: (UnifiedEntry) -> Unit = {},
    onCopyUsername: (UnifiedEntry) -> Unit = {},
    onCopyPassword: (UnifiedEntry) -> Unit = {},
    onFavoriteToggle: (UnifiedEntry) -> Unit = {},
    isLoading: Boolean = false
) {
    if (isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    
    if (entries.isEmpty()) {
        V2CipherEmptyState(modifier = modifier)
        return
    }
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = entries,
            key = { it.id }
        ) { entry ->
            V2CipherCard(
                entry = entry,
                onClick = { onEntryClick(entry) },
                onCopyUsername = { onCopyUsername(entry) },
                onCopyPassword = { onCopyPassword(entry) },
                onFavoriteToggle = { onFavoriteToggle(entry) }
            )
        }
        
        // 底部留白
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * 单个 Cipher 卡片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2CipherCard(
    entry: UnifiedEntry,
    onClick: () -> Unit,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 类型图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(getTypeColor(entry.type).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getTypeIcon(entry.type),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = getTypeColor(entry.type)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 标题和副标题
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    // 收藏标记
                    if (entry.isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "收藏",
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    // 数据源标签
                    SourceBadge(source = entry.source)
                    
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    // 副标题（用户名/URL）
                    Text(
                        text = entry.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // 快捷操作按钮
            if (entry.type == V2VaultFilter.LOGIN) {
                IconButton(onClick = onCopyPassword) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "复制密码",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // 更多菜单
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (entry.type == V2VaultFilter.LOGIN) {
                        DropdownMenuItem(
                            text = { Text("复制用户名") },
                            onClick = {
                                showMenu = false
                                onCopyUsername()
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Person, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("复制密码") },
                            onClick = {
                                showMenu = false
                                onCopyPassword()
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Key, contentDescription = null)
                            }
                        )
                        HorizontalDivider()
                    }
                    
                    DropdownMenuItem(
                        text = { Text(if (entry.isFavorite) "取消收藏" else "添加收藏") },
                        onClick = {
                            showMenu = false
                            onFavoriteToggle()
                        },
                        leadingIcon = {
                            Icon(
                                if (entry.isFavorite) Icons.Filled.StarOutline else Icons.Filled.Star,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * 数据源标签
 */
@Composable
fun SourceBadge(
    source: V2VaultSource,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (source) {
        V2VaultSource.LOCAL -> MaterialTheme.colorScheme.secondary to "本地"
        V2VaultSource.BITWARDEN -> MaterialTheme.colorScheme.primary to "BW"
        V2VaultSource.KEEPASS -> MaterialTheme.colorScheme.tertiary to "KP"
        V2VaultSource.ALL -> MaterialTheme.colorScheme.outline to "全部"
    }
    
    if (source != V2VaultSource.ALL) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(4.dp),
            color = color.copy(alpha = 0.15f)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

/**
 * 空状态显示
 */
@Composable
fun V2CipherEmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "没有找到条目",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "尝试调整筛选条件或连接同步服务",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * 获取类型对应的图标
 */
@Composable
fun getTypeIcon(type: V2VaultFilter): ImageVector {
    return when (type) {
        V2VaultFilter.ALL -> Icons.Filled.Folder
        V2VaultFilter.LOGIN -> Icons.Filled.Key
        V2VaultFilter.CARD -> Icons.Filled.CreditCard
        V2VaultFilter.IDENTITY -> Icons.Filled.Person
        V2VaultFilter.NOTE -> Icons.Filled.Note
    }
}

/**
 * 获取类型对应的颜色
 */
@Composable
fun getTypeColor(type: V2VaultFilter): androidx.compose.ui.graphics.Color {
    return when (type) {
        V2VaultFilter.ALL -> MaterialTheme.colorScheme.primary
        V2VaultFilter.LOGIN -> MaterialTheme.colorScheme.primary
        V2VaultFilter.CARD -> MaterialTheme.colorScheme.tertiary
        V2VaultFilter.IDENTITY -> MaterialTheme.colorScheme.secondary
        V2VaultFilter.NOTE -> MaterialTheme.colorScheme.error
    }
}
