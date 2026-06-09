package takagi.ru.monica.sync

import kotlinx.coroutines.flow.Flow

const val KEEPASS_REMOTE_SYNC_DEDUPE_KEY = "keepass_visible_remote"
const val KEEPASS_COMPATIBILITY_INDEX_DEDUPE_KEY = "keepass_compat"

data class SyncKey(val value: String) {
    init {
        require(value.isNotBlank()) { "SyncKey cannot be blank" }
    }

    override fun toString(): String = value
}

enum class SyncTargetKind {
    BITWARDEN,
    KEEPASS_DATABASE,
    KEEPASS_COMPATIBILITY_INDEX,
    MDBX_VAULT,
    BACKUP,
    AUTOFILL_SAVE
}

enum class SyncItemKind {
    PASSWORD,
    TOTP,
    NOTE,
    BANK_CARD,
    DOCUMENT,
    PASSKEY,
    QR_CODE
}

enum class SyncBackupProvider {
    WEBDAV,
    ONEDRIVE,
    LOCAL_FILE
}

enum class SyncAutofillDestination {
    MONICA_LOCAL,
    MDBX,
    KEEPASS,
    BITWARDEN
}

sealed class SyncTarget(val kind: SyncTargetKind) {
    abstract val stableKey: SyncKey
    open val defaultDedupeKey: SyncKey get() = stableKey

    data class BitwardenVault(val vaultId: Long) : SyncTarget(SyncTargetKind.BITWARDEN) {
        override val stableKey: SyncKey = SyncKey("bitwarden:$vaultId")
    }

    data class KeePassDatabase(val databaseId: Long) : SyncTarget(SyncTargetKind.KEEPASS_DATABASE) {
        override val stableKey: SyncKey = SyncKey("keepass:$databaseId")
    }

    data class KeePassCompatibilityIndex(
        val databaseId: Long?,
        val itemTypes: Set<SyncItemKind>
    ) : SyncTarget(SyncTargetKind.KEEPASS_COMPATIBILITY_INDEX) {
        init {
            require(itemTypes.isNotEmpty()) { "KeePass compatibility index needs at least one item type" }
        }

        private val databasePart: String = databaseId?.toString() ?: "all"
        private val itemPart: String = itemTypes
            .toList()
            .sortedBy { it.name }
            .joinToString("+") { it.name.lowercase() }

        override val stableKey: SyncKey = SyncKey("keepass_compat:$databasePart:$itemPart")
        override val defaultDedupeKey: SyncKey = SyncKey(KEEPASS_COMPATIBILITY_INDEX_DEDUPE_KEY)
    }

    data class MdbxVault(val databaseId: Long) : SyncTarget(SyncTargetKind.MDBX_VAULT) {
        override val stableKey: SyncKey = SyncKey("mdbx:$databaseId")
    }

    data class Backup(val provider: SyncBackupProvider) : SyncTarget(SyncTargetKind.BACKUP) {
        override val stableKey: SyncKey = SyncKey("backup:${provider.name.lowercase()}")
    }

    data class AutofillSave(
        val destination: SyncAutofillDestination,
        val databaseId: Long?
    ) : SyncTarget(SyncTargetKind.AUTOFILL_SAVE) {
        override val stableKey: SyncKey = SyncKey(
            "autofill:${destination.name.lowercase()}:${databaseId?.toString() ?: "local"}"
        )
    }
}

enum class SyncTrigger {
    MANUAL,
    PAGE_VISIBLE,
    APP_START,
    APP_RESUME,
    LOCAL_MUTATION,
    REMOTE_NOTIFICATION,
    FILE_CHANGED,
    WORKER_RECOVERY,
    RETRY,
    RESTORE,
    DELETE,
    AUTOFILL_SAVE,
    BACKUP_SCHEDULE
}

enum class SyncPriority(val rank: Int) {
    BACKGROUND(0),
    PERIODIC(10),
    PAGE_VISIBLE(20),
    FILE_CHANGE(30),
    REPAIR(40),
    LOCAL_MUTATION(50),
    MANUAL(100);

    companion object {
        fun forTrigger(trigger: SyncTrigger): SyncPriority = when (trigger) {
            SyncTrigger.MANUAL -> MANUAL
            SyncTrigger.LOCAL_MUTATION,
            SyncTrigger.AUTOFILL_SAVE -> LOCAL_MUTATION
            SyncTrigger.RESTORE,
            SyncTrigger.DELETE -> REPAIR
            SyncTrigger.REMOTE_NOTIFICATION,
            SyncTrigger.FILE_CHANGED -> FILE_CHANGE
            SyncTrigger.PAGE_VISIBLE -> PAGE_VISIBLE
            SyncTrigger.BACKUP_SCHEDULE -> PERIODIC
            SyncTrigger.APP_START,
            SyncTrigger.APP_RESUME,
            SyncTrigger.WORKER_RECOVERY,
            SyncTrigger.RETRY -> BACKGROUND
        }
    }
}

enum class SyncMode {
    FOREGROUND,
    BACKGROUND,
    SILENT
}

enum class SyncNetworkPolicy {
    ALLOWED,
    REQUIRED,
    WIFI_ONLY,
    FORBIDDEN
}

data class SyncRequest(
    val requestId: String,
    val target: SyncTarget,
    val trigger: SyncTrigger,
    val createdAtMillis: Long,
    val priority: SyncPriority = SyncPriority.forTrigger(trigger),
    val mode: SyncMode = SyncMode.BACKGROUND,
    val dedupeKey: SyncKey = target.defaultDedupeKey,
    val throttleKey: SyncKey = target.stableKey,
    val networkPolicy: SyncNetworkPolicy = SyncNetworkPolicy.ALLOWED,
    val requiresUnlockedTarget: Boolean = false,
    val debounceMs: Long = 0L,
    val throttleMs: Long = 0L,
    val maxRetryCount: Int = 0
) {
    init {
        require(requestId.isNotBlank()) { "requestId cannot be blank" }
        require(createdAtMillis >= 0L) { "createdAtMillis cannot be negative" }
        require(debounceMs >= 0L) { "debounceMs cannot be negative" }
        require(throttleMs >= 0L) { "throttleMs cannot be negative" }
        require(maxRetryCount >= 0) { "maxRetryCount cannot be negative" }
    }
}

enum class SyncPhase {
    IDLE,
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    BLOCKED,
    CONFLICT,
    CANCELED
}

enum class SyncErrorKind {
    NETWORK_UNAVAILABLE,
    WIFI_REQUIRED,
    AUTH_REQUIRED,
    TARGET_LOCKED,
    PERMISSION_DENIED,
    CONFLICT,
    RATE_LIMITED,
    REMOTE_UNAVAILABLE,
    VALIDATION_FAILED,
    UNEXPECTED
}

data class SyncError(
    val kind: SyncErrorKind,
    val redactedMessage: String? = null,
    val retryable: Boolean = false
)

data class SyncTaskStatus(
    val key: SyncKey,
    val target: SyncTarget,
    val phase: SyncPhase,
    val queuedCount: Int = 0,
    val runningRequestId: String? = null,
    val lastTrigger: SyncTrigger? = null,
    val lastStartedAtMillis: Long? = null,
    val lastFinishedAtMillis: Long? = null,
    val lastSuccessAtMillis: Long? = null,
    val lastError: SyncError? = null,
    val nextRetryAtMillis: Long? = null,
    val progressLabel: String? = null
) {
    init {
        require(queuedCount >= 0) { "queuedCount cannot be negative" }
    }
}

sealed class SyncEnqueueResult {
    data class Accepted(
        val request: SyncRequest,
        val status: SyncTaskStatus,
        val replacedRequestId: String? = null
    ) : SyncEnqueueResult()
    data class Merged(val request: SyncRequest, val existingStatus: SyncTaskStatus) : SyncEnqueueResult()
    data class Skipped(val request: SyncRequest, val reason: String) : SyncEnqueueResult()
    data class Blocked(val request: SyncRequest, val error: SyncError) : SyncEnqueueResult()
}

sealed class SyncExecutionResult {
    data class Success(val finishedAtMillis: Long, val detail: String? = null) : SyncExecutionResult()
    data class Failed(val finishedAtMillis: Long, val error: SyncError) : SyncExecutionResult()
    data class Blocked(val finishedAtMillis: Long, val error: SyncError) : SyncExecutionResult()
    data class Conflict(val finishedAtMillis: Long, val error: SyncError) : SyncExecutionResult()
    data class Canceled(val finishedAtMillis: Long, val reason: String? = null) : SyncExecutionResult()
}

interface SyncCoordinator {
    val statuses: Flow<Map<SyncKey, SyncTaskStatus>>

    fun observe(target: SyncTarget): Flow<SyncTaskStatus?>

    fun observe(key: SyncKey): Flow<SyncTaskStatus?>

    suspend fun request(request: SyncRequest): SyncEnqueueResult

    suspend fun cancel(key: SyncKey, reason: String? = null)
}

interface SyncExecutor {
    fun canExecute(target: SyncTarget): Boolean

    suspend fun execute(request: SyncRequest): SyncExecutionResult
}

interface SyncStatusStore {
    val statuses: Flow<Map<SyncKey, SyncTaskStatus>>

    fun observe(key: SyncKey): Flow<SyncTaskStatus?>

    fun snapshot(): Map<SyncKey, SyncTaskStatus>

    suspend fun update(status: SyncTaskStatus)

    suspend fun clear(key: SyncKey)
}

fun interface SyncNetworkGate {
    fun evaluate(policy: SyncNetworkPolicy): SyncError?
}

interface SyncWorkScheduler {
    suspend fun scheduleRecovery(request: SyncRequest)

    suspend fun cancelRecovery(key: SyncKey)
}
