package takagi.ru.monica.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.KeePassStorageLocation
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.bitwarden.BitwardenVault

/**
 * 统一存储目标选择卡片（本地 / Bitwarden / KeePass）
 */
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
    modifier: Modifier = Modifier
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val selectedKeePassDatabase = keepassDatabases.find { it.id == selectedKeePassDatabaseId }
    val selectedBitwardenVault = bitwardenVaults.find { it.id == selectedBitwardenVaultId }

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
        isBitwarden -> stringResource(R.string.sync_save_to_bitwarden)
        else -> stringResource(R.string.vault_monica_only_desc)
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
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = iconColor,
                modifier = Modifier.size(48.dp)
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
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f).padding(start = 16.dp, end = 12.dp)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    text = displaySubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
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
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.vault_select_storage),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                StorageTargetOptionItem(
                    title = stringResource(R.string.vault_monica_only),
                    subtitle = stringResource(R.string.vault_monica_only_desc),
                    icon = Icons.Default.Shield,
                    isSelected = isLocal,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    onClick = {
                        onKeePassDatabaseSelected(null)
                        onBitwardenVaultSelected(null)
                        showBottomSheet = false
                    }
                )

                Text(
                    text = stringResource(R.string.category),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )

                StorageTargetOptionItem(
                    title = stringResource(R.string.category_none),
                    subtitle = stringResource(R.string.category_none),
                    icon = Icons.Default.FolderOff,
                    isSelected = selectedCategoryId == null,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconColor = MaterialTheme.colorScheme.outline,
                    onClick = {
                        onCategorySelected(null)
                        showBottomSheet = false
                    }
                )

                categories.forEach { category ->
                    StorageTargetOptionItem(
                        title = category.name,
                        subtitle = stringResource(R.string.category),
                        icon = Icons.Default.Folder,
                        isSelected = selectedCategoryId == category.id,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        iconColor = MaterialTheme.colorScheme.primary,
                        onClick = {
                            onCategorySelected(category.id)
                            showBottomSheet = false
                        }
                    )
                }

                if (bitwardenVaults.isNotEmpty()) {
                    Text(
                        text = "Bitwarden",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )

                    bitwardenVaults.forEach { vault ->
                        StorageTargetOptionItem(
                            title = vault.displayName ?: vault.email,
                            subtitle = "${vault.serverUrl} · ${stringResource(R.string.sync_save_to_bitwarden)}",
                            icon = Icons.Default.Cloud,
                            isSelected = selectedBitwardenVaultId == vault.id,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            iconColor = MaterialTheme.colorScheme.tertiary,
                            onClick = {
                                onBitwardenVaultSelected(vault.id)
                                showBottomSheet = false
                            }
                        )
                    }
                }

                if (keepassDatabases.isNotEmpty()) {
                    Text(
                        text = "KeePass",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )

                    keepassDatabases.forEach { database ->
                        val storageText = if (database.storageLocation == KeePassStorageLocation.EXTERNAL) {
                            stringResource(R.string.external_storage)
                        } else {
                            stringResource(R.string.internal_storage)
                        }
                        StorageTargetOptionItem(
                            title = database.name,
                            subtitle = "$storageText · ${stringResource(R.string.vault_sync_hint)}",
                            icon = Icons.Default.Key,
                            isSelected = selectedKeePassDatabaseId == database.id,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            iconColor = MaterialTheme.colorScheme.primary,
                            onClick = {
                                onKeePassDatabaseSelected(database.id)
                                showBottomSheet = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageTargetOptionItem(
    title: String,
    subtitle: String,
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
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) containerColor else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) iconColor else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) contentColor else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) contentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
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
