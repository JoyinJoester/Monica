package takagi.ru.monica.domain.provider

import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.ui.model.SecretValueState

interface PasswordProvider {
    fun supports(entry: PasswordEntry): Boolean
    fun sourceOf(entry: PasswordEntry): PasswordSource
    fun inspectSecret(entry: PasswordEntry): SecretValueState
    fun commandPolicy(entry: PasswordEntry): PasswordCommandPolicy
    fun resolvePasswordForStorage(
        existingEntry: PasswordEntry?,
        pendingEntry: PasswordEntry,
        incomingPassword: String
    ): String
}
