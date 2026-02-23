package takagi.ru.monica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel
import takagi.ru.monica.data.bitwarden.BitwardenSend
import takagi.ru.monica.ui.common.layout.DetailPane
import takagi.ru.monica.ui.common.layout.ListPane
import takagi.ru.monica.ui.screens.AddEditSendScreen
import takagi.ru.monica.ui.screens.SendScreen

@Composable
internal fun SendPane(
    isCompactWidth: Boolean,
    wideListPaneWidth: Dp,
    bitwardenViewModel: BitwardenViewModel,
    sendState: BitwardenViewModel.SendState,
    selectedSend: BitwardenSend?,
    isAddingSendInline: Boolean,
    onSendClick: (BitwardenSend) -> Unit,
    onInlineSendEditorBack: () -> Unit,
    onCreateSend: (
        title: String,
        text: String,
        notes: String?,
        password: String?,
        maxAccessCount: Int?,
        hideEmail: Boolean,
        hiddenText: Boolean,
        expireInDays: Int
    ) -> Unit
) {
    if (isCompactWidth) {
        SendScreen(bitwardenViewModel = bitwardenViewModel)
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            ListPane(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(wideListPaneWidth)
            ) {
                SendScreen(
                    onSendClick = onSendClick,
                    selectedSendId = selectedSend?.bitwardenSendId,
                    bitwardenViewModel = bitwardenViewModel
                )
            }
            DetailPane(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (isAddingSendInline) {
                    AddEditSendScreen(
                        sendState = sendState,
                        onNavigateBack = onInlineSendEditorBack,
                        onCreate = onCreateSend,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (selectedSend == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Select an item to preview",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    SendDetailPane(
                        send = selectedSend,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
