package takagi.ru.monica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.dedup.DedupMergeExecutionResult
import takagi.ru.monica.data.dedup.DedupMergePlan
import takagi.ru.monica.data.dedup.DedupMergeService
import takagi.ru.monica.data.dedup.DedupMergeSourceOption
import takagi.ru.monica.data.dedup.DedupMergeTarget
import takagi.ru.monica.data.dedup.DedupMergeTargetOption

data class DedupEngineUiState(
    val isLoading: Boolean = true,
    val isAnalyzing: Boolean = false,
    val isExecutingMerge: Boolean = false,
    val sourceOptions: List<DedupMergeSourceOption> = emptyList(),
    val selectedMergeSourceKeys: Set<String> = emptySet(),
    val targetOptions: List<DedupMergeTargetOption> = emptyList(),
    val selectedMergeTarget: DedupMergeTarget? = null,
    val mergePlan: DedupMergePlan = DedupMergePlan(),
    val executionResult: DedupMergeExecutionResult? = null,
    val error: String? = null,
    val message: String? = null
)

class DedupEngineViewModel(
    private val mergeService: DedupMergeService
) : ViewModel() {
    private val _uiState = MutableStateFlow(DedupEngineUiState())
    val uiState: StateFlow<DedupEngineUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var analyzeJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                withContext(Dispatchers.Default) {
                    mergeService.getSourceOptions() to mergeService.getTargetOptions()
                }
            }.onSuccess { (sources, targets) ->
                val current = _uiState.value
                val validSourceKeys = sources.map { it.key }.toSet()
                val selectedKeys = current.selectedMergeSourceKeys.intersect(validSourceKeys)
                val selectedTarget = current.selectedMergeTarget?.takeIf { target ->
                    targets.any { it.target == target }
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sourceOptions = sources,
                        selectedMergeSourceKeys = selectedKeys,
                        targetOptions = targets,
                        selectedMergeTarget = selectedTarget,
                        error = null
                    )
                }
                rebuildPlan()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = throwable.message ?: "去重引擎加载失败"
                    )
                }
            }
        }
    }

    fun toggleMergeSource(sourceKey: String) {
        _uiState.update { state ->
            val next = state.selectedMergeSourceKeys.toMutableSet().apply {
                if (!add(sourceKey)) remove(sourceKey)
            }
            state.copy(
                selectedMergeSourceKeys = next,
                executionResult = null,
                message = null,
                error = null
            )
        }
        rebuildPlan()
    }

    fun selectAllSources() {
        _uiState.update { state ->
            state.copy(
                selectedMergeSourceKeys = state.sourceOptions.map { it.key }.toSet(),
                executionResult = null,
                message = null,
                error = null
            )
        }
        rebuildPlan()
    }

    fun clearSources() {
        _uiState.update {
            it.copy(
                selectedMergeSourceKeys = emptySet(),
                mergePlan = DedupMergePlan(target = it.selectedMergeTarget),
                executionResult = null,
                message = null,
                error = null
            )
        }
    }

    fun selectMergeTarget(target: DedupMergeTarget) {
        _uiState.update {
            it.copy(
                selectedMergeTarget = target,
                executionResult = null,
                message = null,
                error = null
            )
        }
        rebuildPlan()
    }

    fun executeMerge() {
        val plan = _uiState.value.mergePlan
        if (plan.selectedSources.isEmpty() || plan.target == null || plan.writableItems <= 0) {
            _uiState.update {
                it.copy(message = "没有可写入的合并结果")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isExecutingMerge = true, error = null, message = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    mergeService.executePlan(plan)
                }
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isExecutingMerge = false,
                        executionResult = result,
                        message = result.toMessage(),
                        error = null
                    )
                }
                refresh()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isExecutingMerge = false,
                        error = throwable.message ?: "合并写入失败"
                    )
                }
            }
        }
    }

    fun consumeMessage() {
        if (_uiState.value.message == null) return
        _uiState.update { it.copy(message = null) }
    }

    private fun rebuildPlan() {
        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            val selectedKeys = _uiState.value.selectedMergeSourceKeys
            val selectedTarget = _uiState.value.selectedMergeTarget
            _uiState.update { it.copy(isAnalyzing = true, error = null) }
            runCatching {
                withContext(Dispatchers.Default) {
                    mergeService.buildPlan(selectedKeys, selectedTarget)
                }
            }.onSuccess { plan ->
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        mergePlan = plan,
                        error = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        error = throwable.message ?: "合并计划生成失败"
                    )
                }
            }
        }
    }
}

private fun DedupMergeExecutionResult.toMessage(): String {
    val details = buildList {
        if (insertedPasswords > 0) add("密码 $insertedPasswords")
        if (insertedSecureItems > 0) add("安全项 $insertedSecureItems")
        if (failedPasswords > 0 || failedSecureItems > 0) add("失败 ${failedPasswords + failedSecureItems}")
        if (skippedExistingItems > 0) add("跳过已有 $skippedExistingItems")
        if (skippedUnsupportedPasskeys > 0) add("通行密钥未复制 $skippedUnsupportedPasskeys")
    }.joinToString("，")
    return "已向 $targetLabel 写入 $insertedItems 条" + details.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
}
