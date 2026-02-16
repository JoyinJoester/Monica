package takagi.ru.monica.bitwarden.mapper

import android.util.Base64
import takagi.ru.monica.bitwarden.api.SendApiResponse
import takagi.ru.monica.bitwarden.api.SendCreateRequest
import takagi.ru.monica.bitwarden.api.SendTextCreateRequest
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey
import takagi.ru.monica.data.bitwarden.BitwardenSend
import java.time.Instant

object BitwardenSendMapper {

    data class CreateTextSendPayload(
        val request: SendCreateRequest,
        val keyBase64: String,
        val shareUrl: String
    )

    fun mapApiToEntity(
        vaultId: Long,
        serverUrl: String,
        api: SendApiResponse,
        vaultKey: SymmetricCryptoKey
    ): BitwardenSend? {
        return try {
            val keyBase64 = BitwardenCrypto.decryptToString(api.key, vaultKey)
            val keyMaterial = Base64.decode(keyBase64, Base64.NO_WRAP)
            val sendKey = BitwardenCrypto.deriveSendKey(keyMaterial)

            val decryptedName = decryptString(api.name, sendKey) ?: "Untitled Send"
            val decryptedNotes = decryptString(api.notes, sendKey).orEmpty()
            val decryptedText = decryptString(api.text?.text, sendKey)
            val decryptedFileName = api.file?.fileName?.let { fileName ->
                decryptString(fileName, sendKey) ?: fileName
            }

            val now = System.currentTimeMillis()
            BitwardenSend(
                vaultId = vaultId,
                bitwardenSendId = api.id,
                accessId = api.accessId,
                keyBase64 = keyBase64,
                type = api.type,
                name = decryptedName,
                notes = decryptedNotes,
                textContent = decryptedText,
                isTextHidden = api.text?.hidden ?: false,
                fileName = decryptedFileName,
                fileSize = api.file?.size,
                accessCount = api.accessCount,
                maxAccessCount = api.maxAccessCount,
                hasPassword = api.password != null,
                disabled = api.disabled,
                hideEmail = api.hideEmail ?: false,
                revisionDate = api.revisionDate,
                expirationDate = api.expirationDate,
                deletionDate = api.deletionDate,
                shareUrl = buildShareUrl(
                    serverUrl = serverUrl,
                    accessId = api.accessId,
                    keyMaterial = keyMaterial
                ),
                lastSyncedAt = now,
                createdAt = now,
                updatedAt = now
            )
        } catch (_: Exception) {
            null
        }
    }

    fun buildCreateTextSendPayload(
        serverUrl: String,
        vaultKey: SymmetricCryptoKey,
        title: String,
        text: String,
        notes: String?,
        password: String?,
        maxAccessCount: Int?,
        hideEmail: Boolean,
        hiddenText: Boolean,
        deletionMillis: Long,
        expirationMillis: Long?
    ): CreateTextSendPayload {
        val keyMaterial = BitwardenCrypto.generateSendKeyMaterial()
        val keyBase64 = Base64.encodeToString(keyMaterial, Base64.NO_WRAP)
        val sendKey = BitwardenCrypto.deriveSendKey(keyMaterial)

        val encryptedKey = BitwardenCrypto.encryptString(keyBase64, vaultKey)
        val encryptedName = BitwardenCrypto.encryptString(title, sendKey)
        val encryptedNotes = notes
            ?.takeIf { it.isNotBlank() }
            ?.let { BitwardenCrypto.encryptString(it, sendKey) }
        val encryptedText = BitwardenCrypto.encryptString(text, sendKey)

        val passwordHash = password
            ?.takeIf { it.isNotBlank() }
            ?.let { BitwardenCrypto.hashSendPassword(it, keyMaterial) }

        val request = SendCreateRequest(
            key = encryptedKey,
            type = BitwardenSend.TYPE_TEXT,
            name = encryptedName,
            notes = encryptedNotes,
            password = passwordHash,
            disabled = false,
            hideEmail = hideEmail,
            deletionDate = Instant.ofEpochMilli(deletionMillis).toString(),
            expirationDate = expirationMillis?.let { Instant.ofEpochMilli(it).toString() },
            maxAccessCount = maxAccessCount?.takeIf { it > 0 },
            text = SendTextCreateRequest(
                text = encryptedText,
                hidden = hiddenText
            )
        )

        return CreateTextSendPayload(
            request = request,
            keyBase64 = keyBase64,
            shareUrl = buildShareUrl(
                serverUrl = serverUrl,
                accessId = "",
                keyMaterial = keyMaterial
            )
        )
    }

    fun buildShareUrl(
        serverUrl: String,
        accessId: String,
        keyMaterial: ByteArray
    ): String {
        val baseUrl = buildSendBaseUrl(serverUrl)
        val key = Base64.encodeToString(
            keyMaterial,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return if (accessId.isBlank()) "$baseUrl/$key" else "$baseUrl$accessId/$key"
    }

    private fun buildSendBaseUrl(serverUrl: String): String {
        val normalized = serverUrl.trimEnd('/')
        val lower = normalized.lowercase()

        return when {
            lower.contains("bitwarden.eu") -> "https://send.bitwarden.eu/#/send/"
            lower.contains("bitwarden.com") -> "https://send.bitwarden.com/#/send/"
            else -> "$normalized/#/send/"
        }
    }

    private fun decryptString(value: String?, key: SymmetricCryptoKey): String? {
        if (value.isNullOrBlank()) return null
        return try {
            BitwardenCrypto.decryptToString(value, key)
        } catch (_: Exception) {
            null
        }
    }
}

