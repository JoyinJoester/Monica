package takagi.ru.monica.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import takagi.ru.monica.R
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.DocumentType
import kotlinx.serialization.json.Json

/**
 * 证件卡片组件
 * 显示证件类型、号码（脱敏）等信息
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentCard(
    item: SecureItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    onToggleFavorite: ((Long, Boolean) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    // 解析证件数据
    val documentData = try {
        Json.decodeFromString<DocumentData>(item.itemData)
    } catch (e: Exception) {
        DocumentData(
            documentNumber = "",
            documentType = DocumentType.ID_CARD,
            fullName = "",
            issuedDate = "",
            expiryDate = ""
        )
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    // 长按进入编辑模式
                }
            ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = when (documentData.documentType) {
                DocumentType.ID_CARD -> MaterialTheme.colorScheme.primaryContainer
                DocumentType.PASSPORT -> MaterialTheme.colorScheme.secondaryContainer
                DocumentType.DRIVER_LICENSE -> MaterialTheme.colorScheme.tertiaryContainer
                DocumentType.SOCIAL_SECURITY -> MaterialTheme.colorScheme.surfaceVariant
                DocumentType.OTHER -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 标题和菜单
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = getDocumentTypeName(documentData.documentType),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 证件类型图标
                    Icon(
                        when (documentData.documentType) {
                            DocumentType.ID_CARD -> Icons.Default.Badge
                            DocumentType.PASSPORT -> Icons.Default.FlightTakeoff
                            DocumentType.DRIVER_LICENSE -> Icons.Default.DirectionsCar
                            DocumentType.SOCIAL_SECURITY -> Icons.Default.HealthAndSafety
                            DocumentType.OTHER -> Icons.Default.Description
                        },
                        contentDescription = documentData.documentType.name,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    
                    if (item.isFavorite) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "收藏",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // 菜单按钮
                    if (onDelete != null) {
                        var expanded by remember { mutableStateOf(false) }
                        
                        Box {
                            IconButton(onClick = { expanded = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "更多"
                                )
                            }
                            
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                // 收藏选项
                                if (onToggleFavorite != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(if (item.isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites)) },
                                        onClick = {
                                            expanded = false
                                            onToggleFavorite(item.id, !item.isFavorite)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (item.isFavorite) Icons.Default.FavoriteBorder else Icons.Default.Favorite,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                }
                                
                                // 上移选项
                                if (onMoveUp != null) {
                                    DropdownMenuItem(
                                        text = { Text("上移") },
                                        onClick = {
                                            expanded = false
                                            onMoveUp()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.KeyboardArrowUp,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                                
                                // 下移选项
                                if (onMoveDown != null) {
                                    DropdownMenuItem(
                                        text = { Text("下移") },
                                        onClick = {
                                            expanded = false
                                            onMoveDown()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.KeyboardArrowDown,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                                
                                DropdownMenuItem(
                                    text = { Text("删除") },
                                    onClick = {
                                        expanded = false
                                        onDelete()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 证件号码（脱敏）
            Text(
                text = "证件号码",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = maskDocumentNumber(documentData.documentNumber, documentData.documentType),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 持有人和有效期
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (documentData.fullName.isNotBlank()) {
                    Column {
                        Text(
                            text = "持有人",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = documentData.fullName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                if (documentData.expiryDate.isNotBlank()) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "有效期至",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = documentData.expiryDate,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * 证件号码脱敏处理
 */
private fun maskDocumentNumber(number: String, type: DocumentType): String {
    if (number.isBlank()) return "****"
    
    return when (type) {
        DocumentType.ID_CARD -> {
            // 身份证: 显示前4位和后4位，中间用*代替
            // 例如: 110101199001011234 -> 1101********1234
            if (number.length >= 8) {
                val prefix = number.take(4)
                val suffix = number.takeLast(4)
                "$prefix********$suffix"
            } else {
                "****"
            }
        }
        DocumentType.PASSPORT -> {
            // 护照: 显示前2位和后3位
            // 例如: E12345678 -> E1*****678
            if (number.length >= 5) {
                val prefix = number.take(2)
                val suffix = number.takeLast(3)
                "$prefix*****$suffix"
            } else {
                "****"
            }
        }
        DocumentType.DRIVER_LICENSE -> {
            // 驾照: 显示前4位和后4位
            if (number.length >= 8) {
                val prefix = number.take(4)
                val suffix = number.takeLast(4)
                "$prefix****$suffix"
            } else {
                "****"
            }
        }
        DocumentType.SOCIAL_SECURITY -> {
            // 社保卡: 显示前2位和后2位
            if (number.length >= 4) {
                val prefix = number.take(2)
                val suffix = number.takeLast(2)
                "$prefix******$suffix"
            } else {
                "****"
            }
        }
        DocumentType.OTHER -> {
            // 其他: 只显示最后4位
            if (number.length >= 4) {
                "****${number.takeLast(4)}"
            } else {
                "****"
            }
        }
    }
}

/**
 * 获取证件类型名称
 */
private fun getDocumentTypeName(type: DocumentType): String {
    return when (type) {
        DocumentType.ID_CARD -> "身份证"
        DocumentType.PASSPORT -> "护照"
        DocumentType.DRIVER_LICENSE -> "驾驶证"
        DocumentType.SOCIAL_SECURITY -> "社保卡"
        DocumentType.OTHER -> "其他证件"
    }
}
