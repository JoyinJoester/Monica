package takagi.ru.monica.ui.v2

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.bitwarden.BitwardenVault

/**
 * 排序方式枚举
 */
enum class V2SortMode {
    TITLE,              // 按标题
    MODIFIED_DATE,      // 按修改日期
    PASSWORD,           // 按密码
    PASSWORD_DATE,      // 按密码修改日期
    PASSWORD_STRENGTH   // 按密码强度
}

/**
 * 排序方向
 */
enum class V2SortDirection {
    ASCENDING,   // 正序 A-Z
    DESCENDING   // 倒序 Z-A
}

/**
 * V2 多源密码库 ViewModel
 * 
 * 统一管理多个数据源（本地、Bitwarden、KeePass）的密码条目
 * 复用 V1 的 SecurityManager 和现有的 Bitwarden 基础设施
 * 
 * 职责：
 * 1. 聚合多数据源的条目
 * 2. 统一筛选和搜索
 * 3. 管理同步服务状态
 * 4. 代理 Bitwarden 操作到 BitwardenViewModel
 */
class V2ViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "V2ViewModel"
    }
    
    // 数据源
    private val database = PasswordDatabase.getDatabase(application)
    private val passwordEntryDao = database.passwordEntryDao()
    private val bitwardenRepository = BitwardenRepository.getInstance(application)
    
    // ==================== 数据源状态 ====================
    
    /**
     * 数据源状态
     */
    data class DataSourceState(
        val id: String,
        val name: String,
        val isConnected: Boolean,
        val isSyncing: Boolean = false,
        val lastSyncTime: Long? = null,
        val itemCount: Int = 0,
        val errorMessage: String? = null
    )
    
    private val _dataSources = MutableStateFlow<List<DataSourceState>>(emptyList())
    val dataSources: StateFlow<List<DataSourceState>> = _dataSources.asStateFlow()
    
    // ==================== 条目数据 ====================
    
    /**
     * 统一条目模型（支持多数据源）
     */
    data class UnifiedEntry(
        val id: Long,
        val source: V2VaultSource,
        val type: V2VaultFilter,
        val title: String,
        val subtitle: String,
        val iconUrl: String? = null,
        val isFavorite: Boolean = false,
        val modifiedAt: Long = 0,
        val originalEntry: PasswordEntry? = null
    )
    
    // 全部条目（未筛选）
    private val _allEntries = MutableStateFlow<List<UnifiedEntry>>(emptyList())
    
    // 当前筛选条件
    private val _selectedSource = MutableStateFlow(V2VaultSource.ALL)
    val selectedSource: StateFlow<V2VaultSource> = _selectedSource.asStateFlow()
    
    private val _selectedFilter = MutableStateFlow(V2VaultFilter.ALL)
    val selectedFilter: StateFlow<V2VaultFilter> = _selectedFilter.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // 排序状态
    private val _sortMode = MutableStateFlow(V2SortMode.TITLE)
    val sortMode: StateFlow<V2SortMode> = _sortMode.asStateFlow()
    
    private val _sortDirection = MutableStateFlow(V2SortDirection.ASCENDING)
    val sortDirection: StateFlow<V2SortDirection> = _sortDirection.asStateFlow()
    
    // 杂项筛选
    private val _filterHasTotp = MutableStateFlow(false)
    val filterHasTotp: StateFlow<Boolean> = _filterHasTotp.asStateFlow()
    
    private val _filterFavorite = MutableStateFlow(false)
    val filterFavorite: StateFlow<Boolean> = _filterFavorite.asStateFlow()
    
    // 筛选后的条目 - 使用嵌套 combine 解决参数限制
    private val filterParams = combine(
        _selectedSource,
        _selectedFilter,
        _searchQuery,
        _filterFavorite
    ) { source, filter, query, filterFav ->
        FilterParams(source, filter, query, filterFav)
    }
    
    private val sortParams = combine(
        _sortMode,
        _sortDirection
    ) { mode, direction ->
        SortParams(mode, direction)
    }
    
    val filteredEntries: StateFlow<List<UnifiedEntry>> = combine(
        _allEntries,
        filterParams,
        sortParams
    ) { entries, filterP, sortP ->
        entries
            .filter { entry ->
                // 数据源筛选
                when (filterP.source) {
                    V2VaultSource.ALL -> true
                    V2VaultSource.LOCAL -> entry.source == V2VaultSource.LOCAL
                    V2VaultSource.BITWARDEN -> entry.source == V2VaultSource.BITWARDEN
                    V2VaultSource.KEEPASS -> entry.source == V2VaultSource.KEEPASS
                }
            }
            .filter { entry ->
                // 类型筛选
                when (filterP.filter) {
                    V2VaultFilter.ALL -> true
                    else -> entry.type == filterP.filter
                }
            }
            .filter { entry ->
                // 收藏筛选
                if (filterP.filterFav) entry.isFavorite else true
            }
            .filter { entry ->
                // 搜索筛选
                if (filterP.query.isBlank()) true
                else {
                    entry.title.contains(filterP.query, ignoreCase = true) ||
                    entry.subtitle.contains(filterP.query, ignoreCase = true)
                }
            }
            .let { list ->
                // 排序
                val sorted = when (sortP.mode) {
                    V2SortMode.TITLE -> list.sortedBy { it.title.lowercase() }
                    V2SortMode.MODIFIED_DATE -> list.sortedBy { it.modifiedAt }
                    V2SortMode.PASSWORD -> list.sortedBy { it.originalEntry?.password ?: "" }
                    V2SortMode.PASSWORD_DATE -> list.sortedBy { it.originalEntry?.updatedAt?.time ?: 0 }
                    V2SortMode.PASSWORD_STRENGTH -> list.sortedBy { 
                        // 简单密码强度评估
                        it.originalEntry?.password?.length ?: 0 
                    }
                }
                if (sortP.direction == V2SortDirection.DESCENDING) sorted.reversed() else sorted
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    // 筛选参数数据类
    private data class FilterParams(
        val source: V2VaultSource,
        val filter: V2VaultFilter,
        val query: String,
        val filterFav: Boolean
    )
    
    // 排序参数数据类
    private data class SortParams(
        val mode: V2SortMode,
        val direction: V2SortDirection
    )
    
    // ==================== 同步状态 ====================
    
    private val _syncState = MutableStateFlow<V2SyncState>(V2SyncState.Idle)
    val syncState: StateFlow<V2SyncState> = _syncState.asStateFlow()
    
    // Bitwarden 特定状态
    private val _bitwardenVaults = MutableStateFlow<List<BitwardenVault>>(emptyList())
    val bitwardenVaults: StateFlow<List<BitwardenVault>> = _bitwardenVaults.asStateFlow()
    
    private val _bitwardenUnlockState = MutableStateFlow<V2UnlockState>(V2UnlockState.Locked)
    val bitwardenUnlockState: StateFlow<V2UnlockState> = _bitwardenUnlockState.asStateFlow()
    
    // ==================== 事件 ====================
    
    sealed class V2Event {
        data class ShowError(val message: String) : V2Event()
        data class ShowSuccess(val message: String) : V2Event()
        object NavigateToBitwardenLogin : V2Event()
        object NavigateToKeePassImport : V2Event()
        
        /**
         * 空 Vault 保护警告事件
         */
        data class EmptyVaultWarning(
            val vaultId: Long,
            val localCount: Int,
            val serverCount: Int
        ) : V2Event()
    }
    
    private val _events = MutableSharedFlow<V2Event>()
    val events = _events.asSharedFlow()
    
    // 空 Vault 保护状态
    private var pendingEmptyVaultVaultId: Long? = null
    
    init {
        loadDataSources()
        loadAllEntries()
    }
    
    // ==================== 公开方法 ====================
    
    /**
     * 设置数据源筛选
     */
    fun setSourceFilter(source: V2VaultSource) {
        _selectedSource.value = source
    }
    
    /**
     * 设置类型筛选
     */
    fun setTypeFilter(filter: V2VaultFilter) {
        _selectedFilter.value = filter
    }
    
    /**
     * 设置搜索查询
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    /**
     * 设置排序方式
     */
    fun setSortMode(mode: V2SortMode) {
        _sortMode.value = mode
    }
    
    /**
     * 设置排序方向
     */
    fun setSortDirection(direction: V2SortDirection) {
        _sortDirection.value = direction
    }
    
    /**
     * 切换收藏筛选
     */
    fun toggleFavoriteFilter() {
        _filterFavorite.value = !_filterFavorite.value
    }
    
    /**
     * 设置收藏筛选
     */
    fun setFavoriteFilter(enabled: Boolean) {
        _filterFavorite.value = enabled
    }
    
    /**
     * 切换 TOTP 筛选
     */
    fun toggleTotpFilter() {
        _filterHasTotp.value = !_filterHasTotp.value
    }

    /**
     * 刷新所有数据
     */
    fun refresh() {
        loadDataSources()
        loadAllEntries()
    }
    
    /**
     * 同步指定数据源
     */
    fun syncDataSource(sourceId: String) {
        viewModelScope.launch {
            when (sourceId) {
                "bitwarden" -> syncBitwarden()
                "local" -> {
                    // 本地数据无需同步，只需刷新
                    loadLocalEntries()
                }
                "keepass" -> {
                    // KeePass 同步逻辑（待实现）
                    _events.emit(V2Event.ShowError("KeePass 同步功能开发中"))
                }
            }
        }
    }
    
    /**
     * 连接 Bitwarden
     */
    fun connectBitwarden() {
        viewModelScope.launch {
            _events.emit(V2Event.NavigateToBitwardenLogin)
        }
    }
    
    /**
     * 断开 Bitwarden 连接
     */
    fun disconnectBitwarden() {
        viewModelScope.launch {
            try {
                val activeVault = bitwardenRepository.getActiveVault()
                if (activeVault != null) {
                    bitwardenRepository.logout(activeVault.id)
                    loadDataSources()
                    loadAllEntries()
                    _events.emit(V2Event.ShowSuccess("已断开 Bitwarden 连接"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "断开 Bitwarden 失败", e)
                _events.emit(V2Event.ShowError("断开失败: ${e.message}"))
            }
        }
    }
    
    /**
     * 导入 KeePass
     */
    fun importKeePass() {
        viewModelScope.launch {
            _events.emit(V2Event.NavigateToKeePassImport)
        }
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 加载数据源状态
     */
    private fun loadDataSources() {
        viewModelScope.launch {
            try {
                val sources = mutableListOf<DataSourceState>()
                
                // 1. 本地数据源（始终存在）- 纯本地条目（非 Bitwarden、非 KeePass）
                val localCount = passwordEntryDao.getLocalEntriesCount()
                sources.add(
                    DataSourceState(
                        id = "local",
                        name = "本地密码库",
                        isConnected = true,
                        itemCount = localCount
                    )
                )
                
                // 2. Bitwarden 数据源
                val bitwardenVaults = bitwardenRepository.getAllVaults()
                _bitwardenVaults.value = bitwardenVaults
                
                if (bitwardenVaults.isNotEmpty()) {
                    val activeVault = bitwardenRepository.getActiveVault()
                    if (activeVault != null) {
                        val isUnlocked = bitwardenRepository.isVaultUnlocked(activeVault.id)
                        _bitwardenUnlockState.value = if (isUnlocked) V2UnlockState.Unlocked else V2UnlockState.Locked
                        
                        val bitwardenCount = passwordEntryDao.getBitwardenEntriesCount(activeVault.id)
                        sources.add(
                            DataSourceState(
                                id = "bitwarden",
                                name = "Bitwarden",
                                isConnected = true,
                                lastSyncTime = activeVault.lastSyncAt,
                                itemCount = bitwardenCount
                            )
                        )
                    }
                } else {
                    sources.add(
                        DataSourceState(
                            id = "bitwarden",
                            name = "Bitwarden",
                            isConnected = false
                        )
                    )
                }
                
                // 3. KeePass 数据源
                val keepassCount = passwordEntryDao.getKeePassEntriesCount()
                sources.add(
                    DataSourceState(
                        id = "keepass",
                        name = "KeePass",
                        isConnected = keepassCount > 0,
                        itemCount = keepassCount
                    )
                )
                
                _dataSources.value = sources
                
            } catch (e: Exception) {
                Log.e(TAG, "加载数据源失败", e)
            }
        }
    }
    
    /**
     * 加载所有条目
     */
    private fun loadAllEntries() {
        viewModelScope.launch {
            try {
                val allEntries = mutableListOf<UnifiedEntry>()
                
                // 加载本地条目（纯本地，非 Bitwarden、非 KeePass）
                allEntries.addAll(loadLocalEntriesInternal())
                
                // 加载 Bitwarden 条目
                allEntries.addAll(loadBitwardenEntriesInternal())
                
                // 加载 KeePass 条目
                allEntries.addAll(loadKeePassEntriesInternal())
                
                _allEntries.value = allEntries
                
            } catch (e: Exception) {
                Log.e(TAG, "加载条目失败", e)
            }
        }
    }
    
    /**
     * 加载本地条目
     */
    private fun loadLocalEntries() {
        viewModelScope.launch {
            try {
                val currentEntries = _allEntries.value.toMutableList()
                
                // 移除旧的本地条目
                currentEntries.removeAll { it.source == V2VaultSource.LOCAL }
                
                // 添加新的本地条目
                currentEntries.addAll(loadLocalEntriesInternal())
                
                _allEntries.value = currentEntries
                
            } catch (e: Exception) {
                Log.e(TAG, "加载本地条目失败", e)
            }
        }
    }
    
    private suspend fun loadLocalEntriesInternal(): List<UnifiedEntry> {
        return try {
            passwordEntryDao.getAllLocalEntries().map { entry ->
                UnifiedEntry(
                    id = entry.id,
                    source = V2VaultSource.LOCAL,
                    type = mapEntryType(entry),
                    title = entry.title,
                    subtitle = entry.username,
                    iconUrl = entry.website,
                    isFavorite = entry.isFavorite,
                    modifiedAt = entry.updatedAt.time,
                    originalEntry = entry
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载本地条目失败", e)
            emptyList()
        }
    }
    
    /**
     * 加载 Bitwarden 条目
     * 
     * 注意：这里不检查 isVaultUnlocked，因为：
     * 1. 数据已经同步到本地数据库
     * 2. isVaultUnlocked 依赖内存缓存（symmetricKeyCache、accessTokenCache）
     * 3. 应用重启后缓存会丢失，但数据库中的数据仍然有效
     * 4. V1 的 BitwardenViewModel 也是直接从数据库读取
     */
    private suspend fun loadBitwardenEntriesInternal(): List<UnifiedEntry> {
        return try {
            val activeVault = bitwardenRepository.getActiveVault() ?: return emptyList()
            
            // 直接从数据库读取已同步的 Bitwarden 条目，不需要检查解锁状态
            // 解锁状态只影响是否能与服务器同步，不影响读取本地已同步的数据
            passwordEntryDao.getEntriesByVaultId(activeVault.id).map { entry ->
                UnifiedEntry(
                    id = entry.id,
                    source = V2VaultSource.BITWARDEN,
                    type = mapEntryType(entry),
                    title = entry.title,
                    subtitle = entry.username,
                    iconUrl = entry.website,
                    isFavorite = entry.isFavorite,
                    modifiedAt = entry.updatedAt.time,
                    originalEntry = entry
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载 Bitwarden 条目失败", e)
            emptyList()
        }
    }
    
    /**
     * 加载 KeePass 条目
     */
    private suspend fun loadKeePassEntriesInternal(): List<UnifiedEntry> {
        return try {
            passwordEntryDao.getAllKeePassEntries().map { entry ->
                UnifiedEntry(
                    id = entry.id,
                    source = V2VaultSource.KEEPASS,
                    type = mapEntryType(entry),
                    title = entry.title,
                    subtitle = entry.username,
                    iconUrl = entry.website,
                    isFavorite = entry.isFavorite,
                    modifiedAt = entry.updatedAt.time,
                    originalEntry = entry
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载 KeePass 条目失败", e)
            emptyList()
        }
    }
    
    /**
     * 同步 Bitwarden
     */
    private suspend fun syncBitwarden() {
        _syncState.value = V2SyncState.Syncing("bitwarden")
        
        try {
            val activeVault = bitwardenRepository.getActiveVault()
            if (activeVault == null) {
                _events.emit(V2Event.ShowError("请先连接 Bitwarden"))
                _syncState.value = V2SyncState.Idle
                return
            }
            
            when (val result = bitwardenRepository.sync(activeVault.id)) {
                is BitwardenRepository.SyncResult.Success -> {
                    loadDataSources()
                    loadAllEntries()
                    _events.emit(V2Event.ShowSuccess("同步完成，已同步 ${result.syncedCount} 项"))
                }
                is BitwardenRepository.SyncResult.Error -> {
                    _events.emit(V2Event.ShowError("同步失败: ${result.message}"))
                }
                is BitwardenRepository.SyncResult.EmptyVaultBlocked -> {
                    // 空 Vault 保护触发，发送警告事件
                    Log.w(TAG, "空 Vault 保护触发: ${result.reason}")
                    pendingEmptyVaultVaultId = result.vaultId
                    _events.emit(V2Event.EmptyVaultWarning(
                        vaultId = result.vaultId,
                        localCount = result.localCount,
                        serverCount = result.serverCount
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "同步 Bitwarden 失败", e)
            _events.emit(V2Event.ShowError("同步失败: ${e.message}"))
        } finally {
            _syncState.value = V2SyncState.Idle
        }
    }
    
    /**
     * 用户确认清空本地数据（响应空 Vault 警告）
     */
    fun confirmEmptyVaultClear() {
        viewModelScope.launch {
            val vaultId = pendingEmptyVaultVaultId ?: return@launch
            pendingEmptyVaultVaultId = null
            
            try {
                // 用户确认，允许清空
                takagi.ru.monica.bitwarden.sync.EmptyVaultProtection.confirmClearLocalData(vaultId)
                
                // 重新触发同步
                _events.emit(V2Event.ShowSuccess("正在重新同步..."))
                syncBitwarden()
            } catch (e: Exception) {
                Log.e(TAG, "确认清空失败", e)
                _events.emit(V2Event.ShowError("操作失败: ${e.message}"))
            }
        }
    }
    
    /**
     * 用户取消同步（响应空 Vault 警告）
     */
    fun cancelEmptyVaultSync() {
        viewModelScope.launch {
            val vaultId = pendingEmptyVaultVaultId ?: return@launch
            pendingEmptyVaultVaultId = null
            
            try {
                takagi.ru.monica.bitwarden.sync.EmptyVaultProtection.cancelSync(vaultId)
                _events.emit(V2Event.ShowSuccess("同步已取消，本地数据已保留"))
            } catch (e: Exception) {
                Log.e(TAG, "取消同步失败", e)
            }
        }
    }
    
    /**
     * 映射条目类型
     * 对于 Bitwarden 条目使用 cipher type: 1=Login, 2=SecureNote, 3=Card, 4=Identity
     * 对于本地/KeePass 条目根据字段内容判断
     */
    private fun mapEntryType(entry: PasswordEntry): V2VaultFilter {
        // 如果是 Bitwarden 条目，使用 cipher type
        if (entry.isBitwardenEntry()) {
            return when (entry.bitwardenCipherType) {
                1 -> V2VaultFilter.LOGIN
                2 -> V2VaultFilter.NOTE
                3 -> V2VaultFilter.CARD
                4 -> V2VaultFilter.IDENTITY
                else -> V2VaultFilter.LOGIN
            }
        }
        
        // 本地/KeePass 条目：根据字段内容智能判断类型
        return when {
            // 有信用卡信息 -> 支付卡
            entry.creditCardNumber.isNotBlank() -> V2VaultFilter.CARD
            // 有身份信息（地址等）-> 身份
            entry.addressLine.isNotBlank() || entry.city.isNotBlank() || 
            (entry.phone.isNotBlank() && entry.email.isNotBlank() && entry.username.isBlank()) -> V2VaultFilter.IDENTITY
            // 只有笔记内容，没有密码和用户名 -> 笔记
            entry.notes.isNotBlank() && entry.password.isBlank() && entry.username.isBlank() -> V2VaultFilter.NOTE
            // 默认为登录类型
            else -> V2VaultFilter.LOGIN
        }
    }
}

/**
 * V2 同步状态
 */
sealed class V2SyncState {
    object Idle : V2SyncState()
    data class Syncing(val sourceId: String) : V2SyncState()
    data class Error(val message: String) : V2SyncState()
}
/**
 * V2 解锁状态
 */
sealed class V2UnlockState {
    object Locked : V2UnlockState()
    object Unlocked : V2UnlockState()
    object Unlocking : V2UnlockState()
}