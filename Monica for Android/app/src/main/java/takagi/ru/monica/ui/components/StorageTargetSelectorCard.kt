package takagi.ru.monica.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flowOf
import takagi.ru.monica.R
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.utils.decodeKeePassPathForDisplay

private enum class StorageScope {
    Local,
    KeePass,
    Bitwarden
}

private data class StorageDatabaseChipItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit
)

private data class StorageFolderChipItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StorageTargetSelectorCard(
    keepassDatabases: List<LocalKeePassDatabase>,
    selectedKeePassDatabaseId: Long?,
    onKeePassDatabaseSelected: (Long?) -> Unit,
    bitwardenVaults: List<BitwardenVault>,
    selectedBitwardenVaultId: Long?,
    onBitwardenVaultSelected: (Long?) -> Unit,
    categories: List<Category> = emptyList(),
    selectedCategoryId: Long? = null,
    onCategorySelected: (Long?) -> Unit = {},
    selectedBitwardenFolderId: String? = null,
    onBitwardenFolderSelected: (String?) -> Unit = {},
    selectedKeePassGroupPath: String? = null,
    modifier: Modifier = Modifier
) {
    val hasExternalSources = keepassDatabases.isNotEmpty() || bitwardenVaults.isNotEmpty()
    if (!hasExternalSources) return

    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val maxSheetHeight = configuration.screenHeightDp.dp * 0.82f
    val minSheetHeight = (configuration.screenHeightDp.dp * 0.45f)
        .coerceIn(300.dp, 430.dp)
    val effectiveMinSheetHeight = if (minSheetHeight > maxSheetHeight) {
        maxSheetHeight
    } else {
        minSheetHeight
    }
    val database = remember { PasswordDatabase.getDatabase(context) }

    val selectedKeePassDatabase = keepassDatabases.find { it.id == selectedKeePassDatabaseId }
    val selectedBitwardenVault = bitwardenVaults.find { it.id == selectedBitwardenVaultId }
    val selectedLocalCategory = categories.find { it.id == selectedCategoryId }
    val selectedVaultFolders by (
        if (selectedBitwardenVaultId != null) {
            database.bitwardenFolderDao().getFoldersByVaultFlow(selectedBitwardenVaultId)
        } else {
            flowOf(emptyList<BitwardenFolder>())
        }
    ).collectAsState(initial = emptyList())
    val selectedBitwardenFolderName = selectedBitwardenFolderId?.let { folderId ->
        selectedVaultFolders.find { it.bitwardenFolderId == folderId }?.name
    }

    val currentScope = when {
        selectedBitwardenVaultId != null -> StorageScope.Bitwarden
        selectedKeePassDatabaseId != null -> StorageScope.KeePass
        else -> StorageScope.Local
    }

    val displayName = when (currentScope) {
        StorageScope.KeePass -> selectedKeePassDatabase?.name ?: stringResource(R.string.vault_monica_only)
        StorageScope.Bitwarden -> "Bitwarden (${selectedBitwardenVault?.email.orEmpty()})"
        StorageScope.Local -> stringResource(R.string.vault_monica_only)
    }
    val displaySubtitle = when (currentScope) {
        StorageScope.KeePass -> selectedKeePassGroupPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::decodeKeePassPathForDisplay)
            ?: stringResource(R.string.vault_sync_hint)
        StorageScope.Bitwarden -> selectedBitwardenFolderName ?: stringResource(R.string.sync_save_to_bitwarden)
        StorageScope.Local -> selectedLocalCategory?.name ?: stringResource(R.string.category_none)
    }

    val containerColor = when (currentScope) {
        StorageScope.Bitwarden -> MaterialTheme.colorScheme.tertiaryContainer
        StorageScope.KeePass -> MaterialTheme.colorScheme.primaryContainer
        StorageScope.Local -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when (currentScope) {
        StorageScope.Bitwarden -> MaterialTheme.colorScheme.onTertiaryContainer
        StorageScope.KeePass -> MaterialTheme.colorScheme.onPrimaryContainer
        StorageScope.Local -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val iconColor = when (currentScope) {
        StorageScope.Bitwarden -> MaterialTheme.colorScheme.tertiary
        StorageScope.KeePass -> MaterialTheme.colorScheme.primary
        StorageScope.Local -> MaterialTheme.colorScheme.secondary
    }
    val icon = when (currentScope) {
        StorageScope.Bitwarden -> Icons.Default.Cloud
        StorageScope.KeePass -> Icons.Default.Key
        StorageScope.Local -> Icons.Default.Shield
    }

    val localDatabaseChipLabel = stringResource(R.string.vault_monica_only)
    val databaseChips = buildList {
        add(
            StorageDatabaseChipItem(
                label = localDatabaseChipLabel,
                icon = Icons.Default.Shield,
                selected = currentScope == StorageScope.Local,
                onClick = {
                    onKeePassDatabaseSelected(null)
                    onBitwardenVaultSelected(null)
                    onBitwardenFolderSelected(null)
                }
            )
        )
        keepassDatabases.forEach { keepassDatabase ->
            add(
                StorageDatabaseChipItem(
                    label = keepassDatabase.name,
                    icon = Icons.Default.Key,
                    selected = currentScope == StorageScope.KeePass &&
                        selectedKeePassDatabaseId == keepassDatabase.id,
                    onClick = {
                        onCategorySelected(null)
                        onBitwardenVaultSelected(null)
                        onBitwardenFolderSelected(null)
                        onKeePassDatabaseSelected(keepassDatabase.id)
                    }
                )
            )
        }
        bitwardenVaults.forEach { vault ->
            add(
                StorageDatabaseChipItem(
                    label = vault.displayName ?: vault.email,
                    icon = Icons.Default.Cloud,
                    selected = currentScope == StorageScope.Bitwarden &&
                        selectedBitwardenVaultId == vault.id,
                    onClick = {
                        onCategorySelected(null)
                        onKeePassDatabaseSelected(null)
                        onBitwardenVaultSelected(vault.id)
                        onBitwardenFolderSelected(null)
                    }
                )
            )
        }
    }
    val topRowDatabaseChips = databaseChips.filterIndexed { index, _ -> index % 2 == 0 }
    val bottomRowDatabaseChips = databaseChips.filterIndexed { index, _ -> index % 2 != 0 }

    val folderChips = when (currentScope) {
        StorageScope.Local -> buildList {
            add(
                StorageFolderChipItem(
                    label = stringResource(R.string.category_none),
                    icon = Icons.Default.FolderOff,
                    selected = selectedCategoryId == null,
                    onClick = {
                        onKeePassDatabaseSelected(null)
                        onBitwardenVaultSelected(null)
                        onBitwardenFolderSelected(null)
                        onCategorySelected(null)
                    }
                )
            )
            categories.forEach { category ->
                add(
                    StorageFolderChipItem(
                        label = category.name,
                        icon = Icons.Default.Folder,
                        selected = selectedCategoryId == category.id,
                        onClick = {
                            onKeePassDatabaseSelected(null)
                            onBitwardenVaultSelected(null)
                            onBitwardenFolderSelected(null)
                            onCategorySelected(category.id)
                        }
                    )
                )
            }
        }
        StorageScope.Bitwarden -> {
            val selectedVaultId = selectedBitwardenVaultId
            if (selectedVaultId == null) {
                emptyList()
            } else {
                buildList {
                    add(
                        StorageFolderChipItem(
                            label = stringResource(R.string.folder_no_folder_root),
                            icon = Icons.Default.FolderOff,
                            selected = selectedBitwardenFolderId == null,
                            onClick = {
                                onCategorySelected(null)
                                onKeePassDatabaseSelected(null)
                                onBitwardenVaultSelected(selectedVaultId)
                                onBitwardenFolderSelected(null)
                            }
                        )
                    )
                    selectedVaultFolders.forEach { folder ->
                        add(
                            StorageFolderChipItem(
                                label = folder.name,
                                icon = Icons.Default.Folder,
                                selected = selectedBitwardenFolderId == folder.bitwardenFolderId,
                                onClick = {
                                    onCategorySelected(null)
                                    onKeePassDatabaseSelected(null)
                                    onBitwardenVaultSelected(selectedVaultId)
                                    onBitwardenFolderSelected(folder.bitwardenFolderId)
                                }
                            )
                        )
                    }
                }
            }
        }
        StorageScope.KeePass -> emptyList()
    }
    val keepassGroupHint = if (currentScope == StorageScope.KeePass) {
        selectedKeePassGroupPath?.takeIf { it.isNotBlank() }
    } else {
        null
    }

    Surface(
        onClick = { showBottomSheet = true },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = iconColor,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = when (currentScope) {
                            StorageScope.Bitwarden -> MaterialTheme.colorScheme.onTertiary
                            StorageScope.KeePass -> MaterialTheme.colorScheme.onPrimary
                            StorageScope.Local -> MaterialTheme.colorScheme.onSecondary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 12.dp)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    text = displaySubtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.75f)
                )
            }

            Icon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = null,
                tint = contentColor
            )
        }
    }

    if (showBottomSheet) {
        MonicaModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = effectiveMinSheetHeight, max = maxSheetHeight)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.vault_select_storage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp)
                )

                StorageSelectorSectionTitle(text = stringResource(R.string.category_selection_menu_databases))
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        topRowDatabaseChips.forEach { chip ->
                            MonicaExpressiveFilterChip(
                                selected = chip.selected,
                                onClick = chip.onClick,
                                label = chip.label,
                                leadingIcon = chip.icon
                            )
                        }
                    }

                    if (bottomRowDatabaseChips.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            bottomRowDatabaseChips.forEach { chip ->
                                MonicaExpressiveFilterChip(
                                    selected = chip.selected,
                                    onClick = chip.onClick,
                                    label = chip.label,
                                    leadingIcon = chip.icon
                                )
                            }
                        }
                    }
                }

                if (folderChips.isNotEmpty() || !keepassGroupHint.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StorageSelectorSectionTitle(text = stringResource(R.string.category_selection_menu_folders))

                        if (folderChips.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                folderChips.forEach { chip ->
                                    MonicaExpressiveFilterChip(
                                        selected = chip.selected,
                                        onClick = chip.onClick,
                                        label = chip.label,
                                        leadingIcon = chip.icon
                                    )
                                }
                            }
                        }

                        if (!keepassGroupHint.isNullOrBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = decodeKeePassPathForDisplay(keepassGroupHint),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = stringResource(R.string.vault_sync_hint),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
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
}

@Composable
private fun StorageSelectorSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
