package takagi.ru.monica.data

/**
 * 备份报告 - 用于向用户展示备份结果的详细信息
 */
data class BackupReport(
    val success: Boolean,
    val totalItems: ItemCounts,
    val successItems: ItemCounts,
    val failedItems: List<FailedItem>,
    val warnings: List<String>
) {
    /**
     * 是否有警告或失败
     */
    fun hasIssues(): Boolean = failedItems.isNotEmpty() || warnings.isNotEmpty()
    
    /**
     * 获取可读的报告摘要
     */
    fun getSummary(): String {
        return buildString {
            if (success) {
                appendLine("✅ 备份成功")
            } else {
                appendLine("❌ 备份失败")
            }
            
            appendLine()
            appendLine("📊 数据统计:")
            appendLine("  密码: ${successItems.passwords}/${totalItems.passwords}")
            appendLine("  笔记: ${successItems.notes}/${totalItems.notes}")
            appendLine("  验证器: ${successItems.totp}/${totalItems.totp}")
            appendLine("  银行卡: ${successItems.bankCards}/${totalItems.bankCards}")
            appendLine("  证件: ${successItems.documents}/${totalItems.documents}")
            appendLine("  图片: ${successItems.images}/${totalItems.images}")
            
            if (failedItems.isNotEmpty()) {
                appendLine()
                appendLine("⚠️ 失败的项目:")
                failedItems.forEach { item ->
                    appendLine("  [${item.type}] ${item.title} - ${item.reason}")
                }
            }
            
            if (warnings.isNotEmpty()) {
                appendLine()
                appendLine("⚠️ 警告:")
                warnings.forEach { warning ->
                    appendLine("  • $warning")
                }
            }
        }
    }
}

/**
 * 恢复报告 - 用于向用户展示恢复结果的详细信息
 */
data class RestoreReport(
    val success: Boolean,
    val backupContains: ItemCounts,
    val restoredSuccessfully: ItemCounts,
    val failedItems: List<FailedItem>,
    val warnings: List<String>
) {
    /**
     * 是否有警告或失败
     */
    fun hasIssues(): Boolean = failedItems.isNotEmpty() || warnings.isNotEmpty()
    
    /**
     * 获取可读的报告摘要
     */
    fun getSummary(): String {
        return buildString {
            if (success) {
                appendLine("✅ 恢复成功")
            } else {
                appendLine("❌ 恢复失败")
            }
            
            appendLine()
            appendLine("📊 数据统计:")
            appendLine("  密码: ${restoredSuccessfully.passwords}/${backupContains.passwords}")
            appendLine("  笔记: ${restoredSuccessfully.notes}/${backupContains.notes}")
            appendLine("  验证器: ${restoredSuccessfully.totp}/${backupContains.totp}")
            appendLine("  银行卡: ${restoredSuccessfully.bankCards}/${backupContains.bankCards}")
            appendLine("  证件: ${restoredSuccessfully.documents}/${backupContains.documents}")
            appendLine("  图片: ${restoredSuccessfully.images}/${backupContains.images}")
            
            if (failedItems.isNotEmpty()) {
                appendLine()
                appendLine("⚠️ 恢复失败的项目:")
                failedItems.forEach { item ->
                    appendLine("  [${item.type}] ${item.title} - ${item.reason}")
                }
            }
            
            if (warnings.isNotEmpty()) {
                appendLine()
                appendLine("⚠️ 警告:")
                warnings.forEach { warning ->
                    appendLine("  • $warning")
                }
            }
        }
    }
}

/**
 * 项目计数
 */
data class ItemCounts(
    val passwords: Int = 0,
    val notes: Int = 0,
    val totp: Int = 0,
    val bankCards: Int = 0,
    val documents: Int = 0,
    val images: Int = 0,
    val generatorHistory: Int = 0
) {
    fun getTotalCount(): Int {
        return passwords + notes + totp + bankCards + documents
    }
}

/**
 * 失败的项目详情
 */
data class FailedItem(
    val id: Long,
    val type: String,
    val title: String,
    val reason: String
)
