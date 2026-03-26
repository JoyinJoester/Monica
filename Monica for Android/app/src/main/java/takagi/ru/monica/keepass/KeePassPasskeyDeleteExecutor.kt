package takagi.ru.monica.keepass

import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.repository.KeePassCompatibilityBridge

class KeePassPasskeyDeleteExecutor(
    private val bridge: KeePassCompatibilityBridge?
) {
    suspend fun delete(
        passkey: PasskeyEntry,
        deleteLocal: suspend (PasskeyEntry) -> Unit
    ): Result<Unit> {
        val databaseId = passkey.keepassDatabaseId
        if (databaseId != null && passkey.passkeyMode == PasskeyEntry.MODE_KEEPASS_COMPAT) {
            val keepassBridge = bridge ?: return Result.failure(
                IllegalStateException("KeePass bridge unavailable")
            )
            keepassBridge.deleteLegacyPasskeys(
                databaseId = databaseId,
                passkeys = listOf(passkey)
            ).getOrElse { return Result.failure(it) }
        }

        deleteLocal(passkey)
        return Result.success(Unit)
    }
}
