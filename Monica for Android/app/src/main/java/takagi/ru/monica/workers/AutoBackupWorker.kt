package takagi.ru.monica.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.utils.WebDavHelper
import takagi.ru.monica.webdav.WebDavBackoffState
import takagi.ru.monica.webdav.WebDavErrorClassifier
import takagi.ru.monica.webdav.WebDavErrorKind
import takagi.ru.monica.webdav.WebDavGateway

/**
 * 自动 WebDAV 备份工作器。
 *
 * 与 OpenList 等有速率限制的 WebDAV 服务兼容的关键在于：
 * - 在调用前查询 [WebDavBackoffState]，若目标主机仍处于 backoff 或临时禁用期，
 *   直接返回 Result.success() 跳过本轮，避免持续冲击服务器。
 * - 业务调用失败后按 [WebDavErrorClassifier] 分类映射结果：
 *   - 速率限制 / 鉴权失败 / 方法不被支持 / 响应格式错误 → success（重试无意义）
 *   - 网络不可达 / 超时 → retry
 *   - 成功 → success
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        android.util.Log.d(TAG, "Starting auto backup work...")

        val isManualTrigger = inputData.getBoolean(KEY_MANUAL_TRIGGER, false)
        android.util.Log.d(TAG, "Manual trigger: $isManualTrigger")

        try {
            val webDavHelper = WebDavHelper(applicationContext)

            if (!webDavHelper.isConfigured()) {
                android.util.Log.w(TAG, "WebDAV not configured, skipping backup")
                return androidx.work.ListenableWorker.Result.success()
            }

            if (!isManualTrigger && !webDavHelper.isAutoBackupEnabled()) {
                android.util.Log.w(TAG, "Auto backup disabled, skipping backup")
                return androidx.work.ListenableWorker.Result.success()
            }

            if (!isManualTrigger && !webDavHelper.shouldAutoBackup()) {
                android.util.Log.d(TAG, "Backup not needed yet (< 12 hours since last backup)")
                return androidx.work.ListenableWorker.Result.success()
            }

            val host = WebDavGateway.hostOf(
                webDavHelper.getCurrentConfig()?.serverUrl.orEmpty()
            )
            if (host.isNotEmpty()) {
                if (WebDavBackoffState.isTemporarilyDisabled(host)) {
                    val waitMs = WebDavBackoffState.suggestedWaitMillis(host)
                    android.util.Log.i(
                        TAG,
                        "Host $host temporarily disabled (${waitMs}ms remaining); skip."
                    )
                    return androidx.work.ListenableWorker.Result.success()
                }
                if (WebDavBackoffState.shouldBlock(host)) {
                    val waitMs = WebDavBackoffState.suggestedWaitMillis(host)
                    android.util.Log.i(
                        TAG,
                        "Host $host backoff until +${waitMs}ms; skip."
                    )
                    return androidx.work.ListenableWorker.Result.success()
                }
            }

            android.util.Log.d(TAG, "Proceeding with backup (manual=$isManualTrigger)")

            val database = PasswordDatabase.getDatabase(applicationContext)
            val passwordRepo = PasswordRepository(database.passwordEntryDao())
            val secureItemRepo = SecureItemRepository(database.secureItemDao())

            val passwords = passwordRepo.getAllPasswordEntries().first()
            val secureItems = secureItemRepo.getAllItems().first()

            val securityManager = takagi.ru.monica.security.SecurityManager(applicationContext)
            val decryptedPasswords = passwords.map { entry ->
                try {
                    entry.copy(password = securityManager.decryptData(entry.password))
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "无法解密密码 ${entry.title}: ${e.message}")
                    entry
                }
            }

            val backupPreferences = webDavHelper.getBackupPreferences()

            android.util.Log.d(
                TAG,
                "Creating backup with ${passwords.size} passwords and ${secureItems.size} secure items"
            )

            val backupResult = webDavHelper.createAndUploadBackup(
                passwords = decryptedPasswords,
                secureItems = secureItems,
                preferences = backupPreferences,
                isPermanent = false,
                isManualTrigger = isManualTrigger
            )

            if (backupResult.isSuccess) {
                if (host.isNotEmpty()) {
                    WebDavBackoffState.recordSuccess(host)
                }
                val report = backupResult.getOrNull()
                android.util.Log.d(TAG, "Auto backup completed: ${report?.getSummary()}")
                if (report != null && report.hasIssues()) {
                    android.util.Log.w(TAG, "Backup has issues but completed")
                }
                return androidx.work.ListenableWorker.Result.success()
            }

            val error = backupResult.exceptionOrNull()
            val classified = WebDavErrorClassifier.classify(error)
            android.util.Log.e(
                TAG,
                "Auto backup failed: kind=${classified.kind}, msg=${error?.message}",
                error
            )
            return when (classified.kind) {
                WebDavErrorKind.RateLimited,
                WebDavErrorKind.AuthFailed,
                WebDavErrorKind.MethodNotAllowed,
                WebDavErrorKind.MalformedResponse -> androidx.work.ListenableWorker.Result.success()
                WebDavErrorKind.Timeout,
                WebDavErrorKind.NetworkUnreachable -> androidx.work.ListenableWorker.Result.retry()
                else -> androidx.work.ListenableWorker.Result.retry()
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Auto backup error", e)
            val classified = WebDavErrorClassifier.classify(e)
            return when (classified.kind) {
                WebDavErrorKind.RateLimited,
                WebDavErrorKind.AuthFailed,
                WebDavErrorKind.MethodNotAllowed,
                WebDavErrorKind.MalformedResponse -> androidx.work.ListenableWorker.Result.success()
                else -> androidx.work.ListenableWorker.Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
        const val WORK_NAME = "auto_webdav_backup"
        const val KEY_MANUAL_TRIGGER = "manual_trigger"
    }
}
