package takagi.ru.monica.domain.provider

import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.ui.model.SecretValueState

class DefaultPasswordProvider(
    private val decodePassword: (String) -> String?,
    private val encryptPassword: (String) -> String
) : PasswordProvider {
    override fun supports(entry: PasswordEntry): Boolean = entry.isLocalOnlyEntry()

    override fun sourceOf(entry: PasswordEntry): PasswordSource = PasswordSource.Local

    override fun inspectSecret(entry: PasswordEntry): SecretValueState {
        if (entry.password.isEmpty()) return SecretValueState.Empty
        val decoded = decodePassword(entry.password) ?: return SecretValueState.Unreadable(sourceOf(entry))
        return if (decoded.isEmpty()) {
            SecretValueState.Empty
        } else {
            SecretValueState.Available(decoded)
        }
    }

    override fun commandPolicy(entry: PasswordEntry): PasswordCommandPolicy {
        return PasswordCommandPolicy(
            archiveProviderType = takagi.ru.monica.data.PasswordArchiveSyncMeta.PROVIDER_LOCAL,
            shouldMarkPendingRemoteMutation = false,
            usesRemoteDeleteQueue = false
        )
    }

    override fun resolvePasswordForStorage(
        existingEntry: PasswordEntry?,
        pendingEntry: PasswordEntry,
        incomingPassword: String
    ): String {
        return encryptPassword(incomingPassword)
    }
}
