package takagi.ru.monica.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.data.MdbxSourceType
import takagi.ru.monica.data.MdbxTigaMode
import takagi.ru.monica.repository.MdbxConflictResolution
import takagi.ru.monica.repository.MdbxConflictSummary
import takagi.ru.monica.repository.MdbxCommitDiff
import takagi.ru.monica.repository.MdbxDeltaSummary
import takagi.ru.monica.repository.MdbxSnapshotSummary
import takagi.ru.monica.repository.MdbxVaultDiagnostics
import takagi.ru.monica.utils.ClipboardUtils
import takagi.ru.monica.viewmodel.MdbxViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MdbxManagerScreen(
    viewModel: MdbxViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCreateVault: () -> Unit
) {
    val databases by viewModel.allDatabases.collectAsState()
    val operationState by viewModel.operationState.collectAsState()
    val conflictCounts by viewModel.conflictCounts.collectAsState()
    val vaultDiagnostics by viewModel.vaultDiagnostics.collectAsState()
    val conflictDialogState by viewModel.conflictDialogState.collectAsState()
    val deltaDialogState by viewModel.deltaDialogState.collectAsState()
    val advancedDialogState by viewModel.advancedDialogState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<LocalMdbxDatabase?>(null) }
    var selectedDatabase by remember { mutableStateOf<LocalMdbxDatabase?>(null) }
    val internalDatabases = remember(databases) {
        databases.filter { it.sourceTypeEnum == MdbxSourceType.LOCAL_INTERNAL }
    }
    val externalDatabases = remember(databases) {
        databases.filter { it.sourceTypeEnum == MdbxSourceType.LOCAL_EXTERNAL }
    }
    val remoteDatabases = remember(databases) {
        databases.filter { it.sourceTypeEnum == MdbxSourceType.REMOTE_WEBDAV }
    }

    LaunchedEffect(Unit) {
        viewModel.clearOperationState()
        viewModel.pruneMissingLocalVaults()
    }
    LaunchedEffect(databases) {
        viewModel.refreshConflictCounts(databases)
        if (selectedDatabase != null && databases.none { it.id == selectedDatabase?.id }) {
            selectedDatabase = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mdbx_format_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToCreateVault) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCreateVault,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.mdbx_create_new_vault_button)) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (databases.isEmpty()) {
                EmptyMdbxState(onCreateClick = onNavigateToCreateVault)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        MdbxOperationsDashboard(
                            databases = databases,
                            diagnostics = vaultDiagnostics
                        )
                    }
                    if (internalDatabases.isNotEmpty()) {
                        item {
                            MdbxSectionHeader(
                                icon = Icons.Default.Security,
                                title = "Monica 私有目录",
                                subtitle = "应用内部保存的 MDBX vault",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(items = internalDatabases, key = { it.id }) { db ->
                            MdbxVaultSmallCard(
                                database = db,
                                isDefault = db.isDefault,
                                conflictCount = conflictCounts[db.id] ?: 0,
                                diagnostics = vaultDiagnostics[db.id],
                                onOpen = { selectedDatabase = db }
                            )
                        }
                    }
                    if (externalDatabases.isNotEmpty()) {
                        item {
                            MdbxSectionHeader(
                                icon = Icons.Default.Folder,
                                title = "本地文件",
                                subtitle = "通过系统文件选择器连接的 MDBX vault",
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        items(items = externalDatabases, key = { it.id }) { db ->
                            MdbxVaultSmallCard(
                                database = db,
                                isDefault = db.isDefault,
                                conflictCount = conflictCounts[db.id] ?: 0,
                                diagnostics = vaultDiagnostics[db.id],
                                onOpen = { selectedDatabase = db }
                            )
                        }
                    }
                    if (remoteDatabases.isNotEmpty()) {
                        item {
                            MdbxSectionHeader(
                                icon = Icons.Default.CloudSync,
                                title = "WebDAV",
                                subtitle = "远程 MDBX vault 的本地工作副本",
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        items(items = remoteDatabases, key = { it.id }) { db ->
                            MdbxVaultSmallCard(
                                database = db,
                                isDefault = db.isDefault,
                                conflictCount = conflictCounts[db.id] ?: 0,
                                diagnostics = vaultDiagnostics[db.id],
                                onOpen = { selectedDatabase = db }
                            )
                        }
                    }
                    item {
                        MdbxQuickActionsCard(onCreateClick = onNavigateToCreateVault)
                    }
                }
            }

            when (val state = operationState) {
                is MdbxViewModel.OperationState.Success -> {
                    MdbxOperationStatusBar(
                        text = state.message,
                        icon = Icons.Default.CheckCircle,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 16.dp, end = 16.dp, bottom = 96.dp)
                    )
                }
                is MdbxViewModel.OperationState.Error -> {
                    MdbxOperationStatusBar(
                        text = state.message,
                        icon = Icons.Default.Warning,
                        isError = true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 16.dp, end = 16.dp, bottom = 96.dp)
                    )
                }
                else -> Unit
            }
        }
    }

    selectedDatabase?.let { db ->
        MdbxVaultDetailBottomSheet(
            database = db,
            isDefault = db.isDefault,
            conflictCount = conflictCounts[db.id] ?: 0,
            diagnostics = vaultDiagnostics[db.id],
            onDismiss = { selectedDatabase = null },
            onSync = {
                selectedDatabase = null
                viewModel.syncVault(db.id)
            },
            onShowConflicts = {
                selectedDatabase = null
                viewModel.showConflicts(db)
            },
            onShowDeltas = {
                selectedDatabase = null
                viewModel.showDeltaHistory(db)
            },
            onShowAdvanced = {
                selectedDatabase = null
                viewModel.showAdvancedTools(db)
            },
            onSetDefault = {
                selectedDatabase = null
                viewModel.setAsDefault(db.id)
            },
            onDelete = {
                selectedDatabase = null
                showDeleteDialog = db
            }
        )
    }

    showDeleteDialog?.let { db ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.mdbx_delete_vault_title)) },
            text = {
                Text(stringResource(R.string.mdbx_delete_vault_message, db.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedDatabase?.id == db.id) selectedDatabase = null
                        viewModel.deleteVault(db.id)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.mdbx_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.mdbx_cancel))
                }
            }
        )
    }

    when (val state = deltaDialogState) {
        MdbxViewModel.MdbxDeltaDialogState.Hidden -> Unit
        is MdbxViewModel.MdbxDeltaDialogState.Visible -> {
            MdbxDeltaDialog(
                state = state,
                onDismiss = viewModel::dismissDeltaDialog,
                onShowDiff = { commitId -> viewModel.showCommitDiff(state.databaseId, commitId) },
                onRevert = { commitId -> viewModel.revertCommit(state.databaseId, commitId) },
                onCreateSnapshot = { name, fullSnapshot ->
                    viewModel.createSnapshot(state.databaseId, name, fullSnapshot)
                },
                onDeleteSnapshot = { snapshotId ->
                    viewModel.deleteSnapshot(state.databaseId, snapshotId)
                },
                onRevertSnapshot = { snapshotId ->
                    viewModel.revertToSnapshot(state.databaseId, snapshotId)
                },
                onPruneAutomaticSnapshots = {
                    viewModel.pruneAutomaticSnapshots(state.databaseId)
                }
            )
        }
    }

    when (val state = advancedDialogState) {
        MdbxViewModel.MdbxAdvancedDialogState.Hidden -> Unit
        is MdbxViewModel.MdbxAdvancedDialogState.Visible -> {
            MdbxAdvancedToolsDialog(
                state = state,
                onDismiss = viewModel::dismissAdvancedTools,
                onExportBundle = { baseCommitId ->
                    viewModel.exportSyncBundle(state.databaseId, baseCommitId)
                },
                onImportBundle = { payload ->
                    viewModel.importSyncBundleFromJson(state.databaseId, payload)
                },
                onFlushPendingUpload = {
                    viewModel.flushPendingVaultUpload(state.databaseId)
                },
                onRunBenchmark = { operationCount ->
                    viewModel.runBenchmark(state.databaseId, operationCount)
                }
            )
        }
    }

    when (val state = conflictDialogState) {
        MdbxViewModel.MdbxConflictDialogState.Hidden -> Unit
        is MdbxViewModel.MdbxConflictDialogState.Visible -> {
            MdbxConflictDialog(
                state = state,
                onDismiss = viewModel::dismissConflictDialog,
                onResolve = { conflictId, resolution ->
                    viewModel.resolveConflict(state.databaseId, conflictId, resolution)
                }
            )
        }
    }
}

@Composable
private fun EmptyMdbxState(
    onCreateClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.mdbx_no_vaults),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.mdbx_create_first_vault),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onCreateClick,
            modifier = Modifier.fillMaxWidth(0.82f).heightIn(min = 48.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.mdbx_create_new_vault_button))
        }
    }
}

@Composable
private fun MdbxSectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = color.copy(alpha = 0.12f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = color
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MdbxQuickActionsCard(
    onCreateClick: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCreateClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.mdbx_create_new_vault_button),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "创建本地 MDBX、打开本地文件，或连接 WebDAV vault",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MdbxOperationStatusBar(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun sourceColor(database: LocalMdbxDatabase): Color =
    when (database.sourceTypeEnum) {
        MdbxSourceType.LOCAL_INTERNAL -> MaterialTheme.colorScheme.primary
        MdbxSourceType.LOCAL_EXTERNAL -> MaterialTheme.colorScheme.secondary
        MdbxSourceType.REMOTE_WEBDAV -> MaterialTheme.colorScheme.tertiary
    }

private fun sourceIcon(database: LocalMdbxDatabase): ImageVector =
    when (database.sourceTypeEnum) {
        MdbxSourceType.LOCAL_INTERNAL -> Icons.Default.Security
        MdbxSourceType.LOCAL_EXTERNAL -> Icons.Default.Folder
        MdbxSourceType.REMOTE_WEBDAV -> Icons.Default.CloudSync
    }

private fun mdbxSourceLabel(database: LocalMdbxDatabase): String =
    when (database.sourceTypeEnum) {
        MdbxSourceType.LOCAL_INTERNAL -> "Monica 私有目录"
        MdbxSourceType.LOCAL_EXTERNAL -> "本地文件"
        MdbxSourceType.REMOTE_WEBDAV -> "WebDAV"
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MdbxVaultSmallCard(
    database: LocalMdbxDatabase,
    isDefault: Boolean,
    conflictCount: Int,
    diagnostics: MdbxVaultDiagnostics?,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val healthIssueCount = diagnostics?.healthIssueCount ?: 0
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = sourceColor(database).copy(alpha = 0.12f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            sourceIcon(database),
                            contentDescription = null,
                            tint = sourceColor(database),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            database.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isDefault) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Star,
                                contentDescription = stringResource(R.string.mdbx_default_badge),
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        "${mdbxSourceLabel(database)} · ${diagnostics?.lastSyncStatus ?: database.lastSyncStatus}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        database.displayPath(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = onOpen,
                    label = {
                        Text(if (conflictCount > 0) "冲突 $conflictCount" else "冲突干净")
                    },
                    leadingIcon = {
                        Icon(
                            if (conflictCount > 0) Icons.AutoMirrored.Filled.CallMerge else Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                AssistChip(
                    onClick = onOpen,
                    label = {
                        Text(if (healthIssueCount > 0) "健康 $healthIssueCount" else "健康正常")
                    },
                    leadingIcon = {
                        Icon(
                            if (healthIssueCount > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MdbxVaultDetailBottomSheet(
    database: LocalMdbxDatabase,
    isDefault: Boolean,
    conflictCount: Int,
    diagnostics: MdbxVaultDiagnostics?,
    onDismiss: () -> Unit,
    onSync: () -> Unit,
    onShowConflicts: () -> Unit,
    onShowDeltas: () -> Unit,
    onShowAdvanced: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val tigaLabel = try {
        MdbxTigaMode.valueOf(database.tigaMode).label
    } catch (_: IllegalArgumentException) {
        database.tigaMode
    }

    val healthIssueCount = diagnostics?.healthIssueCount ?: 0
    val hasUnavailableCopy = diagnostics?.isReadable == false

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = sourceColor(database).copy(alpha = 0.12f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            sourceIcon(database),
                            contentDescription = null,
                            tint = sourceColor(database),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            database.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isDefault) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Star,
                                contentDescription = stringResource(R.string.mdbx_default_badge),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tiga: $tigaLabel · ${mdbxSourceLabel(database)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (database.filePath.isNotBlank()) {
                        Text(
                            database.displayPath(context),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusTile(
                    modifier = Modifier.weight(1f),
                    icon = if (conflictCount > 0) Icons.AutoMirrored.Filled.CallMerge else Icons.Default.CheckCircle,
                    label = stringResource(R.string.mdbx_status_conflicts),
                    value = if (conflictCount > 0) {
                        stringResource(R.string.mdbx_conflict_count_short, conflictCount)
                    } else {
                        stringResource(R.string.mdbx_no_conflicts_short)
                    },
                    isWarning = conflictCount > 0
                )
                StatusTile(
                    modifier = Modifier.weight(1f),
                    icon = if (healthIssueCount > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                    label = stringResource(R.string.mdbx_status_health),
                    value = if (healthIssueCount > 0) {
                        stringResource(R.string.mdbx_health_issues_short, healthIssueCount)
                    } else {
                        stringResource(R.string.mdbx_health_ok_short)
                    },
                    isWarning = healthIssueCount > 0
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.History,
                    label = stringResource(R.string.mdbx_status_delta),
                    value = diagnostics?.let {
                        stringResource(R.string.mdbx_commit_tombstone_short, it.commitCount, it.tombstoneCount)
                    } ?: stringResource(R.string.mdbx_status_loading),
                    isWarning = false
                )
                StatusTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Storage,
                    label = stringResource(R.string.mdbx_status_attachments),
                    value = diagnostics?.let {
                        stringResource(
                            R.string.mdbx_attachment_short,
                            it.attachmentCount,
                            it.externalAttachmentCount,
                            formatBytes(it.storedAttachmentBytes)
                        )
                    } ?: stringResource(R.string.mdbx_status_loading),
                    isWarning = false
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            diagnostics?.let { diagnostic ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                    DiagnosticLine(
                        icon = if (diagnostic.isReadable) Icons.Default.CloudSync else Icons.Default.CloudOff,
                        label = stringResource(R.string.mdbx_sync_status_label),
                        value = diagnostic.lastSyncStatus
                    )
                    DiagnosticLine(
                        icon = Icons.Default.Security,
                        label = stringResource(R.string.mdbx_compatibility_label),
                        value = stringResource(
                            R.string.mdbx_format_tiga_value,
                            diagnostic.formatVersion ?: "MDBX-?",
                            diagnostic.defaultTigaMode ?: database.tigaMode
                        )
                    )
                    DiagnosticLine(
                        icon = Icons.Default.Sync,
                        label = stringResource(R.string.mdbx_recovery_label),
                        value = if (diagnostic.structuralIssueCount == 0 && diagnostic.integrityOk) {
                            stringResource(R.string.mdbx_recovery_clean)
                        } else {
                            stringResource(
                                R.string.mdbx_recovery_issue_value,
                                diagnostic.structuralIssueCount,
                                diagnostic.integrityMessage ?: "-"
                            )
                        }
                    )
                    DiagnosticLine(
                        icon = Icons.Default.Info,
                        label = stringResource(R.string.mdbx_file_size_label),
                        value = formatBytes(diagnostic.fileSizeBytes)
                    )
                    DiagnosticLine(
                        icon = Icons.Default.Storage,
                        label = "客户端",
                        value = diagnostic.currentDeviceId ?: "-"
                    )
                    DiagnosticLine(
                        icon = Icons.Default.Folder,
                        label = "目录/索引",
                        value = "${diagnostic.folderCount} folders · ${diagnostic.indexedObjectCount} indexed"
                    )
                    }
                }
                if (hasUnavailableCopy) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        diagnostic.unavailableReason
                            ?: stringResource(R.string.mdbx_unavailable_local_copy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.actions),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isDefault) {
                    OutlinedButton(
                        onClick = onSetDefault,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.mdbx_set_default))
                    }
                }
                OutlinedButton(
                    onClick = onSync,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("同步")
                }
                OutlinedButton(
                    onClick = onShowConflicts,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (conflictCount > 0) "冲突管理($conflictCount)" else "冲突管理")
                }
                OutlinedButton(
                    onClick = onShowDeltas,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("历史 / 快照")
                }
                OutlinedButton(
                    onClick = onShowAdvanced,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Science, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("高级工具")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.mdbx_delete))
                }
            }
        }
    }
}

@Composable
private fun MdbxOperationsDashboard(
    databases: List<LocalMdbxDatabase>,
    diagnostics: Map<Long, MdbxVaultDiagnostics>
) {
    val totalConflicts = diagnostics.values.sumOf { it.unresolvedConflictCount }
    val totalHealthIssues = diagnostics.values.sumOf { it.healthIssueCount }
    val totalCommits = diagnostics.values.sumOf { it.commitCount }
    val externalAttachments = diagnostics.values.sumOf { it.externalAttachmentCount }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Science,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.mdbx_operations_dashboard_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.CallMerge,
                    label = stringResource(R.string.mdbx_status_conflicts),
                    value = totalConflicts.toString(),
                    isWarning = totalConflicts > 0
                )
                StatusTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Warning,
                    label = stringResource(R.string.mdbx_status_health),
                    value = totalHealthIssues.toString(),
                    isWarning = totalHealthIssues > 0
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.History,
                    label = stringResource(R.string.mdbx_status_delta),
                    value = totalCommits.toString(),
                    isWarning = false
                )
                StatusTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Storage,
                    label = stringResource(R.string.mdbx_status_attachments),
                    value = stringResource(
                        R.string.mdbx_dashboard_attachment_value,
                        externalAttachments
                    ),
                    isWarning = false
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.mdbx_dashboard_vault_count, databases.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusTile(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    isWarning: Boolean
) {
    val color = when {
        isWarning -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = modifier.heightIn(min = 76.dp),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DiagnosticLine(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MdbxAdvancedToolsDialog(
    state: MdbxViewModel.MdbxAdvancedDialogState.Visible,
    onDismiss: () -> Unit,
    onExportBundle: (String?) -> Unit,
    onImportBundle: (String) -> Unit,
    onFlushPendingUpload: () -> Unit,
    onRunBenchmark: (Int) -> Unit
) {
    val context = LocalContext.current
    var baseCommitId by rememberSaveable(state.databaseId) { mutableStateOf("") }
    var importJson by rememberSaveable(state.databaseId) { mutableStateOf("") }
    var benchmarkCountText by rememberSaveable(state.databaseId) { mutableStateOf("10") }
    val benchmarkCount = benchmarkCountText.toIntOrNull()?.coerceIn(1, 500) ?: 10
    val diagnostics = state.diagnostics

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("高级工具 · ${state.databaseName}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                state.message?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                AdvancedToolSection(title = "Oplog / Sync bundle") {
                    OutlinedTextField(
                        value = baseCommitId,
                        onValueChange = { baseCommitId = it },
                        label = { Text("Base commit ID，可留空") },
                        singleLine = true,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onExportBundle(baseCommitId.trim().takeIf { it.isNotBlank() }) },
                            enabled = !state.isLoading,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("导出")
                        }
                        OutlinedButton(
                            onClick = {
                                state.exportedBundleJson?.let {
                                    ClipboardUtils.copyToClipboard(context, it, "MDBX sync bundle")
                                }
                            },
                            enabled = !state.exportedBundleJson.isNullOrBlank(),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("复制")
                        }
                    }
                    state.lastExportedBundle?.let { bundle ->
                        Text(
                            "head ${shortId(bundle.headCommitId)} · ${bundle.commitCount} commits · ${bundle.payloadHash.take(12)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = importJson,
                        onValueChange = { importJson = it },
                        label = { Text("粘贴 bundle JSON 导入") },
                        minLines = 3,
                        maxLines = 6,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onImportBundle(importJson) },
                        enabled = !state.isLoading && importJson.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("导入 bundle")
                    }
                    state.lastImportResult?.let { result ->
                        Text(
                            "导入结果: ${result.appliedObjectCount} applied · ${result.keptLocalObjectCount} kept · ${result.conflictCount} conflicts · ${result.tombstoneCount} tombstones",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AdvancedToolSection(title = "后台合并上传") {
                    DiagnosticLine(
                        icon = Icons.Default.Sync,
                        label = "同步状态",
                        value = diagnostics?.lastSyncStatus ?: "-"
                    )
                    Button(
                        onClick = onFlushPendingUpload,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("立即上传待处理写入")
                    }
                }

                AdvancedToolSection(title = "附件 chunk / external-hash-ref") {
                    DiagnosticLine(
                        icon = Icons.Default.Storage,
                        label = "附件",
                        value = diagnostics?.let {
                            "${it.attachmentCount} total · ${it.externalAttachmentCount} external"
                        } ?: "-"
                    )
                    DiagnosticLine(
                        icon = Icons.Default.Folder,
                        label = "存储",
                        value = diagnostics?.let {
                            "${formatBytes(it.originalAttachmentBytes)} original · ${formatBytes(it.storedAttachmentBytes)} stored"
                        } ?: "-"
                    )
                    DiagnosticLine(
                        icon = if ((diagnostics?.attachmentChunkMismatchCount ?: 0) > 0) {
                            Icons.Default.Warning
                        } else {
                            Icons.Default.CheckCircle
                        },
                        label = "Chunk 校验",
                        value = diagnostics?.let { "${it.attachmentChunkMismatchCount} mismatch" } ?: "-"
                    )
                }

                AdvancedToolSection(title = "性能 benchmark") {
                    OutlinedTextField(
                        value = benchmarkCountText,
                        onValueChange = { value ->
                            benchmarkCountText = value.filter { it.isDigit() }.take(3)
                        },
                        label = { Text("Commit 数量") },
                        singleLine = true,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onRunBenchmark(benchmarkCount) },
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("运行 benchmark")
                    }
                    state.lastBenchmarkResult?.let { result ->
                        Text(
                            "${result.operationCount} commits · ${result.elapsedMs} ms · ${formatBytes(result.fileDeltaBytes)} file delta",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.mdbx_close))
            }
        }
    )
}

@Composable
private fun AdvancedToolSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun MdbxConflictDialog(
    state: MdbxViewModel.MdbxConflictDialogState.Visible,
    onDismiss: () -> Unit,
    onResolve: (String, MdbxConflictResolution) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.mdbx_conflict_queue_title, state.databaseName))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (state.conflicts.isEmpty() && !state.isLoading) {
                    Text(
                        stringResource(R.string.mdbx_conflict_queue_empty),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                state.conflicts.forEach { conflict ->
                    ConflictRow(
                        conflict = conflict,
                        enabled = !state.isLoading,
                        onResolve = onResolve
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.mdbx_close))
            }
        }
    )
}

@Composable
private fun ConflictRow(
    conflict: MdbxConflictSummary,
    enabled: Boolean,
    onResolve: (String, MdbxConflictResolution) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.CallMerge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${conflict.objectType}: ${conflict.objectId}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(
                            R.string.mdbx_conflict_fields_value,
                            conflict.conflictingFields
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                stringResource(
                    R.string.mdbx_conflict_commits_value,
                    shortId(conflict.localCommitId),
                    shortId(conflict.incomingCommitId),
                    shortId(conflict.baseCommitId)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ConflictVersionPreview(
                label = "本地版本",
                title = conflict.localTitle ?: conflict.objectId,
                preview = conflict.localPayloadPreview ?: "无 payload 快照"
            )
            ConflictVersionPreview(
                label = "传入版本",
                title = conflict.incomingTitle ?: conflict.objectId,
                preview = conflict.incomingPayloadPreview ?: "无 payload 快照"
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            onResolve(conflict.conflictId, MdbxConflictResolution.LOCAL_WINS)
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.mdbx_conflict_local_wins))
                    }
                    OutlinedButton(
                        onClick = {
                            onResolve(conflict.conflictId, MdbxConflictResolution.INCOMING_WINS)
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.mdbx_conflict_incoming_wins))
                    }
                }
                Text(
                    stringResource(R.string.mdbx_conflict_custom_merge_pending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        onResolve(conflict.conflictId, MdbxConflictResolution.MARK_RESOLVED)
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.mdbx_conflict_mark_resolved))
                }
            }
        }
    }
}

@Composable
private fun ConflictVersionPreview(
    label: String,
    title: String,
    preview: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MdbxDeltaDialog(
    state: MdbxViewModel.MdbxDeltaDialogState.Visible,
    onDismiss: () -> Unit,
    onShowDiff: (String) -> Unit,
    onRevert: (String) -> Unit,
    onCreateSnapshot: (String, Boolean) -> Unit,
    onDeleteSnapshot: (String) -> Unit,
    onRevertSnapshot: (String) -> Unit,
    onPruneAutomaticSnapshots: () -> Unit
) {
    var snapshotName by rememberSaveable(state.databaseId) { mutableStateOf("") }
    var fullSnapshot by rememberSaveable(state.databaseId) { mutableStateOf(false) }
    val manualSnapshots = state.snapshots.filterNot { it.autoPrune }
    val automaticSnapshots = state.snapshots.filter { it.autoPrune }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("增量管理 · ${state.databaseName}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (state.isDiffLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (state.isSnapshotLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (state.deltas.isNotEmpty()) {
                    DeltaSummaryHeader(state.deltas)
                }
                SnapshotManagerPanel(
                    snapshotName = snapshotName,
                    onSnapshotNameChange = { snapshotName = it },
                    fullSnapshot = fullSnapshot,
                    onFullSnapshotChange = { fullSnapshot = it },
                    snapshots = state.snapshots,
                    manualSnapshotCount = manualSnapshots.size,
                    automaticSnapshotCount = automaticSnapshots.size,
                    enabled = !state.isLoading && !state.isSnapshotLoading,
                    onCreateSnapshot = {
                        onCreateSnapshot(snapshotName, fullSnapshot)
                        snapshotName = ""
                    },
                    onPruneAutomaticSnapshots = onPruneAutomaticSnapshots,
                    onDeleteSnapshot = onDeleteSnapshot,
                    onRevertSnapshot = onRevertSnapshot
                )
                if (state.selectedDiffCommitId != null) {
                    CommitDiffPanel(
                        commitId = state.selectedDiffCommitId,
                        diffItems = state.diffItems,
                        isLoading = state.isDiffLoading
                    )
                }
                if (state.deltas.isEmpty() && !state.isLoading) {
                    Text("还没有增量提交记录", style = MaterialTheme.typography.bodyMedium)
                }
                state.deltas.forEach { delta ->
                    DeltaRow(
                        delta = delta,
                        onShowDiff = { onShowDiff(delta.commitId) },
                        onRevert = { onRevert(delta.commitId) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.mdbx_close))
            }
        }
    )
}

@Composable
private fun SnapshotManagerPanel(
    snapshotName: String,
    onSnapshotNameChange: (String) -> Unit,
    fullSnapshot: Boolean,
    onFullSnapshotChange: (Boolean) -> Unit,
    snapshots: List<MdbxSnapshotSummary>,
    manualSnapshotCount: Int,
    automaticSnapshotCount: Int,
    enabled: Boolean,
    onCreateSnapshot: () -> Unit,
    onPruneAutomaticSnapshots: () -> Unit,
    onDeleteSnapshot: (String) -> Unit,
    onRevertSnapshot: (String) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Restore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "快照",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "手动 $manualSnapshotCount · 自动 $automaticSnapshotCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            OutlinedTextField(
                value = snapshotName,
                onValueChange = onSnapshotNameChange,
                enabled = enabled,
                singleLine = true,
                label = { Text("快照名称") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(
                    checked = fullSnapshot,
                    onCheckedChange = onFullSnapshotChange,
                    enabled = enabled
                )
                Text(
                    if (fullSnapshot) "完整快照" else "Delta 快照",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCreateSnapshot,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("创建")
                }
                OutlinedButton(
                    onClick = onPruneAutomaticSnapshots,
                    enabled = enabled && automaticSnapshotCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("清理自动")
                }
            }
            if (snapshots.isEmpty()) {
                Text(
                    "还没有快照",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            snapshots.take(30).forEach { snapshot ->
                SnapshotRow(
                    snapshot = snapshot,
                    enabled = enabled,
                    onDelete = { onDeleteSnapshot(snapshot.snapshotId) },
                    onRevert = { onRevertSnapshot(snapshot.snapshotId) }
                )
            }
        }
    }
}

@Composable
private fun SnapshotRow(
    snapshot: MdbxSnapshotSummary,
    enabled: Boolean,
    onDelete: () -> Unit,
    onRevert: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (snapshot.autoPrune) Icons.Default.History else Icons.Default.Restore,
                    contentDescription = null,
                    tint = if (snapshot.integrityOk) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        snapshot.name.ifBlank { shortId(snapshot.snapshotId) },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${shortId(snapshot.baseCommitId)} · ${snapshot.createdAt}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                "${if (snapshot.autoPrune) "自动" else "手动"} · ${if (snapshot.isFull) "完整" else "Delta"} · ${formatBytes(snapshot.payloadBytes)} · ${if (snapshot.integrityOk) "校验正常" else "校验失败"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onRevert,
                    enabled = enabled && snapshot.integrityOk
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("回滚")
                }
                TextButton(
                    onClick = onDelete,
                    enabled = enabled
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun CommitDiffPanel(
    commitId: String,
    diffItems: List<MdbxCommitDiff>,
    isLoading: Boolean
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Diff · ${shortId(commitId)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (diffItems.isEmpty() && !isLoading) {
                Text(
                    "此提交没有可显示的对象版本快照",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            diffItems.forEach { item ->
                CommitDiffRow(item)
            }
        }
    }
}

@Composable
private fun CommitDiffRow(item: MdbxCommitDiff) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "${item.objectType}:${shortId(item.objectId)} · ${item.changedFields.joinToString()}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "标题: ${item.previousTitle ?: "-"} -> ${item.currentTitle ?: "-"}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "内容: ${item.previousPayloadPreview ?: "-"} -> ${item.currentPayloadPreview ?: "-"}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (item.previousDeleted != item.currentDeleted) {
            Text(
                "删除状态: ${item.previousDeleted ?: false} -> ${item.currentDeleted}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun DeltaSummaryHeader(deltas: List<MdbxDeltaSummary>) {
    val deviceCount = deltas.map { it.deviceId }.distinct().size
    val changedObjectCount = deltas.sumOf { changedObjectCount(it.changedObjectIds) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.History,
                label = "提交",
                value = deltas.size.toString(),
                isWarning = false
            )
            StatusTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Storage,
                label = "客户端",
                value = deviceCount.toString(),
                isWarning = false
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Sync,
                label = "对象变更",
                value = changedObjectCount.toString(),
                isWarning = false
            )
            StatusTile(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.CallMerge,
                label = "分叉提交",
                value = deltas.count { it.parentCount > 1 }.toString(),
                isWarning = deltas.any { it.parentCount > 1 }
            )
        }
    }
}

@Composable
private fun DeltaRow(
    delta: MdbxDeltaSummary,
    onShowDiff: () -> Unit,
    onRevert: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "#${delta.localSeq} ${delta.commitKind} · ${delta.changeScope}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${shortId(delta.commitId)} · ${delta.createdAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                "客户端 ${delta.deviceId} · parents ${delta.parentCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                delta.changedObjectIds,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onShowDiff) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Diff")
                }
                TextButton(onClick = onRevert) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Revert")
                }
            }
        }
    }
}

private fun shortId(value: String): String =
    value.take(8).ifBlank { "-" }

private fun changedObjectCount(changedObjectIds: String): Int {
    val normalized = changedObjectIds.trim()
    if (normalized.isBlank() || normalized == "[]") return 0
    return normalized
        .trim('[', ']')
        .split(',')
        .map { it.trim().trim('"') }
        .count { it.isNotBlank() }
}

private fun LocalMdbxDatabase.displayPath(context: Context): String {
    val raw = filePath.takeIf { it.isNotBlank() } ?: workingCopyPath.orEmpty()
    return when (sourceTypeEnum) {
        MdbxSourceType.REMOTE_WEBDAV -> "WebDAV · $raw"
        MdbxSourceType.LOCAL_INTERNAL -> {
            val copiedName = workingCopyPath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            listOfNotNull("Monica 私有目录", copiedName).joinToString(" · ").ifBlank { raw }
        }
        MdbxSourceType.LOCAL_EXTERNAL -> {
            val uri = runCatching { Uri.parse(raw) }.getOrNull()
            val displayName = uri?.let { context.displayNameForUri(it) }
            val location = uri?.lastPathSegment
                ?.substringAfterLast(':')
                ?.takeIf { it.isNotBlank() && it != displayName }
            listOfNotNull("本地文件", location, displayName)
                .joinToString(" · ")
                .ifBlank { raw }
        }
    }
}

private fun Context.displayNameForUri(uri: Uri): String? =
    runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}
