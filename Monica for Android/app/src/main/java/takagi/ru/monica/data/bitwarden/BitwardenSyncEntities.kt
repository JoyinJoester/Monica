package takagi.ru.monica.data.bitwarden

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Bitwarden 冲突备份 Entity - 当同步冲突发生时保存数据副本
 * 
 * 这是数据安全的关键组件！
 * 
 * 使用场景:
 * 1. 本地和服务器同时修改同一条目
 * 2. 服务器返回与本地版本不同的数据
 * 3. 网络错误导致同步状态不确定
 * 
 * 安全规则:
 * - 永远不要自动删除冲突备份
 * - 需要用户显式确认后才能删除
 * - 保留完整的 JSON 快照
 */
@Entity(
    tableName = "bitwarden_conflict_backups",
    indices = [
        Index(value = ["vault_id"]),
        Index(value = ["entry_id"]),
        Index(value = ["conflict_type"]),
        Index(value = ["created_at"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = BitwardenVault::class,
            parentColumns = ["id"],
            childColumns = ["vault_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION
        )
    ]
)
data class BitwardenConflictBackup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // === 关联信息 ===
    @ColumnInfo(name = "vault_id")
    val vaultId: Long,
    
    @ColumnInfo(name = "entry_id")
    val entryId: Long? = null,               // Monica PasswordEntry ID (如果存在)
    
    @ColumnInfo(name = "bitwarden_cipher_id")
    val bitwardenCipherId: String? = null,   // Bitwarden Cipher UUID
    
    // === 冲突类型 ===
    @ColumnInfo(name = "conflict_type")
    val conflictType: String,                // CONCURRENT_EDIT, VERSION_MISMATCH, SERVER_DELETE, etc.
    
    // === 数据快照 ===
    @ColumnInfo(name = "local_data_json")
    val localDataJson: String,               // 本地版本的完整 JSON
    
    @ColumnInfo(name = "server_data_json")
    val serverDataJson: String? = null,      // 服务器版本的完整 JSON (如果有)
    
    @ColumnInfo(name = "local_revision_date")
    val localRevisionDate: String? = null,
    
    @ColumnInfo(name = "server_revision_date")
    val serverRevisionDate: String? = null,
    
    // === 元信息 ===
    @ColumnInfo(name = "entry_title")
    val entryTitle: String,                  // 条目标题 (便于用户识别)
    
    @ColumnInfo(name = "description")
    val description: String? = null,         // 冲突描述
    
    // === 解决状态 ===
    @ColumnInfo(name = "is_resolved", defaultValue = "0")
    val isResolved: Boolean = false,
    
    @ColumnInfo(name = "resolution")
    val resolution: String? = null,          // KEEP_LOCAL, KEEP_SERVER, MERGED, DISCARDED
    
    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Long? = null,
    
    // === 审计 ===
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        // 冲突类型常量
        const val TYPE_CONCURRENT_EDIT = "CONCURRENT_EDIT"      // 并发编辑
        const val TYPE_VERSION_MISMATCH = "VERSION_MISMATCH"    // 版本不匹配
        const val TYPE_SERVER_DELETE = "SERVER_DELETE"          // 服务器删除
        const val TYPE_LOCAL_DELETE = "LOCAL_DELETE"            // 本地删除
        const val TYPE_SYNC_ERROR = "SYNC_ERROR"                // 同步错误
        
        // 解决方式常量
        const val RESOLUTION_KEEP_LOCAL = "KEEP_LOCAL"
        const val RESOLUTION_KEEP_SERVER = "KEEP_SERVER"
        const val RESOLUTION_MERGED = "MERGED"
        const val RESOLUTION_DISCARDED = "DISCARDED"
    }
}

/**
 * Bitwarden 待处理操作 Entity - 离线操作队列
 * 
 * 用途:
 * - 离线时记录用户操作
 * - 网络恢复后按顺序同步到服务器
 * - 确保操作的原子性和顺序性
 * 
 * 安全规则:
 * - 操作失败时保留在队列中，不丢弃
 * - 超过最大重试次数后暂停，需要用户干预
 * - 删除操作需要特别谨慎处理
 */
@Entity(
    tableName = "bitwarden_pending_operations",
    indices = [
        Index(value = ["vault_id"]),
        Index(value = ["status"]),
        Index(value = ["created_at"]),
        Index(value = ["item_type"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = BitwardenVault::class,
            parentColumns = ["id"],
            childColumns = ["vault_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION
        )
    ]
)
data class BitwardenPendingOperation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // === 关联信息 ===
    @ColumnInfo(name = "vault_id")
    val vaultId: Long,
    
    @ColumnInfo(name = "entry_id")
    val entryId: Long? = null,               // Monica PasswordEntry ID 或 SecureItem ID 或 PasskeyEntry ID
    
    @ColumnInfo(name = "bitwarden_cipher_id")
    val bitwardenCipherId: String? = null,   // Bitwarden Cipher UUID
    
    // === 数据类型 ===
    @ColumnInfo(name = "item_type", defaultValue = "PASSWORD")
    val itemType: String = ITEM_TYPE_PASSWORD,  // PASSWORD, TOTP, CARD, NOTE, DOCUMENT, PASSKEY
    
    // === 操作信息 ===
    @ColumnInfo(name = "operation_type")
    val operationType: String,               // CREATE, UPDATE, DELETE, MOVE_FOLDER
    
    @ColumnInfo(name = "target_type")
    val targetType: String,                  // CIPHER, FOLDER
    
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,                 // 操作数据的 JSON
    
    // === 执行状态 ===
    @ColumnInfo(name = "status")
    val status: String = STATUS_PENDING,     // PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
    
    @ColumnInfo(name = "retry_count", defaultValue = "0")
    val retryCount: Int = 0,
    
    @ColumnInfo(name = "max_retries", defaultValue = "3")
    val maxRetries: Int = 3,
    
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
    
    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long? = null,
    
    // === 时间戳 ===
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null
) {
    companion object {
        // 操作类型
        const val OP_CREATE = "CREATE"
        const val OP_UPDATE = "UPDATE"
        const val OP_DELETE = "DELETE"
        const val OP_MOVE_FOLDER = "MOVE_FOLDER"
        
        // 目标类型
        const val TARGET_CIPHER = "CIPHER"
        const val TARGET_FOLDER = "FOLDER"
        
        // 数据项类型 (对应 Monica 数据模型)
        const val ITEM_TYPE_PASSWORD = "PASSWORD"   // PasswordEntry -> Login
        const val ITEM_TYPE_TOTP = "TOTP"           // SecureItem TOTP -> Login with totp
        const val ITEM_TYPE_CARD = "CARD"           // SecureItem BANK_CARD -> Card
        const val ITEM_TYPE_NOTE = "NOTE"           // SecureItem NOTE -> SecureNote
        const val ITEM_TYPE_DOCUMENT = "DOCUMENT"   // SecureItem DOCUMENT -> Identity
        const val ITEM_TYPE_PASSKEY = "PASSKEY"     // PasskeyEntry -> Login (metadata only)
        
        // 状态
        const val STATUS_PENDING = "PENDING"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
    }
    
    /**
     * 是否可以重试
     */
    fun canRetry(): Boolean = retryCount < maxRetries && status == STATUS_FAILED
    
    /**
     * 是否已达到最大重试次数
     */
    fun isMaxRetriesReached(): Boolean = retryCount >= maxRetries
}
