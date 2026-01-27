package takagi.ru.monica.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.data.OperationLog
import takagi.ru.monica.data.OperationLogItemType
import takagi.ru.monica.data.OperationType
import takagi.ru.monica.data.PasswordDatabase

/**
 * 操作日志记录工具类
 * 用于在 Repository 操作中自动记录变更日志
 */
object OperationLogger {
    
    private var database: PasswordDatabase? = null
    private var deviceId: String = ""
    private var deviceName: String = ""
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private val json = Json { 
        prettyPrint = false
        ignoreUnknownKeys = true 
    }
    
    /**
     * 初始化日志记录器
     */
    fun init(context: Context) {
        database = PasswordDatabase.getDatabase(context)
        deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
    }
    
    /**
     * 记录创建操作
     * @param details 可选的初始字段详情列表，用于记录创建时的关键信息
     */
    fun logCreate(
        itemType: OperationLogItemType,
        itemId: Long,
        itemTitle: String,
        details: List<FieldChange> = emptyList()
    ) {
        log(
            itemType = itemType,
            itemId = itemId,
            itemTitle = itemTitle,
            operationType = OperationType.CREATE,
            changes = details
        )
    }
    
    /**
     * 记录更新操作
     */
    fun logUpdate(
        itemType: OperationLogItemType,
        itemId: Long,
        itemTitle: String,
        changes: List<FieldChange>
    ) {
        if (changes.isEmpty()) return // 没有实际变更则不记录
        
        log(
            itemType = itemType,
            itemId = itemId,
            itemTitle = itemTitle,
            operationType = OperationType.UPDATE,
            changes = changes
        )
    }
    
    /**
     * 记录删除操作
     * @param detail 可选的详情描述（如"移入回收站"）
     */
    fun logDelete(
        itemType: OperationLogItemType,
        itemId: Long,
        itemTitle: String,
        detail: String? = null
    ) {
        log(
            itemType = itemType,
            itemId = itemId,
            itemTitle = if (detail != null) "$itemTitle ($detail)" else itemTitle,
            operationType = OperationType.DELETE,
            changes = emptyList()
        )
    }
    
    /**
     * 记录 WebDAV 上传操作
     * @param isAutomatic 是否自动上传
     * @param isPermanent 是否永久备份
     * @param details 上传详情（如备份了多少项目）
     */
    fun logWebDavUpload(
        isAutomatic: Boolean,
        isPermanent: Boolean,
        details: List<FieldChange> = emptyList()
    ) {
        val triggerType = if (isAutomatic) "自动" else "手动"
        val backupType = if (isPermanent) "永久" else "临时"
        val title = "${triggerType}上传 · $backupType"
        
        log(
            itemType = OperationLogItemType.WEBDAV_UPLOAD,
            itemId = System.currentTimeMillis(),
            itemTitle = title,
            operationType = OperationType.SYNC,
            changes = details
        )
    }
    
    /**
     * 记录 WebDAV 下载/同步操作
     * @param addedItems 新增的项目列表
     */
    fun logWebDavDownload(
        addedItems: List<FieldChange> = emptyList()
    ) {
        val title = "同步下载"
        
        log(
            itemType = OperationLogItemType.WEBDAV_DOWNLOAD,
            itemId = System.currentTimeMillis(),
            itemTitle = title,
            operationType = OperationType.SYNC,
            changes = addedItems
        )
    }
    
    private fun log(
        itemType: OperationLogItemType,
        itemId: Long,
        itemTitle: String,
        operationType: OperationType,
        changes: List<FieldChange>
    ) {
        val db = database
        if (db == null) {
            android.util.Log.e("OperationLogger", "Database is null! init() was not called")
            return
        }
        
        android.util.Log.d("OperationLogger", "Logging $operationType for $itemType: $itemTitle (id=$itemId)")
        
        val changesJson = if (changes.isNotEmpty()) {
            json.encodeToString(changes)
        } else {
            ""
        }
        
        val operationLog = OperationLog(
            itemType = itemType.name,
            itemId = itemId,
            itemTitle = itemTitle,
            operationType = operationType.name,
            changesJson = changesJson,
            deviceId = deviceId,
            deviceName = deviceName,
            timestamp = System.currentTimeMillis()
        )
        
        scope.launch {
            try {
                db.operationLogDao().insert(operationLog)
                android.util.Log.d("OperationLogger", "Successfully logged operation for $itemTitle")
            } catch (e: Exception) {
                android.util.Log.e("OperationLogger", "Failed to log operation", e)
            }
        }
    }
    
    /**
     * 比较两个对象并生成变更列表
     */
    fun <T> compareAndGetChanges(
        old: T?,
        new: T,
        fields: List<Pair<String, (T) -> String?>>
    ): List<FieldChange> {
        if (old == null) return emptyList()
        
        return fields.mapNotNull { (fieldName, getter) ->
            val oldValue = getter(old) ?: ""
            val newValue = getter(new) ?: ""
            if (oldValue != newValue) {
                FieldChange(fieldName, oldValue, newValue)
            } else {
                null
            }
        }
    }
}

/**
 * 字段变更记录
 */
@Serializable
data class FieldChange(
    val fieldName: String,
    val oldValue: String,
    val newValue: String
)
