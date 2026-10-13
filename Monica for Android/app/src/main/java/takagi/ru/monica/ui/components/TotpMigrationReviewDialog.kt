package takagi.ru.monica.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import takagi.ru.monica.R
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.util.MigrationFailureReason
import takagi.ru.monica.util.TotpParseResult
import takagi.ru.monica.util.migrationImportIdentityKey

private enum class MigrationDuplicateState {
    NONE,
    EXISTING,
    IN_BATCH
}

private data class MigrationReviewEntry(
    val index: Int,
    val item: TotpParseResult,
    val duplicateState: MigrationDuplicateState,
    val existingTitle: String?
)

@Composable
fun TotpMigrationReviewDialog(
    items: List<TotpParseResult>,
    existingTitleFor: (TotpData) -> String?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onImport: (List<TotpParseResult>) -> Unit
) {
    val entries = buildMigrationReviewEntries(items, existingTitleFor)
    val entrySignature = entries.map {
        "${it.index}:${it.duplicateState}:${it.existingTitle.orEmpty()}"
    }
    val selectedState = remember(items, entrySignature) {
        mutableStateMapOf<Int, Boolean>().apply {
            entries.forEach { entry ->
                this[entry.index] = entry.duplicateState == MigrationDuplicateState.NONE
            }
        }
    }
    val selectedItems = entries
        .filter { selectedState[it.index] == true }
        .map(MigrationReviewEntry::item)
    val regularEntries = entries.filter { it.duplicateState == MigrationDuplicateState.NONE }
    val allRegularSelected = regularEntries.isNotEmpty() && regularEntries.all {
        selectedState[it.index] == true
    }

    Dialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 600.dp)
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.totp_migration_review_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(
                            R.string.totp_migration_review_summary,
                            items.size,
                            selectedItems.size
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        enabled = regularEntries.isNotEmpty() && !isSaving,
                        onClick = {
                            if (allRegularSelected) {
                                entries.forEach { selectedState[it.index] = false }
                            } else {
                                regularEntries.forEach { selectedState[it.index] = true }
                            }
                        }
                    ) {
                        Text(
                            stringResource(
                                if (allRegularSelected) {
                                    R.string.deselect_all
                                } else {
                                    R.string.select_all
                                }
                            )
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        items = entries,
                        key = { entry -> "${entry.index}:${migrationImportIdentityKey(entry.item.totpData)}" }
                    ) { entry ->
                        MigrationReviewRow(
                            entry = entry,
                            checked = selectedState[entry.index] == true,
                            enabled = !isSaving && entry.duplicateState != MigrationDuplicateState.IN_BATCH,
                            onCheckedChange = { checked -> selectedState[entry.index] = checked }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = !isSaving,
                        onClick = onDismiss
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = selectedItems.isNotEmpty() && !isSaving,
                        onClick = { onImport(selectedItems) }
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            stringResource(
                                R.string.totp_migration_import_selected,
                                selectedItems.size
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MigrationReviewRow(
    entry: MigrationReviewEntry,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val data = entry.item.totpData
    val title = entry.item.suggestedTitle
        .ifBlank { stringResource(R.string.untitled) }
    val identityText = listOf(data.issuer, data.accountName)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" · ")
    val typeText = if (data.otpType == OtpType.HOTP) "HOTP" else "TOTP"
    val parameterText = if (data.otpType == OtpType.HOTP) {
        stringResource(
            R.string.totp_migration_hotp_parameters,
            typeText,
            data.algorithm,
            data.digits,
            data.counter
        )
    } else {
        stringResource(
            R.string.totp_migration_totp_parameters,
            typeText,
            data.algorithm,
            data.digits,
            data.period
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (identityText.isNotBlank() && identityText != title) {
                Text(
                    text = identityText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = parameterText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MigrationDuplicateLabel(entry)
        }

        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun MigrationDuplicateLabel(entry: MigrationReviewEntry) {
    val text = when (entry.duplicateState) {
        MigrationDuplicateState.NONE -> null
        MigrationDuplicateState.EXISTING -> stringResource(
            R.string.totp_migration_existing_item,
            entry.existingTitle.orEmpty().ifBlank { stringResource(R.string.untitled) }
        )
        MigrationDuplicateState.IN_BATCH -> stringResource(R.string.totp_migration_batch_duplicate)
    }
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = when (entry.duplicateState) {
                MigrationDuplicateState.EXISTING -> MaterialTheme.colorScheme.tertiary
                MigrationDuplicateState.IN_BATCH -> MaterialTheme.colorScheme.error
                MigrationDuplicateState.NONE -> Color.Unspecified
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun buildMigrationReviewEntries(
    items: List<TotpParseResult>,
    existingTitleFor: (TotpData) -> String?
): List<MigrationReviewEntry> {
    val seen = mutableSetOf<String>()
    return items.mapIndexed { index, item ->
        val identityKey = migrationImportIdentityKey(item.totpData)
        val duplicateInBatch = !seen.add(identityKey)
        val existingTitle = if (duplicateInBatch) null else existingTitleFor(item.totpData)
        MigrationReviewEntry(
            index = index,
            item = item,
            duplicateState = when {
                duplicateInBatch -> MigrationDuplicateState.IN_BATCH
                existingTitle != null -> MigrationDuplicateState.EXISTING
                else -> MigrationDuplicateState.NONE
            },
            existingTitle = existingTitle
        )
    }
}

@StringRes
fun migrationFailureMessageRes(reason: MigrationFailureReason): Int {
    return when (reason) {
        MigrationFailureReason.PAYLOAD_TOO_LARGE -> R.string.totp_migration_error_too_large
        MigrationFailureReason.MULTI_QR_BATCH -> R.string.totp_migration_error_multiple_qr
        MigrationFailureReason.UNSUPPORTED_VERSION -> R.string.totp_migration_error_version
        MigrationFailureReason.UNSUPPORTED_ALGORITHM,
        MigrationFailureReason.UNSUPPORTED_DIGITS,
        MigrationFailureReason.UNSUPPORTED_OTP_TYPE -> R.string.totp_migration_error_parameters
        MigrationFailureReason.MISSING_ACCOUNT_NAME -> R.string.totp_migration_error_missing_name
        MigrationFailureReason.MALFORMED_PAYLOAD -> R.string.totp_migration_error_invalid
    }
}
