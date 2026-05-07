package takagi.ru.monica.ui.screens

import android.app.Activity
import androidx.annotation.StringRes
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private enum class QuickSetupStep(
    val titleRes: Int,
    val subtitleRes: Int
) {
    WELCOME(
        titleRes = R.string.qs_step_welcome_title,
        subtitleRes = R.string.qs_step_welcome_subtitle
    ),
    SECURITY(
        titleRes = R.string.qs_step_security_title,
        subtitleRes = R.string.qs_step_security_subtitle
    ),
    AUTOFILL(
        titleRes = R.string.qs_step_autofill_title,
        subtitleRes = R.string.qs_step_autofill_subtitle
    ),
    APPEARANCE(
        titleRes = R.string.qs_step_appearance_title,
        subtitleRes = R.string.qs_step_appearance_subtitle
    ),
    BOTTOM_NAV(
        titleRes = R.string.qs_step_bottom_nav_title,
        subtitleRes = R.string.qs_step_bottom_nav_subtitle
    ),
    DATA_IMPORT(
        titleRes = R.string.qs_step_data_import_title,
        subtitleRes = R.string.qs_step_data_import_subtitle
    ),
    PASSWORD_LIST(
        titleRes = R.string.qs_step_password_list_title,
        subtitleRes = R.string.qs_step_password_list_subtitle
    ),
    PASSWORD_CARD(
        titleRes = R.string.qs_step_password_card_title,
        subtitleRes = R.string.qs_step_password_card_subtitle
    ),
    AUTHENTICATOR_CARD(
        titleRes = R.string.qs_step_authenticator_card_title,
        subtitleRes = R.string.qs_step_authenticator_card_subtitle
    ),
    MONICA_PLUS(
        titleRes = R.string.qs_step_monica_plus_title,
        subtitleRes = R.string.qs_step_monica_plus_subtitle
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
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

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
            title = { Text(stringResource(R.string.qs_finish_dialog_title)) },
            text = {
                Text(stringResource(R.string.qs_finish_dialog_message))
            },
            confirmButton = {
                Button(onClick = { finishFlow() }) {
                    Text(stringResource(R.string.qs_finish))
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
                    QuickSetupStep.WELCOME -> stringResource(R.string.qs_start)
                    QuickSetupStep.MONICA_PLUS -> stringResource(R.string.qs_finish)
                    else -> stringResource(R.string.qs_next)
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
                        text = stringResource(step.titleRes),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(step.subtitleRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                TextButton(onClick = ::completeWithoutDialog) {
                    Text(stringResource(R.string.qs_skip))
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
                            onLanguageSelected = { language ->
                                coroutineScope.launch {
                                    settingsViewModel.updateLanguage(language)
                                    delay(200)
                                    if (context is Activity) {
                                        context.recreate()
                                    }
                                }
                            }
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
                text = stringResource(R.string.qs_welcome_heading),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.qs_welcome_description),
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
                            text = stringResource(R.string.qs_language_selection),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(languageLabelRes(selectedLanguage)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AssistChip(
                        onClick = { languageExpanded = !languageExpanded },
                        label = { Text(stringResource(if (languageExpanded) R.string.qs_collapse else R.string.qs_change)) }
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
                                label = { Text(stringResource(languageLabelRes(language))) },
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
        title = stringResource(R.string.qs_master_password),
        description = stringResource(if (masterPasswordSet) R.string.qs_master_password_set else R.string.qs_master_password_unset),
        badge = stringResource(if (masterPasswordSet) R.string.qs_completed else R.string.qs_go_setup),
        onClick = onOpenMasterPassword
    )
    SetupSwitchCard(
        icon = Icons.Default.Fingerprint,
        title = stringResource(R.string.qs_biometric),
        description = stringResource(R.string.qs_biometric_desc),
        checked = biometricEnabled,
        onCheckedChange = onBiometricChange
    )
    SetupActionCard(
        icon = Icons.Default.QuestionAnswer,
        title = stringResource(R.string.qs_security_questions),
        description = stringResource(if (securityQuestionsSet) R.string.qs_security_questions_set else R.string.qs_security_questions_unset),
        badge = stringResource(if (securityQuestionsSet) R.string.qs_completed else R.string.qs_go_setup),
        onClick = onOpenSecurityQuestions
    )
}

@Composable
private fun AutofillStep(onOpenAutofillSettings: () -> Unit) {
    HeroCard(
        icon = Icons.Default.Shield,
        title = stringResource(R.string.qs_autofill_enable),
        description = stringResource(R.string.qs_autofill_enable_desc)
    )
    Button(
        onClick = onOpenAutofillSettings,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
    ) {
        Icon(Icons.Default.Security, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.qs_open_autofill_settings))
    }
    Text(
        text = stringResource(R.string.qs_autofill_hint),
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
    SetupSection(title = stringResource(R.string.qs_color_scheme), icon = Icons.Default.Palette) {
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
    SetupSection(title = stringResource(R.string.qs_bottom_preview), icon = Icons.Default.Widgets) {
        MonicaBottomNavPreview(
            tabs = visibleTabs,
            selectedTab = visibleTabs.firstOrNull() ?: BottomNavContentTab.PASSWORDS
        )
    }

    SetupSection(title = stringResource(R.string.qs_display_items), icon = Icons.Default.DashboardCustomize) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            allTabs.forEach { tab ->
                SetupSwitchRow(
                    icon = tabIcon(tab),
                    title = stringResource(tabLabelRes(tab)),
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
                    text = stringResource(R.string.qs_keep_at_least_one_tab),
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
                                contentDescription = stringResource(tabLabelRes(tab))
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(tabShortLabelRes(tab)),
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
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.nav_settings_short),
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
            text = stringResource(R.string.qs_data_import_heading),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.qs_data_import_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    SetupActionCard(
        icon = Icons.Default.Shield,
        title = stringResource(R.string.qs_link_bitwarden),
        description = stringResource(R.string.qs_link_bitwarden_desc),
        badge = stringResource(R.string.qs_go_link),
        onClick = onOpenBitwardenSettings
    )
    SetupActionCard(
        icon = Icons.Default.Link,
        title = stringResource(R.string.qs_link_webdav),
        description = stringResource(R.string.qs_link_webdav_desc),
        badge = stringResource(R.string.qs_go_setup),
        onClick = onOpenWebDavBackup
    )
    SetupActionCard(
        icon = Icons.Default.Key,
        title = stringResource(R.string.qs_link_keepass),
        description = stringResource(R.string.qs_link_keepass_desc),
        badge = stringResource(R.string.qs_go_link),
        onClick = onOpenLocalKeePass
    )
    SetupActionCard(
        icon = Icons.Default.UploadFile,
        title = stringResource(R.string.qs_manual_import),
        description = stringResource(R.string.qs_manual_import_desc),
        badge = stringResource(R.string.qs_go_import),
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
    SetupSection(title = stringResource(R.string.qs_list_content), icon = Icons.Default.DashboardCustomize) {
        SetupSwitchRow(
            icon = Icons.Default.Widgets,
            title = stringResource(R.string.qs_aggregate_all_items),
            checked = aggregateEnabled,
            onCheckedChange = onAggregateChange
        )
        SetupSwitchRow(
            icon = Icons.Default.Security,
            title = stringResource(R.string.qs_quick_access),
            checked = quickAccessEnabled,
            onCheckedChange = onQuickAccessChange
        )
    }
    SetupSection(title = stringResource(R.string.qs_filter), icon = Icons.Default.Storage) {
        SetupSwitchRow(
            icon = Icons.Default.Check,
            title = stringResource(R.string.qs_quick_filters),
            checked = quickFiltersEnabled,
            onCheckedChange = onQuickFiltersChange
        )
        SetupSwitchRow(
            icon = Icons.Default.DashboardCustomize,
            title = stringResource(R.string.qs_category_quick_filters),
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
    SetupSection(title = stringResource(R.string.qs_display_fields), icon = Icons.Default.Password) {
        SetupSwitchRow(
            icon = Icons.Default.Key,
            title = stringResource(R.string.qs_show_username),
            checked = PasswordCardDisplayField.USERNAME in selectedFields,
            onCheckedChange = {
                onFieldsChange(togglePasswordCardField(selectedFields, PasswordCardDisplayField.USERNAME, it))
            }
        )
        SetupSwitchRow(
            icon = Icons.Default.Language,
            title = stringResource(R.string.qs_show_website),
            checked = PasswordCardDisplayField.WEBSITE in selectedFields,
            onCheckedChange = {
                onFieldsChange(togglePasswordCardField(selectedFields, PasswordCardDisplayField.WEBSITE, it))
            }
        )
    }
    SetupSection(title = stringResource(R.string.qs_authenticator_link), icon = Icons.Default.Security) {
        SetupSwitchRow(
            icon = Icons.Default.Lock,
            title = stringResource(R.string.qs_show_bound_authenticator),
            checked = showAuthenticator,
            onCheckedChange = onShowAuthenticatorChange
        )
        SetupSwitchRow(
            icon = Icons.Default.Shield,
            title = stringResource(R.string.qs_hide_other_when_authenticator),
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
    SetupSection(title = stringResource(R.string.qs_display_fields), icon = Icons.Default.Security) {
        SetupSwitchRow(
            icon = Icons.Default.Shield,
            title = stringResource(R.string.qs_show_issuer),
            checked = AuthenticatorCardDisplayField.ISSUER in selectedFields,
            onCheckedChange = {
                onFieldsChange(toggleAuthenticatorField(selectedFields, AuthenticatorCardDisplayField.ISSUER, it))
            }
        )
        SetupSwitchRow(
            icon = Icons.Default.Key,
            title = stringResource(R.string.qs_show_account_name),
            checked = AuthenticatorCardDisplayField.ACCOUNT_NAME in selectedFields,
            onCheckedChange = {
                onFieldsChange(toggleAuthenticatorField(selectedFields, AuthenticatorCardDisplayField.ACCOUNT_NAME, it))
            }
        )
    }
    SetupSection(title = stringResource(R.string.qs_progress_display), icon = Icons.Default.AutoAwesome) {
        SetupSwitchRow(
            icon = Icons.Default.Widgets,
            title = stringResource(R.string.qs_unified_progress_bar),
            checked = unifiedProgressEnabled,
            onCheckedChange = onUnifiedProgressChange
        )
        SetupSwitchRow(
            icon = Icons.Default.AutoAwesome,
            title = stringResource(R.string.qs_smooth_progress_animation),
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
    SetupSection(title = stringResource(R.string.qs_live_preview), icon = Icons.Default.Password) {
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
            text = stringResource(R.string.qs_preview_note_3_fields),
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
    SetupSection(title = stringResource(R.string.qs_live_preview), icon = Icons.Default.Security) {
        TotpCodeCard(
            item = previewItem,
            onCopyCode = {},
            appSettings = settings.copy(
                authenticatorCardDisplayFields = selectedFields,
                iconCardsEnabled = settings.iconCardsEnabled && settings.authenticatorPageIconEnabled
            )
        )
        Text(
            text = stringResource(R.string.qs_authenticator_preview_note),
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
            text = stringResource(if (isPlusActivated) R.string.qs_plus_activated else R.string.qs_plus_prompt),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(if (isPlusActivated) R.string.qs_plus_activated_desc else R.string.qs_plus_prompt_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!isPlusActivated) {
            Button(
                onClick = onOpenMonicaPlus,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.qs_open_monica_plus))
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
            if (onBack != null) {
                OutlinedButton(onClick = onBack) {
                    Text(stringResource(R.string.qs_previous))
                }
            }
            Spacer(modifier = Modifier.weight(1f))
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
            text = stringResource(colorSchemeLabelRes(scheme)),
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
                    text = stringResource(R.string.qs_yes),
                    selected = selectedYes,
                    onClick = { onAnswer(true) },
                    modifier = Modifier.weight(1f)
                )
                AnswerButton(
                    text = stringResource(R.string.qs_no),
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

@StringRes
private fun languageLabelRes(language: Language): Int = when (language) {
    Language.SYSTEM -> R.string.qs_lang_system
    Language.ENGLISH -> R.string.qs_lang_english
    Language.CHINESE -> R.string.qs_lang_chinese
    Language.VIETNAMESE -> R.string.qs_lang_vietnamese
    Language.JAPANESE -> R.string.qs_lang_japanese
    Language.RUSSIAN -> R.string.qs_lang_russian
}

@StringRes
private fun colorSchemeLabelRes(scheme: ColorScheme): Int = when (scheme) {
    ColorScheme.DEFAULT -> R.string.color_scheme_default
    ColorScheme.OCEAN_BLUE -> R.string.ocean_blue_scheme
    ColorScheme.SUNSET_ORANGE -> R.string.sunset_orange_scheme
    ColorScheme.FOREST_GREEN -> R.string.forest_green_scheme
    ColorScheme.TECH_PURPLE -> R.string.tech_purple_scheme
    ColorScheme.BLACK_MAMBA -> R.string.black_mamba_scheme
    ColorScheme.GREY_STYLE -> R.string.grey_style_scheme
    ColorScheme.WATER_LILIES -> R.string.water_lilies_scheme
    ColorScheme.IMPRESSION_SUNRISE -> R.string.impression_sunrise_scheme
    ColorScheme.JAPANESE_BRIDGE -> R.string.japanese_bridge_scheme
    ColorScheme.HAYSTACKS -> R.string.haystacks_scheme
    ColorScheme.ROUEN_CATHEDRAL -> R.string.rouen_cathedral_scheme
    ColorScheme.PARLIAMENT_FOG -> R.string.parliament_fog_scheme
    ColorScheme.CATPPUCCIN_LATTE -> R.string.catppuccin_latte_scheme
    ColorScheme.CATPPUCCIN_FRAPPE -> R.string.catppuccin_frappe_scheme
    ColorScheme.CATPPUCCIN_MACCHIATO -> R.string.catppuccin_macchiato_scheme
    ColorScheme.CATPPUCCIN_MOCHA -> R.string.catppuccin_mocha_scheme
    ColorScheme.CUSTOM -> R.string.color_scheme_custom
}

private fun schemeSwatches(scheme: ColorScheme): List<Color> = when (scheme) {
    ColorScheme.OCEAN_BLUE -> listOf(Color(0xFF0B57D0), Color(0xFF00A1C9), Color(0xFFB9E9F2))
    ColorScheme.SUNSET_ORANGE -> listOf(Color(0xFFB84A00), Color(0xFFFF8A50), Color(0xFFFFD7C2))
    ColorScheme.FOREST_GREEN -> listOf(Color(0xFF006C47), Color(0xFF3E8F65), Color(0xFFC8E6C9))
    ColorScheme.GREY_STYLE -> listOf(Color(0xFF4B465C), Color(0xFF7C748D), Color(0xFFE5E0EC))
    ColorScheme.BLACK_MAMBA -> listOf(Color(0xFF0B0B0D), Color(0xFFD9A900), Color(0xFF8F5CFF))
    else -> listOf(Color(0xFF6750A4), Color(0xFF625B71), Color(0xFFEADDFF))
}

@StringRes
private fun tabLabelRes(tab: BottomNavContentTab): Int = when (tab) {
    BottomNavContentTab.VAULT_V2 -> R.string.nav_v2_vault
    BottomNavContentTab.PASSWORDS -> R.string.nav_passwords
    BottomNavContentTab.AUTHENTICATOR -> R.string.nav_authenticator
    BottomNavContentTab.CARD_WALLET -> R.string.nav_card_wallet
    BottomNavContentTab.GENERATOR -> R.string.nav_generator
    BottomNavContentTab.NOTES -> R.string.nav_notes
    BottomNavContentTab.SEND -> R.string.nav_v2_send
    BottomNavContentTab.PASSKEY -> R.string.nav_passkey
}

@StringRes
private fun tabShortLabelRes(tab: BottomNavContentTab): Int = when (tab) {
    BottomNavContentTab.VAULT_V2 -> R.string.nav_v2_vault_short
    BottomNavContentTab.PASSWORDS -> R.string.nav_passwords_short
    BottomNavContentTab.AUTHENTICATOR -> R.string.nav_authenticator_short
    BottomNavContentTab.CARD_WALLET -> R.string.nav_card_wallet_short
    BottomNavContentTab.GENERATOR -> R.string.nav_generator_short
    BottomNavContentTab.NOTES -> R.string.nav_notes_short
    BottomNavContentTab.SEND -> R.string.nav_v2_send_short
    BottomNavContentTab.PASSKEY -> R.string.nav_passkey_short
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
