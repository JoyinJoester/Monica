package takagi.ru.monica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.BottomNavContentTab
import takagi.ru.monica.data.Language
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.ui.components.TrashSettingsSheet
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.utils.BiometricAuthHelper
import takagi.ru.monica.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import takagi.ru.monica.data.SecureItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onResetPassword: () -> Unit,
    onSecurityQuestions: () -> Unit,
    onNavigateToSyncBackup: () -> Unit = {},
    onNavigateToAutofill: () -> Unit = {},
    onNavigateToPasskeySettings: () -> Unit = {},
    onNavigateToBottomNavSettings: () -> Unit = {},
    onNavigateToColorScheme: () -> Unit = {},
    onSecurityAnalysis: () -> Unit = {},
    onNavigateToDeveloperSettings: () -> Unit = {},
    onNavigateToPermissionManagement: () -> Unit = {},
    onNavigateToMonicaPlus: () -> Unit = {},
    onNavigateToExtensions: () -> Unit = {},
    onClearAllData: (Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    showTopBar: Boolean = true  // 添加参数控制是否显示顶栏
) {
    val context = LocalContext.current
    
    // 直接使用 LocalContext.current as? ComponentActivity 获取 Activity
    val activity = context as? FragmentActivity
    android.util.Log.d("SettingsScreen", "Activity: $activity (type: ${context.javaClass.name})")
    
    val settings by viewModel.settings.collectAsState()
    val totpItems by viewModel.totpItems.collectAsState()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var showClearDataDialog by remember { mutableStateOf(false) }
    var clearDataPasswordInput by remember { mutableStateOf("") }
    
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAutoLockDialog by remember { mutableStateOf(false) }
    var showDeveloperVerifyDialog by remember { mutableStateOf(false) }
    var previewFeaturesExpanded by remember { mutableStateOf(false) }
    var developerPasswordInput by remember { mutableStateOf("") }
    var showWeakBiometricWarning by remember { mutableStateOf(false) }
    
    // 生物识别帮助类
    val biometricHelper = remember(context) { BiometricAuthHelper(context) }
    val isBiometricAvailable = remember(biometricHelper) { 
        val available = biometricHelper.isBiometricAvailable()
        android.util.Log.d("SettingsScreen", "Biometric available: $available")
        android.util.Log.d("SettingsScreen", "Activity: $activity")
        available
    }
    
    // 使用本地状态跟踪生物识别开关,避免验证失败时状态不一致
    var biometricSwitchState by remember(settings.biometricEnabled) { 
        mutableStateOf(settings.biometricEnabled) 
    }


    // 准备共享元素 Modifier
    val sharedTransitionScope = takagi.ru.monica.ui.LocalSharedTransitionScope.current
    val animatedVisibilityScope = takagi.ru.monica.ui.LocalAnimatedVisibilityScope.current
    
    @Composable
    fun getSharedModifier(key: String): Modifier {
        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                 return Modifier.sharedBounds(
                     sharedContentState = rememberSharedContentState(key = key),
                     animatedVisibilityScope = animatedVisibilityScope,
                     resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                 )
            }
        }
        return Modifier
    }

    val startBiometricEnable = {
        if (activity != null) {
            android.util.Log.d("SettingsScreen", "Starting biometric authentication...")
            biometricHelper.authenticate(
                activity = activity,
                title = context.getString(R.string.biometric_login_title),
                subtitle = "验证指纹以启用指纹解锁",
                description = context.getString(R.string.biometric_login_description),
                negativeButtonText = context.getString(R.string.cancel),
                onSuccess = {
                    android.util.Log.d("SettingsScreen", "Biometric authentication SUCCESS")
                    biometricSwitchState = true
                    viewModel.updateBiometricEnabled(true)
                    Toast.makeText(
                        context,
                        "指纹解锁已启用",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onError = { errorCode, errorMsg ->
                    android.util.Log.e("SettingsScreen", "Biometric authentication ERROR: code=$errorCode, msg=$errorMsg")
                    biometricSwitchState = false
                    Toast.makeText(
                        context,
                        "指纹验证失败: $errorMsg",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onCancel = {
                    android.util.Log.d("SettingsScreen", "Biometric authentication CANCELLED")
                    biometricSwitchState = false
                    Toast.makeText(
                        context,
                        "已取消",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        } else {
            android.util.Log.e("SettingsScreen", "Activity is NULL! Cannot authenticate")
            biometricSwitchState = false
            Toast.makeText(
                context,
                context.getString(R.string.biometric_cannot_enable),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    Scaffold(
        topBar = if (showTopBar) {
            {
                // 使用自定义顶部栏以减小高度
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars) // 仅适配状态栏
                            .height(56.dp) // 标准高度
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = context.getString(R.string.back))
                        }
                        
                        Text(
                            text = context.getString(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        )
                        
                        // 安全分析图标
                        IconButton(onClick = onSecurityAnalysis) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = context.getString(R.string.security_analysis),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        } else {
            {}
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Top padding spacer for edge-to-edge scrolling
            Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))
            
            // Monica Plus Card
            takagi.ru.monica.ui.components.MonicaPlusCard(
                isPlusActivated = settings.isPlusActivated,
                onClick = {
                    android.util.Log.d("SettingsScreen", "Monica Plus card clicked")
                    onNavigateToMonicaPlus()
                },
                modifier = getSharedModifier("monica_plus_card")
            )

            // 安全分析入口卡片 - 置顶显示
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .then(getSharedModifier("security_analysis_card"))
                    .clickable { onSecurityAnalysis() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = context.getString(R.string.security_analysis),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = context.getString(R.string.security_analysis_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Security Settings
            SettingsSection(
                title = context.getString(R.string.security)
            ) {
                // 生物识别开关
                SettingsItemWithSwitch(
                    icon = Icons.Default.Fingerprint,
                    title = context.getString(R.string.biometric_unlock),
                    subtitle = if (isBiometricAvailable) {
                        if (biometricSwitchState)
                            context.getString(R.string.biometric_unlock_enabled)
                        else
                            context.getString(R.string.biometric_unlock_disabled)
                    } else {
                        biometricHelper.getBiometricStatusMessage()
                    },
                    checked = biometricSwitchState,
                    enabled = isBiometricAvailable,
                    onCheckedChange = { newState ->
                        android.util.Log.d("SettingsScreen", "Switch clicked: newState=$newState, activity=$activity")
                        if (newState) {
                            val weakOnly = biometricHelper.isWeakBiometricOnly()
                            if (weakOnly) {
                                showWeakBiometricWarning = true
                            } else {
                                startBiometricEnable()
                            }
                        } else {
                            // 用户想禁用指纹解锁,直接禁用不需要验证
                            android.util.Log.d("SettingsScreen", "Disabling biometric unlock")
                            biometricSwitchState = false
                            viewModel.updateBiometricEnabled(false)
                            Toast.makeText(
                                context,
                                context.getString(R.string.biometric_unlock_disabled),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
                
                SettingsItem(
                    icon = Icons.Default.Security,
                    title = context.getString(R.string.screenshot_protection),
                    subtitle = if (settings.screenshotProtectionEnabled) 
                        context.getString(R.string.screenshot_protection_enabled)
                    else 
                        context.getString(R.string.screenshot_protection_disabled),
                    onClick = { 
                        viewModel.updateScreenshotProtectionEnabled(!settings.screenshotProtectionEnabled)
                    }
                )
                
                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = context.getString(R.string.auto_lock),
                    subtitle = getAutoLockDisplayName(settings.autoLockMinutes, context),
                    onClick = { showAutoLockDialog = true }
                )
                
                SettingsItem(
                    icon = Icons.Default.Security,
                    title = context.getString(R.string.security_questions),
                    subtitle = context.getString(R.string.security_questions_description),
                    onClick = onSecurityQuestions,
                    modifier = getSharedModifier("security_questions_card")
                )
                
                SettingsItem(
                    icon = Icons.Default.AdminPanelSettings,
                    title = context.getString(R.string.permission_management_title),
                    subtitle = context.getString(R.string.permission_management_subtitle),
                    onClick = onNavigateToPermissionManagement,
                    modifier = getSharedModifier("permission_settings_card")
                )
                
                SettingsItem(
                    icon = Icons.Default.VpnKey,
                    title = context.getString(R.string.reset_master_password),
                    subtitle = context.getString(R.string.reset_password_description),
                    onClick = onResetPassword,
                    modifier = getSharedModifier("reset_password_card")
                )
            }
            
            // Data Management Settings
            SettingsSection(
                title = context.getString(R.string.data_management)
            ) {
                // 同步与备份入口
                SettingsItem(
                    icon = Icons.Default.Sync,
                    title = context.getString(R.string.sync_backup_title),
                    subtitle = context.getString(R.string.sync_backup_description),
                    onClick = onNavigateToSyncBackup,
                    modifier = getSharedModifier("sync_settings_card")
                )
                




                SettingsItem(
                    icon = Icons.Default.VpnKey,
                    title = context.getString(R.string.autofill),
                    subtitle = context.getString(R.string.autofill_subtitle),
                    onClick = onNavigateToAutofill,

                    modifier = getSharedModifier("autofill_settings_card")
                )
                
                // Passkey 设置

                
                // 回收站设置
                SettingsItemWithTrashConfig(
                    trashEnabled = settings.trashEnabled,
                    trashAutoDeleteDays = settings.trashAutoDeleteDays,
                    onTrashEnabledChange = { enabled ->
                        viewModel.updateTrashEnabled(enabled)
                    },
                    onAutoDeleteDaysChange = { days ->
                        viewModel.updateTrashAutoDeleteDays(days)
                    }
                )
                
                SettingsItem(
                    icon = Icons.Default.DeleteForever,
                    title = context.getString(R.string.clear_all_data),
                    subtitle = context.getString(R.string.clear_all_data_subtitle),
                    onClick = { showClearDataDialog = true },
                    iconTint = MaterialTheme.colorScheme.error
                )
            }
            
            // Appearance Settings (原Theme Settings)
            SettingsSection(
                title = context.getString(R.string.theme)  // 现在显示为"外观"
            ) {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = context.getString(R.string.theme),
                    subtitle = getThemeDisplayName(settings.themeMode, context),
                    onClick = { showThemeDialog = true }
                )
                
                SettingsItem(
                    icon = Icons.Default.Colorize,
                    title = context.getString(R.string.color_scheme),
                    subtitle = getColorSchemeDisplayName(settings.colorScheme, context),
                    onClick = { onNavigateToColorScheme() },
                    modifier = getSharedModifier("color_scheme_card")
                )
                
                // 移入的设置项：
                // 1. 语言设置
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = context.getString(R.string.language),
                    subtitle = getLanguageDisplayName(settings.language, context),
                    onClick = { showLanguageDialog = true }
                )
                
                // 导航栏版本切换
                SettingsItem(
                    icon = Icons.Default.Dashboard,
                    title = "导航栏版本",
                    subtitle = if (settings.navBarVersion == takagi.ru.monica.data.NavBarVersion.V1) 
                        "V1 经典导航栏（可自定义）" 
                    else 
                        "V2 简洁导航栏（固定4项 + 最近页面）",
                    onClick = {
                        val newVersion = if (settings.navBarVersion == takagi.ru.monica.data.NavBarVersion.V1) 
                            takagi.ru.monica.data.NavBarVersion.V2 
                        else 
                            takagi.ru.monica.data.NavBarVersion.V1
                        viewModel.updateNavBarVersion(newVersion)
                    }
                )
                
                // 2. 底部导航栏设置（仅V1模式可用）
                if (settings.navBarVersion == takagi.ru.monica.data.NavBarVersion.V1) {
                    SettingsItem(
                        icon = Icons.Default.ViewWeek,
                        title = context.getString(R.string.bottom_nav_settings),
                        subtitle = context.getString(R.string.bottom_nav_settings_entry_subtitle),
                        onClick = onNavigateToBottomNavSettings,
                        modifier = getSharedModifier("bottom_nav_settings_card")
                    )
                }
                
                // 3. 功能扩展入口
                SettingsItem(
                    icon = Icons.Default.Extension,
                    title = context.getString(R.string.extensions_title),
                    subtitle = context.getString(R.string.extensions_description),
                    onClick = onNavigateToExtensions,
                    modifier = getSharedModifier("extensions_settings_card")
                )
                

                // 3. 关闭壁纸取色设置
                SettingsItemWithSwitch(
                    icon = Icons.Default.Colorize,
                    title = context.getString(R.string.disable_wallpaper_color_extraction),
                    subtitle = context.getString(R.string.disable_wallpaper_color_extraction_description),
                    checked = !settings.dynamicColorEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.updateDynamicColorEnabled(!enabled)
                    }
                )
                
                // 4. 减少动画设置 - 解决 HyperOS 2/Android 15 等设备的动画卡顿问题
                SettingsItemWithSwitch(
                    icon = Icons.Default.Speed,
                    title = context.getString(R.string.reduce_animations),
                    subtitle = context.getString(R.string.reduce_animations_description),
                    checked = settings.reduceAnimations,
                    onCheckedChange = { enabled ->
                        viewModel.updateReduceAnimations(enabled)
                    }
                )
            }

            // About Settings
            SettingsSection(
                title = context.getString(R.string.about)
            ) {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = context.getString(R.string.version),
                    subtitle = context.getString(R.string.settings_version_number),
                    onClick = {
                        // 打开 GitHub 仓库链接
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/JoyinJoester/Monica"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.cannot_open_browser),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
            
            // 开发者设置入口
            SettingsSection(
                title = stringResource(R.string.developer_settings)
            ) {
                // 预览功能 - 点击弹出对话框
                SettingsItem(
                    icon = Icons.Default.Science,
                    title = stringResource(R.string.preview_features_title),
                    subtitle = stringResource(R.string.preview_features_description),
                    onClick = { previewFeaturesExpanded = true }
                )

                SettingsItem(
                    icon = Icons.Default.Code,
                    title = stringResource(R.string.developer_settings),
                    subtitle = stringResource(R.string.developer_settings_subtitle),
                    modifier = getSharedModifier("developer_settings_card"),
                    onClick = {
                        val hasActivity = activity != null
                        val disablePasswordVerification = settings.disablePasswordVerification
                        val biometricEnabled = settings.biometricEnabled
                        val biometricAvailableNow = hasActivity && biometricEnabled && biometricHelper.isBiometricAvailable()
                        android.util.Log.d(
                            "SettingsScreen",
                            "Developer settings tapped. hasActivity=$hasActivity, biometricEnabled=$biometricEnabled, biometricAvailable=$biometricAvailableNow, disablePasswordVerification=$disablePasswordVerification"
                        )

                        developerPasswordInput = ""
                        showDeveloperVerifyDialog = false

                        when {
                            disablePasswordVerification -> {
                                onNavigateToDeveloperSettings()
                            }
                            !hasActivity -> {
                                android.util.Log.w(
                                    "SettingsScreen",
                                    "Cannot start biometric auth: FragmentActivity context missing"
                                )
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.use_master_password),
                                    Toast.LENGTH_SHORT
                                ).show()
                                showDeveloperVerifyDialog = true
                            }
                            biometricAvailableNow -> {
                                biometricHelper.authenticate(
                                    activity = activity!!,
                                    title = context.getString(R.string.biometric_login_title),
                                    subtitle = context.getString(R.string.biometric_login_subtitle),
                                    description = context.getString(R.string.biometric_login_description),
                                    negativeButtonText = context.getString(R.string.use_master_password),
                                    onSuccess = {
                                        android.util.Log.d(
                                            "SettingsScreen",
                                            "Developer biometric authentication succeeded"
                                        )
                                        showDeveloperVerifyDialog = false
                                        developerPasswordInput = ""
                                        onNavigateToDeveloperSettings()
                                    },
                                    onError = { errorCode, errorMessage ->
                                        android.util.Log.w(
                                            "SettingsScreen",
                                            "Developer biometric error: code=$errorCode, message=$errorMessage"
                                        )
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.biometric_auth_error, errorMessage),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        showDeveloperVerifyDialog = true
                                    },
                                    onCancel = {
                                        android.util.Log.d(
                                            "SettingsScreen",
                                            "Developer biometric canceled by user"
                                        )
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.use_master_password),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        showDeveloperVerifyDialog = true
                                    }
                                )
                            }
                            else -> {
                                android.util.Log.d(
                                    "SettingsScreen",
                                    "Biometric unavailable, showing password dialog for developer settings"
                                )
                                if (biometricEnabled) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.biometric_not_available),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                showDeveloperVerifyDialog = true
                            }
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Bottom padding spacer for edge-to-edge scrolling
            Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
        }
    }
    
    // Theme Selection Dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = settings.themeMode,
            onThemeSelected = { theme ->
                viewModel.updateThemeMode(theme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
    
    // Language Selection Dialog
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = settings.language,
            onLanguageSelected = { language ->
                coroutineScope.launch {
                    viewModel.updateLanguage(language)
                    showLanguageDialog = false
                    // 等待DataStore保存完成
                    delay(200)
                    // Restart activity to apply language change
                    if (context is Activity) {
                        context.recreate()
                    }
                }
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
    
    // Auto Lock Selection Dialog
    if (showAutoLockDialog) {
        AutoLockSelectionSheet(
            currentMinutes = settings.autoLockMinutes,
            onMinutesSelected = { minutes ->
                viewModel.updateAutoLockMinutes(minutes)
                showAutoLockDialog = false
            },
            onDismiss = { showAutoLockDialog = false }
        )
    }
    
    // Developer Settings Verification Dialog
    if (showDeveloperVerifyDialog) {
        AlertDialog(
            onDismissRequest = { 
                showDeveloperVerifyDialog = false
                developerPasswordInput = ""
            },
            icon = {
                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(stringResource(R.string.biometric_title))
            },
            text = {
                Column {
                    Text(
                        stringResource(R.string.enter_master_password_confirm),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = developerPasswordInput,
                        onValueChange = { developerPasswordInput = it },
                        label = { Text(stringResource(R.string.master_password)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            val securityManager = takagi.ru.monica.security.SecurityManager(context)
                            if (securityManager.verifyMasterPassword(developerPasswordInput)) {
                                showDeveloperVerifyDialog = false
                                developerPasswordInput = ""
                                onNavigateToDeveloperSettings()
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "密码错误",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                developerPasswordInput = ""
                            }
                        }
                    },
                    enabled = developerPasswordInput.isNotEmpty()
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showDeveloperVerifyDialog = false
                        developerPasswordInput = ""
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showWeakBiometricWarning) {
        AlertDialog(
            onDismissRequest = { showWeakBiometricWarning = false },
            icon = {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(stringResource(R.string.biometric_weak_warning_title)) },
            text = { Text(stringResource(R.string.biometric_weak_warning_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWeakBiometricWarning = false
                        startBiometricEnable()
                    }
                ) {
                    Text(stringResource(R.string.biometric_weak_warning_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWeakBiometricWarning = false }
                ) {
                    Text(stringResource(R.string.biometric_weak_warning_cancel))
                }
            }
        )
    }
    
    // 预览功能对话框
    if (previewFeaturesExpanded) {
        AlertDialog(
            onDismissRequest = { previewFeaturesExpanded = false },
            icon = {
                Icon(
                    Icons.Default.Science,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(stringResource(R.string.preview_features_title))
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        stringResource(R.string.preview_features_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // === 验证器设置分组 ===
                    Text(
                        text = stringResource(R.string.validator_settings_section),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // 统一进度条开关
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { 
                                val newMode = if (settings.validatorUnifiedProgressBar == takagi.ru.monica.data.UnifiedProgressBarMode.ENABLED) {
                                    takagi.ru.monica.data.UnifiedProgressBarMode.DISABLED
                                } else {
                                    takagi.ru.monica.data.UnifiedProgressBarMode.ENABLED
                                }
                                viewModel.updateValidatorUnifiedProgressBar(newMode)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinearScale,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.unified_progress_bar_title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.unified_progress_bar_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.validatorUnifiedProgressBar == takagi.ru.monica.data.UnifiedProgressBarMode.ENABLED,
                            onCheckedChange = { enabled ->
                                viewModel.updateValidatorUnifiedProgressBar(
                                    if (enabled) takagi.ru.monica.data.UnifiedProgressBarMode.ENABLED 
                                    else takagi.ru.monica.data.UnifiedProgressBarMode.DISABLED
                                )
                            }
                        )
                    }
                    
                    // 进度条样式选择
                    var showProgressStyleDialog by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showProgressStyleDialog = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (settings.validatorProgressBarStyle == takagi.ru.monica.data.ProgressBarStyle.WAVE) 
                                Icons.Default.Waves else Icons.Default.Straighten,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.validator_progress_bar_style),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = getProgressBarStyleDisplayName(settings.validatorProgressBarStyle, context),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (showProgressStyleDialog) {
                        ProgressBarStyleDialog(
                            currentStyle = settings.validatorProgressBarStyle,
                            onStyleSelected = { style ->
                                viewModel.updateValidatorProgressBarStyle(style)
                                showProgressStyleDialog = false
                            },
                            onDismiss = { showProgressStyleDialog = false }
                        )
                    }
                    
                    // 平滑进度条开关
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { 
                                viewModel.updateValidatorSmoothProgress(!settings.validatorSmoothProgress)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.smooth_progress_bar_title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.smooth_progress_bar_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.validatorSmoothProgress,
                            onCheckedChange = { enabled ->
                                viewModel.updateValidatorSmoothProgress(enabled)
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // === 实验功能分组 ===
                    Text(
                        text = stringResource(R.string.experimental_features_section),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // 可拖拽底部导航栏开关
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.updateUseDraggableBottomNav(!settings.useDraggableBottomNav) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwipeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.draggable_bottom_nav),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.draggable_bottom_nav_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.useDraggableBottomNav,
                            onCheckedChange = { viewModel.updateUseDraggableBottomNav(it) }
                        )
                    }

                    // 滚动隐藏 FAB 开关
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.updateHideFabOnScroll(!settings.hideFabOnScroll) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.hide_fab_on_scroll_title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.hide_fab_on_scroll_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.hideFabOnScroll,
                            onCheckedChange = { viewModel.updateHideFabOnScroll(it) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { previewFeaturesExpanded = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
    
    // Clear All Data Confirmation Bottom Sheet
    if (showClearDataDialog) {
        var clearPasswords by remember { mutableStateOf(true) }
        var clearTotp by remember { mutableStateOf(true) }
        var clearNotes by remember { mutableStateOf(true) }
        var clearDocuments by remember { mutableStateOf(true) }
        var clearBankCards by remember { mutableStateOf(true) }
        var clearGeneratorHistory by remember { mutableStateOf(true) }
        
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        
        ModalBottomSheet(
            onDismissRequest = { 
                showClearDataDialog = false 
                clearDataPasswordInput = ""
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Title
                Text(
                    text = context.getString(R.string.clear_all_data),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Warning Text
                Text(
                    text = context.getString(R.string.clear_all_data_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Options Group
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = context.getString(R.string.select_data_types_to_clear),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        // Passwords
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { clearPasswords = !clearPasswords }
                        ) {
                            Checkbox(checked = clearPasswords, onCheckedChange = { clearPasswords = it })
                            Text(
                                text = context.getString(R.string.data_type_passwords),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        
                        // TOTP
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { clearTotp = !clearTotp }
                        ) {
                            Checkbox(checked = clearTotp, onCheckedChange = { clearTotp = it })
                            Text(
                                text = context.getString(R.string.data_type_totp),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        
                        // Notes (NEW)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { clearNotes = !clearNotes }
                        ) {
                            Checkbox(checked = clearNotes, onCheckedChange = { clearNotes = it })
                            Text(
                                text = context.getString(R.string.data_type_notes),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        
                        // Documents
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { clearDocuments = !clearDocuments }
                        ) {
                            Checkbox(checked = clearDocuments, onCheckedChange = { clearDocuments = it })
                            Text(
                                text = context.getString(R.string.data_type_documents),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        
                        // Bank Cards
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { clearBankCards = !clearBankCards }
                        ) {
                            Checkbox(checked = clearBankCards, onCheckedChange = { clearBankCards = it })
                            Text(
                                text = context.getString(R.string.data_type_bank_cards),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        
                        // History
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { clearGeneratorHistory = !clearGeneratorHistory }
                        ) {
                            Checkbox(checked = clearGeneratorHistory, onCheckedChange = { clearGeneratorHistory = it })
                            Text(
                                text = context.getString(R.string.data_type_generator_history),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Password Validation
                OutlinedTextField(
                    value = clearDataPasswordInput,
                    onValueChange = { clearDataPasswordInput = it },
                    label = { Text(context.getString(R.string.enter_master_password_to_confirm)) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel
                    OutlinedButton(
                        onClick = { 
                            showClearDataDialog = false 
                            clearDataPasswordInput = ""
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = CircleShape
                    ) {
                        Text(context.getString(R.string.cancel))
                    }
                    
                    // Confirm Delete
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val securityManager = takagi.ru.monica.security.SecurityManager(context)
                                if (securityManager.verifyMasterPassword(clearDataPasswordInput)) {
                                    showClearDataDialog = false
                                    clearDataPasswordInput = ""
                                    onClearAllData(
                                        clearPasswords, 
                                        clearTotp,
                                        clearNotes, 
                                        clearDocuments, 
                                        clearBankCards, 
                                        clearGeneratorHistory
                                    )
                                    Toast.makeText(context, context.getString(R.string.clearing_data), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.password_incorrect), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        enabled = clearDataPasswordInput.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(context.getString(R.string.confirm))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
        )
        content()
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    trailingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (trailingContent != null) {
                trailingContent()
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 带开关的设置项组件
 */
@Composable
fun SettingsItemWithSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.38f),
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) {
                        LocalContentColor.current
                    } else {
                        LocalContentColor.current.copy(alpha = 0.38f)
                    }
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    }
                )
            }
            
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

/**
 * 带复选框的行组件
 */
@Composable
fun CheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun BottomNavConfigRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    switchEnabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = switchEnabled
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = stringResource(R.string.bottom_nav_move_up)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = stringResource(R.string.bottom_nav_move_down)
                    )
                }
            }
        }
    }
}

private fun BottomNavContentTab.toIcon(): ImageVector = when (this) {
    BottomNavContentTab.PASSWORDS -> Icons.Default.Lock
    BottomNavContentTab.AUTHENTICATOR -> Icons.Default.Security
    BottomNavContentTab.CARD_WALLET -> Icons.Default.Wallet
    BottomNavContentTab.GENERATOR -> Icons.Default.AutoAwesome
    BottomNavContentTab.NOTES -> Icons.Default.Note
    BottomNavContentTab.TIMELINE -> Icons.Default.AccountTree
    BottomNavContentTab.PASSKEY -> Icons.Default.Key
    BottomNavContentTab.VAULT -> Icons.Default.Dataset
    BottomNavContentTab.SEND -> Icons.AutoMirrored.Default.Send
}

private fun BottomNavContentTab.toLabelRes(): Int = when (this) {
    BottomNavContentTab.PASSWORDS -> R.string.nav_passwords
    BottomNavContentTab.AUTHENTICATOR -> R.string.nav_authenticator
    BottomNavContentTab.CARD_WALLET -> R.string.nav_card_wallet
    BottomNavContentTab.GENERATOR -> R.string.nav_generator
    BottomNavContentTab.NOTES -> R.string.nav_notes
    BottomNavContentTab.TIMELINE -> R.string.nav_timeline
    BottomNavContentTab.PASSKEY -> R.string.nav_passkey
    BottomNavContentTab.VAULT -> R.string.nav_v2_vault
    BottomNavContentTab.SEND -> R.string.nav_v2_send
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.theme)) },
        text = {
            Column {
                ThemeMode.values().forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = theme == currentTheme,
                            onClick = { onThemeSelected(theme) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(getThemeDisplayName(theme, context))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.ok))
            }
        }
    )
}

@Composable
fun LanguageSelectionDialog(
    currentLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.language)) },
        text = {
            Column {
                Language.values().forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = language == currentLanguage,
                            onClick = { onLanguageSelected(language) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(getLanguageDisplayName(language, context))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.ok))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoLockSelectionSheet(
    currentMinutes: Int,
    onMinutesSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val options = listOf(0, 1, 5, 10, 60, 300, 1440, -1) // 0=立即, 1/5/10分钟, 60=1小时, 300=5小时, 1440=1天, -1=从不
    val sheetState = rememberModalBottomSheetState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = context.getString(R.string.auto_lock),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            options.forEach { minutes ->
                val isSelected = minutes == currentMinutes
                val containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                }
                
                val contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(containerColor)
                        .clickable { onMinutesSelected(minutes) }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            minutes == 0 -> Icons.Default.LockOpen
                            minutes == -1 -> Icons.Default.Lock
                            minutes >= 1440 -> Icons.Default.Bedtime
                            else -> Icons.Default.Timer
                        },
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Text(
                        text = getAutoLockDisplayName(minutes, context),
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = contentColor
                        )
                    }
                }
            }
        }
    }
}

// Helper functions
private fun getThemeDisplayName(theme: ThemeMode, context: android.content.Context): String {
    return when (theme) {
        ThemeMode.SYSTEM -> context.getString(R.string.theme_system)
        ThemeMode.LIGHT -> context.getString(R.string.theme_light)
        ThemeMode.DARK -> context.getString(R.string.theme_dark)
    }
}

private fun getLanguageDisplayName(language: Language, context: android.content.Context): String {
    return when (language) {
        Language.SYSTEM -> context.getString(R.string.language_system)
        Language.ENGLISH -> context.getString(R.string.language_english)
        Language.CHINESE -> context.getString(R.string.language_chinese)
        Language.VIETNAMESE -> context.getString(R.string.language_vietnamese)
        Language.JAPANESE -> context.getString(R.string.language_japanese)
    }
}

private fun getAutoLockDisplayName(minutes: Int, context: android.content.Context): String {
    return when (minutes) {
        0 -> context.getString(R.string.auto_lock_immediately)
        1 -> context.getString(R.string.auto_lock_1_minute)
        5 -> context.getString(R.string.auto_lock_5_minutes)
        10 -> context.getString(R.string.auto_lock_10_minutes)
        15 -> context.getString(R.string.auto_lock_15_minutes)
        30 -> context.getString(R.string.auto_lock_30_minutes)
        60 -> context.getString(R.string.auto_lock_1_hour)
        300 -> context.getString(R.string.auto_lock_5_hours)
        1440 -> context.getString(R.string.auto_lock_1_day)
        -1 -> context.getString(R.string.auto_lock_never)
        else -> "$minutes ${context.getString(R.string.auto_lock_5_minutes).substringAfter("5")}"
    }
}

private fun getColorSchemeDisplayName(colorScheme: takagi.ru.monica.data.ColorScheme, context: android.content.Context): String {
    return when (colorScheme) {
        takagi.ru.monica.data.ColorScheme.DEFAULT -> context.getString(R.string.default_color_scheme)
        takagi.ru.monica.data.ColorScheme.OCEAN_BLUE -> context.getString(R.string.ocean_blue_scheme)
        takagi.ru.monica.data.ColorScheme.SUNSET_ORANGE -> context.getString(R.string.sunset_orange_scheme)
        takagi.ru.monica.data.ColorScheme.FOREST_GREEN -> context.getString(R.string.forest_green_scheme)
        takagi.ru.monica.data.ColorScheme.TECH_PURPLE -> context.getString(R.string.tech_purple_scheme)
        takagi.ru.monica.data.ColorScheme.BLACK_MAMBA -> context.getString(R.string.black_mamba_scheme)
        takagi.ru.monica.data.ColorScheme.GREY_STYLE -> context.getString(R.string.grey_style_scheme)
        takagi.ru.monica.data.ColorScheme.WATER_LILIES -> context.getString(R.string.water_lilies_scheme)
        takagi.ru.monica.data.ColorScheme.IMPRESSION_SUNRISE -> context.getString(R.string.impression_sunrise_scheme)
        takagi.ru.monica.data.ColorScheme.JAPANESE_BRIDGE -> context.getString(R.string.japanese_bridge_scheme)
        takagi.ru.monica.data.ColorScheme.HAYSTACKS -> context.getString(R.string.haystacks_scheme)
        takagi.ru.monica.data.ColorScheme.ROUEN_CATHEDRAL -> context.getString(R.string.rouen_cathedral_scheme)
        takagi.ru.monica.data.ColorScheme.PARLIAMENT_FOG -> context.getString(R.string.parliament_fog_scheme)
        takagi.ru.monica.data.ColorScheme.CATPPUCCIN_LATTE -> context.getString(R.string.catppuccin_latte_scheme)
        takagi.ru.monica.data.ColorScheme.CATPPUCCIN_FRAPPE -> context.getString(R.string.catppuccin_frappe_scheme)
        takagi.ru.monica.data.ColorScheme.CATPPUCCIN_MACCHIATO -> context.getString(R.string.catppuccin_macchiato_scheme)
        takagi.ru.monica.data.ColorScheme.CATPPUCCIN_MOCHA -> context.getString(R.string.catppuccin_mocha_scheme)
        takagi.ru.monica.data.ColorScheme.CUSTOM -> context.getString(R.string.custom_color_scheme)
    }
}

private fun getProgressBarStyleDisplayName(style: takagi.ru.monica.data.ProgressBarStyle, context: android.content.Context): String {
    return when (style) {
        takagi.ru.monica.data.ProgressBarStyle.LINEAR -> context.getString(R.string.progress_bar_style_linear)
        takagi.ru.monica.data.ProgressBarStyle.WAVE -> context.getString(R.string.progress_bar_style_wave)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun BottomNavSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val bottomNavVisibility = settings.bottomNavVisibility
    val scrollState = rememberScrollState()

    // 准备共享元素 Modifier
    val sharedTransitionScope = takagi.ru.monica.ui.LocalSharedTransitionScope.current
    val animatedVisibilityScope = takagi.ru.monica.ui.LocalAnimatedVisibilityScope.current
    
    var sharedModifier: Modifier = Modifier
    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            sharedModifier = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "bottom_nav_settings_card"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
            )
        }
    }

    Scaffold(
        modifier = sharedModifier,
        topBar = {
            TopAppBar(
                title = { Text(text = context.getString(R.string.bottom_nav_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = context.getString(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = context.getString(R.string.bottom_nav_reorder_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            )

            val bottomNavOrder = settings.bottomNavOrder
            val visibleCount = bottomNavVisibility.visibleCount()
            bottomNavOrder.forEachIndexed { index, tab ->
                val isVisible = bottomNavVisibility.isVisible(tab)
                val switchEnabled = !isVisible || visibleCount > 1
                BottomNavConfigRow(
                    icon = tab.toIcon(),
                    title = context.getString(tab.toLabelRes()),
                    subtitle = context.getString(R.string.bottom_nav_toggle_subtitle),
                    checked = isVisible,
                    switchEnabled = switchEnabled,
                    canMoveUp = index > 0,
                    canMoveDown = index < bottomNavOrder.lastIndex,
                    onCheckedChange = { checked ->
                        viewModel.updateBottomNavVisibility(tab, checked)
                    },
                    onMoveUp = {
                        if (index > 0) {
                            val newOrder = bottomNavOrder.toMutableList().apply {
                                add(index - 1, removeAt(index))
                            }
                            viewModel.updateBottomNavOrder(newOrder)
                        }
                    },
                    onMoveDown = {
                        if (index < bottomNavOrder.lastIndex) {
                            val newOrder = bottomNavOrder.toMutableList().apply {
                                add(index + 1, removeAt(index))
                            }
                            viewModel.updateBottomNavOrder(newOrder)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ProgressBarStyleDialog(
    currentStyle: takagi.ru.monica.data.ProgressBarStyle,
    onStyleSelected: (takagi.ru.monica.data.ProgressBarStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.validator_progress_bar_style)) },
        text = {
            Column {
                takagi.ru.monica.data.ProgressBarStyle.values().forEach { style ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = style == currentStyle,
                            onClick = { 
                                android.util.Log.d("ProgressBarStyleDialog", "User selected style: $style")
                                onStyleSelected(style)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(getProgressBarStyleDisplayName(style, context))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.ok))
            }
        }
    )
}

@Composable
fun NotificationValidatorCard(
    enabled: Boolean,
    autoMatchEnabled: Boolean,
    selectedId: Long,
    totpItems: List<SecureItem>,
    onEnabledChange: (Boolean) -> Unit,
    onAutoMatchChange: (Boolean) -> Unit,
    onValidatorSelected: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // If disabled, collapse
    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
            }
        )
    ) {
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
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.notification_validator_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f)
                    )
                    Text(
                        text = if (enabled) stringResource(R.string.notification_validator_enabled) else stringResource(R.string.notification_validator_disabled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }
                
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
            }
            
            // Expanded Content
            if (expanded && enabled) {
                Divider(modifier = Modifier.padding(horizontal = 16.dp))
                
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    run {
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
    }
}

/**
 * 回收站设置项组件
 */
@Composable
private fun SettingsItemWithTrashConfig(
    trashEnabled: Boolean,
    trashAutoDeleteDays: Int,
    onTrashEnabledChange: (Boolean) -> Unit,
    onAutoDeleteDaysChange: (Int) -> Unit
) {
    var showTrashSettingsSheet by remember { mutableStateOf(false) }
    
    val subtitleText = if (trashEnabled) {
        if (trashAutoDeleteDays > 0) {
            "已启用 · ${trashAutoDeleteDays}天后自动清空"
        } else {
            "已启用 · 不自动清空"
        }
    } else {
        "已禁用 · 删除将永久删除"
    }

    SettingsItem(
        icon = Icons.Default.Delete,
        title = stringResource(R.string.trash_bin),
        subtitle = subtitleText,
        onClick = { showTrashSettingsSheet = true }
    )

    if (showTrashSettingsSheet) {
        TrashSettingsSheet(
            currentSettings = takagi.ru.monica.viewmodel.TrashSettings(trashEnabled, trashAutoDeleteDays),
            onDismiss = { showTrashSettingsSheet = false },
            onConfirm = { enabled, days ->
                onTrashEnabledChange(enabled)
                onAutoDeleteDaysChange(days)
                showTrashSettingsSheet = false
            }
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
    val commonAccountPreferences = remember { takagi.ru.monica.data.CommonAccountPreferences(context) }
    
    val commonInfo by commonAccountPreferences.commonAccountInfo.collectAsState(
        initial = takagi.ru.monica.data.CommonAccountInfo()
    )
    
    var expanded by remember { mutableStateOf(false) }
    var email by remember(commonInfo.email) { mutableStateOf(commonInfo.email) }
    var phone by remember(commonInfo.phone) { mutableStateOf(commonInfo.phone) }
    var username by remember(commonInfo.username) { mutableStateOf(commonInfo.username) }
    var autoFillEnabled by remember(commonInfo.autoFillEnabled) { mutableStateOf(commonInfo.autoFillEnabled) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
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
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = context.getString(R.string.common_account_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (commonInfo.hasAnyInfo()) 
                            context.getString(R.string.common_account_configured) 
                        else 
                            context.getString(R.string.common_account_not_configured),
                        style = MaterialTheme.typography.bodyMedium,
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
                        text = context.getString(R.string.common_account_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // 常用邮箱
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(context.getString(R.string.common_account_email)) },
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
                        label = { Text(context.getString(R.string.common_account_phone)) },
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
                        label = { Text(context.getString(R.string.common_account_username)) },
                        placeholder = { Text(context.getString(R.string.common_account_username_hint)) },
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
                                text = context.getString(R.string.common_account_auto_fill),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = context.getString(R.string.common_account_auto_fill_desc),
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
                        Text(context.getString(R.string.save))
                    }
                }
            }
        }
    }
}
