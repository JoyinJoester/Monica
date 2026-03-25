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

    data class Conflict(
        val hasKeePassBinding: Boolean,
        val hasBitwardenBinding: Boolean
    ) : PasskeyOwnership()
}

fun PasskeyEntry.resolveOwnership(): PasskeyOwnership {
    val hasKeePassBinding = keepassDatabaseId != null
    val hasBitwardenBinding = bitwardenVaultId != null || !bitwardenCipherId.isNullOrBlank()

    return when {
        hasKeePassBinding && hasBitwardenBinding -> PasskeyOwnership.Conflict(
            hasKeePassBinding = true,
            hasBitwardenBinding = true
        )

        hasKeePassBinding -> PasskeyOwnership.KeePass(
            databaseId = keepassDatabaseId!!
        )

        hasBitwardenBinding -> PasskeyOwnership.Bitwarden(
            vaultId = bitwardenVaultId,
            cipherId = bitwardenCipherId
        )

        else -> PasskeyOwnership.MonicaLocal
    }
}

fun PasskeyEntry.isKeePassOwned(): Boolean = resolveOwnership() is PasskeyOwnership.KeePass

fun PasskeyEntry.isLocalOnlyPasskey(): Boolean = resolveOwnership() is PasskeyOwnership.MonicaLocal

fun PasskeyEntry.hasOwnershipConflict(): Boolean = resolveOwnership() is PasskeyOwnership.Conflict
