package takagi.ru.monica.ui.v2

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.utils.KeePassGroupInfo
import takagi.ru.monica.viewmodel.V2FolderManagementViewModel
import takagi.ru.monica.viewmodel.VaultType

/**
 * V2 文件夹管理页面 - M3E 表达性设计
 * 
 * 设计原则：
 * 1. 清晰的数据源分层：Monica本地 / KeePass / Bitwarden
 * 2. 直观的同步开关
 * 3. 支持创建、编辑、删除 Bitwarden 文件夹
 * 4. 一目了然的关联状态
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2FolderManagementScreen(
    viewModel: V2FolderManagementViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    // 收集状态
    val settings by viewModel.settings.collectAsState()
    val selectedVaultType by viewModel.selectedVaultType.collectAsState()
    val selectedKeePassDatabaseId by viewModel.selectedKeePassDatabaseId.collectAsState()
    val keepassDatabases by viewModel.keepassDatabases.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val keepassGroups by viewModel.keepassGroups.collectAsState()
    val bitwardenVaults by viewModel.bitwardenVaults.collectAsState()
    val selectedBitwardenVaultId by viewModel.selectedBitwardenVaultId.collectAsState()
    val bitwardenFolders by viewModel.bitwardenFolders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val operationResult by viewModel.operationResult.collectAsState()

    // UI 状态
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showEditFolderDialog by remember { mutableStateOf(false) }
    var showDeleteFolderDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf<BitwardenFolder?>(null) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var selectedKeePassGroup by remember { mutableStateOf<KeePassGroupInfo?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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
            // M3E 风格顶栏
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.v2_folder_management_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.v2_folder_management_subtitle),
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
                    // 同步状态指示
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
                                    text = "同步中",
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

            // ======== 2. 数据源选择器 ========
            item(key = "source_selector") {
                VaultSourceSelector(
                    selectedType = selectedVaultType,
                    selectedKeePassDbId = selectedKeePassDatabaseId,
                    keepassDatabases = keepassDatabases,
                    onTypeSelected = { viewModel.selectVaultType(it) },
                    onKeePassDbSelected = { viewModel.selectKeePassDatabase(it) }
                )
            }

            // ======== 3. Bitwarden 账户选择 ========
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

            // ======== 4. 当前数据源的文件夹列表 ========
            item(key = "folders_header") {
                FolderSectionHeader(
                    title = when (selectedVaultType) {
                        VaultType.MONICA_LOCAL -> stringResource(R.string.v2_local_folders)
                        VaultType.KEEPASS -> stringResource(R.string.v2_keepass_groups)
                    },
                    icon = when (selectedVaultType) {
                        VaultType.MONICA_LOCAL -> Icons.Outlined.Folder
                        VaultType.KEEPASS -> Icons.Outlined.Key
                    }
                )
            }

            // 本地文件夹列表
            when (selectedVaultType) {
                VaultType.MONICA_LOCAL -> {
                    if (categories.isEmpty()) {
                        item(key = "empty_local") {
                            EmptyFolderState(
                                message = stringResource(R.string.v2_no_local_folders),
                                icon = Icons.Outlined.FolderOff
                            )
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
                                }
                            )
                        }
                    }
                }

                VaultType.KEEPASS -> {
                    if (keepassGroups.isEmpty()) {
                        item(key = "empty_keepass") {
                            EmptyFolderState(
                                message = stringResource(R.string.v2_no_keepass_groups),
                                icon = Icons.Outlined.FolderOff
                            )
                        }
                    } else {
                        items(keepassGroups, key = { "keepass_${it.path}" }) { group ->
                            KeePassGroupCard(
                                group = group,
                                onLinkClick = {
                                    selectedKeePassGroup = group
                                    showLinkDialog = true
                                }
                            )
                        }
                    }
                }
            }

            // ======== 5. Bitwarden 文件夹管理 ========
            if (selectedBitwardenVaultId != null) {
                item(key = "bitwarden_folders_header") {
                    Spacer(modifier = Modifier.height(8.dp))
                    FolderSectionHeader(
                        title = stringResource(R.string.v2_bitwarden_folders),
                        icon = Icons.Outlined.Cloud,
                        action = {
                            FilledTonalIconButton(
                                onClick = { showCreateFolderDialog = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.v2_create_folder)
                                )
                            }
                        }
                    )
                }

                if (bitwardenFolders.isEmpty()) {
                    item(key = "empty_bitwarden") {
                        EmptyFolderState(
                            message = stringResource(R.string.v2_no_bitwarden_folders),
                            icon = Icons.Outlined.CloudOff,
                            actionLabel = stringResource(R.string.v2_create_first_folder),
                            onAction = { showCreateFolderDialog = true }
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

        // 加载指示器
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    // ======== 对话框 ========

    // 创建文件夹对话框
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

    // 编辑文件夹对话框
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

    // 删除确认对话框
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

    // 关联对话框
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
                selectedKeePassGroup?.let { group ->
                    selectedKeePassDatabaseId?.let { dbId ->
                        viewModel.linkKeePassGroup(dbId, group.path, group.uuid, vaultId, folderId, emptyList())
                    }
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
}

// ============ UI 组件 ============

/**
 * 同步 Hero 卡片 - 页面顶部醒目位置
 */
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
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
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
                            text = stringResource(R.string.v2_sync_all_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Text(
                            text = stringResource(R.string.v2_sync_all_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isEnabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            }

            // 展开的操作区域
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
                                text = "目标账户",
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
                            Text(stringResource(R.string.v2_apply_sync))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 数据源选择器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultSourceSelector(
    selectedType: VaultType,
    selectedKeePassDbId: Long?,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    onTypeSelected: (VaultType) -> Unit,
    onKeePassDbSelected: (Long) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.v2_select_database),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // 主要数据源选择
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                selected = selectedType == VaultType.MONICA_LOCAL,
                onClick = { onTypeSelected(VaultType.MONICA_LOCAL) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            ) {
                Text("Monica 本地")
            }

            SegmentedButton(
                selected = selectedType == VaultType.KEEPASS,
                onClick = { onTypeSelected(VaultType.KEEPASS) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            ) {
                Text("KeePass")
            }
        }

        // KeePass 数据库选择（如果有多个）
        AnimatedVisibility(
            visible = selectedType == VaultType.KEEPASS && keepassDatabases.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    keepassDatabases.forEach { db ->
                        FilterChip(
                            selected = db.id == selectedKeePassDbId,
                            onClick = { onKeePassDbSelected(db.id) },
                            label = { Text(db.name) },
                            leadingIcon = {
                                if (db.id == selectedKeePassDbId) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bitwarden 账户选择器
 */
@Composable
private fun BitwardenVaultSelector(
    vaults: List<BitwardenVault>,
    selectedVaultId: Long?,
    onVaultSelected: (Long) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.v2_bitwarden_account),
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
                            tint = Color(0xFF175DDC) // Bitwarden blue
                        )
                    }
                )
            }
        }
    }
}

/**
 * Bitwarden 未连接提示卡片
 */
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
                    text = stringResource(R.string.v2_bitwarden_not_connected),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.v2_bitwarden_connect_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 文件夹区域标题
 */
@Composable
private fun FolderSectionHeader(
    title: String,
    icon: ImageVector,
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
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}

/**
 * 空文件夹状态
 */
@Composable
private fun EmptyFolderState(
    message: String,
    icon: ImageVector,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
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
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(onClick = onAction) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

/**
 * 本地文件夹卡片
 */
@Composable
private fun LocalFolderCard(
    category: Category,
    linkedFolder: BitwardenFolder?,
    linkedVault: BitwardenVault?,
    onLinkClick: () -> Unit,
    onUnlinkClick: () -> Unit
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
            // 文件夹图标
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

            // 文件夹信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (isLinked) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = buildString {
                                append(linkedVault?.displayName ?: linkedVault?.email ?: "")
                                linkedFolder?.let { append(" / ${it.name}") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.v2_not_synced),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 操作按钮
            if (isLinked) {
                IconButton(onClick = onUnlinkClick) {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = stringResource(R.string.v2_unlink),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                FilledTonalIconButton(onClick = onLinkClick) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = stringResource(R.string.v2_link)
                    )
                }
            }
        }
    }
}

/**
 * KeePass 组卡片
 */
@Composable
private fun KeePassGroupCard(
    group: KeePassGroupInfo,
    onLinkClick: () -> Unit
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
                    text = group.path.split("/").lastOrNull() ?: group.path,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = group.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FilledTonalIconButton(onClick = onLinkClick) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = stringResource(R.string.v2_link)
                )
            }
        }
    }
}

/**
 * Bitwarden 文件夹卡片
 */
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
                    contentDescription = stringResource(R.string.v2_edit)
                )
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.v2_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ============ 对话框 ============

/**
 * 创建文件夹对话框
 */
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
        title = {
            Text(stringResource(R.string.v2_create_folder))
        },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text(stringResource(R.string.v2_folder_name)) },
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

/**
 * 编辑文件夹对话框
 */
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
        title = {
            Text(stringResource(R.string.v2_edit_folder))
        },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text(stringResource(R.string.v2_folder_name)) },
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

/**
 * 删除确认对话框
 */
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
        title = {
            Text(stringResource(R.string.v2_delete_folder))
        },
        text = {
            Text(stringResource(R.string.v2_delete_folder_confirm, folderName))
        },
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

/**
 * 关联文件夹对话框
 */
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
        title = {
            Text(stringResource(R.string.v2_link_to_bitwarden))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Vault 选择
                if (vaults.size > 1) {
                    Text(
                        text = stringResource(R.string.v2_select_vault),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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

                // 文件夹列表
                Text(
                    text = stringResource(R.string.v2_select_folder),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 创建新文件夹
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
                            text = stringResource(R.string.v2_create_new_folder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 根目录选项
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
                        Icon(
                            imageVector = Icons.Default.FolderOff,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.v2_no_folder_root),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // 现有文件夹
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
