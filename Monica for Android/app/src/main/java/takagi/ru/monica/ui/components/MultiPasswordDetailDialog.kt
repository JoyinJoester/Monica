package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordEntry

/**
 * 多密码详情对话框
 * 显示除密码外信息相同的多个密码条目
 * 
 * @param passwords 密码列表（信息相同但密码不同）
 * @param onDismiss 关闭对话框回调
 * @param onAddPassword 添加新密码回调
 * @param onEditPassword 编辑单个密码回调
 * @param onDeletePassword 删除单个密码回调
 * @param onToggleFavorite 切换收藏状态回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiPasswordDetailDialog(
    passwords: List<PasswordEntry>,
    onDismiss: () -> Unit,
    onAddPassword: () -> Unit,
    onEditPassword: (PasswordEntry) -> Unit,
    onDeletePassword: (PasswordEntry) -> Unit,
    onToggleFavorite: (PasswordEntry) -> Unit
) {
    val scrollState = rememberScrollState()
    val firstEntry = passwords.first()
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标题栏
                TopAppBar(
                    title = { 
                        Text(
                            text = firstEntry.title,
                            maxLines = 1
                        )
                    },
                    actions = {
                        // 添加密码按钮
                        IconButton(onClick = onAddPassword) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "添加密码",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        // 关闭按钮
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                
                // 内容区域
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 共同信息部分
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "📝 共同信息",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            if (firstEntry.website.isNotEmpty()) {
                                InfoItem(label = "网站", value = firstEntry.website)
                            }
                            if (firstEntry.username.isNotEmpty()) {
                                InfoItem(label = "用户名", value = firstEntry.username)
                            }
                            if (firstEntry.notes.isNotEmpty()) {
                                InfoItem(label = "备注", value = firstEntry.notes)
                            }
                            if (firstEntry.appName.isNotEmpty()) {
                                InfoItem(label = "关联应用", value = firstEntry.appName)
                            }
                        }
                    }
                    
                    // 密码列表
                    Text(
                        text = "🔑 密码列表 (${passwords.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    passwords.forEachIndexed { index, password ->
                        PasswordItemCard(
                            password = password,
                            index = index + 1,
                            onEdit = { onEditPassword(password) },
                            onDelete = { onDeletePassword(password) },
                            onToggleFavorite = { onToggleFavorite(password) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个密码卡片
 */
@Composable
private fun PasswordItemCard(
    password: PasswordEntry,
    index: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val context = LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (password.isFavorite) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题和操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "密码 $index",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (password.isFavorite) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "已收藏",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 收藏按钮
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (password.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "收藏",
                            modifier = Modifier.size(20.dp),
                            tint = if (password.isFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    // 编辑按钮
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "编辑",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // 删除按钮
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // 密码显示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (passwordVisible) password.password else "••••••••",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 显示/隐藏密码
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "隐藏" else "显示",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // 复制密码
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) 
                                as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("password", password.password)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(
                                context,
                                "密码已复制",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "复制",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 信息展示项
 */
@Composable
private fun InfoItem(label: String, value: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
