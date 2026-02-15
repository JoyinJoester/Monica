package takagi.ru.monica.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.BackupPreferences

/**
 * 单个内容类型的开关行
 */
@Composable
private fun ContentTypeSwitch(
    label: String,
    count: Int?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    subtitle: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) 
                    MaterialTheme.colorScheme.onSurface 
                else 
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            if (count != null) {
                Text(
                    text = stringResource(R.string.common_count_items, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) 
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) 
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

/**
 * 选择性备份设置卡片
 * 可折叠的卡片，允许用户选择要备份的内容类型
 */
@Composable
fun SelectiveBackupCard(
    preferences: BackupPreferences,
    onPreferencesChange: (BackupPreferences) -> Unit,
    passwordCount: Int,
    authenticatorCount: Int,
    documentCount: Int,
    bankCardCount: Int,
    noteCount: Int,
    trashCount: Int = 0,
    passkeyCount: Int = 0,  // ✅ 新增：验证密钥数量
    localKeePassCount: Int = 0,
    isWebDavConfigured: Boolean = false,
    isKeePassWebDavConfigured: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    // KeePass WebDAV feature has been removed. Keep parameter for call-site compatibility.
    if (isKeePassWebDavConfigured) {
        // no-op
    }
    
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 标题行（可点击展开/折叠）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.selective_backup_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    // 折叠状态下显示摘要
                    if (!expanded) {
                        // 统计选中的项目数（新逻辑：8个主要选项）
                        val walletSelected = preferences.includeDocuments && preferences.includeBankCards
                        val selectedCount = listOf(
                            preferences.includePasswords,
                            preferences.includeAuthenticators,
                            walletSelected,  // 卡包
                            preferences.includePasskeys,  // 验证密钥
                            preferences.includeNotes,
                            preferences.includeImages,
                            preferences.includeTrashAndHistory,  // 回收站与历史（合并项）
                            preferences.includeLocalKeePass
                        ).count { it }
                        
                        // 附加配置单独显示
                        val extras = mutableListOf<String>()
                        if (preferences.includeWebDavConfig && isWebDavConfigured) extras.add("WebDAV")
                        if (preferences.includeLocalKeePass && localKeePassCount > 0) extras.add("KeePass")
                        val extrasText = if (extras.isNotEmpty()) " (+${extras.joinToString(", ")})" else ""
                        
                        Text(
                            text = stringResource(R.string.selective_backup_summary, selectedCount, 8) + extrasText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) 
                        stringResource(R.string.collapse) 
                    else 
                        stringResource(R.string.expand)
                )
            }
            
            // 展开的内容
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.selective_backup_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 内容类型开关列表
                    ContentTypeSwitch(
                        label = stringResource(R.string.backup_content_passwords),
                        count = passwordCount,
                        checked = preferences.includePasswords,
                        onCheckedChange = { 
                            onPreferencesChange(preferences.copy(includePasswords = it))
                        }
                    )
                    
                    ContentTypeSwitch(
                        label = stringResource(R.string.backup_content_authenticators),
                        count = authenticatorCount,
                        checked = preferences.includeAuthenticators,
                        onCheckedChange = { 
                            onPreferencesChange(preferences.copy(includeAuthenticators = it))
                        }
                    )
                    
                    // ✅ 新增：验证密钥 (Passkey)
                    ContentTypeSwitch(
                        label = stringResource(R.string.backup_content_passkeys),
                        count = if (passkeyCount > 0) passkeyCount else null,
                        checked = preferences.includePasskeys,
                        onCheckedChange = { 
                            onPreferencesChange(preferences.copy(includePasskeys = it))
                        },
                        enabled = passkeyCount > 0
                    )
                    
                    // 卡包（证件 + 银行卡）
                    ContentTypeSwitch(
                        label = stringResource(R.string.backup_content_wallet),
                        count = documentCount + bankCardCount,
                        checked = preferences.includeDocuments && preferences.includeBankCards,
                        onCheckedChange = { 
                            onPreferencesChange(preferences.copy(
                                includeDocuments = it,
                                includeBankCards = it
                            ))
                        }
                    )
                    
                    ContentTypeSwitch(
                        label = stringResource(R.string.backup_content_notes),
                        count = noteCount,
                        checked = preferences.includeNotes,
                        onCheckedChange = { 
                            onPreferencesChange(preferences.copy(includeNotes = it))
                        }
                    )
                    
                    ContentTypeSwitch(
                        label = stringResource(R.string.backup_content_images),
                        count = null, // 图片不显示数量
                        checked = preferences.includeImages,
                        onCheckedChange = { 
                            onPreferencesChange(preferences.copy(includeImages = it))
                        }
                    )
                    
                    // ✅ 合并项：回收站与历史（包含密码生成历史、操作历史、回收站）
                    ContentTypeSwitch(
                        label = stringResource(R.string.backup_content_trash_and_history),
                        count = trashCount,
                        checked = preferences.includeTrashAndHistory,
                        onCheckedChange = { 
                            onPreferencesChange(preferences.copy(
                                includeTrashAndHistory = it,
                                // 同时更新旧字段以保持兼容性
                                includeGeneratorHistory = it,
                                includeTimeline = it,
                                includeTrash = it
                            ))
                        },
                        subtitle = stringResource(R.string.backup_content_trash_and_history_hint)
                    )
                    
                    // 本地 KeePass 数据库选项（始终显示）
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    ContentTypeSwitch(
                        label = stringResource(R.string.backup_content_local_keepass),
                        count = if (localKeePassCount > 0) localKeePassCount else null,
                        checked = preferences.includeLocalKeePass,
                        onCheckedChange = { 
                            onPreferencesChange(preferences.copy(includeLocalKeePass = it))
                        },
                        enabled = localKeePassCount > 0
                    )
                    
                    Text(
                        text = if (localKeePassCount > 0) 
                            stringResource(R.string.backup_content_local_keepass_hint)
                        else 
                            stringResource(R.string.backup_content_local_keepass_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (localKeePassCount > 0) 1f else 0.6f
                        ),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    
                    // WebDAV 配置选项（仅在已配置时显示）
                    if (isWebDavConfigured) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        ContentTypeSwitch(
                            label = stringResource(R.string.backup_content_webdav_config),
                            count = null,
                            checked = preferences.includeWebDavConfig,
                            onCheckedChange = { 
                                onPreferencesChange(preferences.copy(includeWebDavConfig = it))
                            }
                        )
                        
                        Text(
                            text = stringResource(R.string.backup_content_webdav_config_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 全选/全不选按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                onPreferencesChange(
                                    BackupPreferences(
                                        includePasswords = true,
                                        includeAuthenticators = true,
                                        includeDocuments = true,
                                        includeBankCards = true,
                                        includePasskeys = passkeyCount > 0,  // ✅ 新增
                                        includeNotes = true,
                                        includeImages = true,
                                        includeTrashAndHistory = true,  // ✅ 新增
                                        includeGeneratorHistory = true,
                                        includeTimeline = true,
                                        includeTrash = true,
                                        includeLocalKeePass = localKeePassCount > 0
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.select_all))
                        }
                        
                        FilledTonalButton(
                            onClick = {
                                onPreferencesChange(
                                    BackupPreferences(
                                        includePasswords = false,
                                        includeAuthenticators = false,
                                        includeDocuments = false,
                                        includeBankCards = false,
                                        includePasskeys = false,  // ✅ 新增
                                        includeNotes = false,
                                        includeImages = false,
                                        includeTrashAndHistory = false,  // ✅ 新增
                                        includeGeneratorHistory = false,
                                        includeTimeline = false,
                                        includeTrash = false,
                                        includeLocalKeePass = false
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.deselect_all))
                        }
                    }
                }
            }
        }
    }
}
