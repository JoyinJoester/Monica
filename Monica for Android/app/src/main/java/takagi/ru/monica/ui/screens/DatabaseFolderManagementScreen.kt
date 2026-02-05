package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.KeepassGroupSyncConfig
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.ui.components.BitwardenFolderItem
import takagi.ru.monica.ui.components.BitwardenFolderSelectorDialog
import takagi.ru.monica.ui.components.BitwardenLinkCard
import takagi.ru.monica.ui.components.BitwardenVaultItem
import takagi.ru.monica.ui.components.SyncTypeConfigDialog
import takagi.ru.monica.utils.KeePassGroupInfo
import takagi.ru.monica.viewmodel.DatabaseFolderManagementViewModel
import takagi.ru.monica.viewmodel.DatabaseSourceOption
import takagi.ru.monica.viewmodel.DatabaseSourceType

sealed class LinkTarget {
    data class LocalCategory(val category: Category) : LinkTarget()
    data class KeePassGroup(val databaseId: Long, val group: KeePassGroupInfo) : LinkTarget()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DatabaseFolderManagementScreen(
    viewModel: DatabaseFolderManagementViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val keepassDatabases by viewModel.keepassDatabases.collectAsState()
    val selectedSource by viewModel.selectedSource.collectAsState()
    val vaults by viewModel.bitwardenVaults.collectAsState()
    val selectedVaultId by viewModel.selectedBitwardenVaultId.collectAsState()
    val folders by viewModel.bitwardenFolders.collectAsState()
    val keepassGroups by viewModel.keepassGroups.collectAsState()
    val keepassMappings by viewModel.keepassGroupMappings.collectAsState()

    val createFolderNotSupportedMessage = stringResource(R.string.bitwarden_create_folder_not_supported)

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val sourceOptions = remember(keepassDatabases) {
        buildList<DatabaseSourceOption> {
            add(DatabaseSourceOption.local())
            keepassDatabases.forEach { db ->
                add(
                    DatabaseSourceOption(
                        type = DatabaseSourceType.KEEPASS,
                        databaseId = db.id,
                        name = db.name
                    )
                )
            }
        }
    }

    val folderNameMap: Map<String, String> = remember(folders) {
        folders.associateBy({ it.bitwardenFolderId }, { it.name })
    }

    val linkedFolderIds = remember(categories, keepassMappings) {
        val categoryIds = categories.mapNotNull { it.bitwardenFolderId?.takeIf { id -> id.isNotEmpty() } }
        val keepassIds = keepassMappings.mapNotNull { it.bitwardenFolderId?.takeIf { id -> id.isNotEmpty() } }
        (categoryIds + keepassIds).toSet()
    }

    val keepassMappingByPath = remember(keepassMappings) {
        keepassMappings.associateBy { it.groupPath }
    }

    var showFolderDialog by remember { mutableStateOf(false) }
    var showSyncTypeDialog by remember { mutableStateOf(false) }
    var pendingTarget by remember { mutableStateOf<LinkTarget?>(null) }
    var pendingFolderId by remember { mutableStateOf<String?>(null) }
    var pendingSyncTypes by remember { mutableStateOf<List<String>>(emptyList()) }

    val currentLinkedFolderId = remember(pendingTarget, categories, keepassMappings) {
        when (val target = pendingTarget) {
            is LinkTarget.LocalCategory -> target.category.bitwardenFolderId
            is LinkTarget.KeePassGroup -> {
                keepassMappings.firstOrNull { it.groupPath == target.group.path }?.bitwardenFolderId
            }
            null -> null
        }
    }
    val selectableLinkedFolderIds = remember(linkedFolderIds, currentLinkedFolderId) {
        if (currentLinkedFolderId == null) linkedFolderIds else linkedFolderIds - currentLinkedFolderId
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.database_folder_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionTitle(text = stringResource(R.string.sync_scope_section_title))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.bitwarden_upload_all),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = stringResource(R.string.bitwarden_upload_all_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.bitwardenUploadAll,
                                onCheckedChange = { viewModel.updateBitwardenUploadAll(it) }
                            )
                        }
                        if (settings.bitwardenUploadAll) {
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = {
                                    selectedVaultId?.let { viewModel.applyUploadAll(it) }
                                },
                                enabled = selectedVaultId != null
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.apply_upload_all))
                            }
                        }
                    }
                }
            }

            item {
                SectionTitle(text = stringResource(R.string.database_source_label))
                FlowRowChips(
                    options = sourceOptions,
                    selected = selectedSource,
                    onSelected = { viewModel.selectSource(it) },
                    localLabel = stringResource(R.string.database_source_local)
                )
            }

            item {
                SectionTitle(text = stringResource(R.string.bitwarden_vault_label))
                if (vaults.isEmpty()) {
                    Text(
                        text = stringResource(R.string.bitwarden_not_logged_in),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        vaults.forEach { vault ->
                            FilterChip(
                                selected = vault.id == selectedVaultId,
                                onClick = { viewModel.selectBitwardenVault(vault.id) },
                                label = { Text(vault.displayName ?: vault.email) }
                            )
                        }
                    }
                }
            }

            if (selectedSource.type == DatabaseSourceType.LOCAL) {
                item {
                    SectionTitle(text = stringResource(R.string.folder_management_title))
                }
                items(categories, key = { it.id }) { category ->
                    FolderLinkCard(
                        title = category.name,
                        linkedVault = vaults.find { it.id == category.bitwardenVaultId },
                        linkedFolderId = category.bitwardenFolderId,
                        folderNameMap = folderNameMap,
                        syncTypes = parseSyncTypesJson(category.syncItemTypes),
                        onLink = {
                            pendingTarget = LinkTarget.LocalCategory(category)
                            pendingFolderId = category.bitwardenFolderId
                            pendingSyncTypes = parseSyncTypesJson(category.syncItemTypes)
                            showFolderDialog = true
                        },
                        onUnlink = { viewModel.unlinkCategoryFromBitwarden(category.id) },
                        onConfigureSyncTypes = {
                            pendingTarget = LinkTarget.LocalCategory(category)
                            pendingFolderId = category.bitwardenFolderId
                            pendingSyncTypes = parseSyncTypesJson(category.syncItemTypes)
                            showSyncTypeDialog = true
                        },
                        enabled = selectedVaultId != null
                    )
                }
            } else {
                item {
                    SectionTitle(text = stringResource(R.string.folder_management_title))
                }
                items(keepassGroups, key = { it.path }) { group ->
                    val mapping = keepassMappingByPath[group.path]
                    FolderLinkCard(
                        title = group.path,
                        linkedVault = vaults.find { it.id == mapping?.bitwardenVaultId },
                        linkedFolderId = mapping?.bitwardenFolderId,
                        folderNameMap = folderNameMap,
                        syncTypes = parseSyncTypesJson(mapping?.syncItemTypes),
                        onLink = {
                            val databaseId = selectedSource.databaseId ?: return@FolderLinkCard
                            pendingTarget = LinkTarget.KeePassGroup(databaseId, group)
                            pendingFolderId = mapping?.bitwardenFolderId
                            pendingSyncTypes = parseSyncTypesJson(mapping?.syncItemTypes)
                            showFolderDialog = true
                        },
                        onUnlink = {
                            val databaseId = selectedSource.databaseId ?: return@FolderLinkCard
                            viewModel.unlinkKeePassGroup(databaseId, group.path)
                        },
                        onConfigureSyncTypes = {
                            val databaseId = selectedSource.databaseId ?: return@FolderLinkCard
                            pendingTarget = LinkTarget.KeePassGroup(databaseId, group)
                            pendingFolderId = mapping?.bitwardenFolderId
                            pendingSyncTypes = parseSyncTypesJson(mapping?.syncItemTypes)
                            showSyncTypeDialog = true
                        },
                        enabled = selectedVaultId != null
                    )
                }
            }

            item {
                SectionTitle(text = stringResource(R.string.bitwarden_folder_section_title))
                if (folders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.bitwarden_folder_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        folders.forEach { folder ->
                            BitwardenFolderRow(folder = folder)
                        }
                    }
                }
            }
        }
    }

    if (showFolderDialog) {
        BitwardenFolderSelectorDialog(
            vaults = vaults.map { vault ->
                BitwardenVaultItem(
                    id = vault.id,
                    name = vault.displayName ?: vault.email,
                    serverUrl = vault.serverUrl
                )
            },
            folders = folders.map { folder ->
                BitwardenFolderItem(
                    id = folder.bitwardenFolderId,
                    name = folder.name,
                    isLinked = selectableLinkedFolderIds.contains(folder.bitwardenFolderId)
                )
            },
            selectedVaultId = selectedVaultId,
            selectedFolderId = pendingFolderId,
            isLoading = false,
            onVaultSelected = { viewModel.selectBitwardenVault(it) },
            onFolderSelected = { pendingFolderId = it },
            onCreateNewFolder = {
                showFolderDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(createFolderNotSupportedMessage)
                }
            },
            onConfirm = {
                showFolderDialog = false
                showSyncTypeDialog = true
            },
            onDismiss = { showFolderDialog = false }
        )
    }

    if (showSyncTypeDialog) {
        SyncTypeConfigDialog(
            selectedTypes = pendingSyncTypes,
            onTypesChanged = { pendingSyncTypes = it },
            onConfirm = {
                val target = pendingTarget
                val vaultId = selectedVaultId
                val folderId = pendingFolderId ?: ""
                if (target != null && vaultId != null) {
                    when (target) {
                        is LinkTarget.LocalCategory -> {
                            viewModel.linkCategoryToBitwarden(
                                categoryId = target.category.id,
                                vaultId = vaultId,
                                folderId = folderId,
                                syncTypes = pendingSyncTypes
                            )
                        }
                        is LinkTarget.KeePassGroup -> {
                            viewModel.linkKeePassGroup(
                                databaseId = target.databaseId,
                                groupPath = target.group.path,
                                groupUuid = target.group.uuid,
                                vaultId = vaultId,
                                folderId = folderId,
                                syncTypes = pendingSyncTypes
                            )
                        }
                    }
                }
                showSyncTypeDialog = false
            },
            onDismiss = { showSyncTypeDialog = false }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FlowRowChips(
    options: List<DatabaseSourceOption>,
    selected: DatabaseSourceOption,
    onSelected: (DatabaseSourceOption) -> Unit,
    localLabel: String
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option.type == selected.type && option.databaseId == selected.databaseId,
                onClick = { onSelected(option) },
                label = { Text(if (option.type == DatabaseSourceType.LOCAL) localLabel else option.name) }
            )
        }
    }
}

@Composable
private fun FolderLinkCard(
    title: String,
    linkedVault: BitwardenVault?,
    linkedFolderId: String?,
    folderNameMap: Map<String, String>,
    syncTypes: List<String>,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
    onConfigureSyncTypes: () -> Unit,
    enabled: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            BitwardenLinkCard(
                isLinked = linkedVault != null,
                vaultName = linkedVault?.displayName ?: linkedVault?.email,
                folderName = linkedFolderId?.takeIf { it.isNotEmpty() }?.let { folderNameMap[it] ?: it },
                syncTypes = syncTypes,
                onLinkClick = { if (enabled) onLink() },
                onUnlinkClick = { if (enabled) onUnlink() },
                onConfigureSyncTypesClick = { if (enabled) onConfigureSyncTypes() }
            )
        }
    }
}

@Composable
private fun BitwardenFolderRow(folder: BitwardenFolder) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = folder.name, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun parseSyncTypesJson(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        json.trim('[', ']')
            .split(",")
            .map { it.trim().trim('"') }
            .filter { it.isNotEmpty() }
    } catch (e: Exception) {
        emptyList()
    }
}