package takagi.ru.monica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.maintenance.QuickDatabaseMaintenanceEngine
import takagi.ru.monica.data.maintenance.QuickMaintenanceCategory
import takagi.ru.monica.data.maintenance.QuickMaintenanceMode
import takagi.ru.monica.data.maintenance.QuickMaintenancePlan
import takagi.ru.monica.data.maintenance.QuickMaintenanceProgress
import takagi.ru.monica.data.maintenance.QuickMaintenanceRequest
import takagi.ru.monica.data.maintenance.QuickMaintenanceResult
import takagi.ru.monica.data.maintenance.QuickMaintenanceSource
import takagi.ru.monica.data.maintenance.QuickMaintenanceSourceStats

data class QuickDatabaseMaintenanceUiState(
    val isLoading: Boolean = true,
    val isRunning: Boolean = false,
    val includePasswords: Boolean = true,
    val includeAuthenticators: Boolean = true,
    val includeBankCards: Boolean = true,
    val includePasskeys: Boolean = false,
    val connectedSources: List<QuickMaintenanceSource> = emptyList(),
    val sourceStats: List<QuickMaintenanceSourceStats> = emptyList(),
    val latestResult: QuickMaintenanceResult? = null,
    val errorResId: Int? = null,
    val selectedMode: QuickMaintenanceMode = QuickMaintenanceMode.FULL_BIDIRECTIONAL,
    val selectedTargetSourceKey: String? = null,
    val pendingPlan: QuickMaintenancePlan? = null,
    val progress: QuickMaintenanceProgress? = null,
    val operationMessages: List<String> = emptyList()
)

class QuickDatabaseMaintenanceViewModel(
    private val engine: QuickDatabaseMaintenanceEngine
) : ViewModel() {
    private val _uiState = MutableStateFlow(QuickDatabaseMaintenanceUiState())
    val uiState: StateFlow<QuickDatabaseMaintenanceUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorResId = null)
            runCatching {
                val sources = engine.loadSources()
                val stats = engine.loadSourceStats(sources)
                sources to stats
            }
                .onSuccess { (sources, stats) ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        connectedSources = sources,
                        sourceStats = stats
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorResId = R.string.quick_database_maintenance_error_load_sources
                    )
                }
        }
    }

    fun updateIncludePasswords(value: Boolean) {
        _uiState.value = _uiState.value.copy(includePasswords = value)
    }

    fun updateIncludeAuthenticators(value: Boolean) {
        _uiState.value = _uiState.value.copy(includeAuthenticators = value)
    }

    fun updateIncludeBankCards(value: Boolean) {
        _uiState.value = _uiState.value.copy(includeBankCards = value)
    }

    fun updateIncludePasskeys(value: Boolean) {
        _uiState.value = _uiState.value.copy(includePasskeys = value)
    }

    fun updateMode(mode: QuickMaintenanceMode) {
        _uiState.value = _uiState.value.copy(
            selectedMode = mode,
            selectedTargetSourceKey = if (mode == QuickMaintenanceMode.FULL_BIDIRECTIONAL) {
                null
            } else {
                _uiState.value.selectedTargetSourceKey
            }
        )
    }

    fun updateTargetSource(sourceKey: String) {
        _uiState.value = _uiState.value.copy(selectedTargetSourceKey = sourceKey)
    }

    fun runMaintenance() {
        val categories = buildSet {
            if (_uiState.value.includePasswords) add(QuickMaintenanceCategory.PASSWORDS)
            if (_uiState.value.includeAuthenticators) add(QuickMaintenanceCategory.AUTHENTICATORS)
            if (_uiState.value.includeBankCards) add(QuickMaintenanceCategory.BANK_CARDS)
            if (_uiState.value.includePasskeys) add(QuickMaintenanceCategory.PASSKEYS)
        }
        if (categories.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorResId = R.string.quick_database_maintenance_error_no_categories)
            return
        }

        if (_uiState.value.selectedMode == QuickMaintenanceMode.TARGET_DATABASE && _uiState.value.selectedTargetSourceKey.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(errorResId = R.string.quick_database_maintenance_error_no_target)
            return
        }

        val request = QuickMaintenanceRequest(
            categories = categories,
            mode = _uiState.value.selectedMode,
            targetSourceKey = _uiState.value.selectedTargetSourceKey
        )

        viewModelScope.launch {
            runCatching {
                engine.plan(request)
            }.onSuccess { plan ->
                _uiState.value = _uiState.value.copy(
                    pendingPlan = plan,
                    errorResId = null
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorResId = R.string.quick_database_maintenance_error_run)
            }
        }
    }

    fun dismissPlan() {
        _uiState.value = _uiState.value.copy(pendingPlan = null)
    }

    fun confirmAndRunMaintenance() {
        val categories = buildSet {
            if (_uiState.value.includePasswords) add(QuickMaintenanceCategory.PASSWORDS)
            if (_uiState.value.includeAuthenticators) add(QuickMaintenanceCategory.AUTHENTICATORS)
            if (_uiState.value.includeBankCards) add(QuickMaintenanceCategory.BANK_CARDS)
            if (_uiState.value.includePasskeys) add(QuickMaintenanceCategory.PASSKEYS)
        }

        val request = QuickMaintenanceRequest(
            categories = categories,
            mode = _uiState.value.selectedMode,
            targetSourceKey = _uiState.value.selectedTargetSourceKey
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRunning = true,
                errorResId = null,
                pendingPlan = null,
                operationMessages = emptyList()
            )
            runCatching {
                val logs = mutableListOf<String>()
                val result = engine.run(request) { progress ->
                    _uiState.value = _uiState.value.copy(progress = progress)
                    logs += progress.message
                }
                result.copy(operationLogs = logs + result.operationLogs)
            }.onSuccess { result ->
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    progress = null,
                    latestResult = result,
                    connectedSources = result.sources,
                    sourceStats = result.sourceStats,
                    operationMessages = result.operationLogs.takeLast(80)
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    progress = null,
                    errorResId = R.string.quick_database_maintenance_error_run
                )
            }
        }
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorResId = null)
    }
}
