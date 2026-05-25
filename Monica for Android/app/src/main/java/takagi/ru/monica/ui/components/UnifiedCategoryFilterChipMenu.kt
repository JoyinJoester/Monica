package takagi.ru.monica.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import takagi.ru.monica.R
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.data.isMonicaLocalCategory
import takagi.ru.monica.data.writeOperationAvailability
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.repository.MdbxStoredFolderEntry
import takagi.ru.monica.utils.KeePassGroupInfo
import takagi.ru.monica.utils.decodeKeePassPathForDisplay

private data class ChipMenuLocalCategoryNode(
    val category: Category,
    val path: String,
    val parentPath: String?,
    val displayName: String
)

@Composable
private fun <T> rememberAsyncComputed(
    vararg keys: Any?,
    initialValue: T,
    compute: suspend () -> T
): T {
    val state = remember { mutableStateOf(initialValue) }
    val latestCompute by rememberUpdatedState(compute)

    LaunchedEffect(*keys) {
        state.value = withContext(Dispatchers.Default) {
            latestCompute()
        }
    }

    return state.value
}

val UnifiedCategoryFilterChipMenuOffset = DpOffset(x = 48.dp, y = 6.dp)
private val UnifiedCategoryFilterChipMenuMinWidth = 280.dp
private val UnifiedCategoryFilterChipMenuMaxWidth = 336.dp
private val UnifiedCategoryFilterChipMenuCompactInset = 72.dp
private val UnifiedCategoryFilterChipMenuShape = RoundedCornerShape(20.dp)

@Composable
fun rememberUnifiedCategoryFilterChipMenuWidth(): androidx.compose.ui.unit.Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    return minOf(
        UnifiedCategoryFilterChipMenuMaxWidth,
        maxOf(
            UnifiedCategoryFilterChipMenuMinWidth,
            screenWidth - UnifiedCategoryFilterChipMenuCompactInset
        )
    )
}

@Composable
fun unifiedCategoryFilterChipMenuModifier(): Modifier {
    val resolvedMenuWidth = rememberUnifiedCategoryFilterChipMenuWidth()
    return Modifier
        .widthIn(min = resolvedMenuWidth, max = resolvedMenuWidth)
        .heightIn(max = 460.dp)
        .shadow(10.dp, UnifiedCategoryFilterChipMenuShape)
        .clip(UnifiedCategoryFilterChipMenuShape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
            shape = UnifiedCategoryFilterChipMenuShape
        )
}

@Composable
fun UnifiedCategoryFilterChipMenuDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: DpOffset = UnifiedCategoryFilterChipMenuOffset,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(20.dp),
            small = RoundedCornerShape(20.dp)
        )
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            offset = offset,
            modifier = unifiedCategoryFilterChipMenuModifier()
        ) {
            content()
        }
    }
}

@Composable
private fun ChipMenuSection(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(durationMillis = 160),
        label = "chip_menu_section_arrow"
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onExpandedChange(!expanded) }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(180)) + fadeIn(animationSpec = tween(120)),
            exit = shrinkVertically(animationSpec = tween(140)) + fadeOut(animationSpec = tween(100))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun UnifiedCategoryFilterChipMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
    selected: UnifiedCategoryFilterSelection,
    onSelect: (UnifiedCategoryFilterSelection) -> Unit,
    showLocalOnlyQuickFilter: Boolean = false,
    isLocalOnlyQuickFilterSelected: Boolean = false,
    onSelectLocalOnlyQuickFilter: (() -> Unit)? = null,
    launchAnchorBounds: Rect? = null,
    categories: List<Category>,
    keepassDatabases: List<LocalKeePassDatabase>,
    mdbxDatabases: List<LocalMdbxDatabase> = emptyList(),
    bitwardenVaults: List<BitwardenVault>,
    getBitwardenFolders: (Long) -> Flow<List<BitwardenFolder>>,
    getMdbxFolders: (Long) -> Flow<List<MdbxStoredFolderEntry>> = { flowOf(emptyList()) },
    getKeePassGroups: ((Long) -> Flow<List<KeePassGroupInfo>>)? = null,
    categoryEditMode: Boolean = false,
    onRequestCategoryAction: ((Category) -> Unit)? = null,
    quickFilterContent: (@Composable ColumnScope.() -> Unit)? = null,
    trailingContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    if (!visible) return

    var showDeferredFolderSection by remember { mutableStateOf(false) }
    var databasesExpanded by rememberSaveable { mutableStateOf(false) }
    var quickFiltersExpanded by rememberSaveable { mutableStateOf(true) }
    var foldersExpanded by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        showDeferredFolderSection = true
    }
    val localNodes = rememberAsyncComputed(
        categories,
        initialValue = emptyList()
    ) {
        buildLocalCategoryNodes(categories.filter(Category::isMonicaLocalCategory))
    }
    val localNodeByPath = remember(localNodes) { localNodes.associateBy(ChipMenuLocalCategoryNode::path) }
    val localCurrentPath = remember(selected, localNodes) {
        when (selected) {
            is UnifiedCategoryFilterSelection.Custom ->
                localNodes.firstOrNull { node -> node.category.id == selected.categoryId }?.path
            else -> null
        }
    }
    val selectedVaultId = when (selected) {
        is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> selected.vaultId
        is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> selected.vaultId
        is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> selected.vaultId
        is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> selected.vaultId
        else -> null
    }
    val selectedKeePassDatabaseId = when (selected) {
        is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> selected.databaseId
        is UnifiedCategoryFilterSelection.KeePassGroupFilter -> selected.databaseId
        is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> selected.databaseId
        is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> selected.databaseId
        else -> null
    }
    val selectedMdbxDatabaseId = when (selected) {
        is UnifiedCategoryFilterSelection.MdbxDatabaseFilter -> selected.databaseId
        is UnifiedCategoryFilterSelection.MdbxFolderFilter -> selected.databaseId
        else -> null
    }
    val bitwardenFolders by remember(selectedVaultId) {
        selectedVaultId?.let(getBitwardenFolders) ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val keepassGroups by remember(selectedKeePassDatabaseId, getKeePassGroups) {
        selectedKeePassDatabaseId?.let { databaseId ->
            getKeePassGroups?.invoke(databaseId)
        } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val mdbxFolders by remember(selectedMdbxDatabaseId, getMdbxFolders) {
        selectedMdbxDatabaseId?.let(getMdbxFolders) ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val folderChips = remember(
        selected,
        localNodes,
        localCurrentPath,
        bitwardenFolders,
        keepassGroups,
        mdbxFolders
    ) {
        buildFolderChips(
            selected = selected,
            localNodes = localNodes,
            localNodeByPath = localNodeByPath,
            localCurrentPath = localCurrentPath,
            bitwardenFolders = bitwardenFolders,
            keepassGroups = keepassGroups,
            mdbxFolders = mdbxFolders
        )
    }
    val quickFilterItems = remember(selected, showLocalOnlyQuickFilter, isLocalOnlyQuickFilterSelected, onSelectLocalOnlyQuickFilter) {
        buildList {
            add(QuickFilterChipItem(
                selection = selected.toStarredSelection(),
                isSelected = selected.isStarredScope(),
                labelRes = R.string.filter_starred,
                icon = Icons.Outlined.CheckCircle
            ))
            add(QuickFilterChipItem(
                selection = selected.toUncategorizedSelection(),
                isSelected = selected.isUncategorizedScope(),
                labelRes = R.string.filter_uncategorized,
                icon = Icons.Default.FolderOff
            ))
            if (showLocalOnlyQuickFilter && onSelectLocalOnlyQuickFilter != null) {
                add(QuickFilterChipItem(
                    selection = null,
                    isSelected = isLocalOnlyQuickFilterSelected,
                    labelRes = R.string.filter_local_only,
                    icon = Icons.Default.Smartphone
                ))
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val dbArrowRotation by animateFloatAsState(
                targetValue = if (databasesExpanded) 0f else -90f,
                animationSpec = tween(durationMillis = 160),
                label = "database_section_arrow"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { databasesExpanded = !databasesExpanded }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.category_selection_menu_databases),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = dbArrowRotation }
                )
            }
            AnimatedVisibility(
                visible = databasesExpanded,
                enter = expandVertically(animationSpec = tween(180)) + fadeIn(animationSpec = tween(120)),
                exit = shrinkVertically(animationSpec = tween(140)) + fadeOut(animationSpec = tween(100))
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MonicaExpressiveFilterChip(
                        selected = selected is UnifiedCategoryFilterSelection.All,
                        onClick = { onSelect(UnifiedCategoryFilterSelection.All) },
                        label = stringResource(R.string.category_all),
                        leadingIcon = Icons.Default.List
                    )
                    MonicaExpressiveFilterChip(
                        selected = selected.isMonicaScope(),
                        onClick = { onSelect(UnifiedCategoryFilterSelection.Local) },
                        label = stringResource(R.string.category_selection_menu_local_database),
                        leadingIcon = Icons.Default.Smartphone
                    )
                    keepassDatabases.forEach { database ->
                        MonicaExpressiveFilterChip(
                            selected = selected.isKeePassScope(database.id),
                            onClick = { onSelect(UnifiedCategoryFilterSelection.KeePassDatabaseFilter(database.id)) },
                            label = database.name,
                            leadingIcon = Icons.Default.Key,
                            statusDotColor = if (database.writeOperationAvailability().canOperate) {
                                StorageHealthyGreen
                            } else {
                                null
                            }
                        )
                    }
                    mdbxDatabases.forEach { database ->
                        MonicaExpressiveFilterChip(
                            selected = selected.isMdbxScope(database.id),
                            onClick = { onSelect(UnifiedCategoryFilterSelection.MdbxDatabaseFilter(database.id)) },
                            label = database.name,
                            leadingIcon = Icons.Default.Key,
                            statusDotColor = StorageHealthyGreen
                        )
                    }
                    bitwardenVaults.forEach { vault ->
                        MonicaExpressiveFilterChip(
                            selected = selected.isBitwardenScope(vault.id),
                            onClick = { onSelect(UnifiedCategoryFilterSelection.BitwardenVaultFilter(vault.id)) },
                            label = vault.email.ifBlank { "Bitwarden" },
                            leadingIcon = Icons.Default.CloudSync,
                            statusDotColor = if (vault.hasHealthyConnection()) StorageHealthyGreen else null
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = !databasesExpanded,
                enter = expandVertically(animationSpec = tween(180)) + fadeIn(animationSpec = tween(120)),
                exit = shrinkVertically(animationSpec = tween(140)) + fadeOut(animationSpec = tween(100))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MonicaExpressiveFilterChip(
                        selected = selected is UnifiedCategoryFilterSelection.All,
                        onClick = { onSelect(UnifiedCategoryFilterSelection.All) },
                        label = stringResource(R.string.category_all),
                        leadingIcon = Icons.Default.List
                    )
                    MonicaExpressiveFilterChip(
                        selected = selected.isMonicaScope(),
                        onClick = { onSelect(UnifiedCategoryFilterSelection.Local) },
                        label = stringResource(R.string.category_selection_menu_local_database),
                        leadingIcon = Icons.Default.Smartphone
                    )
                    keepassDatabases.forEach { database ->
                        MonicaExpressiveFilterChip(
                            selected = selected.isKeePassScope(database.id),
                            onClick = { onSelect(UnifiedCategoryFilterSelection.KeePassDatabaseFilter(database.id)) },
                            label = database.name,
                            leadingIcon = Icons.Default.Key,
                            statusDotColor = if (database.writeOperationAvailability().canOperate) {
                                StorageHealthyGreen
                            } else {
                                null
                            }
                        )
                    }
                    mdbxDatabases.forEach { database ->
                        MonicaExpressiveFilterChip(
                            selected = selected.isMdbxScope(database.id),
                            onClick = { onSelect(UnifiedCategoryFilterSelection.MdbxDatabaseFilter(database.id)) },
                            label = database.name,
                            leadingIcon = Icons.Default.Key,
                            statusDotColor = StorageHealthyGreen
                        )
                    }
                    bitwardenVaults.forEach { vault ->
                        MonicaExpressiveFilterChip(
                            selected = selected.isBitwardenScope(vault.id),
                            onClick = { onSelect(UnifiedCategoryFilterSelection.BitwardenVaultFilter(vault.id)) },
                            label = vault.email.ifBlank { "Bitwarden" },
                            leadingIcon = Icons.Default.CloudSync,
                            statusDotColor = if (vault.hasHealthyConnection()) StorageHealthyGreen else null
                        )
                    }
                }
            }
        }

        if (quickFilterContent != null) {
            ChipMenuSection(
                title = stringResource(R.string.category_selection_menu_quick_filters),
                expanded = quickFiltersExpanded,
                onExpandedChange = { quickFiltersExpanded = it }
            ) {
                quickFilterContent.invoke(this)
            }
        } else {
            ChipMenuSection(
                title = stringResource(R.string.category_selection_menu_quick_filters),
                expanded = quickFiltersExpanded,
                onExpandedChange = { quickFiltersExpanded = it }
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickFilterItems.forEach { item ->
                        MonicaExpressiveFilterChip(
                            selected = item.isSelected,
                            onClick = {
                                if (item.isSelected) {
                                    // 再次点击已选中的快捷筛选 → 取消，回到基础 scope
                                    onSelect(selected.toBaseScope())
                                } else if (item.selection != null) {
                                    onSelect(item.selection)
                                } else {
                                    onSelectLocalOnlyQuickFilter?.invoke()
                                }
                            },
                            label = stringResource(item.labelRes),
                            leadingIcon = item.icon
                        )
                    }
                }
            }
        }

        if (showDeferredFolderSection && folderChips.isNotEmpty()) {
            ChipMenuSection(
                title = stringResource(R.string.category_selection_menu_folders),
                expanded = foldersExpanded,
                onExpandedChange = { foldersExpanded = it }
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    folderChips.forEach { chip ->
                        val editableCategory = if (
                            categoryEditMode &&
                            onRequestCategoryAction != null &&
                            !chip.isBack
                        ) {
                            (chip.selection as? UnifiedCategoryFilterSelection.Custom)
                                ?.let { selection -> categories.firstOrNull { it.id == selection.categoryId } }
                        } else {
                            null
                        }
                        MonicaExpressiveFilterChip(
                            selected = chip.selection == selected,
                            onClick = {
                                if (editableCategory != null && onRequestCategoryAction != null) {
                                    onRequestCategoryAction(editableCategory)
                                } else {
                                    onSelect(chip.selection)
                                }
                            },
                            label = chip.label,
                            leadingIcon = if (chip.isBack) {
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft
                            } else if (categoryEditMode && editableCategory != null) {
                                Icons.Default.MoreVert
                            } else {
                                Icons.Default.Folder
                            }
                        )
                    }
                }
            }
        }

        trailingContent?.invoke(this)
    }
}

private data class QuickFilterChipItem(
    val selection: UnifiedCategoryFilterSelection?,
    val isSelected: Boolean,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private data class FolderChipItem(
    val label: String,
    val selection: UnifiedCategoryFilterSelection,
    val isBack: Boolean = false
)

private val StorageHealthyGreen = Color(0xFF22C55E)

private fun BitwardenVault.hasHealthyConnection(): Boolean {
    return isConnected && !encryptedRefreshToken.isNullOrBlank()
}

private fun buildFolderChips(
    selected: UnifiedCategoryFilterSelection,
    localNodes: List<ChipMenuLocalCategoryNode>,
    localNodeByPath: Map<String, ChipMenuLocalCategoryNode>,
    localCurrentPath: String?,
    bitwardenFolders: List<BitwardenFolder>,
    keepassGroups: List<KeePassGroupInfo>,
    mdbxFolders: List<MdbxStoredFolderEntry>
): List<FolderChipItem> {
    return when (selected) {
        UnifiedCategoryFilterSelection.All,
        UnifiedCategoryFilterSelection.Local,
        UnifiedCategoryFilterSelection.Starred,
        UnifiedCategoryFilterSelection.Uncategorized,
        UnifiedCategoryFilterSelection.LocalStarred,
        UnifiedCategoryFilterSelection.LocalUncategorized,
        is UnifiedCategoryFilterSelection.Custom -> {
            val currentPath = localCurrentPath
            val chips = mutableListOf<FolderChipItem>()
            val parentPath = currentPath?.substringBeforeLast('/', "")
                ?.takeIf { it.isNotBlank() }
            if (currentPath != null) {
                val parentSelection = parentPath?.let { path ->
                    localNodeByPath[path]?.let { UnifiedCategoryFilterSelection.Custom(it.category.id) }
                } ?: UnifiedCategoryFilterSelection.Local
                chips += FolderChipItem(
                    label = localNodeByPath[parentPath ?: ""]?.displayName
                        ?: localNodeByPath[currentPath]?.parentPath?.substringAfterLast('/')
                        ?: "返回",
                    selection = parentSelection,
                    isBack = true
                )
            }
            chips += localNodes
                .filter { node -> node.parentPath == currentPath }
                .map { node -> FolderChipItem(node.displayName, UnifiedCategoryFilterSelection.Custom(node.category.id)) }
            chips
        }

        is UnifiedCategoryFilterSelection.BitwardenVaultFilter,
        is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter,
        is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter,
        is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> {
            bitwardenFolders
                .filter { it.bitwardenFolderId.isNotBlank() }
                .map {
                    FolderChipItem(
                        label = it.name,
                        selection = UnifiedCategoryFilterSelection.BitwardenFolderFilter(
                            vaultId = it.vaultId,
                            folderId = it.bitwardenFolderId
                        )
                    )
                }
        }

        is UnifiedCategoryFilterSelection.KeePassDatabaseFilter,
        is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter,
        is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter,
        is UnifiedCategoryFilterSelection.KeePassGroupFilter -> {
            val currentPath = (selected as? UnifiedCategoryFilterSelection.KeePassGroupFilter)?.groupPath
                ?.trim('/')
                ?.trim()
            val databaseId = when (selected) {
                is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> selected.databaseId
                is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> selected.databaseId
                is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> selected.databaseId
                is UnifiedCategoryFilterSelection.KeePassGroupFilter -> selected.databaseId
                else -> return emptyList()
            }
            val chips = mutableListOf<FolderChipItem>()
            if (!currentPath.isNullOrBlank()) {
                val parentPath = currentPath.substringBeforeLast('/', "").takeIf { it.isNotBlank() }
                chips += FolderChipItem(
                    label = "返回",
                    selection = parentPath?.let {
                        UnifiedCategoryFilterSelection.KeePassGroupFilter(databaseId, it)
                    } ?: UnifiedCategoryFilterSelection.KeePassDatabaseFilter(databaseId),
                    isBack = true
                )
            }
            val children = keepassGroups.filter { group ->
                val normalizedPath = group.path.trim('/').trim()
                val parent = normalizedPath.substringBeforeLast('/', "").takeIf { it.isNotBlank() }
                parent == currentPath
            }
            chips += children.map {
                FolderChipItem(
                    label = decodeKeePassPathForDisplay(it.path),
                    selection = UnifiedCategoryFilterSelection.KeePassGroupFilter(databaseId, it.path)
                )
            }
            chips
        }

        is UnifiedCategoryFilterSelection.MdbxDatabaseFilter,
        is UnifiedCategoryFilterSelection.MdbxFolderFilter -> {
            val databaseId = when (selected) {
                is UnifiedCategoryFilterSelection.MdbxDatabaseFilter -> selected.databaseId
                is UnifiedCategoryFilterSelection.MdbxFolderFilter -> selected.databaseId
                else -> return emptyList()
            }
            val chips = mutableListOf<FolderChipItem>()
            if (selected is UnifiedCategoryFilterSelection.MdbxFolderFilter) {
                chips += FolderChipItem(
                    label = "返回",
                    selection = UnifiedCategoryFilterSelection.MdbxDatabaseFilter(databaseId),
                    isBack = true
                )
            }
            chips += mdbxFolders
                .filter { it.folderId.isNotBlank() }
                .sortedWith(compareBy<MdbxStoredFolderEntry>({ it.parentFolderId ?: "" }, { it.name }, { it.folderId }))
                .map {
                    FolderChipItem(
                        label = it.name.ifBlank { "Folder ${it.folderId.take(8)}" },
                        selection = UnifiedCategoryFilterSelection.MdbxFolderFilter(databaseId, it.folderId)
                    )
                }
            chips
        }
    }
}

private fun buildLocalCategoryNodes(categories: List<Category>): List<ChipMenuLocalCategoryNode> {
    return categories.mapNotNull { category ->
        val normalizedPath = category.name
            .split("/")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("/")
        if (normalizedPath.isBlank()) {
            null
        } else {
            ChipMenuLocalCategoryNode(
                category = category,
                path = normalizedPath,
                parentPath = normalizedPath.substringBeforeLast('/', "").takeIf { it.isNotBlank() },
                displayName = normalizedPath.substringAfterLast('/')
            )
        }
    }.distinctBy(ChipMenuLocalCategoryNode::path)
}

private fun UnifiedCategoryFilterSelection.isMonicaScope(): Boolean = when (this) {
    UnifiedCategoryFilterSelection.Local,
    UnifiedCategoryFilterSelection.Starred,
    UnifiedCategoryFilterSelection.Uncategorized,
    UnifiedCategoryFilterSelection.LocalStarred,
    UnifiedCategoryFilterSelection.LocalUncategorized,
    is UnifiedCategoryFilterSelection.Custom -> true
    else -> false
}

private fun UnifiedCategoryFilterSelection.isKeePassScope(databaseId: Long): Boolean = when (this) {
    is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> this.databaseId == databaseId
    is UnifiedCategoryFilterSelection.KeePassGroupFilter -> this.databaseId == databaseId
    is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> this.databaseId == databaseId
    is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> this.databaseId == databaseId
    else -> false
}

private fun UnifiedCategoryFilterSelection.isMdbxScope(databaseId: Long): Boolean = when (this) {
    is UnifiedCategoryFilterSelection.MdbxDatabaseFilter -> this.databaseId == databaseId
    is UnifiedCategoryFilterSelection.MdbxFolderFilter -> this.databaseId == databaseId
    else -> false
}

private fun UnifiedCategoryFilterSelection.isBitwardenScope(vaultId: Long): Boolean = when (this) {
    is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> this.vaultId == vaultId
    is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> this.vaultId == vaultId
    is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> this.vaultId == vaultId
    is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> this.vaultId == vaultId
    else -> false
}

private fun UnifiedCategoryFilterSelection.isStarredScope(): Boolean = when (this) {
    UnifiedCategoryFilterSelection.Starred,
    UnifiedCategoryFilterSelection.LocalStarred,
    is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter,
    is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> true
    else -> false
}

private fun UnifiedCategoryFilterSelection.isUncategorizedScope(): Boolean = when (this) {
    UnifiedCategoryFilterSelection.Uncategorized,
    UnifiedCategoryFilterSelection.LocalUncategorized,
    is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter,
    is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> true
    else -> false
}

private fun UnifiedCategoryFilterSelection.toStarredSelection(): UnifiedCategoryFilterSelection = when (this) {
    is UnifiedCategoryFilterSelection.KeePassDatabaseFilter,
    is UnifiedCategoryFilterSelection.KeePassGroupFilter,
    is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter,
    is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter ->
        UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter(
            when (this) {
                is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> this.databaseId
                is UnifiedCategoryFilterSelection.KeePassGroupFilter -> this.databaseId
                is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> this.databaseId
                is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> this.databaseId
                else -> error("unreachable")
            }
        )

    is UnifiedCategoryFilterSelection.BitwardenVaultFilter,
    is UnifiedCategoryFilterSelection.BitwardenFolderFilter,
    is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter,
    is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter ->
        UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter(
            when (this) {
                is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> this.vaultId
                is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> this.vaultId
                is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> this.vaultId
                is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> this.vaultId
                else -> error("unreachable")
            }
        )

    UnifiedCategoryFilterSelection.Local,
    UnifiedCategoryFilterSelection.LocalStarred,
    UnifiedCategoryFilterSelection.LocalUncategorized,
    is UnifiedCategoryFilterSelection.Custom -> UnifiedCategoryFilterSelection.LocalStarred

    else -> UnifiedCategoryFilterSelection.Starred
}

private fun UnifiedCategoryFilterSelection.toUncategorizedSelection(): UnifiedCategoryFilterSelection = when (this) {
    is UnifiedCategoryFilterSelection.KeePassDatabaseFilter,
    is UnifiedCategoryFilterSelection.KeePassGroupFilter,
    is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter,
    is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter ->
        UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter(
            when (this) {
                is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> this.databaseId
                is UnifiedCategoryFilterSelection.KeePassGroupFilter -> this.databaseId
                is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> this.databaseId
                is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> this.databaseId
                else -> error("unreachable")
            }
        )

    is UnifiedCategoryFilterSelection.BitwardenVaultFilter,
    is UnifiedCategoryFilterSelection.BitwardenFolderFilter,
    is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter,
    is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter ->
        UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter(
            when (this) {
                is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> this.vaultId
                is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> this.vaultId
                is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> this.vaultId
                is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> this.vaultId
                else -> error("unreachable")
            }
        )

    UnifiedCategoryFilterSelection.Local,
    UnifiedCategoryFilterSelection.LocalStarred,
    UnifiedCategoryFilterSelection.LocalUncategorized,
    is UnifiedCategoryFilterSelection.Custom -> UnifiedCategoryFilterSelection.LocalUncategorized

    else -> UnifiedCategoryFilterSelection.Uncategorized
}

/**
 * 返回当前 scope 的"基础"选择（去掉 Starred / Uncategorized 修饰符）。
 * 用于快捷筛选 chip 的 toggle 行为：已选中时再点一次回到基础 scope。
 */
private fun UnifiedCategoryFilterSelection.toBaseScope(): UnifiedCategoryFilterSelection = when (this) {
    is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter ->
        UnifiedCategoryFilterSelection.KeePassDatabaseFilter(this.databaseId)
    is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter ->
        UnifiedCategoryFilterSelection.KeePassDatabaseFilter(this.databaseId)
    is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter ->
        UnifiedCategoryFilterSelection.BitwardenVaultFilter(this.vaultId)
    is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter ->
        UnifiedCategoryFilterSelection.BitwardenVaultFilter(this.vaultId)
    UnifiedCategoryFilterSelection.LocalStarred,
    UnifiedCategoryFilterSelection.LocalUncategorized -> UnifiedCategoryFilterSelection.Local
    UnifiedCategoryFilterSelection.Starred,
    UnifiedCategoryFilterSelection.Uncategorized -> UnifiedCategoryFilterSelection.All
    else -> this
}
