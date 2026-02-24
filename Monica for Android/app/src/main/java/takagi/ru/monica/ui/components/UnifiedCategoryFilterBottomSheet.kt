package takagi.ru.monica.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.Flow
import takagi.ru.monica.R
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.KeePassStorageLocation
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.utils.KEEPASS_DISPLAY_PATH_SEPARATOR
import takagi.ru.monica.utils.KeePassGroupInfo
import takagi.ru.monica.utils.decodeKeePassPathSegments
import kotlin.math.roundToInt

typealias BiometricVerifyRequester = (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit

sealed interface UnifiedCategoryFilterSelection {
    data object All : UnifiedCategoryFilterSelection
    data object Local : UnifiedCategoryFilterSelection
    data object Starred : UnifiedCategoryFilterSelection
    data object Uncategorized : UnifiedCategoryFilterSelection
    data object LocalStarred : UnifiedCategoryFilterSelection
    data object LocalUncategorized : UnifiedCategoryFilterSelection
    data class Custom(val categoryId: Long) : UnifiedCategoryFilterSelection
    data class BitwardenVaultFilter(val vaultId: Long) : UnifiedCategoryFilterSelection
    data class BitwardenFolderFilter(val vaultId: Long, val folderId: String) : UnifiedCategoryFilterSelection
    data class BitwardenVaultStarredFilter(val vaultId: Long) : UnifiedCategoryFilterSelection
    data class BitwardenVaultUncategorizedFilter(val vaultId: Long) : UnifiedCategoryFilterSelection
    data class KeePassDatabaseFilter(val databaseId: Long) : UnifiedCategoryFilterSelection
    data class KeePassGroupFilter(val databaseId: Long, val groupPath: String) : UnifiedCategoryFilterSelection
    data class KeePassDatabaseStarredFilter(val databaseId: Long) : UnifiedCategoryFilterSelection
    data class KeePassDatabaseUncategorizedFilter(val databaseId: Long) : UnifiedCategoryFilterSelection
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UnifiedCategoryFilterBottomSheet(
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
    onCreateCategory: (() -> Unit)? = null,
    onCreateCategoryWithName: ((String) -> Unit)? = null,
    onCreateBitwardenFolder: ((Long, String) -> Unit)? = null,
    onRenameBitwardenFolder: ((vaultId: Long, folderId: String, newName: String) -> Unit)? = null,
    onDeleteBitwardenFolder: ((vaultId: Long, folderId: String) -> Unit)? = null,
    onVerifyMasterPassword: ((String) -> Boolean)? = null,
    onRenameCategory: ((Category) -> Unit)? = null,
    onDeleteCategory: ((Category) -> Unit)? = null,
    onRequestBiometricVerify: BiometricVerifyRequester? = null,
    onCreateKeePassGroup: ((databaseId: Long, parentPath: String?, name: String) -> Unit)? = null,
    onRenameKeePassGroup: ((databaseId: Long, groupPath: String, newName: String) -> Unit)? = null,
    onDeleteKeePassGroup: ((databaseId: Long, groupPath: String) -> Unit)? = null
) {
    if (!visible) return

    val context = LocalContext.current
    var expandedMenuId by remember { mutableStateOf<Long?>(null) }
    var monicaExpanded by remember { mutableStateOf(false) }
    val bitwardenExpanded = remember { mutableStateMapOf<Long, Boolean>() }
    val keepassExpanded = remember { mutableStateMapOf<Long, Boolean>() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createNameInput by remember { mutableStateOf("") }
    var createTarget by remember { mutableStateOf(CreateTarget.Local) }
    var createLocalParentPath by remember { mutableStateOf<String?>(null) }
    var createKeePassParentPath by remember { mutableStateOf<String?>(null) }
    var selectedCreateVaultId by remember { mutableStateOf<Long?>(null) }
    var selectedCreateKeePassDbId by remember { mutableStateOf<Long?>(null) }
    var createOptionsExpanded by remember { mutableStateOf(true) }
    var renameAction by remember { mutableStateOf<RenameAction?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var localCategoryRenameMode by remember { mutableStateOf(LocalCategoryRenameMode.LeafOnly) }
    var deleteAction by remember { mutableStateOf<DeleteAction?>(null) }
    var deletePasswordInput by remember { mutableStateOf("") }
    var deletePasswordError by remember { mutableStateOf(false) }
    val expandCollapseSpec = spring<IntSize>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    LaunchedEffect(bitwardenVaults) {
        if (selectedCreateVaultId == null) {
            selectedCreateVaultId = bitwardenVaults.firstOrNull()?.id
        }
    }
    LaunchedEffect(keepassDatabases) {
        val preferredId = keepassDatabases.firstOrNull { !it.isWebDavDatabase() }?.id
            ?: keepassDatabases.firstOrNull()?.id
        val isCurrentValid = keepassDatabases.any { it.id == selectedCreateKeePassDbId }
        if (!isCurrentValid) {
            selectedCreateKeePassDbId = preferredId
        } else if (
            selectedCreateKeePassDbId != null &&
            keepassDatabases.firstOrNull { it.id == selectedCreateKeePassDbId }?.isWebDavDatabase() == true &&
            preferredId != null
        ) {
            selectedCreateKeePassDbId = preferredId
        }
    }

    LaunchedEffect(selected) {
        when (selected) {
            is UnifiedCategoryFilterSelection.Custom,
            is UnifiedCategoryFilterSelection.LocalStarred,
            is UnifiedCategoryFilterSelection.LocalUncategorized -> monicaExpanded = true
            is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> {
                bitwardenExpanded[selected.vaultId] = true
            }
            is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> {
                bitwardenExpanded[selected.vaultId] = true
            }
            is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> {
                bitwardenExpanded[selected.vaultId] = true
            }
            is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> {
                keepassExpanded[selected.databaseId] = true
            }
            is UnifiedCategoryFilterSelection.KeePassGroupFilter -> {
                keepassExpanded[selected.databaseId] = true
            }
            is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> {
                keepassExpanded[selected.databaseId] = true
            }
            is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> {
                keepassExpanded[selected.databaseId] = true
            }
            else -> Unit
        }
    }

    val canCreateLocal = onCreateCategoryWithName != null || onCreateCategory != null
    val canCreateBitwarden = onCreateBitwardenFolder != null && bitwardenVaults.isNotEmpty()
    val canCreateKeePass = onCreateKeePassGroup != null && keepassDatabases.any { !it.isWebDavDatabase() }
    val localKeePassDatabases = keepassDatabases.filterNot { it.isWebDavDatabase() }
    val localCategoryNodes = remember(categories) { buildLocalCategoryNodes(categories) }

    @Composable
    fun KeePassDatabaseItems(databases: List<LocalKeePassDatabase>, forceWebDavBadge: Boolean) {
        databases.forEach { database ->
            val expanded = keepassExpanded[database.id] ?: false
            val groups by (
                if (expanded) {
                    getKeePassGroups?.invoke(database.id)
                        ?: kotlinx.coroutines.flow.flowOf(emptyList())
                } else {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                }
            ).collectAsState(initial = emptyList())
            Column {
                UnifiedCategoryListItem(
                    title = database.name,
                    icon = Icons.Default.Key,
                    selected = (
                        selected is UnifiedCategoryFilterSelection.KeePassDatabaseFilter &&
                            selected.databaseId == database.id
                        ) || (
                        selected is UnifiedCategoryFilterSelection.KeePassGroupFilter &&
                            selected.databaseId == database.id
                        ) || (
                        selected is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter &&
                            selected.databaseId == database.id
                        ) || (
                        selected is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter &&
                            selected.databaseId == database.id
                        ),
                    onClick = { onSelect(UnifiedCategoryFilterSelection.KeePassDatabaseFilter(database.id)) },
                    badge = {
                        Text(
                            text = when {
                                forceWebDavBadge || database.isWebDavDatabase() -> stringResource(R.string.keepass_webdav_database_badge)
                                database.storageLocation == KeePassStorageLocation.EXTERNAL -> stringResource(R.string.external_storage)
                                else -> stringResource(R.string.internal_storage)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    menu = {
                        IconButton(onClick = {
                            keepassExpanded[database.id] = !expanded
                        }) {
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
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.padding(start = 16.dp)) {
                            UnifiedCategoryListItem(
                                title = stringResource(R.string.filter_starred),
                                icon = Icons.Outlined.CheckCircle,
                                selected = selected is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter &&
                                    selected.databaseId == database.id,
                                onClick = {
                                    onSelect(
                                        UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter(
                                            database.id
                                        )
                                    )
                                }
                            )
                        }
                        Box(modifier = Modifier.padding(start = 16.dp)) {
                            UnifiedCategoryListItem(
                                title = stringResource(R.string.filter_uncategorized),
                                icon = Icons.Default.FolderOff,
                                selected = selected is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter &&
                                    selected.databaseId == database.id,
                                onClick = {
                                    onSelect(
                                        UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter(
                                            database.id
                                        )
                                    )
                                }
                            )
                        }
                        groups.forEach { group ->
                            val depth = group.depth.coerceAtLeast(0)
                            val groupSelected = selected is UnifiedCategoryFilterSelection.KeePassGroupFilter &&
                                selected.databaseId == database.id &&
                                selected.groupPath == group.path
                            val parentPathLabel = decodeKeePassPathSegments(group.path)
                                .dropLast(1)
                                .takeIf { it.isNotEmpty() }
                                ?.joinToString(KEEPASS_DISPLAY_PATH_SEPARATOR)
                            HierarchyIndentedItem(depth = depth) {
                                UnifiedCategoryListItem(
                                    title = buildHierarchyDisplayTitle(group.name, depth),
                                    icon = Icons.Default.Folder,
                                    selected = groupSelected,
                                    onClick = {
                                        onSelect(
                                            UnifiedCategoryFilterSelection.KeePassGroupFilter(
                                                databaseId = database.id,
                                                groupPath = group.path
                                            )
                                        )
                                    },
                                    menu = if (onRenameKeePassGroup != null || onDeleteKeePassGroup != null) {
                                        {
                                            IconButton(onClick = { expandedMenuId = database.id * 1_000_000 + group.path.hashCode().toLong() }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = null)
                                            }
                                            val menuId = database.id * 1_000_000 + group.path.hashCode().toLong()
                                            DropdownMenu(
                                                expanded = expandedMenuId == menuId,
                                                onDismissRequest = { expandedMenuId = null },
                                                modifier = Modifier.clip(RoundedCornerShape(18.dp))
                                            ) {
                                                if (onRenameKeePassGroup != null) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.edit)) },
                                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                        onClick = {
                                                            expandedMenuId = null
                                                            renameInput = group.name
                                                            renameAction = RenameAction.KeePassGroup(database.id, group.path)
                                                        }
                                                    )
                                                }
                                                if (onDeleteKeePassGroup != null) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.delete)) },
                                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                                        onClick = {
                                                            expandedMenuId = null
                                                            deletePasswordInput = ""
                                                            deletePasswordError = false
                                                            deleteAction = DeleteAction.KeePassGroup(
                                                                databaseId = database.id,
                                                                groupPath = group.path,
                                                                displayName = group.displayPath
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                    badge = if (!parentPathLabel.isNullOrBlank()) {
                                        {
                                            Text(
                                                text = parentPathLabel,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.ledger_select_category),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickFilterChip(
                                label = stringResource(R.string.category_all),
                                icon = Icons.Default.List,
                                selected = selected is UnifiedCategoryFilterSelection.All,
                                onClick = { onSelect(UnifiedCategoryFilterSelection.All) }
                            )
                            QuickFilterChip(
                                label = stringResource(R.string.filter_starred),
                                icon = Icons.Outlined.CheckCircle,
                                selected = selected is UnifiedCategoryFilterSelection.Starred,
                                onClick = { onSelect(UnifiedCategoryFilterSelection.Starred) }
                            )
                            QuickFilterChip(
                                label = stringResource(R.string.filter_uncategorized),
                                icon = Icons.Default.FolderOff,
                                selected = selected is UnifiedCategoryFilterSelection.Uncategorized,
                                onClick = { onSelect(UnifiedCategoryFilterSelection.Uncategorized) }
                            )
                            if (showLocalOnlyQuickFilter && onSelectLocalOnlyQuickFilter != null) {
                                QuickFilterChip(
                                    label = stringResource(R.string.filter_local_only),
                                    icon = Icons.Default.Smartphone,
                                    selected = isLocalOnlyQuickFilterSelected,
                                    onClick = onSelectLocalOnlyQuickFilter
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(14.dp))
                }

                item {
                    StorageSectionCard(title = stringResource(R.string.filter_monica)) {
                        UnifiedCategoryListItem(
                            title = stringResource(R.string.filter_monica),
                            icon = Icons.Default.Smartphone,
                            selected = selected is UnifiedCategoryFilterSelection.Local,
                            onClick = { onSelect(UnifiedCategoryFilterSelection.Local) },
                            menu = {
                                IconButton(onClick = { monicaExpanded = !monicaExpanded }) {
                                    Icon(
                                        imageVector = if (monicaExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                        AnimatedVisibility(
                            visible = monicaExpanded,
                            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = expandCollapseSpec),
                            exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(animationSpec = expandCollapseSpec)
                        ) {
                            Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.padding(start = 16.dp)) {
                                UnifiedCategoryListItem(
                                    title = stringResource(R.string.filter_starred),
                                    icon = Icons.Outlined.CheckCircle,
                                    selected = selected is UnifiedCategoryFilterSelection.LocalStarred,
                                    onClick = { onSelect(UnifiedCategoryFilterSelection.LocalStarred) }
                                )
                            }
                            Box(modifier = Modifier.padding(start = 16.dp)) {
                                UnifiedCategoryListItem(
                                    title = stringResource(R.string.filter_uncategorized),
                                    icon = Icons.Default.FolderOff,
                                    selected = selected is UnifiedCategoryFilterSelection.LocalUncategorized,
                                    onClick = { onSelect(UnifiedCategoryFilterSelection.LocalUncategorized) }
                                )
                            }
                            localCategoryNodes.forEach { node ->
                                val category = node.category
                                val isSelected = selected is UnifiedCategoryFilterSelection.Custom &&
                                    selected.categoryId == category.id
                                HierarchyIndentedItem(depth = node.depth) {
                                    UnifiedCategoryListItem(
                                        title = buildHierarchyDisplayTitle(node.displayName, node.depth),
                                        icon = Icons.Default.Folder,
                                        selected = isSelected,
                                        onClick = { onSelect(UnifiedCategoryFilterSelection.Custom(category.id)) },
                                        badge = if (!node.parentPathLabel.isNullOrBlank()) {
                                            {
                                                Text(
                                                    text = node.parentPathLabel,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        menu = if (canCreateLocal || onRenameCategory != null || onDeleteCategory != null) {
                                            {
                                                IconButton(onClick = { expandedMenuId = category.id }) {
                                                    Icon(Icons.Default.MoreVert, contentDescription = null)
                                                }
                                                DropdownMenu(
                                                    expanded = expandedMenuId == category.id,
                                                    onDismissRequest = { expandedMenuId = null },
                                                    modifier = Modifier.clip(RoundedCornerShape(18.dp))
                                                ) {
                                                    if (canCreateLocal) {
                                                        DropdownMenuItem(
                                                            text = { Text(stringResource(R.string.new_category)) },
                                                            leadingIcon = {
                                                                Icon(
                                                                    Icons.Default.Add,
                                                                    contentDescription = null
                                                                )
                                                            },
                                                            onClick = {
                                                                expandedMenuId = null
                                                                createNameInput = ""
                                                                createTarget = CreateTarget.Local
                                                                createLocalParentPath = node.fullPath
                                                                showCreateDialog = true
                                                            }
                                                        )
                                                    }
                                                    if (onRenameCategory != null) {
                                                        DropdownMenuItem(
                                                            text = { Text(stringResource(R.string.edit)) },
                                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                            onClick = {
                                                                expandedMenuId = null
                                                                localCategoryRenameMode = LocalCategoryRenameMode.LeafOnly
                                                                renameInput = getLocalCategoryLeafName(category.name)
                                                                renameAction = RenameAction.LocalCategory(category)
                                                            }
                                                        )
                                                    }
                                                    if (onDeleteCategory != null) {
                                                        DropdownMenuItem(
                                                            text = { Text(stringResource(R.string.delete)) },
                                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                                            onClick = {
                                                                expandedMenuId = null
                                                                deletePasswordInput = ""
                                                                deletePasswordError = false
                                                                deleteAction = DeleteAction.LocalCategory(category)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            null
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
                        StorageSectionCard(title = stringResource(R.string.filter_bitwarden)) {
                            bitwardenVaults.forEach { vault ->
                                val expanded = bitwardenExpanded[vault.id] ?: false
                                val folders by getBitwardenFolders(vault.id).collectAsState(initial = emptyList())
                                Column {
                                    UnifiedCategoryListItem(
                                        title = vault.email,
                                        icon = Icons.Default.CloudSync,
                                        selected = (
                                            selected is UnifiedCategoryFilterSelection.BitwardenVaultFilter &&
                                                selected.vaultId == vault.id
                                            ) || (
                                            selected is UnifiedCategoryFilterSelection.BitwardenFolderFilter &&
                                                selected.vaultId == vault.id
                                            ) || (
                                            selected is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter &&
                                                selected.vaultId == vault.id
                                            ) || (
                                            selected is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter &&
                                                selected.vaultId == vault.id
                                            ),
                                        onClick = { onSelect(UnifiedCategoryFilterSelection.BitwardenVaultFilter(vault.id)) },
                                        badge = {
                                            if (vault.isDefault) {
                                                Text(
                                                    text = stringResource(R.string.default_label),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        menu = {
                                            IconButton(onClick = {
                                                bitwardenExpanded[vault.id] = !expanded
                                            }) {
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
                                        Column {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(modifier = Modifier.padding(start = 16.dp)) {
                                            UnifiedCategoryListItem(
                                                title = stringResource(R.string.filter_starred),
                                                icon = Icons.Outlined.CheckCircle,
                                                selected = selected is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter &&
                                                    selected.vaultId == vault.id,
                                                onClick = {
                                                    onSelect(
                                                        UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter(
                                                            vault.id
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                        Box(modifier = Modifier.padding(start = 16.dp)) {
                                            UnifiedCategoryListItem(
                                                title = stringResource(R.string.filter_uncategorized),
                                                icon = Icons.Default.FolderOff,
                                                selected = selected is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter &&
                                                    selected.vaultId == vault.id,
                                                onClick = {
                                                    onSelect(
                                                        UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter(
                                                            vault.id
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                        folders.forEach { folder ->
                                            val folderSelected = selected is UnifiedCategoryFilterSelection.BitwardenFolderFilter &&
                                                selected.folderId == folder.bitwardenFolderId &&
                                                selected.vaultId == vault.id
                                            Box(modifier = Modifier.padding(start = 16.dp)) {
                                                UnifiedCategoryListItem(
                                                    title = folder.name,
                                                    icon = Icons.Default.Folder,
                                                    selected = folderSelected,
                                                    onClick = {
                                                        onSelect(
                                                            UnifiedCategoryFilterSelection.BitwardenFolderFilter(
                                                                vault.id,
                                                                folder.bitwardenFolderId
                                                            )
                                                        )
                                                    },
                                                    menu = if (onRenameBitwardenFolder != null || onDeleteBitwardenFolder != null) {
                                                        {
                                                            IconButton(onClick = { expandedMenuId = folder.id }) {
                                                                Icon(Icons.Default.MoreVert, contentDescription = null)
                                                            }
                                                            DropdownMenu(
                                                                expanded = expandedMenuId == folder.id,
                                                                onDismissRequest = { expandedMenuId = null },
                                                                modifier = Modifier.clip(RoundedCornerShape(18.dp))
                                                            ) {
                                                                if (onRenameBitwardenFolder != null) {
                                                                    DropdownMenuItem(
                                                                        text = { Text(stringResource(R.string.edit)) },
                                                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                                        onClick = {
                                                                            expandedMenuId = null
                                                                            renameInput = folder.name
                                                                            renameAction = RenameAction.BitwardenFolder(vault.id, folder.bitwardenFolderId)
                                                                        }
                                                                    )
                                                                }
                                                                if (onDeleteBitwardenFolder != null) {
                                                                    DropdownMenuItem(
                                                                        text = { Text(stringResource(R.string.delete)) },
                                                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                                                        onClick = {
                                                                            expandedMenuId = null
                                                                            deletePasswordInput = ""
                                                                            deletePasswordError = false
                                                                            deleteAction = DeleteAction.BitwardenFolder(
                                                                                vaultId = vault.id,
                                                                                folderId = folder.bitwardenFolderId,
                                                                                displayName = folder.name
                                                                            )
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        null
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
                        StorageSectionCard(title = stringResource(R.string.local_keepass_database)) {
                            KeePassDatabaseItems(localKeePassDatabases, forceWebDavBadge = false)
                        }
                    }
                }

                if (canCreateLocal || canCreateBitwarden || canCreateKeePass) {
                    item {
                        FilledTonalButton(
                            onClick = {
                                createNameInput = ""
                                createLocalParentPath = null
                                createKeePassParentPath = null
                                createTarget = when {
                                    canCreateLocal -> CreateTarget.Local
                                    canCreateBitwarden -> CreateTarget.Bitwarden
                                    canCreateKeePass -> CreateTarget.KeePass
                                    else -> CreateTarget.Local
                                }
                                createOptionsExpanded = true
                                showCreateDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.new_category))
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        val createTargetScroll = rememberScrollState()
        val createVaultScroll = rememberScrollState()
        val createLocalParentScroll = rememberScrollState()
        val createKeePassDbScroll = rememberScrollState()
        val createKeePassParentScroll = rememberScrollState()
        val createKeePassGroups by (
            if (
                createTarget == CreateTarget.KeePass &&
                selectedCreateKeePassDbId != null &&
                getKeePassGroups != null
            ) {
                getKeePassGroups.invoke(selectedCreateKeePassDbId!!)
            } else {
                kotlinx.coroutines.flow.flowOf(emptyList())
            }
        ).collectAsState(initial = emptyList())
        val sortedCreateKeePassGroups = remember(createKeePassGroups) {
            createKeePassGroups.sortedBy { it.displayPath.lowercase() }
        }

        LaunchedEffect(createTarget, sortedCreateKeePassGroups, createKeePassParentPath) {
            if (createTarget != CreateTarget.KeePass) return@LaunchedEffect
            val currentParent = createKeePassParentPath ?: return@LaunchedEffect
            if (sortedCreateKeePassGroups.none { it.path == currentParent }) {
                createKeePassParentPath = null
            }
        }

        val selectedCreateKeePassParentDisplay = sortedCreateKeePassGroups
            .firstOrNull { it.path == createKeePassParentPath }
            ?.displayPath
        val localPreviewPath = buildNestedLocalCategoryPath(createLocalParentPath, createNameInput)
        val keepassPreviewPath = if (createNameInput.trim().isBlank()) {
            ""
        } else {
            val parentDisplay = selectedCreateKeePassParentDisplay.orEmpty()
            if (parentDisplay.isBlank()) {
                createNameInput.trim()
            } else {
                "$parentDisplay$KEEPASS_DISPLAY_PATH_SEPARATOR${createNameInput.trim()}"
            }
        }
        val previewPath = when (createTarget) {
            CreateTarget.Local -> localPreviewPath
            CreateTarget.Bitwarden -> createNameInput.trim()
            CreateTarget.KeePass -> keepassPreviewPath
        }
        val targetLabel = when (createTarget) {
            CreateTarget.Local -> stringResource(R.string.create_target_local)
            CreateTarget.Bitwarden -> stringResource(R.string.create_target_bitwarden)
            CreateTarget.KeePass -> stringResource(R.string.create_target_keepass)
        }
        val targetIcon = when (createTarget) {
            CreateTarget.Local -> Icons.Default.Smartphone
            CreateTarget.Bitwarden -> Icons.Default.CloudSync
            CreateTarget.KeePass -> Icons.Default.Key
        }
        val targetTint = when (createTarget) {
            CreateTarget.Local -> MaterialTheme.colorScheme.primary
            CreateTarget.Bitwarden -> MaterialTheme.colorScheme.secondary
            CreateTarget.KeePass -> MaterialTheme.colorScheme.tertiary
        }
        val createChipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
        val canSubmit = when (createTarget) {
            CreateTarget.Local -> canCreateLocal
            CreateTarget.Bitwarden -> canCreateBitwarden && selectedCreateVaultId != null
            CreateTarget.KeePass -> canCreateKeePass && selectedCreateKeePassDbId != null
        } && createNameInput.trim().isNotBlank()

        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                createLocalParentPath = null
                createKeePassParentPath = null
            },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = targetTint.copy(alpha = 0.18f)
                        ) {
                            Icon(
                                imageVector = targetIcon,
                                contentDescription = null,
                                tint = targetTint,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.create_folder_dialog_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = stringResource(R.string.create_folder_dialog_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = targetTint.copy(alpha = 0.14f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = targetTint.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.create_target_section_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = targetIcon,
                                    contentDescription = null,
                                    tint = targetTint,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = targetLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = targetTint
                                )
                            }
                        }
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.create_target_section_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(createTargetScroll),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (canCreateLocal) {
                                    FilterChip(
                                        selected = createTarget == CreateTarget.Local,
                                        onClick = {
                                            createTarget = CreateTarget.Local
                                            createKeePassParentPath = null
                                        },
                                        modifier = Modifier.height(38.dp),
                                        colors = createChipColors,
                                        label = { Text(stringResource(R.string.create_target_local)) },
                                        leadingIcon = { Icon(Icons.Default.Smartphone, contentDescription = null) }
                                    )
                                }
                                if (canCreateBitwarden) {
                                    FilterChip(
                                        selected = createTarget == CreateTarget.Bitwarden,
                                        onClick = {
                                            createTarget = CreateTarget.Bitwarden
                                            createLocalParentPath = null
                                            createKeePassParentPath = null
                                        },
                                        modifier = Modifier.height(38.dp),
                                        colors = createChipColors,
                                        label = { Text(stringResource(R.string.create_target_bitwarden)) },
                                        leadingIcon = { Icon(Icons.Default.CloudSync, contentDescription = null) }
                                    )
                                }
                                if (canCreateKeePass) {
                                    FilterChip(
                                        selected = createTarget == CreateTarget.KeePass,
                                        onClick = {
                                            createTarget = CreateTarget.KeePass
                                            createLocalParentPath = null
                                        },
                                        modifier = Modifier.height(38.dp),
                                        colors = createChipColors,
                                        label = { Text(stringResource(R.string.create_target_keepass)) },
                                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) }
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { createOptionsExpanded = !createOptionsExpanded }
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.create_nested_section_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = if (createOptionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AnimatedVisibility(
                                visible = createOptionsExpanded,
                                enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = expandCollapseSpec),
                                exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(animationSpec = expandCollapseSpec)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    when (createTarget) {
                                        CreateTarget.Local -> {
                                            Text(
                                                text = stringResource(R.string.create_select_local_parent),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(createLocalParentScroll),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                FilterChip(
                                                    selected = createLocalParentPath.isNullOrBlank(),
                                                    onClick = { createLocalParentPath = null },
                                                    colors = createChipColors,
                                                    label = { Text(stringResource(R.string.folder_no_folder_root)) },
                                                    leadingIcon = { Icon(Icons.Default.FolderOff, contentDescription = null) }
                                                )
                                                localCategoryNodes.forEach { node ->
                                                    FilterChip(
                                                        selected = createLocalParentPath == node.fullPath,
                                                        onClick = { createLocalParentPath = node.fullPath },
                                                        colors = createChipColors,
                                                        label = {
                                                            Text(
                                                                text = node.fullPath,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.widthIn(max = 180.dp)
                                                            )
                                                        },
                                                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
                                                    )
                                                }
                                            }
                                            if (!createLocalParentPath.isNullOrBlank()) {
                                                Text(
                                                    text = stringResource(
                                                        R.string.category_parent_path_hint,
                                                        createLocalParentPath.orEmpty()
                                                    ),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        CreateTarget.Bitwarden -> {
                                            if (canCreateBitwarden) {
                                                Text(
                                                    text = stringResource(R.string.create_select_bitwarden_vault),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(createVaultScroll),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    bitwardenVaults.forEach { vault ->
                                                        FilterChip(
                                                            selected = selectedCreateVaultId == vault.id,
                                                            onClick = { selectedCreateVaultId = vault.id },
                                                            colors = createChipColors,
                                                            label = {
                                                                Text(
                                                                    text = vault.email,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    modifier = Modifier.widthIn(max = 200.dp)
                                                                )
                                                            },
                                                            leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null) }
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = stringResource(R.string.bitwarden_folder_flat_hint),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        CreateTarget.KeePass -> {
                                            if (canCreateKeePass) {
                                                Text(
                                                    text = stringResource(R.string.create_select_keepass_database),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(createKeePassDbScroll),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    localKeePassDatabases.forEach { db ->
                                                        FilterChip(
                                                            selected = selectedCreateKeePassDbId == db.id,
                                                            onClick = {
                                                                selectedCreateKeePassDbId = db.id
                                                                createKeePassParentPath = null
                                                            },
                                                            colors = createChipColors,
                                                            label = {
                                                                Text(
                                                                    text = db.name,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    modifier = Modifier.widthIn(max = 180.dp)
                                                                )
                                                            },
                                                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) }
                                                        )
                                                    }
                                                }

                                                Text(
                                                    text = stringResource(R.string.create_select_keepass_parent_group),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(createKeePassParentScroll),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    FilterChip(
                                                        selected = createKeePassParentPath.isNullOrBlank(),
                                                        onClick = { createKeePassParentPath = null },
                                                        colors = createChipColors,
                                                        label = { Text(stringResource(R.string.folder_no_folder_root)) },
                                                        leadingIcon = { Icon(Icons.Default.FolderOff, contentDescription = null) }
                                                    )
                                                    sortedCreateKeePassGroups.forEach { group ->
                                                        FilterChip(
                                                            selected = createKeePassParentPath == group.path,
                                                            onClick = { createKeePassParentPath = group.path },
                                                            colors = createChipColors,
                                                            label = {
                                                                Text(
                                                                    text = group.displayPath,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    modifier = Modifier.widthIn(max = 220.dp)
                                                                )
                                                            },
                                                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
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

                    if (previewPath.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.create_preview_path_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = previewPath,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = createNameInput,
                        onValueChange = { createNameInput = it },
                        label = { Text(stringResource(R.string.folder_name_label)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(
                    enabled = canSubmit,
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        val name = createNameInput.trim()
                        if (name.isBlank()) return@FilledTonalButton
                        when (createTarget) {
                            CreateTarget.Local -> {
                                val normalizedName = buildNestedLocalCategoryPath(
                                    createLocalParentPath,
                                    name
                                )
                                if (normalizedName.isBlank()) return@FilledTonalButton
                                if (onCreateCategoryWithName != null) {
                                    onCreateCategoryWithName(normalizedName)
                                } else {
                                    onCreateCategory?.invoke()
                                }
                            }
                            CreateTarget.Bitwarden -> {
                                val vaultId = selectedCreateVaultId
                                if (vaultId != null) {
                                    onCreateBitwardenFolder?.invoke(vaultId, name)
                                }
                            }
                            CreateTarget.KeePass -> {
                                val dbId = selectedCreateKeePassDbId
                                if (dbId != null) {
                                    onCreateKeePassGroup?.invoke(dbId, createKeePassParentPath, name)
                                }
                            }
                        }
                        showCreateDialog = false
                        createLocalParentPath = null
                        createKeePassParentPath = null
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.padding(end = 4.dp),
                    onClick = {
                    showCreateDialog = false
                    createLocalParentPath = null
                    createKeePassParentPath = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (renameAction != null) {
        val target = renameAction!!
        val localCategory = (target as? RenameAction.LocalCategory)?.category
        val localCategoryParentPath = localCategory?.name?.let(::getLocalCategoryParentPath)
        val canEditFullPath = !localCategoryParentPath.isNullOrBlank()
        val titleRes = if (target is RenameAction.LocalCategory) {
            R.string.edit_category
        } else {
            R.string.folder_edit
        }
        val labelRes = if (target is RenameAction.LocalCategory) {
            R.string.category_name
        } else {
            R.string.folder_name_label
        }
        AlertDialog(
            onDismissRequest = { renameAction = null },
            title = { Text(stringResource(titleRes)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (canEditFullPath) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = localCategoryRenameMode == LocalCategoryRenameMode.LeafOnly,
                                onClick = {
                                    localCategoryRenameMode = LocalCategoryRenameMode.LeafOnly
                                    renameInput = getLocalCategoryLeafName(localCategory?.name.orEmpty())
                                },
                                label = { Text(stringResource(R.string.category_rename_mode_leaf_only)) }
                            )
                            FilterChip(
                                selected = localCategoryRenameMode == LocalCategoryRenameMode.FullPath,
                                onClick = {
                                    localCategoryRenameMode = LocalCategoryRenameMode.FullPath
                                    renameInput = localCategory?.name.orEmpty()
                                },
                                label = { Text(stringResource(R.string.category_rename_mode_full_path)) }
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.category_parent_path_hint,
                                localCategoryParentPath.orEmpty()
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        label = { Text(stringResource(labelRes)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = renameInput.trim()
                        if (newName.isBlank()) return@TextButton
                        when (target) {
                            is RenameAction.LocalCategory -> {
                                val resolvedName = when (localCategoryRenameMode) {
                                    LocalCategoryRenameMode.LeafOnly -> {
                                        buildNestedLocalCategoryPath(localCategoryParentPath, newName)
                                    }
                                    LocalCategoryRenameMode.FullPath -> {
                                        buildNestedLocalCategoryPath(null, newName)
                                    }
                                }
                                if (resolvedName.isBlank()) return@TextButton
                                onRenameCategory?.invoke(target.category.copy(name = resolvedName))
                            }
                            is RenameAction.BitwardenFolder -> onRenameBitwardenFolder?.invoke(
                                target.vaultId,
                                target.folderId,
                                newName
                            )
                            is RenameAction.KeePassGroup -> onRenameKeePassGroup?.invoke(
                                target.databaseId,
                                target.groupPath,
                                newName
                            )
                        }
                        renameAction = null
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameAction = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (deleteAction != null) {
        val action = deleteAction!!
        val displayType = stringResource(R.string.folder_generic)
        val performDeleteAction = {
            when (action) {
                is DeleteAction.LocalCategory -> onDeleteCategory?.invoke(action.category)
                is DeleteAction.BitwardenFolder -> onDeleteBitwardenFolder?.invoke(action.vaultId, action.folderId)
                is DeleteAction.KeePassGroup -> onDeleteKeePassGroup?.invoke(action.databaseId, action.groupPath)
            }
            deleteAction = null
            deletePasswordInput = ""
            deletePasswordError = false
        }
        val biometricAction = onRequestBiometricVerify?.let { request ->
            {
                request(
                    {
                        performDeleteAction()
                    },
                    { error ->
                        android.widget.Toast.makeText(
                            context,
                            error,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
        M3IdentityVerifyDialog(
            title = stringResource(R.string.delete_item_title, displayType),
            message = stringResource(R.string.delete_item_message, displayType, action.displayName),
            passwordValue = deletePasswordInput,
            onPasswordChange = {
                deletePasswordInput = it
                deletePasswordError = false
            },
            onDismiss = {
                deleteAction = null
                deletePasswordInput = ""
                deletePasswordError = false
            },
            onConfirm = {
                val verifier = onVerifyMasterPassword
                val verified = verifier?.invoke(deletePasswordInput) ?: true
                if (!verified) {
                    deletePasswordError = true
                    return@M3IdentityVerifyDialog
                }
                performDeleteAction()
            },
            confirmText = stringResource(R.string.delete),
            destructiveConfirm = true,
            isPasswordError = deletePasswordError,
            passwordErrorText = stringResource(R.string.current_password_incorrect),
            showBiometricSlot = true,
            onBiometricClick = biometricAction,
            biometricHintText = if (biometricAction == null) {
                stringResource(R.string.biometric_not_available)
            } else {
                null
            }
        )
    }
}

@Composable
private fun rememberCategoryFilterLabel(
    selected: UnifiedCategoryFilterSelection,
    categories: List<Category>,
    keepassDatabases: List<LocalKeePassDatabase>,
    bitwardenVaults: List<BitwardenVault>
): String {
    val monica = stringResource(R.string.filter_monica)
    val bitwarden = stringResource(R.string.filter_bitwarden)
    val keepass = stringResource(R.string.filter_keepass)
    val starred = stringResource(R.string.filter_starred)
    val uncategorized = stringResource(R.string.filter_uncategorized)
    return when (selected) {
        UnifiedCategoryFilterSelection.All -> stringResource(R.string.category_all)
        UnifiedCategoryFilterSelection.Local -> monica
        UnifiedCategoryFilterSelection.Starred -> starred
        UnifiedCategoryFilterSelection.Uncategorized -> uncategorized
        UnifiedCategoryFilterSelection.LocalStarred -> "$monica · $starred"
        UnifiedCategoryFilterSelection.LocalUncategorized -> "$monica · $uncategorized"
        is UnifiedCategoryFilterSelection.Custom -> categories.find { it.id == selected.categoryId }?.name
            ?: stringResource(R.string.unknown_category)
        is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> bitwardenVaults.find { it.id == selected.vaultId }?.email
            ?: bitwarden
        is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> bitwarden
        is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> "$bitwarden · $starred"
        is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> "$bitwarden · $uncategorized"
        is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> keepassDatabases.find { it.id == selected.databaseId }?.name
            ?: keepass
        is UnifiedCategoryFilterSelection.KeePassGroupFilter -> keepass
        is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> "$keepass · $starred"
        is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> "$keepass · $uncategorized"
    }
}

@Composable
private fun StorageSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
        ) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickFilterChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null) },
        label = { Text(label) }
    )
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private data class LocalCategoryNode(
    val category: Category,
    val fullPath: String,
    val displayName: String,
    val depth: Int,
    val parentPathLabel: String?
)

private fun buildLocalCategoryNodes(categories: List<Category>): List<LocalCategoryNode> {
    return categories
        .map { category ->
            val segments = category.name
                .split("/")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val fullPath = if (segments.isEmpty()) category.name.trim() else segments.joinToString("/")
            val displayName = segments.lastOrNull() ?: fullPath
            val depth = (segments.size - 1).coerceAtLeast(0)
            LocalCategoryNode(
                category = category,
                fullPath = fullPath,
                displayName = displayName,
                depth = depth,
                parentPathLabel = segments
                    .dropLast(1)
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(" / ")
            )
        }
        .sortedWith(
            compareBy<LocalCategoryNode>(
                { it.fullPath.lowercase() },
                { it.category.sortOrder },
                { it.category.id }
            )
        )
}

private fun buildNestedLocalCategoryPath(parentPath: String?, name: String): String {
    val child = name
        .split("/")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString("/")
    if (child.isBlank()) return ""

    val parent = parentPath
        ?.split("/")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.joinToString("/")
        .orEmpty()

    return if (parent.isBlank()) child else "$parent/$child"
}

private fun getLocalCategoryLeafName(path: String): String {
    val normalizedPath = buildNestedLocalCategoryPath(null, path)
    if (normalizedPath.isBlank()) return ""
    return normalizedPath.substringAfterLast('/')
}

private fun getLocalCategoryParentPath(path: String): String? {
    val normalizedPath = buildNestedLocalCategoryPath(null, path)
    if (!normalizedPath.contains('/')) return null
    return normalizedPath.substringBeforeLast('/').ifBlank { null }
}

private fun buildHierarchyDisplayTitle(baseName: String, depth: Int): String {
    if (depth <= 0) return baseName
    val prefix = buildString {
        repeat((depth - 1).coerceAtMost(3)) { append("  ") }
        append("> ")
    }
    return prefix + baseName
}

@Composable
private fun HierarchyIndentedItem(
    depth: Int,
    content: @Composable () -> Unit
) {
    val clampedDepth = depth.coerceAtLeast(0).coerceAtMost(6)
    val startInset = 14 + (clampedDepth * 18)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startInset.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (clampedDepth > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                repeat(clampedDepth) { index ->
                    val alpha = 0.20f + (index * 0.10f)
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.width(4.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

private enum class CreateTarget {
    Local,
    Bitwarden,
    KeePass
}

private enum class LocalCategoryRenameMode {
    LeafOnly,
    FullPath
}

private sealed interface RenameAction {
    data class LocalCategory(val category: Category) : RenameAction
    data class BitwardenFolder(val vaultId: Long, val folderId: String) : RenameAction
    data class KeePassGroup(val databaseId: Long, val groupPath: String) : RenameAction
}

private sealed interface DeleteAction {
    val displayName: String

    data class LocalCategory(val category: Category) : DeleteAction {
        override val displayName: String = category.name
    }

    data class BitwardenFolder(
        val vaultId: Long,
        val folderId: String,
        override val displayName: String
    ) : DeleteAction

    data class KeePassGroup(
        val databaseId: Long,
        val groupPath: String,
        override val displayName: String
    ) : DeleteAction
}

@Composable
private fun UnifiedCategoryListItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    menu: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = containerColor,
            headlineColor = contentColor,
            leadingIconColor = contentColor,
            trailingIconColor = contentColor
        ),
        leadingContent = {
            Icon(icon, contentDescription = null)
        },
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = badge,
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (selected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = contentColor)
                }
                menu?.invoke()
            }
        }
    )
}
