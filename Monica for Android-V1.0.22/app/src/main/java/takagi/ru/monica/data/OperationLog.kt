package takagi.ru.monica.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 操作日志实体 - 用于记录密码管理器中的所有增删改操作
 */
@Entity(
    tableName = "operation_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["itemType"])
    ]
)
data class OperationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 操作的条目类型: PASSWORD, TOTP, BANK_CARD, DOCUMENT, NOTE, CATEGORY */
    val itemType: String,
    
    /** 关联条目的 ID */
    val itemId: Long,
    
    /** 条目标题 (便于显示, 避免关联查询) */
    val itemTitle: String,
    
    /** 操作类型: CREATE, UPDATE, DELETE */
    val operationType: String,
    
    /** 字段变更记录 (JSON 格式) */
    @ColumnInfo(defaultValue = "")
    val changesJson: String = "",
    
    /** 设备标识 */
    @ColumnInfo(defaultValue = "")
    val deviceId: String = "",
    
    /** 设备名称 */
    @ColumnInfo(defaultValue = "")
    val deviceName: String = "",
    
    /** 操作时间戳 */
    val timestamp: Long = System.currentTimeMillis(),
    
    /** 是否已恢复 (用于编辑操作的回滚) */
    @ColumnInfo(defaultValue = "0")
    val isReverted: Boolean = false
)

/**
 * 操作日志类型枚举
 */
enum class OperationLogItemType {
    PASSWORD,
    TOTP,
    BANK_CARD,
    DOCUMENT,
    NOTE,
    CATEGORY,
    WEBDAV_UPLOAD,    // WebDAV 上传
    WEBDAV_DOWNLOAD   // WebDAV 下载/同步
}

/**
 * 操作类型枚举
 */
enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    SYNC    // 同步操作
}
