package takagi.ru.monica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import takagi.ru.monica.data.ledger.LedgerCategory
import takagi.ru.monica.data.ledger.LedgerEntry
import takagi.ru.monica.data.ledger.LedgerEntryType
import takagi.ru.monica.data.ledger.LedgerEntryWithRelations
import takagi.ru.monica.repository.LedgerRepository

class LedgerViewModel(
    private val repository: LedgerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LedgerUiState())
    val uiState: StateFlow<LedgerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Initialize default categories if needed
            repository.initializeDefaultCategories()
            
            combine(
                repository.observeEntries(),
                repository.observeCategories()
            ) { entries, categories ->
                val summary = computeSummary(entries)
                LedgerUiState(
                    isLoading = false,
                    entries = entries,
                    totalIncome = summary.income,
                    totalExpense = summary.expense,
                    categories = categories
                )
            }.collect { _uiState.value = it }
        }
    }

    private fun computeSummary(entries: List<LedgerEntryWithRelations>): Summary {
        val income = entries
            .filter { it.entry.type == LedgerEntryType.INCOME }
            .sumOf { it.entry.amountInCents } / 100.0

        val expense = entries
            .filter { it.entry.type == LedgerEntryType.EXPENSE }
            .sumOf { it.entry.amountInCents } / 100.0

        return Summary(income, expense)
    }

    fun saveEntry(entry: LedgerEntry) {
        viewModelScope.launch {
            repository.upsertEntry(entry)
        }
    }

    fun deleteEntry(entry: LedgerEntry) {
        viewModelScope.launch {
            repository.deleteEntry(entry)
        }
    }

    fun saveCategory(category: LedgerCategory) {
        viewModelScope.launch {
            repository.upsertCategory(category)
        }
    }

    private data class Summary(val income: Double, val expense: Double)
}

data class LedgerUiState(
    val isLoading: Boolean = true,
    val entries: List<LedgerEntryWithRelations> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val categories: List<LedgerCategory> = emptyList()
)
