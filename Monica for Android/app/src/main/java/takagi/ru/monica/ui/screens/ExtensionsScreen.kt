package takagi.ru.monica.ui.screens

import android.widget.Toast
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.CommonAccountInfo
import takagi.ru.monica.data.CommonAccountPreferences
import takagi.ru.monica.data.SecureItem

/**
 * 功能拓展页面 - 聚合各种扩展功能的设置
 * 包含：常用账号信息设置、验证器震动提醒等
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ExtensionsScreen(
    onNavigateBack: () -> Unit,
    isPlusActivated: Boolean = false,
    validatorVibrationEnabled: Boolean = false,
    onValidatorVibrationChange: (Boolean) -> Unit = {},
    copyNextCodeWhenExpiring: Boolean = false,
    onCopyNextCodeWhenExpiringChange: (Boolean) -> Unit = {},
    smartDeduplicationEnabled: Boolean = false,
    onSmartDeduplicationEnabledChange: (Boolean) -> Unit = {},
    passwordCardDisplayMode: takagi.ru.monica.data.PasswordCardDisplayMode = takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL,
    onPasswordCardDisplayModeChange: (takagi.ru.monica.data.PasswordCardDisplayMode) -> Unit = {},
    validatorUnifiedProgressBar: takagi.ru.monica.data.UnifiedProgressBarMode = takagi.ru.monica.data.UnifiedProgressBarMode.DISABLED,
    onValidatorUnifiedProgressBarChange: (takagi.ru.monica.data.UnifiedProgressBarMode) -> Unit = {},
    // 通知栏验证器参数
    notificationValidatorEnabled: Boolean = false,
    notificationValidatorAutoMatch: Boolean = false,
    notificationValidatorId: Long = 0L,
    totpItems: List<SecureItem> = emptyList(),
    onNotificationValidatorEnabledChange: (Boolean) -> Unit = {},
    onNotificationValidatorAutoMatchChange: (Boolean) -> Unit = {},
    onNotificationValidatorSelected: (Long) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    
    // 密码卡片显示模式选择对话框
    var showDisplayModeDialog by remember { mutableStateOf(false) }
    
    if (showDisplayModeDialog) {
        AlertDialog(
            onDismissRequest = { showDisplayModeDialog = false },
            title = { Text(stringResource(R.string.password_card_display_mode_title)) },
            text = {
                Column {
                    takagi.ru.monica.data.PasswordCardDisplayMode.values().forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPasswordCardDisplayModeChange(mode)
                                    showDisplayModeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (mode == passwordCardDisplayMode),
                                onClick = {
                                    onPasswordCardDisplayModeChange(mode)
                                    showDisplayModeDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when (mode) {
                                    takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL -> 
                                        stringResource(R.string.display_mode_all)
                                    takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_USERNAME -> 
                                        stringResource(R.string.display_mode_title_username)
                                    takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_ONLY -> 
                                        stringResource(R.string.display_mode_title_only)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDisplayModeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // 准备共享元素 Modifier
    val sharedTransitionScope = takagi.ru.monica.ui.LocalSharedTransitionScope.current
    val animatedVisibilityScope = takagi.ru.monica.ui.LocalAnimatedVisibilityScope.current
    
    var sharedModifier: Modifier = Modifier
    if (false && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            sharedModifier = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "extensions_settings_card"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
            )
        }
    }
    
    Scaffold(
        modifier = sharedModifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.extensions_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            // 顶部说明卡片
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            stringResource(R.string.extensions_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            stringResource(R.string.extensions_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            
            // 常用账号信息设置
            ExtensionSection(title = stringResource(R.string.extensions_account_settings)) {
                CommonAccountCard()
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // 密码卡片显示内容模式选择
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDisplayModeDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.password_card_display_mode_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = when (passwordCardDisplayMode) {
                                takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL -> 
                                    stringResource(R.string.display_mode_all)
                                takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_USERNAME -> 
                                    stringResource(R.string.display_mode_title_username)
                                takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_ONLY -> 
                                    stringResource(R.string.display_mode_title_only)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            ExtensionSection(title = stringResource(R.string.display_options_menu_title)) {
                ExtensionSwitchItem(
                    icon = Icons.Default.CallMerge,
                    title = stringResource(R.string.smart_deduplication),
                    description = stringResource(R.string.smart_deduplication_desc),
                    checked = smartDeduplicationEnabled,
                    onCheckedChange = onSmartDeduplicationEnabledChange
                )
            }
             
            // 验证器设置（需要 Plus）
            if (isPlusActivated) {
                Spacer(modifier = Modifier.height(8.dp))
                ExtensionSection(title = stringResource(R.string.extensions_totp_settings)) {
                    ExtensionSwitchItem(
                        icon = Icons.Default.Vibration,
                        title = stringResource(R.string.validator_vibration),
                        description = stringResource(R.string.validator_vibration_description),
                        checked = validatorVibrationEnabled,
                        onCheckedChange = onValidatorVibrationChange
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ExtensionSwitchItem(
                        icon = Icons.Default.Update,
                        title = stringResource(R.string.copy_next_code_when_expiring),
                        description = stringResource(R.string.copy_next_code_when_expiring_description),
                        checked = copyNextCodeWhenExpiring,
                        onCheckedChange = onCopyNextCodeWhenExpiringChange
                    )
                }
                
                // 通知栏验证器设置（需要 Plus）
                Spacer(modifier = Modifier.height(8.dp))
                ExtensionSection(title = stringResource(R.string.notification_settings_title)) {
                    NotificationValidatorExtensionCard(
                        enabled = notificationValidatorEnabled,
                        autoMatchEnabled = notificationValidatorAutoMatch,
                        selectedId = notificationValidatorId,
                        totpItems = totpItems,
                        onEnabledChange = onNotificationValidatorEnabledChange,
                        onAutoMatchChange = onNotificationValidatorAutoMatchChange,
                        onValidatorSelected = onNotificationValidatorSelected
                    )
                }
            }
            
            // 更多功能即将推出提示
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Upcoming,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        stringResource(R.string.extensions_more_coming_soon),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 功能分类区块
 */
@Composable
private fun ExtensionSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column {
                content()
            }
        }
    }
}

/**
 * 可点击的功能项（导航到其他页面）
 */
@Composable
private fun ExtensionClickableItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // 文字内容
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // 右箭头
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 带开关的功能项
 */
@Composable
private fun ExtensionSwitchItem(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // 文字内容
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 开关
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 常用账号信息卡片（折叠式）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommonAccountCard() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val commonAccountPreferences = remember { CommonAccountPreferences(context) }
    
    val commonInfo by commonAccountPreferences.commonAccountInfo.collectAsState(
        initial = CommonAccountInfo()
    )
    
    var expanded by remember { mutableStateOf(false) }
    var email by remember(commonInfo.email) { mutableStateOf(commonInfo.email) }
    var phone by remember(commonInfo.phone) { mutableStateOf(commonInfo.phone) }
    var username by remember(commonInfo.username) { mutableStateOf(commonInfo.username) }
    var autoFillEnabled by remember(commonInfo.autoFillEnabled) { mutableStateOf(commonInfo.autoFillEnabled) }
    
    Column(
        modifier = Modifier.animateContentSize()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.common_account_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (commonInfo.hasAnyInfo()) 
                        stringResource(R.string.common_account_configured) 
                    else 
                        stringResource(R.string.common_account_not_configured),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Expanded Content
        if (expanded) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.common_account_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 常用邮箱
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.common_account_email)) },
                    placeholder = { Text("name@example.com") },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                // 常用手机号
                OutlinedTextField(
                    value = phone,
                    onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 15) phone = it },
                    label = { Text(stringResource(R.string.common_account_phone)) },
                    placeholder = { Text("13800000000") },
                    leadingIcon = { Icon(Icons.Default.Phone, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                
                // 常用用户名
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.common_account_username)) },
                    placeholder = { Text(stringResource(R.string.common_account_username_hint)) },
                    leadingIcon = { Icon(Icons.Default.AccountCircle, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                // 自动填入开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { autoFillEnabled = !autoFillEnabled }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = autoFillEnabled,
                        onCheckedChange = { autoFillEnabled = it }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.common_account_auto_fill),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.common_account_auto_fill_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 保存按钮
                Button(
                    onClick = {
                        coroutineScope.launch {
                            commonAccountPreferences.setDefaultEmail(email)
                            commonAccountPreferences.setDefaultPhone(phone)
                            commonAccountPreferences.setDefaultUsername(username)
                            commonAccountPreferences.setAutoFillEnabled(autoFillEnabled)
                            Toast.makeText(context, context.getString(R.string.common_account_saved), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

/**
 * 通知栏验证器卡片（扩展页面版本）
 */
@Composable
private fun NotificationValidatorExtensionCard(
    enabled: Boolean,
    autoMatchEnabled: Boolean,
    selectedId: Long,
    totpItems: List<SecureItem>,
    onEnabledChange: (Boolean) -> Unit,
    onAutoMatchChange: (Boolean) -> Unit,
    onValidatorSelected: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    // If disabled, collapse
    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }

    Column(
        modifier = Modifier.animateContentSize()
    ) {
        // Header with Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (enabled) expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (enabled) MaterialTheme.colorScheme.primaryContainer 
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.notification_validator_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface 
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = if (enabled) stringResource(R.string.notification_validator_enabled) 
                           else stringResource(R.string.notification_validator_disabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
        
        // Expanded Content
        if (expanded && enabled) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.select_validator_to_display),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (totpItems.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_validators_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    totpItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onValidatorSelected(item.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = item.id == selectedId,
                                onClick = { onValidatorSelected(item.id) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

