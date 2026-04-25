package takagi.ru.monica.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import takagi.ru.monica.ui.components.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import takagi.ru.monica.R
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.utils.KEEPASS_DISPLAY_PATH_SEPARATOR
import takagi.ru.monica.utils.KeePassGroupInfo
import takagi.ru.monica.utils.buildLocalCategoryPathOptions

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreateCategoryDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    categories: List<Category>,
    keepassDatabases: List<LocalKeePassDatabase>,
    bitwardenVaults: List<BitwardenVault>,
    getKeePassGroups: ((Long) -> Flow<List<KeePassGroupInfo>>)? = null,
    onCreateCategory: (() -> Unit)? = null,
    onCreateCategoryWithName: ((String) -> Unit)? = null,
    onCreateBitwardenFolder: ((Long, String) -> Unit)? = null,
    onCreateKeePassGroup: ((databaseId: Long, parentPath: String?, name: String) -> Unit)? = null,
    initialLocalParentPath: String? = null
) {
    if (!visible) return

    val canCreateLocal = onCreateCategoryWithName != null || onCreateCategory != null
    val canCreateBitwarden = onCreateBitwardenFolder != null && bitwardenVaults.isNotEmpty()
    val canCreateKeePass = onCreateKeePassGroup != null && keepassDatabases.isNotEmpty()
    if (!canCreateLocal && !canCreateBitwarden && !canCreateKeePass) return

    val localCategoryNodes = remember(categories) { buildCreateDialogLocalCategoryNodes(categories) }

    var createNameInput by remember { mutableStateOf("") }
    var createTarget by remember { mutableStateOf(CreateDialogTarget.Local) }
    var createLocalParentPath by remember { mutableStateOf<String?>(null) }
    var createKeePassParentPath by remember { mutableStateOf<String?>(null) }
    var selectedCreateVaultId by remember { mutableStateOf<Long?>(null) }
    var selectedCreateKeePassDbId by remember { mutableStateOf<Long?>(null) }
    var createOptionsExpanded by remember { mutableStateOf(true) }

    val expandCollapseSpec = spring<IntSize>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    LaunchedEffect(visible, canCreateLocal, canCreateBitwarden, canCreateKeePass, initialLocalParentPath) {
        if (!visible) return@LaunchedEffect
        createNameInput = ""
        createLocalParentPath = initialLocalParentPath
        createKeePassParentPath = null
        createOptionsExpanded = true
        createTarget = when {
            !initialLocalParentPath.isNullOrBlank() && canCreateLocal -> CreateDialogTarget.Local
            canCreateLocal -> CreateDialogTarget.Local
            canCreateBitwarden -> CreateDialogTarget.Bitwarden
            canCreateKeePass -> CreateDialogTarget.KeePass
            else -> CreateDialogTarget.Local
        }
    }

    LaunchedEffect(bitwardenVaults) {
        if (selectedCreateVaultId == null || bitwardenVaults.none { it.id == selectedCreateVaultId }) {
            selectedCreateVaultId = bitwardenVaults.firstOrNull()?.id
        }
    }
    LaunchedEffect(keepassDatabases) {
        if (selectedCreateKeePassDbId == null || keepassDatabases.none { it.id == selectedCreateKeePassDbId }) {
            selectedCreateKeePassDbId = keepassDatabases.firstOrNull()?.id
        }
    }

    val createTargetScroll = rememberScrollState()
    val createVaultScroll = rememberScrollState()
    val createKeePassDbScroll = rememberScrollState()
    val createKeePassParentScroll = rememberScrollState()

    val createKeePassGroups by (
        if (
            createTarget == CreateDialogTarget.KeePass &&
            selectedCreateKeePassDbId != null &&
            getKeePassGroups != null
        ) {
            getKeePassGroups.invoke(selectedCreateKeePassDbId!!)
        } else {
            flowOf(emptyList())
        }
    ).collectAsState(initial = emptyList())

    val sortedCreateKeePassGroups = remember(createKeePassGroups) {
        createKeePassGroups.sortedBy { it.displayPath.lowercase() }
    }

    LaunchedEffect(createTarget, sortedCreateKeePassGroups, createKeePassParentPath) {
        if (createTarget != CreateDialogTarget.KeePass) return@LaunchedEffect
        val currentParent = createKeePassParentPath ?: return@LaunchedEffect
        if (sortedCreateKeePassGroups.none { it.path == currentParent }) {
            createKeePassParentPath = null
        }
    }

    val selectedCreateKeePassParentDisplay = sortedCreateKeePassGroups
        .firstOrNull { it.path == createKeePassParentPath }
        ?.displayPath

    val localPreviewPath = buildCreateDialogNestedLocalPath(createLocalParentPath, createNameInput)
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
        CreateDialogTarget.Local -> localPreviewPath
        CreateDialogTarget.Bitwarden -> createNameInput.trim()
        CreateDialogTarget.KeePass -> keepassPreviewPath
    }

    val targetLabel = when (createTarget) {
        CreateDialogTarget.Local -> stringResource(R.string.create_target_local)
        CreateDialogTarget.Bitwarden -> stringResource(R.string.create_target_bitwarden)
        CreateDialogTarget.KeePass -> stringResource(R.string.create_target_keepass)
    }
    val targetIcon = when (createTarget) {
        CreateDialogTarget.Local -> Icons.Default.Smartphone
        CreateDialogTarget.Bitwarden -> Icons.Default.CloudSync
        CreateDialogTarget.KeePass -> Icons.Default.Key
    }
    val targetTint = when (createTarget) {
        CreateDialogTarget.Local -> MaterialTheme.colorScheme.primary
        CreateDialogTarget.Bitwarden -> MaterialTheme.colorScheme.secondary
        CreateDialogTarget.KeePass -> MaterialTheme.colorScheme.tertiary
    }

    val createChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
    )

    val canSubmit = when (createTarget) {
        CreateDialogTarget.Local -> canCreateLocal
        CreateDialogTarget.Bitwarden -> canCreateBitwarden && selectedCreateVaultId != null
        CreateDialogTarget.KeePass -> canCreateKeePass && selectedCreateKeePassDbId != null
    } && createNameInput.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
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
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            selected = createTarget == CreateDialogTarget.Local,
                            onClick = {
                                createTarget = CreateDialogTarget.Local
                                createKeePassParentPath = null
                            },
                            colors = createChipColors,
                            label = { Text(stringResource(R.string.create_target_local)) },
                            leadingIcon = { Icon(Icons.Default.Smartphone, contentDescription = null) }
                        )
                    }
                    if (canCreateBitwarden) {
                        FilterChip(
                            selected = createTarget == CreateDialogTarget.Bitwarden,
                            onClick = {
                                createTarget = CreateDialogTarget.Bitwarden
                                createLocalParentPath = null
                                createKeePassParentPath = null
                            },
                            colors = createChipColors,
                            label = { Text(stringResource(R.string.create_target_bitwarden)) },
                            leadingIcon = { Icon(Icons.Default.CloudSync, contentDescription = null) }
                        )
                    }
                    if (canCreateKeePass) {
                        FilterChip(
                            selected = createTarget == CreateDialogTarget.KeePass,
                            onClick = {
                                createTarget = CreateDialogTarget.KeePass
                                createLocalParentPath = null
                            },
                            colors = createChipColors,
                            label = { Text(stringResource(R.string.create_target_keepass)) },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) }
                        )
                    }
                }

                Text(
                    text = when (createTarget) {
                        CreateDialogTarget.Local -> stringResource(R.string.create_select_local_parent)
                        CreateDialogTarget.Bitwarden -> stringResource(R.string.create_select_bitwarden_vault)
                        CreateDialogTarget.KeePass -> stringResource(R.string.create_select_keepass_database)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                when (createTarget) {
                    CreateDialogTarget.Local -> {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                            modifier = Modifier.widthIn(max = 210.dp)
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
                                )
                            }
                        }
                    }

                    CreateDialogTarget.Bitwarden -> {
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
                    }

                    CreateDialogTarget.KeePass -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(createKeePassDbScroll),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            keepassDatabases.forEach { db ->
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
                        CreateDialogTarget.Local -> {
                            val normalizedName = buildCreateDialogNestedLocalPath(createLocalParentPath, name)
                            if (normalizedName.isBlank()) return@FilledTonalButton
                            if (onCreateCategoryWithName != null) {
                                onCreateCategoryWithName(normalizedName)
                            } else {
                                onCreateCategory?.invoke()
                            }
                        }

                        CreateDialogTarget.Bitwarden -> {
                            val vaultId = selectedCreateVaultId
                            if (vaultId != null) {
                                onCreateBitwardenFolder?.invoke(vaultId, name)
                            }
                        }

                        CreateDialogTarget.KeePass -> {
                            val dbId = selectedCreateKeePassDbId
                            if (dbId != null) {
                                onCreateKeePassGroup?.invoke(dbId, createKeePassParentPath, name)
                            }
                        }
                    }
                    onDismiss()
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
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private data class CreateDialogLocalCategoryNode(
    val fullPath: String
)

private fun buildCreateDialogLocalCategoryNodes(categories: List<Category>): List<CreateDialogLocalCategoryNode> {
    return buildLocalCategoryPathOptions(categories)
        .map { option -> CreateDialogLocalCategoryNode(fullPath = option.path) }
}

private fun buildCreateDialogNestedLocalPath(parentPath: String?, name: String): String {
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

@Composable
private fun CreateDialogAnimatedVisibility(
    visible: Boolean,
    enter: EnterTransition,
    exit: ExitTransition,
    content: @Composable () -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        if (visible) content()
        return
    }
    AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit
    ) {
        content()
    }
}

private enum class CreateDialogTarget {
    Local,
    Bitwarden,
    KeePass
}
