package takagi.ru.monica.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 自定义字段的数据访问对象 (DAO)
 * 
 * 提供对 custom_fields 表的所有 CRUD 操作。
 * 支持批量操作以提高性能，尤其是在导入导出场景中。
 */
@Dao
interface CustomFieldDao {
    
    // =============== 查询操作 ===============
    
    /**
     * 获取指定密码条目的所有自定义字段（Flow，实时监听变化）
     */
    @Query("SELECT * FROM custom_fields WHERE entry_id = :entryId ORDER BY sort_order ASC, id ASC")
    fun getFieldsByEntryId(entryId: Long): Flow<List<CustomField>>
    
    /**
     * 获取指定密码条目的所有自定义字段（同步版本，用于备份和同步）
     */
    @Query("SELECT * FROM custom_fields WHERE entry_id = :entryId ORDER BY sort_order ASC, id ASC")
    suspend fun getFieldsByEntryIdSync(entryId: Long): List<CustomField>
    
    /**
     * 获取单个自定义字段
     */
    @Query("SELECT * FROM custom_fields WHERE id = :id")
    suspend fun getFieldById(id: Long): CustomField?
    
    /**
     * 获取所有自定义字段（用于备份）
     */
    @Query("SELECT * FROM custom_fields ORDER BY entry_id, sort_order ASC")
    suspend fun getAllFieldsSync(): List<CustomField>
    
    /**
     * 获取指定多个条目的所有自定义字段（批量查询，用于列表显示优化）
     */
    @Query("SELECT * FROM custom_fields WHERE entry_id IN (:entryIds) ORDER BY entry_id, sort_order ASC")
    suspend fun getFieldsByEntryIds(entryIds: List<Long>): List<CustomField>
    
    /**
     * 检查指定条目是否有自定义字段
     */
    @Query("SELECT COUNT(*) FROM custom_fields WHERE entry_id = :entryId")
    suspend fun getFieldCountByEntryId(entryId: Long): Int
    
    /**
     * 搜索自定义字段（根据标题或值）
     */
    @Query("""
        SELECT * FROM custom_fields 
        WHERE title LIKE '%' || :query || '%' OR value LIKE '%' || :query || '%'
        ORDER BY entry_id, sort_order ASC
    """)
    suspend fun searchFields(query: String): List<CustomField>
    
    /**
     * 获取包含指定关键词的条目ID列表（用于搜索功能扩展）
     */
    @Query("""
        SELECT DISTINCT entry_id FROM custom_fields 
        WHERE title LIKE '%' || :query || '%' OR value LIKE '%' || :query || '%'
    """)
    suspend fun searchEntryIdsByFieldContent(query: String): List<Long>
    
    // =============== 插入操作 ===============
    
    /**
     * 插入单个自定义字段
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(field: CustomField): Long
    
    /**
     * 批量插入自定义字段
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(fields: List<CustomField>): List<Long>
    
    // =============== 更新操作 ===============
    
    /**
     * 更新单个自定义字段
     */
    @Update
    suspend fun update(field: CustomField)
    
    /**
     * 批量更新自定义字段
     */
    @Update
    suspend fun updateAll(fields: List<CustomField>)
    
    /**
     * 更新字段的排序顺序
     */
    @Query("UPDATE custom_fields SET sort_order = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)
    
    /**
     * 更新字段的保护状态
     */
    @Query("UPDATE custom_fields SET is_protected = :isProtected WHERE id = :id")
    suspend fun updateProtectedStatus(id: Long, isProtected: Boolean)
    
    // =============== 删除操作 ===============
    
    /**
     * 删除单个自定义字段
     */
    @Delete
    suspend fun delete(field: CustomField)
    
    /**
     * 根据ID删除自定义字段
     */
    @Query("DELETE FROM custom_fields WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    /**
     * 删除指定条目的所有自定义字段
     */
    @Query("DELETE FROM custom_fields WHERE entry_id = :entryId")
    suspend fun deleteByEntryId(entryId: Long)
    
    /**
     * 删除多个条目的所有自定义字段
     */
    @Query("DELETE FROM custom_fields WHERE entry_id IN (:entryIds)")
    suspend fun deleteByEntryIds(entryIds: List<Long>)
    
    /**
     * 删除所有自定义字段（用于清空数据库）
     */
    @Query("DELETE FROM custom_fields")
    suspend fun deleteAll()
    
    // =============== 事务操作 ===============
    
    /**
     * 替换指定条目的所有自定义字段
     * 用于编辑保存时一次性替换所有字段
     */
    @Transaction
    suspend fun replaceFieldsForEntry(entryId: Long, newFields: List<CustomField>) {
        deleteByEntryId(entryId)
        if (newFields.isNotEmpty()) {
            insertAll(newFields.mapIndexed { index, field ->
                field.copy(entryId = entryId, sortOrder = index)
            })
        }
    }
    
    /**
     * 批量更新排序顺序
     */
    @Transaction
    suspend fun updateSortOrders(updates: List<Pair<Long, Int>>) {
        updates.forEach { (id, sortOrder) ->
            updateSortOrder(id, sortOrder)
        }
    }
}
