package takagi.ru.monica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.ui.common.layout.InspectorRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun PasskeyOverviewPane(
    totalPasskeys: Int,
    boundPasskeys: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.passkey_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.passkey_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.passkey_count, totalPasskeys)) }
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text("${stringResource(R.string.passkey_bound_label)} $boundPasskeys") }
                    )
                }
            }
        }
    }
}

@Composable
internal fun PasskeyDetailPane(
    passkey: PasskeyEntry,
    boundPasswordTitle: String?,
    totalPasskeys: Int,
    boundPasskeys: Int,
    onOpenBoundPassword: (() -> Unit)?,
    onUnbindPassword: (() -> Unit)?,
    onDeletePasskey: () -> Unit,
    modifier: Modifier = Modifier
) {
    val createdTime = remember(passkey.createdAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(passkey.createdAt))
    }
    val transports = remember(passkey.transports) {
        passkey.getTransportsList().joinToString(", ").ifBlank { "-" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = passkey.rpName.ifBlank { passkey.rpId },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = passkey.rpId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.passkey_count, totalPasskeys)) })
                AssistChip(onClick = {}, label = { Text("${stringResource(R.string.passkey_bound_label)} $boundPasskeys") })
            }
        }

        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InspectorRow(stringResource(R.string.passkey_detail_user), passkey.userDisplayName.ifBlank { "-" })
                InspectorRow(stringResource(R.string.passkey_detail_username), passkey.userName.ifBlank { "-" })
                InspectorRow(stringResource(R.string.passkey_detail_created), createdTime)
                InspectorRow(stringResource(R.string.passkey_detail_last_used), passkey.getLastUsedFormatted())
                InspectorRow(stringResource(R.string.passkey_detail_use_count), passkey.useCount.toString())
            }
        }

        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.security),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                InspectorRow("Algorithm", passkey.getAlgorithmName())
                InspectorRow("Transports", transports)
                InspectorRow("Discoverable", if (passkey.isDiscoverable) stringResource(R.string.yes) else stringResource(R.string.no))
                InspectorRow("User verification", if (passkey.isUserVerificationRequired) stringResource(R.string.yes) else stringResource(R.string.no))
                InspectorRow("Backed up", if (passkey.isBackedUp) stringResource(R.string.yes) else stringResource(R.string.no))
                InspectorRow("Sync status", passkey.syncStatus)
                SelectionContainer {
                    InspectorRow("Credential ID", passkey.credentialId)
                }
            }
        }

        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.bind_password),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = boundPasswordTitle ?: stringResource(R.string.common_account_not_configured),
                    style = MaterialTheme.typography.bodyLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = { onOpenBoundPassword?.invoke() },
                        enabled = onOpenBoundPassword != null
                    ) {
                        Text(stringResource(R.string.passkey_view_details))
                    }
                    OutlinedButton(
                        onClick = { onUnbindPassword?.invoke() },
                        enabled = onUnbindPassword != null
                    ) {
                        Text(stringResource(R.string.unbind))
                    }
                }
            }
        }

        if (passkey.notes.isNotBlank()) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.notes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = passkey.notes,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.delete),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.passkey_delete_message, passkey.rpName.ifBlank { passkey.rpId }, passkey.userName.ifBlank { "-" }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onDeletePasskey,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(stringResource(R.string.passkey_delete_button))
                }
            }
        }
    }
}
