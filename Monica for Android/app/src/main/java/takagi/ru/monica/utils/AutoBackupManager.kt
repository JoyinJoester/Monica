package takagi.ru.monica.utils

import android.content.Context
import androidx.work.*
import takagi.ru.monica.webdav.WebDavBackoffState
import takagi.ru.monica.webdav.WebDavGateway
import takagi.ru.monica.workers.AutoBackupWorker
import java.util.concurrent.TimeUnit

/**
 * 自动备份管理器
 * 负责调度和管理自动备份任务
 */
class AutoBackupManager(private val context: Context) {
    
    private val workManager = WorkManager.getInstance(context)
    
    /**
     * 启动自动备份
     * 每天执行一次，在凌晨2点执行
     */
    fun scheduleAutoBackup() {
        android.util.Log.d("AutoBackupManager", "Scheduling auto backup...")
        
        // 创建约束条件
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // 需要网络连接
            .setRequiresBatteryNotLow(true) // 电量不能太低
            .build()
        
        // 计算到凌晨2点的延迟时间
        val currentTime = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = currentTime
            set(java.util.Calendar.HOUR_OF_DAY, 2)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            
            // 如果今天的2点已经过了，则安排到明天
            if (timeInMillis <= currentTime) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }
        
        val initialDelay = calendar.timeInMillis - currentTime
        
        // 创建周期性工作请求
        val workRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            12, TimeUnit.HOURS // 每12小时执行一次
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag("auto_backup")
            .build()
        
        // 使用 KEEP 策略，如果已存在则保持现有的
        workManager.enqueueUniquePeriodicWork(
            AutoBackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        
        android.util.Log.d("AutoBackupManager", "Auto backup scheduled successfully. Initial delay: ${initialDelay / 1000 / 60} minutes")
    }
    
    /**
     * 取消自动备份
     */
    fun cancelAutoBackup() {
        android.util.Log.d("AutoBackupManager", "Cancelling auto backup...")
        workManager.cancelUniqueWork(AutoBackupWorker.WORK_NAME)
        android.util.Log.d("AutoBackupManager", "Auto backup cancelled")
    }
    
    /**
     * 检查自动备份是否已调度
     */
    fun isAutoBackupScheduled(): Boolean {
        val workInfos = workManager.getWorkInfosForUniqueWork(AutoBackupWorker.WORK_NAME).get()
        val isScheduled = workInfos.any { 
            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING 
        }
        android.util.Log.d("AutoBackupManager", "Auto backup scheduled: $isScheduled")
        return isScheduled
    }
    
    /**
     * 立即执行一次备份（用于测试或用户主动触发）。
     *
     * 使用 [ExistingWorkPolicy.KEEP] 避免同一分钟内多次点击取消正在执行的备份；
     * 并在入队前检查 [WebDavBackoffState]，若目标主机仍处于 backoff 期，直接返回 false
     * 让 UI 提示用户稍后再试，而不是继续打穿服务器速率限制。
     *
     * @return true 表示任务已入队；false 表示由于 backoff 被拒绝或未配置服务器。
     */
    fun triggerBackupNow(): Boolean {
        android.util.Log.d("AutoBackupManager", "Triggering immediate backup...")

        val webDavHelper = takagi.ru.monica.utils.WebDavHelper(context)
        val serverUrl = webDavHelper.getCurrentConfig()?.serverUrl.orEmpty()
        val host = WebDavGateway.hostOf(serverUrl)
        if (host.isNotEmpty() && WebDavBackoffState.shouldBlock(host)) {
            val waitMs = WebDavBackoffState.suggestedWaitMillis(host)
            android.util.Log.w(
                "AutoBackupManager",
                "Refuse manual backup: host $host backoff for ${waitMs}ms"
            )
            return false
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = androidx.work.Data.Builder()
            .putBoolean(AutoBackupWorker.KEY_MANUAL_TRIGGER, true)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag("manual_backup")
            .build()

        // KEEP 策略：同一分钟内多次手动触发复用已存在任务，避免取消正在执行的备份。
        workManager.enqueueUniqueWork(
            MANUAL_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
        android.util.Log.d("AutoBackupManager", "Immediate backup triggered (KEEP)")
        return true
    }
    
    /**
     * 获取最后一次备份的工作状态
     */
    fun getLastBackupStatus(): WorkInfo? {
        val workInfos = workManager.getWorkInfosForUniqueWork(AutoBackupWorker.WORK_NAME).get()
        return workInfos.firstOrNull()
    }

    companion object {
        /** 手动触发的 WebDAV 备份 unique work name。 */
        const val MANUAL_WORK_NAME = "manual_webdav_backup"
    }
}
