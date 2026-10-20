package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

/** Shared controls keep primary and added credential password rows consistent. */
@Composable
fun CredentialPasswordActions(
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    onGenerate: () -> Unit,
    onRemove: (() -> Unit)?,
    tag: String,
    removeTag: String = "${tag}_remove",
) {
    var menu by remember { mutableStateOf(false) }
    Row {
        IconButton(onClick = onToggleVisibility, modifier = Modifier.testTag("${tag}_visibility")) {
            Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                stringResource(if (visible) R.string.hide_password else R.string.show_password))
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("${tag}_menu")) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
            }
            EditorActionMenu(menu, { menu = false }, buildList {
                add(EditorMenuAction(stringResource(R.string.generator), Icons.Default.AutoAwesome,
                    onClick = onGenerate, tag = "${tag}_generate"))
                if (onRemove != null) add(EditorMenuAction(stringResource(R.string.delete), Icons.Default.Delete,
                    onClick = onRemove, destructive = true, tag = removeTag))
            })
        }
    }
}

/** A plain, always visible per-account note; independent of page-level rich notes. */
@Composable
fun CredentialNoteField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalFilledEntryForm provides true) {
        OutlinedTextField(value = value, onValueChange = onValueChange, saveTextState = false,
            label = { Text(stringResource(R.string.credential_note)) },
            leadingIcon = { Icon(Icons.Default.Notes, null) },
            modifier = modifier.fillMaxWidth(), minLines = 2,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp))
    }
}
