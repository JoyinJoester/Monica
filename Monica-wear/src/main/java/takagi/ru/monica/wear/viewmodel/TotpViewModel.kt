package takagi.ru.monica.wear.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import takagi.ru.monica.wear.data.SecureItem
import takagi.ru.monica.wear.data.model.TotpData
import takagi.ru.monica.wear.repository.TotpRepository
import takagi.ru.monica.wear.util.TotpGenerator

/**
 * TOTP项目状态
 * 包含验证码、剩余时间、进度等实时信息
 */
data class TotpItemState(
    val item: SecureItem,
    val totpData: TotpData,
    val code: String,
    val remainingSeconds: Int,
    val progress: Float
)

/**
 * TotpViewModel
 * 管理TOTP验证器列表和实时更新
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TotpViewModel(
    private val repository: TotpRepository,
    private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "WearTotpViewModel"
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    
    // 震动服务
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
    
    // 记录上次震动的秒数，避免重复震动
    private var lastVibrationSecond = -1
    
    // 搜索查询
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // TOTP更新任务
    private var updateJob: Job? = null
    
    // 完整TOTP项目列表（不受搜索影响）
    private val rawAllItems: Flow<List<SecureItem>> = repository.getAllTotpItems()
    
    private val _allTotpItems = MutableStateFlow<List<TotpItemState>>(emptyList())
    val allTotpItems: StateFlow<List<TotpItemState>> = _allTotpItems.asStateFlow()
    
    // 搜索结果列表
    private val _searchResults = MutableStateFlow<List<TotpItemState>>(emptyList())
    val searchResults: StateFlow<List<TotpItemState>> = _searchResults.asStateFlow()
    
    // TOTP项目状态列表（兼容性，指向完整列表）
    @Deprecated("Use allTotpItems instead")
    val totpItems: StateFlow<List<TotpItemState>> = _allTotpItems.asStateFlow()
    
    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        // 监听完整列表变化
        viewModelScope.launch {
            rawAllItems.collect { items ->
                Log.d(TAG, "All items updated: count=${items.size}")
                updateAllTotpStates(items)
                
                // 如果有搜索查询，同时更新搜索结果
                val query = _searchQuery.value
                if (query.isNotBlank()) {
                    val currentStates = _allTotpItems.value
                    val filtered = currentStates.filter { state ->
                        val matchIssuer = state.totpData.issuer.contains(query, ignoreCase = true)
                        val matchAccount = state.totpData.accountName.contains(query, ignoreCase = true)
                        val matchTitle = state.item.title.contains(query, ignoreCase = true)
                        matchIssuer || matchAccount || matchTitle
                    }
                    _searchResults.value = filtered
                }
            }
        }
        
        // 监听搜索查询变化
        viewModelScope.launch {
            _searchQuery.collect { query ->
                if (query.isNotBlank()) {
                    Log.d(TAG, "Searching with query='$query'")
                    // 从当前的完整列表中过滤
                    val currentItems = _allTotpItems.value
                    val filtered = currentItems.filter { state ->
                        val matchIssuer = state.totpData.issuer.contains(query, ignoreCase = true)
                        val matchAccount = state.totpData.accountName.contains(query, ignoreCase = true)
                        val matchTitle = state.item.title.contains(query, ignoreCase = true)
                        matchIssuer || matchAccount || matchTitle
                    }
                    Log.d(TAG, "Search results after filtering: count=${filtered.size}")
                    _searchResults.value = filtered
                } else {
                    _searchResults.value = emptyList()
                }
            }
        }
    }
    
    /**
     * 更新完整TOTP状态列表
     */
    private fun updateAllTotpStates(items: List<SecureItem>) {
        val states = items.mapNotNull { item ->
            try {
                val totpData = json.decodeFromString<TotpData>(item.itemData)
                val code = TotpGenerator.generateOtp(totpData)
                val remainingSeconds = TotpGenerator.getRemainingSeconds(totpData.period)
                val progress = TotpGenerator.getProgress(totpData.period)
                
                TotpItemState(
                    item = item,
                    totpData = totpData,
                    code = code,
                    remainingSeconds = remainingSeconds,
                    progress = progress
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse TOTP item id=${'$'}{item.id}", e)
                null
            }
        }
        Log.d(TAG, "All TOTP states updated: ${states.size} items")
        _allTotpItems.value = states
    }
    
    /**
     * 开始TOTP更新（每秒更新一次）
     */
    fun startTotpUpdates() {
        if (updateJob?.isActive == true) return
        
        updateJob = viewModelScope.launch {
            while (true) {
                delay(1000) // 每秒更新
                
                // 更新完整列表的验证码和进度
                val allItems = _allTotpItems.value
                val updatedAll = allItems.map { state ->
                    val code = TotpGenerator.generateOtp(state.totpData)
                    val remainingSeconds = TotpGenerator.getRemainingSeconds(state.totpData.period)
                    val progress = TotpGenerator.getProgress(state.totpData.period)
                    
                    // 倒计时<=5秒时震动提醒
                    if (remainingSeconds <= 5 && remainingSeconds > 0 && remainingSeconds != lastVibrationSecond) {
                        performVibration()
                        lastVibrationSecond = remainingSeconds
                        Log.d(TAG, "Vibrating at ${remainingSeconds}s")
                    } else if (remainingSeconds > 5) {
                        // 重置震动标记
                        lastVibrationSecond = -1
                    }
                    
                    state.copy(
                        code = code,
                        remainingSeconds = remainingSeconds,
                        progress = progress
                    )
                }
                _allTotpItems.value = updatedAll
                
                // 同时更新搜索结果
                val searchItems = _searchResults.value
                if (searchItems.isNotEmpty()) {
                    val updatedSearch = searchItems.map { state ->
                        val code = TotpGenerator.generateOtp(state.totpData)
                        val remainingSeconds = TotpGenerator.getRemainingSeconds(state.totpData.period)
                        val progress = TotpGenerator.getProgress(state.totpData.period)
                        
                        state.copy(
                            code = code,
                            remainingSeconds = remainingSeconds,
                            progress = progress
                        )
                    }
                    _searchResults.value = updatedSearch
                }
            }
        }
    }
    
    /**
     * 停止TOTP更新
     */
    fun stopTotpUpdates() {
        updateJob?.cancel()
        updateJob = null
        lastVibrationSecond = -1
    }
    
    /**
     * 执行震动反馈
     */
    private fun performVibration() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(
                    VibrationEffect.createOneShot(
                        100,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(100)
            }
        }
    }
    
    /**
     * 复制验证码到剪贴板
     */
    fun copyCode(code: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("TOTP Code", code)
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy code to clipboard", e)
        }
    }
    
    /**
     * 搜索TOTP项目
     */
    fun searchTotpItems(query: String) {
        Log.d(TAG, "searchTotpItems invoked with query='${'$'}query'")
        _searchQuery.value = query
    }
    
    /**
     * 清除搜索
     */
    fun clearSearch() {
        _searchQuery.value = ""
    }
    
    override fun onCleared() {
        super.onCleared()
        stopTotpUpdates()
    }
}
