package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.passkey.PasskeyTransferPlan

internal data class PasskeyPreflightIssue(val account: String, val relyingParty: String, val reason: Int)

@Composable
internal fun PasskeyTransferPreflightDialog(
    plan: PasskeyTransferPlan, action: UnifiedMoveAction, onCancel: () -> Unit, onSkip: () -> Unit
) {
    val copying = action == UnifiedMoveAction.COPY
    PasskeyPreflightDialog(
        title = stringResource(if (copying) R.string.passkey_copy_blocked_title else R.string.passkey_move_blocked_title),
        summary = stringResource(R.string.passkey_transfer_preflight_summary, plan.issues.size, plan.eligible.size),
        issues = plan.issues.map { PasskeyPreflightIssue(it.account, it.relyingParty, it.reason.messageRes()) },
        cancelText = stringResource(if (copying) R.string.passkey_cancel_copy else R.string.passkey_cancel_move),
        confirmText = stringResource(R.string.passkey_transfer_skip, plan.issues.size),
        confirmEnabled = plan.eligible.isNotEmpty(), tag = "passkey_transfer",
        onCancel = onCancel, onConfirm = onSkip)
}

/** Shared preflight layout for moving/copying credentials and nearby sync. */
@Composable
internal fun PasskeyPreflightDialog(
    title: String, summary: String, issues: List<PasskeyPreflightIssue>,
    cancelText: String, confirmText: String, confirmEnabled: Boolean = true,
    tag: String, onCancel: () -> Unit, onConfirm: () -> Unit,
    showCancel: Boolean = true, footer: (@Composable () -> Unit)? = null
) {
    AlertDialog(
        modifier = Modifier.testTag("${tag}_preflight"),
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text(summary) }
                    items(issues) { issue ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(issue.account.ifBlank { issue.relyingParty }, style = MaterialTheme.typography.titleSmall)
                            if (issue.relyingParty.isNotBlank()) Text(issue.relyingParty, style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(issue.reason), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                footer?.invoke()
            }
        },
        dismissButton = {
            if (showCancel) TextButton(onClick = onCancel, modifier = Modifier.testTag("${tag}_cancel")) {
                Text(cancelText)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.testTag("${tag}_skip")) {
                Text(confirmText)
            }
        }
    )
}
