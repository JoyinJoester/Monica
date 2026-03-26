package takagi.ru.monica.keepass

import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.repository.KeePassCompatibilityBridge

class KeePassPasskeyUpdateExecutor(
    private val bridge: KeePassCompatibilityBridge?
) {
    suspend fun update(
        existing: PasskeyEntry,
        updated: PasskeyEntry,
        persistUpdate: suspend (PasskeyEntry) -> Unit
    ): Result<PasskeyEntry> {
        val keepassBridge = bridge
        if (keepassBridge == null) {
            persistUpdate(updated)
            return Result.success(updated)
        }

        val oldDatabaseId = existing.keepassDatabaseId
        val newDatabaseId = updated.keepassDatabaseId
        val oldManaged = oldDatabaseId != null && existing.passkeyMode == PasskeyEntry.MODE_KEEPASS_COMPAT
        val newManaged = newDatabaseId != null && updated.passkeyMode == PasskeyEntry.MODE_KEEPASS_COMPAT

        if (newManaged) {
            keepassBridge.upsertLegacyPasskeys(
                databaseId = newDatabaseId!!,
                passkeys = listOf(updated)
            ).getOrElse { return Result.failure(it) }
        }

        val shouldDeleteOld = oldManaged && (
            !newManaged ||
                oldDatabaseId != newDatabaseId ||
                existing.keepassGroupPath != updated.keepassGroupPath
            )
        if (shouldDeleteOld) {
            keepassBridge.deleteLegacyPasskeys(
                databaseId = oldDatabaseId!!,
                passkeys = listOf(existing)
            ).getOrElse { error ->
                if (newManaged && oldDatabaseId != newDatabaseId) {
                    keepassBridge.deleteLegacyPasskeys(
                        databaseId = newDatabaseId!!,
                        passkeys = listOf(updated)
                    )
                }
                return Result.failure(error)
            }
        }

        persistUpdate(updated)
        return Result.success(updated)
    }
}
