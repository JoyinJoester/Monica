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

    data class Mdbx(
        val databaseId: Long,
        val folderId: String? = null
    ) : StorageTarget {
        override val stableKey: String = "mdbx:$databaseId:${folderId.orEmpty()}"
    }
}

fun StorageTarget.storageScopeKey(): String = when (this) {
    is StorageTarget.MonicaLocal -> "local"
    is StorageTarget.KeePass -> "keepass:$databaseId"
    is StorageTarget.Bitwarden -> "bitwarden:$vaultId"
    is StorageTarget.Mdbx -> "mdbx:$databaseId"
}

fun StorageTarget.uncategorizedPeer(): StorageTarget = when (this) {
    is StorageTarget.MonicaLocal -> StorageTarget.MonicaLocal(null)
    is StorageTarget.KeePass -> StorageTarget.KeePass(databaseId, null)
    is StorageTarget.Bitwarden -> StorageTarget.Bitwarden(vaultId, null)
    is StorageTarget.Mdbx -> StorageTarget.Mdbx(databaseId)
}

fun StorageTarget.isUncategorizedTarget(): Boolean = when (this) {
    is StorageTarget.MonicaLocal -> categoryId == null
    is StorageTarget.KeePass -> groupPath.isNullOrBlank()
    is StorageTarget.Bitwarden -> folderId.isNullOrBlank()
    is StorageTarget.Mdbx -> folderId.isNullOrBlank()
}

fun List<StorageTarget>.normalizedStorageTargets(
    defaultTarget: StorageTarget = StorageTarget.MonicaLocal(null)
): List<StorageTarget> = distinctBy(StorageTarget::stableKey).ifEmpty { listOf(defaultTarget) }

fun List<StorageTarget>.withStorageTargetSelected(
    target: StorageTarget,
    defaultTarget: StorageTarget = StorageTarget.MonicaLocal(null)
): List<StorageTarget> {
    val current = normalizedStorageTargets(defaultTarget)
    if (current.any { it.stableKey == target.stableKey }) return current
    val withoutImplicitFallback = if (target.isUncategorizedTarget()) {
        current
    } else {
        current.filterNot {
            it.storageScopeKey() == target.storageScopeKey() && it.isUncategorizedTarget()
        }
    }
    return (withoutImplicitFallback + target).normalizedStorageTargets(defaultTarget)
}

fun List<StorageTarget>.withoutStorageTarget(
    target: StorageTarget,
    defaultTarget: StorageTarget = StorageTarget.MonicaLocal(null)
): List<StorageTarget> {
    val current = normalizedStorageTargets(defaultTarget)
    val remaining = current.filterNot { it.stableKey == target.stableKey }
    if (remaining.size == current.size) return current
    val hasSameScopeTarget = remaining.any { it.storageScopeKey() == target.storageScopeKey() }
    val fallback = target.uncategorizedPeer()
    val next = when {
        hasSameScopeTarget -> remaining
        fallback.stableKey != target.stableKey -> remaining + fallback
        else -> remaining
    }
    return next.normalizedStorageTargets(defaultTarget)
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
    mdbxDatabaseId != null -> StorageTarget.Mdbx(
        databaseId = mdbxDatabaseId,
        folderId = mdbxFolderId
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
    mdbxDatabaseId != null -> StorageTarget.Mdbx(
        databaseId = mdbxDatabaseId,
        folderId = mdbxFolderId
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
            mdbxDatabaseId = null,
            mdbxFolderId = null,
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
            mdbxDatabaseId = null,
            mdbxFolderId = null,
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
            mdbxDatabaseId = null,
            mdbxFolderId = null,
            bitwardenVaultId = vaultId,
            bitwardenCipherId = null,
            bitwardenFolderId = folderId,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            replicaGroupId = replicaGroupId
        )
        is StorageTarget.Mdbx -> entry.copy(
            categoryId = null,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            keepassEntryUuid = null,
            keepassGroupUuid = null,
            bitwardenVaultId = null,
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            mdbxDatabaseId = databaseId,
            mdbxFolderId = folderId,
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
            mdbxDatabaseId = null,
            mdbxFolderId = null,
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
            mdbxDatabaseId = null,
            mdbxFolderId = null,
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
            mdbxDatabaseId = null,
            mdbxFolderId = null,
            bitwardenVaultId = vaultId,
            bitwardenCipherId = null,
            bitwardenFolderId = folderId,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            syncStatus = "PENDING",
            replicaGroupId = replicaGroupId
        )
        is StorageTarget.Mdbx -> item.copy(
            categoryId = null,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            keepassEntryUuid = null,
            keepassGroupUuid = null,
            bitwardenVaultId = null,
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            mdbxDatabaseId = databaseId,
            mdbxFolderId = folderId,
            syncStatus = "NONE",
            replicaGroupId = replicaGroupId
        )
    }
}
