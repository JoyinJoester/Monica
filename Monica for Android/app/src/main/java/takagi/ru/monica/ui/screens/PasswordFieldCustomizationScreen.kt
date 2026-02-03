package takagi.ru.monica.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.PasswordFieldVisibility
import takagi.ru.monica.data.PresetCustomField
import takagi.ru.monica.data.PresetFieldType
import takagi.ru.monica.ui.icons.MonicaIcons
import takagi.ru.monica.viewmodel.SettingsViewModel
import java.util.UUID

/**
 * 添加密码页面字段定制设置页面
 * 允许用户关闭不需要的字段卡片，以及管理预设自定义字段
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordFieldCustomizationScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val fieldVisibility = settings.passwordFieldVisibility
    val presetFields by viewModel.presetCustomFields.collectAsState()
    
    // 添加/编辑预设字段对话框状态
    var showAddPresetDialog by remember { mutableStateOf(false) }
    var editingPresetField by remember { mutableStateOf<PresetCustomField?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "添加密码页面字段定制",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(MonicaIcons.Navigation.back, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 说明卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "关闭的字段将不会在添加/编辑密码页面显示。\n\n注意：如果某条密码已有该字段的数据，即使关闭了开关，编辑该条目时仍会显示对应卡片。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 系统字段开关列表
            item {
                Text(
                    text = "系统字段",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        FieldToggleItem(
                            icon = Icons.Default.VpnKey,
                            title = "安全验证",
                            subtitle = "TOTP验证码密钥",
                            checked = fieldVisibility.securityVerification,
                            onCheckedChange = { 
                                viewModel.updatePasswordFieldVisibility("securityVerification", it) 
                            }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        FieldToggleItem(
                            icon = Icons.Default.Category,
                            title = "分类与备注",
                            subtitle = "密码分类和备注信息",
                            checked = fieldVisibility.categoryAndNotes,
                            onCheckedChange = { 
                                viewModel.updatePasswordFieldVisibility("categoryAndNotes", it) 
                            }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        FieldToggleItem(
                            icon = Icons.Default.Apps,
                            title = "应用关联",
                            subtitle = "关联已安装的应用",
                            checked = fieldVisibility.appBinding,
                            onCheckedChange = { 
                                viewModel.updatePasswordFieldVisibility("appBinding", it) 
                            }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        FieldToggleItem(
                            icon = Icons.Default.Person,
                            title = "个人信息",
                            subtitle = "邮箱、电话等联系方式",
                            checked = fieldVisibility.personalInfo,
                            onCheckedChange = { 
                                viewModel.updatePasswordFieldVisibility("personalInfo", it) 
                            }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        FieldToggleItem(
                            icon = Icons.Default.LocationOn,
                            title = "地址信息",
                            subtitle = "街道、城市、国家等",
                            checked = fieldVisibility.addressInfo,
                            onCheckedChange = { 
                                viewModel.updatePasswordFieldVisibility("addressInfo", it) 
                            }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        FieldToggleItem(
                            icon = Icons.Default.CreditCard,
                            title = "支付信息",
                            subtitle = "信用卡/银行卡信息",
                            checked = fieldVisibility.paymentInfo,
                            onCheckedChange = { 
                                viewModel.updatePasswordFieldVisibility("paymentInfo", it) 
                            }
                        )
                    }
                }
            }
            
            // ==================== 预设自定义字段区域 ====================
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "预设自定义字段",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "添加密码时自动包含，不可删除",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { showAddPresetDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("添加")
                    }
                }
            }
            
            // 预设字段说明卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "预设的自定义字段会在每次添加密码时自动出现，并且带有锁定标记不能被删除，确保重要字段不会被意外移除。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            // 预设字段列表
            if (presetFields.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "暂无预设字段",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "点击上方「添加」按钮创建常用字段模板",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                items(presetFields.sortedBy { it.order }, key = { it.id }) { field ->
                    PresetFieldCard(
                        field = field,
                        onEdit = { editingPresetField = field },
                        onDelete = { viewModel.deletePresetCustomField(field.id) }
                    )
                }
            }

            // 重置按钮
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        // 重置所有字段为默认开启
                        viewModel.updatePasswordFieldVisibility("securityVerification", true)
                        viewModel.updatePasswordFieldVisibility("categoryAndNotes", true)
                        viewModel.updatePasswordFieldVisibility("appBinding", true)
                        viewModel.updatePasswordFieldVisibility("personalInfo", true)
                        viewModel.updatePasswordFieldVisibility("addressInfo", true)
                        viewModel.updatePasswordFieldVisibility("paymentInfo", true)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重置系统字段为默认")
                }
            }
            
            // 清空预设字段按钮
            if (presetFields.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.clearAllPresetCustomFields() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("清空所有预设字段")
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
    
    // 添加预设字段对话框
    if (showAddPresetDialog) {
        PresetFieldDialog(
            field = null,
            onDismiss = { showAddPresetDialog = false },
            onSave = { newField ->
                viewModel.addPresetCustomField(newField)
                showAddPresetDialog = false
            }
        )
    }
    
    // 编辑预设字段对话框
    editingPresetField?.let { field ->
        PresetFieldDialog(
            field = field,
            onDismiss = { editingPresetField = null },
            onSave = { updatedField ->
                viewModel.updatePresetCustomField(updatedField)
                editingPresetField = null
            }
        )
    }
}

@Composable
private fun FieldToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = subtitle,
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
 * 预设字段卡片
 */
@Composable
private fun PresetFieldCard(
    field: PresetCustomField,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onEdit() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 字段类型图标
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getFieldTypeIcon(field.fieldType),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = field.fieldName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // 锁定标记
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "锁定",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        // 必填标记
                        if (field.isRequired) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "必填",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        // 敏感标记
                        if (field.isSensitive) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "敏感",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "类型: ${field.fieldType.displayName}" + 
                               if (field.defaultValue.isNotBlank()) " · 默认: ${field.defaultValue}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // 删除按钮
            IconButton(
                onClick = { showDeleteConfirm = true }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    
    // 删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("删除预设字段") },
            text = { Text("确定要删除预设字段「${field.fieldName}」吗？\n\n已添加的密码中该字段的数据不会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 添加/编辑预设字段对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetFieldDialog(
    field: PresetCustomField?,
    onDismiss: () -> Unit,
    onSave: (PresetCustomField) -> Unit
) {
    val isEditing = field != null
    
    var fieldName by remember { mutableStateOf(field?.fieldName ?: "") }
    var fieldType by remember { mutableStateOf(field?.fieldType ?: PresetFieldType.TEXT) }
    var isSensitive by remember { mutableStateOf(field?.isSensitive ?: false) }
    var isRequired by remember { mutableStateOf(field?.isRequired ?: false) }
    var defaultValue by remember { mutableStateOf(field?.defaultValue ?: "") }
    var placeholder by remember { mutableStateOf(field?.placeholder ?: "") }
    
    var showTypeDropdown by remember { mutableStateOf(false) }
    var fieldNameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(if (isEditing) "编辑预设字段" else "添加预设字段") 
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 字段名称
                OutlinedTextField(
                    value = fieldName,
                    onValueChange = { 
                        fieldName = it
                        fieldNameError = false
                    },
                    label = { Text("字段名称 *") },
                    placeholder = { Text("如：恢复密钥、客服电话") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = fieldNameError,
                    supportingText = if (fieldNameError) {
                        { Text("请输入字段名称") }
                    } else null,
                    shape = RoundedCornerShape(12.dp)
                )
                
                // 字段类型选择
                ExposedDropdownMenuBox(
                    expanded = showTypeDropdown,
                    onExpandedChange = { showTypeDropdown = it }
                ) {
                    OutlinedTextField(
                        value = fieldType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("字段类型") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTypeDropdown)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    ExposedDropdownMenu(
                        expanded = showTypeDropdown,
                        onDismissRequest = { showTypeDropdown = false }
                    ) {
                        PresetFieldType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = getFieldTypeIcon(type),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(type.displayName)
                                    }
                                },
                                onClick = {
                                    fieldType = type
                                    showTypeDropdown = false
                                    // 根据类型自动设置敏感属性
                                    if (type == PresetFieldType.PASSWORD) {
                                        isSensitive = true
                                    }
                                }
                            )
                        }
                    }
                }
                
                // 选项开关
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "敏感数据",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "默认隐藏显示，复制时标记为敏感",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isSensitive,
                            onCheckedChange = { isSensitive = it }
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "必填字段",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "添加密码时必须填写",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isRequired,
                            onCheckedChange = { isRequired = it }
                        )
                    }
                }
                
                // 默认值
                OutlinedTextField(
                    value = defaultValue,
                    onValueChange = { defaultValue = it },
                    label = { Text("默认值（可选）") },
                    placeholder = { Text("新建时的初始值") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                // 占位提示
                OutlinedTextField(
                    value = placeholder,
                    onValueChange = { placeholder = it },
                    label = { Text("占位提示（可选）") },
                    placeholder = { Text("输入框为空时显示的提示") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fieldName.isBlank()) {
                        fieldNameError = true
                        return@Button
                    }
                    val newField = PresetCustomField(
                        id = field?.id ?: UUID.randomUUID().toString(),
                        fieldName = fieldName.trim(),
                        fieldType = fieldType,
                        isSensitive = isSensitive,
                        isRequired = isRequired,
                        defaultValue = defaultValue.trim(),
                        placeholder = placeholder.trim(),
                        order = field?.order ?: 0
                    )
                    onSave(newField)
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isEditing) "保存" else "添加")
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
 * 根据字段类型获取图标
 */
private fun getFieldTypeIcon(type: PresetFieldType): ImageVector {
    return when (type) {
        PresetFieldType.TEXT -> Icons.Default.TextFields
        PresetFieldType.PASSWORD -> Icons.Default.Password
        PresetFieldType.NUMBER -> Icons.Default.Numbers
        PresetFieldType.DATE -> Icons.Default.DateRange
        PresetFieldType.URL -> Icons.Default.Link
        PresetFieldType.EMAIL -> Icons.Default.Email
        PresetFieldType.PHONE -> Icons.Default.Phone
    }
}
