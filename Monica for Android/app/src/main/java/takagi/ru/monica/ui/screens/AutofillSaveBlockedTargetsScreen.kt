package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.autofill_ng.AutofillPreferences

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AutofillSaveBlockedTargetsScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { AutofillPreferences(context) }
    val records by preferences.saveBlockedTargetRecords.collectAsState(initial = emptyList())
    val sortedRecords = remember(records) {
        records.sortedWith(
            compareBy<AutofillPreferences.SaveBlockedTargetRecord> { it.webDomain == null }
                .thenBy { it.webDomain ?: it.packageName.orEmpty() }
        )
    }
    val appLabels = remember(context, sortedRecords) {
        sortedRecords
            .mapNotNull { record ->
                val packageName = record.packageName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                packageName to resolveAutofillBlockedTargetAppLabel(context, packageName)
            }
            .toMap()
    }
    var showClearAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.autofill_save_blocked_targets_manage)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.autofill_settings_back),
                        )
                    }
                },
                actions = {
                    if (sortedRecords.isNotEmpty()) {
                        TextButton(onClick = { showClearAllDialog = true }) {
                            Text(text = stringResource(R.string.autofill_blocked_fields_clear_all))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Block,
                                    contentDescription = null,
                                    modifier = Modifier.padding(10.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stringResource(R.string.autofill_save_blocked_targets_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.autofill_save_blocked_targets_manage_desc,
                                        sortedRecords.size,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f),
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.autofill_save_blocked_targets_dialog_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f),
                        )
                    }
                }
            }

            if (sortedRecords.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.autofill_save_blocked_targets_empty),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(
                    items = sortedRecords,
                    key = { it.key },
                ) { record ->
                    SaveBlockedTargetCard(
                        record = record,
                        appLabel = record.packageName?.let(appLabels::get),
                        onRemove = {
                            scope.launch { preferences.removeSaveBlockedTarget(record.key) }
                        },
                    )
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(text = stringResource(R.string.autofill_save_blocked_targets_clear_all)) },
            text = { Text(text = stringResource(R.string.autofill_save_blocked_targets_dialog_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        scope.launch { preferences.clearSaveBlockedTargets() }
                    },
                ) {
                    Text(text = stringResource(R.string.autofill_save_blocked_targets_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(text = stringResource(R.string.autofill_blacklist_dialog_done))
                }
            },
        )
    }
}

@Composable
private fun SaveBlockedTargetCard(
    record: AutofillPreferences.SaveBlockedTargetRecord,
    appLabel: String?,
    onRemove: () -> Unit,
) {
    val isDomainTarget = !record.webDomain.isNullOrBlank()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(
                        imageVector = if (isDomainTarget) Icons.Outlined.Language else Icons.Outlined.Apps,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = record.webDomain ?: appLabel ?: record.packageName.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = if (isDomainTarget) {
                            stringResource(R.string.autofill_save_blocked_targets_badge_domain)
                        } else {
                            stringResource(R.string.autofill_save_blocked_targets_badge_app)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                TextButton(onClick = onRemove) {
                    Text(text = stringResource(R.string.autofill_blocked_fields_remove))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            SaveBlockedTargetSourceCard(
                icon = if (isDomainTarget) Icons.Outlined.Language else Icons.Outlined.Apps,
                title = record.webDomain ?: appLabel ?: record.packageName.orEmpty(),
                subtitle = when {
                    isDomainTarget -> stringResource(R.string.autofill_save_blocked_targets_domain_only)
                    !record.packageName.isNullOrBlank() && !appLabel.isNullOrBlank() -> record.packageName
                    else -> stringResource(R.string.autofill_save_blocked_targets_app_only)
                },
            )
        }
    }
}

@Composable
private fun SaveBlockedTargetSourceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun resolveAutofillBlockedTargetAppLabel(
    context: android.content.Context,
    packageName: String?,
): String? {
    val normalized = packageName?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val appInfo = context.packageManager.getApplicationInfo(normalized, 0)
        context.packageManager.getApplicationLabel(appInfo).toString().trim().ifBlank { null }
    }.getOrNull()
}
