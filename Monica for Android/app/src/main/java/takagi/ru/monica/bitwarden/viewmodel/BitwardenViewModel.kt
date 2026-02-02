package takagi.ru.monica.bitwarden.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.bitwarden.service.LoginResult
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.bitwarden.BitwardenConflictBackup
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenVault

/**
 * Bitwarden ViewModel
 * 
 * 管理 Bitwarden 相关的 UI 状态和用户操作
 * 
 * 主要功能：
 * 1. 登录/登出流程
 * 2. Vault 解锁/锁定
 * 3. 同步状态管理
 * 4. 密码条目和文件夹的访问
 * 5. 冲突解决
 */
class BitwardenViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "BitwardenViewModel"
    }
    
    // 仓库
    private val repository = BitwardenRepository.getInstance(application)
    
    // 两步验证临时状态
    private var twoFactorState: LoginResult.TwoFactorRequired? = null
    private var pendingServerUrl: String? = null
    
    // ==================== UI 状态 ====================
    
    // 登录状态
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()
    
    // Vault 列表
    private val _vaults = MutableStateFlow<List<BitwardenVault>>(emptyList())
    val vaults: StateFlow<List<BitwardenVault>> = _vaults.asStateFlow()
    
    // 当前活跃 Vault
    private val _activeVault = MutableStateFlow<BitwardenVault?>(null)
    val activeVault: StateFlow<BitwardenVault?> = _activeVault.asStateFlow()
    
    // Vault 解锁状态
    private val _unlockState = MutableStateFlow<UnlockState>(UnlockState.Locked)
    val unlockState: StateFlow<UnlockState> = _unlockState.asStateFlow()
    
    // 同步状态
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    // 密码条目列表
    private val _entries = MutableStateFlow<List<PasswordEntry>>(emptyList())
    val entries: StateFlow<List<PasswordEntry>> = _entries.asStateFlow()
    
    // 文件夹列表
    private val _folders = MutableStateFlow<List<BitwardenFolder>>(emptyList())
    val folders: StateFlow<List<BitwardenFolder>> = _folders.asStateFlow()
    
    // 冲突列表
    private val _conflicts = MutableStateFlow<List<BitwardenConflictBackup>>(emptyList())
    val conflicts: StateFlow<List<BitwardenConflictBackup>> = _conflicts.asStateFlow()
    
    // 搜索结果
    private val _searchResults = MutableStateFlow<List<PasswordEntry>>(emptyList())
    val searchResults: StateFlow<List<PasswordEntry>> = _searchResults.asStateFlow()
    
    // 当前选中的文件夹
    private val _selectedFolder = MutableStateFlow<BitwardenFolder?>(null)
    val selectedFolder: StateFlow<BitwardenFolder?> = _selectedFolder.asStateFlow()
    
    // 一次性事件
    private val _events = MutableSharedFlow<BitwardenEvent>()
    val events = _events.asSharedFlow()
    
    init {
        loadVaults()
    }
    
    // ==================== 公开方法 ====================
    
    /**
     * 加载 Vault 列表
     */
    fun loadVaults() {
        viewModelScope.launch {
            try {
                val vaultList = repository.getAllVaults()
                _vaults.value = vaultList
                
                // 加载活跃 Vault
                val active = repository.getActiveVault()
                _activeVault.value = active
                
                if (active != null) {
                    // 检查是否已解锁
                    if (repository.isVaultUnlocked(active.id)) {
                        _unlockState.value = UnlockState.Unlocked
                        loadVaultData(active.id)
                    } else {
                        _unlockState.value = UnlockState.Locked
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载 Vault 失败", e)
                _events.emit(BitwardenEvent.ShowError("加载 Vault 失败: ${e.message}"))
            }
        }
    }
    
    /**
     * 登录 Bitwarden
     */
    fun login(
        serverUrl: String?,
        email: String,
        masterPassword: String
    ) {
        if (email.isBlank() || masterPassword.isBlank()) {
            viewModelScope.launch {
                _events.emit(BitwardenEvent.ShowError("请填写邮箱和主密码"))
            }
            return
        }
        
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            
            val result = repository.login(
                serverUrl = serverUrl?.takeIf { it.isNotBlank() },
                email = email,
                masterPassword = masterPassword
            )
            
            when (result) {
                is BitwardenRepository.RepositoryLoginResult.Success -> {
                    _loginState.value = LoginState.Success(result.vault)
                    _activeVault.value = result.vault
                    _unlockState.value = UnlockState.Unlocked
                    loadVaults()
                    loadVaultData(result.vault.id)
                    _events.emit(BitwardenEvent.ShowSuccess("登录成功"))
                    _events.emit(BitwardenEvent.NavigateToVault(result.vault.id))
                }
                
                is BitwardenRepository.RepositoryLoginResult.TwoFactorRequired -> {
                    twoFactorState = result.state
                    pendingServerUrl = serverUrl
                    _loginState.value = LoginState.TwoFactorRequired(result.providers)
                    _events.emit(BitwardenEvent.ShowTwoFactorDialog(result.providers))
                }
                
                is BitwardenRepository.RepositoryLoginResult.Error -> {
                    _loginState.value = LoginState.Error(result.message)
                    _events.emit(BitwardenEvent.ShowError(result.message))
                }
            }
        }
    }
    
    /**
     * 使用两步验证登录
     */
    fun loginWithTwoFactor(
        serverUrl: String?,
        email: String,
        masterPassword: String,
        twoFactorCode: String,
        twoFactorMethod: Int
    ) {
        val state = twoFactorState ?: run {
            viewModelScope.launch {
                _events.emit(BitwardenEvent.ShowError("两步验证状态丢失，请重新登录"))
            }
            return
        }
        
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            
            val result = repository.loginWithTwoFactor(
                twoFactorState = state,
                twoFactorCode = twoFactorCode,
                twoFactorProvider = twoFactorMethod,
                serverUrl = pendingServerUrl
            )
            
            when (result) {
                is BitwardenRepository.RepositoryLoginResult.Success -> {
                    twoFactorState = null
                    pendingServerUrl = null
                    _loginState.value = LoginState.Success(result.vault)
                    _activeVault.value = result.vault
                    _unlockState.value = UnlockState.Unlocked
                    loadVaults()
                    loadVaultData(result.vault.id)
                    _events.emit(BitwardenEvent.ShowSuccess("登录成功"))
                    _events.emit(BitwardenEvent.NavigateToVault(result.vault.id))
                }
                
                is BitwardenRepository.RepositoryLoginResult.TwoFactorRequired -> {
                    _loginState.value = LoginState.TwoFactorRequired(result.providers)
                    _events.emit(BitwardenEvent.ShowError("验证码错误，请重试"))
                }
                
                is BitwardenRepository.RepositoryLoginResult.Error -> {
                    _loginState.value = LoginState.Error(result.message)
                    _events.emit(BitwardenEvent.ShowError(result.message))
                }
            }
        }
    }
    
    /**
     * 解锁 Vault
     */
    fun unlock(masterPassword: String) {
        val vault = _activeVault.value ?: return
        
        viewModelScope.launch {
            _unlockState.value = UnlockState.Unlocking
            
            when (val result = repository.unlock(vault.id, masterPassword)) {
                is BitwardenRepository.UnlockResult.Success -> {
                    _unlockState.value = UnlockState.Unlocked
                    loadVaultData(vault.id)
                    _events.emit(BitwardenEvent.ShowSuccess("Vault 已解锁"))
                }
                
                is BitwardenRepository.UnlockResult.Error -> {
                    _unlockState.value = UnlockState.Locked
                    _events.emit(BitwardenEvent.ShowError(result.message))
                }
            }
        }
    }
    
    /**
     * 锁定 Vault
     */
    fun lock() {
        val vault = _activeVault.value ?: return
        
        viewModelScope.launch {
            repository.lock(vault.id)
            _unlockState.value = UnlockState.Locked
            _entries.value = emptyList()
            _folders.value = emptyList()
            _events.emit(BitwardenEvent.ShowSuccess("Vault 已锁定"))
        }
    }
    
    /**
     * 锁定所有 Vault
     */
    fun lockAll() {
        viewModelScope.launch {
            repository.lockAll()
            _unlockState.value = UnlockState.Locked
            _entries.value = emptyList()
            _folders.value = emptyList()
        }
    }
    
    /**
     * 登出
     */
    fun logout(vaultId: Long? = null) {
        val targetVaultId = vaultId ?: _activeVault.value?.id ?: return
        
        viewModelScope.launch {
            val success = repository.logout(targetVaultId)
            if (success) {
                loadVaults()
                _entries.value = emptyList()
                _folders.value = emptyList()
                _unlockState.value = UnlockState.Locked
                _loginState.value = LoginState.Idle
                _events.emit(BitwardenEvent.ShowSuccess("已登出"))
                _events.emit(BitwardenEvent.NavigateToLogin)
            } else {
                _events.emit(BitwardenEvent.ShowError("登出失败"))
            }
        }
    }
    
    /**
     * 切换活跃 Vault
     */
    fun setActiveVault(vault: BitwardenVault) {
        repository.setActiveVault(vault.id)
        _activeVault.value = vault
        
        if (repository.isVaultUnlocked(vault.id)) {
            _unlockState.value = UnlockState.Unlocked
            viewModelScope.launch {
                loadVaultData(vault.id)
            }
        } else {
            _unlockState.value = UnlockState.Locked
            _entries.value = emptyList()
            _folders.value = emptyList()
        }
    }
    
    /**
     * 同步
     */
    fun sync() {
        val vault = _activeVault.value ?: return
        
        if (!repository.isVaultUnlocked(vault.id)) {
            viewModelScope.launch {
                _events.emit(BitwardenEvent.ShowError("请先解锁 Vault"))
            }
            return
        }
        
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            
            when (val result = repository.sync(vault.id)) {
                is BitwardenRepository.SyncResult.Success -> {
                    _syncState.value = SyncState.Success(result.syncedCount, result.conflictCount)
                    loadVaultData(vault.id)
                    
                    if (result.conflictCount > 0) {
                        _events.emit(BitwardenEvent.ShowWarning("同步完成，但有 ${result.conflictCount} 个冲突需要处理"))
                    } else {
                        _events.emit(BitwardenEvent.ShowSuccess("同步完成，共 ${result.syncedCount} 条记录"))
                    }
                }
                
                is BitwardenRepository.SyncResult.Error -> {
                    _syncState.value = SyncState.Error(result.message)
                    _events.emit(BitwardenEvent.ShowError("同步失败: ${result.message}"))
                }
            }
        }
    }
    
    /**
     * 搜索
     */
    fun search(query: String) {
        val vault = _activeVault.value ?: return
        
        viewModelScope.launch {
            if (query.isBlank()) {
                _searchResults.value = emptyList()
            } else {
                _searchResults.value = repository.searchEntries(vault.id, query)
            }
        }
    }
    
    /**
     * 清除搜索
     */
    fun clearSearch() {
        _searchResults.value = emptyList()
    }
    
    /**
     * 选择文件夹
     */
    fun selectFolder(folder: BitwardenFolder?) {
        _selectedFolder.value = folder
        
        viewModelScope.launch {
            if (folder == null) {
                val vault = _activeVault.value ?: return@launch
                _entries.value = repository.getPasswordEntries(vault.id)
            } else {
                _entries.value = repository.getPasswordEntriesByFolder(folder.bitwardenFolderId)
            }
        }
    }
    
    /**
     * 解决冲突：使用本地版本
     */
    fun resolveConflictWithLocal(conflictId: Long) {
        viewModelScope.launch {
            val success = repository.resolveConflictWithLocal(conflictId)
            if (success) {
                loadConflicts()
                _events.emit(BitwardenEvent.ShowSuccess("冲突已解决（保留本地版本）"))
            } else {
                _events.emit(BitwardenEvent.ShowError("解决冲突失败"))
            }
        }
    }
    
    /**
     * 解决冲突：使用服务器版本
     */
    fun resolveConflictWithServer(conflictId: Long) {
        viewModelScope.launch {
            val success = repository.resolveConflictWithServer(conflictId)
            if (success) {
                loadConflicts()
                sync() // 重新同步以获取服务器版本
                _events.emit(BitwardenEvent.ShowSuccess("冲突已解决（使用服务器版本）"))
            } else {
                _events.emit(BitwardenEvent.ShowError("解决冲突失败"))
            }
        }
    }
    
    /**
     * 重置登录状态
     */
    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }
    
    // ==================== 设置相关 ====================
    
    var isAutoSyncEnabled: Boolean
        get() = repository.isAutoSyncEnabled
        set(value) { repository.isAutoSyncEnabled = value }
    
    var isSyncOnWifiOnly: Boolean
        get() = repository.isSyncOnWifiOnly
        set(value) { repository.isSyncOnWifiOnly = value }
    
    val lastSyncTime: Long
        get() = repository.lastSyncTime
    
    // ==================== 私有方法 ====================
    
    private suspend fun loadVaultData(vaultId: Long) {
        try {
            _entries.value = repository.getPasswordEntries(vaultId)
            _folders.value = repository.getFolders(vaultId)
            loadConflicts()
        } catch (e: Exception) {
            Log.e(TAG, "加载 Vault 数据失败", e)
            _events.emit(BitwardenEvent.ShowError("加载数据失败: ${e.message}"))
        }
    }
    
    private suspend fun loadConflicts() {
        val vault = _activeVault.value ?: return
        _conflicts.value = repository.getConflictBackups(vault.id)
    }
    
    // ==================== 状态类型 ====================
    
    sealed class LoginState {
        object Idle : LoginState()
        object Loading : LoginState()
        data class Success(val vault: BitwardenVault) : LoginState()
        data class TwoFactorRequired(val methods: List<Int>) : LoginState()
        data class Error(val message: String) : LoginState()
    }
    
    sealed class UnlockState {
        object Locked : UnlockState()
        object Unlocking : UnlockState()
        object Unlocked : UnlockState()
    }
    
    sealed class SyncState {
        object Idle : SyncState()
        object Syncing : SyncState()
        data class Success(val syncedCount: Int, val conflictCount: Int) : SyncState()
        data class Error(val message: String) : SyncState()
    }
    
    // ==================== 事件类型 ====================
    
    sealed class BitwardenEvent {
        data class ShowSuccess(val message: String) : BitwardenEvent()
        data class ShowError(val message: String) : BitwardenEvent()
        data class ShowWarning(val message: String) : BitwardenEvent()
        data class ShowTwoFactorDialog(val methods: List<Int>) : BitwardenEvent()
        data class NavigateToVault(val vaultId: Long) : BitwardenEvent()
        object NavigateToLogin : BitwardenEvent()
    }
}
