package takagi.ru.monica.ui

import androidx.compose.runtime.Composable
import takagi.ru.monica.ui.screens.SettingsScreen
import takagi.ru.monica.viewmodel.SettingsViewModel

@Composable
internal fun SettingsTabContent(
    viewModel: SettingsViewModel,
    onResetPassword: () -> Unit,
    onSecurityQuestions: () -> Unit,
    onNavigateToSyncBackup: () -> Unit,
    onNavigateToAutofill: () -> Unit,
    onNavigateToPasskeySettings: () -> Unit,
    onNavigateToBottomNavSettings: () -> Unit,
    onNavigateToColorScheme: () -> Unit,
    onSecurityAnalysis: () -> Unit,
    onNavigateToDeveloperSettings: () -> Unit,
    onNavigateToPermissionManagement: () -> Unit,
    onNavigateToMonicaPlus: () -> Unit,
    onNavigateToExtensions: () -> Unit,
    onClearAllData: (Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit
) {
    SettingsScreen(
        viewModel = viewModel,
        onNavigateBack = {},
        onResetPassword = onResetPassword,
        onSecurityQuestions = onSecurityQuestions,
        onNavigateToSyncBackup = onNavigateToSyncBackup,
        onNavigateToAutofill = onNavigateToAutofill,
        onNavigateToPasskeySettings = onNavigateToPasskeySettings,
        onNavigateToBottomNavSettings = onNavigateToBottomNavSettings,
        onNavigateToColorScheme = onNavigateToColorScheme,
        onSecurityAnalysis = onSecurityAnalysis,
        onNavigateToDeveloperSettings = onNavigateToDeveloperSettings,
        onNavigateToPermissionManagement = onNavigateToPermissionManagement,
        onNavigateToMonicaPlus = onNavigateToMonicaPlus,
        onNavigateToExtensions = onNavigateToExtensions,
        onClearAllData = onClearAllData,
        showTopBar = false
    )
}
