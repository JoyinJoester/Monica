package takagi.ru.monica.bitwarden.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.bitwarden.api.BitwardenApiManager
import takagi.ru.monica.bitwarden.api.BitwardenVaultApi
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.util.TotpDataResolver
import java.util.Date

data class BitwardenHistoricalTotpRepairResult(
    val normalizedTotpItems: Int,
    val queuedTotpItemsForSync: Int,
    val normalizedPasswords: Int,
    val queuedPasswordsForSync: Int,
    val skippedItems: Int
) {
    val normalizedCount: Int
        get() = normalizedTotpItems + normalizedPasswords

    val queuedForSyncCount: Int
        get() = queuedTotpItemsForSync + queuedPasswordsForSync
}

class BitwardenHistoricalTotpRepairService(
    context: Context,
    private val apiManager: BitwardenApiManager = BitwardenApiManager()
) {
    companion object {
        private const val TAG = "BwHistoricalTotpRepair"
    }

    private val database = PasswordDatabase.getDatabase(context)
    private val passwordEntryDao = database.passwordEntryDao()
    private val secureItemDao = database.secureItemDao()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun repairHistoricalTotp(
        vault: BitwardenVault,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey
    ): BitwardenHistoricalTotpRepairResult = withContext(Dispatchers.IO) {
        val now = Date()
        val vaultApi = apiManager.getVaultApi(vault)

        var normalizedTotpItems = 0
        var queuedTotpItemsForSync = 0
        var normalizedPasswords = 0
        var queuedPasswordsForSync = 0
        var skippedItems = 0

        val secureItems = secureItemDao.getByBitwardenVaultId(vault.id)
            .filter { it.itemType == ItemType.TOTP && !it.isDeleted && !it.bitwardenCipherId.isNullOrBlank() }
        secureItems.forEach { item ->
            when (val outcome = repairStandaloneTotpItem(item, vaultApi, accessToken, symmetricKey, now)) {
                is RepairOutcome.Repaired -> {
                    if (outcome.localChanged) normalizedTotpItems += 1
                    if (outcome.queuedForSync) queuedTotpItemsForSync += 1
                }
                RepairOutcome.Skipped -> skippedItems += 1
            }
        }

        val passwordEntries = passwordEntryDao.getByBitwardenVaultId(vault.id)
            .filter { !it.isDeleted && !it.bitwardenCipherId.isNullOrBlank() && it.bitwardenCipherType == 1 }
        passwordEntries.forEach { entry ->
            when (val outcome = repairBoundPasswordTotp(entry, vaultApi, accessToken, symmetricKey, now)) {
                is RepairOutcome.Repaired -> {
                    if (outcome.localChanged) normalizedPasswords += 1
                    if (outcome.queuedForSync) queuedPasswordsForSync += 1
                }
                RepairOutcome.Skipped -> skippedItems += 1
            }
        }

        BitwardenHistoricalTotpRepairResult(
            normalizedTotpItems = normalizedTotpItems,
            queuedTotpItemsForSync = queuedTotpItemsForSync,
            normalizedPasswords = normalizedPasswords,
            queuedPasswordsForSync = queuedPasswordsForSync,
            skippedItems = skippedItems
        )
    }

    private suspend fun repairStandaloneTotpItem(
        item: SecureItem,
        vaultApi: BitwardenVaultApi,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey,
        now: Date
    ): RepairOutcome {
        val remoteCipherId = item.bitwardenCipherId ?: return repairStandaloneTotpItemFromLocal(item, now)
        val remote = fetchRemoteTotpContext(vaultApi, accessToken, remoteCipherId, symmetricKey)
            ?: return repairStandaloneTotpItemFromLocal(item, now)

        val normalizedData = TotpDataResolver.fromAuthenticatorKey(
            rawKey = remote.totpRaw,
            fallbackIssuer = remote.displayName,
            fallbackAccountName = remote.username
        ) ?: return repairStandaloneTotpItemFromLocal(item, now)

        val normalizedItemData = json.encodeToString(normalizedData)
        val normalizedPayload = TotpDataResolver.toBitwardenPayload(remote.displayName, normalizedData)
        val shouldQueueRemoteRewrite = shouldQueueRemoteRewrite(remote.totpRaw, normalizedPayload)

        val updatedItem = item.copy(
            itemData = normalizedItemData,
            updatedAt = if (normalizedItemData != item.itemData || shouldQueueRemoteRewrite) now else item.updatedAt,
            bitwardenRevisionDate = remote.revisionDate ?: item.bitwardenRevisionDate,
            bitwardenLocalModified = if (shouldQueueRemoteRewrite) true else item.bitwardenLocalModified,
            syncStatus = if (shouldQueueRemoteRewrite) "PENDING" else item.syncStatus
        )
        if (updatedItem != item) {
            secureItemDao.update(updatedItem)
        }

        return RepairOutcome.Repaired(
            localChanged = normalizedItemData != item.itemData,
            queuedForSync = shouldQueueRemoteRewrite
        )
    }

    private suspend fun repairBoundPasswordTotp(
        entry: PasswordEntry,
        vaultApi: BitwardenVaultApi,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey,
        now: Date
    ): RepairOutcome {
        val remoteCipherId = entry.bitwardenCipherId ?: return repairBoundPasswordTotpFromLocal(entry, now)
        val remote = fetchRemoteTotpContext(vaultApi, accessToken, remoteCipherId, symmetricKey)
            ?: return repairBoundPasswordTotpFromLocal(entry, now)

        val normalizedTotp = TotpDataResolver.fromAuthenticatorKey(
            rawKey = remote.totpRaw,
            fallbackIssuer = remote.website.takeIf { it.isNotBlank() } ?: remote.displayName,
            fallbackAccountName = remote.username
        ) ?: return repairBoundPasswordTotpFromLocal(entry, now)

        val normalizedPayload = TotpDataResolver.toBitwardenPayload(remote.displayName, normalizedTotp)
        val shouldQueueRemoteRewrite = shouldQueueRemoteRewrite(remote.totpRaw, normalizedPayload)

        val updatedEntry = entry.copy(
            authenticatorKey = normalizedPayload,
            updatedAt = if (normalizedPayload != entry.authenticatorKey || shouldQueueRemoteRewrite) now else entry.updatedAt,
            bitwardenRevisionDate = remote.revisionDate ?: entry.bitwardenRevisionDate,
            bitwardenLocalModified = if (shouldQueueRemoteRewrite) true else entry.bitwardenLocalModified
        )
        if (updatedEntry != entry) {
            passwordEntryDao.update(updatedEntry)
        }

        return RepairOutcome.Repaired(
            localChanged = normalizedPayload != entry.authenticatorKey,
            queuedForSync = shouldQueueRemoteRewrite
        )
    }

    private suspend fun repairStandaloneTotpItemFromLocal(
        item: SecureItem,
        now: Date
    ): RepairOutcome {
        val normalizedData = TotpDataResolver.parseStoredItemData(
            itemData = item.itemData,
            fallbackIssuer = item.title
        ) ?: return RepairOutcome.Skipped

        val normalizedItemData = json.encodeToString(normalizedData)
        val shouldQueueRemoteRewrite =
            (item.bitwardenCipherId != null) && (
                item.itemData.contains("://", ignoreCase = true) ||
                    TotpDataResolver.hasNonDefaultOtpSettings(normalizedData)
                )

        val updatedItem = item.copy(
            itemData = normalizedItemData,
            updatedAt = if (normalizedItemData != item.itemData || shouldQueueRemoteRewrite) now else item.updatedAt,
            bitwardenLocalModified = if (shouldQueueRemoteRewrite) true else item.bitwardenLocalModified,
            syncStatus = if (shouldQueueRemoteRewrite) "PENDING" else item.syncStatus
        )
        if (updatedItem != item) {
            secureItemDao.update(updatedItem)
        }

        return RepairOutcome.Repaired(
            localChanged = normalizedItemData != item.itemData,
            queuedForSync = shouldQueueRemoteRewrite
        )
    }

    private suspend fun repairBoundPasswordTotpFromLocal(
        entry: PasswordEntry,
        now: Date
    ): RepairOutcome {
        if (entry.authenticatorKey.isBlank()) {
            return RepairOutcome.Skipped
        }

        val normalizedTotp = TotpDataResolver.fromAuthenticatorKey(
            rawKey = entry.authenticatorKey,
            fallbackIssuer = entry.website.takeIf { it.isNotBlank() } ?: entry.title,
            fallbackAccountName = entry.username.takeIf { it.isNotBlank() } ?: entry.title
        ) ?: return RepairOutcome.Skipped

        val normalizedPayload = TotpDataResolver.toBitwardenPayload(entry.title, normalizedTotp)
        val shouldQueueRemoteRewrite =
            (entry.bitwardenCipherId != null) && (
                entry.authenticatorKey.contains("://", ignoreCase = true) ||
                    TotpDataResolver.hasNonDefaultOtpSettings(normalizedTotp)
                )

        val updatedEntry = entry.copy(
            authenticatorKey = normalizedPayload,
            updatedAt = if (normalizedPayload != entry.authenticatorKey || shouldQueueRemoteRewrite) now else entry.updatedAt,
            bitwardenLocalModified = if (shouldQueueRemoteRewrite) true else entry.bitwardenLocalModified
        )
        if (updatedEntry != entry) {
            passwordEntryDao.update(updatedEntry)
        }

        return RepairOutcome.Repaired(
            localChanged = normalizedPayload != entry.authenticatorKey,
            queuedForSync = shouldQueueRemoteRewrite
        )
    }

    private suspend fun fetchRemoteTotpContext(
        vaultApi: BitwardenVaultApi,
        accessToken: String,
        cipherId: String,
        symmetricKey: SymmetricCryptoKey
    ): RemoteTotpContext? {
        val response = runCatching {
            vaultApi.getCipher(
                authorization = "Bearer $accessToken",
                cipherId = cipherId
            )
        }.getOrElse { error ->
            Log.w(TAG, "Failed to fetch cipher $cipherId for TOTP repair: ${error.message}")
            return null
        }
        if (!response.isSuccessful) {
            Log.w(TAG, "Skipping TOTP repair for cipher $cipherId: getCipher ${response.code()}")
            return null
        }

        val cipher = response.body() ?: return null
        val login = cipher.login ?: return null
        val remoteTotp = decryptString(login.totp, symmetricKey)?.trim().orEmpty()
        if (remoteTotp.isBlank()) {
            return null
        }

        return RemoteTotpContext(
            displayName = decryptString(cipher.name, symmetricKey).orEmpty().ifBlank { "Authenticator" },
            username = decryptString(login.username, symmetricKey).orEmpty(),
            website = extractPrimaryWebsite(login.uris, symmetricKey),
            totpRaw = remoteTotp,
            revisionDate = cipher.revisionDate
        )
    }

    private fun shouldQueueRemoteRewrite(remoteTotpRaw: String, normalizedPayload: String): Boolean {
        return remoteTotpRaw.trim() != normalizedPayload.trim()
    }

    private fun extractPrimaryWebsite(
        uris: List<takagi.ru.monica.bitwarden.api.CipherUriApiData>?,
        symmetricKey: SymmetricCryptoKey
    ): String {
        if (uris.isNullOrEmpty()) return ""

        var website = ""
        uris.forEach { uriData ->
            val uri = decryptString(uriData.uri, symmetricKey) ?: return@forEach
            if (!uri.startsWith("androidapp://", ignoreCase = true) && website.isBlank()) {
                website = uri
            }
        }
        return website
    }

    private fun decryptString(encrypted: String?, key: SymmetricCryptoKey): String? {
        if (encrypted.isNullOrBlank()) return null
        return try {
            BitwardenCrypto.decryptToString(encrypted, key)
        } catch (_: Exception) {
            null
        }
    }

    private data class RemoteTotpContext(
        val displayName: String,
        val username: String,
        val website: String,
        val totpRaw: String,
        val revisionDate: String?
    )

    private sealed class RepairOutcome {
        data class Repaired(
            val localChanged: Boolean,
            val queuedForSync: Boolean
        ) : RepairOutcome()

        data object Skipped : RepairOutcome()
    }
}
