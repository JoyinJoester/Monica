package takagi.ru.monica.bitwarden.viewmodel

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.bitwarden.service.LoginResult
import takagi.ru.monica.bitwarden.sync.BitwardenSyncOrchestrator
import takagi.ru.monica.bitwarden.sync.NetworkGateResult
import takagi.ru.monica.bitwarden.sync.SyncBlockReason
import takagi.ru.monica.bitwarden.sync.SyncExecutionOutcome
import takagi.ru.monica.bitwarden.sync.SyncTriggerReason
import takagi.ru.monica.bitwarden.sync.VaultSyncStatus
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.bitwarden.BitwardenConflictBackup
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenSend
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
        private const val COLD_START_AUTO_SYNC_GRACE_MS = 8_000L
    }
    
    // 仓库
    private val repository = BitwardenRepository.getInstance(application)
    
    // 两步验证临时状态
    private var twoFactorState: LoginResult.TwoFactorRequired? = null
    private var pendingServerUrl: String? = null
    private val processStartMs = System.currentTimeMillis()
    private val delayedAutoSyncJobs = mutableMapOf<Long, Job>()
    
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

    // Send 列表
    private val _sends = MutableStateFlow<List<BitwardenSend>>(emptyList())
    val sends: StateFlow<List<BitwardenSend>> = _sends.asStateFlow()

    // Send 页面状态
    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState.asStateFlow()
    
    // 冲突列表
    private val _conflicts = MutableStateFlow<List<BitwardenConflictBackup>>(emptyList())
    val conflicts: StateFlow<List<BitwardenConflictBackup>> = _conflicts.asStateFlow()
    
    // 搜索结果
    private val _searchResults = MutableStateFlow<List<PasswordEntry>>(emptyList())
    val searchResults: StateFlow<List<PasswordEntry>> = _searchResults.asStateFlow()
    
    // 当前选中的文件夹
    private val _selectedFolder = MutableStateFlow<BitwardenFolder?>(null)
    val selectedFolder: StateFlow<BitwardenFolder?> = _selectedFolder.asStateFlow()
    
    // 永不锁定设置状态
    private val _isNeverLockEnabled = MutableStateFlow(false)
    val isNeverLockEnabledFlow: StateFlow<Boolean> = _isNeverLockEnabled.asStateFlow()

    // 同步设置状态（用于界面实时更新）
    private val _isAutoSyncEnabled = MutableStateFlow(false)
    val isAutoSyncEnabledFlow: StateFlow<Boolean> = _isAutoSyncEnabled.asStateFlow()

    private val _isSyncOnWifiOnly = MutableStateFlow(false)
    val isSyncOnWifiOnlyFlow: StateFlow<Boolean> = _isSyncOnWifiOnly.asStateFlow()
    
    // 一次性事件
    private val _events = MutableSharedFlow<BitwardenEvent>()
    val events = _events.asSharedFlow()
    
    private val syncOrchestrator = BitwardenSyncOrchestrator(
        scope = viewModelScope,
        isAutoSyncEnabled = { _isAutoSyncEnabled.value },
        checkNetwork = { evaluateNetworkGate() },
        isVaultUnlocked = { vaultId -> repository.isVaultUnlocked(vaultId) },
        executeSync = { vaultId, silent -> runSync(vaultId = vaultId, silent = silent) }
    )
    val syncStatusByVault: StateFlow<Map<Long, VaultSyncStatus>> = syncOrchestrator.statusByVault
    
    init {
        // 加载永不锁定设置
        _isNeverLockEnabled.value = repository.isNeverLockEnabled
        _isAutoSyncEnabled.value = repository.isAutoSyncEnabled
        _isSyncOnWifiOnly.value = repository.isSyncOnWifiOnly
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
                        _sendState.value = SendState.Idle
                        loadVaultData(active.id)
                        maybeTriggerSilentAutoSync(active, trigger = "loadVaults:activeUnlocked")
                    } else if (_isNeverLockEnabled.value) {
                        // 永不锁定模式：尝试从存储恢复解锁状态
                        Log.d(TAG, "永不锁定模式：尝试恢复 Vault 解锁状态")
                        val restored = repository.tryRestoreUnlockState(active.id)
                        if (restored) {
                            _unlockState.value = UnlockState.Unlocked
                            _sendState.value = SendState.Idle
                            loadVaultData(active.id)
                            maybeTriggerSilentAutoSync(active, trigger = "loadVaults:restoredUnlock")
                            Log.d(TAG, "成功恢复 Vault 解锁状态")
                        } else {
                            _unlockState.value = UnlockState.Locked
                            _sendState.value = SendState.Locked
                            _sends.value = emptyList()
                            Log.w(TAG, "无法恢复 Vault 解锁状态，需要手动解锁")
                        }
                    } else {
                        _unlockState.value = UnlockState.Locked
                        _sendState.value = SendState.Locked
                        _sends.value = emptyList()
                    }
                } else {
                    _sendState.value = SendState.Idle
                    _sends.value = emptyList()
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
        masterPassword: String,
        captchaResponse: String? = null
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
                masterPassword = masterPassword,
                captchaResponse = captchaResponse
            )
            
            when (result) {
                is BitwardenRepository.RepositoryLoginResult.Success -> {
                    _loginState.value = LoginState.Success(result.vault)
                    _activeVault.value = result.vault
                    _unlockState.value = UnlockState.Unlocked
                    _events.emit(BitwardenEvent.ShowSuccess("登录成功"))
                    _events.emit(BitwardenEvent.NavigateToVault(result.vault.id))
                    // 延迟加载以避免并发问题
                    kotlinx.coroutines.delay(100)
                    loadVaults()
                    loadVaultData(result.vault.id)
                }
                
                is BitwardenRepository.RepositoryLoginResult.TwoFactorRequired -> {
                    twoFactorState = result.state
                    pendingServerUrl = serverUrl
                    _loginState.value = LoginState.TwoFactorRequired(result.providers)
                    _events.emit(BitwardenEvent.ShowTwoFactorDialog(result.providers))
                }

                is BitwardenRepository.RepositoryLoginResult.CaptchaRequired -> {
                    if (!result.siteKey.isNullOrBlank()) {
                        _loginState.value = LoginState.Error(result.message)
                        _events.emit(
                            BitwardenEvent.ShowCaptchaDialog(
                                message = result.message,
                                forTwoFactor = false,
                                siteKey = result.siteKey
                            )
                        )
                    } else {
                        val message = "登录被风控拦截，请稍后重试或使用官方客户端完成一次验证后再试。"
                        _loginState.value = LoginState.Error(message)
                        _events.emit(BitwardenEvent.ShowError(message))
                    }
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
        twoFactorCode: String,
        twoFactorMethod: Int,
        captchaResponse: String? = null
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
                serverUrl = pendingServerUrl,
                captchaResponse = captchaResponse
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

                is BitwardenRepository.RepositoryLoginResult.CaptchaRequired -> {
                    if (!result.siteKey.isNullOrBlank()) {
                        _loginState.value = LoginState.Error(result.message)
                        _events.emit(
                            BitwardenEvent.ShowCaptchaDialog(
                                message = result.message,
                                forTwoFactor = true,
                                siteKey = result.siteKey
                            )
                        )
                    } else {
                        val message = "两步验证被风控拦截，请稍后重试或使用官方客户端完成一次验证后再试。"
                        _loginState.value = LoginState.Error(message)
                        _events.emit(BitwardenEvent.ShowError(message))
                    }
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
                    _sendState.value = SendState.Idle
                    loadVaultData(vault.id)
                    maybeTriggerSilentAutoSync(vault, trigger = "unlock")
                    _events.emit(BitwardenEvent.ShowSuccess("Vault 已解锁"))
                }
                
                is BitwardenRepository.UnlockResult.Error -> {
                    _unlockState.value = UnlockState.Locked
                    _sendState.value = SendState.Locked
                    _sends.value = emptyList()
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
            syncOrchestrator.clearVault(vault.id)
            _unlockState.value = UnlockState.Locked
            _entries.value = emptyList()
            _folders.value = emptyList()
            _sends.value = emptyList()
            _events.emit(BitwardenEvent.ShowSuccess("Vault 已锁定"))
        }
    }
    
    /**
     * 锁定所有 Vault
     */
    fun lockAll() {
        viewModelScope.launch {
            repository.lockAll()
            _vaults.value.forEach { syncOrchestrator.clearVault(it.id) }
            _unlockState.value = UnlockState.Locked
            _entries.value = emptyList()
            _folders.value = emptyList()
            _sends.value = emptyList()
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
                syncOrchestrator.clearVault(targetVaultId)
                loadVaults()
                _entries.value = emptyList()
                _folders.value = emptyList()
                _sends.value = emptyList()
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
                maybeTriggerSilentAutoSync(vault, trigger = "setActiveVault")
            }
        } else {
            _unlockState.value = UnlockState.Locked
            _entries.value = emptyList()
            _folders.value = emptyList()
            _sends.value = emptyList()
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

        syncOrchestrator.requestSync(
            vaultId = vault.id,
            reason = SyncTriggerReason.MANUAL,
            force = true
        )
    }

    /**
     * 页面进入时触发自动同步（节流+门控由 Orchestrator 负责）。
     */
    fun requestPageEnterAutoSync(vaultId: Long? = null) {
        val targetVaultId = vaultId ?: _activeVault.value?.id ?: return
        requestAutoSyncWithStartupGrace(targetVaultId, SyncTriggerReason.PAGE_ENTER)
    }

    /**
     * 本地数据增删改后触发自动同步（带防抖）。
     */
    fun requestLocalMutationSync(vaultId: Long? = null) {
        val targetVaultId = vaultId ?: _activeVault.value?.id ?: return
        syncOrchestrator.requestSync(
            vaultId = targetVaultId,
            reason = SyncTriggerReason.LOCAL_MUTATION
        )
    }

    /**
     * 指定 vault 的手动同步请求（用于页面顶部“一键同步”按钮）。
     */
    fun requestManualSync(vaultId: Long) {
        syncOrchestrator.requestSync(
            vaultId = vaultId,
            reason = SyncTriggerReason.MANUAL,
            force = true
        )
    }

    /**
     * 加载 Send 列表
     */
    fun loadSends(forceRemoteSync: Boolean = false) {
        val vault = _activeVault.value
        if (vault == null) {
            _sends.value = emptyList()
            _sendState.value = SendState.Error("请先连接 Bitwarden Vault")
            return
        }
        if (!repository.isVaultUnlocked(vault.id)) {
            _sendState.value = SendState.Locked
            return
        }

        viewModelScope.launch {
            if (!forceRemoteSync) {
                _sendState.value = SendState.Loading
                _sends.value = repository.getSends(vault.id)
                _sendState.value = SendState.Idle
                return@launch
            }

            _sendState.value = SendState.Syncing
            when (val result = repository.refreshSends(vault.id)) {
                is BitwardenRepository.SendSyncResult.Success -> {
                    _sends.value = result.sends
                    _sendState.value = SendState.Idle
                }
                is BitwardenRepository.SendSyncResult.Warning -> {
                    _sends.value = result.sends
                    _sendState.value = SendState.Warning(result.message)
                }
                is BitwardenRepository.SendSyncResult.Error -> {
                    _sends.value = repository.getSends(vault.id)
                    _sendState.value = SendState.Error(result.message)
                }
            }
        }
    }

    /**
     * 创建文本 Send
     */
    fun createTextSend(
        title: String,
        text: String,
        notes: String?,
        password: String?,
        maxAccessCount: Int?,
        hideEmail: Boolean,
        hiddenText: Boolean,
        expireInDays: Int
    ) {
        val vault = _activeVault.value ?: return
        if (!repository.isVaultUnlocked(vault.id)) {
            _sendState.value = SendState.Locked
            return
        }

        viewModelScope.launch {
            _sendState.value = SendState.Creating
            val now = System.currentTimeMillis()
            val days = expireInDays.coerceIn(1, 30)
            val expirationMillis = now + days * 24L * 60L * 60L * 1000L
            val deletionMillis = now + (days + 1).coerceAtMost(31) * 24L * 60L * 60L * 1000L

            when (
                val result = repository.createTextSend(
                    vaultId = vault.id,
                    title = title.trim(),
                    text = text.trim(),
                    notes = notes?.trim(),
                    password = password?.trim(),
                    maxAccessCount = maxAccessCount,
                    hideEmail = hideEmail,
                    hiddenText = hiddenText,
                    deletionMillis = deletionMillis,
                    expirationMillis = expirationMillis
                )
            ) {
                is BitwardenRepository.SendMutationResult.Success -> {
                    _sends.value = listOf(result.send) + _sends.value.filterNot {
                        it.bitwardenSendId == result.send.bitwardenSendId
                    }
                    _sendState.value = SendState.Idle
                    requestLocalMutationSync()
                    _events.emit(BitwardenEvent.ShowSuccess("Send 已创建"))
                }
                is BitwardenRepository.SendMutationResult.Error -> {
                    _sendState.value = SendState.Error(result.message)
                    _events.emit(BitwardenEvent.ShowError(result.message))
                }
                is BitwardenRepository.SendMutationResult.Deleted -> {
                    _sendState.value = SendState.Idle
                }
            }
        }
    }

    /**
     * 删除 Send
     */
    fun deleteSend(sendId: String) {
        val vault = _activeVault.value ?: return
        if (!repository.isVaultUnlocked(vault.id)) {
            _sendState.value = SendState.Locked
            return
        }

        viewModelScope.launch {
            _sendState.value = SendState.Deleting
            when (val result = repository.deleteSend(vault.id, sendId)) {
                is BitwardenRepository.SendMutationResult.Deleted -> {
                    _sends.value = _sends.value.filterNot { it.bitwardenSendId == result.sendId }
                    _sendState.value = SendState.Idle
                    requestLocalMutationSync()
                    _events.emit(BitwardenEvent.ShowSuccess("Send 已删除"))
                }
                is BitwardenRepository.SendMutationResult.Error -> {
                    _sendState.value = SendState.Error(result.message)
                    _events.emit(BitwardenEvent.ShowError(result.message))
                }
                is BitwardenRepository.SendMutationResult.Success -> {
                    _sendState.value = SendState.Idle
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
                _entries.value = repository.getPasswordEntriesByFolder(folder.vaultId, folder.bitwardenFolderId)
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
        get() = _isAutoSyncEnabled.value
        set(value) {
            repository.isAutoSyncEnabled = value
            _isAutoSyncEnabled.value = value
            if (!value) {
                repository.isSyncOnWifiOnly = false
                _isSyncOnWifiOnly.value = false
            }
        }
    
    var isSyncOnWifiOnly: Boolean
        get() = _isSyncOnWifiOnly.value
        set(value) {
            repository.isSyncOnWifiOnly = value
            _isSyncOnWifiOnly.value = value
        }
    
    /**
     * 是否永不锁定 Bitwarden
     */
    var isNeverLockEnabled: Boolean
        get() = _isNeverLockEnabled.value
        set(value) { 
            repository.isNeverLockEnabled = value
            _isNeverLockEnabled.value = value
        }
    
    val lastSyncTime: Long
        get() = repository.lastSyncTime
    
    // 同步队列计数（实时）
    val pendingSyncCount: StateFlow<Int> = repository.getPendingSyncCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedSyncCount: StateFlow<Int> = repository.getFailedSyncCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    // ==================== 私有方法 ====================
    
    private suspend fun loadVaultData(vaultId: Long) {
        try {
            _entries.value = repository.getPasswordEntries(vaultId)
            _folders.value = repository.getFolders(vaultId)
            _sends.value = repository.getSends(vaultId)
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

    private fun maybeTriggerSilentAutoSync(vault: BitwardenVault, trigger: String) {
        val reason = when (trigger) {
            "unlock" -> SyncTriggerReason.APP_RESUME
            else -> SyncTriggerReason.PAGE_ENTER
        }
        Log.d(TAG, "Trigger auto sync: vault=${vault.id}, reason=$trigger")
        requestAutoSyncWithStartupGrace(vault.id, reason)
    }

    private fun requestAutoSyncWithStartupGrace(vaultId: Long, reason: SyncTriggerReason) {
        val elapsed = System.currentTimeMillis() - processStartMs
        val remaining = COLD_START_AUTO_SYNC_GRACE_MS - elapsed
        if (remaining <= 0L) {
            syncOrchestrator.requestSync(vaultId, reason)
            return
        }

        val existingJob = delayedAutoSyncJobs[vaultId]
        if (existingJob?.isActive == true) return

        delayedAutoSyncJobs[vaultId] = viewModelScope.launch {
            delay(remaining)
            syncOrchestrator.requestSync(vaultId, reason)
            delayedAutoSyncJobs.remove(vaultId)
        }
    }

    private fun evaluateNetworkGate(): NetworkGateResult {
        val connectivityManager = getApplication<Application>()
            .getSystemService(ConnectivityManager::class.java) ?: return NetworkGateResult.NETWORK_UNAVAILABLE
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkGateResult.NETWORK_UNAVAILABLE
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return NetworkGateResult.NETWORK_UNAVAILABLE
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkGateResult.NETWORK_UNAVAILABLE
        }
        if (_isSyncOnWifiOnly.value &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        ) {
            return NetworkGateResult.WIFI_REQUIRED
        }
        return NetworkGateResult.ALLOWED
    }

    private suspend fun runSync(vaultId: Long, silent: Boolean): SyncExecutionOutcome {
        val vault = _vaults.value.firstOrNull { it.id == vaultId }
            ?: repository.getAllVaults().firstOrNull { it.id == vaultId }
            ?: return SyncExecutionOutcome.FatalError("Vault 不存在")

        if (!silent) {
            _syncState.value = SyncState.Syncing
        }

        return when (val result = repository.sync(vault.id)) {
            is BitwardenRepository.SyncResult.Success -> {
                if (!silent) {
                    _syncState.value = SyncState.Success(result.syncedCount, result.conflictCount)
                }
                if (!silent) {
                    loadVaultData(vault.id)
                }

                if (!silent) {
                    if (result.conflictCount > 0) {
                        _events.emit(BitwardenEvent.ShowWarning("同步完成，但有 ${result.conflictCount} 个冲突需要处理"))
                    } else {
                        _events.emit(BitwardenEvent.ShowSuccess("同步完成，共 ${result.syncedCount} 条记录"))
                    }
                }
                SyncExecutionOutcome.Success(
                    syncedCount = result.syncedCount,
                    conflictCount = result.conflictCount
                )
            }

            is BitwardenRepository.SyncResult.Error -> {
                if (!silent) {
                    _syncState.value = SyncState.Error(result.message)
                }
                if (!silent) {
                    _events.emit(BitwardenEvent.ShowError("同步失败: ${result.message}"))
                } else {
                    Log.w(TAG, "Silent auto sync failed: ${result.message}")
                }
                classifyError(result.message)
            }

            is BitwardenRepository.SyncResult.EmptyVaultBlocked -> {
                if (!silent) {
                    _syncState.value = SyncState.Error("空 Vault 保护已触发")
                }
                if (!silent) {
                    _events.emit(BitwardenEvent.ShowWarning(
                        "服务器返回空数据，本地有 ${result.localCount} 条记录。" +
                            "请使用 V2 界面处理此情况。"
                    ))
                } else {
                    Log.w(
                        TAG,
                        "Silent auto sync blocked by empty-vault protection: local=${result.localCount}, server=${result.serverCount}"
                    )
                }
                SyncExecutionOutcome.FatalError("空 Vault 保护已触发")
            }
        }
    }

    private fun classifyError(message: String): SyncExecutionOutcome {
        val msg = message.lowercase()
        return when {
            msg.contains("mdk not available") ||
                msg.contains("vault 未解锁") ||
                msg.contains("密钥不可用") -> {
                SyncExecutionOutcome.Blocked(SyncBlockReason.VAULT_LOCKED, message)
            }

            msg.contains("token 刷新失败") ||
                msg.contains("重新登录") ||
                msg.contains("401") ||
                msg.contains("403") ||
                msg.contains("unauthorized") ||
                msg.contains("forbidden") -> {
                SyncExecutionOutcome.Blocked(SyncBlockReason.AUTH_REQUIRED, message)
            }

            msg.contains("timeout") ||
                msg.contains("connect") ||
                msg.contains("network") ||
                msg.contains("ioexception") -> {
                SyncExecutionOutcome.RetryableError(message)
            }

            else -> SyncExecutionOutcome.FatalError(message)
        }
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

    sealed class SendState {
        object Idle : SendState()
        object Loading : SendState()
        object Syncing : SendState()
        object Creating : SendState()
        object Deleting : SendState()
        object Locked : SendState()
        data class Warning(val message: String) : SendState()
        data class Error(val message: String) : SendState()
    }
    
    // ==================== 事件类型 ====================
    
    sealed class BitwardenEvent {
        data class ShowSuccess(val message: String) : BitwardenEvent()
        data class ShowError(val message: String) : BitwardenEvent()
        data class ShowWarning(val message: String) : BitwardenEvent()
        data class ShowTwoFactorDialog(val methods: List<Int>) : BitwardenEvent()
        data class ShowCaptchaDialog(
            val message: String,
            val forTwoFactor: Boolean,
            val siteKey: String? = null
        ) : BitwardenEvent()
        data class NavigateToVault(val vaultId: Long) : BitwardenEvent()
        object NavigateToLogin : BitwardenEvent()
    }
}
