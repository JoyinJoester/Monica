package takagi.ru.monica.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.viewmodel.FolderManagementViewModel
import takagi.ru.monica.viewmodel.KeePassGroupWithDatabase

/**
 * 文件夹管理页面 - M3E 表达性设计
 * 
 * 统一显示所有数据源的文件夹，无需切换：
 * - Monica 本地分类
 * - KeePass 数据库组（如有）
 * - Bitwarden 云端文件夹
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderManagementScreen(
    viewModel: FolderManagementViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    // 收集状态
    val settings by viewModel.settings.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val keepassDatabases by viewModel.keepassDatabases.collectAsState()
    val allKeePassGroups by viewModel.allKeePassGroups.collectAsState()
    val bitwardenVaults by viewModel.bitwardenVaults.collectAsState()
    val selectedBitwardenVaultId by viewModel.selectedBitwardenVaultId.collectAsState()
    val bitwardenFolders by viewModel.bitwardenFolders.collectAsState()
    val keepassMappings by viewModel.keepassGroupMappings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val operationResult by viewModel.operationResult.collectAsState()
    val syncStatusMessage by viewModel.syncStatusMessage.collectAsState()
    val needsUnlock by viewModel.needsUnlock.collectAsState()

    // UI 状态
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateLocalFolderDialog by remember { mutableStateOf(false) }
    var showEditFolderDialog by remember { mutableStateOf(false) }
    var showDeleteFolderDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf<BitwardenFolder?>(null) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var selectedKeePassGroup by remember { mutableStateOf<KeePassGroupWithDatabase?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // 处理操作结果
    LaunchedEffect(operationResult) {
        operationResult?.let { result ->
            snackbarHostState.showSnackbar(
                message = result.message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearOperationResult()
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.folder_management_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.folder_management_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (settings.bitwardenUploadAll) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.sync_status_syncing_full),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
            // ======== 1. 全量同步 Hero Card ========
            item(key = "sync_hero") {
                SyncHeroCard(
                    isEnabled = settings.bitwardenUploadAll,
                    onToggle = { viewModel.updateBitwardenUploadAll(it) },
                    selectedVault = bitwardenVaults.find { it.id == selectedBitwardenVaultId },
                    onApplyAll = {
                        selectedBitwardenVaultId?.let { viewModel.applyUploadAll(it) }
                    }
                )
            }

            // ======== 2. Bitwarden 账户选择 ========
            if (bitwardenVaults.isNotEmpty()) {
                item(key = "bitwarden_vault_selector") {
                    BitwardenVaultSelector(
                        vaults = bitwardenVaults,
                        selectedVaultId = selectedBitwardenVaultId,
                        onVaultSelected = { viewModel.selectBitwardenVault(it) }
                    )
                }
            } else {
                item(key = "bitwarden_not_connected") {
                    BitwardenNotConnectedCard()
                }
            }

            // ======== 3. Monica 本地文件夹 ========
            item(key = "local_folders_header") {
                FolderSectionHeader(
                    title = stringResource(R.string.folder_section_local),
                    icon = Icons.Filled.PhoneAndroid,
                    iconTint = MaterialTheme.colorScheme.primary,
                    action = {
                        FilledTonalIconButton(
                            onClick = { showCreateLocalFolderDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.folder_create)
                            )
                        }
                    }
                )
            }

            if (categories.isEmpty()) {
                item(key = "empty_local") {
                    EmptyFolderHint(stringResource(R.string.folder_empty_local))
                }
            } else {
                items(categories, key = { "local_${it.id}" }) { category ->
                    LocalFolderCard(
                        category = category,
                        linkedFolder = bitwardenFolders.find { 
                            it.bitwardenFolderId == category.bitwardenFolderId 
                        },
                        linkedVault = bitwardenVaults.find { 
                            it.id == category.bitwardenVaultId 
                        },
                        onLinkClick = {
                            selectedCategory = category
                            showLinkDialog = true
                        },
                        onUnlinkClick = {
                            viewModel.unlinkCategoryFromBitwarden(category.id)
                        },
                        isEnabled = selectedBitwardenVaultId != null
                    )
                }
            }

            // ======== 4. KeePass 文件夹（如果有数据库） ========
            if (keepassDatabases.isNotEmpty()) {
                item(key = "keepass_folders_header") {
                    Spacer(modifier = Modifier.height(8.dp))
                    FolderSectionHeader(
                        title = stringResource(R.string.folder_section_keepass),
                        icon = Icons.Filled.Key,
                        iconTint = Color(0xFF4CAF50)
                    )
                }

                if (allKeePassGroups.isEmpty()) {
                    item(key = "empty_keepass") {
                        EmptyFolderHint(stringResource(R.string.folder_empty_keepass))
                    }
                } else {
                    items(allKeePassGroups, key = { "keepass_${it.database.id}_${it.group.path}" }) { groupWithDb ->
                        val mapping = keepassMappings.find { 
                            it.keepassDatabaseId == groupWithDb.database.id && 
                            it.groupPath == groupWithDb.group.path 
                        }
                        KeePassGroupCard(
                            groupWithDb = groupWithDb,
                            linkedFolder = mapping?.bitwardenFolderId?.let { folderId ->
                                bitwardenFolders.find { it.bitwardenFolderId == folderId }
                            },
                            linkedVault = mapping?.bitwardenVaultId?.let { vaultId ->
                                bitwardenVaults.find { it.id == vaultId }
                            },
                            onLinkClick = {
                                selectedKeePassGroup = groupWithDb
                                showLinkDialog = true
                            },
                            onUnlinkClick = {
                                viewModel.unlinkKeePassGroup(groupWithDb.database.id, groupWithDb.group.path)
                            },
                            isEnabled = selectedBitwardenVaultId != null
                        )
                    }
                }
            }

            // ======== 5. Bitwarden 文件夹管理 ========
            if (selectedBitwardenVaultId != null) {
                item(key = "bitwarden_folders_header") {
                    Spacer(modifier = Modifier.height(8.dp))
                    FolderSectionHeader(
                        title = stringResource(R.string.folder_section_bitwarden),
                        icon = Icons.Filled.Cloud,
                        iconTint = Color(0xFF175DDC),
                        action = {
                            FilledTonalIconButton(
                                onClick = { showCreateFolderDialog = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.folder_create)
                                )
                            }
                        }
                    )
                }

                if (bitwardenFolders.isEmpty()) {
                    item(key = "empty_bitwarden") {
                        EmptyBitwardenFolderCard(
                            onCreateClick = { showCreateFolderDialog = true }
                        )
                    }
                } else {
                    items(bitwardenFolders, key = { "bw_${it.bitwardenFolderId}" }) { folder ->
                        BitwardenFolderCard(
                            folder = folder,
                            onEditClick = {
                                selectedFolder = folder
                                showEditFolderDialog = true
                            },
                            onDeleteClick = {
                                selectedFolder = folder
                                showDeleteFolderDialog = true
                            }
                        )
                    }
                }
            }

            // 底部间距
            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // 加载指示器（带同步状态消息）
        if (isLoading || syncStatusMessage != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        if (syncStatusMessage != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = syncStatusMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    // ======== 对话框 ========

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onConfirm = { name ->
                selectedBitwardenVaultId?.let { vaultId ->
                    viewModel.createBitwardenFolder(vaultId, name)
                }
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    if (showCreateLocalFolderDialog) {
        CreateFolderDialog(
            onConfirm = { name ->
                viewModel.createLocalFolder(name)
                showCreateLocalFolderDialog = false
            },
            onDismiss = { showCreateLocalFolderDialog = false }
        )
    }

    if (showEditFolderDialog && selectedFolder != null) {
        EditFolderDialog(
            folder = selectedFolder!!,
            onConfirm = { newName ->
                viewModel.renameBitwardenFolder(selectedFolder!!.bitwardenFolderId, newName)
                showEditFolderDialog = false
            },
            onDismiss = { showEditFolderDialog = false }
        )
    }

    if (showDeleteFolderDialog && selectedFolder != null) {
        DeleteFolderConfirmDialog(
            folderName = selectedFolder!!.name,
            onConfirm = {
                viewModel.deleteBitwardenFolder(selectedFolder!!.bitwardenFolderId)
                showDeleteFolderDialog = false
            },
            onDismiss = { showDeleteFolderDialog = false }
        )
    }

    if (showLinkDialog) {
        FolderLinkDialog(
            folders = bitwardenFolders,
            vaults = bitwardenVaults,
            selectedVaultId = selectedBitwardenVaultId,
            onVaultSelected = { viewModel.selectBitwardenVault(it) },
            onFolderSelected = { folderId ->
                val vaultId = selectedBitwardenVaultId ?: return@FolderLinkDialog
                selectedCategory?.let { cat ->
                    viewModel.linkCategoryToBitwarden(cat.id, vaultId, folderId, emptyList())
                }
                selectedKeePassGroup?.let { groupWithDb ->
                    viewModel.linkKeePassGroup(
                        groupWithDb.database.id,
                        groupWithDb.group.path,
                        groupWithDb.group.uuid,
                        vaultId,
                        folderId,
                        emptyList()
                    )
                }
                showLinkDialog = false
                selectedCategory = null
                selectedKeePassGroup = null
            },
            onCreateFolder = { showCreateFolderDialog = true },
            onDismiss = {
                showLinkDialog = false
                selectedCategory = null
                selectedKeePassGroup = null
            }
        )
    }

    // 解锁 Bitwarden 对话框
    if (needsUnlock) {
        BitwardenUnlockDialog(
            email = viewModel.getSelectedVaultEmail() ?: "",
            onUnlock = { password ->
                viewModel.unlockVault(password)
            },
            onDismiss = {
                viewModel.cancelUnlock()
            }
        )
    }
}

// ============ UI 组件 ============

@Composable
private fun SyncHeroCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    selectedVault: BitwardenVault?,
    onApplyAll: () -> Unit
) {
    val containerColor = if (isEnabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isEnabled) Icons.Default.CloudSync else Icons.Outlined.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = if (isEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.sync_all_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Text(
                            text = stringResource(R.string.sync_all_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isEnabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                
                Switch(checked = isEnabled, onCheckedChange = onToggle)
            }

            AnimatedVisibility(
                visible = isEnabled && selectedVault != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.target_account),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = selectedVault?.displayName ?: selectedVault?.email ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        FilledTonalButton(onClick = onApplyAll) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.sync_apply_now))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BitwardenVaultSelector(
    vaults: List<BitwardenVault>,
    selectedVaultId: Long?,
    onVaultSelected: (Long) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.bitwarden_account_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            vaults.forEach { vault ->
                FilterChip(
                    selected = vault.id == selectedVaultId,
                    onClick = { onVaultSelected(vault.id) },
                    label = {
                        Text(
                            text = vault.displayName ?: vault.email,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFF175DDC)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun BitwardenNotConnectedCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.bitwarden_not_connected),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.bitwarden_connect_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FolderSectionHeader(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}

@Composable
private fun EmptyFolderHint(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun EmptyBitwardenFolderCard(onCreateClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.folder_empty_bitwarden),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = onCreateClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.folder_create_first))
            }
        }
    }
}

@Composable
private fun LocalFolderCard(
    category: Category,
    linkedFolder: BitwardenFolder?,
    linkedVault: BitwardenVault?,
    onLinkClick: () -> Unit,
    onUnlinkClick: () -> Unit,
    isEnabled: Boolean
) {
    val isLinked = linkedVault != null

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isLinked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (isLinked) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = buildString {
                                linkedFolder?.let { append(it.name) }
                                    ?: append(linkedVault?.displayName ?: linkedVault?.email)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.folder_not_synced),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isLinked) {
                IconButton(onClick = onUnlinkClick, enabled = isEnabled) {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = stringResource(R.string.folder_unlink),
                        tint = if (isEnabled) MaterialTheme.colorScheme.error 
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                FilledTonalIconButton(onClick = onLinkClick, enabled = isEnabled) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = stringResource(R.string.folder_link)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeePassGroupCard(
    groupWithDb: KeePassGroupWithDatabase,
    linkedFolder: BitwardenFolder?,
    linkedVault: BitwardenVault?,
    onLinkClick: () -> Unit,
    onUnlinkClick: () -> Unit,
    isEnabled: Boolean
) {
    val isLinked = linkedVault != null
    val groupName = groupWithDb.group.path.split("/").lastOrNull() ?: groupWithDb.group.path

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isLinked) {
            Color(0xFF4CAF50).copy(alpha = 0.1f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = groupName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${groupWithDb.database.name} / ${groupWithDb.group.path}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLinked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isLinked && linkedFolder != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = linkedFolder.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            if (isLinked) {
                IconButton(onClick = onUnlinkClick, enabled = isEnabled) {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = stringResource(R.string.folder_unlink),
                        tint = if (isEnabled) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                FilledTonalIconButton(onClick = onLinkClick, enabled = isEnabled) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = stringResource(R.string.folder_link)
                    )
                }
            }
        }
    }
}

@Composable
private fun BitwardenFolderCard(
    folder: BitwardenFolder,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF175DDC).copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color(0xFF175DDC),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.edit)
                )
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ============ 对话框 ============

@Composable
private fun CreateFolderDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CreateNewFolder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.folder_create)) },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text(stringResource(R.string.folder_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(folderName) },
                enabled = folderName.isNotBlank()
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun EditFolderDialog(
    folder: BitwardenFolder,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var folderName by remember { mutableStateOf(folder.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.folder_edit)) },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text(stringResource(R.string.folder_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(folderName) },
                enabled = folderName.isNotBlank() && folderName != folder.name
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun DeleteFolderConfirmDialog(
    folderName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.folder_delete)) },
        text = { Text(stringResource(R.string.folder_delete_confirm, folderName)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun FolderLinkDialog(
    folders: List<BitwardenFolder>,
    vaults: List<BitwardenVault>,
    selectedVaultId: Long?,
    onVaultSelected: (Long) -> Unit,
    onFolderSelected: (String) -> Unit,
    onCreateFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_link_to_bitwarden)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (vaults.size > 1) {
                    Text(
                        text = stringResource(R.string.folder_select_vault),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vaults.forEach { vault ->
                            FilterChip(
                                selected = vault.id == selectedVaultId,
                                onClick = { onVaultSelected(vault.id) },
                                label = { Text(vault.displayName ?: vault.email) }
                            )
                        }
                    }
                    HorizontalDivider()
                }

                Text(
                    text = stringResource(R.string.folder_select_folder),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    onClick = onCreateFolder,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.folder_create_new),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Surface(
                    onClick = { onFolderSelected("") },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FolderOff, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.folder_no_folder_root),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                folders.forEach { folder ->
                    Surface(
                        onClick = { onFolderSelected(folder.bitwardenFolderId) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = Color(0xFF175DDC)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Bitwarden 解锁对话框
 */
@Composable
private fun BitwardenUnlockDialog(
    email: String,
    onUnlock: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.bitwarden_unlock_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.bitwarden_unlock_message, email),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.bitwarden_master_password)) },
                    visualTransformation = if (showPassword) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onUnlock(password) },
                enabled = password.isNotBlank()
            ) {
                Text(stringResource(R.string.unlock))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
