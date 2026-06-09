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
import takagi.ru.monica.sync.KEEPASS_REMOTE_SYNC_DEDUPE_KEY
import takagi.ru.monica.sync.SyncDiagnostics
import takagi.ru.monica.sync.SyncKey
import takagi.ru.monica.sync.SyncMode
import takagi.ru.monica.sync.SyncNetworkPolicy
import takagi.ru.monica.sync.SyncPriority
import takagi.ru.monica.sync.SyncRequest
import takagi.ru.monica.sync.SyncTarget
import takagi.ru.monica.sync.SyncTaskAwaitResult
import takagi.ru.monica.sync.SyncTaskRunner
import takagi.ru.monica.sync.SyncTrigger
import takagi.ru.monica.utils.KeePassKdbxService
import takagi.ru.monica.utils.isOneDriveAuthTemporarilyUnavailable
import java.util.concurrent.TimeUnit

class KeePassRemoteUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        var requestedDatabaseId = inputData.getLong(KEY_DATABASE_ID, -1L).takeIf { it > 0L }
        val skippedDatabaseIds = mutableSetOf<Long>()
        var drainedUploads = 0
        var drainSteps = 0

        while (drainSteps < MAX_DRAIN_STEPS) {
            val initialTarget = requestedDatabaseId?.let { "keepass:$it" } ?: "keepass:any_pending"
            val targetDatabaseId = resolveTargetDatabaseId(requestedDatabaseId, skippedDatabaseIds)
            requestedDatabaseId = null
            if (targetDatabaseId == null) {
                if (drainSteps == 0) {
                    val taskId = SyncDiagnostics.nextTaskId("kp-worker")
                    SyncDiagnostics.queued(
                        taskId = taskId,
                        target = initialTarget,
                        trigger = "REMOTE_UPLOAD_WORKER",
                        detail = "attempt=$runAttemptCount"
                    )
                    SyncDiagnostics.skipped(taskId, initialTarget, "REMOTE_UPLOAD_WORKER", "no_pending")
                }
                Log.d(TAG, "KeePass remote upload worker drained uploads=$drainedUploads steps=$drainSteps")
                return Result.success()
            }

            drainSteps += 1
            when (val stepResult = uploadOnePendingDatabase(targetDatabaseId, drainSteps)) {
                is UploadStepResult.Completed -> {
                    if (stepResult.uploaded) {
                        drainedUploads += 1
                    } else {
                        skippedDatabaseIds += targetDatabaseId
                    }
                    Log.d(
                        TAG,
                        "KeePass remote upload step completed db=$targetDatabaseId uploaded=${stepResult.uploaded} drained=$drainedUploads step=$drainSteps"
                    )
                }
                is UploadStepResult.Merged -> {
                    skippedDatabaseIds += targetDatabaseId
                    Log.d(TAG, "KeePass remote upload worker merged with active sync db=$targetDatabaseId")
                }
                is UploadStepResult.Skipped -> {
                    skippedDatabaseIds += targetDatabaseId
                    Log.d(TAG, "KeePass remote upload worker skipped db=$targetDatabaseId reason=${stepResult.reason}")
                }
                is UploadStepResult.Blocked -> {
                    if (stepResult.error.retryable && runAttemptCount < MAX_RETRY_COUNT) {
                        return Result.retry()
                    }
                    return Result.failure(
                        workDataOf(KEY_ERROR to (stepResult.error.redactedMessage ?: stepResult.error.kind.name))
                    )
                }
                is UploadStepResult.Canceled -> {
                    return Result.retry()
                }
                is UploadStepResult.Failed -> {
                    val error = stepResult.error
                    Log.w(TAG, "KeePass remote upload worker failed db=$targetDatabaseId", error)
                    if (isRemoteConflict(error)) {
                        skippedDatabaseIds += targetDatabaseId
                    } else if (error.isOneDriveAuthTemporarilyUnavailable()) {
                        return Result.retry()
                    } else if (runAttemptCount < MAX_RETRY_COUNT) {
                        return Result.retry()
                    } else {
                        return Result.failure(workDataOf(KEY_ERROR to (error.message ?: "KeePass remote upload failed")))
                    }
                }
            }
        }

        val taskId = SyncDiagnostics.nextTaskId("kp-worker")
        SyncDiagnostics.skipped(
            taskId = taskId,
            target = "keepass:any_pending",
            trigger = "REMOTE_UPLOAD_WORKER",
            reason = "drain_limit",
            detail = "steps=$drainSteps uploaded=$drainedUploads"
        )
        return Result.retry()
    }

    private suspend fun uploadOnePendingDatabase(
        targetDatabaseId: Long,
        drainStep: Int
    ): UploadStepResult {
        val taskId = SyncDiagnostics.nextTaskId("kp-worker")
        val target = "keepass:$targetDatabaseId"
        SyncDiagnostics.queued(
            taskId = taskId,
            target = target,
            trigger = "REMOTE_UPLOAD_WORKER",
            detail = "attempt=$runAttemptCount step=$drainStep"
        )
        val syncTarget = SyncTarget.KeePassDatabase(targetDatabaseId)
        val syncResult = SyncTaskRunner.requestAndAwait(
            request = SyncRequest(
                requestId = taskId,
                target = syncTarget,
                trigger = SyncTrigger.WORKER_RECOVERY,
                createdAtMillis = System.currentTimeMillis(),
                priority = SyncPriority.BACKGROUND,
                mode = SyncMode.BACKGROUND,
                dedupeKey = SyncKey(KEEPASS_REMOTE_SYNC_DEDUPE_KEY),
                throttleKey = syncTarget.stableKey,
                networkPolicy = SyncNetworkPolicy.REQUIRED
            )
        ) {
            val startedAt = SyncDiagnostics.start(
                taskId = taskId,
                target = target,
                trigger = "REMOTE_UPLOAD_WORKER",
                detail = "attempt=$runAttemptCount step=$drainStep"
            )
            val database = PasswordDatabase.getDatabase(applicationContext)
            val service = KeePassKdbxService(
                context = applicationContext,
                dao = database.localKeePassDatabaseDao(),
                securityManager = SecurityManager(applicationContext)
            )
            try {
                val uploaded = service.flushPendingRemoteUpload(targetDatabaseId)
                SyncDiagnostics.success(
                    taskId = taskId,
                    target = target,
                    trigger = "REMOTE_UPLOAD_WORKER",
                    startedAt = startedAt,
                    detail = "uploaded=$uploaded step=$drainStep"
                )
                uploaded
            } catch (error: Exception) {
                SyncDiagnostics.failed(
                    taskId = taskId,
                    target = target,
                    trigger = "REMOTE_UPLOAD_WORKER",
                    startedAt = startedAt,
                    error = error,
                    detail = "attempt=$runAttemptCount retry=${runAttemptCount < MAX_RETRY_COUNT} step=$drainStep"
                )
                throw error
            }
        }

        return when (syncResult) {
            is SyncTaskAwaitResult.Completed -> UploadStepResult.Completed(syncResult.value)
            is SyncTaskAwaitResult.Merged -> {
                SyncDiagnostics.skipped(
                    taskId = taskId,
                    target = target,
                    trigger = "REMOTE_UPLOAD_WORKER",
                    reason = "merged",
                    detail = "running=${syncResult.status.runningRequestId.orEmpty()} step=$drainStep"
                )
                UploadStepResult.Merged
            }
            is SyncTaskAwaitResult.Skipped -> {
                SyncDiagnostics.skipped(taskId, target, "REMOTE_UPLOAD_WORKER", syncResult.reason)
                UploadStepResult.Skipped(syncResult.reason)
            }
            is SyncTaskAwaitResult.Blocked -> {
                SyncDiagnostics.blocked(
                    taskId = taskId,
                    target = target,
                    trigger = "REMOTE_UPLOAD_WORKER",
                    reason = syncResult.error.redactedMessage ?: syncResult.error.kind.name
                )
                UploadStepResult.Blocked(syncResult.error)
            }
            is SyncTaskAwaitResult.Canceled -> {
                SyncDiagnostics.skipped(
                    taskId = taskId,
                    target = target,
                    trigger = "REMOTE_UPLOAD_WORKER",
                    reason = syncResult.reason ?: "canceled"
                )
                UploadStepResult.Canceled
            }
            is SyncTaskAwaitResult.Failed -> UploadStepResult.Failed(syncResult.error)
        }
    }

    private suspend fun resolveTargetDatabaseId(
        requestedDatabaseId: Long?,
        skippedDatabaseIds: Set<Long>
    ): Long? = withContext(Dispatchers.IO) {
        val dao = PasswordDatabase.getDatabase(applicationContext).localKeePassDatabaseDao()
        if (requestedDatabaseId != null) {
            val requested = dao.getDatabaseById(requestedDatabaseId)
            if (requested != null && requested.isRemoteSource() && requestedDatabaseId !in skippedDatabaseIds) {
                return@withContext requestedDatabaseId
            }
        }
        dao.getAllDatabasesSync()
            .asSequence()
            .filter { it.isRemoteSource() }
            .filter { it.lastSyncStatus == KeePassSyncStatus.PENDING_UPLOAD }
            .filter { it.id !in skippedDatabaseIds }
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
        private const val MAX_DRAIN_STEPS = 25

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
                    ExistingWorkPolicy.KEEP,
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

    private sealed class UploadStepResult {
        data class Completed(val uploaded: Boolean) : UploadStepResult()
        data object Merged : UploadStepResult()
        data class Skipped(val reason: String) : UploadStepResult()
        data class Blocked(val error: takagi.ru.monica.sync.SyncError) : UploadStepResult()
        data object Canceled : UploadStepResult()
        data class Failed(val error: Exception) : UploadStepResult()
    }
}
