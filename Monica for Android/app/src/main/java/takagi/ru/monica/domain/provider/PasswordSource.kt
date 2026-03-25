package takagi.ru.monica.domain.provider

sealed class PasswordSource {
    object Local : PasswordSource()
    data class Bitwarden(
        val vaultId: Long?,
        val cipherId: String?
    ) : PasswordSource()

    data class KeePass(
        val databaseId: Long?,
        val entryUuid: String?
    ) : PasswordSource()

    data class Conflict(
        val hasKeePassBinding: Boolean,
        val hasBitwardenBinding: Boolean
    ) : PasswordSource()
}
