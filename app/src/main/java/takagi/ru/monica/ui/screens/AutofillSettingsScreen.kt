package takagi.ru.monica.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.LaunchedEffect
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch
import takagi.ru.monica.autofill.AutofillPreferences
import takagi.ru.monica.autofill.DomainMatchStrategy
import takagi.ru.monica.autofill.core.AutofillServiceChecker
import takagi.ru.monica.autofill.core.AutofillDiagnostics
import takagi.ru.monica.ui.components.AutofillStatusCard
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutofillSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val autofillPreferences = remember { AutofillPreferences(context) }
    
    val autofillEnabled by autofillPreferences.isAutofillEnabled.collectAsState(initial = false)
    val domainMatchStrategy by autofillPreferences.domainMatchStrategy.collectAsState(initial = DomainMatchStrategy.BASE_DOMAIN)
    val fillSuggestionsEnabled by autofillPreferences.isFillSuggestionsEnabled.collectAsState(initial = true)
    val manualSelectionEnabled by autofillPreferences.isManualSelectionEnabled.collectAsState(initial = true)
    val requestSaveDataEnabled by autofillPreferences.isRequestSaveDataEnabled.collectAsState(initial = true)
    val autoSaveAppInfoEnabled by autofillPreferences.isAutoSaveAppInfoEnabled.collectAsState(initial = true)
    val autoSaveWebsiteInfoEnabled by autofillPreferences.isAutoSaveWebsiteInfoEnabled.collectAsState(initial = true)
    val autoUpdateDuplicatePasswordsEnabled by autofillPreferences.isAutoUpdateDuplicatePasswordsEnabled.collectAsState(initial = false)
    val showSaveNotificationEnabled by autofillPreferences.isShowSaveNotificationEnabled.collectAsState(initial = true)
    val smartTitleGenerationEnabled by autofillPreferences.isSmartTitleGenerationEnabled.collectAsState(initial = true)
    val blacklistEnabled by autofillPreferences.isBlacklistEnabled.collectAsState(initial = true)
    val blacklistPackages by autofillPreferences.blacklistPackages.collectAsState(initial = AutofillPreferences.DEFAULT_BLACKLIST_PACKAGES)
    
    var showStrategyDialog by remember { mutableStateOf(false) }
    var showBlacklistDialog by remember { mutableStateOf(false) }
    
    // 服务状态检查器
    val serviceChecker = remember { AutofillServiceChecker(context) }
    var serviceStatus by remember { mutableStateOf<AutofillServiceChecker.ServiceStatus?>(null) }
    
    // 使用可变状态来追踪系统自动填充服务状态,这样可以实时更新
    var isSystemAutofillEnabled by remember { mutableStateOf(false) }
    
    // 获取生命周期所有者
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 刷新状态的函数
    fun refreshAutofillStatus() {
        scope.launch {
            serviceStatus = serviceChecker.checkServiceStatus()
            
            // 实时检查系统自动填充服务状态
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val autofillManager = context.getSystemService(AutofillManager::class.java)
                isSystemAutofillEnabled = autofillManager?.hasEnabledAutofillServices() == true
                
                android.util.Log.d("AutofillSettings", "刷新状态: isSystemAutofillEnabled = $isSystemAutofillEnabled")
                android.util.Log.d("AutofillSettings", "当前服务: ${autofillManager?.autofillServiceComponentName}")
            }
        }
    }
    
    // 监听生命周期,当界面 Resume 时刷新状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                android.util.Log.d("AutofillSettings", "界面 Resume,刷新自动填充状态")
                refreshAutofillStatus()
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // 首次加载时检查状态
    LaunchedEffect(Unit) {
        refreshAutofillStatus()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            "自动填充",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 添加刷新按钮
                    IconButton(
                        onClick = { 
                            android.util.Log.d("AutofillSettings", "手动刷新状态")
                            refreshAutofillStatus() 
                        }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新状态",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 顶部状态卡片 - 使用新的 AutofillStatusCard 组件
            serviceStatus?.let { status ->
                AutofillStatusCard(
                    status = status,
                    onEnableClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                            intent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        }
                    }
                )
            }
            
            // 系统设置卡片
            SectionCard(
                title = "系统设置",
                icon = Icons.Outlined.Settings,
                iconTint = MaterialTheme.colorScheme.primary
            ) {
                AutofillSettingItem(
                    icon = Icons.Outlined.Smartphone,
                    title = "设置凭据提供商",
                    subtitle = "在系统设置中选择Monica作为自动填充服务",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                            intent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        }
                    }
                )
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    AutofillSettingItem(
                        icon = Icons.Outlined.Key,
                        title = "通行密钥和密码设置",
                        subtitle = "管理通行密钥、密码和数据服务",
                        onClick = {
                            val intent = Intent(Settings.ACTION_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                AutofillSettingItem(
                    icon = Icons.Outlined.Language,
                    title = "Chrome自动填充设置",
                    subtitle = "在Chrome中启用第三方填充服务支持",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://passwords.google.com/options"))
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val chromeIntent = context.packageManager.getLaunchIntentForPackage("com.android.chrome")
                            if (chromeIntent != null) {
                                context.startActivity(chromeIntent)
                            }
                        }
                    }
                )
            }
            
            // 匹配设置卡片
            SectionCard(
                title = "匹配设置",
                icon = Icons.Outlined.Link,
                iconTint = MaterialTheme.colorScheme.secondary
            ) {
                AutofillSettingItem(
                    icon = Icons.Outlined.AccountTree,
                    title = "域名匹配策略",
                    subtitle = DomainMatchStrategy.getDisplayName(domainMatchStrategy),
                    trailingIcon = Icons.Default.ChevronRight,
                    onClick = { showStrategyDialog = true }
                )
            }
            
            // 填充行为卡片
            SectionCard(
                title = "填充行为",
                icon = Icons.Outlined.Input,
                iconTint = MaterialTheme.colorScheme.tertiary
            ) {
                SwitchSettingItem(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "填充建议",
                    subtitle = "自动显示匹配的密码建议",
                    checked = fillSuggestionsEnabled,
                    onCheckedChange = {
                        scope.launch {
                            autofillPreferences.setFillSuggestionsEnabled(it)
                        }
                    }
                )
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchSettingItem(
                    icon = Icons.Outlined.TouchApp,
                    title = "手动选择",
                    subtitle = "允许手动选择要填充的密码",
                    checked = manualSelectionEnabled,
                    onCheckedChange = {
                        scope.launch {
                            autofillPreferences.setManualSelectionEnabled(it)
                        }
                    }
                )
            }
            
            // 保存行为卡片
            SectionCard(
                title = "保存行为",
                icon = Icons.Outlined.Save,
                iconTint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
            ) {
                SwitchSettingItem(
                    icon = Icons.Outlined.AddCircleOutline,
                    title = "启用自动保存",
                    subtitle = "填写表单时自动提示保存密码",
                    checked = requestSaveDataEnabled,
                    onCheckedChange = {
                        scope.launch {
                            autofillPreferences.setRequestSaveDataEnabled(it)
                        }
                    }
                )
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchSettingItem(
                    icon = Icons.Outlined.Sync,
                    title = "自动更新重复密码",
                    subtitle = "检测到重复用户名时自动更新密码",
                    checked = autoUpdateDuplicatePasswordsEnabled,
                    onCheckedChange = {
                        scope.launch {
                            autofillPreferences.setAutoUpdateDuplicatePasswordsEnabled(it)
                        }
                    }
                )
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchSettingItem(
                    icon = Icons.Outlined.Notifications,
                    title = "显示保存通知",
                    subtitle = "密码保存成功后显示通知提示",
                    checked = showSaveNotificationEnabled,
                    onCheckedChange = {
                        scope.launch {
                            autofillPreferences.setShowSaveNotificationEnabled(it)
                        }
                    }
                )
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchSettingItem(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "智能标题生成",
                    subtitle = "自动从应用名或域名生成密码标题",
                    checked = smartTitleGenerationEnabled,
                    onCheckedChange = {
                        scope.launch {
                            autofillPreferences.setSmartTitleGenerationEnabled(it)
                        }
                    }
                )
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchSettingItem(
                    icon = Icons.Outlined.PhoneAndroid,
                    title = "自动保存应用信息",
                    subtitle = "自动记录应用包名到密码条目",
                    checked = autoSaveAppInfoEnabled,
                    onCheckedChange = {
                        scope.launch {
                            autofillPreferences.setAutoSaveAppInfoEnabled(it)
                        }
                    }
                )
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchSettingItem(
                    icon = Icons.Outlined.Public,
                    title = "自动保存网站信息",
                    subtitle = "自动记录网站域名到密码条目",
                    checked = autoSaveWebsiteInfoEnabled,
                    onCheckedChange = {
                        scope.launch {
                            autofillPreferences.setAutoSaveWebsiteInfoEnabled(it)
                        }
                    }
                )
            }
            
            // 黑名单卡片
            SectionCard(
                title = "黑名单",
                icon = Icons.Outlined.Block,
                iconTint = MaterialTheme.colorScheme.error
            ) {
                SwitchSettingItem(
                    icon = Icons.Outlined.Block,
                    title = "启用黑名单",
                    subtitle = "禁止特定应用使用自动填充功能",
                    checked = blacklistEnabled,
                    onCheckedChange = {
                        scope.launch {
                            autofillPreferences.setBlacklistEnabled(it)
                        }
                    }
                )
                
                if (blacklistEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    AutofillSettingItem(
                        icon = Icons.Outlined.Apps,
                        title = "管理黑名单应用",
                        subtitle = "已添加 ${blacklistPackages.size} 个应用",
                        trailingIcon = Icons.Default.ChevronRight,
                        onClick = { showBlacklistDialog = true }
                    )
                }
            }
            
            // 底部说明
            InfoCard()
        }
    }
    
    // 域名匹配策略选择对话框
    if (showStrategyDialog) {
        StrategySelectionDialog(
            currentStrategy = domainMatchStrategy,
            onStrategySelected = { strategy ->
                scope.launch {
                    autofillPreferences.setDomainMatchStrategy(strategy)
                    showStrategyDialog = false
                }
            },
            onDismiss = { showStrategyDialog = false }
        )
    }
    
    // 黑名单管理对话框
    if (showBlacklistDialog) {
        BlacklistManagementDialog(
            blacklistPackages = blacklistPackages,
            onDismiss = { showBlacklistDialog = false },
            onPackageToggle = { packageName, isBlacklisted ->
                scope.launch {
                    if (isBlacklisted) {
                        autofillPreferences.addToBlacklist(packageName)
                    } else {
                        autofillPreferences.removeFromBlacklist(packageName)
                    }
                }
            }
        )
    }
}

// 状态Banner组件
@Composable
fun StatusBanner(isEnabled: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (isEnabled)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else
                                MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isEnabled) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = if (isEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEnabled) "自动填充已启用" else "自动填充未启用",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isEnabled) 
                            "Monica已设置为默认自动填充服务" 
                        else 
                            "请在系统设置中启用Monica",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isEnabled)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else
                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// 分区卡片组件
@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 卡片标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = iconTint
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            // 内容
            Column(content = content)
            
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// 设置项组件
@Composable
fun AutofillSettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingIcon: ImageVector = Icons.Outlined.OpenInNew,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Icon(
            imageVector = trailingIcon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

// 开关设置项组件
@Composable
fun SwitchSettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (checked) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

// 策略选择对话框
@Composable
fun StrategySelectionDialog(
    currentStrategy: DomainMatchStrategy,
    onStrategySelected: (DomainMatchStrategy) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.AccountTree,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        },
        title = { 
            Text(
                "选择域名匹配策略",
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DomainMatchStrategy.values().forEach { strategy ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onStrategySelected(strategy) },
                        color = if (strategy == currentStrategy) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = strategy == currentStrategy,
                                onClick = { onStrategySelected(strategy) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = DomainMatchStrategy.getDisplayName(strategy),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (strategy == currentStrategy) 
                                        FontWeight.SemiBold 
                                    else 
                                        FontWeight.Normal
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = DomainMatchStrategy.getDescription(strategy),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

// 黑名单管理对话框
@Composable
fun BlacklistManagementDialog(
    blacklistPackages: Set<String>,
    onDismiss: () -> Unit,
    onPackageToggle: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    
    // 预定义的常见应用
    val commonApps = listOf(
        "com.tencent.mm" to "微信",
        "com.eg.android.AlipayGphone" to "支付宝",
        "com.unionpay" to "云闪付",
        "com.tencent.mobileqq" to "QQ",
        "com.taobao.taobao" to "淘宝",
        "com.jingdong.app.mall" to "京东",
        "com.tencent.wework" to "企业微信",
        "com.sina.weibo" to "微博"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Block,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                "黑名单管理",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "黑名单中的应用将无法使用自动填充功能",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                commonApps.forEach { (packageName, appName) ->
                    val isInBlacklist = blacklistPackages.contains(packageName)
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { 
                                onPackageToggle(packageName, !isInBlacklist)
                            },
                        color = if (isInBlacklist)
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // 尝试显示应用图标
                                val appIcon = remember(packageName) {
                                    try {
                                        context.packageManager.getApplicationIcon(packageName)
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                
                                if (appIcon != null) {
                                    Image(
                                        painter = rememberDrawablePainter(drawable = appIcon),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Apps,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Column {
                                    Text(
                                        text = appName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Switch(
                                checked = isInBlacklist,
                                onCheckedChange = { checked ->
                                    onPackageToggle(packageName, checked)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

// 信息卡片
@Composable
fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "自动填充功能需要Android 8.0及以上版本。启用后，Monica将在您访问应用和网站时自动填充保存的密码。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
            )
        }
    }
}
