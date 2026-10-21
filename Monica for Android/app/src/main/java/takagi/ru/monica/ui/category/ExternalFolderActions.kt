package takagi.ru.monica.ui.category

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.repository.ExternalFolderRepository
import takagi.ru.monica.repository.ExternalFolderTarget
import takagi.ru.monica.repository.DatabaseManagerLocation
import takagi.ru.monica.repository.DatabaseManagerStore
import takagi.ru.monica.ui.FolderActionsDialog
import takagi.ru.monica.ui.components.OutlinedTextField
import takagi.ru.monica.ui.components.UnifiedCategoryFilterSelection
import takagi.ru.monica.utils.decodeKeePassPathForDisplay
import takagi.ru.monica.viewmodel.CategoryFilter

internal fun CategoryFilter.externalFolder(name: String): ExternalFolderTarget? = when (this) {
    is CategoryFilter.KeePassGroupFilter -> ExternalFolderTarget.KeePass(databaseId, groupPath, groupUuid, name)
    is CategoryFilter.MdbxFolderFilter -> ExternalFolderTarget.Mdbx(databaseId, folderId, name)
    is CategoryFilter.BitwardenFolderFilter -> ExternalFolderTarget.Bitwarden(vaultId, folderId, name)
    else -> null
}

internal fun UnifiedCategoryFilterSelection.externalFolder(name: String): ExternalFolderTarget? = when (this) {
    is UnifiedCategoryFilterSelection.KeePassGroupFilter -> ExternalFolderTarget.KeePass(databaseId, groupPath, groupUuid, name)
    is UnifiedCategoryFilterSelection.MdbxFolderFilter -> ExternalFolderTarget.Mdbx(databaseId, folderId, name)
    is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> ExternalFolderTarget.Bitwarden(vaultId, folderId, name)
    else -> null
}

@Composable
internal fun ManagedExternalFolderActions(target: ExternalFolderTarget?, onDismiss: () -> Unit,
    onChanged: (ExternalFolderTarget) -> Unit,
    onDeleted: (ExternalFolderTarget) -> Unit = {}) {
    if (target == null) return
    val context = LocalContext.current
    val repository = remember(context) { ExternalFolderRepository(context) }
    ExternalFolderActionDialogs(target, onDismiss,
        onRename = { repository.rename(target, it); onChanged(target) },
        onDelete = { repository.delete(target); onChanged(target); onDeleted(target) },
        onTransfer = { destination, copy ->
            repository.transfer(target, destination, copy)
            onChanged(target)
            if (!copy && (target !is ExternalFolderTarget.Bitwarden || destination.database.databaseId != target.databaseId)) onDeleted(target)
        },
        loadStores = { repository.transferStores(target) },
        loadFolders = { repository.transferFolders(target, it) })
}

/** Failures keep the dialog and typed name; deleting always requires a separate confirmation. */
@Composable
internal fun ExternalFolderActionDialogs(target: ExternalFolderTarget, onDismiss: () -> Unit,
    onRename: suspend (String) -> Unit, onDelete: suspend () -> Unit,
    onTransfer: (suspend (DatabaseManagerLocation, Boolean) -> Unit)? = null,
    loadStores: suspend () -> List<DatabaseManagerStore> = { emptyList() },
    loadFolders: suspend (DatabaseManagerStore) -> List<Pair<DatabaseManagerLocation, String>> = { emptyList() }) {
    key(target) {
        var action by remember { mutableStateOf("actions") }
        var name by remember { mutableStateOf(if (target is ExternalFolderTarget.KeePass)
            decodeKeePassPathForDisplay(target.path.substringAfterLast('/')) else target.name) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        fun submit(block: suspend () -> Unit) {
            if (busy) return
            busy = true
            error = null
            scope.launch {
                try { block(); onDismiss() }
                catch (cancel: CancellationException) { throw cancel }
                catch (failure: Exception) { error = context.getString(R.string.save_failed_with_error, failure.message.orEmpty()) }
                finally { busy = false }
            }
        }
        if (action == "actions") {
            FolderActionsDialog(target.name, onDismiss,
                onRename = { action = "rename" }, onDelete = { action = "delete" },
                onMove = onTransfer?.let { { action = "move" } },
                onCopy = onTransfer?.let { { action = "copy" } })
        } else if (action == "move" || action == "copy") {
            ExternalFolderTransferDialog(target.name, action == "copy", onDismiss,
                loadStores, loadFolders,
                accountHint = if (target is ExternalFolderTarget.Bitwarden) stringResource(R.string.external_folder_bitwarden_hint) else null,
                onConfirm = { destination -> onTransfer!!(destination, action == "copy") })
        } else {
            AlertDialog(
                onDismissRequest = { if (!busy) onDismiss() },
                title = { Text(stringResource(if (action == "rename") R.string.rename_category else R.string.delete)) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (action == "rename") OutlinedTextField(value = name, onValueChange = { name = it },
                        label = { Text(stringResource(R.string.category_name)) }, singleLine = true,
                        enabled = !busy, modifier = Modifier.fillMaxWidth())
                    else Text(stringResource(if (target is ExternalFolderTarget.Bitwarden)
                        R.string.external_folder_delete_keep_items else R.string.external_folder_delete_contents, target.name))
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                } },
                confirmButton = { TextButton(enabled = !busy && (action != "rename" || name.isNotBlank()),
                    onClick = { submit { if (action == "rename") onRename(name.trim()) else onDelete() } }) {
                    Text(stringResource(if (action == "rename") R.string.confirm else R.string.delete))
                } },
                dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
            )
        }
    }
}
