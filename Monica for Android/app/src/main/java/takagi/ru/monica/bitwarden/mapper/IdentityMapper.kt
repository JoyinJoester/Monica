package takagi.ru.monica.bitwarden.mapper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import takagi.ru.monica.bitwarden.api.*
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import java.util.Date

/**
 * 证件/身份数据映射器
 * 
 * Monica SecureItem (DOCUMENT) <-> Bitwarden Identity (Type 4)
 */
class IdentityMapper : BitwardenMapper<SecureItem> {
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    override fun toCreateRequest(item: SecureItem, folderId: String?): CipherCreateRequest {
        require(item.itemType == ItemType.DOCUMENT) { 
            "IdentityMapper only supports DOCUMENT items" 
        }
        
        val docData = parseDocumentData(item.itemData)
        
        return CipherCreateRequest(
            type = 4, // Identity
            name = item.title,
            notes = item.notes.takeIf { it.isNotBlank() },
            folderId = folderId,
            favorite = item.isFavorite,
            identity = CipherIdentityApiData(
                title = docData.title,
                firstName = docData.firstName,
                middleName = docData.middleName,
                lastName = docData.lastName,
                address1 = docData.address1,
                address2 = docData.address2,
                city = docData.city,
                state = docData.state,
                postalCode = docData.postalCode,
                country = docData.country,
                email = docData.email,
                phone = docData.phone,
                ssn = docData.ssn,
                passportNumber = docData.passportNumber,
                licenseNumber = docData.licenseNumber
            )
        )
    }
    
    override fun fromCipherResponse(cipher: CipherApiResponse, vaultId: Long): SecureItem {
        require(cipher.type == 4) { 
            "IdentityMapper only supports Identity ciphers (type 4)" 
        }
        
        val identity = cipher.identity ?: CipherIdentityApiData()
        
        val docData = DocumentItemData(
            documentType = "identity", // Bitwarden Identity 类型
            title = identity.title ?: "",
            firstName = identity.firstName ?: "",
            middleName = identity.middleName ?: "",
            lastName = identity.lastName ?: "",
            address1 = identity.address1 ?: "",
            address2 = identity.address2 ?: "",
            city = identity.city ?: "",
            state = identity.state ?: "",
            postalCode = identity.postalCode ?: "",
            country = identity.country ?: "",
            email = identity.email ?: "",
            phone = identity.phone ?: "",
            ssn = identity.ssn ?: "",
            passportNumber = identity.passportNumber ?: "",
            licenseNumber = identity.licenseNumber ?: ""
        )
        
        return SecureItem(
            id = 0,
            itemType = ItemType.DOCUMENT,
            title = cipher.name ?: "证件",
            notes = cipher.notes ?: "",
            isFavorite = cipher.favorite == true,
            createdAt = Date(),
            updatedAt = Date(),
            itemData = json.encodeToString(DocumentItemData.serializer(), docData),
            bitwardenVaultId = vaultId,
            bitwardenCipherId = cipher.id,
            bitwardenFolderId = cipher.folderId,
            bitwardenRevisionDate = cipher.revisionDate,
            syncStatus = "SYNCED"
        )
    }
    
    override fun hasDifference(item: SecureItem, cipher: CipherApiResponse): Boolean {
        if (cipher.type != 4) return true
        
        val identity = cipher.identity ?: return true
        val localData = parseDocumentData(item.itemData)
        
        return item.title != cipher.name ||
                item.notes != (cipher.notes ?: "") ||
                item.isFavorite != (cipher.favorite == true) ||
                localData.firstName != (identity.firstName ?: "") ||
                localData.lastName != (identity.lastName ?: "") ||
                localData.email != (identity.email ?: "") ||
                localData.phone != (identity.phone ?: "") ||
                localData.passportNumber != (identity.passportNumber ?: "") ||
                localData.licenseNumber != (identity.licenseNumber ?: "") ||
                localData.ssn != (identity.ssn ?: "")
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
    
    private fun parseDocumentData(itemData: String): DocumentItemData {
        return try {
            json.decodeFromString(DocumentItemData.serializer(), itemData)
        } catch (e: Exception) {
            // 尝试旧格式兼容
            try {
                val obj = json.parseToJsonElement(itemData) as? JsonObject
                DocumentItemData(
                    documentType = obj?.get("type")?.jsonPrimitive?.content 
                        ?: obj?.get("documentType")?.jsonPrimitive?.content ?: "",
                    documentNumber = obj?.get("number")?.jsonPrimitive?.content 
                        ?: obj?.get("documentNumber")?.jsonPrimitive?.content ?: "",
                    firstName = obj?.get("firstName")?.jsonPrimitive?.content 
                        ?: obj?.get("name")?.jsonPrimitive?.content ?: "",
                    passportNumber = obj?.get("passportNumber")?.jsonPrimitive?.content ?: "",
                    licenseNumber = obj?.get("licenseNumber")?.jsonPrimitive?.content 
                        ?: obj?.get("driverLicense")?.jsonPrimitive?.content ?: ""
                )
            } catch (e2: Exception) {
                DocumentItemData()
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
}

/**
 * Monica 证件数据结构
 */
@kotlinx.serialization.Serializable
data class DocumentItemData(
    // 证件基本信息
    val documentType: String = "",        // passport, id_card, driver_license, etc.
    val documentNumber: String = "",
    val issueDate: String = "",
    val expiryDate: String = "",
    val issuingAuthority: String = "",
    
    // 个人信息 (对应 Bitwarden Identity)
    val title: String = "",               // Mr, Mrs, etc.
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    
    // 地址信息
    val address1: String = "",
    val address2: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val country: String = "",
    
    // 联系方式
    val email: String = "",
    val phone: String = "",
    val additionalInfo: String = "",
    
    // 特定证件号码
    val ssn: String = "",                 // 社保号
    val passportNumber: String = "",      // 护照号
    val licenseNumber: String = ""        // 驾照号
)
