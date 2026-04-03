package takagi.ru.monica.data

sealed class SecureItemOwnership {
    object MonicaLocal : SecureItemOwnership()

    data class KeePass(
        val databaseId: Long,
        val entryUuid: String?
    ) : SecureItemOwnership()

    data class Bitwarden(
        val vaultId: Long?,
        val cipherId: String?
    ) : SecureItemOwnership()

    data class Conflict(
        val hasKeePassBinding: Boolean,
        val hasBitwardenBinding: Boolean
    ) : SecureItemOwnership()
}

fun SecureItem.resolveOwnership(): SecureItemOwnership {
    val hasKeePassBinding = keepassDatabaseId != null
    val hasBitwardenBinding = bitwardenVaultId != null || !bitwardenCipherId.isNullOrBlank()
    val hasConcreteKeePassBinding =
        !keepassEntryUuid.isNullOrBlank() ||
            !keepassGroupUuid.isNullOrBlank() ||
            !keepassGroupPath.isNullOrBlank()
    val hasConcreteBitwardenBinding =
        !bitwardenCipherId.isNullOrBlank() ||
            !bitwardenRevisionDate.isNullOrBlank() ||
            !bitwardenFolderId.isNullOrBlank() ||
            bitwardenLocalModified ||
            !syncStatus.equals("NONE", ignoreCase = true)

    return when {
        hasKeePassBinding && hasBitwardenBinding -> when {
            hasConcreteKeePassBinding && !hasConcreteBitwardenBinding -> SecureItemOwnership.KeePass(
                databaseId = keepassDatabaseId!!,
                entryUuid = keepassEntryUuid
            )

            hasConcreteBitwardenBinding && !hasConcreteKeePassBinding -> SecureItemOwnership.Bitwarden(
                vaultId = bitwardenVaultId,
                cipherId = bitwardenCipherId
            )

            !hasConcreteKeePassBinding && !hasConcreteBitwardenBinding -> SecureItemOwnership.MonicaLocal

            else -> SecureItemOwnership.Conflict(
                hasKeePassBinding = true,
                hasBitwardenBinding = true
            )
        }

        hasKeePassBinding -> SecureItemOwnership.KeePass(
            databaseId = keepassDatabaseId!!,
            entryUuid = keepassEntryUuid
        )
        hasBitwardenBinding -> SecureItemOwnership.Bitwarden(
            vaultId = bitwardenVaultId,
            cipherId = bitwardenCipherId
        )
        else -> SecureItemOwnership.MonicaLocal
    }
}

fun SecureItem.hasBitwardenBinding(): Boolean {
    return bitwardenVaultId != null || !bitwardenCipherId.isNullOrBlank()
}

fun SecureItem.asMonicaLocalCopy(categoryId: Long?): SecureItem {
    return copy(
        id = 0,
        categoryId = categoryId,
        keepassDatabaseId = null,
        keepassGroupPath = null,
        keepassEntryUuid = null,
        keepassGroupUuid = null,
        isDeleted = false,
        deletedAt = null,
        bitwardenVaultId = null,
        bitwardenCipherId = null,
        bitwardenFolderId = null,
        bitwardenRevisionDate = null,
        bitwardenLocalModified = false,
        syncStatus = "NONE"
    )
}

fun SecureItem.isBitwardenOwned(): Boolean = resolveOwnership() is SecureItemOwnership.Bitwarden

fun SecureItem.isKeePassOwned(): Boolean = resolveOwnership() is SecureItemOwnership.KeePass

fun SecureItem.hasOwnershipConflict(): Boolean = resolveOwnership() is SecureItemOwnership.Conflict

fun SecureItem.isLocalOnlyItem(): Boolean = resolveOwnership() is SecureItemOwnership.MonicaLocal
