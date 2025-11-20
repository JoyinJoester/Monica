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
    modifier: Modifier = Modifier
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
                style = MaterialTheme.typography.bodyMedium
            )
            if (count != null) {
                Text(
                    text = "$count 项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
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
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
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
                        val selectedCount = listOf(
                            preferences.includePasswords,
                            preferences.includeAuthenticators,
                            preferences.includeDocuments,
                            preferences.includeBankCards,
                            preferences.includeGeneratorHistory,
                            preferences.includeImages
                        ).count { it }
                        
                        Text(
                            text = stringResource(R.string.selective_backup_summary, selectedCount, 6),
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
                    
                    ContentTypeSwitch(
                        label = stringResource(R.string.backup_content_documents),
                        count = documentCount,
                        checked = preferences.includeDocuments,
                        onCheckedChange = { 
                            onPreferencesChange(preferences.copy(includeDocuments = it))
                        }
                    )
                    
                    ContentTypeSwitch(
                        label = stringResource(R.string.backup_content_bank_cards),
                        count = bankCardCount,
                        checked = preferences.includeBankCards,
                        onCheckedChange = { 
                            onPreferencesChange(preferences.copy(includeBankCards = it))
                        }
                    )
                    
                    ContentTypeSwitch(
                        label = stringResource(R.string.backup_content_generator_history),
                        count = null, // 历史记录不显示数量
                        checked = preferences.includeGeneratorHistory,
                        onCheckedChange = { 
                            onPreferencesChange(preferences.copy(includeGeneratorHistory = it))
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
                                        includeGeneratorHistory = true,
                                        includeImages = true
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
                                        includeGeneratorHistory = false,
                                        includeImages = false
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
