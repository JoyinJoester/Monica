package takagi.ru.monica.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import takagi.ru.monica.ui.common.layout.DetailPane
import takagi.ru.monica.ui.common.layout.ListPane
import takagi.ru.monica.ui.screens.AddEditTotpScreen
import takagi.ru.monica.viewmodel.PasswordViewModel

@Composable
internal fun AuthenticatorTabPane(
    isCompactWidth: Boolean,
    wideListPaneWidth: Dp,
    totpViewModel: takagi.ru.monica.viewmodel.TotpViewModel,
    passwordViewModel: PasswordViewModel,
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    onTotpOpen: (Long) -> Unit,
    onNavigateToQuickTotpScan: () -> Unit,
    onSelectionModeChange: (
        Boolean,
        Int,
        () -> Unit,
        () -> Unit,
        () -> Unit,
        () -> Unit
    ) -> Unit,
    isAddingTotpInline: Boolean,
    selectedTotpId: Long?,
    totpNewItemDefaults: NewItemStorageDefaults,
    onInlineTotpEditorBack: () -> Unit,
    showStandaloneSettingsEntry: Boolean,
    onOpenStandaloneSettings: () -> Unit
) {
    val listPaneContent: @Composable ColumnScope.() -> Unit = {
        TotpListContent(
            viewModel = totpViewModel,
            passwordViewModel = passwordViewModel,
            onTotpClick = onTotpOpen,
            onDeleteTotp = { totp ->
                totpViewModel.deleteTotpItem(totp)
            },
            onQuickScanTotp = onNavigateToQuickTotpScan,
            onSelectionModeChange = onSelectionModeChange,
            showStandaloneSettingsEntry = showStandaloneSettingsEntry,
            onOpenStandaloneSettings = onOpenStandaloneSettings
        )
    }

    if (isCompactWidth) {
        ListPane(
            modifier = Modifier.fillMaxSize(),
            content = listPaneContent
        )
    } else {
        val totpItems by totpViewModel.totpItems.collectAsState()
        val selectedTotpItem = remember(selectedTotpId, totpItems) {
            selectedTotpId?.let { selectedId ->
                totpItems.firstOrNull { it.id == selectedId }
            }
        }
        val parsedTotpItems by totpViewModel.parsedTotpItems.collectAsState()
        val selectedTotpData = remember(selectedTotpId, parsedTotpItems) {
            parsedTotpItems.firstOrNull { it.item.id == selectedTotpId }?.totpData
        }
        val totpCategories by totpViewModel.categories.collectAsState()

        Row(modifier = Modifier.fillMaxSize()) {
            ListPane(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(wideListPaneWidth),
                content = listPaneContent
            )
            DetailPane(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (isAddingTotpInline) {
                    AddEditTotpScreen(
                        totpId = null,
                        initialData = null,
                        initialTitle = "",
                        initialNotes = "",
                        initialCategoryId = totpNewItemDefaults.categoryId,
                        initialKeePassDatabaseId = totpNewItemDefaults.keepassDatabaseId,
                        initialKeePassGroupPath = totpNewItemDefaults.keepassGroupPath,
                        initialBitwardenVaultId = totpNewItemDefaults.bitwardenVaultId,
                        initialBitwardenFolderId = totpNewItemDefaults.bitwardenFolderId,
                        initialIsFavorite = false,
                        categories = totpCategories,
                        passwordViewModel = passwordViewModel,
                        totpViewModel = totpViewModel,
                        localKeePassViewModel = localKeePassViewModel,
                        onSave = { title, notes, totpData, isFavorite, targets, onComplete ->
                            totpViewModel.saveTotpAcrossTargets(
                                id = null,
                                title = title,
                                notes = notes,
                                totpData = totpData,
                                isFavorite = isFavorite,
                                targets = targets,
                                onComplete = { saved ->
                                    if (saved) {
                                        totpViewModel.revealSavedTotpTargets(targets)
                                        onInlineTotpEditorBack()
                                    }
                                    onComplete(saved)
                                }
                            )
                        },
                        onNavigateBack = onInlineTotpEditorBack,
                        onScanQrCode = onNavigateToQuickTotpScan,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (selectedTotpId == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Select an item to view details",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (selectedTotpItem == null || selectedTotpItem.id <= 0L || selectedTotpData == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "This item is not available for inline editing",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    AddEditTotpScreen(
                        totpId = selectedTotpItem.id,
                        initialData = selectedTotpData,
                        initialTitle = selectedTotpItem.title,
                        initialNotes = selectedTotpItem.notes,
                        initialCategoryId = selectedTotpData.categoryId,
                        initialKeePassDatabaseId = selectedTotpItem.keepassDatabaseId,
                        initialKeePassGroupPath = selectedTotpItem.keepassGroupPath,
                        initialBitwardenVaultId = selectedTotpItem.bitwardenVaultId,
                        initialBitwardenFolderId = selectedTotpItem.bitwardenFolderId,
                        initialReplicaGroupId = selectedTotpItem.replicaGroupId,
                        initialIsFavorite = selectedTotpItem.isFavorite,
                        categories = totpCategories,
                        passwordViewModel = passwordViewModel,
                        totpViewModel = totpViewModel,
                        localKeePassViewModel = localKeePassViewModel,
                        onSave = { title, notes, totpData, isFavorite, targets, onComplete ->
                            totpViewModel.saveTotpAcrossTargets(
                                id = selectedTotpItem.id,
                                title = title,
                                notes = notes,
                                totpData = totpData,
                                isFavorite = isFavorite,
                                targets = targets,
                                onComplete = { saved ->
                                    if (saved) {
                                        totpViewModel.revealSavedTotpTargets(targets)
                                    }
                                    onComplete(saved)
                                }
                            )
                        },
                        onNavigateBack = onInlineTotpEditorBack,
                        onScanQrCode = onNavigateToQuickTotpScan,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
