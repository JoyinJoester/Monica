package takagi.ru.monica.bitwarden.mapper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import takagi.ru.monica.bitwarden.api.*
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.util.TotpDataResolver
import java.util.Date

/**
 * 独立验证器 (TOTP) 数据映射器
 * 
 * Monica SecureItem (TOTP) <-> Bitwarden Login with TOTP (Type 1)
 * 
 * 注意：这是用于独立 TOTP 验证器的映射，不是与密码绑定的 TOTP。
 * 独立 TOTP 在 Bitwarden 中表现为一个只有 totp 字段的 Login 条目。
 */
class TotpMapper : BitwardenMapper<SecureItem> {
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    override fun toCreateRequest(item: SecureItem, folderId: String?): CipherCreateRequest {
        require(item.itemType == ItemType.TOTP) { 
            "TotpMapper only supports TOTP items" 
        }
        
        val totpData = TotpDataResolver.normalizeTotpData(parseTotpData(item.itemData))
        val totpPayload = TotpDataResolver.toBitwardenPayload(item.title, totpData)
        
        return CipherCreateRequest(
            type = 1, // Login (with TOTP only)
            name = item.title,
            notes = item.notes.takeIf { it.isNotBlank() },
            folderId = folderId,
            favorite = item.isFavorite,
            login = CipherLoginApiData(
                // 独立 TOTP 通常没有用户名密码，但可以有 URI
                uris = totpData.issuer.takeIf { it.isNotBlank() }?.let {
                    listOf(CipherUriApiData(uri = "otpauth://totp/${it}"))
                },
                totp = totpPayload,
                username = totpData.accountName.takeIf { it.isNotBlank() }
            )
        )
    }
    
    override fun fromCipherResponse(cipher: CipherApiResponse, vaultId: Long): SecureItem {
        require(cipher.type == 1 && cipher.login?.totp != null) { 
            "TotpMapper only supports Login ciphers with TOTP" 
        }
        
        val login = cipher.login!!
        
        // 从 URI 解析 issuer
        val issuer = parseIssuerFromUri(login.uris?.firstOrNull()?.uri)
        val remoteNotes = cipher.notes.orEmpty()
        val notesWithoutLegacyConfig = stripLegacyConfigNotes(remoteNotes)
        val legacyConfig = parseLegacyConfig(remoteNotes)
        val resolvedTotpData = TotpDataResolver.fromAuthenticatorKey(
            rawKey = login.totp ?: "",
            fallbackIssuer = issuer ?: cipher.name.orEmpty(),
            fallbackAccountName = login.username.orEmpty()
        ) ?: TotpData(
            secret = "",
            issuer = issuer ?: cipher.name.orEmpty(),
            accountName = login.username.orEmpty()
        )
        val totpData = TotpDataResolver.normalizeTotpData(
            resolvedTotpData.copy(
                algorithm = legacyConfig?.algorithm ?: resolvedTotpData.algorithm,
                digits = legacyConfig?.digits ?: resolvedTotpData.digits,
                period = legacyConfig?.period ?: resolvedTotpData.period
            )
        )
        
        return SecureItem(
            id = 0,
            itemType = ItemType.TOTP,
            title = cipher.name ?: "验证器",
            notes = notesWithoutLegacyConfig,
            isFavorite = cipher.favorite == true,
            createdAt = Date(),
            updatedAt = Date(),
            itemData = json.encodeToString(TotpData.serializer(), totpData),
            bitwardenVaultId = vaultId,
            bitwardenCipherId = cipher.id,
            bitwardenFolderId = cipher.folderId,
            bitwardenRevisionDate = cipher.revisionDate,
            syncStatus = "SYNCED"
        )
    }
    
    override fun hasDifference(item: SecureItem, cipher: CipherApiResponse): Boolean {
        if (cipher.type != 1) return true
        
        val localData = parseTotpData(item.itemData)
        val remoteData = fromCipherResponse(cipher, item.bitwardenVaultId ?: 0)
            .let { parseTotpData(it.itemData) }
        
        return item.title != cipher.name ||
                item.isFavorite != (cipher.favorite == true) ||
                item.notes != stripLegacyConfigNotes(cipher.notes.orEmpty()) ||
                !TotpDataResolver.hasEquivalentOtpParameters(localData, remoteData)
    }
    
    override fun merge(
        local: SecureItem,
        remote: CipherApiResponse,
        preference: MergePreference
    ): SecureItem {
        return when (preference) {
            MergePreference.LOCAL -> local.copy(
                bitwardenRevisionDate = remote.revisionDate
            )
            MergePreference.REMOTE -> fromCipherResponse(remote, local.bitwardenVaultId ?: 0).copy(
                id = local.id,
                createdAt = local.createdAt
            )
            MergePreference.LATEST -> {
                val localTime = local.updatedAt.time
                val remoteTime = parseRevisionDate(remote.revisionDate)
                if (localTime > remoteTime) {
                    local
                } else {
                    fromCipherResponse(remote, local.bitwardenVaultId ?: 0).copy(
                        id = local.id,
                        createdAt = local.createdAt
                    )
                }
            }
        }
    }
    
    /**
     * 从 otpauth URI 解析 issuer
     */
    private fun parseIssuerFromUri(uri: String?): String? {
        if (uri == null) return null
        return try {
            // otpauth://totp/Issuer:account?secret=xxx&issuer=Issuer
            val regex = Regex("otpauth://totp/([^:/?]+)")
            regex.find(uri)?.groupValues?.getOrNull(1)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseTotpData(itemData: String): TotpData {
        return TotpDataResolver.parseStoredItemData(itemData) ?: TotpData(secret = "")
    }

    private fun stripLegacyConfigNotes(notes: String): String {
        val marker = "\n---\n[Monica TOTP Config]"
        return notes.substringBefore(marker).trim()
    }

    private fun parseLegacyConfig(notes: String): LegacyTotpConfig? {
        val marker = "[Monica TOTP Config]"
        if (!notes.contains(marker)) return null

        val configBlock = notes.substringAfter(marker, "").trim()
        if (configBlock.isBlank()) return null

        val values = configBlock
            .lineSequence()
            .map { it.trim() }
            .filter { it.contains(":") }
            .associate {
                val key = it.substringBefore(":").trim()
                val value = it.substringAfter(":").trim()
                key to value
            }

        return LegacyTotpConfig(
            algorithm = values["Algorithm"]?.takeIf { it.isNotBlank() },
            digits = values["Digits"]?.toIntOrNull(),
            period = values["Period"]?.toIntOrNull()
        )
    }
    
    private fun parseRevisionDate(dateStr: String?): Long {
        if (dateStr == null) return 0
        return try {
            java.time.Instant.parse(dateStr).toEpochMilli()
        } catch (e: Exception) {
            0
        }
    }
    
    companion object {
        /**
         * 判断一个 Login Cipher 是否为独立 TOTP（没有密码，只有 totp）
         */
        fun isStandaloneTotpCipher(cipher: CipherApiResponse): Boolean {
            if (cipher.type != 1) return false
            val login = cipher.login ?: return false
            
            // 有 TOTP 但没有密码
            return login.totp?.isNotBlank() == true && 
                   login.password.isNullOrBlank()
        }
    }
}

private data class LegacyTotpConfig(
    val algorithm: String?,
    val digits: Int?,
    val period: Int?
)

/**
 * Monica TOTP 数据结构
 */
@kotlinx.serialization.Serializable
data class TotpItemData(
    val secret: String = "",
    val issuer: String = "",
    val account: String = "",
    val algorithm: String = "SHA1",    // SHA1, SHA256, SHA512
    val digits: Int = 6,
    val period: Int = 30,
    // Monica 特有字段
    val iconUrl: String = "",
    val category: String = ""
)
