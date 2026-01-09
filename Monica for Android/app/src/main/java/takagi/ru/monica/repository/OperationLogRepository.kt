package takagi.ru.monica.repository

import kotlinx.coroutines.flow.Flow
import takagi.ru.monica.data.OperationLog
import takagi.ru.monica.data.OperationLogDao

/**
 * 操作日志 Repository
 */
class OperationLogRepository(
    private val operationLogDao: OperationLogDao
) {
    /**
     * 获取所有操作日志
     */
    fun getAllLogs(): Flow<List<OperationLog>> {
        return operationLogDao.getAllLogs()
    }
    
    /**
     * 获取最近 N 条操作日志
     */
    fun getRecentLogs(limit: Int = 100): Flow<List<OperationLog>> {
        return operationLogDao.getRecentLogs(limit)
    }
    
    /**
     * 按条目类型获取日志
     */
    fun getLogsByItemType(itemType: String): Flow<List<OperationLog>> {
        return operationLogDao.getLogsByItemType(itemType)
    }
    
    /**
     * 按条目获取历史
     */
    fun getLogsByItem(itemType: String, itemId: Long): Flow<List<OperationLog>> {
        return operationLogDao.getLogsByItem(itemType, itemId)
    }
    
    /**
     * 插入日志
     */
    suspend fun insertLog(log: OperationLog): Long {
        return operationLogDao.insert(log)
    }
    
    /**
     * 清空所有日志
     */
    suspend fun clearAllLogs() {
        operationLogDao.deleteAll()
    }
    
    /**
     * 获取日志数量
     */
    suspend fun getLogCount(): Int {
        return operationLogDao.getLogCount()
    }
    
    /**
     * 删除超过指定天数的旧日志
     */
    suspend fun deleteOldLogs(daysToKeep: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        operationLogDao.deleteOldLogs(cutoffTime)
    }
}
