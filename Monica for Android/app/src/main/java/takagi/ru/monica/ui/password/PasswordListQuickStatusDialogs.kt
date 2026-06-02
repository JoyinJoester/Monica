package takagi.ru.monica.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.ui.password.PasswordBatchDeleteGlobalProgressState
import takagi.ru.monica.ui.password.PasswordBatchTransferGlobalProgressState

@Composable
internal fun PasswordListQuickStatusDialogs(
    showQuickStatusTransferDialog: Boolean,
    quickStatusTransferState: PasswordBatchTransferGlobalProgressState?,
    onMoveTransferToBackground: () -> Unit,
    showQuickStatusDeleteDialog: Boolean,
    quickStatusDeleteState: PasswordBatchDeleteGlobalProgressState?,
    onMoveDeleteToBackground: () -> Unit,
    showQuickStatusKeePassSyncDialog: Boolean,
    quickStatusKeePassSyncState: QuickStatusKeePassSyncState?,
    onMoveKeePassSyncToBackground: (QuickStatusKeePassSyncState) -> Unit,
    onRunKeePassSyncNow: (QuickStatusKeePassSyncState) -> Unit
) {
    if (showQuickStatusTransferDialog) {
        quickStatusTransferState?.toDialogUiState()?.let { state ->
            PasswordBatchTransferProgressDialog(
                state = state,
                onMoveToBackground = onMoveTransferToBackground
            )
        }
    }

    if (showQuickStatusDeleteDialog) {
        quickStatusDeleteState?.toDialogUiState()?.let { state ->
            PasswordBatchDeleteProgressDialog(
                state = state,
                onMoveToBackground = onMoveDeleteToBackground
            )
        }
    }

    if (showQuickStatusKeePassSyncDialog) {
        quickStatusKeePassSyncState?.let { state ->
            AlertDialog(
                onDismissRequest = {},
                title = {
                    Text(text = state.databaseName.ifBlank { "KeePass" })
                },
                text = {
                    Column {
                        Text(text = keepassQuickSyncStatusLabel(state))
                        if (state.isRunning) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { onMoveKeePassSyncToBackground(state) }
                    ) {
                        Text(text = stringResource(R.string.password_batch_transfer_continue_in_background))
                    }
                },
                dismissButton = if (!state.isRunning) {
                    {
                        TextButton(
                            onClick = { onRunKeePassSyncNow(state) }
                        ) {
                            Text(text = "立即同步")
                        }
                    }
                } else {
                    null
                }
            )
        }
    }
}
