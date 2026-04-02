package takagi.ru.monica.data.model

import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem

sealed interface StorageTarget {
    val stableKey: String

    data class MonicaLocal(val categoryId: Long?) : StorageTarget {
        override val stableKey: String = "local:${categoryId ?: "root"}"
    }

    data class KeePass(
        val databaseId: Long,
        val groupPath: String?
    ) : StorageTarget {
        override val stableKey: String = "keepass:$databaseId:${groupPath.orEmpty()}"
    }

    data class Bitwarden(
        val vaultId: Long,
        val folderId: String?
    ) : StorageTarget {
        override val stableKey: String = "bitwarden:$vaultId:${folderId.orEmpty()}"
    }
}

fun PasswordEntry.toStorageTarget(): StorageTarget = when {
    bitwardenVaultId != null -> StorageTarget.Bitwarden(
        vaultId = bitwardenVaultId,
        folderId = bitwardenFolderId
    )
    keepassDatabaseId != null -> StorageTarget.KeePass(
        databaseId = keepassDatabaseId,
        groupPath = keepassGroupPath
    )
    else -> StorageTarget.MonicaLocal(categoryId = categoryId)
}

fun SecureItem.toStorageTarget(): StorageTarget = when {
    bitwardenVaultId != null -> StorageTarget.Bitwarden(
        vaultId = bitwardenVaultId,
        folderId = bitwardenFolderId
    )
    keepassDatabaseId != null -> StorageTarget.KeePass(
        databaseId = keepassDatabaseId,
        groupPath = keepassGroupPath
    )
    else -> StorageTarget.MonicaLocal(categoryId = categoryId)
}

fun StorageTarget.applyToPasswordEntry(
    entry: PasswordEntry,
    replicaGroupId: String? = entry.replicaGroupId
): PasswordEntry {
    return when (this) {
        is StorageTarget.MonicaLocal -> entry.copy(
            categoryId = categoryId,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            keepassEntryUuid = null,
            keepassGroupUuid = null,
            bitwardenVaultId = null,
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            replicaGroupId = replicaGroupId
        )
        is StorageTarget.KeePass -> entry.copy(
            categoryId = null,
            keepassDatabaseId = databaseId,
            keepassGroupPath = groupPath,
            keepassEntryUuid = null,
            keepassGroupUuid = null,
            bitwardenVaultId = null,
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            replicaGroupId = replicaGroupId
        )
        is StorageTarget.Bitwarden -> entry.copy(
            categoryId = null,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            keepassEntryUuid = null,
            keepassGroupUuid = null,
            bitwardenVaultId = vaultId,
            bitwardenCipherId = null,
            bitwardenFolderId = folderId,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            replicaGroupId = replicaGroupId
        )
    }
}

fun StorageTarget.applyToSecureItem(
    item: SecureItem,
    replicaGroupId: String? = item.replicaGroupId
): SecureItem {
    return when (this) {
        is StorageTarget.MonicaLocal -> item.copy(
            categoryId = categoryId,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            keepassEntryUuid = null,
            keepassGroupUuid = null,
            bitwardenVaultId = null,
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            syncStatus = "NONE",
            replicaGroupId = replicaGroupId
        )
        is StorageTarget.KeePass -> item.copy(
            categoryId = null,
            keepassDatabaseId = databaseId,
            keepassGroupPath = groupPath,
            keepassEntryUuid = null,
            keepassGroupUuid = null,
            bitwardenVaultId = null,
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            syncStatus = "NONE",
            replicaGroupId = replicaGroupId
        )
        is StorageTarget.Bitwarden -> item.copy(
            categoryId = null,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            keepassEntryUuid = null,
            keepassGroupUuid = null,
            bitwardenVaultId = vaultId,
            bitwardenCipherId = null,
            bitwardenFolderId = folderId,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            syncStatus = "PENDING",
            replicaGroupId = replicaGroupId
        )
    }
}
