package takagi.ru.monica.wear.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import takagi.ru.monica.wear.utils.WearWebDavHelper
import java.text.SimpleDateFormat
import java.util.*

/**
 * 同步状态
 */
sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    object Success : SyncState()
    data class Error(val message: String) : SyncState()
}

/**
 * SettingsViewModel - Material 3 支持
 * 管理应用设置、WebDAV配置和同步
 */
class SettingsViewModel(
    application: Application
) : AndroidViewModel(application) {
    
    private val webDavHelper = WearWebDavHelper(application)
    private val TAG = "SettingsViewModel"
    
    // 同步状态
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    // 最后同步时间
    private val _lastSyncTime = MutableStateFlow("")
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()
    
    // WebDAV是否已配置
    private val _isWebDavConfigured = MutableStateFlow(false)
    val isWebDavConfigured: StateFlow<Boolean> = _isWebDavConfigured.asStateFlow()
    
    // 生物识别开关
    private val _biometricEnabled = MutableStateFlow(false)
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()
    
    init {
        // 检查WebDAV配置状态
        _isWebDavConfigured.value = webDavHelper.isConfigured()
        
        // 加载最后同步时间
        loadLastSyncTime()
        
        Log.d(TAG, "SettingsViewModel initialized, WebDAV configured: ${_isWebDavConfigured.value}")
    }
    
    /**
     * 配置WebDAV连接
     */
    fun configureWebDav(
        serverUrl: String,
        username: String,
        password: String,
        callback: (success: Boolean, error: String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Configuring WebDAV: $serverUrl")
                
                // 配置连接
                webDavHelper.configure(serverUrl, username, password)
                
                // 测试连接
                _syncState.value = SyncState.Syncing
                val testResult = webDavHelper.testConnection()
                
                if (testResult.isSuccess) {
                    _isWebDavConfigured.value = true
                    _syncState.value = SyncState.Idle
                    Log.d(TAG, "WebDAV configured successfully")
                    callback(true, null)
                } else {
                    // 连接失败，清除配置
                    webDavHelper.clearConfig()
                    _isWebDavConfigured.value = false
                    _syncState.value = SyncState.Error("连接测试失败")
                    Log.e(TAG, "WebDAV connection test failed")
                    callback(false, "连接测试失败，请检查服务器地址和凭据")
                }
            } catch (e: Exception) {
                webDavHelper.clearConfig()
                _isWebDavConfigured.value = false
                _syncState.value = SyncState.Error("配置失败: ${e.message}")
                Log.e(TAG, "Error configuring WebDAV", e)
                callback(false, "配置失败: ${e.message}")
            }
        }
    }
    
    /**
     * 配置加密密码
     */
    fun configureEncryptionPassword(password: String) {
        Log.d(TAG, "Configuring encryption password")
        webDavHelper.configureEncryption(true, password)
    }
    
    /**
     * 立即同步
     */
    fun syncNow() {
        if (!webDavHelper.isConfigured()) {
            _syncState.value = SyncState.Error("WebDAV 未配置")
            Log.w(TAG, "Sync requested but WebDAV not configured")
            return
        }
        
        if (_syncState.value == SyncState.Syncing) {
            Log.w(TAG, "Sync already in progress")
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting manual sync")
                _syncState.value = SyncState.Syncing
                
                val result = webDavHelper.downloadAndImportLatestBackup()
                
                if (result.isSuccess) {
                    val importedCount = result.getOrDefault(0)
                    _syncState.value = SyncState.Success
                    loadLastSyncTime()
                    Log.d(TAG, "Manual sync completed successfully, imported $importedCount items")
                    
                    // 3秒后恢复为Idle状态
                    kotlinx.coroutines.delay(3000)
                    if (_syncState.value == SyncState.Success) {
                        _syncState.value = SyncState.Idle
                    }
                } else {
                    val error = result.exceptionOrNull()
                    val errorMessage = error?.message ?: "同步失败，请检查网络连接"
                    _syncState.value = SyncState.Error(errorMessage)
                    Log.e(TAG, "Manual sync failed: ${error?.message}", error)
                }
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("同步出错: ${e.message}")
                Log.e(TAG, "Error during manual sync", e)
            }
        }
    }
    
    /**
     * 检查是否需要自动同步
     */
    fun checkAutoSync() {
        if (!webDavHelper.isConfigured()) {
            Log.d(TAG, "Auto sync check skipped: WebDAV not configured")
            return
        }
        
        if (!webDavHelper.shouldAutoSync()) {
            Log.d(TAG, "Auto sync not needed at this time")
            return
        }
        
        Log.d(TAG, "Auto sync conditions met, starting sync")
        syncNow()
    }
    
    /**
     * 加载最后同步时间
     */
    private fun loadLastSyncTime() {
        val lastSync = webDavHelper.getLastSyncTime()
        if (lastSync > 0) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            _lastSyncTime.value = dateFormat.format(Date(lastSync))
            Log.d(TAG, "Last sync time: ${_lastSyncTime.value}")
        } else {
            _lastSyncTime.value = ""
        }
    }
    
    /**
     * 切换生物识别
     */
    fun toggleBiometric() {
        viewModelScope.launch {
            try {
                val newValue = !_biometricEnabled.value
                _biometricEnabled.value = newValue
                Log.d(TAG, "Biometric enabled: $newValue")
                // TODO: 保存到 DataStore
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling biometric", e)
            }
        }
    }
    
    /**
     * 清除所有数据
     */
    fun clearAllData() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Clearing all data")
                // TODO: 实现清除所有数据功能
                // 需要调用 Repository 删除所有 TOTP 数据
                webDavHelper.clearConfig()
                _isWebDavConfigured.value = false
                _lastSyncTime.value = ""
                _syncState.value = SyncState.Idle
                Log.d(TAG, "All data cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing data", e)
            }
        }
    }
}
