package takagi.ru.monica.workers

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.work.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import takagi.ru.monica.data.*
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.sync.SyncTaskAwaitResult
import takagi.ru.monica.viewmodel.MdbxViewModel

/** Durable delivery; manual and automatic jobs share SyncTaskRunner's per-vault lock. */
class MdbxAutoSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Dirty state remains in Room; foreground/unlock recovery re-enqueues it.
        // Do not park a retry chain (and delay the next unlock) or open a locked vault.
        if (!SessionManager.isUnlocked.value) return Result.success()
        val id = inputData.getLong("database", -1)
        if (id <= 0) return Result.failure()
        val preferences = MdbxAutoSyncPreferences(applicationContext)
        if (!preferences.isEnabled(id)) return Result.success()
        val db = PasswordDatabase.getDatabase(applicationContext)
        val row = db.localMdbxDatabaseDao().getDatabaseById(id) ?: return Result.success()
        if (!eligible(row)) return Result.success()
        if (!inputData.getBoolean("pull", false) && row.lastSyncStatus == MdbxSyncStatus.IN_SYNC.name) return Result.success()
        val states = MdbxSyncStateStore(db.mdbxSyncStateDao())
        val before = states.read(id).syncedCommitInventory
        // Reuse the existing projection importer; scope is explicitly owned/cleared by this job.
        val store = ViewModelStore()
        return try {
            val vm = withContext(Dispatchers.Main) {
                ViewModelProvider(store, object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        modelClass.cast(MdbxViewModel(applicationContext as Application, db.localMdbxDatabaseDao(),
                    db.mdbxRemoteSourceDao(), db.passwordEntryDao(), db.secureItemDao(),
                    db.passkeyDao(), db.attachmentDao(), db.customFieldDao(),
                    SecurityManager(applicationContext), backgroundOnly = true))
                })["sync", MdbxViewModel::class.java]
            }
            when (vm.syncAutomatically(id)) {
                is SyncTaskAwaitResult.Completed -> {
                    val after = db.localMdbxDatabaseDao().getDatabaseById(id)
                    if (after?.lastSyncStatus == MdbxSyncStatus.PENDING_UPLOAD.name) Result.retry() else {
                        if (after?.lastSyncStatus == MdbxSyncStatus.IN_SYNC.name) {
                            preferences.recordSuccess(id, before != states.read(id).syncedCommitInventory)
                        }
                        Result.success()
                    }
                }
                is SyncTaskAwaitResult.Skipped -> Result.success()
                else -> if (runAttemptCount < 6) Result.retry() else Result.failure()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount < 6) Result.retry() else Result.failure()
        } finally {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { store.clear() }
        }
    }

    companion object {
        private val schedulerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
        private val schedulerMutex = kotlinx.coroutines.sync.Mutex()
        internal fun eligible(row: LocalMdbxDatabase) = row.isUsable &&
            row.supports(MdbxCapability.REMOTE_SYNC) && row.isRemoteSource()

        fun enqueue(context: Context, id: Long, pull: Boolean = false) {
            val app = context.applicationContext
            val preferences = MdbxAutoSyncPreferences(app)
            if (!preferences.isEnabled(id)) return
            if (!pull) preferences.resetIdle(id)
            schedulerScope.launch {
              try { schedulerMutex.withLock {
                if (!preferences.isEnabled(id) || (pull && !preferences.isPollDue(id))) return@withLock
                val row = PasswordDatabase.getDatabase(app).localMdbxDatabaseDao().getDatabaseById(id)
                    ?: return@withLock
                if (!eligible(row)) return@withLock
                val work = WorkManager.getInstance(app)
                val active = work.getWorkInfosForUniqueWork("mdbx-auto-$id").get().filter { !it.state.isFinished }
                // An enqueued successor will read all edits already committed before this call.
                // If only the running job remains, append one successor to cover its final-checkpoint race.
                if (active.any { it.state != WorkInfo.State.RUNNING } || (pull && active.isNotEmpty())) return@withLock
            val request = OneTimeWorkRequestBuilder<MdbxAutoSyncWorker>()
                .setInputData(workDataOf("database" to id, "pull" to pull))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(if (pull) 0 else 3, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // Never cancel an in-flight publication. Appended save jobs also cover edits made
            // between the running job's final checkpoint and WorkManager recording completion.
                work.enqueueUniqueWork("mdbx-auto-$id", ExistingWorkPolicy.APPEND_OR_REPLACE, request).result.get()
              } } catch (_: Exception) {
                  // A committed save must remain successful. Pending state is recovered on unlock.
              }
            }
        }
    }
}
