package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.util.TotpDataResolver
import takagi.ru.monica.ui.screens.resolvePasswordScreenAuthenticatorDraft
import takagi.ru.monica.ui.screens.buildPasswordScreenAuthenticatorPayload

/** Each account owns its OTP payload; editing this field never changes another account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectCredentialOtpField(
    value: String, issuer: String, username: String, settings: AppSettings,
    onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    followingContent: @Composable () -> Unit = {},
) {
    val draft = remember(value, issuer, username) { resolvePasswordScreenAuthenticatorDraft(value, issuer, username) }
    var advanced by remember { mutableStateOf(false) }
    val preview = remember(value, issuer, username) { TotpDataResolver.fromAuthenticatorKey(value, issuer, username) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CompositionLocalProvider(LocalFilledEntryForm provides true) {
            OutlinedTextField(value = draft.secret, onValueChange = { input ->
                onValueChange(if (input.contains("://")) input else buildPasswordScreenAuthenticatorPayload(
                    input, draft.otpType, issuer, username, draft.parameters))
            }, saveTextState = false,
                label = { Text(stringResource(R.string.authenticator_key_optional)) },
                placeholder = { Text(stringResource(R.string.authenticator_key_hint)) },
                leadingIcon = { Icon(Icons.Default.VpnKey, null) },
                trailingIcon = { EditorAdvancedMenuButton(onOpen = { advanced = true }) },
                shape = RoundedCornerShape(4.dp), singleLine = true, modifier = modifier.fillMaxWidth())
        }
        followingContent()
        if (preview != null) {
            val now by produceState(System.currentTimeMillis(), preview.otpType, settings.validatorSmoothProgress) {
                while (true) {
                    this.value = System.currentTimeMillis()
                    delay(if (settings.validatorSmoothProgress) 50L else 1000L)
                }
            }
            InlineTotpPreviewCard(preview, now / 1000, now, settings.totpTimeOffset,
                settings.validatorSmoothProgress, showHeader = false, showProgress = true,
                modifier = Modifier.padding(top = 10.dp).testTag("credential_otp_preview"))
        }
    }
    if (advanced) {
        var type by remember { mutableStateOf(draft.otpType) }
        var parameters by remember { mutableStateOf(draft.parameters) }
        androidx.compose.ui.window.Dialog(onDismissRequest = { advanced = false }) {
          Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.authenticator_key_optional), style = MaterialTheme.typography.titleLarge)
                OtpTypeSelector(type, onChange = { type = it; parameters = parameters.selectType(it) })
                OtpParameterFields(type, parameters, onChange = { parameters = it })
                FilledTonalButton(onClick = {
                    onValueChange(buildPasswordScreenAuthenticatorPayload(draft.secret, type, issuer, username, parameters))
                    advanced = false
                }, enabled = parameters.isValid(type), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
            }
          }
        }
    }
}
