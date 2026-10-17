package takagi.ru.monica.credentialexchange

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import takagi.ru.monica.attachments.AttachmentContainer
import takagi.ru.monica.attachments.facade.AttachmentFacade
import takagi.ru.monica.attachments.model.AttachmentOwner
import takagi.ru.monica.attachments.model.AttachmentSource
import takagi.ru.monica.bitwarden.BitwardenVaultPremiumStore
import takagi.ru.monica.bitwarden.api.BitwardenApiManager
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey
import takagi.ru.monica.bitwarden.service.BitwardenCipherKeyResolver
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.bitwarden.BitwardenVault

/** Only imported attachments enter this queue. Payloads remain encrypted in the attachment store. */
class BitwardenImportAttachmentQueue(private val context: Context) {
    private val preferences = context.getSharedPreferences("credential_import_attachments_v1", Context.MODE_PRIVATE)
    private val database = PasswordDatabase.getDatabase(context)
    private val facade = AttachmentContainer.facade(context)

    suspend fun enqueue(vaultId: Long, owners: List<AttachmentOwner>) = mutex.withLock {
        val ids = owners.flatMap { facade.list(it) }.filter { it.sourceEnum == AttachmentSource.LOCAL }.map { it.id.toString() }
        if (ids.isNotEmpty()) {
            val key = "vault:$vaultId"
            check(preferences.edit().putStringSet(key, preferences.getStringSet(key, emptySet()).orEmpty() + ids).commit())
        }
    }

    /** Called by normal vault sync after parent ciphers have been created. Failed blobs stay queued. */
    suspend fun flush(vault: BitwardenVault, accessToken: String, vaultKey: SymmetricCryptoKey,
        apiManager: BitwardenApiManager): Int = mutex.withLock {
        val key = "vault:${vault.id}"
        val pending = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        if (pending.isEmpty()) return@withLock 0
        val api = apiManager.getVaultApi(vault)
        var failures = 0
        for (id in pending.toList()) {
            val attachment = id.toLongOrNull()?.let { facade.getById(it) }
            val owner = attachment?.owner
            if (attachment == null || owner == null || attachment.isDeleted || attachment.sourceEnum != AttachmentSource.LOCAL) {
                pending.remove(id)
                continue
            }
            val password = owner.passwordId?.let { database.passwordEntryDao().getPasswordEntryById(it) }
            val item = owner.secureItemId?.let { database.secureItemDao().getItemById(it) }
            if (password?.bitwardenVaultId != vault.id && item?.bitwardenVaultId != vault.id) {
                pending.remove(id)
                continue
            }
            val cipherId = password?.bitwardenCipherId ?: item?.bitwardenCipherId
            if (cipherId.isNullOrBlank() || !BitwardenVaultPremiumStore.isPremium(context, vault.id)) {
                failures++
                continue
            }
            try {
                val response = api.getCipher("Bearer $accessToken", cipherId)
                check(response.isSuccessful)
                val cipher = checkNotNull(response.body())
                takagi.ru.monica.bitwarden.service.BitwardenCipherOwnership.requireWritable(cipher, cipherId)
                val effectiveKey = BitwardenCipherKeyResolver.resolveRemoteCipherKey(api, accessToken, cipher, vaultKey)
                try {
                    facade.promoteLocalAttachmentsToBitwarden(owner,
                        AttachmentFacade.BitwardenContext(api, apiManager.getOkHttpClient(vault), accessToken,
                            cipherId, effectiveKey, true), setOf(attachment.fileName), setOf(attachment.id))
                } finally { effectiveKey.clear() }
                pending.remove(id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failures++ }
            // Persist acknowledgements as we go; retries never discard the encrypted local source.
            check(preferences.edit().putStringSet(key, pending).commit())
        }
        check(preferences.edit().putStringSet(key, pending).commit())
        failures
    }

    companion object { private val mutex = Mutex() }
}
