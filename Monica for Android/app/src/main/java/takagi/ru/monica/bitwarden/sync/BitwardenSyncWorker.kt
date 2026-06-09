package takagi.ru.monica.bitwarden.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.sync.SyncDiagnostics
import takagi.ru.monica.sync.SyncError
import takagi.ru.monica.sync.SyncMode
import takagi.ru.monica.sync.SyncNetworkPolicy
import takagi.ru.monica.sync.SyncPriority
import takagi.ru.monica.sync.SyncTrigger
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

        private fun oneTimeWorkName(vaultId: Long?): String {
            return if (vaultId != null && vaultId > 0) {
                "${WORK_NAME_ONE_TIME}_vault_$vaultId"
            } else {
                WORK_NAME_ONE_TIME
            }
        }
        
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
                    oneTimeWorkName(vaultId),
                    ExistingWorkPolicy.KEEP,
                    request
                )
            
            Log.d(TAG, "Triggered immediate sync: type=$syncType, vaultId=$vaultId")
        }
    }
    
    override suspend fun doWork(): Result {
        val syncType = inputData.getString(KEY_SYNC_TYPE) ?: SYNC_TYPE_QUEUE
        val vaultId = inputData.getLong(KEY_VAULT_ID, -1L).takeIf { it > 0 }
        val taskId = SyncDiagnostics.nextTaskId("bw-worker")
        val target = vaultId?.let { "bitwarden:$it" } ?: "bitwarden:all_unlocked"
        val startedAt = SyncDiagnostics.start(
            taskId = taskId,
            target = target,
            trigger = "WORKER_$syncType",
            detail = "attempt=$runAttemptCount"
        )
        
        Log.d(TAG, "Starting sync work: type=$syncType, vaultId=$vaultId")
        
        return try {
            when (syncType) {
                SYNC_TYPE_QUEUE -> {
                    if (vaultId != null) {
                        syncVault(vaultId)
                    } else {
                        syncAllUnlockedVaults()
                    }
                }
                SYNC_TYPE_FULL -> {
                    if (vaultId != null) {
                        syncVault(vaultId)
                    } else {
                        Log.w(TAG, "Full sync requested without vaultId")
                    }
                }
            }
            
            Log.d(TAG, "Sync work completed successfully")
            SyncDiagnostics.success(
                taskId = taskId,
                target = target,
                trigger = "WORKER_$syncType",
                startedAt = startedAt,
                detail = "attempt=$runAttemptCount"
            )
            Result.success()
            
        } catch (e: BitwardenWorkerBlockedException) {
            Log.w(TAG, "Sync work blocked: ${e.syncError.kind}")
            SyncDiagnostics.blocked(
                taskId = taskId,
                target = target,
                trigger = "WORKER_$syncType",
                reason = e.syncError.redactedMessage ?: e.syncError.kind.name,
                startedAt = startedAt,
                detail = "attempt=$runAttemptCount retry=${e.syncError.retryable && runAttemptCount < 3}"
            )

            if (e.syncError.retryable && runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf("error" to (e.syncError.redactedMessage ?: e.syncError.kind.name))
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync work failed", e)
            SyncDiagnostics.failed(
                taskId = taskId,
                target = target,
                trigger = "WORKER_$syncType",
                startedAt = startedAt,
                error = e,
                detail = "attempt=$runAttemptCount retry=${runAttemptCount < 3}"
            )
            
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
    }
    
    private suspend fun syncVault(vaultId: Long) {
        val repository = BitwardenRepository.getInstance(applicationContext)
        val syncResult = repository.syncViaCoordinator(
            vaultId = vaultId,
            requestIdPrefix = "bw-worker-vault",
            trigger = SyncTrigger.WORKER_RECOVERY,
            priority = SyncPriority.BACKGROUND,
            mode = SyncMode.BACKGROUND,
            networkPolicy = SyncNetworkPolicy.REQUIRED
        )
        val result = when (syncResult) {
            is BitwardenCoordinatedSyncResult.Completed -> syncResult.result
            BitwardenCoordinatedSyncResult.Merged -> {
                Log.d(TAG, "Repository sync merged with active coordinator task: vaultId=$vaultId")
                return
            }
            is BitwardenCoordinatedSyncResult.Skipped -> {
                Log.d(TAG, "Repository sync skipped by coordinator: vaultId=$vaultId, reason=${syncResult.reason}")
                return
            }
            is BitwardenCoordinatedSyncResult.Blocked -> {
                throw BitwardenWorkerBlockedException(syncResult.error)
            }
            is BitwardenCoordinatedSyncResult.Canceled -> {
                throw IllegalStateException("Bitwarden sync canceled: ${syncResult.reason}")
            }
            is BitwardenCoordinatedSyncResult.Failed -> {
                throw syncResult.error
            }
        }

        when (result) {
            is BitwardenRepository.SyncResult.Success -> {
                Log.d(
                    TAG,
                    "Repository sync completed: vaultId=$vaultId, applied=${result.appliedChangeCount}"
                )
            }
            is BitwardenRepository.SyncResult.EmptyVaultBlocked -> {
                Log.w(TAG, "Repository sync blocked by empty vault protection: vaultId=$vaultId")
            }
            is BitwardenRepository.SyncResult.Error -> {
                throw IllegalStateException(result.message)
            }
        }
    }

    private suspend fun syncAllUnlockedVaults() {
        val repository = BitwardenRepository.getInstance(applicationContext)
        val unlockedVaults = repository.getAllVaults().filter { vault ->
            repository.isVaultUnlocked(vault.id)
        }
        if (unlockedVaults.isEmpty()) {
            Log.d(TAG, "No unlocked Bitwarden vaults available for background sync")
            return
        }
        unlockedVaults.forEach { vault ->
            syncVault(vault.id)
        }
    }

    private class BitwardenWorkerBlockedException(val syncError: SyncError) : Exception(
        syncError.redactedMessage ?: syncError.kind.name
    )
}
