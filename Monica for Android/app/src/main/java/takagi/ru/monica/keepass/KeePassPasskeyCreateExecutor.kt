package takagi.ru.monica.keepass

import android.util.Log
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.repository.KeePassCompatibilityBridge

class KeePassPasskeyCreateExecutor(
    private val bridge: KeePassCompatibilityBridge?
) {
    suspend fun create(
        passkey: PasskeyEntry,
        insertPasskey: suspend (PasskeyEntry) -> Unit,
        rollbackPasskey: suspend (String) -> Unit
    ): Boolean {
        insertPasskey(passkey)
        val databaseId = passkey.keepassDatabaseId
        if (databaseId == null || passkey.passkeyMode != PasskeyEntry.MODE_KEEPASS_COMPAT) {
            return true
        }

        val keepassBridge = bridge
        if (keepassBridge == null) {
            Log.w(TAG, "Create passkey ${passkey.credentialId} skipped KeePass sync because bridge is unavailable")
            rollbackPasskey(passkey.credentialId)
            return false
        }

        val syncResult = keepassBridge.upsertLegacyPasskeys(
            databaseId = databaseId,
            passkeys = listOf(passkey)
        )
        if (syncResult.isFailure) {
            rollbackPasskey(passkey.credentialId)
            Log.e(TAG, "KeePass passkey write failed: ${syncResult.exceptionOrNull()?.message}")
            return false
        }

        return true
    }

    private companion object {
        const val TAG = "KeePassPasskeyCreate"
    }
}
