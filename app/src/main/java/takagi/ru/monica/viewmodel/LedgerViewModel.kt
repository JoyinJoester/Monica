package takagi.ru.monica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.ledger.Asset
import takagi.ru.monica.data.ledger.LedgerCategory
import takagi.ru.monica.data.ledger.LedgerEntry
import takagi.ru.monica.data.ledger.LedgerEntryType
import takagi.ru.monica.data.ledger.LedgerEntryWithRelations
import takagi.ru.monica.repository.LedgerRepository
import takagi.ru.monica.repository.SecureItemRepository

class LedgerViewModel(
    private val repository: LedgerRepository,
    private val secureItemRepository: SecureItemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LedgerUiState())
    val uiState: StateFlow<LedgerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Initialize default categories and assets if needed
            repository.initializeDefaultCategories()
            repository.initializeDefaultAssets()
            
            // Sync bank cards to assets on startup
            repository.syncBankCardsToAssets()
            
            combine(
                repository.observeEntries(),
                repository.observeCategories(),
                repository.observeAssets()
            ) { entries, categories, assets ->
                val summary = computeSummary(entries)
                val currencyStats = computeCurrencyStats(entries)
                val currencyAssetStats = computeCurrencyAssetStats(assets)
                LedgerUiState(
                    isLoading = false,
                    entries = entries,
                    totalIncome = summary.income,
                    totalExpense = summary.expense,
                    categories = categories,
                    currencyStats = currencyStats,
                    currencyAssetStats = currencyAssetStats
                )
            }.collect { _uiState.value = it }
        }
        
        // 自动监听银行卡变化并同步到资产
        viewModelScope.launch {
            secureItemRepository.getItemsByType(ItemType.BANK_CARD).collect {
                repository.syncBankCardsToAssets()
            }
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
    
    private fun computeCurrencyStats(entries: List<LedgerEntryWithRelations>): List<CurrencyStats> {
        // 按货币代码分组统计
        val statsByCurrency = entries.groupBy { it.entry.currencyCode }
            .map { (currencyCode, entriesForCurrency) ->
                val income = entriesForCurrency
                    .filter { it.entry.type == LedgerEntryType.INCOME }
                    .sumOf { it.entry.amountInCents } / 100.0
                    
                val expense = entriesForCurrency
                    .filter { it.entry.type == LedgerEntryType.EXPENSE }
                    .sumOf { it.entry.amountInCents } / 100.0
                    
                CurrencyStats(
                    currencyCode = currencyCode,
                    income = income,
                    expense = expense,
                    transactionCount = entriesForCurrency.size
                )
            }
            .sortedByDescending { it.transactionCount } // 按交易数量降序
            .take(3) // 只取前3种货币
            
        return statsByCurrency
    }
    
    private fun computeCurrencyAssetStats(assets: List<Asset>): List<CurrencyAssetStats> {
        // 按货币代码分组统计资产
        val statsByCurrency = assets
            .filter { it.isActive } // 只统计活跃的资产
            .groupBy { it.currencyCode }
            .map { (currencyCode, assetsForCurrency) ->
                CurrencyAssetStats(
                    currencyCode = currencyCode,
                    totalBalance = assetsForCurrency.sumOf { it.balanceInCents },
                    assetCount = assetsForCurrency.size
                )
            }
            .sortedByDescending { it.totalBalance } // 按总资产降序
            .take(3) // 只取前3种货币
            
        return statsByCurrency
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

    // ===== 资产管理 =====
    val assets = repository.observeAssets()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val totalBalance = repository.observeTotalBalance()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0L
        )

    suspend fun getAssetById(id: Long): Asset? {
        return repository.getAssetById(id)
    }

    fun saveAsset(asset: Asset) {
        viewModelScope.launch {
            repository.upsertAsset(asset)
        }
    }

    fun deleteAssetById(assetId: Long) {
        viewModelScope.launch {
            repository.getAssetById(assetId)?.let { asset ->
                repository.deleteAsset(asset)
            }
        }
    }

    fun syncBankCardsToAssets() {
        viewModelScope.launch {
            repository.syncBankCardsToAssets()
        }
    }

    private data class Summary(val income: Double, val expense: Double)
}

// 单个货币的统计信息
data class CurrencyStats(
    val currencyCode: String,
    val income: Double,
    val expense: Double,
    val transactionCount: Int // 交易数量
) {
    val balance: Double get() = income - expense
}

// 资产按货币统计
data class CurrencyAssetStats(
    val currencyCode: String,
    val totalBalance: Long, // 该货币的总资产(分)
    val assetCount: Int // 该货币的资产数量
)

data class LedgerUiState(
    val isLoading: Boolean = true,
    val entries: List<LedgerEntryWithRelations> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val categories: List<LedgerCategory> = emptyList(),
    val currencyStats: List<CurrencyStats> = emptyList(), // 多货币统计(按交易数量排序,最多3个)
    val currencyAssetStats: List<CurrencyAssetStats> = emptyList() // 资产的多货币统计(最多3个)
)
