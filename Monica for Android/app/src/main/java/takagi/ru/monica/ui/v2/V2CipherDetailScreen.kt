package takagi.ru.monica.ui.v2

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.ui.v2.components.getTypeColor
import takagi.ru.monica.ui.v2.components.getTypeIcon

/**
 * V2 Cipher 详情页面
 * 
 * 支持 Login、Card、Identity、Note、SSH Key 等类型的详情展示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2CipherDetailScreen(
    entry: PasswordEntry?,
    source: V2VaultSource,
    onNavigateBack: () -> Unit,
    onEdit: (Long) -> Unit = {},
    onDelete: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    if (entry == null) {
        // 加载中或找不到条目
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    
    // 确定条目类型
    val entryType = when (entry.bitwardenCipherType) {
        1 -> V2VaultFilter.LOGIN
        2 -> V2VaultFilter.NOTE
        3 -> V2VaultFilter.CARD
        4 -> V2VaultFilter.IDENTITY
        else -> V2VaultFilter.LOGIN
    }
    
    // 删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("确认删除") },
            text = { Text("确定要删除 \"${entry.title}\" 吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(entry.id)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (entryType) {
                                V2VaultFilter.LOGIN -> "登录凭证"
                                V2VaultFilter.CARD -> "支付卡"
                                V2VaultFilter.IDENTITY -> "身份信息"
                                V2VaultFilter.NOTE -> "安全笔记"
                                else -> "条目"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    // 编辑按钮（仅本地条目可编辑）
                    if (source == V2VaultSource.LOCAL) {
                        IconButton(onClick = { onEdit(entry.id) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "编辑"
                            )
                        }
                    }
                    
                    // 删除按钮
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 条目图标和基本信息
            DetailHeader(
                title = entry.title,
                website = entry.website,
                type = entryType,
                isFavorite = entry.isFavorite,
                source = source
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 根据类型显示不同的详情内容
            when (entryType) {
                V2VaultFilter.LOGIN -> LoginDetailContent(entry, context)
                V2VaultFilter.CARD -> CardDetailContent(entry, context)
                V2VaultFilter.IDENTITY -> IdentityDetailContent(entry, context)
                V2VaultFilter.NOTE -> NoteDetailContent(entry)
                else -> LoginDetailContent(entry, context) // 默认显示登录类型
            }
            
            // 备注区域
            if (!entry.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                NotesSection(notes = entry.notes!!)
            }
            
            // 元数据
            Spacer(modifier = Modifier.height(16.dp))
            MetadataSection(entry = entry, source = source)
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 详情页头部
 */
@Composable
private fun DetailHeader(
    title: String,
    website: String?,
    type: V2VaultFilter,
    isFavorite: Boolean,
    source: V2VaultSource
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 类型图标
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(getTypeColor(type).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getTypeIcon(type),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = getTypeColor(type)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "收藏",
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                if (!website.isNullOrBlank()) {
                    Text(
                        text = website,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 来源标识
            Icon(
                imageVector = source.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 登录凭证详情内容
 */
@Composable
private fun LoginDetailContent(entry: PasswordEntry, context: Context) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        // 用户名
        if (!entry.username.isNullOrBlank()) {
            CopyableField(
                label = "用户名",
                value = entry.username!!,
                icon = Icons.Outlined.Person,
                onCopy = { copyToClipboard(context, "用户名", entry.username!!) }
            )
        }
        
        // 密码
        if (!entry.password.isNullOrBlank()) {
            PasswordField(
                label = "密码",
                value = entry.password!!,
                onCopy = { copyToClipboard(context, "密码", entry.password!!) }
            )
        }
        
        // TOTP
        if (!entry.authenticatorKey.isNullOrBlank()) {
            CopyableField(
                label = "验证器 (TOTP)",
                value = "已配置",
                icon = Icons.Outlined.QrCode,
                onCopy = { copyToClipboard(context, "TOTP 密钥", entry.authenticatorKey!!) }
            )
        }
        
        // 网站
        if (!entry.website.isNullOrBlank()) {
            CopyableField(
                label = "网站",
                value = entry.website!!,
                icon = Icons.Outlined.Link,
                onCopy = { copyToClipboard(context, "网站", entry.website!!) }
            )
        }
    }
}

/**
 * 支付卡详情内容
 */
@Composable
private fun CardDetailContent(entry: PasswordEntry, context: Context) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        // 使用通用字段显示卡片信息
        // 实际的卡片数据存储在 customFields 或专用字段中
        CopyableField(
            label = "持卡人",
            value = entry.title,
            icon = Icons.Outlined.Person,
            onCopy = { copyToClipboard(context, "持卡人", entry.title) }
        )
        
        if (!entry.username.isNullOrBlank()) {
            PasswordField(
                label = "卡号",
                value = entry.username!!,
                onCopy = { copyToClipboard(context, "卡号", entry.username!!) }
            )
        }
        
        if (!entry.password.isNullOrBlank()) {
            PasswordField(
                label = "CVV",
                value = entry.password!!,
                onCopy = { copyToClipboard(context, "CVV", entry.password!!) }
            )
        }
    }
}

/**
 * 身份信息详情内容
 */
@Composable
private fun IdentityDetailContent(entry: PasswordEntry, context: Context) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        CopyableField(
            label = "姓名",
            value = entry.title,
            icon = Icons.Outlined.Person,
            onCopy = { copyToClipboard(context, "姓名", entry.title) }
        )
        
        if (!entry.username.isNullOrBlank()) {
            CopyableField(
                label = "邮箱",
                value = entry.username!!,
                icon = Icons.Outlined.Email,
                onCopy = { copyToClipboard(context, "邮箱", entry.username!!) }
            )
        }
    }
}

/**
 * 安全笔记详情内容
 */
@Composable
private fun NoteDetailContent(entry: PasswordEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Note,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "安全笔记",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = entry.notes ?: "无内容",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * 备注区域
 */
@Composable
private fun NotesSection(notes: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Notes,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "备注",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * 元数据区域
 */
@Composable
private fun MetadataSection(entry: PasswordEntry, source: V2VaultSource) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "详细信息",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            MetadataRow(label = "来源", value = when (source) {
                V2VaultSource.LOCAL -> "本地密码库"
                V2VaultSource.BITWARDEN -> "Bitwarden"
                V2VaultSource.KEEPASS -> "KeePass"
                V2VaultSource.ALL -> "未知"
            })
            
            MetadataRow(
                label = "创建时间",
                value = formatDateTime(entry.createdAt.time)
            )
            
            MetadataRow(
                label = "修改时间",
                value = formatDateTime(entry.updatedAt.time)
            )
            
            if (entry.bitwardenCipherId != null) {
                MetadataRow(
                    label = "Bitwarden ID",
                    value = entry.bitwardenCipherId!!.take(8) + "..."
                )
            }
        }
    }
}

/**
 * 元数据行
 */
@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * 可复制字段
 */
@Composable
private fun CopyableField(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCopy)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "复制",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 密码字段（带显示/隐藏）
 */
@Composable
private fun PasswordField(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isVisible) value else "••••••••",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            
            IconButton(onClick = { isVisible = !isVisible }) {
                Icon(
                    imageVector = if (isVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (isVisible) "隐藏" else "显示",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "复制",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 复制到剪贴板
 */
private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, value)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label 已复制", Toast.LENGTH_SHORT).show()
}

/**
 * 格式化日期时间
 */
private fun formatDateTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
