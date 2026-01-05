package takagi.ru.monica.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.utils.WebDavHelper
import kotlinx.coroutines.flow.first

/**
 * 自动 WebDAV 备份工作器
 * 使用 WorkManager 实现每日自动备份
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        android.util.Log.d("AutoBackupWorker", "Starting auto backup work...")
        
        // ✅ 检查是否为手动触发的备份
        val isManualTrigger = inputData.getBoolean(KEY_MANUAL_TRIGGER, false)
        android.util.Log.d("AutoBackupWorker", "Manual trigger: $isManualTrigger")
        
        try {
            val webDavHelper = WebDavHelper(applicationContext)
            
            // 检查是否配置了 WebDAV
            if (!webDavHelper.isConfigured()) {
                android.util.Log.w("AutoBackupWorker", "WebDAV not configured, skipping backup")
                return androidx.work.ListenableWorker.Result.success()
            }
            
            // 检查是否启用自动备份（手动触发时跳过此检查）
            if (!isManualTrigger && !webDavHelper.isAutoBackupEnabled()) {
                android.util.Log.w("AutoBackupWorker", "Auto backup disabled, skipping backup")
                return androidx.work.ListenableWorker.Result.success()
            }
            
            // ✅ 检查是否需要备份（防止重复备份）- 手动触发时跳过此检查
            if (!isManualTrigger && !webDavHelper.shouldAutoBackup()) {
                android.util.Log.d("AutoBackupWorker", "Backup not needed yet (< 24 hours since last backup)")
                return androidx.work.ListenableWorker.Result.success()
            }
            
            android.util.Log.d("AutoBackupWorker", "Proceeding with backup (manual=$isManualTrigger)")
            
            // 获取所有数据
            val database = PasswordDatabase.getDatabase(applicationContext)
            val passwordRepo = PasswordRepository(database.passwordEntryDao())
            val secureItemRepo = SecureItemRepository(database.secureItemDao())
            
            val passwords = passwordRepo.getAllPasswordEntries().first()
            val secureItems = secureItemRepo.getAllItems().first()
            
            // ✅ 解密密码再备份（修复：之前导出的是加密数据）
            val securityManager = takagi.ru.monica.security.SecurityManager(applicationContext)
            val decryptedPasswords = passwords.map { entry ->
                try {
                    entry.copy(password = securityManager.decryptData(entry.password))
                } catch (e: Exception) {
                    // 如果解密失败，保留原值（可能已是明文）
                    android.util.Log.w("AutoBackupWorker", "无法解密密码 ${entry.title}: ${e.message}")
                    entry
                }
            }
            
            // 加载备份偏好设置
            val backupPreferences = webDavHelper.getBackupPreferences()
            
            android.util.Log.d("AutoBackupWorker", "Creating backup with ${passwords.size} passwords and ${secureItems.size} secure items")
            android.util.Log.d("AutoBackupWorker", "Backup preferences: $backupPreferences")
            
            // 创建并上传备份（使用偏好设置）
            val backupResult = webDavHelper.createAndUploadBackup(
                passwords = decryptedPasswords,
                secureItems = secureItems,
                preferences = backupPreferences,
                isPermanent = false  // Auto backups are temporary
            )
            
            return if (backupResult.isSuccess) {
                val report = backupResult.getOrNull()
                android.util.Log.d("AutoBackupWorker", "Auto backup completed: ${report?.getSummary()}")
                // P0修复：检查是否有失败项
                if (report != null && report.hasIssues()) {
                    android.util.Log.w("AutoBackupWorker", "Backup has issues but completed")
                }
                androidx.work.ListenableWorker.Result.success()
            } else {
                val error = backupResult.exceptionOrNull()
                android.util.Log.e("AutoBackupWorker", "Auto backup failed: ${error?.message}", error)
                // 返回 retry 以便稍后重试
                androidx.work.ListenableWorker.Result.retry()
            }
            
        } catch (e: Exception) {
            android.util.Log.e("AutoBackupWorker", "Auto backup error", e)
            return androidx.work.ListenableWorker.Result.retry()
        }
    }
    
    companion object {
        const val WORK_NAME = "auto_webdav_backup"
        const val KEY_MANUAL_TRIGGER = "manual_trigger"  // ✅ 手动触发标志的键
    }
}
