package takagi.ru.monica.ui.password

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.ui.components.InfoFieldWithCopy
import takagi.ru.monica.ui.components.PasswordField
import takagi.ru.monica.ui.icons.MonicaIcons
import takagi.ru.monica.viewmodel.BitwardenSyncRawHistoryItem
import takagi.ru.monica.viewmodel.BitwardenSyncSnapshotFieldGroupPreview
import takagi.ru.monica.viewmodel.BitwardenSyncSnapshotFieldPreview
import takagi.ru.monica.viewmodel.BitwardenSyncSnapshotPreview
import takagi.ru.monica.viewmodel.BitwardenSyncSnapshotPreviewStatus
import java.text.DateFormat
import java.util.Date

@Composable
fun BitwardenSyncSnapshotSection(
    currentPreview: BitwardenSyncSnapshotPreview?,
    history: List<BitwardenSyncRawHistoryItem>,
    context: Context,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    var currentExpanded by remember { mutableStateOf(false) }
    val historyExpandedState = remember { mutableStateMapOf<Long, Boolean>() }
    val rawVisibilityState = remember { mutableStateMapOf<Long, Boolean>() }
    val sensitiveVisibilityState = remember { mutableStateMapOf<String, Boolean>() }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(28.dp, 28.dp, 20.dp, 20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.bitwarden_sync_raw_history_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.bitwarden_sync_raw_history_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SnapshotCurrentDataCard(
                preview = currentPreview,
                expanded = currentExpanded,
                onToggleExpanded = { currentExpanded = !currentExpanded },
                sensitiveVisibilityState = sensitiveVisibilityState,
                context = context
            )

            if (history.isEmpty()) {
                Text(
                    text = stringResource(R.string.bitwarden_sync_snapshot_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                history.forEachIndexed { index, item ->
                    val expanded = historyExpandedState[item.id] == true
                    val previewTitle = item.preview?.title
                        ?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.untitled)
                    SnapshotExpandableCard(
                        expanded = expanded,
                        onToggleExpanded = { historyExpandedState[item.id] = !expanded },
                        badge = if (index == 0) stringResource(R.string.bitwarden_sync_raw_latest) else null,
                        badgeContainer = MaterialTheme.colorScheme.primaryContainer,
                        badgeContent = MaterialTheme.colorScheme.onPrimaryContainer,
                        eyebrow = dateFormatter.format(Date(item.capturedAt)),
                        title = when {
                            item.preview?.isReady == true && item.preview.username.isNotBlank() ->
                                "$previewTitle  ·  ${item.preview.username}"
                            item.preview?.title?.isNotBlank() == true -> previewTitle
                            else -> stringResource(R.string.bitwarden_sync_snapshot_tap_to_preview)
                        },
                        trailingText = item.operation
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = stringResource(
                                    R.string.bitwarden_sync_raw_meta,
                                    snapshotSourceLabel(item.payloadSource),
                                    item.responseCode?.toString() ?: "-",
                                    item.payloadDigest
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.endpoint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            SnapshotPreviewBody(
                                item = item,
                                sensitiveVisibilityState = sensitiveVisibilityState,
                                context = context
                            )
                            SnapshotRawPayload(
                                payload = item.payload.orEmpty(),
                                isVisible = rawVisibilityState[item.id] == true,
                                onToggle = {
                                    rawVisibilityState[item.id] = rawVisibilityState[item.id] != true
                                },
                                context = context
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotExpandableCard(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    eyebrow: String,
    title: String,
    trailingText: String? = null,
    badge: String? = null,
    badgeContainer: Color = Color.Unspecified,
    badgeContent: Color = Color.Unspecified,
    content: @Composable () -> Unit
) {
    val resolvedBadgeContainer = if (badgeContainer == Color.Unspecified) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        badgeContainer
    }
    val resolvedBadgeContent = if (badgeContent == Color.Unspecified) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        badgeContent
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpanded() },
        shape = RoundedCornerShape(24.dp, 18.dp, 24.dp, 18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!badge.isNullOrBlank()) {
                        Surface(
                            shape = CircleShape,
                            color = resolvedBadgeContainer
                        ) {
                            Text(
                                text = badge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = resolvedBadgeContent
                            )
                        }
                    }
                    Text(
                        text = eyebrow,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!trailingText.isNullOrBlank()) {
                        Text(
                            text = trailingText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.show),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SnapshotCurrentDataCard(
    preview: BitwardenSyncSnapshotPreview?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    sensitiveVisibilityState: SnapshotStateMap<String, Boolean>,
    context: Context
) {
    SnapshotExpandableCard(
        expanded = expanded,
        onToggleExpanded = onToggleExpanded,
        badge = stringResource(R.string.bitwarden_sync_snapshot_current_badge),
        eyebrow = stringResource(R.string.bitwarden_sync_snapshot_current_title),
        title = when {
            preview?.isReady == true && preview.username.isNotBlank() ->
                "${preview.title.takeIf { it.isNotBlank() } ?: stringResource(R.string.untitled)}  ·  ${preview.username}"
            preview?.isReady == true ->
                preview.title.takeIf { it.isNotBlank() } ?: stringResource(R.string.untitled)
            else -> stringResource(R.string.bitwarden_sync_snapshot_current_description)
        },
        trailingText = null
    ) {
        when (preview) {
            null -> SnapshotStatusMessage(
                text = stringResource(R.string.bitwarden_sync_snapshot_current_unavailable)
            )
            else -> SnapshotPreviewContent(
                itemKey = "current",
                preview = preview,
                sensitiveVisibilityState = sensitiveVisibilityState,
                context = context
            )
        }
    }
}

@Composable
private fun SnapshotPreviewBody(
    item: BitwardenSyncRawHistoryItem,
    sensitiveVisibilityState: SnapshotStateMap<String, Boolean>,
    context: Context
) {
    val preview = item.preview
    when {
        preview == null ->
            SnapshotStatusMessage(stringResource(R.string.bitwarden_sync_raw_payload_unavailable))
        preview.status == BitwardenSyncSnapshotPreviewStatus.INVALID_PAYLOAD ->
            SnapshotStatusMessage(stringResource(R.string.bitwarden_sync_snapshot_preview_invalid))
        else ->
            SnapshotPreviewContent(
                itemKey = "history_${item.id}",
                preview = preview,
                sensitiveVisibilityState = sensitiveVisibilityState,
                context = context
            )
    }
}

@Composable
private fun SnapshotPreviewContent(
    itemKey: String,
    preview: BitwardenSyncSnapshotPreview,
    sensitiveVisibilityState: SnapshotStateMap<String, Boolean>,
    context: Context
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = preview.title.takeIf { it.isNotBlank() } ?: stringResource(R.string.untitled),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            when (preview.status) {
                BitwardenSyncSnapshotPreviewStatus.VAULT_LOCKED -> SnapshotStatusMessage(
                    text = stringResource(R.string.bitwarden_sync_snapshot_preview_locked)
                )
                BitwardenSyncSnapshotPreviewStatus.UNSUPPORTED_TYPE -> SnapshotStatusMessage(
                    text = stringResource(
                        R.string.bitwarden_sync_snapshot_preview_unsupported,
                        preview.cipherType ?: -1
                    )
                )
                else -> Unit
            }

            if (preview.username.isNotBlank()) {
                InfoFieldWithCopy(
                    label = stringResource(R.string.username),
                    value = preview.username,
                    context = context
                )
            }

            if (preview.password.isNotBlank()) {
                val key = "password_$itemKey"
                PasswordField(
                    label = stringResource(R.string.password),
                    value = preview.password,
                    visible = sensitiveVisibilityState[key] == true,
                    onToggleVisibility = { sensitiveVisibilityState[key] = sensitiveVisibilityState[key] != true },
                    context = context
                )
            }

            if (preview.totp.isNotBlank()) {
                val key = "totp_$itemKey"
                PasswordField(
                    label = stringResource(R.string.verification_code),
                    value = preview.totp,
                    visible = sensitiveVisibilityState[key] == true,
                    onToggleVisibility = { sensitiveVisibilityState[key] = sensitiveVisibilityState[key] != true },
                    context = context
                )
            }

            if (preview.websites.isNotEmpty()) {
                val websitesText = preview.websites.joinToString("\n")
                InfoFieldWithCopy(
                    label = stringResource(R.string.website),
                    value = websitesText,
                    copyValue = websitesText,
                    context = context
                )
            }

            if (preview.notes.isNotBlank()) {
                InfoFieldWithCopy(
                    label = stringResource(R.string.notes),
                    value = preview.notes,
                    context = context
                )
            }

            if (preview.customFields.isNotEmpty()) {
                SnapshotFieldGroup(
                    title = stringResource(R.string.bitwarden_sync_snapshot_custom_fields),
                    itemKey = itemKey,
                    fields = preview.customFields,
                    sensitiveVisibilityState = sensitiveVisibilityState,
                    context = context
                )
            }

            if (preview.metadataFields.isNotEmpty()) {
                SnapshotFieldGroup(
                    title = "Bitwarden Metadata",
                    itemKey = "${itemKey}_meta",
                    fields = preview.metadataFields,
                    sensitiveVisibilityState = sensitiveVisibilityState,
                    context = context
                )
            }

            preview.extraSections.forEachIndexed { index, section ->
                SnapshotSectionGroup(
                    itemKey = "${itemKey}_section_$index",
                    section = section,
                    sensitiveVisibilityState = sensitiveVisibilityState,
                    context = context
                )
            }
        }
    }
}

@Composable
private fun SnapshotFieldGroup(
    title: String,
    itemKey: String,
    fields: List<BitwardenSyncSnapshotFieldPreview>,
    sensitiveVisibilityState: SnapshotStateMap<String, Boolean>,
    context: Context
) {
    if (fields.isEmpty()) return
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    fields.forEachIndexed { index, field ->
        SnapshotField(
            itemKey = itemKey,
            fieldIndex = index,
            field = field,
            sensitiveVisibilityState = sensitiveVisibilityState,
            context = context
        )
    }
}

@Composable
private fun SnapshotSectionGroup(
    itemKey: String,
    section: BitwardenSyncSnapshotFieldGroupPreview,
    sensitiveVisibilityState: SnapshotStateMap<String, Boolean>,
    context: Context
) {
    SnapshotFieldGroup(
        title = section.title,
        itemKey = itemKey,
        fields = section.fields,
        sensitiveVisibilityState = sensitiveVisibilityState,
        context = context
    )
}

@Composable
private fun SnapshotField(
    itemKey: String,
    fieldIndex: Int,
    field: BitwardenSyncSnapshotFieldPreview,
    sensitiveVisibilityState: SnapshotStateMap<String, Boolean>,
    context: Context
) {
    if (field.hidden) {
        val key = "field_${itemKey}_$fieldIndex"
        PasswordField(
            label = field.name,
            value = field.value,
            visible = sensitiveVisibilityState[key] == true,
            onToggleVisibility = { sensitiveVisibilityState[key] = sensitiveVisibilityState[key] != true },
            context = context
        )
        return
    }

    InfoFieldWithCopy(
        label = field.name,
        value = field.value,
        context = context
    )
}

@Composable
private fun SnapshotStatusMessage(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SnapshotRawPayload(
    payload: String,
    isVisible: Boolean,
    onToggle: () -> Unit,
    context: Context
) {
    if (payload.isBlank()) return

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isVisible) {
                        stringResource(R.string.bitwarden_sync_snapshot_hide_raw)
                    } else {
                        stringResource(R.string.bitwarden_sync_snapshot_show_raw)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row {
                    IconButton(onClick = onToggle) {
                        Icon(
                            imageVector = if (isVisible) MonicaIcons.Security.visibilityOff else MonicaIcons.Security.visibility,
                            contentDescription = if (isVisible) stringResource(R.string.hide) else stringResource(R.string.show),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(
                            context.getString(R.string.bitwarden_sync_raw_history_title),
                            payload
                        )
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, context.getString(R.string.copy), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = MonicaIcons.Action.copy,
                            contentDescription = stringResource(R.string.copy),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = if (isVisible) payload else stringResource(R.string.bitwarden_sync_raw_payload_hidden, payload.length),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (isVisible) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun snapshotSourceLabel(payloadSource: String): String {
    return when (payloadSource) {
        "REQUEST" -> stringResource(R.string.bitwarden_sync_raw_source_request)
        "RESPONSE" -> stringResource(R.string.bitwarden_sync_raw_source_response)
        "SYNC_RESPONSE" -> stringResource(R.string.bitwarden_sync_raw_source_sync)
        else -> payloadSource
    }
}
