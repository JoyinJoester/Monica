package takagi.ru.monica.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import takagi.ru.monica.data.CustomField
import takagi.ru.monica.data.CustomFieldDraft

/**
 * 自定义字段编辑器组件
 * 
 * 用于编辑和新建页面的自定义字段输入区域，支持：
 * - 动态添加/删除字段
 * - 标题和值的输入
 * - 敏感数据保护开关
 * - 拖动排序（未来可扩展）
 */
@Composable
fun CustomFieldEditorSection(
    fields: List<CustomFieldDraft>,
    onFieldsChange: (List<CustomFieldDraft>) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    var localExpanded by remember { mutableStateOf(expanded) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            // 标题栏（可折叠）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        localExpanded = !localExpanded
                        onExpandedChange(localExpanded)
                    }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "自定义字段",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (fields.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${fields.size}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (localExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (localExpanded) "收起" else "展开"
                )
            }
            
            // 可折叠内容
            AnimatedVisibility(
                visible = localExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 字段列表
                    fields.forEachIndexed { index, field ->
                        CustomFieldEditorItem(
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
                            },
                            fieldIndex = index + 1
                        )
                    }
                    
                    // 添加字段按钮
                    OutlinedButton(
                        onClick = {
                            val newList = fields.toMutableList()
                            newList.add(CustomFieldDraft(
                                id = CustomFieldDraft.nextTempId(),
                                title = "",
                                value = "",
                                isProtected = false
                            ))
                            onFieldsChange(newList)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("添加自定义字段")
                    }
                    
                    // 提示文字
                    if (fields.isEmpty()) {
                        Text(
                            text = "可添加安全问题、备用邮箱、PIN码等自定义信息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * 单个自定义字段编辑项
 */
@Composable
fun CustomFieldEditorItem(
    field: CustomFieldDraft,
    onFieldChange: (CustomFieldDraft) -> Unit,
    onDelete: () -> Unit,
    fieldIndex: Int,
    modifier: Modifier = Modifier
) {
    var valueVisible by remember { mutableStateOf(!field.isProtected) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 顶部：序号和删除按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "字段 $fieldIndex",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "删除字段",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
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
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp)
            )
            
            // 字段值输入
            OutlinedTextField(
                value = field.value,
                onValueChange = { onFieldChange(field.copy(value = it)) },
                label = { Text("字段内容") },
                placeholder = { Text("输入内容...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                visualTransformation = if (!valueVisible && field.isProtected) 
                    PasswordVisualTransformation() 
                else 
                    VisualTransformation.None,
                trailingIcon = {
                    if (field.isProtected) {
                        IconButton(onClick = { valueVisible = !valueVisible }) {
                            Icon(
                                imageVector = if (valueVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (valueVisible) "隐藏" else "显示"
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                minLines = 1,
                maxLines = 3
            )
            
            // 敏感数据开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { 
                        onFieldChange(field.copy(isProtected = !field.isProtected))
                        if (!field.isProtected) valueVisible = false
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (field.isProtected) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (field.isProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "敏感数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = field.isProtected,
                    onCheckedChange = { 
                        onFieldChange(field.copy(isProtected = it))
                        if (it) valueVisible = false
                    },
                    modifier = Modifier.height(24.dp)
                )
            }
        }
    }
}

/**
 * 自定义字段详情展示卡片
 * 
 * 用于详情页面展示自定义字段，支持：
 * - 敏感数据隐藏/显示
 * - 一键复制
 * - 清晰的视觉层级（标题小字、内容大字）
 */
@Composable
fun CustomFieldDisplayCard(
    fields: List<CustomField>,
    modifier: Modifier = Modifier,
    onCopyField: (String, String) -> Unit = { _, _ -> }  // (fieldName, value)
) {
    if (fields.isEmpty()) return
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "自定义字段",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // 字段列表
            fields.forEach { field ->
                CustomFieldDisplayItem(
                    field = field,
                    onCopy = { onCopyField(field.title, field.value) }
                )
            }
        }
    }
}

/**
 * 单个自定义字段展示项
 */
@Composable
fun CustomFieldDisplayItem(
    field: CustomField,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    var valueVisible by remember { mutableStateOf(!field.isProtected) }
    val clipboardManager = LocalClipboardManager.current
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCopy() }
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        // 字段名称（小字、灰色）
        Text(
            text = field.title,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(2.dp))
        
        // 字段内容和操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 字段值（大字、黑色）
            Text(
                text = if (valueVisible || !field.isProtected) {
                    field.value.ifEmpty { "-" }
                } else {
                    "••••••••"
                },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            
            // 操作按钮
            Row {
                // 敏感数据的显示/隐藏切换
                if (field.isProtected) {
                    IconButton(
                        onClick = { valueVisible = !valueVisible },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (valueVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (valueVisible) "隐藏" else "显示",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                // 复制按钮
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(field.value))
                        onCopy()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        
        // 敏感数据标记
        if (field.isProtected) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "敏感数据",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
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
