package takagi.ru.monica.bitwarden.mapper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import takagi.ru.monica.bitwarden.api.*
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
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
        
        val totpData = parseTotpData(item.itemData)
        
        return CipherCreateRequest(
            type = 1, // Login (with TOTP only)
            name = item.title,
            notes = buildNotes(item, totpData),
            folderId = folderId,
            favorite = item.isFavorite,
            login = CipherLoginApiData(
                // 独立 TOTP 通常没有用户名密码，但可以有 URI
                uris = totpData.issuer.takeIf { it.isNotBlank() }?.let {
                    listOf(CipherUriApiData(uri = "otpauth://totp/${it}"))
                },
                totp = totpData.secret,
                username = totpData.account.takeIf { it.isNotBlank() }
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
        
        val totpData = TotpItemData(
            secret = login.totp ?: "",
            issuer = issuer ?: cipher.name ?: "",
            account = login.username ?: "",
            algorithm = "SHA1",  // 默认值
            digits = 6,
            period = 30
        )
        
        return SecureItem(
            id = 0,
            itemType = ItemType.TOTP,
            title = cipher.name ?: "验证器",
            notes = cipher.notes ?: "",
            isFavorite = cipher.favorite == true,
            createdAt = Date(),
            updatedAt = Date(),
            itemData = json.encodeToString(TotpItemData.serializer(), totpData),
            bitwardenVaultId = vaultId,
            bitwardenCipherId = cipher.id,
            bitwardenFolderId = cipher.folderId,
            bitwardenRevisionDate = cipher.revisionDate,
            syncStatus = "SYNCED"
        )
    }
    
    override fun hasDifference(item: SecureItem, cipher: CipherApiResponse): Boolean {
        if (cipher.type != 1) return true
        
        val login = cipher.login ?: return true
        val localData = parseTotpData(item.itemData)
        
        return item.title != cipher.name ||
                item.isFavorite != (cipher.favorite == true) ||
                localData.secret != (login.totp ?: "") ||
                localData.account != (login.username ?: "")
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
     * 构建笔记内容（包含 Monica 特有数据）
     */
    private fun buildNotes(item: SecureItem, totpData: TotpItemData): String? {
        val parts = mutableListOf<String>()
        
        if (item.notes.isNotBlank()) {
            parts.add(item.notes)
        }
        
        // 添加 Monica 元数据标记
        if (totpData.algorithm != "SHA1" || totpData.digits != 6 || totpData.period != 30) {
            parts.add("---")
            parts.add("[Monica TOTP Config]")
            parts.add("Algorithm: ${totpData.algorithm}")
            parts.add("Digits: ${totpData.digits}")
            parts.add("Period: ${totpData.period}")
        }
        
        return parts.joinToString("\n").takeIf { it.isNotBlank() }
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
    
    private fun parseTotpData(itemData: String): TotpItemData {
        return try {
            json.decodeFromString(TotpItemData.serializer(), itemData)
        } catch (e: Exception) {
            // 尝试旧格式兼容
            try {
                val obj = json.parseToJsonElement(itemData) as? JsonObject
                TotpItemData(
                    secret = obj?.get("secret")?.jsonPrimitive?.content 
                        ?: obj?.get("key")?.jsonPrimitive?.content ?: "",
                    issuer = obj?.get("issuer")?.jsonPrimitive?.content 
                        ?: obj?.get("serviceName")?.jsonPrimitive?.content ?: "",
                    account = obj?.get("account")?.jsonPrimitive?.content 
                        ?: obj?.get("accountName")?.jsonPrimitive?.content ?: "",
                    algorithm = obj?.get("algorithm")?.jsonPrimitive?.content ?: "SHA1",
                    digits = obj?.get("digits")?.jsonPrimitive?.content?.toIntOrNull() ?: 6,
                    period = obj?.get("period")?.jsonPrimitive?.content?.toIntOrNull() ?: 30
                )
            } catch (e2: Exception) {
                TotpItemData()
            }
        }
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
