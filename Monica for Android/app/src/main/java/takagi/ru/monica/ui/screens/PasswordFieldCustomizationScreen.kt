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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
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
                        stringResource(R.string.password_field_customization_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            MonicaIcons.Navigation.back,
                            contentDescription = stringResource(R.string.back)
                        )
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
                            text = stringResource(R.string.password_field_customization_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 系统字段开关列表
            item {
                Text(
                    text = stringResource(R.string.password_field_customization_system_fields),
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
                            title = stringResource(R.string.password_field_customization_security_title),
                            subtitle = stringResource(R.string.password_field_customization_security_subtitle),
                            checked = fieldVisibility.securityVerification,
                            onCheckedChange = { 
                                viewModel.updatePasswordFieldVisibility("securityVerification", it) 
                            }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        FieldToggleItem(
                            icon = Icons.Default.Category,
                            title = stringResource(R.string.password_field_customization_category_title),
                            subtitle = stringResource(R.string.password_field_customization_category_subtitle),
                            checked = fieldVisibility.categoryAndNotes,
                            onCheckedChange = { 
                                viewModel.updatePasswordFieldVisibility("categoryAndNotes", it) 
                            }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        FieldToggleItem(
                            icon = Icons.Default.Apps,
                            title = stringResource(R.string.password_field_customization_app_binding_title),
                            subtitle = stringResource(R.string.password_field_customization_app_binding_subtitle),
                            checked = fieldVisibility.appBinding,
                            onCheckedChange = { 
                                viewModel.updatePasswordFieldVisibility("appBinding", it) 
                            }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        FieldToggleItem(
                            icon = Icons.Default.Person,
                            title = stringResource(R.string.password_field_customization_personal_info_title),
                            subtitle = stringResource(R.string.password_field_customization_personal_info_subtitle),
                            checked = fieldVisibility.personalInfo,
                            onCheckedChange = { 
                                viewModel.updatePasswordFieldVisibility("personalInfo", it) 
                            }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        FieldToggleItem(
                            icon = Icons.Default.LocationOn,
                            title = stringResource(R.string.password_field_customization_address_info_title),
                            subtitle = stringResource(R.string.password_field_customization_address_info_subtitle),
                            checked = fieldVisibility.addressInfo,
                            onCheckedChange = { 
                                viewModel.updatePasswordFieldVisibility("addressInfo", it) 
                            }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        FieldToggleItem(
                            icon = Icons.Default.CreditCard,
                            title = stringResource(R.string.password_field_customization_payment_info_title),
                            subtitle = stringResource(R.string.password_field_customization_payment_info_subtitle),
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
                            text = stringResource(R.string.password_field_customization_preset_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.password_field_customization_preset_section_subtitle),
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
                        Text(stringResource(R.string.add))
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
                            text = stringResource(R.string.password_field_customization_preset_notice),
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
                                text = stringResource(R.string.password_field_customization_no_preset_fields),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.password_field_customization_no_preset_fields_hint),
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
                    Text(stringResource(R.string.password_field_customization_reset_system_fields))
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
                        Text(stringResource(R.string.password_field_customization_clear_preset_fields))
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
                            contentDescription = null,
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
                                    text = stringResource(R.string.custom_field_required),
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
                                    text = stringResource(R.string.custom_field_sensitive),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(
                            R.string.password_field_customization_type_label,
                            presetFieldTypeLabel(field.fieldType)
                        ) + if (field.defaultValue.isNotBlank()) {
                            stringResource(
                                R.string.password_field_customization_default_value_suffix,
                                field.defaultValue
                            )
                        } else {
                            ""
                        },
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
                    contentDescription = stringResource(R.string.delete),
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
            title = { Text(stringResource(R.string.password_field_customization_delete_preset_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.password_field_customization_delete_preset_message,
                        field.fieldName
                    )
                )
            },
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
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
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
            Text(
                if (isEditing) {
                    stringResource(R.string.password_field_customization_edit_preset_title)
                } else {
                    stringResource(R.string.password_field_customization_add_preset_title)
                }
            )
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
                    label = { Text(stringResource(R.string.password_field_customization_field_name_required)) },
                    placeholder = { Text(stringResource(R.string.password_field_customization_field_name_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = fieldNameError,
                    supportingText = if (fieldNameError) {
                        { Text(stringResource(R.string.password_field_customization_field_name_error)) }
                    } else null,
                    shape = RoundedCornerShape(12.dp)
                )
                
                // 字段类型选择
                ExposedDropdownMenuBox(
                    expanded = showTypeDropdown,
                    onExpandedChange = { showTypeDropdown = it }
                ) {
                    OutlinedTextField(
                        value = presetFieldTypeLabel(fieldType),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.password_field_customization_field_type)) },
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
                                        Text(presetFieldTypeLabel(type))
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
                                text = stringResource(R.string.custom_field_sensitive),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.password_field_customization_sensitive_hint),
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
                                text = stringResource(R.string.password_field_customization_required_field),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.password_field_customization_required_field_hint),
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
                    label = { Text(stringResource(R.string.password_field_customization_default_value_optional)) },
                    placeholder = { Text(stringResource(R.string.password_field_customization_default_value_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                // 占位提示
                OutlinedTextField(
                    value = placeholder,
                    onValueChange = { placeholder = it },
                    label = { Text(stringResource(R.string.password_field_customization_placeholder_optional)) },
                    placeholder = { Text(stringResource(R.string.password_field_customization_placeholder_hint)) },
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
                Text(if (isEditing) stringResource(R.string.save) else stringResource(R.string.add))
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
private fun presetFieldTypeLabel(type: PresetFieldType): String {
    return when (type) {
        PresetFieldType.TEXT -> stringResource(R.string.password_field_customization_type_text)
        PresetFieldType.PASSWORD -> stringResource(R.string.password_field_customization_type_password)
        PresetFieldType.NUMBER -> stringResource(R.string.password_field_customization_type_number)
        PresetFieldType.DATE -> stringResource(R.string.password_field_customization_type_date)
        PresetFieldType.URL -> stringResource(R.string.password_field_customization_type_url)
        PresetFieldType.EMAIL -> stringResource(R.string.password_field_customization_type_email)
        PresetFieldType.PHONE -> stringResource(R.string.password_field_customization_type_phone)
    }
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
