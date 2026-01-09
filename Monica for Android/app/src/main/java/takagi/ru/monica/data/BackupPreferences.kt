package takagi.ru.monica.data

/**
 * 备份偏好设置数据类
 * 用于控制 WebDAV 备份中包含哪些内容类型
 */
data class BackupPreferences(
    val includePasswords: Boolean = true,
    val includeAuthenticators: Boolean = true,
    val includeDocuments: Boolean = true,
    val includeBankCards: Boolean = true,
    val includeGeneratorHistory: Boolean = true,
    val includeImages: Boolean = true,
    val includeNotes: Boolean = true,
    val includeTimeline: Boolean = true,  // 操作历史记录
    val includeTrash: Boolean = true      // 回收站
) {
    /**
     * 检查是否至少启用了一种内容类型
     */
    fun hasAnyEnabled(): Boolean {
        return includePasswords || includeAuthenticators || 
               includeDocuments || includeBankCards || 
               includeGeneratorHistory || includeImages || includeNotes ||
               includeTimeline || includeTrash
    }
    
    /**
     * 检查是否所有内容类型都已启用
     */
    fun allEnabled(): Boolean {
        return includePasswords && includeAuthenticators && 
               includeDocuments && includeBankCards && 
               includeGeneratorHistory && includeImages && includeNotes &&
               includeTimeline && includeTrash
    }
}
