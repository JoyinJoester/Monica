package takagi.ru.monica.utils

import takagi.ru.monica.data.KeePassDatabaseSourceType
import takagi.ru.monica.data.KeePassOpenMode
import takagi.ru.monica.data.KeePassSyncPhase
import takagi.ru.monica.data.KeePassSyncStatus
import takagi.ru.monica.data.KeepassRemoteSourceDao
import takagi.ru.monica.data.KeepassRemoteSyncState
import takagi.ru.monica.data.KeepassRemoteSyncStateDao
import takagi.ru.monica.data.LocalKeePassDatabaseDao

/**
 * 远端 KeePass 同步协调服务骨架。
 *
 * 当前阶段仅负责为后续 WebDAV / OneDrive 接入提供状态落盘与来源绑定能力。
 */
class RemoteKeePassSyncService(
    private val databaseDao: LocalKeePassDatabaseDao,
    private val remoteSourceDao: KeepassRemoteSourceDao,
    private val syncStateDao: KeepassRemoteSyncStateDao
) {
    suspend fun ensureSyncState(databaseId: Long): KeepassRemoteSyncState {
        return syncStateDao.getState(databaseId) ?: KeepassRemoteSyncState(databaseId = databaseId).also {
            syncStateDao.insertState(it)
        }
    }

    suspend fun bindRemoteSource(
        databaseId: Long,
        sourceId: Long,
        sourceType: KeePassDatabaseSourceType
    ) {
        remoteSourceDao.getSourceById(sourceId) ?: throw IllegalArgumentException("远端来源不存在: $sourceId")
        databaseDao.updateSourceBinding(
            id = databaseId,
            sourceType = sourceType,
            sourceId = sourceId,
            openMode = KeePassOpenMode.WORKING_COPY
        )
        ensureSyncState(databaseId)
    }

    suspend fun markSyncFailure(
        databaseId: Long,
        failureCode: String,
        failureMessage: String
    ) {
        val current = ensureSyncState(databaseId)
        syncStateDao.insertState(
            current.copy(
                syncPhase = KeePassSyncPhase.FAILED,
                lastFailureAt = System.currentTimeMillis(),
                failureCode = failureCode,
                failureMessage = failureMessage,
                retryCount = current.retryCount + 1
            )
        )
        databaseDao.updateSyncStatus(
            id = databaseId,
            status = KeePassSyncStatus.FAILED,
            error = failureMessage,
            syncedAt = null
        )
    }

    suspend fun markLocalChanges(databaseId: Long, workingHash: String?) {
        val current = ensureSyncState(databaseId)
        syncStateDao.insertState(
            current.copy(
                workingHash = workingHash,
                hasLocalChanges = true,
                syncPhase = KeePassSyncPhase.IDLE
            )
        )
        databaseDao.updateSyncStatus(
            id = databaseId,
            status = KeePassSyncStatus.PENDING_UPLOAD,
            error = null,
            syncedAt = null
        )
    }

    suspend fun markConflict(
        databaseId: Long,
        workingHash: String?,
        failureMessage: String
    ) {
        val current = ensureSyncState(databaseId)
        syncStateDao.insertState(
            current.copy(
                workingHash = workingHash,
                hasLocalChanges = true,
                hasRemoteChanges = true,
                syncPhase = KeePassSyncPhase.CONFLICT,
                lastFailureAt = System.currentTimeMillis(),
                failureCode = "REMOTE_CONFLICT",
                failureMessage = failureMessage,
                retryCount = current.retryCount + 1
            )
        )
        databaseDao.updateSyncStatus(
            id = databaseId,
            status = KeePassSyncStatus.CONFLICT,
            error = failureMessage,
            syncedAt = null
        )
    }

    suspend fun markSynchronized(
        databaseId: Long,
        versionToken: String?,
        etag: String?,
        baseHash: String?,
        workingHash: String?
    ) {
        val now = System.currentTimeMillis()
        val current = ensureSyncState(databaseId)
        syncStateDao.insertState(
            current.copy(
                remoteVersionToken = versionToken,
                remoteEtag = etag,
                baseHash = baseHash,
                workingHash = workingHash,
                hasLocalChanges = false,
                hasRemoteChanges = false,
                syncPhase = KeePassSyncPhase.IDLE,
                lastSuccessAt = now,
                lastFailureAt = null,
                failureCode = null,
                failureMessage = null,
                retryCount = 0
            )
        )
        databaseDao.updateSyncStatus(
            id = databaseId,
            status = KeePassSyncStatus.IN_SYNC,
            error = null,
            syncedAt = now
        )
    }
}
