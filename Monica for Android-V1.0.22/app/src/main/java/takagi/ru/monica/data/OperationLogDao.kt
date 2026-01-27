package takagi.ru.monica.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 操作日志 DAO
 */
@Dao
interface OperationLogDao {
    
    /**
     * 获取所有操作日志 (按时间倒序)
     */
    @Query("SELECT * FROM operation_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<OperationLog>>
    
    /**
     * 获取最近 N 条操作日志
     */
    @Query("SELECT * FROM operation_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int): Flow<List<OperationLog>>
    
    /**
     * 按条目类型获取操作日志
     */
    @Query("SELECT * FROM operation_logs WHERE itemType = :itemType ORDER BY timestamp DESC")
    fun getLogsByItemType(itemType: String): Flow<List<OperationLog>>
    
    /**
     * 按条目 ID 获取操作历史
     */
    @Query("SELECT * FROM operation_logs WHERE itemType = :itemType AND itemId = :itemId ORDER BY timestamp DESC")
    fun getLogsByItem(itemType: String, itemId: Long): Flow<List<OperationLog>>
    
    /**
     * 获取指定时间范围内的日志
     */
    @Query("SELECT * FROM operation_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getLogsByTimeRange(startTime: Long, endTime: Long): Flow<List<OperationLog>>
    
    /**
     * 插入日志
     */
    @Insert
    suspend fun insert(log: OperationLog): Long
    
    /**
     * 批量插入日志
     */
    @Insert
    suspend fun insertAll(logs: List<OperationLog>)
    
    /**
     * 删除单条日志
     */
    @Delete
    suspend fun delete(log: OperationLog)
    
    /**
     * 删除指定条目的所有日志
     */
    @Query("DELETE FROM operation_logs WHERE itemType = :itemType AND itemId = :itemId")
    suspend fun deleteByItem(itemType: String, itemId: Long)
    
    /**
     * 清空所有日志
     */
    @Query("DELETE FROM operation_logs")
    suspend fun deleteAll()
    
    /**
     * 获取日志数量
     */
    @Query("SELECT COUNT(*) FROM operation_logs")
    suspend fun getLogCount(): Int
    
    /**
     * 删除超过指定天数的旧日志
     */
    @Query("DELETE FROM operation_logs WHERE timestamp < :cutoffTime")
    suspend fun deleteOldLogs(cutoffTime: Long)
    
    /**
     * 更新日志的恢复状态
     */
    @Query("UPDATE operation_logs SET isReverted = :isReverted WHERE id = :logId")
    suspend fun updateRevertedStatus(logId: Long, isReverted: Boolean)
    
    /**
     * 根据 ID 获取单条日志
     */
    @Query("SELECT * FROM operation_logs WHERE id = :logId")
    suspend fun getLogById(logId: Long): OperationLog?
    
    /**
     * 同步获取所有日志（用于备份）
     */
    @Query("SELECT * FROM operation_logs ORDER BY timestamp ASC")
    suspend fun getAllLogsSync(): List<OperationLog>
}
