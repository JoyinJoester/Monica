package takagi.ru.monica.domain.provider

import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.ui.model.SecretValueState

class PasswordProviderRegistry(
    private val providers: List<PasswordProvider>,
    private val fallbackProvider: PasswordProvider
) {
    private fun conflictSourceOf(entry: PasswordEntry): PasswordSource.Conflict {
        return PasswordSource.Conflict(
            hasKeePassBinding = entry.keepassDatabaseId != null,
            hasBitwardenBinding = entry.bitwardenVaultId != null
        )
    }

    fun providerFor(entry: PasswordEntry): PasswordProvider {
        return providers.firstOrNull { it.supports(entry) } ?: fallbackProvider
    }

    fun sourceOf(entry: PasswordEntry): PasswordSource {
        if (entry.hasOwnershipConflict()) return conflictSourceOf(entry)
        return providerFor(entry).sourceOf(entry)
    }

    fun inspectSecret(entry: PasswordEntry): SecretValueState {
        if (entry.hasOwnershipConflict()) {
            return SecretValueState.Unreadable(conflictSourceOf(entry))
        }
        return providerFor(entry).inspectSecret(entry)
    }

    fun commandPolicy(entry: PasswordEntry): PasswordCommandPolicy = providerFor(entry).commandPolicy(entry)

    fun resolvePasswordForStorage(
        existingEntry: PasswordEntry?,
        pendingEntry: PasswordEntry,
        incomingPassword: String
    ): String {
        val provider = existingEntry?.let(::providerFor) ?: providerFor(pendingEntry)
        return provider.resolvePasswordForStorage(
            existingEntry = existingEntry,
            pendingEntry = pendingEntry,
            incomingPassword = incomingPassword
        )
    }
}
