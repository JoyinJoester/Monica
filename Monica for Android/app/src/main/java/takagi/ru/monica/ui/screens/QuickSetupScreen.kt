package takagi.ru.monica.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.BottomNavContentTab
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.AuthenticatorCardDisplayField
import takagi.ru.monica.data.ColorScheme
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.Language
import takagi.ru.monica.data.PasswordCardDisplayField
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.UnifiedProgressBarMode
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.components.TotpCodeCard
import takagi.ru.monica.ui.password.PasswordEntryCard as PasswordEntryCardV2
import takagi.ru.monica.viewmodel.SettingsViewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private enum class QuickSetupStep(
    val title: String,
    val subtitle: String
) {
    WELCOME(
        title = "快速初始化",
        subtitle = "先把最常用的设置一次过掉"
    ),
    SECURITY(
        title = "安全设置",
        subtitle = "设置主密码、生物验证和密保问题"
    ),
    AUTOFILL(
        title = "自动填充",
        subtitle = "引导开启 Monica 的自动填充能力"
    ),
    APPEARANCE(
        title = "外观配色",
        subtitle = "选择一个你看着顺眼的 Monica"
    ),
    BOTTOM_NAV(
        title = "底部导航栏",
        subtitle = "决定常用页面放在哪里"
    ),
    DATA_IMPORT(
        title = "数据导入",
        subtitle = "把已有密码库或备份先接进来"
    ),
    PASSWORD_LIST(
        title = "密码列表调整",
        subtitle = "决定密码列表怎样筛选和聚合"
    ),
    PASSWORD_CARD(
        title = "密码卡片调整",
        subtitle = "决定密码卡片直接显示哪些信息"
    ),
    AUTHENTICATOR_CARD(
        title = "验证器卡片调整",
        subtitle = "决定验证码卡片的信息和进度显示"
    ),
    MONICA_PLUS(
        title = "Monica Plus",
        subtitle = "最后决定是否开启更多扩展能力"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSetupScreen(
    settingsViewModel: SettingsViewModel,
    securityManager: SecurityManager,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
    onOpenMasterPassword: () -> Unit,
    onOpenSecurityQuestions: () -> Unit,
    onOpenAutofillSettings: () -> Unit,
    onOpenBitwardenSettings: () -> Unit,
    onOpenWebDavBackup: () -> Unit,
    onOpenLocalKeePass: () -> Unit,
    onOpenImportData: () -> Unit,
    onOpenMonicaPlus: () -> Unit
) {
    val settings by settingsViewModel.settings.collectAsState()
    val steps = remember { QuickSetupStep.values().toList() }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var showFinishDialog by remember { mutableStateOf(false) }
    val step = steps[stepIndex]

    fun completeWithoutDialog() {
        settingsViewModel.updateQuickSetupCompleted(true)
        onSkip()
    }

    fun finishFlow() {
        settingsViewModel.updateQuickSetupCompleted(true)
        onFinish()
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("初始化完成") },
            text = {
                Text("不用担心，如果设置项有失误，稍后可以再设置里继续修改，你也可以从“功能拓展”中再次找到我")
            },
            confirmButton = {
                Button(onClick = { finishFlow() }) {
                    Text("完成")
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            QuickSetupBottomBar(
                currentIndex = stepIndex,
                total = steps.size,
                primaryText = when (step) {
                    QuickSetupStep.WELCOME -> "开始"
                    QuickSetupStep.MONICA_PLUS -> "完成"
                    else -> "下一步"
                },
                onBack = if (stepIndex > 0) {
                    { stepIndex -= 1 }
                } else {
                    null
                },
                onNext = {
                    if (stepIndex == steps.lastIndex) {
                        showFinishDialog = true
                    } else {
                        stepIndex += 1
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = step.subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                TextButton(onClick = ::completeWithoutDialog) {
                    Text("跳过")
                }
            }
            Spacer(modifier = Modifier.height(18.dp))

            AnimatedContent(
                targetState = stepIndex,
                label = "quick_setup_step",
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (
                        slideInHorizontally(
                            animationSpec = tween(260),
                            initialOffsetX = { it * direction / 2 }
                        ) + fadeIn(animationSpec = tween(220))
                    ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(220),
                            targetOffsetX = { -it * direction / 3 }
                        ) + fadeOut(animationSpec = tween(180))
                    ).using(SizeTransform(clip = false))
                },
                modifier = Modifier.weight(1f)
            ) { targetStep ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (steps[targetStep]) {
                        QuickSetupStep.WELCOME -> WelcomeStep(
                            selectedLanguage = settings.language,
                            onLanguageSelected = settingsViewModel::updateLanguage
                        )

                        QuickSetupStep.SECURITY -> SecurityStep(
                            biometricEnabled = settings.biometricEnabled,
                            masterPasswordSet = securityManager.isMasterPasswordSet(),
                            securityQuestionsSet = securityManager.areSecurityQuestionsSet(),
                            onBiometricChange = settingsViewModel::updateBiometricEnabled,
                            onOpenMasterPassword = onOpenMasterPassword,
                            onOpenSecurityQuestions = onOpenSecurityQuestions
                        )

                        QuickSetupStep.AUTOFILL -> AutofillStep(
                            onOpenAutofillSettings = onOpenAutofillSettings
                        )

                        QuickSetupStep.APPEARANCE -> AppearanceStep(
                            selectedScheme = settings.colorScheme,
                            onSchemeSelected = settingsViewModel::updateColorScheme
                        )

                        QuickSetupStep.BOTTOM_NAV -> BottomNavStep(
                            visibleTabs = settings.bottomNavOrder.filter {
                                settings.bottomNavVisibility.isVisible(it)
                            },
                            allTabs = settings.bottomNavOrder,
                            isVisible = { settings.bottomNavVisibility.isVisible(it) },
                            onVisibilityChange = settingsViewModel::updateBottomNavVisibility
                        )

                        QuickSetupStep.DATA_IMPORT -> DataImportStep(
                            onOpenBitwardenSettings = onOpenBitwardenSettings,
                            onOpenWebDavBackup = onOpenWebDavBackup,
                            onOpenLocalKeePass = onOpenLocalKeePass,
                            onOpenImportData = onOpenImportData
                        )

                        QuickSetupStep.PASSWORD_LIST -> PasswordListAdjustmentStep(
                            aggregateEnabled = settings.passwordPageAggregateEnabled,
                            quickFiltersEnabled = settings.passwordListQuickFiltersEnabled,
                            categoryQuickFiltersEnabled = settings.passwordListCategoryQuickFiltersEnabled,
                            quickAccessEnabled = settings.passwordListQuickAccessEnabled,
                            onAggregateChange = settingsViewModel::updatePasswordPageAggregateEnabled,
                            onQuickFiltersChange = settingsViewModel::updatePasswordListQuickFiltersEnabled,
                            onCategoryQuickFiltersChange = settingsViewModel::updatePasswordListCategoryQuickFiltersEnabled,
                            onQuickAccessChange = settingsViewModel::updatePasswordListQuickAccessEnabled
                        )

                        QuickSetupStep.PASSWORD_CARD -> PasswordCardAdjustmentStep(
                            settings = settings,
                            selectedFields = settings.passwordCardDisplayFields,
                            showAuthenticator = settings.passwordCardShowAuthenticator,
                            hideOtherContentWhenAuthenticator = settings.passwordCardHideOtherContentWhenAuthenticator,
                            onFieldsChange = settingsViewModel::updatePasswordCardDisplayFields,
                            onShowAuthenticatorChange = settingsViewModel::updatePasswordCardShowAuthenticator,
                            onHideOtherContentWhenAuthenticatorChange =
                                settingsViewModel::updatePasswordCardHideOtherContentWhenAuthenticator
                        )

                        QuickSetupStep.AUTHENTICATOR_CARD -> AuthenticatorCardAdjustmentStep(
                            settings = settings,
                            selectedFields = settings.authenticatorCardDisplayFields,
                            unifiedProgressEnabled =
                                settings.validatorUnifiedProgressBar == UnifiedProgressBarMode.ENABLED,
                            smoothProgressEnabled = settings.validatorSmoothProgress,
                            onFieldsChange = settingsViewModel::updateAuthenticatorCardDisplayFields,
                            onUnifiedProgressChange = { enabled ->
                                settingsViewModel.updateValidatorUnifiedProgressBar(
                                    if (enabled) UnifiedProgressBarMode.ENABLED else UnifiedProgressBarMode.DISABLED
                                )
                            },
                            onSmoothProgressChange = settingsViewModel::updateValidatorSmoothProgress
                        )

                        QuickSetupStep.MONICA_PLUS -> MonicaPlusStep(
                            isPlusActivated = settings.isPlusActivated,
                            onOpenMonicaPlus = onOpenMonicaPlus
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit
) {
    var languageExpanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Monica的设置可能有点复杂，所以我可以带你进行快速初始化，是否要开始？",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "下面只保留第一次使用最容易影响体验的设置。你可以跳过，也可以之后在“功能拓展”里重新打开。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { languageExpanded = !languageExpanded },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "语言选择",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = languageLabel(selectedLanguage),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AssistChip(
                        onClick = { languageExpanded = !languageExpanded },
                        label = { Text(if (languageExpanded) "收起" else "更改") }
                    )
                }
                if (languageExpanded) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Language.values().forEach { language ->
                            FilterChip(
                                selected = selectedLanguage == language,
                                onClick = {
                                    onLanguageSelected(language)
                                    languageExpanded = false
                                },
                                label = { Text(languageLabel(language)) },
                                leadingIcon = if (selectedLanguage == language) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityStep(
    biometricEnabled: Boolean,
    masterPasswordSet: Boolean,
    securityQuestionsSet: Boolean,
    onBiometricChange: (Boolean) -> Unit,
    onOpenMasterPassword: () -> Unit,
    onOpenSecurityQuestions: () -> Unit
) {
    SetupActionCard(
        icon = Icons.Default.Password,
        title = "主密码",
        description = if (masterPasswordSet) "已设置，可以用于解锁和保护数据" else "建议先设置一个主密码",
        badge = if (masterPasswordSet) "已完成" else "去设置",
        onClick = onOpenMasterPassword
    )
    SetupSwitchCard(
        icon = Icons.Default.Fingerprint,
        title = "生物验证",
        description = "开启后可以用指纹或面容更快解锁",
        checked = biometricEnabled,
        onCheckedChange = onBiometricChange
    )
    SetupActionCard(
        icon = Icons.Default.QuestionAnswer,
        title = "密保问题",
        description = if (securityQuestionsSet) "已设置，用于忘记主密码时验证身份" else "设置后忘记主密码时更容易找回",
        badge = if (securityQuestionsSet) "已完成" else "去设置",
        onClick = onOpenSecurityQuestions
    )
}

@Composable
private fun AutofillStep(onOpenAutofillSettings: () -> Unit) {
    HeroCard(
        icon = Icons.Default.Shield,
        title = "开启自动填充",
        description = "开启后，Monica 可以在登录表单里提供账号、密码、卡包和验证器相关候选。"
    )
    Button(
        onClick = onOpenAutofillSettings,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
    ) {
        Icon(Icons.Default.Security, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("打开自动填充设置")
    }
    Text(
        text = "如果系统已经开启，直接下一步就可以。后续也可以在设置里继续调整自动填充来源和验证方式。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AppearanceStep(
    selectedScheme: ColorScheme,
    onSchemeSelected: (ColorScheme) -> Unit
) {
    val recommended = listOf(
        ColorScheme.DEFAULT,
        ColorScheme.OCEAN_BLUE,
        ColorScheme.FOREST_GREEN,
        ColorScheme.SUNSET_ORANGE,
        ColorScheme.GREY_STYLE,
        ColorScheme.BLACK_MAMBA
    )
    SetupSection(title = "配色", icon = Icons.Default.Palette) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            recommended.forEach { scheme ->
                ColorSchemeRow(
                    scheme = scheme,
                    selected = selectedScheme == scheme,
                    onClick = { onSchemeSelected(scheme) }
                )
            }
        }
    }
}

@Composable
private fun BottomNavStep(
    visibleTabs: List<BottomNavContentTab>,
    allTabs: List<BottomNavContentTab>,
    isVisible: (BottomNavContentTab) -> Boolean,
    onVisibilityChange: (BottomNavContentTab, Boolean) -> Unit
) {
    SetupSection(title = "底部预览", icon = Icons.Default.Widgets) {
        MonicaBottomNavPreview(
            tabs = visibleTabs,
            selectedTab = visibleTabs.firstOrNull() ?: BottomNavContentTab.PASSWORDS
        )
    }

    SetupSection(title = "显示项目", icon = Icons.Default.DashboardCustomize) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            allTabs.forEach { tab ->
                SetupSwitchRow(
                    icon = tabIcon(tab),
                    title = tabLabel(tab),
                    checked = isVisible(tab),
                    onCheckedChange = { onVisibilityChange(tab, it) }
                )
            }
        }
    }
}
@Composable
private fun ThemePreviewCard(scheme: ColorScheme) {
    val swatches = schemeSwatches(scheme)
    val primary = swatches.first()
    val secondary = swatches.getOrElse(1) { primary }
    val container = swatches.getOrElse(2) { MaterialTheme.colorScheme.primaryContainer }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(12.dp)
                        .clip(CircleShape)
                        .background(primary)
                )
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(9.dp)
                        .clip(CircleShape)
                        .background(container)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                swatches.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(2) { index ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(88.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (index == 0) primary.copy(alpha = 0.92f) else MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = if (index == 0) Icons.Default.Lock else Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = if (index == 0) Color.White else primary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(if (index == 0) Color.White.copy(alpha = 0.9f) else secondary.copy(alpha = 0.72f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(7.dp)
                                .clip(CircleShape)
                                .background(if (index == 0) Color.White.copy(alpha = 0.55f) else container)
                        )
                    }
                }
            }
        }
        MiniNavigationBarPreview(primary = primary)
    }
}

@Composable
private fun MiniNavigationBarPreview(primary: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            Icons.Default.Lock to true,
            Icons.Default.Security to false,
            Icons.Default.Wallet to false,
            Icons.Default.Settings to false
        ).forEach { (icon, selected) ->
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .width(if (selected) 58.dp else 42.dp)
                    .clip(CircleShape)
                    .background(if (selected) primary.copy(alpha = 0.18f) else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun MonicaBottomNavPreview(
    tabs: List<BottomNavContentTab>,
    selectedTab: BottomNavContentTab
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (tabs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "至少保留一个常用入口会更顺手",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            NavigationBar(
                tonalElevation = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = {},
                        icon = {
                            Icon(
                                imageVector = tabIcon(tab),
                                contentDescription = tabLabel(tab)
                            )
                        },
                        label = {
                            Text(
                                text = tabShortLabel(tab),
                                maxLines = 2,
                                overflow = TextOverflow.Clip
                            )
                        }
                    )
                }
                NavigationBarItem(
                    selected = false,
                    onClick = {},
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                    },
                    label = {
                        Text(
                            text = "设置",
                            maxLines = 2,
                            overflow = TextOverflow.Clip
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DataImportStep(
    onOpenBitwardenSettings: () -> Unit,
    onOpenWebDavBackup: () -> Unit,
    onOpenLocalKeePass: () -> Unit,
    onOpenImportData: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "你可以先把数据接进 Monica",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "如果你已经在用其他密码库或备份方式，可以从这里先完成连接或导入。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    SetupActionCard(
        icon = Icons.Default.Shield,
        title = "链接到 Bitwarden",
        description = "打开 Bitwarden 同步设置页，连接后可以同步已有密码库。",
        badge = "去链接",
        onClick = onOpenBitwardenSettings
    )
    SetupActionCard(
        icon = Icons.Default.Link,
        title = "链接 WebDAV",
        description = "用于快照备份，把 Monica 数据备份到你的 WebDAV 存储。",
        badge = "去设置",
        onClick = onOpenWebDavBackup
    )
    SetupActionCard(
        icon = Icons.Default.Key,
        title = "链接到 KeePass",
        description = "打开本地 KeePass 设置页面，连接本地 KDBX 密码库。",
        badge = "去链接",
        onClick = onOpenLocalKeePass
    )
    SetupActionCard(
        icon = Icons.Default.UploadFile,
        title = "手动文件导入",
        description = "进入导入页面，从文件中手动导入已有数据。",
        badge = "去导入",
        onClick = onOpenImportData
    )
}

@Composable
private fun PasswordListAdjustmentStep(
    aggregateEnabled: Boolean,
    quickFiltersEnabled: Boolean,
    categoryQuickFiltersEnabled: Boolean,
    quickAccessEnabled: Boolean,
    onAggregateChange: (Boolean) -> Unit,
    onQuickFiltersChange: (Boolean) -> Unit,
    onCategoryQuickFiltersChange: (Boolean) -> Unit,
    onQuickAccessChange: (Boolean) -> Unit
) {
    SetupSection(title = "列表内容", icon = Icons.Default.DashboardCustomize) {
        SetupSwitchRow(
            icon = Icons.Default.Widgets,
            title = "在密码页面管理所有项目",
            checked = aggregateEnabled,
            onCheckedChange = onAggregateChange
        )
        SetupSwitchRow(
            icon = Icons.Default.Security,
            title = "显示最近/常用快捷入口",
            checked = quickAccessEnabled,
            onCheckedChange = onQuickAccessChange
        )
    }
    SetupSection(title = "筛选", icon = Icons.Default.Storage) {
        SetupSwitchRow(
            icon = Icons.Default.Check,
            title = "显示快捷筛选",
            checked = quickFiltersEnabled,
            onCheckedChange = onQuickFiltersChange
        )
        SetupSwitchRow(
            icon = Icons.Default.DashboardCustomize,
            title = "显示分类快捷筛选",
            checked = categoryQuickFiltersEnabled,
            onCheckedChange = onCategoryQuickFiltersChange
        )
    }
}

@Composable
private fun PasswordCardAdjustmentStep(
    settings: AppSettings,
    selectedFields: List<PasswordCardDisplayField>,
    showAuthenticator: Boolean,
    hideOtherContentWhenAuthenticator: Boolean,
    onFieldsChange: (List<PasswordCardDisplayField>) -> Unit,
    onShowAuthenticatorChange: (Boolean) -> Unit,
    onHideOtherContentWhenAuthenticatorChange: (Boolean) -> Unit
) {
    PasswordCardLivePreview(settings = settings, selectedFields = selectedFields)
    SetupSection(title = "显示字段", icon = Icons.Default.Password) {
        SetupSwitchRow(
            icon = Icons.Default.Key,
            title = "显示用户名",
            checked = PasswordCardDisplayField.USERNAME in selectedFields,
            onCheckedChange = {
                onFieldsChange(togglePasswordCardField(selectedFields, PasswordCardDisplayField.USERNAME, it))
            }
        )
        SetupSwitchRow(
            icon = Icons.Default.Language,
            title = "显示网站",
            checked = PasswordCardDisplayField.WEBSITE in selectedFields,
            onCheckedChange = {
                onFieldsChange(togglePasswordCardField(selectedFields, PasswordCardDisplayField.WEBSITE, it))
            }
        )
    }
    SetupSection(title = "验证器联动", icon = Icons.Default.Security) {
        SetupSwitchRow(
            icon = Icons.Default.Lock,
            title = "显示绑定验证器",
            checked = showAuthenticator,
            onCheckedChange = onShowAuthenticatorChange
        )
        SetupSwitchRow(
            icon = Icons.Default.Shield,
            title = "显示验证器时隐藏其他内容",
            checked = hideOtherContentWhenAuthenticator,
            onCheckedChange = onHideOtherContentWhenAuthenticatorChange
        )
    }
}

@Composable
private fun AuthenticatorCardAdjustmentStep(
    settings: AppSettings,
    selectedFields: List<AuthenticatorCardDisplayField>,
    unifiedProgressEnabled: Boolean,
    smoothProgressEnabled: Boolean,
    onFieldsChange: (List<AuthenticatorCardDisplayField>) -> Unit,
    onUnifiedProgressChange: (Boolean) -> Unit,
    onSmoothProgressChange: (Boolean) -> Unit
) {
    AuthenticatorCardLivePreview(settings = settings, selectedFields = selectedFields)
    SetupSection(title = "显示字段", icon = Icons.Default.Security) {
        SetupSwitchRow(
            icon = Icons.Default.Shield,
            title = "显示发行方",
            checked = AuthenticatorCardDisplayField.ISSUER in selectedFields,
            onCheckedChange = {
                onFieldsChange(toggleAuthenticatorField(selectedFields, AuthenticatorCardDisplayField.ISSUER, it))
            }
        )
        SetupSwitchRow(
            icon = Icons.Default.Key,
            title = "显示账号名",
            checked = AuthenticatorCardDisplayField.ACCOUNT_NAME in selectedFields,
            onCheckedChange = {
                onFieldsChange(toggleAuthenticatorField(selectedFields, AuthenticatorCardDisplayField.ACCOUNT_NAME, it))
            }
        )
    }
    SetupSection(title = "进度显示", icon = Icons.Default.AutoAwesome) {
        SetupSwitchRow(
            icon = Icons.Default.Widgets,
            title = "启用统一进度条",
            checked = unifiedProgressEnabled,
            onCheckedChange = onUnifiedProgressChange
        )
        SetupSwitchRow(
            icon = Icons.Default.AutoAwesome,
            title = "启用平滑进度动画",
            checked = smoothProgressEnabled,
            onCheckedChange = onSmoothProgressChange
        )
    }
}

@Composable
private fun PasswordCardLivePreview(
    settings: AppSettings,
    selectedFields: List<PasswordCardDisplayField>
) {
    val previewEntry = remember {
        PasswordEntry(
            title = "GitHub - Monica-all",
            website = "github.com",
            username = "joyins",
            password = "******",
            appName = "GitHub",
            authenticatorKey = "JBSWY3DPEHPK3PXP"
        )
    }
    SetupSection(title = "实时预览", icon = Icons.Default.Password) {
        PasswordEntryCardV2(
            entry = previewEntry,
            onClick = {},
            isSingleCard = true,
            iconCardsEnabled = settings.iconCardsEnabled && settings.passwordPageIconEnabled,
            unmatchedIconHandlingStrategy = settings.unmatchedIconHandlingStrategy,
            passwordCardDisplayMode = settings.passwordCardDisplayMode,
            passwordCardDisplayFields = selectedFields,
            showAuthenticator = settings.passwordCardShowAuthenticator,
            hideOtherContentWhenAuthenticator = settings.passwordCardHideOtherContentWhenAuthenticator,
            totpTimeOffsetSeconds = settings.totpTimeOffset,
            smoothAuthenticatorProgress = settings.validatorSmoothProgress,
            enableSharedBounds = false
        )
        Text(
            text = "卡片上只会显示已选择字段中的前 3 项。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AuthenticatorCardLivePreview(
    settings: AppSettings,
    selectedFields: List<AuthenticatorCardDisplayField>
) {
    val previewItem = remember {
        SecureItem(
            itemType = ItemType.TOTP,
            title = "GitHub",
            itemData = Json.encodeToString(
                TotpData(
                    secret = "JBSWY3DPEHPK3PXP",
                    issuer = "GitHub",
                    accountName = "joyins@example.com",
                    link = "github.com"
                )
            )
        )
    }
    SetupSection(title = "实时预览", icon = Icons.Default.Security) {
        TotpCodeCard(
            item = previewItem,
            onCopyCode = {},
            appSettings = settings.copy(
                authenticatorCardDisplayFields = selectedFields,
                iconCardsEnabled = settings.iconCardsEnabled && settings.authenticatorPageIconEnabled
            )
        )
        Text(
            text = "验证器卡片会按这里的字段和进度设置实时展示。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MonicaPlusStep(
    isPlusActivated: Boolean,
    onOpenMonicaPlus: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = if (isPlusActivated) "Monica Plus 已开启" else "是否要开启 Monica Plus？",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (isPlusActivated) {
                "Plus 能力已经可用，完成初始化后就可以继续使用。"
            } else {
                "如果你需要 Plus 配色、更多同步与扩展能力，可以现在去开启；不需要的话直接完成就行。"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!isPlusActivated) {
            Button(
                onClick = onOpenMonicaPlus,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("打开 Monica Plus")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }
    }
}

private fun togglePasswordCardField(
    fields: List<PasswordCardDisplayField>,
    field: PasswordCardDisplayField,
    enabled: Boolean
): List<PasswordCardDisplayField> {
    val order = listOf(PasswordCardDisplayField.USERNAME, PasswordCardDisplayField.WEBSITE)
    return order.filter { candidate ->
        if (candidate == field) enabled else candidate in fields
    }
}

private fun toggleAuthenticatorField(
    fields: List<AuthenticatorCardDisplayField>,
    field: AuthenticatorCardDisplayField,
    enabled: Boolean
): List<AuthenticatorCardDisplayField> {
    val order = listOf(
        AuthenticatorCardDisplayField.ISSUER,
        AuthenticatorCardDisplayField.ACCOUNT_NAME
    )
    return order.filter { candidate ->
        if (candidate == field) enabled else candidate in fields
    }
}

@Composable
private fun QuickSetupBottomBar(
    currentIndex: Int,
    total: Int,
    primaryText: String,
    onBack: (() -> Unit)?,
    onNext: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = "（${(currentIndex + 1).toString().padStart(2, '0')}/${total.toString().padStart(2, '0')}）",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                )
            }
            if (onBack != null) {
                OutlinedButton(onClick = onBack) {
                    Text("上一步")
                }
            }
            Button(onClick = onNext) {
                Text(primaryText)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun HeroCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun SetupSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}

@Composable
private fun SetupActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    badge: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconSurface(icon = icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssistChip(
                onClick = onClick,
                label = { Text(badge) }
            )
        }
    }
}

@Composable
private fun SetupSwitchCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconSurface(icon = icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SetupSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text = title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ColorSchemeRow(
    scheme: ColorScheme,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = tween(180),
        label = "quick_setup_color_scale"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ColorSchemePreviewIcon(scheme = scheme)
        Text(
            text = colorSchemeLabel(scheme),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null)
        }
    }
}

@Composable
private fun ColorSchemePreviewIcon(scheme: ColorScheme) {
    val swatches = schemeSwatches(scheme)
    Surface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            swatches.forEach { color ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun QuestionCard(
    question: String,
    description: String,
    selectedYes: Boolean,
    onAnswer: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnswerButton(
                    text = "是",
                    selected = selectedYes,
                    onClick = { onAnswer(true) },
                    modifier = Modifier.weight(1f)
                )
                AnswerButton(
                    text = "不是",
                    selected = !selectedYes,
                    onClick = { onAnswer(false) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AnswerButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun IconSurface(icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun languageLabel(language: Language): String = when (language) {
    Language.SYSTEM -> "跟随系统"
    Language.ENGLISH -> "English"
    Language.CHINESE -> "简体中文"
    Language.VIETNAMESE -> "Tiếng Việt"
    Language.JAPANESE -> "日本語"
}

private fun colorSchemeLabel(scheme: ColorScheme): String = when (scheme) {
    ColorScheme.DEFAULT -> "默认"
    ColorScheme.OCEAN_BLUE -> "海洋蓝"
    ColorScheme.SUNSET_ORANGE -> "日落橙"
    ColorScheme.FOREST_GREEN -> "森林绿"
    ColorScheme.TECH_PURPLE -> "科技紫"
    ColorScheme.BLACK_MAMBA -> "黑曼巴"
    ColorScheme.GREY_STYLE -> "小黑紫"
    ColorScheme.WATER_LILIES -> "睡莲"
    ColorScheme.IMPRESSION_SUNRISE -> "印象·日出"
    ColorScheme.JAPANESE_BRIDGE -> "日本桥"
    ColorScheme.HAYSTACKS -> "干草堆"
    ColorScheme.ROUEN_CATHEDRAL -> "鲁昂大教堂"
    ColorScheme.PARLIAMENT_FOG -> "国会大厦"
    ColorScheme.CATPPUCCIN_LATTE -> "Catppuccin Latte"
    ColorScheme.CATPPUCCIN_FRAPPE -> "Catppuccin Frappé"
    ColorScheme.CATPPUCCIN_MACCHIATO -> "Catppuccin Macchiato"
    ColorScheme.CATPPUCCIN_MOCHA -> "Catppuccin Mocha"
    ColorScheme.CUSTOM -> "自定义"
}

private fun schemeSwatches(scheme: ColorScheme): List<Color> = when (scheme) {
    ColorScheme.OCEAN_BLUE -> listOf(Color(0xFF0B57D0), Color(0xFF00A1C9), Color(0xFFB9E9F2))
    ColorScheme.SUNSET_ORANGE -> listOf(Color(0xFFB84A00), Color(0xFFFF8A50), Color(0xFFFFD7C2))
    ColorScheme.FOREST_GREEN -> listOf(Color(0xFF006C47), Color(0xFF3E8F65), Color(0xFFC8E6C9))
    ColorScheme.GREY_STYLE -> listOf(Color(0xFF4B465C), Color(0xFF7C748D), Color(0xFFE5E0EC))
    ColorScheme.BLACK_MAMBA -> listOf(Color(0xFF0B0B0D), Color(0xFFD9A900), Color(0xFF8F5CFF))
    else -> listOf(Color(0xFF6750A4), Color(0xFF625B71), Color(0xFFEADDFF))
}

private fun tabLabel(tab: BottomNavContentTab): String = when (tab) {
    BottomNavContentTab.VAULT_V2 -> "密码库（测试）"
    BottomNavContentTab.PASSWORDS -> "密码"
    BottomNavContentTab.AUTHENTICATOR -> "验证器"
    BottomNavContentTab.CARD_WALLET -> "卡包"
    BottomNavContentTab.GENERATOR -> "生成器"
    BottomNavContentTab.NOTES -> "笔记"
    BottomNavContentTab.SEND -> "发送"
    BottomNavContentTab.PASSKEY -> "通行密钥"
}

private fun tabShortLabel(tab: BottomNavContentTab): String = when (tab) {
    BottomNavContentTab.VAULT_V2 -> "密码库"
    BottomNavContentTab.PASSWORDS -> "密码"
    BottomNavContentTab.AUTHENTICATOR -> "验证"
    BottomNavContentTab.CARD_WALLET -> "卡包"
    BottomNavContentTab.GENERATOR -> "生成"
    BottomNavContentTab.NOTES -> "笔记"
    BottomNavContentTab.SEND -> "发送"
    BottomNavContentTab.PASSKEY -> "密钥"
}

private fun tabIcon(tab: BottomNavContentTab): ImageVector = when (tab) {
    BottomNavContentTab.VAULT_V2 -> Icons.Default.Home
    BottomNavContentTab.PASSWORDS -> Icons.Default.Lock
    BottomNavContentTab.AUTHENTICATOR -> Icons.Default.Security
    BottomNavContentTab.CARD_WALLET -> Icons.Default.Wallet
    BottomNavContentTab.GENERATOR -> Icons.Default.AutoAwesome
    BottomNavContentTab.NOTES -> Icons.Default.Note
    BottomNavContentTab.SEND -> Icons.Default.Send
    BottomNavContentTab.PASSKEY -> Icons.Default.Key
}
