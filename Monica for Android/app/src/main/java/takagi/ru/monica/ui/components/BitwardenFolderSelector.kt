package takagi.ru.monica.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Bitwarden 文件夹项目数据
 */
data class BitwardenFolderItem(
    val id: String,
    val name: String,
    val isLinked: Boolean = false // 是否已被其他分类关联
)

/**
 * Bitwarden 保险库项目数据
 */
data class BitwardenVaultItem(
    val id: Long,
    val name: String,
    val serverUrl: String
)

/**
 * Bitwarden 关联状态卡片
 * 
 * 用于在分类编辑页面显示 Bitwarden 关联状态
 */
@Composable
fun BitwardenLinkCard(
    isLinked: Boolean,
    vaultName: String?,
    folderName: String?,
    syncTypes: List<String>,
    onLinkClick: () -> Unit,
    onUnlinkClick: () -> Unit,
    onConfigureSyncTypesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLinked) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isLinked) Icons.Default.CloudSync else Icons.Default.FolderOff,
                    contentDescription = null,
                    tint = if (isLinked) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isLinked) "已关联 Bitwarden" else "未关联 Bitwarden",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (isLinked && vaultName != null) {
                        Text(
                            text = buildString {
                                append(vaultName)
                                if (folderName != null) {
                                    append(" / $folderName")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            if (isLinked) {
                // 同步类型标签
                if (syncTypes.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        syncTypes.take(4).forEach { type ->
                            SuggestionChip(
                                onClick = onConfigureSyncTypesClick,
                                label = { 
                                    Text(
                                        text = getSyncTypeDisplayName(type),
                                        style = MaterialTheme.typography.labelSmall
                                    ) 
                                }
                            )
                        }
                        if (syncTypes.size > 4) {
                            SuggestionChip(
                                onClick = onConfigureSyncTypesClick,
                                label = { Text("+${syncTypes.size - 4}") }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "同步所有类型",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 操作按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onConfigureSyncTypesClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("配置同步")
                    }
                    
                    TextButton(
                        onClick = onUnlinkClick,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("解除关联")
                    }
                }
            } else {
                // 未关联状态的操作按钮
                FilledTonalButton(
                    onClick = onLinkClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("关联到 Bitwarden 文件夹")
                }
            }
        }
    }
}

/**
 * Bitwarden 文件夹选择器对话框
 */
@Composable
fun BitwardenFolderSelectorDialog(
    vaults: List<BitwardenVaultItem>,
    folders: List<BitwardenFolderItem>,
    selectedVaultId: Long?,
    selectedFolderId: String?,
    isLoading: Boolean,
    onVaultSelected: (Long) -> Unit,
    onFolderSelected: (String) -> Unit,
    onCreateNewFolder: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 标题
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "选择 Bitwarden 文件夹",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                
                // 保险库选择
                if (vaults.size > 1) {
                    Text(
                        text = "选择保险库",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        vaults.forEach { vault ->
                            FilterChip(
                                selected = vault.id == selectedVaultId,
                                onClick = { onVaultSelected(vault.id) },
                                label = { Text(vault.name) }
                            )
                        }
                    }
                    
                    HorizontalDivider()
                }
                
                // 文件夹列表
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(
                        text = "选择文件夹",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 创建新文件夹选项
                        item {
                            ListItem(
                                headlineContent = { Text("创建新文件夹") },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier.clickable { onCreateNewFolder() }
                            )
                        }
                        
                        // 不关联文件夹选项（放入根目录）
                        item {
                            ListItem(
                                headlineContent = { Text("不关联文件夹（根目录）") },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.FolderOff,
                                        contentDescription = null
                                    )
                                },
                                trailingContent = {
                                    if (selectedFolderId == null && selectedVaultId != null) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "已选中",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                modifier = Modifier.clickable { onFolderSelected("") }
                            )
                        }
                        
                        items(folders) { folder ->
                            ListItem(
                                headlineContent = { Text(folder.name) },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (folder.isLinked)
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                supportingContent = if (folder.isLinked) {
                                    { Text("已被其他分类关联") }
                                } else null,
                                trailingContent = {
                                    if (folder.id == selectedFolderId) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "已选中",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                modifier = Modifier.clickable(enabled = !folder.isLinked) { 
                                    onFolderSelected(folder.id) 
                                }
                            )
                        }
                    }
                }
                
                // 操作按钮
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = selectedVaultId != null
                    ) {
                        Text("确认")
                    }
                }
            }
        }
    }
}

/**
 * 同步类型配置对话框
 */
@Composable
fun SyncTypeConfigDialog(
    selectedTypes: List<String>,
    onTypesChanged: (List<String>) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val allTypes = listOf(
        "PASSWORD" to "密码",
        "TOTP" to "验证器",
        "CARD" to "银行卡",
        "NOTE" to "安全笔记",
        "IDENTITY" to "身份证件",
        "PASSKEY" to "通行密钥"
    )
    
    var currentSelection by remember { mutableStateOf(selectedTypes.toSet()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置同步类型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "选择要同步到此文件夹的数据类型",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 全选/取消全选
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        currentSelection = if (currentSelection.size == allTypes.size) {
                            emptySet()
                        } else {
                            allTypes.map { it.first }.toSet()
                        }
                    }
                ) {
                    Checkbox(
                        checked = currentSelection.size == allTypes.size,
                        onCheckedChange = { checked ->
                            currentSelection = if (checked) {
                                allTypes.map { it.first }.toSet()
                            } else {
                                emptySet()
                            }
                        }
                    )
                    Text(
                        text = "全部类型",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                HorizontalDivider()
                
                allTypes.forEach { (type, name) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            currentSelection = if (currentSelection.contains(type)) {
                                currentSelection - type
                            } else {
                                currentSelection + type
                            }
                        }
                    ) {
                        Checkbox(
                            checked = currentSelection.contains(type),
                            onCheckedChange = { checked ->
                                currentSelection = if (checked) {
                                    currentSelection + type
                                } else {
                                    currentSelection - type
                                }
                            }
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onTypesChanged(currentSelection.toList())
                    onConfirm()
                }
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 获取同步类型的显示名称
 */
private fun getSyncTypeDisplayName(type: String): String {
    return when (type.uppercase()) {
        "PASSWORD" -> "密码"
        "TOTP" -> "验证器"
        "CARD" -> "银行卡"
        "NOTE" -> "笔记"
        "IDENTITY" -> "证件"
        "PASSKEY" -> "通行密钥"
        else -> type
    }
}
