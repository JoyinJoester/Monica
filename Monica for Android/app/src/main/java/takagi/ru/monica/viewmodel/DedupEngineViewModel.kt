package takagi.ru.monica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.dedup.DedupAction
import takagi.ru.monica.data.dedup.DedupCluster
import takagi.ru.monica.data.dedup.DedupClusterType
import takagi.ru.monica.data.dedup.DedupEngine
import takagi.ru.monica.data.dedup.DedupPreferredSource
import takagi.ru.monica.data.dedup.DedupScope

data class DedupEngineUiState(
    val isLoading: Boolean = true,
    val selectedScope: DedupScope = DedupScope.ALL,
    val preferredSource: DedupPreferredSource = DedupPreferredSource.MONICA_LOCAL,
    val selectedType: DedupClusterType? = null,
    val clusters: List<DedupCluster> = emptyList(),
    val selectionMode: Boolean = false,
    val selectedClusterIds: Set<String> = emptySet(),
    val error: String? = null,
    val message: String? = null
)

class DedupEngineViewModel(
    private val dedupEngine: DedupEngine
) : ViewModel() {
    private val _uiState = MutableStateFlow(DedupEngineUiState())
    val uiState: StateFlow<DedupEngineUiState> = _uiState.asStateFlow()
    private var latestRequestId: Long = 0L
    private var activeRefreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        val scope = _uiState.value.selectedScope
        val requestId = nextRequestId()
        activeRefreshJob?.cancel()
        activeRefreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                scanInBackground(scope)
            }.onSuccess { clusters ->
                if (isLatestRequest(requestId)) {
                    val selectedType = _uiState.value.selectedType.takeIf { type ->
                        type == null || clusters.any { it.type == type }
                    }
                    val validIds = clusters.map { it.id }.toSet()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        clusters = clusters,
                        selectedType = selectedType,
                        selectedClusterIds = _uiState.value.selectedClusterIds.intersect(validIds),
                        error = null
                    )
                }
            }.onFailure { throwable ->
                if (isLatestRequest(requestId)) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = throwable.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    fun updateScope(scope: DedupScope) {
        if (_uiState.value.selectedScope == scope) return
        _uiState.value = _uiState.value.copy(
            selectedScope = scope,
            isLoading = true,
            error = null,
            selectionMode = false,
            selectedClusterIds = emptySet()
        )
        refresh()
    }

    fun updateType(type: DedupClusterType?) {
        _uiState.value = _uiState.value.copy(selectedType = type)
    }

    fun updatePreferredSource(source: DedupPreferredSource) {
        if (_uiState.value.preferredSource == source) return
        _uiState.value = _uiState.value.copy(preferredSource = source)
    }

    fun enterSelectionMode() {
        if (_uiState.value.selectionMode) return
        _uiState.value = _uiState.value.copy(selectionMode = true, selectedClusterIds = emptySet())
    }

    fun exitSelectionMode() {
        if (!_uiState.value.selectionMode && _uiState.value.selectedClusterIds.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            selectionMode = false,
            selectedClusterIds = emptySet()
        )
    }

    fun toggleClusterSelection(clusterId: String) {
        val current = _uiState.value
        val nextIds = current.selectedClusterIds.toMutableSet().apply {
            if (!add(clusterId)) remove(clusterId)
        }
        _uiState.value = current.copy(
            selectionMode = true,
            selectedClusterIds = nextIds
        )
    }

    fun selectAll(clusterIds: List<String>) {
        _uiState.value = _uiState.value.copy(
            selectionMode = true,
            selectedClusterIds = clusterIds.toSet()
        )
    }

    fun clearSelected() {
        if (_uiState.value.selectedClusterIds.isEmpty()) return
        _uiState.value = _uiState.value.copy(selectedClusterIds = emptySet())
    }

    fun performAction(cluster: DedupCluster, action: DedupAction) {
        val requestId = nextRequestId()
        viewModelScope.launch {
            val preferredSource = _uiState.value.preferredSource
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                executeInBackground(cluster, action, preferredSource)
            }.onSuccess { result ->
                runCatching { scanInBackground(_uiState.value.selectedScope) }
                    .onSuccess { clusters ->
                        if (isLatestRequest(requestId)) {
                            val nextSelectedType = _uiState.value.selectedType.takeIf { type ->
                                type == null || clusters.any { it.type == type }
                            }
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                clusters = clusters,
                                selectedType = nextSelectedType,
                                message = result.message,
                                error = null
                            )
                        }
                    }
                    .onFailure { throwable ->
                        if (isLatestRequest(requestId)) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                message = result.message,
                                error = throwable.message ?: "Unknown error"
                            )
                        }
                    }
            }.onFailure { throwable ->
                if (isLatestRequest(requestId)) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = throwable.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    fun performBatchAction(clusters: List<DedupCluster>, action: DedupAction) {
        if (clusters.isEmpty()) return
        val requestId = nextRequestId()
        viewModelScope.launch {
            val preferredSource = _uiState.value.preferredSource
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            var changedCount = 0
            var successCount = 0
            var failureCount = 0

            clusters.forEach { cluster ->
                runCatching {
                    executeInBackground(cluster, action, preferredSource)
                }.onSuccess { result ->
                    successCount++
                    changedCount += result.changedCount
                }.onFailure {
                    failureCount++
                }
            }

            runCatching { scanInBackground(_uiState.value.selectedScope) }
                .onSuccess { refreshedClusters ->
                    if (isLatestRequest(requestId)) {
                        val nextSelectedType = _uiState.value.selectedType.takeIf { type ->
                            type == null || refreshedClusters.any { it.type == type }
                        }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            clusters = refreshedClusters,
                            selectedType = nextSelectedType,
                            selectionMode = false,
                            selectedClusterIds = emptySet(),
                            message = when {
                                failureCount == 0 ->
                                    "已批量处理 $successCount 个分组，变更 $changedCount 项"
                                successCount > 0 ->
                                    "已处理 $successCount 个分组，$failureCount 个失败，变更 $changedCount 项"
                                else ->
                                    "批量操作未成功执行"
                            },
                            error = null
                        )
                    }
                }
                .onFailure { throwable ->
                    if (isLatestRequest(requestId)) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = throwable.message ?: "Unknown error"
                        )
                    }
                }
        }
    }

    fun consumeMessage() {
        if (_uiState.value.message == null) return
        _uiState.value = _uiState.value.copy(message = null)
    }

    private suspend fun scanInBackground(scope: DedupScope): List<DedupCluster> {
        return withContext(Dispatchers.Default) {
            dedupEngine.scan(scope)
        }
    }

    private suspend fun executeInBackground(
        cluster: DedupCluster,
        action: DedupAction,
        preferredSource: DedupPreferredSource
    ) = withContext(Dispatchers.Default) {
        dedupEngine.execute(cluster, action, preferredSource)
    }

    private fun nextRequestId(): Long {
        latestRequestId += 1
        return latestRequestId
    }

    private fun isLatestRequest(requestId: Long): Boolean = requestId == latestRequestId
}
