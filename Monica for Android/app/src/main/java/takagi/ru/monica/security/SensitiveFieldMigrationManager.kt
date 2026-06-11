package takagi.ru.monica.security

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.CardWalletDataCodec
import takagi.ru.monica.util.TotpDataResolver

/**
 * Incrementally wraps legacy local plaintext sensitive fields in Monica ciphertext.
 *
 * This migrates only the local Room cache shape. It intentionally updates DAO
 * columns directly instead of going through repositories, so it cannot be
 * interpreted as a user edit by MDBX, Bitwarden, KeePass, or backup sync.
 */
class SensitiveFieldMigrationManager(
    context: Context,
    private val database: PasswordDatabase,
    private val securityManager: SecurityManager
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val passwordEntryDao = database.passwordEntryDao()
    private val secureItemDao = database.secureItemDao()

    suspend fun runUnlockedSmallBatch() {
        migrationMutex.withLock {
            if (!securityManager.canAccessVaultMaterialNow()) {
                Log.d(TAG, "Sensitive field migration skipped: vault material is not available")
                return
            }

            ensureCurrentVersion()

            migrateSecureItemData(
                domain = DOMAIN_TOTP_ITEM_DATA,
                itemType = ItemType.TOTP,
                canMigratePlainValue = { item, plainValue ->
                    TotpDataResolver.parseStoredItemData(
                        itemData = plainValue,
                        fallbackIssuer = item.title,
                        fallbackAccountName = item.title
                    ) != null
                }
            )
            migratePasswordAuthenticatorKeys()
            migrateSecureItemData(
                domain = DOMAIN_BANK_CARD_ITEM_DATA,
                itemType = ItemType.BANK_CARD,
                canMigratePlainValue = { _, plainValue ->
                    CardWalletDataCodec.parseBankCardData(plainValue) != null
                }
            )
            migrateSecureItemData(
                domain = DOMAIN_DOCUMENT_ITEM_DATA,
                itemType = ItemType.DOCUMENT,
                canMigratePlainValue = { _, plainValue ->
                    CardWalletDataCodec.parseDocumentData(plainValue) != null
                }
            )
        }
    }

    private suspend fun migratePasswordAuthenticatorKeys() {
        migrateDomain(
            domain = DOMAIN_PASSWORD_AUTHENTICATOR_KEY,
            loadBatch = { afterId ->
                passwordEntryDao.getAuthenticatorKeyMigrationBatch(afterId, BATCH_SIZE)
            },
            processRecord = process@{ entry ->
                val rawValue = entry.authenticatorKey
                val encrypted = prepareEncryptedLegacyValue(
                    domain = DOMAIN_PASSWORD_AUTHENTICATOR_KEY,
                    id = entry.id,
                    rawValue = rawValue,
                    canMigratePlainValue = { plainValue ->
                        TotpDataResolver.fromAuthenticatorKey(
                            rawKey = plainValue,
                            fallbackIssuer = entry.title,
                            fallbackAccountName = entry.username
                        ) != null
                    }
                ) ?: return@process MigrationRecordResult.Processed

                passwordEntryDao.updateAuthenticatorKey(entry.id, encrypted)
                MigrationRecordResult.Processed
            }
        )
    }

    private suspend fun migrateSecureItemData(
        domain: String,
        itemType: ItemType,
        canMigratePlainValue: (SecureItem, String) -> Boolean
    ) {
        migrateDomain(
            domain = domain,
            loadBatch = { afterId ->
                secureItemDao.getItemDataMigrationBatch(itemType, afterId, BATCH_SIZE)
            },
            processRecord = process@{ item ->
                val encrypted = prepareEncryptedLegacyValue(
                    domain = domain,
                    id = item.id,
                    rawValue = item.itemData,
                    canMigratePlainValue = { plainValue ->
                        canMigratePlainValue(item, plainValue)
                    }
                ) ?: return@process MigrationRecordResult.Processed

                secureItemDao.updateItemData(item.id, encrypted)
                MigrationRecordResult.Processed
            }
        )
    }

    private suspend fun <T> migrateDomain(
        domain: String,
        loadBatch: suspend (afterId: Long) -> List<T>,
        processRecord: suspend (T) -> MigrationRecordResult
    ) where T : Any {
        if (prefs.getBoolean(completedKey(domain), false)) return

        var afterId = prefs.getLong(lastIdKey(domain), 0L)
        repeat(MAX_BATCHES_PER_DOMAIN) {
            val batch = loadBatch(afterId)
            if (batch.isEmpty()) {
                prefs.edit()
                    .putBoolean(completedKey(domain), true)
                    .putLong(blockedIdKey(domain), 0L)
                    .apply()
                Log.d(TAG, "Sensitive field migration completed domain=$domain")
                return
            }

            for (record in batch) {
                val id = recordId(record)
                val result = runCatching { processRecord(record) }
                    .getOrElse { error ->
                        recordBlocked(domain, id, error)
                        return
                    }

                when (result) {
                    MigrationRecordResult.Processed -> {
                        afterId = id
                        prefs.edit()
                            .putLong(lastIdKey(domain), afterId)
                            .putLong(blockedIdKey(domain), 0L)
                            .apply()
                    }
                    is MigrationRecordResult.Blocked -> {
                        recordBlocked(domain, id, result.error)
                        return
                    }
                }
            }
        }
    }

    private fun prepareEncryptedLegacyValue(
        domain: String,
        id: Long,
        rawValue: String,
        canMigratePlainValue: (String) -> Boolean
    ): String? {
        if (rawValue.isBlank()) return null
        if (securityManager.looksLikeMonicaCiphertext(rawValue)) return null

        val plainValue = resolveLegacyPlainCandidate(rawValue)
        val canMigrate = runCatching { canMigratePlainValue(plainValue) }.getOrDefault(false)
        if (!canMigrate) {
            Log.d(TAG, "Sensitive field migration skipped unsupported value domain=$domain id=$id")
            return null
        }

        return runCatching {
            val encrypted = securityManager.encryptDataLegacyCompat(plainValue)
            val roundTrip = securityManager.decryptData(encrypted)
            check(roundTrip == plainValue) { "ciphertext round-trip mismatch" }
            encrypted
        }.getOrElse { error ->
            throw SensitiveFieldMigrationException(domain, id, error)
        }
    }

    private fun resolveLegacyPlainCandidate(value: String): String {
        return runCatching { securityManager.decryptData(value) }.getOrDefault(value)
    }

    private fun recordBlocked(domain: String, id: Long, error: Throwable) {
        val failureCount = prefs.getInt(failureCountKey(domain), 0) + 1
        prefs.edit()
            .putLong(blockedIdKey(domain), id)
            .putInt(failureCountKey(domain), failureCount)
            .putString(lastErrorKey(domain), error.javaClass.simpleName)
            .apply()
        Log.w(
            TAG,
            "Sensitive field migration blocked domain=$domain id=$id failures=$failureCount error=${error.javaClass.simpleName}"
        )
    }

    private fun ensureCurrentVersion() {
        val storedVersion = prefs.getInt(KEY_VERSION, 0)
        if (storedVersion == MIGRATION_VERSION) return

        prefs.edit()
            .clear()
            .putInt(KEY_VERSION, MIGRATION_VERSION)
            .apply()
    }

    private fun recordId(record: Any): Long {
        return when (record) {
            is PasswordEntry -> record.id
            is SecureItem -> record.id
            else -> error("Unsupported migration record type: ${record.javaClass.name}")
        }
    }

    private fun lastIdKey(domain: String): String = "${domain}_last_id"
    private fun completedKey(domain: String): String = "${domain}_completed"
    private fun blockedIdKey(domain: String): String = "${domain}_blocked_id"
    private fun failureCountKey(domain: String): String = "${domain}_failure_count"
    private fun lastErrorKey(domain: String): String = "${domain}_last_error"

    private sealed interface MigrationRecordResult {
        object Processed : MigrationRecordResult
        data class Blocked(val error: Throwable) : MigrationRecordResult
    }

    private class SensitiveFieldMigrationException(
        domain: String,
        id: Long,
        cause: Throwable
    ) : RuntimeException("Sensitive field migration failed for domain=$domain id=$id", cause)

    companion object {
        private const val TAG = "SensitiveMigration"
        private const val PREFS_NAME = "monica_sensitive_field_migration"
        private const val KEY_VERSION = "migration_version"
        private const val MIGRATION_VERSION = 1
        private const val BATCH_SIZE = 3
        private const val MAX_BATCHES_PER_DOMAIN = 1

        private const val DOMAIN_TOTP_ITEM_DATA = "totp_item_data"
        private const val DOMAIN_PASSWORD_AUTHENTICATOR_KEY = "password_authenticator_key"
        private const val DOMAIN_BANK_CARD_ITEM_DATA = "bank_card_item_data"
        private const val DOMAIN_DOCUMENT_ITEM_DATA = "document_item_data"

        private val migrationMutex = Mutex()
    }
}
