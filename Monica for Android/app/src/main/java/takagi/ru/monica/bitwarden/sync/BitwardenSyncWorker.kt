package takagi.ru.monica.bitwarden.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Bitwarden 同步 Worker
 * 
 * 使用 WorkManager 在后台执行同步任务。
 * 支持：
 * - 网络恢复时自动同步
 * - 定时同步
 * - 约束条件（如仅 WiFi）
 */
class BitwardenSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val TAG = "BitwardenSyncWorker"
        
        const val WORK_NAME_PERIODIC = "bitwarden_sync_periodic"
        const val WORK_NAME_ONE_TIME = "bitwarden_sync_one_time"
        
        private const val KEY_SYNC_TYPE = "sync_type"
        private const val KEY_VAULT_ID = "vault_id"
        
        const val SYNC_TYPE_FULL = "full"
        const val SYNC_TYPE_QUEUE = "queue"
        
        /**
         * 创建一次性同步请求
         */
        fun createOneTimeRequest(
            syncType: String = SYNC_TYPE_QUEUE,
            vaultId: Long? = null,
            requiresNetwork: Boolean = true,
            requiresWifi: Boolean = false
        ): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .apply {
                    if (requiresNetwork) {
                        if (requiresWifi) {
                            setRequiredNetworkType(NetworkType.UNMETERED)
                        } else {
                            setRequiredNetworkType(NetworkType.CONNECTED)
                        }
                    }
                }
                .build()
            
            val data = workDataOf(
                KEY_SYNC_TYPE to syncType,
                KEY_VAULT_ID to (vaultId ?: -1L)
            )
            
            return OneTimeWorkRequestBuilder<BitwardenSyncWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
        }
        
        /**
         * 创建定期同步请求
         */
        fun createPeriodicRequest(
            intervalMinutes: Long = 15,
            requiresWifi: Boolean = false
        ): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (requiresWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .build()
            
            val data = workDataOf(
                KEY_SYNC_TYPE to SYNC_TYPE_QUEUE
            )
            
            return PeriodicWorkRequestBuilder<BitwardenSyncWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInputData(data)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
        }
        
        /**
         * 安排定期同步
         */
        fun schedulePeriodicSync(
            context: Context,
            intervalMinutes: Long = 15,
            requiresWifi: Boolean = false
        ) {
            val request = createPeriodicRequest(intervalMinutes, requiresWifi)
            
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            
            Log.d(TAG, "Scheduled periodic sync every $intervalMinutes minutes")
        }
        
        /**
         * 取消定期同步
         */
        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME_PERIODIC)
            Log.d(TAG, "Cancelled periodic sync")
        }
        
        /**
         * 触发立即同步
         */
        fun triggerImmediateSync(
            context: Context,
            syncType: String = SYNC_TYPE_QUEUE,
            vaultId: Long? = null,
            requiresWifi: Boolean = false
        ) {
            val request = createOneTimeRequest(
                syncType = syncType,
                vaultId = vaultId,
                requiresNetwork = true,
                requiresWifi = requiresWifi
            )
            
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME_ONE_TIME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            
            Log.d(TAG, "Triggered immediate sync: type=$syncType, vaultId=$vaultId")
        }
    }
    
    override suspend fun doWork(): Result {
        val syncType = inputData.getString(KEY_SYNC_TYPE) ?: SYNC_TYPE_QUEUE
        val vaultId = inputData.getLong(KEY_VAULT_ID, -1L).takeIf { it > 0 }
        
        Log.d(TAG, "Starting sync work: type=$syncType, vaultId=$vaultId")
        
        return try {
            // 获取 SyncQueueManager 实例
            // 注意：实际使用时需要通过依赖注入获取
            val syncManager = getSyncQueueManager()
            
            if (syncManager == null) {
                Log.w(TAG, "SyncQueueManager not available")
                return Result.retry()
            }
            
            when (syncType) {
                SYNC_TYPE_QUEUE -> {
                    // 处理待同步队列
                    syncManager.processQueue()
                }
                SYNC_TYPE_FULL -> {
                    // 全量同步（从服务器拉取）
                    // TODO: 实现全量同步逻辑
                }
            }
            
            Log.d(TAG, "Sync work completed successfully")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Sync work failed", e)
            
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
    }
    
    /**
     * 获取 SyncQueueManager 实例
     * 实际使用时应该通过 Hilt/Koin 等依赖注入框架获取
     */
    private fun getSyncQueueManager(): SyncQueueManager? {
        // TODO: 通过依赖注入获取实例
        // 临时返回 null，需要在应用初始化时设置
        return SyncQueueManagerHolder.instance
    }
}

/**
 * SyncQueueManager 单例持有者
 * 用于在 Worker 中访问 SyncQueueManager
 * 
 * 注意：这是一个临时方案，正式实现应该使用 Hilt WorkerFactory
 */
object SyncQueueManagerHolder {
    @Volatile
    var instance: SyncQueueManager? = null
}
