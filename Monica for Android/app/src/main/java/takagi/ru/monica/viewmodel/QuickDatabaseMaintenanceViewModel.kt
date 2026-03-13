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
    val errorResId: Int? = null
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

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRunning = true, errorResId = null)
            runCatching {
                engine.run(QuickMaintenanceRequest(categories = categories))
            }.onSuccess { result ->
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    latestResult = result,
                    connectedSources = result.sources,
                    sourceStats = result.sourceStats
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    errorResId = R.string.quick_database_maintenance_error_run
                )
            }
        }
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorResId = null)
    }
}
