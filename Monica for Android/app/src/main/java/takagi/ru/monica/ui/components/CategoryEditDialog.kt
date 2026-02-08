package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.bitwarden.BitwardenVault

/**
 * 增强版分类编辑对话框
 * 
 * 支持：
 * - 编辑分类名称
 * - 关联/解除关联 Bitwarden 文件夹
 * - 配置同步类型
 */
@Composable
fun CategoryEditDialog(
    category: Category,
    availableVaults: List<BitwardenVault>,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
    onLinkToBitwarden: (Category) -> Unit,
    onUnlinkFromBitwarden: (Category) -> Unit,
    onConfigureSyncTypes: (Category, List<String>) -> Unit
) {
    var categoryName by remember { mutableStateOf(category.name) }
    var showSyncTypeDialog by remember { mutableStateOf(false) }
    
    // 解析当前同步类型
    val currentSyncTypes = remember(category.syncItemTypes) {
        parseSyncTypesJson(category.syncItemTypes)
    }
    
    // 查找关联的保险库
    val linkedVault = remember(category.bitwardenVaultId, availableVaults) {
        availableVaults.find { it.id == category.bitwardenVaultId }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_category)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 分类名称输入
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text(stringResource(R.string.category_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Bitwarden 关联卡片
                if (availableVaults.isNotEmpty()) {
                    HorizontalDivider()
                    
                    BitwardenLinkCard(
                        isLinked = category.bitwardenVaultId != null,
                        vaultName = linkedVault?.displayName,
                        folderName = category.bitwardenFolderId?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.folder_generic) },
                        syncTypes = currentSyncTypes,
                        onLinkClick = { 
                            onLinkToBitwarden(category.copy(name = categoryName)) 
                        },
                        onUnlinkClick = { 
                            onUnlinkFromBitwarden(category) 
                        },
                        onConfigureSyncTypesClick = { 
                            showSyncTypeDialog = true 
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (categoryName.isNotBlank()) {
                        onSave(category.copy(name = categoryName))
                    }
                },
                enabled = categoryName.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
    
    // 同步类型配置对话框
    if (showSyncTypeDialog) {
        SyncTypeConfigDialog(
            selectedTypes = currentSyncTypes,
            onTypesChanged = { types ->
                onConfigureSyncTypes(category, types)
            },
            onConfirm = { showSyncTypeDialog = false },
            onDismiss = { showSyncTypeDialog = false }
        )
    }
}

/**
 * 简化版分类编辑对话框
 * 
 * 仅编辑分类名称，不包含 Bitwarden 功能
 */
@Composable
fun SimpleCategoryEditDialog(
    category: Category,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit
) {
    var categoryName by remember { mutableStateOf(category.name) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_category)) },
        text = {
            OutlinedTextField(
                value = categoryName,
                onValueChange = { categoryName = it },
                label = { Text(stringResource(R.string.category_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (categoryName.isNotBlank()) {
                        onSave(category.copy(name = categoryName))
                    }
                },
                enabled = categoryName.isNotBlank()
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

/**
 * 解析同步类型 JSON
 */
private fun parseSyncTypesJson(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    
    return try {
        json.trim('[', ']')
            .split(",")
            .map { it.trim().trim('"') }
            .filter { it.isNotEmpty() }
    } catch (e: Exception) {
        emptyList()
    }
}
