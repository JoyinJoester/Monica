package takagi.ru.monica.bitwarden.mapper

import kotlinx.serialization.json.Json
import takagi.ru.monica.bitwarden.api.*
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import java.util.Date

/**
 * 安全笔记数据映射器
 * 
 * Monica SecureItem (NOTE) <-> Bitwarden SecureNote (Type 2)
 */
class SecureNoteMapper : BitwardenMapper<SecureItem> {
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    override fun toCreateRequest(item: SecureItem, folderId: String?): CipherCreateRequest {
        require(item.itemType == ItemType.NOTE) { 
            "SecureNoteMapper only supports NOTE items" 
        }
        
        val noteData = parseNoteData(item.itemData)
        
        // 合并标题内容和笔记内容
        val fullContent = buildString {
            if (noteData.content.isNotBlank()) {
                append(noteData.content)
            }
            if (item.notes.isNotBlank()) {
                if (isNotEmpty()) append("\n\n---\n\n")
                append(item.notes)
            }
        }
        
        return CipherCreateRequest(
            type = 2, // SecureNote
            name = item.title,
            notes = fullContent.takeIf { it.isNotBlank() },
            folderId = folderId,
            favorite = item.isFavorite,
            secureNote = CipherSecureNoteApiData(type = 0) // Generic secure note
        )
    }
    
    override fun fromCipherResponse(cipher: CipherApiResponse, vaultId: Long): SecureItem {
        require(cipher.type == 2) { 
            "SecureNoteMapper only supports SecureNote ciphers (type 2)" 
        }
        
        val noteData = NoteItemData(
            content = cipher.notes ?: ""
        )
        
        return SecureItem(
            id = 0,
            itemType = ItemType.NOTE,
            title = cipher.name ?: "笔记",
            notes = "", // 内容存储在 itemData 中
            isFavorite = cipher.favorite == true,
            createdAt = Date(),
            updatedAt = Date(),
            itemData = json.encodeToString(NoteItemData.serializer(), noteData),
            bitwardenVaultId = vaultId,
            bitwardenCipherId = cipher.id,
            bitwardenFolderId = cipher.folderId,
            bitwardenRevisionDate = cipher.revisionDate,
            syncStatus = "SYNCED"
        )
    }
    
    override fun hasDifference(item: SecureItem, cipher: CipherApiResponse): Boolean {
        if (cipher.type != 2) return true
        
        val localData = parseNoteData(item.itemData)
        val remoteContent = cipher.notes ?: ""
        
        // 合并本地内容进行比较
        val localContent = buildString {
            if (localData.content.isNotBlank()) {
                append(localData.content)
            }
            if (item.notes.isNotBlank()) {
                if (isNotEmpty()) append("\n\n---\n\n")
                append(item.notes)
            }
        }
        
        return item.title != cipher.name ||
                item.isFavorite != (cipher.favorite == true) ||
                localContent != remoteContent
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
    
    private fun parseNoteData(itemData: String): NoteItemData {
        return try {
            json.decodeFromString(NoteItemData.serializer(), itemData)
        } catch (e: Exception) {
            NoteItemData(content = itemData) // 兼容纯文本格式
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
 * Monica 笔记数据结构
 */
@kotlinx.serialization.Serializable
data class NoteItemData(
    val content: String = "",
    // Monica 特有字段
    val isMarkdown: Boolean = false,
    val tags: List<String> = emptyList()
)
