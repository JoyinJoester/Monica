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

    data class Conflict(
        val hasKeePassBinding: Boolean,
        val hasBitwardenBinding: Boolean
    ) : PasswordOwnership()
}

fun isLocalPasswordOwnership(
    keepassDatabaseId: Long?,
    bitwardenVaultId: Long?
): Boolean = keepassDatabaseId == null && bitwardenVaultId == null

fun PasswordEntry.resolveOwnership(): PasswordOwnership {
    val hasKeePassBinding = keepassDatabaseId != null
    val hasBitwardenBinding = bitwardenVaultId != null

    return when {
        hasKeePassBinding && hasBitwardenBinding -> PasswordOwnership.Conflict(
            hasKeePassBinding = true,
            hasBitwardenBinding = true
        )

        hasKeePassBinding -> PasswordOwnership.KeePass(
            databaseId = keepassDatabaseId!!,
            entryUuid = keepassEntryUuid
        )

        hasBitwardenBinding -> PasswordOwnership.Bitwarden(
            vaultId = bitwardenVaultId!!,
            cipherId = bitwardenCipherId
        )

        else -> PasswordOwnership.MonicaLocal
    }
}
