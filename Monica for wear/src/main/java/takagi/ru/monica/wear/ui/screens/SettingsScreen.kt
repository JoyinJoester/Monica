package takagi.ru.monica.wear.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button as WearButton
import androidx.wear.compose.material3.ButtonDefaults as WearButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.Icon as WearIcon
import takagi.ru.monica.wear.R
import takagi.ru.monica.wear.security.WearSecurityManager
import takagi.ru.monica.wear.ui.components.ChangeLockDialog
import takagi.ru.monica.wear.ui.components.ExpressiveBackground
import takagi.ru.monica.wear.ui.components.MonicaTimeText
import takagi.ru.monica.wear.ui.components.RoundHeaderChip
import takagi.ru.monica.wear.ui.components.RoundSectionChip
import takagi.ru.monica.wear.ui.components.WearPanel
import takagi.ru.monica.wear.ui.components.WearTextField
import takagi.ru.monica.wear.ui.components.roundContentWidthFraction
import takagi.ru.monica.wear.viewmodel.ColorScheme as WearColorScheme
import takagi.ru.monica.wear.viewmodel.SettingsViewModel
import takagi.ru.monica.wear.viewmodel.SyncState
import java.text.SimpleDateFormat
import java.util.*

// Theme colors are now defined in ui/theme/Color.kt and applied in MainActivity via MonicaWearTheme

/**
 * 设置界面 - Material 3 设计（深色主题）
 * 包含WebDAV配置、同步、安全设置等
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    securityManager: takagi.ru.monica.wear.security.WearSecurityManager? = null,
    onBack: () -> Unit,
    onLanguageChanged: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val syncState by viewModel.syncState.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val isWebDavConfigured by viewModel.isWebDavConfigured.collectAsState()
    val currentColorScheme: WearColorScheme by viewModel.currentColorScheme.collectAsState()
    val useOledBlack by viewModel.useOledBlack.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    
    var showWebDavDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showChangeLockDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAddTotpDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    
    // 监听同步状态，如果是 PasswordRequired，显示密码输入框
    LaunchedEffect(syncState) {
        if (syncState is SyncState.PasswordRequired) {
            showPasswordDialog = true
        }
    }
    
    SettingsScreenContent(
        syncState = syncState,
        lastSyncTime = lastSyncTime,
        isWebDavConfigured = isWebDavConfigured,
        currentColorScheme = currentColorScheme,
        useOledBlack = useOledBlack,
        currentLanguage = currentLanguage,
        securityManager = securityManager,
        onSyncClick = { viewModel.syncNow() },
        onWebDavClick = { showWebDavDialog = true },
        onBackupClick = { showBackupDialog = true },
        onAddTotpClick = { showAddTotpDialog = true },
        onThemeClick = { showThemeDialog = true },
        onOledBlackToggle = { viewModel.setOledBlack(it) },
        onLanguageClick = { showLanguageDialog = true },
        onClearDataClick = { showClearDataDialog = true },
        onChangeLockClick = { showChangeLockDialog = true },
        modifier = modifier
    )
    
    // 备份确认对话框
    if (showBackupDialog) {
        WearDialogContainer(
            onDismiss = { showBackupDialog = false },
            title = "备份到云端",
            icon = Icons.Default.Upload
        ) {
            Text(
                text = "确定要将当前所有验证器数据上传到 WebDAV 吗？这将创建一个新的备份文件。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            DialogActionButtons(
                primaryLabel = "备份",
                onPrimary = {
                    showBackupDialog = false
                    viewModel.backupNow { _, _ -> }
                },
                secondaryLabel = stringResource(R.string.common_cancel),
                onSecondary = { showBackupDialog = false }
            )
        }
    }
    
    // 解密密码输入对话框
    if (showPasswordDialog) {
        var tempPassword by remember { mutableStateOf("") }
        WearDialogContainer(
            onDismiss = {
                showPasswordDialog = false
                viewModel.resetSyncState()
            },
            title = "输入解密密码",
            icon = Icons.Default.Lock
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "检测到加密的备份文件，请输入密码进行解密：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                WearTextField(
                    value = tempPassword,
                    onValueChange = { tempPassword = it },
                    label = "密码",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            DialogActionButtons(
                primaryLabel = "确定",
                onPrimary = {
                    showPasswordDialog = false
                    viewModel.syncNow(tempPassword)
                },
                secondaryLabel = stringResource(R.string.common_cancel),
                onSecondary = {
                    showPasswordDialog = false
                    viewModel.resetSyncState()
                },
                primaryEnabled = tempPassword.isNotBlank()
            )
        }
    }

    // WebDAV配置对话框
    if (showWebDavDialog) {
        WebDavConfigDialog(
            viewModel = viewModel,
            onDismiss = { showWebDavDialog = false }
        )
    }

    // 配色方案选择对话框
    if (showThemeDialog) {
        ColorSchemeSelectionDialog(
            currentColorScheme = currentColorScheme,
            onColorSchemeSelected = { scheme ->
                viewModel.setColorScheme(scheme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
    
    // 语言选择对话框
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = currentLanguage,
            onLanguageSelected = { language ->
                viewModel.setLanguage(language)
                showLanguageDialog = false
                // 触发语言变更回调，重新创建 Activity
                onLanguageChanged()
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    // 添加验证器对话框
    if (showAddTotpDialog) {
        AddTotpDialog(
            onDismiss = { showAddTotpDialog = false },
            onConfirm = { secret, issuer, accountName, onResult ->
                viewModel.addTotpItem(
                    secret = secret,
                    issuer = issuer,
                    accountName = accountName
                ) { success, error ->
                    onResult(success, error)
                    if (success) {
                        showAddTotpDialog = false
                    }
                }
            }
        )
    }
    
    // 清除数据确认对话框
    if (showClearDataDialog) {
        ClearDataConfirmDialog(
            onConfirm = {
                viewModel.clearAllData()
                showClearDataDialog = false
            },
            onDismiss = { showClearDataDialog = false }
        )
    }
    
    // 修改锁定方式对话框
    if (showChangeLockDialog && securityManager != null) {
        ChangeLockDialog(
            securityManager = securityManager,
            onDismiss = { showChangeLockDialog = false }
        )
    }
}

/**
 * 设置界面内容组件
 */
@Composable
private fun SettingsScreenContent(
    syncState: SyncState,
    lastSyncTime: String,
    isWebDavConfigured: Boolean,
    currentColorScheme: WearColorScheme,
    useOledBlack: Boolean,
    currentLanguage: takagi.ru.monica.wear.viewmodel.AppLanguage,
    securityManager: takagi.ru.monica.wear.security.WearSecurityManager?,
    onSyncClick: () -> Unit,
    onWebDavClick: () -> Unit,
    onBackupClick: () -> Unit,
    onAddTotpClick: () -> Unit,
    onThemeClick: () -> Unit,
    onOledBlackToggle: (Boolean) -> Unit,
    onLanguageClick: () -> Unit,
    onClearDataClick: () -> Unit,
    onChangeLockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()

    ScreenScaffold(
        timeText = { MonicaTimeText() }
    ) { contentPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            ExpressiveBackground()
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = listState
            ) {
                item {
                    RoundHeaderChip(
                        title = stringResource(R.string.settings_title),
                        subtitle = stringResource(R.string.settings_about_app),
                        modifier = Modifier
                            .fillMaxWidth(roundContentWidthFraction(0.68f, 0.76f))
                            .padding(top = 2.dp, bottom = 6.dp)
                    )
                }

                item {
                    SyncStatusCard(
                        syncState = syncState,
                        lastSyncTime = lastSyncTime,
                        onSyncClick = onSyncClick,
                        isConfigured = isWebDavConfigured
                    )
                }

                // Sync
                item {
                    SectionLabel(text = stringResource(R.string.settings_section_sync))
                }
                item {
                    SettingChip(
                        chipIcon = Icons.Default.Cloud,
                        label = stringResource(R.string.settings_webdav_config),
                        secondaryLabel = stringResource(if (isWebDavConfigured) R.string.settings_webdav_configured else R.string.settings_webdav_not_configured),
                        onClick = onWebDavClick
                    )
                }
                if (isWebDavConfigured) {
                    item {
                        SettingChip(
                            chipIcon = Icons.Default.Upload,
                            label = "备份到云端",
                            secondaryLabel = "手动上传当前数据",
                            onClick = onBackupClick
                        )
                    }
                }

                // Authenticator
                item { SectionLabel(text = stringResource(R.string.settings_section_authenticator)) }
                item {
                    SettingChip(
                        chipIcon = Icons.Default.Add,
                        label = stringResource(R.string.settings_add_totp),
                        secondaryLabel = stringResource(R.string.settings_add_totp_subtitle),
                        onClick = onAddTotpClick
                    )
                }

                // Appearance
                item { SectionLabel(text = stringResource(R.string.settings_section_appearance)) }
                item {
                    SettingChip(
                        chipIcon = Icons.Default.Palette,
                        label = stringResource(R.string.settings_color_scheme),
                        secondaryLabel = stringResource(currentColorScheme.displayNameResId),
                        onClick = onThemeClick
                    )
                }
                item {
                    SettingToggleChip(
                        chipIcon = Icons.Default.Palette,
                        label = stringResource(R.string.settings_oled_black),
                        secondaryLabel = stringResource(if (useOledBlack) R.string.settings_oled_black_enabled else R.string.settings_oled_black_disabled),
                        checked = useOledBlack,
                        onCheckedChange = onOledBlackToggle
                    )
                }
                item {
                    SettingChip(
                        chipIcon = Icons.Default.Language,
                        label = stringResource(R.string.settings_language),
                        secondaryLabel = stringResource(currentLanguage.displayNameResId),
                        onClick = onLanguageClick
                    )
                }

                // Security
                if (securityManager != null) {
                    item { SectionLabel(text = stringResource(R.string.settings_section_security)) }
                    item {
                        SettingChip(
                            chipIcon = Icons.Default.Key,
                            label = stringResource(R.string.settings_reset_pin),
                            secondaryLabel = stringResource(R.string.settings_reset_pin_subtitle),
                            onClick = onChangeLockClick
                        )
                    }
                }

                // Data
                item { SectionLabel(text = stringResource(R.string.settings_section_data)) }
                item {
                    SettingChip(
                        chipIcon = Icons.Default.DeleteForever,
                        label = stringResource(R.string.settings_clear_data),
                        secondaryLabel = stringResource(R.string.settings_clear_data_subtitle),
                        onClick = onClearDataClick,
                        isDestructive = true
                    )
                }

                item { AboutSection() }

                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}

/**
 * Material 3 设置分组
 */
@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    // Legacy section container no longer used after chip refactor
}

/**
 * 同步状态卡片 - Material 3 设计
 */
@Composable
private fun SyncStatusCard(
    syncState: SyncState,
    lastSyncTime: String,
    onSyncClick: () -> Unit,
    isConfigured: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = when (syncState) {
        is SyncState.Success -> MaterialTheme.colorScheme.primaryContainer
        is SyncState.Error -> MaterialTheme.colorScheme.errorContainer
        SyncState.Syncing -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    
    val contentColor = when (syncState) {
        is SyncState.Success -> MaterialTheme.colorScheme.onPrimaryContainer
        is SyncState.Error -> MaterialTheme.colorScheme.onErrorContainer
        SyncState.Syncing -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    WearPanel(
        modifier = modifier
            .fillMaxWidth(roundContentWidthFraction(0.86f, 0.94f))
            .clickable(
                enabled = isConfigured && syncState != SyncState.Syncing,
                onClick = onSyncClick
            ),
        shape = RoundedCornerShape(32.dp),
        containerColor = containerColor,
        borderColor = contentColor.copy(alpha = 0.18f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (syncState) {
                            is SyncState.Success -> Icons.Default.CheckCircle
                            is SyncState.Error -> Icons.Default.Error
                            SyncState.Syncing -> Icons.Default.Sync
                            else -> Icons.Default.CloudOff
                        },
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(32.dp)
                    )
                    
                    Text(
                        text = stringResource(when (syncState) {
                            SyncState.Idle -> if (isConfigured) R.string.sync_status_idle else R.string.sync_status_idle_not_configured
                            SyncState.Syncing -> R.string.sync_status_syncing
                            is SyncState.Success -> R.string.sync_status_success
                            is SyncState.Error -> R.string.sync_status_failed
                            SyncState.PasswordRequired -> R.string.sync_status_failed // 或者使用一个新的字符串资源
                        }),
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                AnimatedVisibility(
                    visible = syncState == SyncState.Syncing,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp
                    )
                }
            }
            
            if (isConfigured) {
                if (lastSyncTime.isNotBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = contentColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.sync_last_time, lastSyncTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // 显示同步结果消息
                when (syncState) {
                    is SyncState.Success -> {
                        Text(
                            text = syncState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(contentColor.copy(alpha = 0.1f))
                                .padding(8.dp)
                        )
                    }
                    is SyncState.Error -> {
                        Text(
                            text = syncState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(contentColor.copy(alpha = 0.1f))
                                .padding(8.dp)
                        )
                    }
                    SyncState.PasswordRequired -> {
                        Text(
                            text = "需要解密密码",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(contentColor.copy(alpha = 0.1f))
                                .padding(8.dp)
                        )
                    }
                    else -> {}
                }
                
                if (syncState != SyncState.Syncing) {
                    Text(
                        text = stringResource(R.string.sync_tap_to_sync),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.sync_configure_first),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Material 3 设置项组件
 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    trailingContent: @Composable () -> Unit = {}
) {
    // Legacy list row retained for reference; chips now used instead
}

@Composable
private fun SectionLabel(text: String) {
    RoundSectionChip(
        text = text,
        modifier = Modifier
            .fillMaxWidth(roundContentWidthFraction(0.58f, 0.66f))
            .padding(vertical = 2.dp)
    )
}

@Composable
private fun WearDialogContainer(
    onDismiss: () -> Unit,
    title: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    fullScreen: Boolean = false,
    showHeader: Boolean = true,
    actions: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints {
            val cardWidth = if (fullScreen) maxWidth else maxWidth * 0.9f
            val cardHeight = if (fullScreen) maxHeight else maxHeight * 0.9f

            WearPanel(
                modifier = modifier
                    .width(cardWidth)
                    .heightIn(max = cardHeight)
                    .clip(if (fullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(20.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = if (fullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showHeader) {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    content()

                    actions()
                }
            }
        }
    }
}

@Composable
private fun DialogActionButtons(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    primaryEnabled: Boolean = true,
    primaryIsDestructive: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WearButton(
            onClick = onSecondary,
            modifier = Modifier.weight(1f),
            label = {
                Text(
                    text = secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            colors = WearButtonDefaults.outlinedButtonColors()
        )

        WearButton(
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier.weight(1f),
            label = {
                Text(
                    text = primaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            colors = WearButtonDefaults.buttonColors(
                containerColor = if (primaryIsDestructive) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (primaryIsDestructive) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimary
                }
            )
        )
    }
}


@Composable
private fun SettingChip(
    chipIcon: ImageVector,
    label: String,
    secondaryLabel: String? = null,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    WearButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(roundContentWidthFraction(0.84f, 0.92f)),
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = if (secondaryLabel != null) {
            {
                Text(
                    text = secondaryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else null,
        icon = {
            WearIcon(
                imageVector = chipIcon,
                contentDescription = null,
                tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        colors = WearButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            secondaryContentColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            iconColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun SettingToggleChip(
    chipIcon: ImageVector,
    label: String,
    secondaryLabel: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.fillMaxWidth(roundContentWidthFraction(0.84f, 0.92f)),
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = if (secondaryLabel != null) {
            {
                Text(
                    text = secondaryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else null,
        icon = {
            WearIcon(
                imageVector = chipIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
    )
}

/**
 * 关于信息部分 - Material 3
 */
@Composable
private fun AboutSection(
    modifier: Modifier = Modifier
) {
    WearPanel(
        modifier = modifier.fillMaxWidth(roundContentWidthFraction(0.84f, 0.92f)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(30.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WearIcon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = stringResource(R.string.settings_about_app),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(R.string.settings_version),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Text(
                text = stringResource(R.string.settings_about_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * WebDAV配置对话框
 */
@Composable
private fun WebDavConfigDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // 获取当前配置
    val currentConfig = remember { viewModel.getWebDavConfig() }
    
    var serverUrl by remember { mutableStateOf(currentConfig.serverUrl) }
    var username by remember { mutableStateOf(currentConfig.username) }
    var password by remember { mutableStateOf(currentConfig.password) }
    var enableEncryption by remember { mutableStateOf(currentConfig.enableEncryption) }
    var encryptionPassword by remember { mutableStateOf(currentConfig.encryptionPassword) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    WearDialogContainer(
        onDismiss = onDismiss,
        title = stringResource(R.string.webdav_config_title),
        icon = Icons.Default.Cloud,
        fullScreen = true,
        showHeader = false
    ) {
        val listState = rememberScalingLazyListState()
        val isRound = LocalConfiguration.current.isScreenRound
        val inputWidthFraction = if (isRound) 0.95f else 1f

        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            state = listState
        ) {
            item {
                Text(
                    text = stringResource(R.string.webdav_config_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    WearTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        placeholder = stringResource(R.string.webdav_server_url),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                        leadingIcon = Icons.Default.Link,
                        modifier = Modifier.fillMaxWidth(inputWidthFraction)
                    )
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    WearTextField(
                        value = username,
                        onValueChange = { username = it },
                        placeholder = stringResource(R.string.webdav_username),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii),
                        leadingIcon = Icons.Default.Person,
                        modifier = Modifier.fillMaxWidth(inputWidthFraction)
                    )
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    WearTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = stringResource(R.string.webdav_password),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        leadingIcon = Icons.Default.Lock,
                        modifier = Modifier.fillMaxWidth(inputWidthFraction)
                    )
                }
            }

            item {
                SwitchButton(
                    checked = enableEncryption,
                    onCheckedChange = { enableEncryption = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = stringResource(R.string.webdav_encryption),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    },
                    icon = {
                        WearIcon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }

            if (enableEncryption) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        WearTextField(
                            value = encryptionPassword,
                            onValueChange = { encryptionPassword = it },
                            placeholder = stringResource(R.string.webdav_encryption_password),
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                            leadingIcon = Icons.Default.Key,
                            modifier = Modifier.fillMaxWidth(inputWidthFraction)
                        )
                    }
                }
            }

            if (errorMessage.isNotEmpty()) {
                item {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    FilledTonalIconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        WearIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_cancel)
                        )
                    }

                    FilledIconButton(
                        onClick = {
                            isLoading = true
                            viewModel.configureWebDav(
                                serverUrl,
                                username,
                                password,
                                enableEncryption,
                                encryptionPassword
                            ) { success, error ->
                                isLoading = false
                                if (success) onDismiss() else errorMessage = error ?: context.getString(R.string.webdav_config_failed)
                            }
                        },
                        enabled = !isLoading && serverUrl.isNotBlank() && username.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        WearIcon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.common_save)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

/**
 * 加密密码配置对话框
 */
@Composable
private fun PasswordConfigDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    val canSave = password.isNotBlank() && confirmPassword.isNotBlank()

    WearDialogContainer(
        onDismiss = onDismiss,
        title = "加密密码配置",
        icon = Icons.Default.Key
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "设置用于解密备份文件的密码",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            WearTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = ""
                },
                label = "加密密码",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Default.Lock
            )

            WearTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    errorMessage = ""
                },
                label = "确认密码",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Default.Lock
            )

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        DialogActionButtons(
            primaryLabel = "保存",
            onPrimary = {
                if (password != confirmPassword) {
                    errorMessage = "两次密码输入不一致"
                } else {
                    viewModel.configureEncryptionPassword(password)
                    onDismiss()
                }
            },
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onDismiss,
            primaryEnabled = canSave
        )
    }
}

/**
 * 清除数据确认对话框
 */
@Composable
private fun ClearDataConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    WearDialogContainer(
        onDismiss = onDismiss,
        title = stringResource(R.string.dialog_clear_data_title),
        icon = Icons.Default.Warning
    ) {
        Text(
            text = stringResource(R.string.dialog_clear_data_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        DialogActionButtons(
            primaryLabel = stringResource(R.string.common_confirm),
            onPrimary = onConfirm,
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onDismiss,
            primaryIsDestructive = true
        )
    }
}

/**
 * 配色方案选择对话框
 */
@Composable
private fun ColorSchemeSelectionDialog(
    currentColorScheme: WearColorScheme,
    onColorSchemeSelected: (WearColorScheme) -> Unit,
    onDismiss: () -> Unit
) {
    WearDialogContainer(
        onDismiss = onDismiss,
        title = stringResource(R.string.dialog_color_scheme_title),
        icon = Icons.Default.Palette,
        fullScreen = true,
        showHeader = false
    ) {
        val listState = rememberScalingLazyListState()

        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            state = listState
        ) {
            // 标题
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.dialog_color_scheme_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 配色选项
            items(WearColorScheme.values().size) { index ->
                val scheme = WearColorScheme.values()[index]
                WearButton(
                    onClick = {
                        onColorSchemeSelected(scheme)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = stringResource(scheme.displayNameResId),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        if (scheme == currentColorScheme) {
                            WearIcon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            WearIcon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = if (scheme == currentColorScheme) {
                        WearButtonDefaults.filledTonalButtonColors()
                    } else {
                        WearButtonDefaults.outlinedButtonColors()
                    }
                )
            }

            // 关闭按钮
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledTonalIconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        WearIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_cancel)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

/**
 * 语言选择对话框
 */
@Composable
private fun LanguageSelectionDialog(
    currentLanguage: takagi.ru.monica.wear.viewmodel.AppLanguage,
    onLanguageSelected: (takagi.ru.monica.wear.viewmodel.AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    WearDialogContainer(
        onDismiss = onDismiss,
        title = stringResource(R.string.dialog_language_title),
        icon = Icons.Default.Language,
        fullScreen = true,
        showHeader = false
    ) {
        val listState = rememberScalingLazyListState()

        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            state = listState
        ) {
            // 标题
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.dialog_language_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 语言选项
            items(takagi.ru.monica.wear.viewmodel.AppLanguage.values().size) { index ->
                val language = takagi.ru.monica.wear.viewmodel.AppLanguage.values()[index]
                WearButton(
                    onClick = {
                        onLanguageSelected(language)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = stringResource(language.displayNameResId),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        if (language == currentLanguage) {
                            WearIcon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            WearIcon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = if (language == currentLanguage) {
                        WearButtonDefaults.filledTonalButtonColors()
                    } else {
                        WearButtonDefaults.outlinedButtonColors()
                    }
                )
            }

            // 关闭按钮
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledTonalIconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        WearIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_cancel)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

/**
 * 添加 TOTP 验证器对话框
 */
@Composable
private fun AddTotpDialog(
    onDismiss: () -> Unit,
    onConfirm: (secret: String, issuer: String, accountName: String, onResult: (Boolean, String?) -> Unit) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var secret by remember { mutableStateOf("") }
    var issuer by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    WearDialogContainer(
        onDismiss = onDismiss,
        title = stringResource(R.string.totp_add_title),
        icon = Icons.Default.Add,
        fullScreen = true,
        showHeader = false
    ) {
        val listState = rememberScalingLazyListState()
        val isRound = LocalConfiguration.current.isScreenRound
        val inputWidthFraction = if (isRound) 0.95f else 1f

        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = listState
        ) {
            // 标题
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.totp_add_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 密钥输入 (必填)
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    WearTextField(
                        value = secret,
                        onValueChange = { 
                            secret = it.uppercase().filter { c -> c.isLetterOrDigit() }
                            errorMessage = null
                        },
                        placeholder = stringResource(R.string.dialog_totp_secret_required),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii,
                            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters
                        ),
                        leadingIcon = Icons.Default.Key,
                        modifier = Modifier.fillMaxWidth(inputWidthFraction),
                        enabled = !isLoading
                    )
                }
            }

            // 服务名称 (可选)
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    WearTextField(
                        value = issuer,
                        onValueChange = { 
                            issuer = it
                            errorMessage = null
                        },
                        placeholder = stringResource(R.string.dialog_totp_issuer),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Text),
                        leadingIcon = Icons.Default.Info,
                        modifier = Modifier.fillMaxWidth(inputWidthFraction),
                        enabled = !isLoading
                    )
                }
            }

            // 账户名称 (可选)
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    WearTextField(
                        value = accountName,
                        onValueChange = { 
                            accountName = it
                            errorMessage = null
                        },
                        placeholder = stringResource(R.string.dialog_totp_account),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
                        leadingIcon = Icons.Default.Person,
                        modifier = Modifier.fillMaxWidth(inputWidthFraction),
                        enabled = !isLoading
                    )
                }
            }

            // 错误信息
            if (errorMessage != null) {
                item {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 加载指示器
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            // 操作按钮
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    // 取消按钮
                    FilledTonalIconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        enabled = !isLoading
                    ) {
                        WearIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_cancel)
                        )
                    }

                    // 保存按钮
                    FilledIconButton(
                        onClick = {
                            if (secret.isBlank()) {
                                errorMessage = context.getString(R.string.dialog_totp_secret_error)
                            } else {
                                isLoading = true
                                errorMessage = null
                                onConfirm(secret, issuer, accountName) { success, error ->
                                    isLoading = false
                                    if (!success) {
                                        errorMessage = error ?: context.getString(R.string.totp_add_failed)
                                    } else {
                                        onDismiss()
                                    }
                                }
                            }
                        },
                        enabled = !isLoading && secret.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        WearIcon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.common_save)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}



