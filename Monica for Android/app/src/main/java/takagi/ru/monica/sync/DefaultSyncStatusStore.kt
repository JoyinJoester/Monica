package takagi.ru.monica.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class DefaultSyncStatusStore : SyncStatusStore {
    private val _statuses = MutableStateFlow<Map<SyncKey, SyncTaskStatus>>(emptyMap())

    override val statuses: Flow<Map<SyncKey, SyncTaskStatus>> = _statuses

    override fun observe(key: SyncKey): Flow<SyncTaskStatus?> {
        return statuses.map { it[key] }
    }

    override fun snapshot(): Map<SyncKey, SyncTaskStatus> {
        return _statuses.value
    }

    override suspend fun update(status: SyncTaskStatus) {
        _statuses.update { old -> old + (status.key to status) }
    }

    override suspend fun clear(key: SyncKey) {
        _statuses.update { old -> old - key }
    }
}
