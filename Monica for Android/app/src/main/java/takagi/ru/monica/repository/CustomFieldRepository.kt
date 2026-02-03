package takagi.ru.monica.repository

import kotlinx.coroutines.flow.Flow
import takagi.ru.monica.data.CustomField
import takagi.ru.monica.data.CustomFieldDao
import takagi.ru.monica.data.CustomFieldDraft

/**
 * 自定义字段仓库
 * 
 * 提供自定义字段的业务逻辑层操作，封装 DAO 接口，
 * 并提供一些便捷方法用于 UI 层和导入导出功能。
 */
class CustomFieldRepository(
    private val customFieldDao: CustomFieldDao
) {
    // =============== 查询操作 ===============
    
    /**
     * 获取指定密码条目的所有自定义字段（实时监听）
     */
    fun getFieldsByEntryId(entryId: Long): Flow<List<CustomField>> {
        return customFieldDao.getFieldsByEntryId(entryId)
    }
    
    /**
     * 获取指定密码条目的所有自定义字段（同步版本）
     */
    suspend fun getFieldsByEntryIdSync(entryId: Long): List<CustomField> {
        return customFieldDao.getFieldsByEntryIdSync(entryId)
    }
    
    /**
     * 获取所有自定义字段（用于备份）
     */
    suspend fun getAllFieldsSync(): List<CustomField> {
        return customFieldDao.getAllFieldsSync()
    }
    
    /**
     * 批量获取多个条目的自定义字段
     */
    suspend fun getFieldsByEntryIds(entryIds: List<Long>): Map<Long, List<CustomField>> {
        if (entryIds.isEmpty()) return emptyMap()
        val fields = customFieldDao.getFieldsByEntryIds(entryIds)
        return fields.groupBy { it.entryId }
    }
    
    /**
     * 检查指定条目是否有自定义字段
     */
    suspend fun hasCustomFields(entryId: Long): Boolean {
        return customFieldDao.getFieldCountByEntryId(entryId) > 0
    }
    
    /**
     * 搜索包含指定关键词的条目ID（用于扩展搜索功能）
     */
    suspend fun searchEntryIdsByFieldContent(query: String): List<Long> {
        return customFieldDao.searchEntryIdsByFieldContent(query)
    }
    
    // =============== 保存操作 ===============
    
    /**
     * 保存密码条目的自定义字段（替换所有现有字段）
     * 
     * @param entryId 密码条目ID
     * @param drafts 要保存的字段草稿列表
     * @return 插入的字段ID列表
     */
    suspend fun saveFieldsForEntry(entryId: Long, drafts: List<CustomFieldDraft>): List<Long> {
        // 过滤掉空字段
        val validDrafts = drafts.filter { it.isValid() }
        
        if (validDrafts.isEmpty()) {
            // 如果没有有效字段，删除该条目的所有字段
            customFieldDao.deleteByEntryId(entryId)
            return emptyList()
        }
        
        // 转换为 CustomField 实体并保存
        val fields = validDrafts.mapIndexed { index, draft ->
            draft.toCustomField(entryId, index)
        }
        
        customFieldDao.replaceFieldsForEntry(entryId, fields)
        
        // 返回保存后的字段ID（重新查询以获取自动生成的ID）
        return customFieldDao.getFieldsByEntryIdSync(entryId).map { it.id }
    }
    
    /**
     * 批量保存多个条目的自定义字段（用于导入）
     * 
     * @param fieldsMap 条目ID到字段列表的映射
     */
    suspend fun saveFieldsForEntries(fieldsMap: Map<Long, List<CustomField>>) {
        fieldsMap.forEach { (entryId, fields) ->
            customFieldDao.replaceFieldsForEntry(entryId, fields)
        }
    }
    
    /**
     * 插入单个自定义字段
     */
    suspend fun insertField(field: CustomField): Long {
        return customFieldDao.insert(field)
    }
    
    /**
     * 批量插入自定义字段
     */
    suspend fun insertFields(fields: List<CustomField>): List<Long> {
        return customFieldDao.insertAll(fields)
    }
    
    // =============== 更新操作 ===============
    
    /**
     * 更新单个自定义字段
     */
    suspend fun updateField(field: CustomField) {
        customFieldDao.update(field)
    }
    
    /**
     * 更新字段的保护状态
     */
    suspend fun updateProtectedStatus(fieldId: Long, isProtected: Boolean) {
        customFieldDao.updateProtectedStatus(fieldId, isProtected)
    }
    
    // =============== 删除操作 ===============
    
    /**
     * 删除单个自定义字段
     */
    suspend fun deleteField(field: CustomField) {
        customFieldDao.delete(field)
    }
    
    /**
     * 根据ID删除自定义字段
     */
    suspend fun deleteFieldById(fieldId: Long) {
        customFieldDao.deleteById(fieldId)
    }
    
    /**
     * 删除指定条目的所有自定义字段
     */
    suspend fun deleteFieldsByEntryId(entryId: Long) {
        customFieldDao.deleteByEntryId(entryId)
    }
    
    /**
     * 删除所有自定义字段（用于清空数据库）
     */
    suspend fun deleteAllFields() {
        customFieldDao.deleteAll()
    }
    
    // =============== 导入导出辅助方法 ===============
    
    /**
     * 从 KeePass StringFields 创建自定义字段
     * 
     * KeePass 的标准字段（Title, UserName, Password, URL, Notes）不应该作为自定义字段导入，
     * 它们应该映射到 PasswordEntry 的固定字段。
     * 
     * @param entryId 密码条目ID
     * @param stringFields KeePass 的 StringField 映射 (key -> Pair<value, isProtected>)
     * @return 创建的自定义字段列表
     */
    fun createFieldsFromKeePass(
        entryId: Long, 
        stringFields: Map<String, Pair<String, Boolean>>
    ): List<CustomField> {
        // KeePass 标准字段名（不应作为自定义字段）
        val standardFields = setOf("Title", "UserName", "Password", "URL", "Notes")
        
        return stringFields
            .filterKeys { it !in standardFields }
            .entries
            .mapIndexed { index, (key, valuePair) ->
                CustomField(
                    id = 0,
                    entryId = entryId,
                    title = key,
                    value = valuePair.first,
                    isProtected = valuePair.second,
                    sortOrder = index
                )
            }
    }
    
    /**
     * 计算自定义字段的顺序无关 Hash
     * 用于同步时判断字段是否发生变化
     */
    suspend fun calculateFieldsHash(entryId: Long): String {
        val fields = customFieldDao.getFieldsByEntryIdSync(entryId)
        if (fields.isEmpty()) return ""
        
        // 按 title+value 排序，确保顺序无关
        val sortedHashKeys = fields
            .map { it.toHashKey() }
            .sorted()
        
        return sortedHashKeys.joinToString("|")
    }
    
    /**
     * 将自定义字段转换为用于备份的序列化格式
     */
    suspend fun getFieldsForBackup(entryId: Long): List<CustomFieldBackupData> {
        return customFieldDao.getFieldsByEntryIdSync(entryId).map { field ->
            CustomFieldBackupData(
                title = field.title,
                value = field.value,
                isProtected = field.isProtected
            )
        }
    }
}

/**
 * 用于备份序列化的自定义字段数据
 * 不包含 ID 和 entryId，便于跨设备恢复
 */
data class CustomFieldBackupData(
    val title: String,
    val value: String,
    val isProtected: Boolean = false
) {
    /**
     * 转换为 CustomField 实体
     */
    fun toCustomField(entryId: Long, sortOrder: Int): CustomField {
        return CustomField(
            id = 0,
            entryId = entryId,
            title = title,
            value = value,
            isProtected = isProtected,
            sortOrder = sortOrder
        )
    }
}
