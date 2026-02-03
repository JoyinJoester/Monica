package takagi.ru.monica.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    
    // Bitwarden 文件夹关联
    @ColumnInfo(name = "bitwarden_vault_id", defaultValue = "NULL")
    val bitwardenVaultId: Long? = null,           // 关联的 Bitwarden Vault
    
    @ColumnInfo(name = "bitwarden_folder_id", defaultValue = "NULL")
    val bitwardenFolderId: String? = null,        // 关联的 Bitwarden Folder UUID
    
    @ColumnInfo(name = "sync_item_types", defaultValue = "NULL")
    val syncItemTypes: String? = null             // 同步的数据类型，JSON 数组如 ["PASSWORD","TOTP","CARD"]
)
