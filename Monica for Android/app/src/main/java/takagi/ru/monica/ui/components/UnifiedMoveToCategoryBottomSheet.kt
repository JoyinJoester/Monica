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
import androidx.compose.runtime.mutableStateMapOf
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
import takagi.ru.monica.utils.KeePassGroupInfo

sealed interface UnifiedMoveCategoryTarget {
    data object Uncategorized : UnifiedMoveCategoryTarget
    data class MonicaCategory(val categoryId: Long) : UnifiedMoveCategoryTarget
    data class BitwardenVaultTarget(val vaultId: Long) : UnifiedMoveCategoryTarget
    data class BitwardenFolderTarget(val vaultId: Long, val folderId: String) : UnifiedMoveCategoryTarget
    data class KeePassDatabaseTarget(val databaseId: Long) : UnifiedMoveCategoryTarget
    data class KeePassGroupTarget(val databaseId: Long, val groupPath: String) : UnifiedMoveCategoryTarget
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
    onTargetSelected: (UnifiedMoveCategoryTarget) -> Unit
) {
    if (!visible) return

    var monicaExpanded = remember { androidx.compose.runtime.mutableStateOf(false) }
    val bitwardenExpanded = remember { mutableStateMapOf<Long, Boolean>() }
    val keepassExpanded = remember { mutableStateMapOf<Long, Boolean>() }
    val expandCollapseSpec = spring<IntSize>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val localKeePassDatabases = keepassDatabases.filterNot { it.isWebDavDatabase() }

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
                    text = stringResource(R.string.move_to_category),
                    style = MaterialTheme.typography.titleLarge
                )
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
                                        onClick = { onTargetSelected(UnifiedMoveCategoryTarget.Uncategorized) }
                                    )
                                }
                                categories.forEach { category ->
                                    Box(modifier = Modifier.padding(start = 16.dp)) {
                                        MoveTargetItem(
                                            title = category.name,
                                            icon = Icons.Default.Folder,
                                            onClick = {
                                                onTargetSelected(UnifiedMoveCategoryTarget.MonicaCategory(category.id))
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
                                val expanded = bitwardenExpanded[vault.id] ?: false
                                val folders by (
                                    if (expanded) getBitwardenFolders(vault.id) else flowOf(emptyList())
                                ).collectAsState(initial = emptyList())
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    MoveTargetItem(
                                        title = vault.email,
                                        icon = Icons.Default.CloudSync,
                                        onClick = {
                                            onTargetSelected(UnifiedMoveCategoryTarget.BitwardenVaultTarget(vault.id))
                                        },
                                        badge = if (vault.isDefault) {
                                            {
                                                Text(
                                                    text = stringResource(R.string.default_label),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        menu = {
                                            IconButton(onClick = { bitwardenExpanded[vault.id] = !expanded }) {
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
                                                                )
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
                                val expanded = keepassExpanded[database.id] ?: false
                                val groups by (
                                    if (expanded) getKeePassGroups(database.id) else flowOf(emptyList())
                                ).collectAsState(initial = emptyList())
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    MoveTargetItem(
                                        title = database.name,
                                        icon = Icons.Default.Key,
                                        onClick = {
                                            onTargetSelected(UnifiedMoveCategoryTarget.KeePassDatabaseTarget(database.id))
                                        },
                                        badge = {
                                            Text(
                                                text = when {
                                                    database.storageLocation == KeePassStorageLocation.EXTERNAL ->
                                                        stringResource(R.string.external_storage)
                                                    else -> stringResource(R.string.internal_storage)
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        menu = {
                                            IconButton(onClick = { keepassExpanded[database.id] = !expanded }) {
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
                                            groups.forEach { group ->
                                                Box(modifier = Modifier.padding(start = 16.dp)) {
                                                    MoveTargetItem(
                                                        title = group.name,
                                                        icon = Icons.Default.Folder,
                                                        onClick = {
                                                            onTargetSelected(
                                                                UnifiedMoveCategoryTarget.KeePassGroupTarget(
                                                                    databaseId = database.id,
                                                                    groupPath = group.path
                                                                )
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
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
    badge: (@Composable () -> Unit)? = null,
    menu: (@Composable () -> Unit)? = null
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
            headlineColor = MaterialTheme.colorScheme.onSurface,
            leadingIconColor = MaterialTheme.colorScheme.onSurface,
            trailingIconColor = MaterialTheme.colorScheme.onSurface
        ),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = badge,
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                menu?.invoke()
            }
        }
    )
}
