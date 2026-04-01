package takagi.ru.monica.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.viewmodel.CategoryFilter

internal fun CategoryFilter.isMonicaDatabaseFilter(): Boolean = when (this) {
    is CategoryFilter.Local,
    is CategoryFilter.Starred,
    is CategoryFilter.Uncategorized,
    is CategoryFilter.LocalStarred,
    is CategoryFilter.LocalUncategorized,
    is CategoryFilter.Custom -> true
    else -> false
}

internal fun CategoryFilter.isKeePassDatabaseFilter(databaseId: Long): Boolean = when (this) {
    is CategoryFilter.KeePassDatabase -> this.databaseId == databaseId
    is CategoryFilter.KeePassGroupFilter -> this.databaseId == databaseId
    is CategoryFilter.KeePassDatabaseStarred -> this.databaseId == databaseId
    is CategoryFilter.KeePassDatabaseUncategorized -> this.databaseId == databaseId
    else -> false
}

internal fun CategoryFilter.isBitwardenVaultFilter(vaultId: Long): Boolean = when (this) {
    is CategoryFilter.BitwardenVault -> this.vaultId == vaultId
    is CategoryFilter.BitwardenFolderFilter -> this.vaultId == vaultId
    is CategoryFilter.BitwardenVaultStarred -> this.vaultId == vaultId
    is CategoryFilter.BitwardenVaultUncategorized -> this.vaultId == vaultId
    else -> false
}

internal const val QUICK_FOLDER_ROOT_ALL = "all"
internal const val QUICK_FOLDER_ROOT_LOCAL = "local"
internal const val QUICK_FOLDER_ROOT_STARRED = "starred"
internal const val QUICK_FOLDER_ROOT_UNCATEGORIZED = "uncategorized"
internal const val QUICK_FOLDER_ROOT_LOCAL_STARRED = "local_starred"
internal const val QUICK_FOLDER_ROOT_LOCAL_UNCATEGORIZED = "local_uncategorized"

internal data class PasswordListEmptyStateMessage(
    val titleRes: Int,
    val subtitleRes: Int? = null
)

@Composable
internal fun PasswordListEmptyState(message: PasswordListEmptyStateMessage) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.height(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(message.titleRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        message.subtitleRes?.let { subtitleRes ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
            )
        }
    }
}

internal fun resolvePasswordListEmptyStateMessage(
    currentFilter: CategoryFilter,
    quickFoldersEnabledForCurrentFilter: Boolean,
    hasQuickFolderShortcuts: Boolean
): PasswordListEmptyStateMessage {
    val isQuickFolderRootDatabaseView = quickFoldersEnabledForCurrentFilter && when (currentFilter) {
        is CategoryFilter.Local,
        is CategoryFilter.KeePassDatabase,
        is CategoryFilter.BitwardenVault -> true
        else -> false
    }

    return if (isQuickFolderRootDatabaseView) {
        PasswordListEmptyStateMessage(
            titleRes = R.string.password_list_quick_folder_root_empty,
            subtitleRes = if (hasQuickFolderShortcuts) {
                R.string.password_list_quick_folder_root_empty_hint
            } else {
                null
            }
        )
    } else {
        PasswordListEmptyStateMessage(titleRes = R.string.no_passwords_saved)
    }
}

internal fun applyQuickFolderRootVisibility(
    entries: List<PasswordEntry>,
    currentFilter: CategoryFilter
): List<PasswordEntry> = when (currentFilter) {
    is CategoryFilter.Local -> {
        entries.filter { entry ->
            entry.keepassDatabaseId == null &&
                entry.bitwardenVaultId == null &&
                entry.categoryId == null
        }
    }

    is CategoryFilter.KeePassDatabase -> {
        entries.filter { entry ->
            entry.keepassDatabaseId == currentFilter.databaseId &&
                entry.keepassGroupPath?.trim().isNullOrBlank()
        }
    }

    is CategoryFilter.BitwardenVault -> {
        entries.filter { entry ->
            entry.bitwardenVaultId == currentFilter.vaultId &&
                entry.bitwardenFolderId?.trim().isNullOrBlank()
        }
    }

    else -> entries
}

internal fun PasswordEntry.matchesPasswordCategoryFilter(filter: CategoryFilter): Boolean = when (filter) {
    is CategoryFilter.All -> !isDeleted && !isArchived
    is CategoryFilter.Archived -> !isDeleted && isArchived
    is CategoryFilter.Local -> !isDeleted && !isArchived && isLocalOnlyEntry()
    is CategoryFilter.LocalOnly -> !isDeleted && !isArchived && isLocalOnlyEntry()
    is CategoryFilter.Starred -> !isDeleted && !isArchived && isFavorite
    is CategoryFilter.Uncategorized -> !isDeleted && !isArchived && categoryId == null
    is CategoryFilter.LocalStarred -> !isDeleted && !isArchived && isLocalOnlyEntry() && isFavorite
    is CategoryFilter.LocalUncategorized -> !isDeleted && !isArchived && isLocalOnlyEntry() && categoryId == null
    is CategoryFilter.Custom -> !isDeleted && !isArchived && categoryId == filter.categoryId
    is CategoryFilter.KeePassDatabase -> !isDeleted && !isArchived && keepassDatabaseId == filter.databaseId
    is CategoryFilter.KeePassGroupFilter -> {
        !isDeleted && !isArchived &&
            keepassDatabaseId == filter.databaseId &&
            keepassGroupPath == filter.groupPath
    }
    is CategoryFilter.KeePassDatabaseStarred -> {
        !isDeleted && !isArchived &&
            keepassDatabaseId == filter.databaseId &&
            isFavorite
    }
    is CategoryFilter.KeePassDatabaseUncategorized -> {
        !isDeleted && !isArchived &&
            keepassDatabaseId == filter.databaseId &&
            keepassGroupPath.isNullOrBlank()
    }
    is CategoryFilter.BitwardenVault -> !isDeleted && !isArchived && bitwardenVaultId == filter.vaultId
    is CategoryFilter.BitwardenFolderFilter -> {
        !isDeleted && !isArchived &&
            bitwardenVaultId == filter.vaultId &&
            bitwardenFolderId == filter.folderId
    }
    is CategoryFilter.BitwardenVaultStarred -> {
        !isDeleted && !isArchived &&
            bitwardenVaultId == filter.vaultId &&
            isFavorite
    }
    is CategoryFilter.BitwardenVaultUncategorized -> {
        !isDeleted && !isArchived &&
            bitwardenVaultId == filter.vaultId &&
            bitwardenFolderId == null
    }
}

internal fun PasswordEntry.matchesCurrentPasswordListView(
    currentFilter: CategoryFilter,
    quickFoldersEnabledForCurrentFilter: Boolean
): Boolean {
    if (!matchesPasswordCategoryFilter(currentFilter)) return false
    if (!quickFoldersEnabledForCurrentFilter) return true
    return applyQuickFolderRootVisibility(listOf(this), currentFilter).isNotEmpty()
}

internal fun CategoryFilter.toQuickFolderRootKeyOrNull(): String? = when (this) {
    is CategoryFilter.All -> QUICK_FOLDER_ROOT_ALL
    is CategoryFilter.Archived -> null
    is CategoryFilter.Custom -> QUICK_FOLDER_ROOT_LOCAL
    is CategoryFilter.Local -> QUICK_FOLDER_ROOT_LOCAL
    is CategoryFilter.Starred -> QUICK_FOLDER_ROOT_STARRED
    is CategoryFilter.Uncategorized -> QUICK_FOLDER_ROOT_UNCATEGORIZED
    is CategoryFilter.LocalStarred -> QUICK_FOLDER_ROOT_LOCAL_STARRED
    is CategoryFilter.LocalUncategorized -> QUICK_FOLDER_ROOT_LOCAL_UNCATEGORIZED
    is CategoryFilter.KeePassDatabase,
    is CategoryFilter.KeePassGroupFilter,
    is CategoryFilter.BitwardenVault,
    is CategoryFilter.BitwardenFolderFilter -> QUICK_FOLDER_ROOT_ALL
    else -> null
}

internal fun String.toQuickFolderRootFilter(): CategoryFilter = when (this) {
    QUICK_FOLDER_ROOT_LOCAL -> CategoryFilter.Local
    QUICK_FOLDER_ROOT_STARRED -> CategoryFilter.Starred
    QUICK_FOLDER_ROOT_UNCATEGORIZED -> CategoryFilter.Uncategorized
    QUICK_FOLDER_ROOT_LOCAL_STARRED -> CategoryFilter.LocalStarred
    QUICK_FOLDER_ROOT_LOCAL_UNCATEGORIZED -> CategoryFilter.LocalUncategorized
    else -> CategoryFilter.All
}
