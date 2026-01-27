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
    val includeTrash: Boolean = true,     // 回收站
    val includeWebDavConfig: Boolean = false,  // WebDAV 配置（默认关闭，需手动开启）
    val includeLocalKeePass: Boolean = false,  // 本地 KeePass 数据库（默认关闭）
    val includeKeePassWebDavConfig: Boolean = false  // KeePass WebDAV 配置（默认关闭）
) {
    /**
     * 检查是否至少启用了一种内容类型
     * 注意：WebDAV 配置和 KeePass 相关选项不计入必选项，因为它们是附加配置
     */
    fun hasAnyEnabled(): Boolean {
        return includePasswords || includeAuthenticators || 
               includeDocuments || includeBankCards || 
               includeGeneratorHistory || includeImages || includeNotes ||
               includeTimeline || includeTrash || includeLocalKeePass
    }
    
    /**
     * 检查是否所有内容类型都已启用
     * 注意：WebDAV 配置使用单独的检查
     */
    fun allEnabled(): Boolean {
        return includePasswords && includeAuthenticators && 
               includeDocuments && includeBankCards && 
               includeGeneratorHistory && includeImages && includeNotes &&
               includeTimeline && includeTrash
    }
    
    /**
     * 检查是否所有内容类型都已启用（包括 WebDAV 配置）
     */
    fun allEnabledIncludingWebDav(): Boolean {
        return allEnabled() && includeWebDavConfig
    }
}
