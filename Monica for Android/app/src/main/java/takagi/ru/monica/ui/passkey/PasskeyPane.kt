package takagi.ru.monica.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.ui.common.layout.DetailPane
import takagi.ru.monica.ui.common.layout.ListPane
import takagi.ru.monica.ui.screens.PasskeyListScreen
import takagi.ru.monica.viewmodel.PasskeyViewModel
import takagi.ru.monica.viewmodel.PasswordViewModel

@Composable
internal fun PasskeyPane(
    isCompactWidth: Boolean,
    wideListPaneWidth: Dp,
    passkeyViewModel: PasskeyViewModel,
    passwordViewModel: PasswordViewModel,
    onNavigateToPasswordDetail: (Long) -> Unit,
    onPasskeyOpen: (PasskeyEntry) -> Unit,
    selectedPasskey: PasskeyEntry?,
    passkeyTotalCount: Int,
    passkeyBoundCount: Int,
    resolvePasswordTitle: (Long) -> String?,
    onOpenPasswordDetail: (Long) -> Unit,
    onUnbindPasskey: (PasskeyEntry) -> Unit,
    onDeletePasskey: (PasskeyEntry) -> Unit
) {
    if (isCompactWidth) {
        PasskeyListScreen(
            viewModel = passkeyViewModel,
            passwordViewModel = passwordViewModel,
            onNavigateToPasswordDetail = onNavigateToPasswordDetail,
            onPasskeyClick = {}
        )
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            ListPane(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(wideListPaneWidth)
            ) {
                PasskeyListScreen(
                    viewModel = passkeyViewModel,
                    passwordViewModel = passwordViewModel,
                    onNavigateToPasswordDetail = onNavigateToPasswordDetail,
                    onPasskeyClick = onPasskeyOpen
                )
            }
            DetailPane(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val passkey = selectedPasskey
                if (passkey == null) {
                    PasskeyOverviewPane(
                        totalPasskeys = passkeyTotalCount,
                        boundPasskeys = passkeyBoundCount,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val boundPasswordTitle = passkey.boundPasswordId?.let(resolvePasswordTitle)
                    PasskeyDetailPane(
                        passkey = passkey,
                        boundPasswordTitle = boundPasswordTitle,
                        totalPasskeys = passkeyTotalCount,
                        boundPasskeys = passkeyBoundCount,
                        onOpenBoundPassword = passkey.boundPasswordId?.let { boundId ->
                            { onOpenPasswordDetail(boundId) }
                        },
                        onUnbindPassword = if (passkey.boundPasswordId != null) {
                            { onUnbindPasskey(passkey) }
                        } else {
                            null
                        },
                        onDeletePasskey = { onDeletePasskey(passkey) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
