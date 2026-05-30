package takagi.ru.monica.workers

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.KeePassSyncStatus
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.isRemoteSource
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.KeePassKdbxService
import takagi.ru.monica.utils.isOneDriveAuthTemporarilyUnavailable
import java.util.concurrent.TimeUnit

class KeePassRemoteUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val requestedDatabaseId = inputData.getLong(KEY_DATABASE_ID, -1L).takeIf { it > 0L }
        val targetDatabaseId = resolveTargetDatabaseId(requestedDatabaseId)
        if (targetDatabaseId == null) {
            Log.d(TAG, "No KeePass remote upload pending")
            return Result.success()
        }

        val database = PasswordDatabase.getDatabase(applicationContext)
        val service = KeePassKdbxService(
            context = applicationContext,
            dao = database.localKeePassDatabaseDao(),
            securityManager = SecurityManager(applicationContext)
        )

        return try {
            val uploaded = service.flushPendingRemoteUpload(targetDatabaseId)
            Log.d(TAG, "KeePass remote upload worker completed db=$targetDatabaseId uploaded=$uploaded")
            enqueueIfPending(applicationContext)
            Result.success()
        } catch (error: Exception) {
            Log.w(TAG, "KeePass remote upload worker failed db=$targetDatabaseId", error)
            if (isRemoteConflict(error)) {
                enqueueIfPending(applicationContext)
                Result.success()
            } else if (error.isOneDriveAuthTemporarilyUnavailable()) {
                Result.retry()
            } else if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                enqueueIfPending(applicationContext)
                Result.failure(workDataOf(KEY_ERROR to (error.message ?: "KeePass remote upload failed")))
            }
        }
    }

    private suspend fun resolveTargetDatabaseId(requestedDatabaseId: Long?): Long? = withContext(Dispatchers.IO) {
        val dao = PasswordDatabase.getDatabase(applicationContext).localKeePassDatabaseDao()
        if (requestedDatabaseId != null) {
            val requested = dao.getDatabaseById(requestedDatabaseId)
            if (requested != null && requested.isRemoteSource()) {
                return@withContext requestedDatabaseId
            }
        }
        dao.getAllDatabasesSync()
            .asSequence()
            .filter { it.isRemoteSource() }
            .filter { it.lastSyncStatus == KeePassSyncStatus.PENDING_UPLOAD }
            .sortedByDescending { it.lastAccessedAt }
            .firstOrNull()
            ?.id
    }

    companion object {
        private const val TAG = "KeePassRemoteUploadWorker"
        private const val WORK_NAME = "keepass_remote_upload_queue"
        private const val KEY_DATABASE_ID = "database_id"
        private const val KEY_ERROR = "error"
        private const val MAX_RETRY_COUNT = 3

        fun enqueue(context: Context, databaseId: Long? = null) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val data = workDataOf(KEY_DATABASE_ID to (databaseId ?: -1L))
            val request = OneTimeWorkRequestBuilder<KeePassRemoteUploadWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    request
                )
        }

        fun enqueueIfPending(context: Context) {
            enqueue(context.applicationContext, null)
        }

        private fun isRemoteConflict(error: Throwable): Boolean {
            val message = error.message.orEmpty()
            return message.contains("远端文件已变化", ignoreCase = true) ||
                message.contains("conflict", ignoreCase = true)
        }
    }
}
