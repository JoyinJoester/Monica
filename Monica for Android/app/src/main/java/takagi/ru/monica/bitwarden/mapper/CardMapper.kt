package takagi.ru.monica.bitwarden.mapper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import takagi.ru.monica.bitwarden.api.*
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import java.util.Date

/**
 * 银行卡数据映射器
 * 
 * Monica SecureItem (BANK_CARD) <-> Bitwarden Card (Type 3)
 */
class CardMapper : BitwardenMapper<SecureItem> {
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    override fun toCreateRequest(item: SecureItem, folderId: String?): CipherCreateRequest {
        require(item.itemType == ItemType.BANK_CARD) { 
            "CardMapper only supports BANK_CARD items" 
        }
        
        val cardData = parseCardData(item.itemData)
        
        return CipherCreateRequest(
            type = 3, // Card
            name = item.title,
            notes = item.notes.takeIf { it.isNotBlank() },
            folderId = folderId,
            favorite = item.isFavorite,
            card = CipherCardApiData(
                cardholderName = cardData.cardholderName,
                brand = cardData.brand,
                number = cardData.number,
                expMonth = cardData.expMonth,
                expYear = cardData.expYear,
                code = cardData.cvv
            )
        )
    }
    
    override fun fromCipherResponse(cipher: CipherApiResponse, vaultId: Long): SecureItem {
        require(cipher.type == 3) { 
            "CardMapper only supports Card ciphers (type 3)" 
        }
        
        val card = cipher.card ?: CipherCardApiData()
        
        val cardData = CardItemData(
            cardholderName = card.cardholderName ?: "",
            number = card.number ?: "",
            expMonth = card.expMonth ?: "",
            expYear = card.expYear ?: "",
            cvv = card.code ?: "",
            brand = card.brand ?: ""
        )
        
        return SecureItem(
            id = 0, // 新建时由数据库生成
            itemType = ItemType.BANK_CARD,
            title = cipher.name ?: "银行卡",
            notes = cipher.notes ?: "",
            isFavorite = cipher.favorite == true,
            createdAt = Date(),
            updatedAt = Date(),
            itemData = json.encodeToString(CardItemData.serializer(), cardData),
            bitwardenVaultId = vaultId,
            bitwardenCipherId = cipher.id,
            bitwardenFolderId = cipher.folderId,
            bitwardenRevisionDate = cipher.revisionDate,
            syncStatus = "SYNCED"
        )
    }
    
    override fun hasDifference(item: SecureItem, cipher: CipherApiResponse): Boolean {
        if (cipher.type != 3) return true
        
        val card = cipher.card ?: return true
        val localData = parseCardData(item.itemData)
        
        return item.title != cipher.name ||
                item.notes != (cipher.notes ?: "") ||
                item.isFavorite != (cipher.favorite == true) ||
                localData.cardholderName != (card.cardholderName ?: "") ||
                localData.number != (card.number ?: "") ||
                localData.expMonth != (card.expMonth ?: "") ||
                localData.expYear != (card.expYear ?: "") ||
                localData.cvv != (card.code ?: "") ||
                localData.brand != (card.brand ?: "")
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
                // 比较时间戳，选择最新的
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
     * 解析 Monica 银行卡 JSON 数据
     */
    private fun parseCardData(itemData: String): CardItemData {
        return try {
            json.decodeFromString(CardItemData.serializer(), itemData)
        } catch (e: Exception) {
            // 尝试旧格式兼容
            try {
                val obj = json.parseToJsonElement(itemData) as? JsonObject
                CardItemData(
                    cardholderName = obj?.get("holderName")?.jsonPrimitive?.content 
                        ?: obj?.get("cardholderName")?.jsonPrimitive?.content ?: "",
                    number = obj?.get("cardNumber")?.jsonPrimitive?.content 
                        ?: obj?.get("number")?.jsonPrimitive?.content ?: "",
                    expMonth = obj?.get("expiryMonth")?.jsonPrimitive?.content 
                        ?: obj?.get("expMonth")?.jsonPrimitive?.content ?: "",
                    expYear = obj?.get("expiryYear")?.jsonPrimitive?.content 
                        ?: obj?.get("expYear")?.jsonPrimitive?.content ?: "",
                    cvv = obj?.get("cvv")?.jsonPrimitive?.content 
                        ?: obj?.get("code")?.jsonPrimitive?.content ?: "",
                    brand = obj?.get("brand")?.jsonPrimitive?.content 
                        ?: obj?.get("bankName")?.jsonPrimitive?.content ?: ""
                )
            } catch (e2: Exception) {
                CardItemData()
            }
        }
    }
    
    private fun parseRevisionDate(dateStr: String?): Long {
        if (dateStr == null) return 0
        return try {
            // ISO 8601 格式
            java.time.Instant.parse(dateStr).toEpochMilli()
        } catch (e: Exception) {
            0
        }
    }
}

/**
 * Monica 银行卡数据结构
 */
@kotlinx.serialization.Serializable
data class CardItemData(
    val cardholderName: String = "",
    val number: String = "",
    val expMonth: String = "",
    val expYear: String = "",
    val cvv: String = "",
    val brand: String = "",
    // Monica 特有字段
    val bankName: String = "",
    val cardType: String = "",  // debit, credit
    val billingAddress: String = ""
)
