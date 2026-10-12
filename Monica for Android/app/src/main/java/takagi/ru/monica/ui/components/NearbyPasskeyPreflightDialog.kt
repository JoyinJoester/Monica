package takagi.ru.monica.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.lansync.DevicePasskeySyncReview
import takagi.ru.monica.lansync.DevicePasskeySyncIssue
import takagi.ru.monica.passkey.PasskeyPortability

@Composable
internal fun NearbyPasskeyPreflightDialog(review: DevicePasskeySyncReview, onCancel: () -> Unit, onContinue: (Boolean) -> Unit) {
    var dontAskAgain by rememberSaveable(review) { mutableStateOf(false) }
    PasskeyPreflightDialog(
        title = stringResource(R.string.device_sync_passkey_title),
        summary = stringResource(R.string.device_sync_passkey_summary, review.issues.size),
        issues = review.issues.preflightIssues(),
        cancelText = stringResource(R.string.device_sync_passkey_cancel),
        confirmText = stringResource(if (review.issues.any { it.skipForCounter }) R.string.device_sync_passkey_skip_continue else R.string.device_sync_passkey_continue),
        tag = "nearby_passkey", onCancel = onCancel, onConfirm = { onContinue(dontAskAgain) },
        footer = {
            Row(Modifier.fillMaxWidth().testTag("nearby_passkey_remember")
                .toggleable(dontAskAgain, role = Role.Checkbox, onValueChange = { dontAskAgain = it })
                .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(dontAskAgain, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.nearby_passkey_dont_ask))
            }
            Text(stringResource(R.string.nearby_passkey_remember_hint), style = MaterialTheme.typography.bodySmall)
        })
}

@Composable
internal fun NearbyPasskeyRestrictionsDialog(issues: List<DevicePasskeySyncIssue>, onClose: () -> Unit) {
    PasskeyPreflightDialog(title = stringResource(R.string.device_sync_passkey_title),
        summary = stringResource(R.string.nearby_passkey_rules), issues = issues.preflightIssues(),
        cancelText = "", confirmText = stringResource(R.string.close), showCancel = false,
        tag = "nearby_passkey_details", onCancel = onClose, onConfirm = onClose)
}

private fun List<DevicePasskeySyncIssue>.preflightIssues() = map { issue ->
    PasskeyPreflightIssue(issue.account, issue.relyingParty,
        if (issue.skipForCounter) R.string.device_sync_passkey_counter_skip_hint
        else if (issue.portability == PasskeyPortability.DEVICE_ONLY) R.string.device_sync_passkey_reference_hint
        else issue.portability.description())
}
