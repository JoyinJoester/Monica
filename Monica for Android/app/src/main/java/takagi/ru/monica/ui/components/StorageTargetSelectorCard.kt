package takagi.ru.monica.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flowOf
import takagi.ru.monica.R
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.KeePassStorageLocation
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenVault

@OptIn(ExperimentalMaterial3Api::class)
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
    modifier: Modifier = Modifier
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val database = remember { PasswordDatabase.getDatabase(context) }

    val selectedKeePassDatabase = keepassDatabases.find { it.id == selectedKeePassDatabaseId }
    val selectedBitwardenVault = bitwardenVaults.find { it.id == selectedBitwardenVaultId }
    val selectedLocalCategory = categories.find { it.id == selectedCategoryId }

    val selectedVaultFolders by (
        if (selectedBitwardenVault != null) {
            database.bitwardenFolderDao().getFoldersByVaultFlow(selectedBitwardenVault.id)
        } else {
            flowOf(emptyList<BitwardenFolder>())
        }
    ).collectAsState(initial = emptyList())
    val selectedBitwardenFolderName = selectedBitwardenFolderId?.let { folderId ->
        selectedVaultFolders.find { it.bitwardenFolderId == folderId }?.name
    }

    val isKeePass = selectedKeePassDatabase != null
    val isBitwarden = selectedBitwardenVault != null
    val isLocal = !isKeePass && !isBitwarden

    val displayName = when {
        isKeePass -> selectedKeePassDatabase!!.name
        isBitwarden -> "Bitwarden (${selectedBitwardenVault!!.email})"
        else -> stringResource(R.string.vault_monica_only)
    }

    val displaySubtitle = when {
        isKeePass -> stringResource(R.string.vault_sync_hint)
        isBitwarden -> selectedBitwardenFolderName ?: stringResource(R.string.sync_save_to_bitwarden)
        else -> selectedLocalCategory?.name ?: stringResource(R.string.category_none)
    }

    val containerColor = when {
        isBitwarden -> MaterialTheme.colorScheme.tertiaryContainer
        isKeePass -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val contentColor = when {
        isBitwarden -> MaterialTheme.colorScheme.onTertiaryContainer
        isKeePass -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    val iconColor = when {
        isBitwarden -> MaterialTheme.colorScheme.tertiary
        isKeePass -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    val icon = when {
        isBitwarden -> Icons.Default.Cloud
        isKeePass -> Icons.Default.Key
        else -> Icons.Default.Shield
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
                        tint = when {
                            isBitwarden -> MaterialTheme.colorScheme.onTertiary
                            isKeePass -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSecondary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 12.dp)) {
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
        var localExpanded by remember { mutableStateOf(false) }
        var expandedBitwardenVaultId by remember { mutableStateOf<Long?>(null) }

        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.vault_select_storage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                StorageTargetSectionHeaderItem(
                    title = stringResource(R.string.vault_monica_only),
                    subtitle = stringResource(R.string.vault_monica_only_desc),
                    icon = Icons.Default.Shield,
                    isSelected = isLocal,
                    expanded = localExpanded,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    onClick = {
                        if (!localExpanded) {
                            localExpanded = true
                            expandedBitwardenVaultId = null
                        } else {
                            onKeePassDatabaseSelected(null)
                            onBitwardenVaultSelected(null)
                            onBitwardenFolderSelected(null)
                        }
                    }
                )

                AnimatedVisibility(
                    visible = localExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StorageTargetLeafItem(
                            title = stringResource(R.string.category_none),
                            subtitle = null,
                            icon = Icons.Default.FolderOff,
                            isSelected = selectedCategoryId == null,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconColor = MaterialTheme.colorScheme.outline,
                            onClick = {
                                onKeePassDatabaseSelected(null)
                                onBitwardenVaultSelected(null)
                                onBitwardenFolderSelected(null)
                                onCategorySelected(null)
                            }
                        )

                        categories.forEach { category ->
                            StorageTargetLeafItem(
                                title = category.name,
                                subtitle = null,
                                icon = Icons.Default.Folder,
                                isSelected = selectedCategoryId == category.id,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                iconColor = MaterialTheme.colorScheme.primary,
                                onClick = {
                                    onKeePassDatabaseSelected(null)
                                    onBitwardenVaultSelected(null)
                                    onBitwardenFolderSelected(null)
                                    onCategorySelected(category.id)
                                }
                            )
                        }
                    }
                }

                bitwardenVaults.forEach { vault ->
                    val vaultExpanded = expandedBitwardenVaultId == vault.id
                    val folders by (
                        if (vaultExpanded) {
                            database.bitwardenFolderDao().getFoldersByVaultFlow(vault.id)
                        } else {
                            flowOf(emptyList<BitwardenFolder>())
                        }
                    ).collectAsState(initial = emptyList())
                    val vaultSelectedAsRoot = selectedBitwardenVaultId == vault.id && selectedBitwardenFolderId == null

                    StorageTargetSectionHeaderItem(
                        title = vault.displayName ?: vault.email,
                        subtitle = stringResource(R.string.sync_save_to_bitwarden),
                        icon = Icons.Default.Cloud,
                        isSelected = vaultSelectedAsRoot,
                        expanded = vaultExpanded,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        iconColor = MaterialTheme.colorScheme.tertiary,
                        onClick = {
                            if (!vaultExpanded) {
                                expandedBitwardenVaultId = vault.id
                                localExpanded = false
                            } else {
                                onKeePassDatabaseSelected(null)
                                onBitwardenVaultSelected(vault.id)
                                onBitwardenFolderSelected(null)
                            }
                        }
                    )

                    AnimatedVisibility(
                        visible = vaultExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            folders.forEach { folder ->
                                StorageTargetLeafItem(
                                    title = folder.name,
                                    subtitle = null,
                                    icon = Icons.Default.Folder,
                                    isSelected = selectedBitwardenVaultId == vault.id &&
                                        selectedBitwardenFolderId == folder.bitwardenFolderId,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    iconColor = MaterialTheme.colorScheme.tertiary,
                                    onClick = {
                                        onKeePassDatabaseSelected(null)
                                        onBitwardenVaultSelected(vault.id)
                                        onBitwardenFolderSelected(folder.bitwardenFolderId)
                                    }
                                )
                            }
                        }
                    }
                }

                keepassDatabases.forEach { databaseItem ->
                    val storageText = if (databaseItem.storageLocation == KeePassStorageLocation.EXTERNAL) {
                        stringResource(R.string.external_storage)
                    } else {
                        stringResource(R.string.internal_storage)
                    }

                    StorageTargetLeafItem(
                        title = databaseItem.name,
                        subtitle = storageText,
                        icon = Icons.Default.Key,
                        isSelected = selectedKeePassDatabaseId == databaseItem.id,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        iconColor = MaterialTheme.colorScheme.primary,
                        onClick = {
                            onKeePassDatabaseSelected(databaseItem.id)
                            onBitwardenVaultSelected(null)
                            onBitwardenFolderSelected(null)
                            expandedBitwardenVaultId = null
                            localExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageTargetSectionHeaderItem(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    isSelected: Boolean,
    expanded: Boolean,
    containerColor: Color,
    contentColor: Color,
    iconColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) containerColor else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) iconColor else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) contentColor else MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) contentColor.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (isSelected) contentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StorageTargetLeafItem(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    isSelected: Boolean,
    containerColor: Color,
    contentColor: Color,
    iconColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) containerColor else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) iconColor else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) contentColor else MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) contentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = iconColor,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
