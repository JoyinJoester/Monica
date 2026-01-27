package takagi.ru.monica.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
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
    onMoveDown: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null
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
    
    // 获取对应容器的文字颜色
    val contentColor = getDocumentCardContentColor(documentData.documentType, isSelected)
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors(
                containerColor = when (documentData.documentType) {
                    DocumentType.ID_CARD -> MaterialTheme.colorScheme.primaryContainer
                    DocumentType.PASSPORT -> MaterialTheme.colorScheme.secondaryContainer
                    DocumentType.DRIVER_LICENSE -> MaterialTheme.colorScheme.tertiaryContainer
                    DocumentType.SOCIAL_SECURITY -> MaterialTheme.colorScheme.surfaceVariant
                    DocumentType.OTHER -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(16.dp)
        ) {
            // 标题和菜单
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 添加复选框（选择模式）
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = getDocumentTypeName(documentData.documentType),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
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
                        tint = contentColor.copy(alpha = 0.6f)
                    )
                    
                    if (item.isFavorite) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = stringResource(R.string.favorite),
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
                                    contentDescription = stringResource(R.string.more_options)
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
                                        text = { Text(stringResource(R.string.move_up)) },
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
                                        text = { Text(stringResource(R.string.move_down)) },
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
                                    text = { Text(stringResource(R.string.delete)) },
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
                text = stringResource(R.string.document_number_label),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = maskDocumentNumber(documentData.documentNumber, documentData.documentType),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
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
                            text = stringResource(R.string.holder_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = documentData.fullName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    }
                }
                
                if (documentData.expiryDate.isNotBlank()) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.valid_until),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = documentData.expiryDate,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
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
@Composable
private fun getDocumentTypeName(type: DocumentType): String {
    return when (type) {
        DocumentType.ID_CARD -> stringResource(R.string.document_type_id_card)
        DocumentType.PASSPORT -> stringResource(R.string.document_type_passport)
        DocumentType.DRIVER_LICENSE -> stringResource(R.string.document_type_driver_license)
        DocumentType.SOCIAL_SECURITY -> stringResource(R.string.document_type_social_security)
        DocumentType.OTHER -> stringResource(R.string.document_type_other)
    }
}

/**
 * 获取证件卡片的内容颜色
 * 根据证件类型和选择状态返回对应的onXxxContainer颜色
 */
@Composable
private fun getDocumentCardContentColor(type: DocumentType, isSelected: Boolean): androidx.compose.ui.graphics.Color {
    return if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        when (type) {
            DocumentType.ID_CARD -> MaterialTheme.colorScheme.onPrimaryContainer
            DocumentType.PASSPORT -> MaterialTheme.colorScheme.onSecondaryContainer
            DocumentType.DRIVER_LICENSE -> MaterialTheme.colorScheme.onTertiaryContainer
            DocumentType.SOCIAL_SECURITY -> MaterialTheme.colorScheme.onSurfaceVariant
            DocumentType.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
}
