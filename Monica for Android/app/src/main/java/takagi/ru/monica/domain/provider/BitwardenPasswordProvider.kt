package takagi.ru.monica.domain.provider

import android.util.Log
import takagi.ru.monica.data.PasswordArchiveSyncMeta
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.ui.model.SecretValueState

class BitwardenPasswordProvider(
    private val decodePassword: (String) -> String?,
    private val encryptPassword: (String) -> String,
    private val loadOfflineCachedSecret: (PasswordEntry) -> String? = { null },
    private val rememberOfflineCachedSecret: (PasswordEntry, String) -> Unit = { _, _ -> }
) : PasswordProvider {
    override fun supports(entry: PasswordEntry): Boolean = entry.bitwardenVaultId != null

    override fun sourceOf(entry: PasswordEntry): PasswordSource {
        val hasRecoverableRemoteBinding =
            entry.bitwardenVaultId != null && !entry.bitwardenCipherId.isNullOrBlank()
        if (!hasRecoverableRemoteBinding) {
            return PasswordSource.Local
        }
        return PasswordSource.Bitwarden(
            vaultId = entry.bitwardenVaultId,
            cipherId = entry.bitwardenCipherId
        )
    }

    override fun inspectSecret(entry: PasswordEntry): SecretValueState {
        if (entry.password.isEmpty()) return SecretValueState.Empty
        val decoded = decodePassword(entry.password)
        return when {
            decoded == null -> {
                val cached = loadOfflineCachedSecret(entry)
                if (cached.isNullOrEmpty()) {
                    SecretValueState.Unreadable(sourceOf(entry))
                } else {
                    SecretValueState.Available(cached)
                }
            }
            decoded.isEmpty() -> SecretValueState.Empty
            else -> {
                rememberOfflineCachedSecret(entry, decoded)
                SecretValueState.Available(decoded)
            }
        }
    }

    override fun commandPolicy(entry: PasswordEntry): PasswordCommandPolicy {
        return PasswordCommandPolicy(
            archiveProviderType = PasswordArchiveSyncMeta.PROVIDER_BITWARDEN_NATIVE,
            shouldMarkPendingRemoteMutation = true,
            usesRemoteDeleteQueue = !entry.bitwardenCipherId.isNullOrBlank()
        )
    }

    override fun resolvePasswordForStorage(
        existingEntry: PasswordEntry?,
        pendingEntry: PasswordEntry,
        incomingPassword: String
    ): String {
        if (existingEntry == null || incomingPassword.isNotEmpty()) {
            return encryptPassword(incomingPassword)
        }
        if (existingEntry.bitwardenVaultId == null || existingEntry.bitwardenCipherId.isNullOrBlank()) {
            return encryptPassword(incomingPassword)
        }
        if (existingEntry.password.isBlank()) {
            return encryptPassword(incomingPassword)
        }
        val decodedExisting = decodePassword(existingEntry.password)
        if (decodedExisting != null) {
            return encryptPassword(incomingPassword)
        }
        Log.w(
            "BitwardenPasswordProvider",
            "Preserve unreadable Bitwarden password for entryId=${existingEntry.id}"
        )
        return existingEntry.password
    }
}
