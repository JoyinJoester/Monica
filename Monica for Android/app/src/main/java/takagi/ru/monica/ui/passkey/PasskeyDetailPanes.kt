package takagi.ru.monica.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.ui.components.InfoField
import takagi.ru.monica.ui.components.InfoFieldWithCopy
import java.text.DateFormat
import java.util.Date

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
        Card(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(14.dp)
                            .size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.passkey_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
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
    onBindPassword: (() -> Unit)? = null,
    onChangeBinding: (() -> Unit)? = null,
    onOpenBoundPassword: (() -> Unit)?,
    onUnbindPassword: (() -> Unit)?,
    onDeletePasskey: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val createdTime = remember(passkey.createdAt) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(passkey.createdAt))
    }
    val lastUsedTime = remember(passkey.lastUsedAt, passkey.useCount) {
        passkey.getLastUsedFormatted()
    }
    val transports = remember(passkey.transports) {
        passkey.getTransportsList().joinToString(", ").ifBlank { "-" }
    }
    val displayTitle = remember(passkey.rpName, passkey.rpId) {
        passkey.rpName.ifBlank { passkey.rpId }
    }
    val bindingLabel = boundPasswordTitle ?: stringResource(R.string.common_account_not_configured)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PasskeyHeroCard(
            passkey = passkey,
            title = displayTitle,
            hasBoundPassword = boundPasswordTitle != null
        )

        PasskeyBindingCard(
            boundPasswordTitle = bindingLabel,
            hasBoundPassword = boundPasswordTitle != null,
            onBindPassword = onBindPassword,
            onChangeBinding = onChangeBinding,
            onOpenBoundPassword = onOpenBoundPassword,
            onUnbindPassword = onUnbindPassword
        )

        PasskeySectionCard(
            title = "Account",
            icon = Icons.Default.Person
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoField(
                    label = stringResource(R.string.passkey_detail_user),
                    value = passkey.userDisplayName.ifBlank { "-" }
                )
                InfoField(
                    label = stringResource(R.string.passkey_detail_username),
                    value = passkey.userName.ifBlank { "-" }
                )
                InfoFieldWithCopy(
                    label = "User ID",
                    value = passkey.userId.ifBlank { "-" },
                    context = context
                )
            }
        }

        PasskeySectionCard(
            title = stringResource(R.string.security),
            icon = Icons.Default.Security
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoField(label = "Algorithm", value = passkey.getAlgorithmName())
                InfoField(label = "Transports", value = transports)
                InfoField(
                    label = stringResource(R.string.passkey_discoverable),
                    value = if (passkey.isDiscoverable) stringResource(R.string.yes) else stringResource(R.string.no)
                )
                InfoField(
                    label = "User verification",
                    value = if (passkey.isUserVerificationRequired) stringResource(R.string.yes) else stringResource(R.string.no)
                )
                InfoField(
                    label = "Backed up",
                    value = if (passkey.isBackedUp) stringResource(R.string.yes) else stringResource(R.string.no)
                )
                InfoField(
                    label = "Sync status",
                    value = passkey.syncStatus.toReadableSyncLabel()
                )
                InfoField(
                    label = "Storage mode",
                    value = passkey.passkeyMode.toReadableModeLabel()
                )
            }
        }

        PasskeySectionCard(
            title = "Activity",
            icon = Icons.Default.AccessTime
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoField(
                    label = stringResource(R.string.passkey_detail_created),
                    value = createdTime
                )
                InfoField(
                    label = stringResource(R.string.passkey_detail_last_used),
                    value = lastUsedTime
                )
                InfoField(
                    label = stringResource(R.string.passkey_detail_use_count),
                    value = passkey.useCount.toString()
                )
                InfoField(
                    label = "Sign count",
                    value = passkey.signCount.toString()
                )
            }
        }

        PasskeySectionCard(
            title = "Technical",
            icon = Icons.Default.Info
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoField(label = "RP ID", value = passkey.rpId)
                InfoFieldWithCopy(
                    label = "Credential ID",
                    value = passkey.credentialId,
                    context = context
                )
                if (passkey.aaguid.isNotBlank()) {
                    InfoFieldWithCopy(
                        label = "AAGUID",
                        value = passkey.aaguid,
                        context = context
                    )
                }
                if (passkey.privateKeyAlias.isNotBlank()) {
                    InfoFieldWithCopy(
                        label = "Key alias",
                        value = passkey.privateKeyAlias,
                        context = context
                    )
                }
            }
        }

        if (passkey.notes.isNotBlank()) {
            PasskeySectionCard(
                title = stringResource(R.string.notes),
                icon = Icons.AutoMirrored.Filled.Notes
            ) {
                Text(
                    text = passkey.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.delete),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = stringResource(
                        R.string.passkey_delete_message,
                        displayTitle,
                        passkey.userName.ifBlank { "-" }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Button(
                    onClick = onDeletePasskey,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.passkey_delete_button))
                }
            }
        }
    }
}

@Composable
private fun PasskeyHeroCard(
    passkey: PasskeyEntry,
    title: String,
    hasBoundPassword: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = passkey.rpId,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PasskeyMetaPill(label = if (hasBoundPassword) "Bound" else "Unbound")
                PasskeyMetaPill(label = passkey.syncStatus.toReadableSyncLabel())
                if (passkey.isDiscoverable) {
                    PasskeyMetaPill(label = stringResource(R.string.passkey_discoverable))
                }
            }
        }
    }
}

@Composable
private fun PasskeyBindingCard(
    boundPasswordTitle: String,
    hasBoundPassword: Boolean,
    onBindPassword: (() -> Unit)?,
    onChangeBinding: (() -> Unit)?,
    onOpenBoundPassword: (() -> Unit)?,
    onUnbindPassword: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.bind_password),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Passkeys cannot be edited, so linked password actions live here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = boundPasswordTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!hasBoundPassword) {
                    if (onBindPassword != null) {
                        FilledTonalButton(
                            onClick = onBindPassword
                        ) {
                            Text(stringResource(R.string.bind_password))
                        }
                    }
                } else {
                    if (onOpenBoundPassword != null) {
                        FilledTonalButton(
                            onClick = onOpenBoundPassword
                        ) {
                            Text(stringResource(R.string.passkey_view_details))
                        }
                    }
                    if (onChangeBinding != null) {
                        OutlinedButton(
                            onClick = onChangeBinding
                        ) {
                            Text(stringResource(R.string.bound_password_change))
                        }
                    }
                    if (onUnbindPassword != null) {
                        OutlinedButton(
                            onClick = onUnbindPassword
                        ) {
                            Text(stringResource(R.string.unbind))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasskeySectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            content()
        }
    }
}

@Composable
private fun PasskeyMetaPill(label: String) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun String.toReadableSyncLabel(): String = when (this) {
    "SYNCED" -> "Synced"
    "SYNCING" -> "Syncing"
    "PENDING" -> "Pending"
    "FAILED" -> "Sync failed"
    "CONFLICT" -> "Conflict"
    "REFERENCE" -> "Reference"
    else -> "Local only"
}

private fun String.toReadableModeLabel(): String = when (this) {
    PasskeyEntry.MODE_BW_COMPAT -> "Bitwarden"
    PasskeyEntry.MODE_KEEPASS_COMPAT -> "KeePass"
    else -> "Local"
}
