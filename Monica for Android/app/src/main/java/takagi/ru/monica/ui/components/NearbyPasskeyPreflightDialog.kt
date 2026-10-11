package takagi.ru.monica.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import takagi.ru.monica.R
import takagi.ru.monica.lansync.DevicePasskeySyncReview
import takagi.ru.monica.passkey.PasskeyPortability

@Composable
internal fun NearbyPasskeyPreflightDialog(review: DevicePasskeySyncReview, onCancel: () -> Unit, onContinue: () -> Unit) {
    PasskeyPreflightDialog(
        title = stringResource(R.string.device_sync_passkey_title),
        summary = stringResource(R.string.device_sync_passkey_summary, review.issues.size),
        issues = review.issues.map { issue ->
            PasskeyPreflightIssue(issue.account, issue.relyingParty,
                if (issue.portability == PasskeyPortability.DEVICE_ONLY) R.string.device_sync_passkey_reference_hint
                else issue.portability.description())
        },
        cancelText = stringResource(R.string.device_sync_passkey_cancel),
        confirmText = stringResource(R.string.device_sync_passkey_continue),
        tag = "nearby_passkey", onCancel = onCancel, onConfirm = onContinue)
}
