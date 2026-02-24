package takagi.ru.monica.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import takagi.ru.monica.R
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.KeePassStorageLocation
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.utils.KEEPASS_DISPLAY_PATH_SEPARATOR
import takagi.ru.monica.utils.KeePassGroupInfo
import takagi.ru.monica.utils.decodeKeePassPathSegments

sealed interface UnifiedMoveCategoryTarget {
    data object Uncategorized : UnifiedMoveCategoryTarget
    data class MonicaCategory(val categoryId: Long) : UnifiedMoveCategoryTarget
    data class BitwardenVaultTarget(val vaultId: Long) : UnifiedMoveCategoryTarget
    data class BitwardenFolderTarget(val vaultId: Long, val folderId: String) : UnifiedMoveCategoryTarget
    data class KeePassDatabaseTarget(val databaseId: Long) : UnifiedMoveCategoryTarget
    data class KeePassGroupTarget(val databaseId: Long, val groupPath: String) : UnifiedMoveCategoryTarget
}

enum class UnifiedMoveAction {
    MOVE,
    COPY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedMoveToCategoryBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    categories: List<Category>,
    keepassDatabases: List<LocalKeePassDatabase>,
    bitwardenVaults: List<BitwardenVault>,
    getBitwardenFolders: (Long) -> Flow<List<BitwardenFolder>>,
    getKeePassGroups: (Long) -> Flow<List<KeePassGroupInfo>>,
    allowCopy: Boolean = false,
    onTargetSelected: (UnifiedMoveCategoryTarget, UnifiedMoveAction) -> Unit
) {
    if (!visible) return

    val monicaExpanded = remember { mutableStateOf(false) }
    val selectedAction = remember { mutableStateOf(UnifiedMoveAction.MOVE) }
    val bitwardenExpanded = remember { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    val keepassExpanded = remember { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    val expandCollapseSpec = spring<IntSize>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val localKeePassDatabases = keepassDatabases.filterNot { it.isWebDavDatabase() }
    val monicaCategoryNodes = remember(categories) { buildMonicaCategoryNodes(categories) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (selectedAction.value == UnifiedMoveAction.COPY) {
                        stringResource(R.string.copy)
                    } else {
                        stringResource(R.string.move_to_category)
                    },
                    style = MaterialTheme.typography.titleLarge
                )
            }
            if (allowCopy) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    FilterChip(
                        selected = selectedAction.value == UnifiedMoveAction.MOVE,
                        onClick = { selectedAction.value = UnifiedMoveAction.MOVE },
                        label = { Text(text = stringResource(R.string.move)) }
                    )
                    FilterChip(
                        selected = selectedAction.value == UnifiedMoveAction.COPY,
                        onClick = { selectedAction.value = UnifiedMoveAction.COPY },
                        label = { Text(text = stringResource(R.string.copy)) }
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    MoveSectionCard(title = stringResource(R.string.filter_monica)) {
                        val expanded = monicaExpanded.value
                        MoveTargetItem(
                            title = stringResource(R.string.filter_monica),
                            icon = Icons.Default.Smartphone,
                            onClick = { monicaExpanded.value = !expanded },
                            menu = {
                                IconButton(onClick = { monicaExpanded.value = !expanded }) {
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                        AnimatedVisibility(
                            visible = expanded,
                            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = expandCollapseSpec),
                            exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(animationSpec = expandCollapseSpec)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(modifier = Modifier.padding(start = 16.dp)) {
                                    MoveTargetItem(
                                        title = stringResource(R.string.category_none),
                                        icon = Icons.Default.FolderOff,
                                        onClick = {
                                            onTargetSelected(
                                                UnifiedMoveCategoryTarget.Uncategorized,
                                                selectedAction.value
                                            )
                                        }
                                    )
                                }
                                monicaCategoryNodes.forEach { node ->
                                    val indentation = (16 + node.depth * 14).coerceAtMost(72)
                                    Box(modifier = Modifier.padding(start = indentation.dp)) {
                                        MoveTargetItem(
                                            title = node.displayName,
                                            icon = Icons.Default.Folder,
                                            supportingText = node.parentPathLabel,
                                            onClick = {
                                                onTargetSelected(
                                                    UnifiedMoveCategoryTarget.MonicaCategory(node.category.id),
                                                    selectedAction.value
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (bitwardenVaults.isNotEmpty()) {
                    item {
                        MoveSectionCard(title = stringResource(R.string.filter_bitwarden)) {
                            bitwardenVaults.forEach { vault ->
                                val expanded = bitwardenExpanded.value[vault.id] ?: false
                                val folders by (
                                    if (expanded) getBitwardenFolders(vault.id) else flowOf(emptyList())
                                ).collectAsState(initial = emptyList())
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    MoveTargetItem(
                                        title = vault.email,
                                        icon = Icons.Default.CloudSync,
                                        onClick = {
                                            onTargetSelected(
                                                UnifiedMoveCategoryTarget.BitwardenVaultTarget(vault.id),
                                                selectedAction.value
                                            )
                                        },
                                        supportingText = if (vault.isDefault) {
                                            stringResource(R.string.default_label)
                                        } else null,
                                        menu = {
                                            IconButton(
                                                onClick = {
                                                    bitwardenExpanded.value = bitwardenExpanded.value.toMutableMap().apply {
                                                        this[vault.id] = !expanded
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = null
                                                )
                                            }
                                        }
                                    )
                                    AnimatedVisibility(
                                        visible = expanded,
                                        enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = expandCollapseSpec),
                                        exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(animationSpec = expandCollapseSpec)
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            folders.forEach { folder ->
                                                Box(modifier = Modifier.padding(start = 16.dp)) {
                                                    MoveTargetItem(
                                                        title = folder.name,
                                                        icon = Icons.Default.Folder,
                                                        onClick = {
                                                            onTargetSelected(
                                                                UnifiedMoveCategoryTarget.BitwardenFolderTarget(
                                                                    vaultId = vault.id,
                                                                    folderId = folder.bitwardenFolderId
                                                                ),
                                                                selectedAction.value
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (localKeePassDatabases.isNotEmpty()) {
                    item {
                        MoveSectionCard(title = stringResource(R.string.local_keepass_database)) {
                            localKeePassDatabases.forEachIndexed { index, database ->
                                val expanded = keepassExpanded.value[database.id] ?: false
                                val groups by (
                                    if (expanded) getKeePassGroups(database.id) else flowOf(emptyList())
                                ).collectAsState(initial = emptyList())
                                val groupNodes = remember(groups) { buildKeePassGroupNodes(groups) }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    MoveTargetItem(
                                        title = database.name,
                                        icon = Icons.Default.Key,
                                        onClick = {
                                            onTargetSelected(
                                                UnifiedMoveCategoryTarget.KeePassDatabaseTarget(database.id),
                                                selectedAction.value
                                            )
                                        },
                                        supportingText = when {
                                            database.storageLocation == KeePassStorageLocation.EXTERNAL ->
                                                stringResource(R.string.external_storage)
                                            else -> stringResource(R.string.internal_storage)
                                        },
                                        menu = {
                                            IconButton(
                                                onClick = {
                                                    keepassExpanded.value = keepassExpanded.value.toMutableMap().apply {
                                                        this[database.id] = !expanded
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = null
                                                )
                                            }
                                        }
                                    )
                                    AnimatedVisibility(
                                        visible = expanded,
                                        enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = expandCollapseSpec),
                                        exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(animationSpec = expandCollapseSpec)
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            groupNodes.forEach { groupNode ->
                                                val indentation = (16 + groupNode.depth * 14).coerceAtMost(72)
                                                Box(modifier = Modifier.padding(start = indentation.dp)) {
                                                    MoveTargetItem(
                                                        title = groupNode.displayName,
                                                        icon = Icons.Default.Folder,
                                                        supportingText = groupNode.parentPathLabel,
                                                        onClick = {
                                                            onTargetSelected(
                                                                UnifiedMoveCategoryTarget.KeePassGroupTarget(
                                                                    databaseId = database.id,
                                                                    groupPath = groupNode.group.path
                                                                ),
                                                                selectedAction.value
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (index < localKeePassDatabases.lastIndex) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun MoveSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun MoveTargetItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    supportingText: String? = null,
    menu: (@Composable () -> Unit)? = null
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.34f),
            headlineColor = MaterialTheme.colorScheme.onSurface,
            leadingIconColor = MaterialTheme.colorScheme.onSurface,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
            trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = if (supportingText.isNullOrBlank()) {
            null
        } else {
            {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        trailingContent = menu
    )
}

private data class MonicaCategoryNode(
    val category: Category,
    val displayName: String,
    val depth: Int,
    val parentPathLabel: String?
)

private data class KeePassGroupNode(
    val group: KeePassGroupInfo,
    val displayName: String,
    val depth: Int,
    val parentPathLabel: String?
)

private fun buildMonicaCategoryNodes(categories: List<Category>): List<MonicaCategoryNode> {
    return categories
        .sortedBy { it.name.lowercase() }
        .map { category ->
            val segments = splitPathSegments(category.name)
            if (segments.isEmpty()) {
                MonicaCategoryNode(
                    category = category,
                    displayName = category.name,
                    depth = 0,
                    parentPathLabel = null
                )
            } else {
                MonicaCategoryNode(
                    category = category,
                    displayName = segments.last(),
                    depth = (segments.size - 1).coerceAtLeast(0),
                    parentPathLabel = segments.dropLast(1)
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(" / ")
                )
            }
        }
}

private fun buildKeePassGroupNodes(groups: List<KeePassGroupInfo>): List<KeePassGroupNode> {
    return groups
        .sortedBy { it.displayPath.lowercase() }
        .map { group ->
            val pathSegments = decodeKeePassPathSegments(group.path)
            val display = pathSegments.lastOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: group.name.ifBlank { group.displayPath }
            val parentPath = pathSegments
                .dropLast(1)
                .takeIf { it.isNotEmpty() }
                ?.joinToString(KEEPASS_DISPLAY_PATH_SEPARATOR)
            KeePassGroupNode(
                group = group,
                displayName = display.ifBlank { group.path },
                depth = group.depth.coerceAtLeast(0),
                parentPathLabel = parentPath
            )
        }
}

private fun splitPathSegments(path: String): List<String> {
    return path
        .split('/')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}
