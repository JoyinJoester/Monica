package takagi.ru.monica.data.bitwarden

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Bitwarden Folder Entity - 存储 Bitwarden 文件夹信息
 * 
 * 设计说明:
 * - Bitwarden 的文件夹类似于 Monica 的分类
 * - 每个文件夹关联到一个特定的 Vault
 * - 文件夹名称在 Bitwarden 中是加密的，这里存储解密后的名称
 * 
 * 安全规则:
 * - 使用外键关联 vault，但设置 NO ACTION 防止级联删除
 * - 服务器删除操作需要同步到本地，但要保留冲突备份
 */
@Entity(
    tableName = "bitwarden_folders",
    indices = [
        Index(value = ["vault_id"]),
        Index(value = ["bitwarden_folder_id"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = BitwardenVault::class,
            parentColumns = ["id"],
            childColumns = ["vault_id"],
            onDelete = ForeignKey.NO_ACTION,  // 安全: 不级联删除
            onUpdate = ForeignKey.NO_ACTION
        )
    ]
)
data class BitwardenFolder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // === 关联 Vault ===
    @ColumnInfo(name = "vault_id")
    val vaultId: Long,
    
    // === Bitwarden 标识 ===
    @ColumnInfo(name = "bitwarden_folder_id")
    val bitwardenFolderId: String,           // Bitwarden 文件夹 UUID
    
    // === 文件夹信息 ===
    @ColumnInfo(name = "name")
    val name: String,                        // 解密后的文件夹名称
    
    @ColumnInfo(name = "encrypted_name")
    val encryptedName: String? = null,       // 原始加密名称 (用于写回)
    
    // === 同步元数据 ===
    @ColumnInfo(name = "revision_date")
    val revisionDate: String,                // ISO 8601 格式
    
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = System.currentTimeMillis(),
    
    // === 本地状态 ===
    @ColumnInfo(name = "is_local_modified", defaultValue = "0")
    val isLocalModified: Boolean = false,    // 本地是否有未同步的修改
    
    @ColumnInfo(name = "local_monica_category_id")
    val localMonicaCategoryId: Long? = null, // 映射到 Monica 分类的 ID
    
    // === 排序 ===
    @ColumnInfo(name = "sort_order", defaultValue = "0")
    val sortOrder: Int = 0
)
