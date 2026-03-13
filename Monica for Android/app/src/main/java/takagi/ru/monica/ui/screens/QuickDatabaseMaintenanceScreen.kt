package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.maintenance.QuickMaintenanceCategory
import takagi.ru.monica.data.maintenance.QuickMaintenanceCategoryNote
import takagi.ru.monica.data.maintenance.QuickMaintenanceCategoryResult
import takagi.ru.monica.data.maintenance.QuickMaintenanceDiffItem
import takagi.ru.monica.data.maintenance.QuickMaintenanceSource
import takagi.ru.monica.data.maintenance.QuickMaintenanceSourceDiff
import takagi.ru.monica.data.maintenance.QuickMaintenanceSourceStats
import takagi.ru.monica.data.maintenance.QuickMaintenanceSourceKind
import takagi.ru.monica.viewmodel.QuickDatabaseMaintenanceUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDatabaseMaintenanceScreen(
    uiState: QuickDatabaseMaintenanceUiState,
    onNavigateBack: () -> Unit,
    onIncludePasswordsChange: (Boolean) -> Unit,
    onIncludeAuthenticatorsChange: (Boolean) -> Unit,
    onIncludeBankCardsChange: (Boolean) -> Unit,
    onIncludePasskeysChange: (Boolean) -> Unit,
    onRun: () -> Unit
) {
    val hasCategorySelection = uiState.includePasswords ||
        uiState.includeAuthenticators ||
        uiState.includeBankCards ||
        uiState.includePasskeys
    val sourceStatsForDisplay = uiState.latestResult?.sourceStats ?: uiState.sourceStats

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quick_database_maintenance_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f)
                        ) {
                            Box(
                                modifier = Modifier.size(52.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.quick_database_maintenance_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(R.string.quick_database_maintenance_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            item {
                MaintenanceSection(
                    title = stringResource(R.string.quick_database_maintenance_scope_title),
                    body = stringResource(R.string.quick_database_maintenance_scope_desc)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CategoryButtonRow(
                            first = CategoryOption(
                                label = stringResource(R.string.data_type_passwords),
                                selected = uiState.includePasswords,
                                icon = Icons.Default.Password,
                                onSelectedChange = onIncludePasswordsChange
                            ),
                            second = CategoryOption(
                                label = stringResource(R.string.data_type_totp),
                                selected = uiState.includeAuthenticators,
                                icon = Icons.Default.History,
                                onSelectedChange = onIncludeAuthenticatorsChange
                            )
                        )
                        CategoryButtonRow(
                            first = CategoryOption(
                                label = stringResource(R.string.quick_database_maintenance_card_pack_label),
                                selected = uiState.includeBankCards,
                                icon = Icons.Default.CreditCard,
                                onSelectedChange = onIncludeBankCardsChange
                            ),
                            second = CategoryOption(
                                label = stringResource(R.string.nav_passkey),
                                selected = uiState.includePasskeys,
                                icon = Icons.Default.Key,
                                onSelectedChange = onIncludePasskeysChange
                            )
                        )
                        Text(
                            text = stringResource(R.string.quick_database_maintenance_selection_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                MaintenanceSection(
                    title = stringResource(R.string.quick_database_maintenance_sources_title),
                    body = stringResource(
                        R.string.quick_database_maintenance_sources_desc,
                        uiState.connectedSources.size
                    )
                ) {
                    if (uiState.isLoading) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text = stringResource(R.string.loading_default),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (uiState.connectedSources.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.quick_database_maintenance_sources_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                uiState.connectedSources.forEach { source ->
                                    SourceRow(source = source)
                                }
                            }
                            Text(
                                text = stringResource(R.string.quick_database_maintenance_limit_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                MaintenanceSection(
                    title = stringResource(R.string.quick_database_maintenance_run_title),
                    body = if (uiState.isRunning) {
                        stringResource(R.string.quick_database_maintenance_running)
                    } else {
                        stringResource(R.string.quick_database_maintenance_run_desc)
                    }
                ) {
                    Button(
                        onClick = onRun,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp),
                        enabled = !uiState.isRunning && hasCategorySelection
                    ) {
                        if (uiState.isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null
                            )
                        }
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = if (uiState.isRunning) {
                                stringResource(R.string.quick_database_maintenance_button_running)
                            } else {
                                stringResource(R.string.quick_database_maintenance_button_label)
                            }
                        )
                    }
                    Text(
                        text = stringResource(R.string.quick_database_maintenance_run_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (sourceStatsForDisplay.isNotEmpty()) {
                val maxTotal = sourceStatsForDisplay.maxOf { it.totalCount }
                val minTotal = sourceStatsForDisplay.minOf { it.totalCount }
                item {
                    MaintenanceSection(
                        title = stringResource(R.string.quick_database_maintenance_stats_title),
                        body = stringResource(
                            R.string.quick_database_maintenance_stats_desc,
                            sourceStatsForDisplay.size,
                            maxTotal,
                            minTotal
                        )
                    ) {}
                }
                items(sourceStatsForDisplay) { stats ->
                    SourceStatsCard(stats = stats, sources = uiState.connectedSources, maxTotal = maxTotal)
                }
            }

            uiState.errorResId?.let { errorResId ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = stringResource(errorResId),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            uiState.latestResult?.let { result ->
                if (result.sourceDiffs.isNotEmpty()) {
                    item {
                        MaintenanceSection(
                            title = stringResource(R.string.quick_database_maintenance_diff_title),
                            body = stringResource(R.string.quick_database_maintenance_diff_desc)
                        ) {}
                    }
                    items(result.sourceDiffs) { diff ->
                        SourceDiffCard(
                            diff = diff,
                            sources = result.sources
                        )
                    }
                }
                item {
                    MaintenanceSection(
                        title = stringResource(R.string.quick_database_maintenance_result_title),
                        body = stringResource(
                            R.string.quick_database_maintenance_summary_line,
                            result.totalMatchedGroups,
                            result.totalUpdatedEntries,
                            result.totalCreatedEntries,
                            result.totalSkippedGroups
                        )
                    ) {}
                }
                items(result.categoryResults) { item ->
                    ResultCard(item)
                }
            }
        }
    }
}

@Composable
private fun SourceStatsCard(
    stats: QuickMaintenanceSourceStats,
    sources: List<QuickMaintenanceSource>,
    maxTotal: Int
) {
    val source = sources.firstOrNull { it.key == stats.sourceKey }
    val sourceLabel = source?.let { sourceDisplayLabel(it) } ?: stats.sourceKey
    val gap = (maxTotal - stats.totalCount).coerceAtLeast(0)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = sourceLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.quick_database_maintenance_stats_total, stats.totalCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (gap == 0) {
                    stringResource(R.string.quick_database_maintenance_stats_leader)
                } else {
                    stringResource(R.string.quick_database_maintenance_stats_gap, gap)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.quick_database_maintenance_stats_breakdown,
                    stats.passwordCount,
                    stats.authenticatorCount,
                    stats.bankCardCount,
                    stats.passkeyCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SourceDiffCard(
    diff: QuickMaintenanceSourceDiff,
    sources: List<QuickMaintenanceSource>
) {
    val source = sources.firstOrNull { it.key == diff.sourceKey }
    val sourceLabel = source?.let { sourceDisplayLabel(it) } ?: diff.sourceKey
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = sourceLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.quick_database_maintenance_diff_count, diff.extraItems.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            diff.extraItems.forEach { item ->
                SourceDiffItemRow(item = item, sources = sources)
            }
        }
    }
}

@Composable
private fun SourceDiffItemRow(
    item: QuickMaintenanceDiffItem,
    sources: List<QuickMaintenanceSource>
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = diffCategoryLabel(item.category),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            val missingLabels = item.missingSourceKeys.map { key ->
                sources.firstOrNull { it.key == key }?.let { sourceDisplayLabel(it) } ?: key
            }.joinToString(" / ")
            Text(
                text = stringResource(R.string.quick_database_maintenance_diff_missing_in, missingLabels),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun diffCategoryLabel(category: QuickMaintenanceCategory): String {
    return when (category) {
        QuickMaintenanceCategory.PASSWORDS -> stringResource(R.string.data_type_passwords)
        QuickMaintenanceCategory.AUTHENTICATORS -> stringResource(R.string.data_type_totp)
        QuickMaintenanceCategory.BANK_CARDS -> stringResource(R.string.quick_database_maintenance_card_pack_label)
        QuickMaintenanceCategory.PASSKEYS -> stringResource(R.string.nav_passkey)
    }
}

private data class CategoryOption(
    val label: String,
    val selected: Boolean,
    val icon: ImageVector,
    val onSelectedChange: (Boolean) -> Unit
)

@Composable
private fun CategoryButtonRow(
    first: CategoryOption,
    second: CategoryOption
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CategoryToggleButton(
            label = first.label,
            selected = first.selected,
            icon = first.icon,
            onSelectedChange = first.onSelectedChange,
            modifier = Modifier.weight(1f)
        )
        CategoryToggleButton(
            label = second.label,
            selected = second.selected,
            icon = second.icon,
            onSelectedChange = second.onSelectedChange,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CategoryToggleButton(
    label: String,
    selected: Boolean,
    icon: ImageVector,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = { onSelectedChange(false) },
            modifier = modifier.heightIn(min = 52.dp)
        ) {
            CategoryButtonContent(label = label, icon = icon)
        }
    } else {
        OutlinedButton(
            onClick = { onSelectedChange(true) },
            modifier = modifier.heightIn(min = 52.dp)
        ) {
            CategoryButtonContent(label = label, icon = icon)
        }
    }
}

@Composable
private fun CategoryButtonContent(
    label: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SourceRow(source: QuickMaintenanceSource) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (source.kind) {
                    QuickMaintenanceSourceKind.KEEPASS -> Icons.Default.Key
                    QuickMaintenanceSourceKind.BITWARDEN -> Icons.Default.Sync
                    QuickMaintenanceSourceKind.MONICA_LOCAL -> Icons.Default.Password
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = sourceDisplayLabel(source),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = sourceTypeLabel(source.kind),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun sourceDisplayLabel(source: QuickMaintenanceSource): String {
    return when (source.kind) {
        QuickMaintenanceSourceKind.MONICA_LOCAL -> stringResource(R.string.dedup_scope_local)
        else -> source.label
    }
}

@Composable
private fun sourceTypeLabel(kind: QuickMaintenanceSourceKind): String {
    return when (kind) {
        QuickMaintenanceSourceKind.MONICA_LOCAL -> stringResource(R.string.dedup_source_local)
        QuickMaintenanceSourceKind.KEEPASS -> stringResource(R.string.dedup_source_keepass)
        QuickMaintenanceSourceKind.BITWARDEN -> stringResource(R.string.dedup_source_bitwarden)
    }
}

@Composable
private fun maintenanceNoteText(note: QuickMaintenanceCategoryNote): String {
    return when (note) {
        QuickMaintenanceCategoryNote.PASSWORDS_NONE_FOUND -> stringResource(R.string.quick_database_maintenance_note_passwords_empty)
        QuickMaintenanceCategoryNote.PASSWORDS_MATCHED_ONLY -> stringResource(R.string.quick_database_maintenance_note_passwords_scope)
        QuickMaintenanceCategoryNote.AUTHENTICATORS_NONE_FOUND -> stringResource(R.string.quick_database_maintenance_note_authenticators_empty)
        QuickMaintenanceCategoryNote.AUTHENTICATORS_MATCHED_ONLY -> stringResource(R.string.quick_database_maintenance_note_authenticators_scope)
        QuickMaintenanceCategoryNote.BANK_CARDS_NONE_FOUND -> stringResource(R.string.quick_database_maintenance_note_bank_cards_empty)
        QuickMaintenanceCategoryNote.BANK_CARDS_MATCHED_ONLY -> stringResource(R.string.quick_database_maintenance_note_bank_cards_scope)
        QuickMaintenanceCategoryNote.PASSKEYS_SCAN_ONLY -> stringResource(R.string.quick_database_maintenance_note_passkeys_scan_only)
    }
}

@Composable
private fun MaintenanceSection(
    title: String,
    body: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@Composable
private fun ResultCard(result: QuickMaintenanceCategoryResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when (result.category) {
                    QuickMaintenanceCategory.PASSWORDS -> stringResource(R.string.data_type_passwords)
                    QuickMaintenanceCategory.AUTHENTICATORS -> stringResource(R.string.data_type_totp)
                    QuickMaintenanceCategory.BANK_CARDS -> stringResource(R.string.quick_database_maintenance_card_pack_label)
                    QuickMaintenanceCategory.PASSKEYS -> stringResource(R.string.nav_passkey)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(
                    R.string.quick_database_maintenance_result_line,
                    result.matchedGroups,
                    result.updatedEntries,
                    result.createdEntries,
                    result.skippedGroups
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            result.note?.let { note ->
                Text(
                    text = maintenanceNoteText(note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
