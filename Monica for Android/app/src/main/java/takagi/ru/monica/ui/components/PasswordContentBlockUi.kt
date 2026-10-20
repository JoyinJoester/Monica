package takagi.ru.monica.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.data.model.PasswordContentBlocks
import takagi.ru.monica.data.model.PasswordContentBlocks.Kind
import takagi.ru.monica.utils.ClipboardUtils

fun Kind.labelRes() = when (this) {
    Kind.API_KEY -> R.string.content_block_api_key
    Kind.API_TOKEN -> R.string.content_block_api_token
    Kind.SSH_KEY -> R.string.content_block_ssh
    Kind.GPG_KEY -> R.string.content_block_gpg
    Kind.QR_CODE -> R.string.content_block_qr
}
fun Kind.blockIcon() = when (this) {
    Kind.API_KEY -> Icons.Default.Key
    Kind.API_TOKEN -> Icons.Default.Token
    Kind.SSH_KEY -> Icons.Default.Terminal
    Kind.GPG_KEY -> Icons.Default.EnhancedEncryption
    Kind.QR_CODE -> Icons.Default.QrCode
}
internal fun fieldLabel(key: String) = when (key) {
    "key" -> R.string.content_block_key
    "token" -> R.string.content_block_token
    "provider" -> R.string.content_block_provider
    "url", "api_base" -> R.string.content_block_url
    "algorithm" -> R.string.content_block_algorithm
    "keySize" -> R.string.content_block_key_size
    "format" -> R.string.content_block_format
    "publicKeyOpenSsh", "publicKey" -> R.string.content_block_public
    "privateKeyOpenSsh", "privateKey" -> R.string.content_block_private
    "fingerprintSha256", "fingerprint" -> R.string.content_block_fingerprint
    "comment", "userId" -> R.string.content_block_identity
    "content" -> R.string.content_block_content
    else -> R.string.notes
}
private fun secretField(key: String) = key in setOf("key", "token", "privateKey", "privateKeyOpenSsh", "content")

@Composable
fun PasswordContentBlockCard(stored: PasswordContentBlocks.Stored, actions: EntryContentActions? = null,
    onClick: () -> Unit) {
    val block = stored.block
    var menu by remember(stored.token) { mutableStateOf(false) }
    var confirm by remember(stored.token) { mutableStateOf(false) }
    val drag = LocalContentDrag.current
    val up = stringResource(R.string.move_up)
    val down = stringResource(R.string.move_down)
    val title = block?.title?.ifBlank { stringResource(block.kind.labelRes()) } ?: stringResource(R.string.content_block_unavailable)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("block_card_${stored.token}")
        .then(drag.modifier).semantics { customActions = buildList {
            actions?.moveUp?.let { add(CustomAccessibilityAction(up) { it(); true }) }
            actions?.moveDown?.let { add(CustomAccessibilityAction(down) { it(); true }) }
        } },
        shape = directReorderShape(actions?.groupIndex ?: 0, actions?.groupCount ?: 1, drag.draggedIndex),
        elevation = CardDefaults.cardElevation(defaultElevation = if (drag.dragging) 3.dp else 0.dp),
        colors = CardDefaults.cardColors(containerColor = if (drag.dragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(block?.kind?.blockIcon() ?: Icons.Default.Lock, null, Modifier.padding(10.dp).size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                Text(block?.let { stringResource(it.kind.labelRes()) } ?: stringResource(R.string.content_block_preserved),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (actions?.remove != null) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
                EditorActionMenu(menu, { menu = false }, listOf(
                    EditorMenuAction(stringResource(R.string.edit), Icons.Default.Edit, onClick = onClick),
                    EditorMenuAction(stringResource(R.string.delete), Icons.Default.Delete, onClick = { confirm = true }, destructive = true)))
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.entry_content_remove_title, title)) },
        text = { Text(stringResource(R.string.entry_content_remove_message)) },
        confirmButton = { TextButton(onClick = { confirm = false; actions?.remove?.invoke() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } })
}

/** Draft is isolated from the entry until Save. Dialog dismissal, including Back, cancels. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordContentBlockEditor(block: PasswordContentBlocks.Block, onSave: (PasswordContentBlocks.Block) -> Unit, onDismiss: () -> Unit,
    qrValues: (suspend () -> takagi.ru.monica.data.model.PasswordQrTemplate.Values)? = null) {
    if (block.kind == Kind.QR_CODE) {
        PasswordQrContentEditor(block, qrValues, onSave = { onSave(it) }, onDismiss = onDismiss)
        return
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        takagi.ru.monica.ui.screens.EmbeddedTemplateEditorScreen(block, onSave, onDismiss)
    }
}

internal fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) { val n = read(buffer); if (n < 0) break; require(output.size() + n <= limit); output.write(buffer, 0, n) }
    return output.toByteArray()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordContentBlockDetail(stored: PasswordContentBlocks.Stored, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val block = stored.block
    var exportValue by remember(stored.token) { mutableStateOf("") }
    var error by remember(stored.token) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(exportValue.toByteArray()) } } }
                .onFailure { error = true }
            exportValue = ""
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("content_block_detail_sheet")) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)
            .navigationBarsPadding().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(block?.title?.ifBlank { stringResource(block.kind.labelRes()) } ?: stringResource(R.string.content_block_unavailable),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(12.dp))
            if (block == null) Text(stringResource(R.string.content_block_preserved), Modifier.padding(12.dp))
            block?.let {
                if (it.kind == Kind.QR_CODE) PasswordQrBarcodePreview(it)
                val displayKeys = PasswordContentBlocks.editableKeys(it.kind).filter { key -> it.value(key).isNotEmpty() }
                displayKeys.forEachIndexed { index, key ->
                    var revealed by remember(stored.token, key) { mutableStateOf(false) }
                    var menu by remember(stored.token, key) { mutableStateOf(false) }
                    val value = it.value(key)
                    val label = stringResource(fieldLabel(key))
                    // Only known display fields are interpreted; opaque future fields remain in the stored envelope.
                    Surface(onClick = { menu = true }, color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.testTag("block_detail_$key"), shape = entryGroupShape(index, displayKeys.size)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(if (secretField(key) && !revealed) "••••••••" else value, maxLines = if (revealed) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.copy)) }, onClick = {
                                    ClipboardUtils.copyToClipboard(context, value, label, sensitive = true); menu = false
                                })
                                DropdownMenuItem(text = { Text(stringResource(if (revealed) R.string.hide_password else R.string.show_password)) },
                                    onClick = { revealed = !revealed; menu = false })
                                if (key.contains("Key")) DropdownMenuItem(text = { Text(stringResource(R.string.content_block_export)) }, onClick = {
                                    exportValue = value; exporter.launch(if (key.contains("private", true)) "private-key.txt" else "public-key.txt"); menu = false
                                })

                            }
                        }
                    }
                }
            }
            if (error) Text(stringResource(R.string.content_block_save_error), color = MaterialTheme.colorScheme.error)
        }
    }
}
