package takagi.ru.monica.data

sealed class PasskeyOwnership {
    object MonicaLocal : PasskeyOwnership()

    data class KeePass(
        val databaseId: Long
    ) : PasskeyOwnership()

    data class Bitwarden(
        val vaultId: Long?,
        val cipherId: String?
    ) : PasskeyOwnership()

    data class Mdbx(
        val databaseId: Long
    ) : PasskeyOwnership()

    data class Conflict(
        val hasKeePassBinding: Boolean,
        val hasBitwardenBinding: Boolean,
        val hasMdbxBinding: Boolean = false
    ) : PasskeyOwnership()
}

fun PasskeyEntry.resolveOwnership(): PasskeyOwnership {
    val hasKeePassBinding = keepassDatabaseId != null
    val hasBitwardenBinding = bitwardenVaultId != null || !bitwardenCipherId.isNullOrBlank()
    val hasMdbxBinding = mdbxDatabaseId != null
    val hasConcreteKeePassBinding = !keepassGroupPath.isNullOrBlank()
    val hasConcreteBitwardenBinding =
        !bitwardenCipherId.isNullOrBlank() ||
            !bitwardenFolderId.isNullOrBlank() ||
            !syncStatus.equals("NONE", ignoreCase = true)

    if (hasMdbxBinding && (hasKeePassBinding || hasBitwardenBinding)) {
        return PasskeyOwnership.Conflict(
            hasKeePassBinding = hasKeePassBinding,
            hasBitwardenBinding = hasBitwardenBinding,
            hasMdbxBinding = true
        )
    }

    return when {
        hasKeePassBinding && hasBitwardenBinding -> when {
            hasConcreteKeePassBinding && !hasConcreteBitwardenBinding -> PasskeyOwnership.KeePass(
                databaseId = keepassDatabaseId!!
            )

            hasConcreteBitwardenBinding && !hasConcreteKeePassBinding -> PasskeyOwnership.Bitwarden(
                vaultId = bitwardenVaultId,
                cipherId = bitwardenCipherId
            )

            !hasConcreteKeePassBinding && !hasConcreteBitwardenBinding -> PasskeyOwnership.MonicaLocal

            else -> PasskeyOwnership.Conflict(
                hasKeePassBinding = true,
                hasBitwardenBinding = true
            )
        }

        hasKeePassBinding -> PasskeyOwnership.KeePass(
            databaseId = keepassDatabaseId!!
        )

        hasBitwardenBinding -> if (hasConcreteBitwardenBinding) {
            PasskeyOwnership.Bitwarden(
                vaultId = bitwardenVaultId,
                cipherId = bitwardenCipherId
            )
        } else {
            PasskeyOwnership.MonicaLocal
        }

        hasMdbxBinding -> PasskeyOwnership.Mdbx(mdbxDatabaseId!!)

        else -> PasskeyOwnership.MonicaLocal
    }
}

fun PasskeyEntry.isKeePassOwned(): Boolean = resolveOwnership() is PasskeyOwnership.KeePass

fun PasskeyEntry.isMdbxOwned(): Boolean = resolveOwnership() is PasskeyOwnership.Mdbx

fun PasskeyEntry.isLocalOnlyPasskey(): Boolean = resolveOwnership() is PasskeyOwnership.MonicaLocal

fun PasskeyEntry.hasOwnershipConflict(): Boolean = resolveOwnership() is PasskeyOwnership.Conflict
