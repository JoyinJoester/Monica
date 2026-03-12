@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package takagi.ru.monica.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.dedup.DedupAction
import takagi.ru.monica.data.dedup.DedupCluster
import takagi.ru.monica.data.dedup.DedupClusterType
import takagi.ru.monica.data.dedup.DedupEntityRef
import takagi.ru.monica.data.dedup.DedupPreferredSource
import takagi.ru.monica.data.dedup.DedupScope
import takagi.ru.monica.data.dedup.DedupSourceKind
import takagi.ru.monica.viewmodel.DedupEngineUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DedupEngineScreen(
    uiState: DedupEngineUiState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onPreferredSourceChange: (DedupPreferredSource) -> Unit,
    onScopeChange: (DedupScope) -> Unit,
    onTypeChange: (DedupClusterType?) -> Unit,
    onClusterAction: (DedupCluster, DedupAction) -> Unit,
    onEnterSelectionMode: () -> Unit,
    onExitSelectionMode: () -> Unit,
    onToggleClusterSelection: (String) -> Unit,
    onSelectAllVisible: (List<String>) -> Unit,
    onClearSelected: () -> Unit,
    onBatchAction: (List<DedupCluster>, DedupAction) -> Unit,
    onConsumeMessage: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val filteredClusters = uiState.clusters.filter { cluster ->
        uiState.selectedType == null || cluster.type == uiState.selectedType
    }
    val typeCounts = uiState.clusters.groupingBy { it.type }.eachCount()
    val selectedClusters = filteredClusters.filter { it.id in uiState.selectedClusterIds }
    val commonBatchActions = selectedClusters
        .map { it.supportedActions.toSet() }
        .reduceOrNull { acc, set -> acc intersect set }
        .orEmpty()
        .toList()
    val actionableCount = filteredClusters.count {
        it.supportedActions.any { action -> action != DedupAction.IGNORE_CLUSTER }
    }
    val sourceCount = filteredClusters.flatMap { it.sources }.distinct().size
    val activeTypeCount = typeCounts.count { it.value > 0 }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onConsumeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (uiState.selectionMode) {
                                stringResource(
                                    R.string.dedup_engine_selection_title,
                                    uiState.selectedClusterIds.size
                                )
                            } else {
                                stringResource(R.string.dedup_engine_title)
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.selectionMode) {
                                stringResource(
                                    R.string.dedup_engine_batch_desc,
                                    selectedClusters.size,
                                    filteredClusters.size
                                )
                            } else {
                                stringResource(R.string.dedup_engine_desc)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.selectionMode) onExitSelectionMode() else onNavigateBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DedupMissionPanel(
                    clusterCount = filteredClusters.size,
                    actionableCount = actionableCount,
                    sourceCount = sourceCount,
                    activeTypeCount = activeTypeCount,
                    isLoading = uiState.isLoading,
                    selectedScope = uiState.selectedScope,
                    preferredSource = uiState.preferredSource,
                    selectionMode = uiState.selectionMode,
                    selectedCount = uiState.selectedClusterIds.size,
                    onRefresh = onRefresh,
                    onToggleSelectionMode = {
                        if (uiState.selectionMode) onExitSelectionMode() else onEnterSelectionMode()
                    }
                )
            }

            if (uiState.selectionMode) {
                item {
                    DedupSelectionWorkbench(
                        selectedCount = selectedClusters.size,
                        visibleCount = filteredClusters.size,
                        preferredSource = uiState.preferredSource,
                        commonBatchActions = commonBatchActions,
                        onSelectAllVisible = {
                            onSelectAllVisible(filteredClusters.map { it.id })
                        },
                        onClearSelected = onClearSelected,
                        onExitSelectionMode = onExitSelectionMode,
                        onBatchAction = { action -> onBatchAction(selectedClusters, action) }
                    )
                }
            }

            item {
                DedupStrategyPanel(
                    preferredSource = uiState.preferredSource,
                    selectedScope = uiState.selectedScope,
                    selectedType = uiState.selectedType,
                    typeCounts = typeCounts,
                    onPreferredSourceChange = onPreferredSourceChange,
                    onScopeChange = onScopeChange,
                    onTypeChange = onTypeChange
                )
            }

            if (!uiState.isLoading && filteredClusters.isNotEmpty()) {
                item {
                    DedupIssueRadar(
                        typeCounts = typeCounts,
                        selectedType = uiState.selectedType,
                        onTypeChange = onTypeChange
                    )
                }
            }

            if (uiState.error != null) {
                item { ErrorCard(message = uiState.error) }
            }

            item {
                DedupSectionHeader(
                    title = stringResource(R.string.dedup_engine_worklist_title),
                    subtitle = if (uiState.isLoading) {
                        stringResource(R.string.dedup_engine_scanning)
                    } else {
                        stringResource(
                            R.string.dedup_engine_worklist_desc,
                            filteredClusters.size
                        )
                    }
                )
            }

            if (uiState.isLoading) {
                item { LoadingCard() }
            } else if (filteredClusters.isEmpty()) {
                item { EmptyCard() }
            } else {
                items(filteredClusters, key = { it.id }) { cluster ->
                    DedupClusterCard(
                        cluster = cluster,
                        preferredSource = uiState.preferredSource,
                        selected = cluster.id in uiState.selectedClusterIds,
                        selectionMode = uiState.selectionMode,
                        onClusterAction = onClusterAction,
                        onToggleSelection = { onToggleClusterSelection(cluster.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DedupMissionPanel(
    clusterCount: Int,
    actionableCount: Int,
    sourceCount: Int,
    activeTypeCount: Int,
    isLoading: Boolean,
    selectedScope: DedupScope,
    preferredSource: DedupPreferredSource,
    selectionMode: Boolean,
    selectedCount: Int,
    onRefresh: () -> Unit,
    onToggleSelectionMode: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.padding(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dedup_engine_overview_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.dedup_engine_overview_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MissionTonePill(
                    icon = Icons.Default.Tune,
                    text = scopeLabel(selectedScope)
                )
                MissionTonePill(
                    icon = Icons.Default.CheckCircle,
                    text = preferredSourceLabel(preferredSource)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InsightStatCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.dedup_engine_cluster_count, clusterCount),
                    value = if (isLoading) "..." else clusterCount.toString(),
                    accentColor = MaterialTheme.colorScheme.primary
                )
                InsightStatCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.dedup_engine_overview_actionable),
                    value = if (selectionMode) selectedCount.toString() else actionableCount.toString(),
                    accentColor = MaterialTheme.colorScheme.tertiary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InsightStatCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.dedup_engine_overview_types),
                    value = activeTypeCount.toString(),
                    accentColor = MaterialTheme.colorScheme.secondary
                )
                InsightStatCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.dedup_engine_overview_sources),
                    value = sourceCount.toString(),
                    accentColor = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onToggleSelectionMode,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Icon(
                        imageVector = if (selectionMode) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.DoneAll
                        },
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectionMode) {
                            stringResource(R.string.dedup_engine_exit_batch)
                        } else {
                            stringResource(R.string.dedup_engine_batch_mode)
                        }
                    )
                }
                OutlinedButton(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh)
                    )
                }
            }
        }
    }
}

@Composable
private fun MissionTonePill(
    icon: ImageVector,
    text: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun InsightStatCard(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.16f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.size(10.dp)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = accentColor
                ) {}
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DedupStrategyPanel(
    preferredSource: DedupPreferredSource,
    selectedScope: DedupScope,
    selectedType: DedupClusterType?,
    typeCounts: Map<DedupClusterType, Int>,
    onPreferredSourceChange: (DedupPreferredSource) -> Unit,
    onScopeChange: (DedupScope) -> Unit,
    onTypeChange: (DedupClusterType?) -> Unit
) {
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }

    ElevatedCard(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            DedupSectionHeader(
                title = stringResource(R.string.dedup_engine_strategy_title),
                subtitle = stringResource(R.string.dedup_engine_strategy_desc)
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.dedup_engine_preferred_source_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DedupPreferredSource.entries.forEach { source ->
                        SourceChoiceCard(
                            modifier = Modifier.weight(1f),
                            selected = preferredSource == source,
                            title = preferredSourceLabel(source),
                            subtitle = if (preferredSource == source) {
                                stringResource(R.string.dedup_engine_overview_active_scope)
                            } else {
                                stringResource(R.string.dedup_engine_preferred_source_hint)
                            },
                            accentColor = sourceAccentColor(source.toSourceKind()),
                            onClick = { onPreferredSourceChange(source) }
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DedupSectionHeader(
                        title = stringResource(R.string.dedup_engine_scope_title),
                        subtitle = stringResource(R.string.dedup_engine_controls_scope_desc)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DedupScope.entries.forEach { scope ->
                            FilterOptionButton(
                                selected = selectedScope == scope,
                                label = scopeLabel(scope),
                                onClick = { onScopeChange(scope) }
                            )
                        }
                    }
                }
            }

            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = { advancedExpanded = !advancedExpanded }
            ) {
                Text(
                    if (advancedExpanded) {
                        stringResource(R.string.dedup_engine_controls_collapse)
                    } else {
                        stringResource(R.string.dedup_engine_controls_expand)
                    }
                )
            }

            if (advancedExpanded) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DedupSectionHeader(
                            title = stringResource(R.string.dedup_engine_type_title),
                            subtitle = stringResource(R.string.dedup_engine_controls_type_desc)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilterOptionButton(
                                selected = selectedType == null,
                                label = stringResource(R.string.dedup_engine_type_all),
                                trailing = typeCounts.values.sum().toString(),
                                onClick = { onTypeChange(null) }
                            )
                            DedupClusterType.entries.forEach { type ->
                                val count = typeCounts[type] ?: 0
                                if (count <= 0) return@forEach
                                FilterOptionButton(
                                    selected = selectedType == type,
                                    label = typeLabel(type),
                                    trailing = count.toString(),
                                    onClick = { onTypeChange(type) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChoiceCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            accentColor.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(),
        label = "source_choice_container"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = spring(),
        label = "source_choice_border"
    )

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        border = BorderStroke(if (selected) 1.6.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DedupSelectionWorkbench(
    selectedCount: Int,
    visibleCount: Int,
    preferredSource: DedupPreferredSource,
    commonBatchActions: List<DedupAction>,
    onSelectAllVisible: () -> Unit,
    onClearSelected: () -> Unit,
    onExitSelectionMode: () -> Unit,
    onBatchAction: (DedupAction) -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dedup_engine_batch_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(
                            R.string.dedup_engine_batch_desc,
                            selectedCount,
                            visibleCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.84f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = onSelectAllVisible,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.SelectAll, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.dedup_engine_select_all_visible))
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onClearSelected,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(stringResource(R.string.dedup_engine_clear_selection))
                }
            }

            if (commonBatchActions.isEmpty()) {
                Text(
                    text = stringResource(R.string.dedup_engine_batch_no_common_action),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    commonBatchActions.forEach { action ->
                        Button(
                            onClick = { onBatchAction(action) },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(batchActionLabel(action, preferredSource))
                        }
                    }
                }
            }

            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = onExitSelectionMode
            ) {
                Text(
                    text = stringResource(R.string.dedup_engine_exit_batch),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun DedupIssueRadar(
    typeCounts: Map<DedupClusterType, Int>,
    selectedType: DedupClusterType?,
    onTypeChange: (DedupClusterType?) -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DedupSectionHeader(
                title = stringResource(R.string.dedup_engine_radar_title),
                subtitle = stringResource(R.string.dedup_engine_radar_desc)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RadarTypeCard(
                    type = null,
                    count = typeCounts.values.sum(),
                    selected = selectedType == null,
                    onClick = { onTypeChange(null) }
                )
                DedupClusterType.entries.forEach { type ->
                    val count = typeCounts[type] ?: 0
                    if (count <= 0) return@forEach
                    RadarTypeCard(
                        type = type,
                        count = count,
                        selected = selectedType == type,
                        onClick = { onTypeChange(type) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RadarTypeCard(
    type: DedupClusterType?,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = type?.let { sourceAccentColorForType(it) } ?: MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) {
            accentColor.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            if (selected) 1.6.dp else 1.dp,
            if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.16f)
            ) {
                Icon(
                    imageVector = type?.let { typeIcon(it) } ?: Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = accentColor
                )
            }
            Column {
                Text(
                    text = type?.let { typeLabel(it) } ?: stringResource(R.string.dedup_engine_type_all),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DedupSectionHeader(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FilterOptionButton(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            if (selected) 1.4.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = FontWeight.SemiBold
            )
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.84f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun DedupClusterCard(
    cluster: DedupCluster,
    preferredSource: DedupPreferredSource,
    selected: Boolean,
    selectionMode: Boolean,
    onClusterAction: (DedupCluster, DedupAction) -> Unit,
    onToggleSelection: () -> Unit
) {
    val accentColor = sourceAccentColorForType(cluster.type)
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            accentColor.copy(alpha = 0.11f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = spring(),
        label = "dedup_cluster_container"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = spring(),
        label = "dedup_cluster_border"
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = selectionMode, onClick = onToggleSelection),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (selected) 3.dp else 1.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = Color.Transparent,
            border = BorderStroke(if (selected) 1.8.dp else 1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = accentColor.copy(alpha = 0.14f)
                    ) {
                        Icon(
                            imageVector = typeIcon(cluster.type),
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp),
                            tint = accentColor
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = typeLabel(cluster.type),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = cluster.keyLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CountBadge(
                            count = cluster.itemCount,
                            accentColor = accentColor
                        )
                        if (selectionMode) {
                            SelectionStateChip(
                                selected = selected,
                                accentColor = accentColor,
                                onClick = onToggleSelection
                            )
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cluster.sources.forEach { source ->
                        SourcePill(sourceKind = source)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = accentColor.copy(alpha = 0.10f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dedup_engine_recommendation_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = clusterRecommendationText(cluster, preferredSource),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dedup_engine_items_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        cluster.items.take(3).forEach { ref ->
                            ClusterItemPreview(ref = ref)
                        }
                        if (cluster.items.size > 3) {
                            Text(
                                text = stringResource(
                                    R.string.dedup_engine_more_items,
                                    cluster.items.size - 3
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = accentColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (!selectionMode) {
                    HorizontalDivider()
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        cluster.supportedActions
                            .filter { it != DedupAction.IGNORE_CLUSTER }
                            .forEach { action ->
                                FilledTonalButton(
                                    onClick = { onClusterAction(cluster, action) },
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Text(actionLabel(action, cluster, preferredSource))
                                }
                            }
                        if (DedupAction.IGNORE_CLUSTER in cluster.supportedActions) {
                            OutlinedButton(
                                onClick = { onClusterAction(cluster, DedupAction.IGNORE_CLUSTER) },
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text(actionLabel(DedupAction.IGNORE_CLUSTER, cluster, preferredSource))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountBadge(
    count: Int,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accentColor.copy(alpha = 0.16f)
    ) {
        Text(
            text = count.toString(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = accentColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ClusterItemPreview(ref: DedupEntityRef) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            SourcePill(sourceKind = ref.sourceKind)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ref.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (ref.subtitle.isNotBlank()) {
                    Text(
                        text = ref.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SourcePill(sourceKind: DedupSourceKind) {
    val accentColor = sourceAccentColor(sourceKind)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accentColor.copy(alpha = 0.14f)
    ) {
        Text(
            text = sourceLabel(sourceKind),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SelectionStateChip(
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            accentColor.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Text(
            text = if (selected) {
                stringResource(R.string.dedup_engine_selected_short)
            } else {
                stringResource(R.string.dedup_engine_select_short)
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LoadingCard() {
    ElevatedCard(shape = RoundedCornerShape(28.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(R.string.dedup_engine_scanning),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun EmptyCard() {
    ElevatedCard(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.dedup_engine_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.dedup_engine_empty_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun scopeLabel(scope: DedupScope): String {
    return when (scope) {
        DedupScope.ALL -> stringResource(R.string.dedup_scope_all)
        DedupScope.MONICA_LOCAL -> stringResource(R.string.dedup_scope_local)
        DedupScope.KEEPASS -> stringResource(R.string.dedup_scope_keepass)
        DedupScope.BITWARDEN -> stringResource(R.string.dedup_scope_bitwarden)
    }
}

@Composable
private fun typeLabel(type: DedupClusterType): String {
    return when (type) {
        DedupClusterType.EXACT_PASSWORD_DUPLICATE -> stringResource(R.string.dedup_type_exact_password)
        DedupClusterType.CROSS_SOURCE_PASSWORD_MIRROR -> stringResource(R.string.dedup_type_cross_source_password)
        DedupClusterType.DUPLICATE_TOTP -> stringResource(R.string.dedup_type_totp)
        DedupClusterType.DUPLICATE_BANK_CARD -> stringResource(R.string.dedup_type_bank_card)
        DedupClusterType.DUPLICATE_DOCUMENT -> stringResource(R.string.dedup_type_document)
        DedupClusterType.PASSKEY_ACCOUNT_CONFLICT -> stringResource(R.string.dedup_type_passkey)
    }
}

@Composable
private fun typeIcon(type: DedupClusterType): ImageVector = when (type) {
    DedupClusterType.EXACT_PASSWORD_DUPLICATE -> Icons.Default.ContentCopy
    DedupClusterType.CROSS_SOURCE_PASSWORD_MIRROR -> Icons.Default.Password
    DedupClusterType.DUPLICATE_TOTP -> Icons.Default.Key
    DedupClusterType.DUPLICATE_BANK_CARD -> Icons.Default.CreditCard
    DedupClusterType.DUPLICATE_DOCUMENT -> Icons.Default.Description
    DedupClusterType.PASSKEY_ACCOUNT_CONFLICT -> Icons.Default.Security
}

@Composable
private fun sourceLabel(sourceKind: DedupSourceKind): String {
    return when (sourceKind) {
        DedupSourceKind.MONICA_LOCAL -> stringResource(R.string.dedup_source_local)
        DedupSourceKind.KEEPASS -> stringResource(R.string.dedup_source_keepass)
        DedupSourceKind.BITWARDEN -> stringResource(R.string.dedup_source_bitwarden)
    }
}

@Composable
private fun preferredSourceLabel(source: DedupPreferredSource): String {
    return when (source) {
        DedupPreferredSource.MONICA_LOCAL -> stringResource(R.string.dedup_source_local)
        DedupPreferredSource.KEEPASS -> stringResource(R.string.dedup_source_keepass)
        DedupPreferredSource.BITWARDEN -> stringResource(R.string.dedup_source_bitwarden)
    }
}

@Composable
private fun batchActionLabel(
    action: DedupAction,
    preferredSource: DedupPreferredSource
): String {
    return when (action) {
        DedupAction.APPLY_PASSWORD_PREFERENCE -> stringResource(
            R.string.dedup_action_keep_source_archive_others,
            preferredSourceLabel(preferredSource)
        )
        DedupAction.MOVE_LOCAL_SECURE_ITEM_COPIES_TO_TRASH -> stringResource(
            R.string.dedup_action_keep_one_local_item
        )
        DedupAction.IGNORE_CLUSTER -> stringResource(R.string.dedup_action_ignore)
    }
}

@Composable
private fun actionLabel(
    action: DedupAction,
    cluster: DedupCluster,
    preferredSource: DedupPreferredSource
): String {
    return when (action) {
        DedupAction.APPLY_PASSWORD_PREFERENCE -> {
            if (cluster.type == DedupClusterType.EXACT_PASSWORD_DUPLICATE) {
                stringResource(
                    R.string.dedup_action_keep_one_source_password,
                    sourceLabel(cluster.sources.firstOrNull() ?: DedupSourceKind.MONICA_LOCAL)
                )
            } else {
                stringResource(
                    R.string.dedup_action_keep_source_archive_others,
                    preferredPasswordSourceLabel(cluster, preferredSource)
                )
            }
        }
        DedupAction.MOVE_LOCAL_SECURE_ITEM_COPIES_TO_TRASH -> stringResource(
            R.string.dedup_action_keep_one_local_item
        )
        DedupAction.IGNORE_CLUSTER -> stringResource(R.string.dedup_action_ignore)
    }
}

@Composable
private fun clusterSupportingText(cluster: DedupCluster): String {
    val localLabel = stringResource(R.string.dedup_source_local)
    val keepassLabel = stringResource(R.string.dedup_source_keepass)
    val bitwardenLabel = stringResource(R.string.dedup_source_bitwarden)
    val sourceSummary = cluster.sources.joinToString(" / ") { source ->
        when (source) {
            DedupSourceKind.MONICA_LOCAL -> localLabel
            DedupSourceKind.KEEPASS -> keepassLabel
            DedupSourceKind.BITWARDEN -> bitwardenLabel
        }
    }

    return when (cluster.type) {
        DedupClusterType.EXACT_PASSWORD_DUPLICATE -> stringResource(R.string.dedup_cluster_exact_password_desc, sourceSummary)
        DedupClusterType.CROSS_SOURCE_PASSWORD_MIRROR -> stringResource(R.string.dedup_cluster_cross_source_password_desc, sourceSummary)
        DedupClusterType.DUPLICATE_TOTP -> stringResource(R.string.dedup_cluster_totp_desc, sourceSummary)
        DedupClusterType.DUPLICATE_BANK_CARD -> stringResource(R.string.dedup_cluster_bank_card_desc, sourceSummary)
        DedupClusterType.DUPLICATE_DOCUMENT -> stringResource(R.string.dedup_cluster_document_desc, sourceSummary)
        DedupClusterType.PASSKEY_ACCOUNT_CONFLICT -> stringResource(R.string.dedup_cluster_passkey_desc, sourceSummary)
    }
}

@Composable
private fun clusterRecommendationText(
    cluster: DedupCluster,
    preferredSource: DedupPreferredSource
): String {
    return when (cluster.type) {
        DedupClusterType.EXACT_PASSWORD_DUPLICATE -> stringResource(
            R.string.dedup_cluster_exact_password_recommendation,
            sourceLabel(cluster.sources.firstOrNull() ?: DedupSourceKind.MONICA_LOCAL)
        )
        DedupClusterType.CROSS_SOURCE_PASSWORD_MIRROR -> {
            val preferredLabel = preferredPasswordSourceLabel(cluster, preferredSource)
            if (cluster.sources.any { it == preferredSource.toSourceKind() }) {
                stringResource(
                    R.string.dedup_cluster_cross_source_password_recommendation,
                    preferredLabel
                )
            } else {
                stringResource(
                    R.string.dedup_cluster_cross_source_password_fallback,
                    preferredSourceLabel(preferredSource),
                    preferredLabel
                )
            }
        }
        else -> clusterSupportingText(cluster)
    }
}

@Composable
private fun preferredPasswordSourceLabel(
    cluster: DedupCluster,
    preferredSource: DedupPreferredSource
): String {
    val preferredKind = preferredSource.toSourceKind()
    return if (cluster.sources.any { it == preferredKind }) {
        sourceLabel(preferredKind)
    } else {
        sourceLabel(cluster.sources.firstOrNull() ?: preferredKind)
    }
}

@Composable
private fun sourceAccentColor(sourceKind: DedupSourceKind): Color {
    return when (sourceKind) {
        DedupSourceKind.MONICA_LOCAL -> MaterialTheme.colorScheme.primary
        DedupSourceKind.KEEPASS -> MaterialTheme.colorScheme.tertiary
        DedupSourceKind.BITWARDEN -> MaterialTheme.colorScheme.secondary
    }
}

@Composable
private fun sourceAccentColorForType(type: DedupClusterType): Color {
    return when (type) {
        DedupClusterType.EXACT_PASSWORD_DUPLICATE -> MaterialTheme.colorScheme.secondary
        DedupClusterType.CROSS_SOURCE_PASSWORD_MIRROR -> MaterialTheme.colorScheme.primary
        DedupClusterType.DUPLICATE_TOTP -> MaterialTheme.colorScheme.tertiary
        DedupClusterType.DUPLICATE_BANK_CARD -> MaterialTheme.colorScheme.secondary
        DedupClusterType.DUPLICATE_DOCUMENT -> MaterialTheme.colorScheme.tertiary
        DedupClusterType.PASSKEY_ACCOUNT_CONFLICT -> MaterialTheme.colorScheme.primary
    }
}

private fun DedupPreferredSource.toSourceKind(): DedupSourceKind {
    return when (this) {
        DedupPreferredSource.MONICA_LOCAL -> DedupSourceKind.MONICA_LOCAL
        DedupPreferredSource.KEEPASS -> DedupSourceKind.KEEPASS
        DedupPreferredSource.BITWARDEN -> DedupSourceKind.BITWARDEN
    }
}
