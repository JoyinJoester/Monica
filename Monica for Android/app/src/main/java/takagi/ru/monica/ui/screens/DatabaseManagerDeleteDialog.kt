package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

@Composable
internal fun DatabaseManagerDeleteDialog(title: String, paneIndex: Int, count: Int, local: Boolean,
    busy: Boolean, error: String?, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        modifier = Modifier.testTag("manager-delete-dialog"),
        icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.manager_delete_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.manager_delete_source,
                stringResource(if (paneIndex == 0) R.string.manager_left_pane else R.string.manager_right_pane), title, count))
            Text(stringResource(if (local) R.string.manager_delete_local_hint else R.string.manager_delete_native_hint))
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy,
            modifier = Modifier.testTag("manager-confirm-delete"),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy,
            modifier = Modifier.testTag("manager-cancel-delete")) { Text(stringResource(R.string.cancel)) } })
}
