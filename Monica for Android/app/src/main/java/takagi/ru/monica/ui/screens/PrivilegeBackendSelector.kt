package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.autofill_ng.privilege.PrivilegeAccess
import takagi.ru.monica.autofill_ng.privilege.PrivilegeBackend
import takagi.ru.monica.autofill_ng.privilege.RootStatus

@Composable
internal fun PrivilegeBackendSelector(enabled: Boolean = true, onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by PrivilegeAccess.status.collectAsStateWithLifecycle()
    val changes by PrivilegeAccess.changes.collectAsStateWithLifecycle()
    val backend = remember(changes) { PrivilegeAccess.backend(context) }
    var showConsent by rememberSaveable { mutableStateOf(false) }
    val usable = enabled && status != RootStatus.AUTHORIZING
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.privilege_method), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            PrivilegeBackend.entries.forEach { method ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("privilege-${method.name}")
                    .selectable(backend == method, enabled = usable, role = Role.RadioButton) {
                        PrivilegeAccess.select(context, method)
                        onChanged()
                    }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = backend == method, onClick = null, enabled = usable)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(if (method == PrivilegeBackend.ROOT) "Root" else "Shizuku", style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(if (method == PrivilegeBackend.ROOT) R.string.privilege_root_hint else R.string.privilege_shizuku_hint),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (backend == PrivilegeBackend.ROOT) {
            val allowed = PrivilegeAccess.rootAllowed(context)
            Text(stringResource(when (status) {
                RootStatus.AUTHORIZING -> R.string.privilege_root_requesting
                RootStatus.DENIED -> R.string.privilege_root_denied
                RootStatus.UNAVAILABLE -> R.string.privilege_root_unavailable
                RootStatus.TIMED_OUT -> R.string.privilege_root_timeout
                else -> if (allowed) R.string.privilege_root_ready else R.string.privilege_root_not_authorized
            }), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("root-status"))
            Button(enabled = usable, onClick = { showConsent = true }, modifier = Modifier.fillMaxWidth().testTag("root-authorize")) {
                Text(stringResource(R.string.privilege_root_grant))
            }
            if (PrivilegeAccess.hasRootConsent(context)) TextButton(enabled = usable, onClick = { PrivilegeAccess.disable(context); onChanged() }, modifier = Modifier.testTag("root-disable")) {
                Text(stringResource(R.string.privilege_root_disable))
            }
        }
    }
    if (showConsent) AlertDialog(
        onDismissRequest = { showConsent = false },
        title = { Text(stringResource(R.string.privilege_root_confirm_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.privilege_root_confirm_body))
            }
        },
        confirmButton = { TextButton(enabled = usable, modifier = Modifier.testTag("root-consent-confirm"), onClick = {
            showConsent = false
            scope.launch { PrivilegeAccess.authorize(context); onChanged() }
        }) { Text(stringResource(R.string.privilege_root_grant)) } },
        dismissButton = { TextButton(modifier = Modifier.testTag("root-consent-cancel"), onClick = { showConsent = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
