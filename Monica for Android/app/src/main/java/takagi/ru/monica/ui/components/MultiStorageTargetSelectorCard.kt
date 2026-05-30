package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import takagi.ru.monica.R
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.KeePassOperationBlockReason
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.data.bitwarden.BitwardenFolderDao
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.data.model.StorageTarget
import takagi.ru.monica.data.writeOperationAvailability
import takagi.ru.monica.repository.MdbxStoredFolderEntry
import takagi.ru.monica.utils.decodeKeePassPathForDisplay

@Composable
fun MultiStorageTargetSelectorCard(
    selectedTargets: List<StorageTarget>,
    existingTargetKeys: Set<String>,
    categories: List<Category>,
    keepassDatabases: List<LocalKeePassDatabase>,
    mdbxDatabases: List<LocalMdbxDatabase> = emptyList(),
    bitwardenVaults: List<BitwardenVault>,
    bitwardenFolderDao: BitwardenFolderDao,
    getMdbxFolders: (Long) -> Flow<List<MdbxStoredFolderEntry>> = { flowOf(emptyList()) },
    isEditing: Boolean,
    onAddTargetClick: () -> Unit,
    onRemoveTarget: (StorageTarget) -> Unit
) {
    val primaryTarget = selectedTargets.firstOrNull() ?: StorageTarget.MonicaLocal(null)
    val folderNameFlow = remember(primaryTarget, bitwardenFolderDao, getMdbxFolders) {
        when (primaryTarget) {
            is StorageTarget.Bitwarden -> {
                bitwardenFolderDao.getFoldersByVaultFlow(primaryTarget.vaultId).map { folders ->
                    primaryTarget.folderId?.let { folderId ->
                        folders.firstOrNull { it.bitwardenFolderId == folderId }?.name
                    }
                }
            }
            is StorageTarget.Mdbx -> {
                getMdbxFolders(primaryTarget.databaseId).map { folders ->
                    primaryTarget.folderId?.let { folderId ->
                        folders.firstOrNull { it.folderId == folderId }?.name
                    }
                }
            }

            else -> flowOf(null)
        }
    }
    val externalFolderName by folderNameFlow.collectAsState(initial = null)

    val monicaLabel = stringResource(R.string.app_name)
    val keepassLabel = stringResource(R.string.create_target_keepass)
    val mdbxLabel = "MDBX"
    val bitwardenLabel = stringResource(R.string.create_target_bitwarden)
    val uncategorizedLabel = stringResource(R.string.multi_storage_uncategorized)
    val keepassRootLabel = stringResource(R.string.multi_storage_keepass_root)
    val noFolderLabel = stringResource(R.string.multi_storage_bitwarden_no_folder)
    val mdbxVaultLabel = stringResource(R.string.category_selection_menu_databases)
    val emptyHint = stringResource(R.string.multi_storage_empty_hint)
    val selectedSummaryFormat = stringResource(R.string.multi_storage_selected_summary)
    val keepOriginalSuffix = stringResource(R.string.multi_storage_preserved_existing_suffix)
    val noTargetLabel = stringResource(R.string.multi_storage_no_target)
    val moreSourcesFormat = stringResource(R.string.multi_storage_more_sources_suffix)
    val keepassUnavailableFormat = stringResource(R.string.keepass_connection_status_unavailable_format)

    val sourceLabels = selectedTargets
        .map { target ->
            when (target) {
                is StorageTarget.MonicaLocal -> monicaLabel
                is StorageTarget.KeePass -> keepassDatabases.firstOrNull { it.id == target.databaseId }?.name
                    ?: keepassLabel
                is StorageTarget.Mdbx -> mdbxDatabases.firstOrNull { it.id == target.databaseId }?.name
                    ?: mdbxLabel
                is StorageTarget.Bitwarden -> bitwardenVaults.firstOrNull { it.id == target.vaultId }?.displayName
                    ?: bitwardenVaults.firstOrNull { it.id == target.vaultId }?.email
                    ?: bitwardenLabel
            }
        }
        .distinct()

    val previewTitle = when (primaryTarget) {
        is StorageTarget.MonicaLocal -> monicaLabel
        is StorageTarget.KeePass -> keepassDatabases.firstOrNull { it.id == primaryTarget.databaseId }?.name
            ?: keepassLabel
        is StorageTarget.Mdbx -> mdbxDatabases.firstOrNull { it.id == primaryTarget.databaseId }?.name
            ?: mdbxLabel
        is StorageTarget.Bitwarden -> bitwardenVaults.firstOrNull { it.id == primaryTarget.vaultId }?.displayName
            ?: bitwardenVaults.firstOrNull { it.id == primaryTarget.vaultId }?.email
            ?: bitwardenLabel
    }
    val previewFolder = when (primaryTarget) {
        is StorageTarget.MonicaLocal -> categories.firstOrNull { it.id == primaryTarget.categoryId }?.name
            ?: uncategorizedLabel
        is StorageTarget.KeePass -> primaryTarget.groupPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::decodeKeePassPathForDisplay)
            ?: keepassRootLabel
        is StorageTarget.Mdbx -> externalFolderName ?: mdbxVaultLabel
        is StorageTarget.Bitwarden -> externalFolderName ?: noFolderLabel
    }
    val keepassConnectionHint = (primaryTarget as? StorageTarget.KeePass)
        ?.let { keepassTarget ->
            keepassDatabases.firstOrNull { it.id == keepassTarget.databaseId }
        }
        ?.let { database ->
            val availability = database.writeOperationAvailability()
            if (availability.canOperate) {
                null
            } else {
                keepassUnavailableFormat.format(keepassBlockReasonLabel(availability.reason))
            }
        }
    val summarizedSources = summarizeStorageSources(sourceLabels, noTargetLabel, moreSourcesFormat)
    val subtitle = when {
        selectedTargets.isEmpty() -> emptyHint
        selectedTargets.size == 1 && keepassConnectionHint != null -> "$previewTitle · $previewFolder · $keepassConnectionHint"
        selectedTargets.size == 1 -> "$previewTitle · $previewFolder"
        else -> selectedSummaryFormat.format(selectedTargets.size, summarizedSources)
    }
    val visuals = storageCardVisuals(primaryTarget, multipleSources = sourceLabels.size > 1)

    Surface(
        onClick = onAddTargetClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = visuals.containerColor,
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
                color = visuals.iconContainerColor,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = visuals.icon,
                        contentDescription = null,
                        tint = visuals.iconTint,
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
                    text = stringResource(R.string.multi_storage_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = visuals.contentColor
                )
                Text(
                    text = if (isEditing && existingTargetKeys.isNotEmpty() && selectedTargets.size > existingTargetKeys.size) {
                        "$subtitle · $keepOriginalSuffix"
                    } else {
                        subtitle
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = visuals.contentColor.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = null,
                tint = visuals.contentColor
            )
        }
    }
}

@Composable
fun keepassBlockReasonLabel(reason: KeePassOperationBlockReason?): String {
    return when (reason) {
        KeePassOperationBlockReason.MISSING_DATABASE -> stringResource(R.string.keepass_connection_status_missing)
        KeePassOperationBlockReason.NEEDS_REFRESH -> stringResource(R.string.keepass_connection_status_needs_refresh)
        KeePassOperationBlockReason.SYNCING -> stringResource(R.string.keepass_connection_status_syncing)
        KeePassOperationBlockReason.CONFLICT -> stringResource(R.string.keepass_connection_status_conflict)
        KeePassOperationBlockReason.FAILED -> stringResource(R.string.keepass_connection_status_failed)
        null -> stringResource(R.string.keepass_connection_status_needs_refresh)
    }
}

private data class StorageCardVisuals(
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val iconContainerColor: Color,
    val iconTint: Color
)

@Composable
private fun storageCardVisuals(
    primaryTarget: StorageTarget,
    multipleSources: Boolean
): StorageCardVisuals {
    if (multipleSources) {
        return StorageCardVisuals(
            icon = Icons.Default.Shield,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            iconContainerColor = MaterialTheme.colorScheme.secondary,
            iconTint = MaterialTheme.colorScheme.onSecondary
        )
    }

    return when (primaryTarget) {
        is StorageTarget.MonicaLocal -> StorageCardVisuals(
            icon = Icons.Default.Shield,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            iconContainerColor = MaterialTheme.colorScheme.secondary,
            iconTint = MaterialTheme.colorScheme.onSecondary
        )
        is StorageTarget.KeePass -> StorageCardVisuals(
            icon = Icons.Default.Key,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            iconContainerColor = MaterialTheme.colorScheme.primary,
            iconTint = MaterialTheme.colorScheme.onPrimary
        )
        is StorageTarget.Mdbx -> StorageCardVisuals(
            icon = Icons.Default.Storage,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            iconContainerColor = MaterialTheme.colorScheme.inverseSurface,
            iconTint = MaterialTheme.colorScheme.inverseOnSurface
        )
        is StorageTarget.Bitwarden -> StorageCardVisuals(
            icon = Icons.Default.Cloud,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            iconContainerColor = MaterialTheme.colorScheme.tertiary,
            iconTint = MaterialTheme.colorScheme.onTertiary
        )
    }
}

private fun summarizeStorageSources(
    labels: List<String>,
    noTargetLabel: String,
    moreSourcesFormat: String
): String {
    if (labels.isEmpty()) return noTargetLabel
    if (labels.size <= 2) return labels.joinToString(" / ")
    return labels.take(2).joinToString(" / ") + " + " + moreSourcesFormat.format(labels.size - 2)
}

fun buildMultiStorageTarget(
    categoryId: Long?,
    keepassDatabaseId: Long?,
    keepassGroupPath: String?,
    mdbxDatabaseId: Long? = null,
    bitwardenVaultId: Long?,
    bitwardenFolderId: String?,
    mdbxFolderId: String? = null
): StorageTarget {
    return when {
        bitwardenVaultId != null -> StorageTarget.Bitwarden(bitwardenVaultId, bitwardenFolderId)
        keepassDatabaseId != null -> StorageTarget.KeePass(keepassDatabaseId, keepassGroupPath)
        mdbxDatabaseId != null -> StorageTarget.Mdbx(mdbxDatabaseId, mdbxFolderId)
        else -> StorageTarget.MonicaLocal(categoryId)
    }
}

fun UnifiedMoveCategoryTarget.toMultiStorageTargetOrNull(): StorageTarget? {
    return when (this) {
        UnifiedMoveCategoryTarget.Uncategorized -> StorageTarget.MonicaLocal(null)
        is UnifiedMoveCategoryTarget.MonicaCategory -> StorageTarget.MonicaLocal(categoryId)
        is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> StorageTarget.KeePass(databaseId, null)
        is UnifiedMoveCategoryTarget.KeePassGroupTarget -> StorageTarget.KeePass(databaseId, groupPath)
        is UnifiedMoveCategoryTarget.MdbxDatabaseTarget -> StorageTarget.Mdbx(databaseId)
        is UnifiedMoveCategoryTarget.MdbxFolderTarget -> StorageTarget.Mdbx(databaseId, folderId)
        is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> StorageTarget.Bitwarden(vaultId, null)
        is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> StorageTarget.Bitwarden(vaultId, folderId)
    }
}
