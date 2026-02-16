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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import takagi.ru.monica.R

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
                        text = if (isLinked) {
                            stringResource(R.string.bitwarden_linked_status)
                        } else {
                            stringResource(R.string.bitwarden_not_linked_status)
                        },
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
                        text = stringResource(R.string.bitwarden_sync_all_types),
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
                        Text(stringResource(R.string.bitwarden_configure_sync))
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
                        Text(stringResource(R.string.folder_unlink))
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
                    Text(stringResource(R.string.folder_link_to_bitwarden))
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
                        text = stringResource(R.string.bitwarden_select_folder_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                
                // 保险库选择
                if (vaults.size > 1) {
                    Text(
                        text = stringResource(R.string.folder_select_vault),
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
                        text = stringResource(R.string.folder_select_folder),
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
                                headlineContent = { Text(stringResource(R.string.folder_create_new)) },
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
                                headlineContent = { Text(stringResource(R.string.folder_no_folder_root)) },
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
                                            contentDescription = stringResource(R.string.bitwarden_selected),
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
                                    { Text(stringResource(R.string.bitwarden_folder_linked_by_other)) }
                                } else null,
                                trailingContent = {
                                    if (folder.id == selectedFolderId) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = stringResource(R.string.bitwarden_selected),
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
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = selectedVaultId != null
                    ) {
                        Text(stringResource(R.string.confirm))
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
        "PASSWORD" to R.string.item_type_password,
        "TOTP" to R.string.item_type_authenticator,
        "CARD" to R.string.item_type_bank_card,
        "NOTE" to R.string.sync_type_note,
        "IDENTITY" to R.string.sync_type_identity,
        "PASSKEY" to R.string.passkey
    )
    
    var currentSelection by remember { mutableStateOf(selectedTypes.toSet()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bitwarden_configure_sync_types_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.bitwarden_sync_types_description),
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
                        text = stringResource(R.string.bitwarden_all_types),
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
                            text = stringResource(name),
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
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun getSyncTypeDisplayName(type: String): String {
    val resId = getSyncTypeDisplayNameRes(type)
    return if (resId != null) stringResource(resId) else type
}

private fun getSyncTypeDisplayNameRes(type: String): Int? {
    return when (type.uppercase()) {
        "PASSWORD" -> R.string.item_type_password
        "TOTP" -> R.string.item_type_authenticator
        "CARD" -> R.string.item_type_bank_card
        "NOTE" -> R.string.sync_type_note
        "IDENTITY" -> R.string.sync_type_identity
        "PASSKEY" -> R.string.passkey
        else -> null
    }
}
