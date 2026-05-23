package takagi.ru.monica.data

sealed class PasswordOwnership {
    object MonicaLocal : PasswordOwnership()

    data class KeePass(
        val databaseId: Long,
        val entryUuid: String?
    ) : PasswordOwnership()

    data class Bitwarden(
        val vaultId: Long,
        val cipherId: String?
    ) : PasswordOwnership()

    data class Mdbx(
        val databaseId: Long
    ) : PasswordOwnership()

    data class Conflict(
        val hasKeePassBinding: Boolean,
        val hasBitwardenBinding: Boolean,
        val hasMdbxBinding: Boolean = false
    ) : PasswordOwnership()
}

fun isLocalPasswordOwnership(
    keepassDatabaseId: Long?,
    bitwardenVaultId: Long?,
    mdbxDatabaseId: Long? = null
): Boolean = keepassDatabaseId == null && bitwardenVaultId == null && mdbxDatabaseId == null

fun PasswordEntry.resolveOwnership(): PasswordOwnership {
    val hasKeePassBinding = keepassDatabaseId != null
    val hasBitwardenBinding = bitwardenVaultId != null
    val hasMdbxBinding = mdbxDatabaseId != null
    val hasConcreteKeePassBinding =
        !keepassEntryUuid.isNullOrBlank() ||
            !keepassGroupUuid.isNullOrBlank() ||
            !keepassGroupPath.isNullOrBlank()
    val hasConcreteBitwardenBinding =
        !bitwardenCipherId.isNullOrBlank() ||
            !bitwardenRevisionDate.isNullOrBlank() ||
            !bitwardenFolderId.isNullOrBlank() ||
            bitwardenLocalModified

    if (hasMdbxBinding && (hasKeePassBinding || hasBitwardenBinding)) {
        return PasswordOwnership.Conflict(
            hasKeePassBinding = hasKeePassBinding,
            hasBitwardenBinding = hasBitwardenBinding,
            hasMdbxBinding = true
        )
    }

    return when {
        hasKeePassBinding && hasBitwardenBinding -> when {
            hasConcreteKeePassBinding && !hasConcreteBitwardenBinding -> PasswordOwnership.KeePass(
                databaseId = keepassDatabaseId!!,
                entryUuid = keepassEntryUuid
            )

            hasConcreteBitwardenBinding && !hasConcreteKeePassBinding -> PasswordOwnership.Bitwarden(
                vaultId = bitwardenVaultId!!,
                cipherId = bitwardenCipherId
            )

            !hasConcreteKeePassBinding && !hasConcreteBitwardenBinding -> PasswordOwnership.MonicaLocal

            else -> PasswordOwnership.Conflict(
                hasKeePassBinding = true,
                hasBitwardenBinding = true
            )
        }

        hasMdbxBinding -> PasswordOwnership.Mdbx(mdbxDatabaseId!!)

        hasKeePassBinding -> PasswordOwnership.KeePass(
            databaseId = keepassDatabaseId!!,
            entryUuid = keepassEntryUuid
        )

        hasBitwardenBinding -> if (hasConcreteBitwardenBinding) {
            PasswordOwnership.Bitwarden(
                vaultId = bitwardenVaultId!!,
                cipherId = bitwardenCipherId
            )
        } else {
            PasswordOwnership.MonicaLocal
        }

        else -> PasswordOwnership.MonicaLocal
    }
}
