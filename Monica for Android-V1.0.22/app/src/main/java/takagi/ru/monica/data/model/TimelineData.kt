package takagi.ru.monica.data.model

/**
 * 时间线数据结构 - 用于跟踪项目历史和可视化 WebDAV 同步冲突
 */

/**
 * 表示单个字段的变更
 * @param fieldName 字段名称（如 "Password", "Username"）
 * @param oldValue 旧值
 * @param newValue 新值
 */
data class DiffChange(
    val fieldName: String,
    val oldValue: String,
    val newValue: String
)

/**
 * 时间线事件 - 密封接口
 * 可以是标准日志或冲突分支
 */
sealed interface TimelineEvent {
    
    /**
     * 标准日志 - 表示线性的本地更改
     * @param id 唯一标识符
     * @param timestamp 时间戳（毫秒）
     * @param deviceId 设备ID
     * @param summary 摘要描述
     * @param itemId 关联条目的ID
     * @param itemType 条目类型
     * @param operationType 操作类型
     * @param isReverted 是否已恢复
     * @param changes 变更列表
     */
    data class StandardLog(
        val id: String,
        val timestamp: Long,
        val deviceId: String,
        val summary: String,
        val itemId: Long = 0,
        val itemType: String = "",
        val operationType: String = "",
        val isReverted: Boolean = false,
        val changes: List<DiffChange> = emptyList()
    ) : TimelineEvent
    
    /**
     * 冲突分支 - 表示需要解决的同步冲突
     * @param ancestor 原始版本（祖先节点 A）
     * @param branches 冲突的版本分支列表（通常包含 2 个分支 B 和 C）
     */
    data class ConflictBranch(
        val ancestor: StandardLog,
        val branches: List<TimelineBranch>
    ) : TimelineEvent
}

/**
 * 时间线分支 - 表示特定设备的版本
 * @param deviceId 设备ID
 * @param deviceName 设备名称（如 "Pixel 7", "Windows PC"）
 * @param timestamp 时间戳（毫秒）
 * @param changes 该分支的变更列表
 */
data class TimelineBranch(
    val deviceId: String,
    val deviceName: String,
    val timestamp: Long,
    val changes: List<DiffChange>
)
