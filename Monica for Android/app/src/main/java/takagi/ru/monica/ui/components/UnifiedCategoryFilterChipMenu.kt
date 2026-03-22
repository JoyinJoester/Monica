package takagi.ru.monica.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import takagi.ru.monica.R
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.utils.KeePassGroupInfo
import takagi.ru.monica.utils.decodeKeePassPathForDisplay

private data class ChipMenuLocalCategoryNode(
    val category: Category,
    val path: String,
    val parentPath: String?,
    val displayName: String
)

val UnifiedCategoryFilterChipMenuOffset = DpOffset(x = 124.dp, y = 6.dp)
private val UnifiedCategoryFilterChipMenuMinWidth = 280.dp
private val UnifiedCategoryFilterChipMenuMaxWidth = 336.dp
private val UnifiedCategoryFilterChipMenuCompactInset = 72.dp
private val UnifiedCategoryFilterChipMenuShape = RoundedCornerShape(20.dp)

@Composable
fun unifiedCategoryFilterChipMenuModifier(): Modifier {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val resolvedMenuWidth = minOf(
        UnifiedCategoryFilterChipMenuMaxWidth,
        maxOf(
            UnifiedCategoryFilterChipMenuMinWidth,
            screenWidth - UnifiedCategoryFilterChipMenuCompactInset
        )
    )
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
    bitwardenVaults: List<BitwardenVault>,
    getBitwardenFolders: (Long) -> Flow<List<BitwardenFolder>>,
    getKeePassGroups: ((Long) -> Flow<List<KeePassGroupInfo>>)? = null,
    quickFilterContent: (@Composable ColumnScope.() -> Unit)? = null,
    trailingContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    if (!visible) return

    val quickFilterScrollState = rememberScrollState()
    val localNodes = remember(categories) { buildLocalCategoryNodes(categories) }
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
    val bitwardenFolders by remember(selectedVaultId) {
        selectedVaultId?.let(getBitwardenFolders) ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val keepassGroups by remember(selectedKeePassDatabaseId, getKeePassGroups) {
        selectedKeePassDatabaseId?.let { databaseId ->
            getKeePassGroups?.invoke(databaseId)
        } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val folderChips = remember(
        selected,
        localNodes,
        localNodeByPath,
        localCurrentPath,
        bitwardenFolders,
        keepassGroups
    ) {
        buildFolderChips(
            selected = selected,
            localNodes = localNodes,
            localNodeByPath = localNodeByPath,
            localCurrentPath = localCurrentPath,
            bitwardenFolders = bitwardenFolders,
            keepassGroups = keepassGroups
        )
    }
    val quickFilterItems = remember(showLocalOnlyQuickFilter, onSelectLocalOnlyQuickFilter) {
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
    val quickFilterColumns = remember(quickFilterItems) { quickFilterItems.chunked(2) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.category_selection_menu_databases),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                    leadingIcon = Icons.Default.Key
                )
            }
            bitwardenVaults.forEach { vault ->
                MonicaExpressiveFilterChip(
                    selected = selected.isBitwardenScope(vault.id),
                    onClick = { onSelect(UnifiedCategoryFilterSelection.BitwardenVaultFilter(vault.id)) },
                    label = vault.email.ifBlank { "Bitwarden" },
                    leadingIcon = Icons.Default.CloudSync
                )
            }
        }

        if (quickFilterContent != null) {
            quickFilterContent.invoke(this)
        } else {
            Text(
                text = stringResource(R.string.category_selection_menu_quick_filters),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(quickFilterScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickFilterColumns.forEach { columnItems ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        columnItems.forEach { item ->
                            MonicaExpressiveFilterChip(
                                selected = item.isSelected,
                                onClick = {
                                    if (item.selection != null) {
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
        }

        if (folderChips.isNotEmpty()) {
            Text(
                text = stringResource(R.string.category_selection_menu_folders),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                folderChips.forEach { chip ->
                    MonicaExpressiveFilterChip(
                        selected = chip.selection == selected,
                        onClick = {
                            onSelect(chip.selection)
                            onDismiss()
                        },
                        label = chip.label,
                        leadingIcon = if (chip.isBack) {
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft
                        } else {
                            Icons.Default.Folder
                        }
                    )
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

private fun buildFolderChips(
    selected: UnifiedCategoryFilterSelection,
    localNodes: List<ChipMenuLocalCategoryNode>,
    localNodeByPath: Map<String, ChipMenuLocalCategoryNode>,
    localCurrentPath: String?,
    bitwardenFolders: List<BitwardenFolder>,
    keepassGroups: List<KeePassGroupInfo>
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
            val chips = mutableListOf<FolderChipItem>()
            if (selected is UnifiedCategoryFilterSelection.BitwardenFolderFilter) {
                chips += FolderChipItem(
                    label = "返回",
                    selection = UnifiedCategoryFilterSelection.BitwardenVaultFilter(selected.vaultId),
                    isBack = true
                )
            }
            chips += bitwardenFolders
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
            chips
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
