package takagi.ru.monica.passkey

import takagi.ru.monica.data.PasskeyEntry

object PasskeyCredentialDiscoveryPolicy {
    private const val SYNC_STATUS_REFERENCE = "REFERENCE"

    fun isUsable(passkey: PasskeyEntry): Boolean {
        if (passkey.syncStatus == SYNC_STATUS_REFERENCE) return false
        return passkey.privateKeyAlias.isNotBlank()
    }

    fun filterUsable(passkeys: List<PasskeyEntry>): List<PasskeyEntry> {
        return passkeys.filter(::isUsable)
    }

    fun filterDiscoverable(passkeys: List<PasskeyEntry>): List<PasskeyEntry> {
        return passkeys.filter { it.isDiscoverable }
    }

    internal fun filterByAllowedCredentialIds(
        candidates: List<PasskeyEntry>,
        allowedCredentialIds: Set<String>,
        normalizer: (String) -> String? = { PasskeyCredentialIdCodec.normalize(it) },
    ): List<PasskeyEntry> {
        if (allowedCredentialIds.isEmpty()) return candidates
        return candidates.filter { passkey ->
            val normalizedId = normalizer(passkey.credentialId)
            normalizedId != null && normalizedId in allowedCredentialIds
        }
    }
}
