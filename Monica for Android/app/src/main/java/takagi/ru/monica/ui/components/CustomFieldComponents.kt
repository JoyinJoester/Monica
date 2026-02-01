package takagi.ru.monica.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import takagi.ru.monica.data.CustomField
import takagi.ru.monica.data.CustomFieldDraft

// =====================================================
// 编辑页面组件 (Edit Screen Components)
// =====================================================

/**
 * 自定义字段区域标题 (带添加按钮)
 * 
 * 显示在自定义字段列表上方，右侧带 "+" 按钮
 */
@Composable
fun CustomFieldSectionHeader(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "自定义字段",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        FilledTonalIconButton(
            onClick = onAddClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加自定义字段",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 单个自定义字段编辑卡片 (支持编辑/查看模式切换)
 * 
 * - 编辑模式：显示输入框、敏感开关、保存/删除按钮
 * - 查看模式：紧凑显示，只有标题和内容，点击可编辑
 */
@Composable
fun CustomFieldEditCard(
    index: Int,
    field: CustomFieldDraft,
    onFieldChange: (CustomFieldDraft) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 编辑模式：新字段（标题或值为空）默认编辑，已保存字段默认查看
    var isEditing by remember { mutableStateOf(field.title.isBlank() || field.value.isBlank()) }
    var valueVisible by remember { mutableStateOf(!field.isProtected) }
    
    // 动态标题
    val displayTitle = field.title.ifBlank { "新字段" }
    
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        if (isEditing) {
            // ========== 编辑模式 ==========
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 顶部：标题和删除按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "删除自定义字段",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                // 字段名称输入
                OutlinedTextField(
                    value = field.title,
                    onValueChange = { onFieldChange(field.copy(title = it)) },
                    label = { Text("字段名称") },
                    placeholder = { Text("如：安全问题、备用邮箱") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Label,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                
                // 字段值输入
                OutlinedTextField(
                    value = field.value,
                    onValueChange = { onFieldChange(field.copy(value = it)) },
                    label = { Text("字段内容") },
                    placeholder = { Text("输入内容...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (!valueVisible && field.isProtected) 
                        PasswordVisualTransformation() 
                    else 
                        VisualTransformation.None,
                    trailingIcon = {
                        if (field.isProtected) {
                            IconButton(onClick = { valueVisible = !valueVisible }) {
                                Icon(
                                    imageVector = if (valueVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (valueVisible) "隐藏内容" else "显示内容"
                                )
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Notes,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    minLines = 1,
                    maxLines = 3
                )
                
                // 敏感数据开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { 
                            onFieldChange(field.copy(isProtected = !field.isProtected))
                            if (!field.isProtected) valueVisible = false
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (field.isProtected) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (field.isProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "敏感数据",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "开启后内容默认隐藏显示",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = field.isProtected,
                        onCheckedChange = { 
                            onFieldChange(field.copy(isProtected = it))
                            if (it) valueVisible = false
                        }
                    )
                }
                
                // 保存按钮
                Button(
                    onClick = { 
                        if (field.title.isNotBlank()) {
                            isEditing = false 
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = field.title.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存字段")
                }
            }
        } else {
            // ========== 查看模式（紧凑） ==========
            Column(
                modifier = Modifier
                    .clickable { isEditing = true }
                    .padding(16.dp)
            ) {
                // 标题行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (field.isProtected) Icons.Default.Lock else Icons.Default.Label,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    // 编辑和删除按钮
                    Row {
                        IconButton(
                            onClick = { isEditing = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "编辑",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 字段值（敏感时隐藏）
                Text(
                    text = if (field.isProtected && !valueVisible) "••••••••" else field.value.ifEmpty { "-" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                
                // 敏感标记
                if (field.isProtected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "敏感数据",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 添加自定义字段按钮 (虚线边框卡片样式)
 */
@Composable
fun AddCustomFieldButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "添加自定义字段",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// =====================================================
// 详情页面组件 (Detail Screen Components)
// =====================================================

/**
 * 单个自定义字段详情展示卡片 (独立卡片样式)
 * 
 * 每个自定义字段独立为一个 ElevatedCard，与编辑页风格一致
 */
@Composable
fun CustomFieldDetailCard(
    field: CustomField,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var valueVisible by remember { mutableStateOf(!field.isProtected) }
    val clipboardManager = LocalClipboardManager.current
    
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .clickable { 
                    clipboardManager.setText(AnnotatedString(field.value))
                    onCopy(field.title)
                }
                .padding(16.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (field.isProtected) Icons.Default.Lock else Icons.Default.Label,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = field.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // 操作按钮
                Row {
                    if (field.isProtected) {
                        IconButton(
                            onClick = { valueVisible = !valueVisible },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (valueVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (valueVisible) "隐藏内容" else "显示内容",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { 
                            clipboardManager.setText(AnnotatedString(field.value))
                            onCopy(field.title)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 字段值
            Text(
                text = if (valueVisible || !field.isProtected) {
                    field.value.ifEmpty { "-" }
                } else {
                    "••••••••"
                },
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // 敏感数据标记
            if (field.isProtected) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "敏感数据",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// =====================================================
// 兼容旧版 API (Backward Compatibility)
// =====================================================

/**
 * 自定义字段编辑器组件 (容器样式 - 渲染为独立卡片)
 * 
 * 保持旧 API 兼容，内部渲染为独立卡片
 */
@Composable
fun CustomFieldEditorSection(
    fields: List<CustomFieldDraft>,
    onFieldsChange: (List<CustomFieldDraft>) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    Column(modifier = modifier) {
        fields.forEachIndexed { index, field ->
            CustomFieldEditCard(
                index = index,
                field = field,
                onFieldChange = { updated ->
                    val newList = fields.toMutableList()
                    newList[index] = updated
                    onFieldsChange(newList)
                },
                onDelete = {
                    val newList = fields.toMutableList()
                    newList.removeAt(index)
                    onFieldsChange(newList)
                }
            )
        }
        
        AddCustomFieldButton(
            onClick = {
                val newList = fields.toMutableList()
                newList.add(CustomFieldDraft(
                    id = CustomFieldDraft.nextTempId(),
                    title = "",
                    value = "",
                    isProtected = false
                ))
                onFieldsChange(newList)
            }
        )
    }
}

/**
 * 自定义字段详情展示 (容器样式 - 渲染为独立卡片)
 * 
 * 保持旧 API 兼容，内部渲染为独立卡片
 */
@Composable
fun CustomFieldDisplayCard(
    fields: List<CustomField>,
    modifier: Modifier = Modifier,
    onCopyField: (String, String) -> Unit = { _, _ -> }
) {
    if (fields.isEmpty()) return
    
    Column(modifier = modifier) {
        fields.forEach { field ->
            CustomFieldDetailCard(
                field = field,
                onCopy = { fieldName -> onCopyField(fieldName, field.value) }
            )
        }
    }
}

/**
 * 简洁的自定义字段列表（用于密码列表项预览）
 */
@Composable
fun CustomFieldPreviewChips(
    fields: List<CustomField>,
    modifier: Modifier = Modifier,
    maxDisplay: Int = 3
) {
    if (fields.isEmpty()) return
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        fields.take(maxDisplay).forEach { field ->
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    text = field.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    maxLines = 1
                )
            }
        }
        
        if (fields.size > maxDisplay) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "+${fields.size - maxDisplay}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
