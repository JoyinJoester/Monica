package takagi.ru.monica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import takagi.ru.monica.ui.common.layout.DetailPane
import takagi.ru.monica.ui.common.layout.ListPane
import takagi.ru.monica.ui.screens.SettingsScreen
import takagi.ru.monica.viewmodel.SettingsViewModel

@Composable
internal fun SettingsTabContent(
    isCompactWidth: Boolean,
    wideListPaneWidth: Dp,
    viewModel: SettingsViewModel,
    onResetPassword: () -> Unit,
    onSecurityQuestions: () -> Unit,
    onNavigateToMasterPasswordLocking: () -> Unit,
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
    onNavigateToPageCustomization: () -> Unit,
    onClearAllData: (Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit
) {
    val settingsListContent: @Composable () -> Unit = {
        SettingsScreen(
            viewModel = viewModel,
            onNavigateBack = {},
            onResetPassword = onResetPassword,
            onSecurityQuestions = onSecurityQuestions,
            onNavigateToMasterPasswordLocking = onNavigateToMasterPasswordLocking,
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
            onNavigateToPageCustomization = onNavigateToPageCustomization,
            onClearAllData = onClearAllData,
            showTopBar = false
        )
    }

    if (isCompactWidth) {
        Box(modifier = Modifier.fillMaxSize()) {
            settingsListContent()
        }
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            ListPane(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(wideListPaneWidth)
            ) {
                settingsListContent()
            }
            DetailPane(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select a setting to view details",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
