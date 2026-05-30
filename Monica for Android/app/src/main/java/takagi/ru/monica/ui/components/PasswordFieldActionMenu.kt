/*
 * Reusable field action menu for sensitive detail pages.
 *
 * Password detail fields reuse the same actions in several places: copy, large
 * text display, QR display, sharing, Send draft creation, and optional secret
 * visibility toggles. Keeping this component separate prevents detail screens
 * from accumulating repeated field-action UI logic.
 */

package takagi.ru.monica.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import takagi.ru.monica.R
import takagi.ru.monica.ui.icons.MonicaIcons
import takagi.ru.monica.utils.ClipboardUtils

private val PasswordFieldActionMenuShape = RoundedCornerShape(20.dp)
private val PasswordFieldActionMenuOffset = DpOffset(x = (-236).dp, y = 8.dp)
private val PasswordFieldActionContentOffset = DpOffset(x = 0.dp, y = 6.dp)

@Stable
class PasswordFieldActionMenuState {
    var expanded by mutableStateOf(false)
    internal var showLargeDisplay by mutableStateOf(false)
    internal var showBarcode by mutableStateOf(false)

    fun open() {
        expanded = true
    }
}

@Composable
fun rememberPasswordFieldActionMenuState(): PasswordFieldActionMenuState {
    return remember { PasswordFieldActionMenuState() }
}

@Composable
fun PasswordFieldActionMenuButton(
    state: PasswordFieldActionMenuState,
    label: String,
    value: String,
    displayValue: String,
    context: Context,
    includeVisibilityToggle: Boolean = false,
    isVisible: Boolean = true,
    onToggleVisibility: (() -> Unit)? = null,
    onCreateSend: ((title: String, text: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp
) {
    Box(modifier = modifier) {
        IconButton(onClick = { state.open() }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.field_action_more),
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        PasswordFieldActionDropdown(
            state = state,
            label = label,
            value = value,
            displayValue = displayValue,
            context = context,
            includeVisibilityToggle = includeVisibilityToggle,
            isVisible = isVisible,
            onToggleVisibility = onToggleVisibility,
            onCreateSend = onCreateSend,
            offset = PasswordFieldActionMenuOffset
        )
    }

    PasswordFieldActionDialogs(state = state, label = label, value = value, context = context)
}

@Composable
fun PasswordFieldActionMenuHost(
    state: PasswordFieldActionMenuState,
    label: String,
    value: String,
    displayValue: String,
    context: Context,
    includeVisibilityToggle: Boolean = false,
    isVisible: Boolean = true,
    onToggleVisibility: (() -> Unit)? = null,
    onCreateSend: ((title: String, text: String) -> Unit)? = null
) {
    Box(modifier = Modifier.size(0.dp)) {
        PasswordFieldActionDropdown(
            state = state,
            label = label,
            value = value,
            displayValue = displayValue,
            context = context,
            includeVisibilityToggle = includeVisibilityToggle,
            isVisible = isVisible,
            onToggleVisibility = onToggleVisibility,
            onCreateSend = onCreateSend,
            offset = PasswordFieldActionContentOffset
        )
    }
    PasswordFieldActionDialogs(state = state, label = label, value = value, context = context)
}

@Composable
private fun PasswordFieldActionDialogs(
    state: PasswordFieldActionMenuState,
    label: String,
    value: String,
    context: Context
) {
    if (state.showLargeDisplay) {
        LargeFieldValueDialog(
            label = label,
            value = value,
            context = context,
            onDismiss = { state.showLargeDisplay = false }
        )
    }

    if (state.showBarcode) {
        FieldBarcodePage(
            label = label,
            value = value,
            onDismiss = { state.showBarcode = false }
        )
    }
}

@Composable
private fun PasswordFieldActionDropdown(
    state: PasswordFieldActionMenuState,
    label: String,
    value: String,
    displayValue: String,
    context: Context,
    includeVisibilityToggle: Boolean,
    isVisible: Boolean,
    onToggleVisibility: (() -> Unit)?,
    onCreateSend: ((title: String, text: String) -> Unit)?,
    offset: DpOffset
) {
    MaterialTheme(
        shapes = MaterialTheme.shapes.copy(
            extraSmall = PasswordFieldActionMenuShape,
            small = PasswordFieldActionMenuShape
        )
    ) {
        DropdownMenu(
            expanded = state.expanded,
            onDismissRequest = { state.expanded = false },
            offset = offset,
            modifier = Modifier
                .widthIn(min = 236.dp, max = 286.dp)
                .shadow(10.dp, PasswordFieldActionMenuShape)
                .clip(PasswordFieldActionMenuShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                    shape = PasswordFieldActionMenuShape
                )
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(stringResource(R.string.copy))
                        Text(
                            text = displayValue,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                leadingIcon = { Icon(MonicaIcons.Action.copy, contentDescription = null) },
                onClick = {
                    copyPasswordDetailFieldValue(context, label, value)
                    state.expanded = false
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            if (includeVisibilityToggle && onToggleVisibility != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (isVisible) {
                                stringResource(R.string.hide_password)
                            } else {
                                stringResource(R.string.show_password)
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (isVisible) MonicaIcons.Security.visibilityOff else MonicaIcons.Security.visibility,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onToggleVisibility()
                        state.expanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.field_action_show_large)) },
                leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                onClick = {
                    state.showLargeDisplay = true
                    state.expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.field_action_show_barcode)) },
                leadingIcon = { Icon(Icons.Default.QrCode2, contentDescription = null) },
                onClick = {
                    state.showBarcode = true
                    state.expanded = false
                }
            )
            if (onCreateSend != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.send_create_title)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                    onClick = {
                        onCreateSend(label, value)
                        state.expanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share)) },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                onClick = {
                    sharePasswordDetailFieldValue(context, label, value)
                    state.expanded = false
                }
            )
        }
    }
}

@Composable
private fun LargeFieldValueDialog(
    label: String,
    value: String,
    context: Context,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(scrollState)
            ) {
                SelectionContainer {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        dismissButton = {
            TextButton(onClick = { copyPasswordDetailFieldValue(context, label, value) }) {
                Text(stringResource(R.string.copy))
            }
        }
    )
}

@Composable
private fun FieldBarcodePage(
    label: String,
    value: String,
    onDismiss: () -> Unit
) {
    val bitmap = remember(value) {
        runCatching {
            BarcodeEncoder().encodeBitmap(value, BarcodeFormat.QR_CODE, 720, 720)
        }.getOrNull()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                    Text(
                        text = stringResource(R.string.field_action_show_barcode),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.field_action_show_barcode),
                                modifier = Modifier
                                    .size(280.dp)
                                    .background(Color.White, RoundedCornerShape(18.dp))
                                    .padding(14.dp)
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.field_action_barcode_failed),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

fun copyPasswordDetailFieldValue(context: Context, label: String, value: String) {
    ClipboardUtils.copyToClipboard(context, value, label)
    Toast.makeText(context, context.getString(R.string.copied, label), Toast.LENGTH_SHORT).show()
}

private fun sharePasswordDetailFieldValue(context: Context, label: String, value: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, label)
        putExtra(Intent.EXTRA_TEXT, value)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
}
