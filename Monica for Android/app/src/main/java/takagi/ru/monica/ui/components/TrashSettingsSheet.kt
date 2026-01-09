package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.viewmodel.TrashSettings

/**
 * 回收站设置底部弹窗 (Material 3 Expressive)
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TrashSettingsSheet(
    currentSettings: TrashSettings,
    onDismiss: () -> Unit,
    onConfirm: (enabled: Boolean, days: Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var enabled by remember { mutableStateOf(currentSettings.enabled) }
    var selectedDays by remember { mutableStateOf(currentSettings.autoDeleteDays) }
    
    val dayOptions = listOf(
        0 to "不自动清空",
        7 to "7天",
        15 to "15天",
        30 to "30天",
        60 to "60天",
        90 to "90天"
    )
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "回收站设置",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
            
            HorizontalDivider()
            
            // 启用/禁用开关
            ListItem(
                headlineContent = { Text("启用回收站") },
                supportingContent = { Text("删除的条目会先移入回收站") },
                trailingContent = {
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow // 适配 BottomSheet 背景
                )
            )
            
            if (enabled) {
                // 自动清空设置
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "自动清空时间",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dayOptions.forEach { (days, label) ->
                            FilterChip(
                                selected = selectedDays == days,
                                onClick = { selectedDays = days },
                                label = { Text(label) },
                                leadingIcon = if (selectedDays == days) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 按钮组
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onConfirm(enabled, selectedDays)
                        onDismiss()
                    }
                ) {
                    Text("保存")
                }
            }
        }
    }
}
