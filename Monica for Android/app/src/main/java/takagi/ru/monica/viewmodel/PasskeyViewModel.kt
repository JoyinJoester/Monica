package takagi.ru.monica.viewmodel

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.repository.PasskeyRepository

/**
 * Passkey ViewModel
 * 
 * 管理 Passkey 数据和 UI 状态
 */
class PasskeyViewModel(
    private val repository: PasskeyRepository
) : ViewModel() {
    
    // ==================== UI 状态 ====================
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // ==================== 数据流 ====================
    
    /**
     * 所有 Passkey 列表
     */
    val allPasskeys: StateFlow<List<PasskeyEntry>> = repository.getAllPasskeys()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * 搜索结果（根据搜索词过滤）
     */
    val filteredPasskeys: StateFlow<List<PasskeyEntry>> = combine(
        allPasskeys,
        searchQuery
    ) { passkeys, query ->
        if (query.isBlank()) {
            passkeys
        } else {
            passkeys.filter { passkey ->
                passkey.rpId.contains(query, ignoreCase = true) ||
                passkey.rpName.contains(query, ignoreCase = true) ||
                passkey.userName.contains(query, ignoreCase = true) ||
                passkey.userDisplayName.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    /**
     * 按域名分组的 Passkey
     */
    val groupedPasskeys: StateFlow<Map<String, List<PasskeyEntry>>> = filteredPasskeys
        .map { passkeys ->
            passkeys.groupBy { it.rpId }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )
    
    /**
     * Passkey 总数
     */
    val passkeyCount: StateFlow<Int> = repository.getPasskeyCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
    
    // ==================== 设备兼容性检查 ====================
    
    /**
     * 检查设备是否支持完整 Passkey 功能
     * Android 14+ (API 34+) 支持 Credential Provider API
     */
    val isPasskeyFullySupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    
    /**
     * 获取 Android 版本信息
     */
    val androidVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    
    /**
     * 获取不支持原因（低版本设备）
     */
    val unsupportedReason: String? = if (!isPasskeyFullySupported) {
        "Passkey 完整功能需要 Android 14 或更高版本。当前设备: $androidVersion"
    } else null
    
    // ==================== 操作方法 ====================
    
    /**
     * 更新搜索词
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * 获取指定 Passkey
     */
    suspend fun getPasskeyById(credentialId: String): PasskeyEntry? {
        return repository.getPasskeyById(credentialId)
    }
    
    /**
     * 根据域名获取 Passkeys
     */
    fun getPasskeysByRpId(rpId: String): Flow<List<PasskeyEntry>> {
        return repository.getPasskeysByRpId(rpId)
    }

    /**
     * 获取绑定到指定密码的 Passkeys
     */
    fun getPasskeysByBoundPasswordId(passwordId: Long): Flow<List<PasskeyEntry>> {
        return repository.getPasskeysByBoundPasswordId(passwordId)
    }
    
    /**
     * 保存 Passkey
     */
    fun savePasskey(passkey: PasskeyEntry) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.savePasskey(passkey)
            } catch (e: Exception) {
                _errorMessage.value = "保存 Passkey 失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 更新 Passkey
     */
    fun updatePasskey(passkey: PasskeyEntry) {
        viewModelScope.launch {
            try {
                repository.updatePasskey(passkey)
            } catch (e: Exception) {
                _errorMessage.value = "更新 Passkey 失败: ${e.message}"
            }
        }
    }

    /**
     * 更新绑定密码
     */
    fun updateBoundPassword(credentialId: String, passwordId: Long?) {
        viewModelScope.launch {
            try {
                repository.updateBoundPasswordId(credentialId, passwordId)
            } catch (e: Exception) {
                _errorMessage.value = "更新绑定失败: ${e.message}"
            }
        }
    }
    
    /**
     * 更新使用记录
     */
    fun updateUsage(credentialId: String, signCount: Long) {
        viewModelScope.launch {
            try {
                repository.updateUsage(credentialId, signCount)
            } catch (e: Exception) {
                _errorMessage.value = "更新使用记录失败: ${e.message}"
            }
        }
    }
    
    /**
     * 删除 Passkey
     * 注：PasskeyRepository 会自动处理 Android Keystore 私钥清理
     */
    fun deletePasskey(passkey: PasskeyEntry) {
        viewModelScope.launch {
            try {
                repository.deletePasskey(passkey)
            } catch (e: Exception) {
                _errorMessage.value = "删除 Passkey 失败: ${e.message}"
            }
        }
    }
    
    /**
     * 根据凭据 ID 删除 Passkey
     * 注：PasskeyRepository 会自动处理 Android Keystore 私钥清理
     */
    fun deletePasskeyById(credentialId: String) {
        viewModelScope.launch {
            try {
                repository.deletePasskeyById(credentialId)
            } catch (e: Exception) {
                _errorMessage.value = "删除 Passkey 失败: ${e.message}"
            }
        }
    }
    
    // ==================== Credential Provider 相关 ====================
    
    /**
     * 获取可发现的 Passkeys（用于 Credential Provider 选择界面）
     */
    suspend fun getDiscoverablePasskeys(): List<PasskeyEntry> {
        return repository.getDiscoverablePasskeys()
    }
    
    /**
     * 获取指定域名的可发现 Passkeys
     */
    suspend fun getDiscoverablePasskeysByRpId(rpId: String): List<PasskeyEntry> {
        return repository.getDiscoverablePasskeysByRpId(rpId)
    }
}
