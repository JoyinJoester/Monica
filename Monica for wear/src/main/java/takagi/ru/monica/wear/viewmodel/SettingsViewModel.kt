package takagi.ru.monica.wear.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import takagi.ru.monica.wear.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import takagi.ru.monica.wear.data.ItemType
import takagi.ru.monica.wear.data.PasswordDatabase
import takagi.ru.monica.wear.data.SecureItem
import takagi.ru.monica.wear.data.model.OtpType
import takagi.ru.monica.wear.data.model.TotpData
import takagi.ru.monica.wear.utils.WearWebDavHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

/**
 * 同步状态
 */
sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    object PasswordRequired : SyncState()
    data class Success(val message: String = "同步成功") : SyncState()
    data class Error(val message: String) : SyncState()
}

/**
 * 配色方案
 */
enum class ColorScheme(val displayNameResId: Int, val descriptionResId: Int) {
    OCEAN_BLUE(R.string.color_scheme_ocean_blue, R.string.color_scheme_ocean_blue_desc),
    SUNSET_ORANGE(R.string.color_scheme_sunset_orange, R.string.color_scheme_sunset_orange_desc),
    FOREST_GREEN(R.string.color_scheme_forest_green, R.string.color_scheme_forest_green_desc),
    TECH_PURPLE(R.string.color_scheme_tech_purple, R.string.color_scheme_tech_purple_desc),
    BLACK_MAMBA(R.string.color_scheme_black_mamba, R.string.color_scheme_black_mamba_desc),
    GREY_STYLE(R.string.color_scheme_grey_style, R.string.color_scheme_grey_style_desc)
}

/**
 * 应用语言
 */
enum class AppLanguage(val displayNameResId: Int, val localeTag: String?) {
    SYSTEM_DEFAULT(R.string.language_system_default, null),
    CHINESE(R.string.language_chinese, "zh"),
    ENGLISH(R.string.language_english, "en"),
    JAPANESE(R.string.language_japanese, "ja")
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
    private val prefs: SharedPreferences = application.getSharedPreferences("monica_wear_prefs", Context.MODE_PRIVATE)
    private val database = PasswordDatabase.getDatabase(application)
    private val json = Json { ignoreUnknownKeys = true }
    
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
    
    // 当前配色方案
    private val _currentColorScheme = MutableStateFlow(ColorScheme.OCEAN_BLUE)
    val currentColorScheme: StateFlow<ColorScheme> = _currentColorScheme.asStateFlow()
    
    // OLED 绝对黑色开关
    private val _useOledBlack = MutableStateFlow(true)
    val useOledBlack: StateFlow<Boolean> = _useOledBlack.asStateFlow()
    
    // 当前语言
    private val _currentLanguage = MutableStateFlow(AppLanguage.SYSTEM_DEFAULT)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    init {
        // 加载配色方案设置
        loadColorScheme()
        
        // 加载语言设置
        loadLanguage()
        
        // 加载 OLED 黑色设置
        loadOledBlackSetting()

        // 检查WebDAV配置状态
        _isWebDavConfigured.value = webDavHelper.isConfigured()
        
        // 加载最后同步时间
        loadLastSyncTime()
        
        Log.d(TAG, "SettingsViewModel initialized, WebDAV configured: ${_isWebDavConfigured.value}")
    }
    
    private fun loadColorScheme() {
        val schemeName = prefs.getString("color_scheme", null)
        
        // 迁移旧的主题设置
        if (schemeName == null) {
            val oldTheme = prefs.getString("app_theme", "Classic")
            _currentColorScheme.value = when (oldTheme) {
                "Classic" -> ColorScheme.OCEAN_BLUE
                "Modern" -> ColorScheme.TECH_PURPLE
                else -> ColorScheme.OCEAN_BLUE
            }
            // 保存迁移后的设置
            prefs.edit().putString("color_scheme", _currentColorScheme.value.name).apply()
        } else {
            _currentColorScheme.value = try {
                ColorScheme.valueOf(schemeName)
            } catch (e: Exception) {
                ColorScheme.OCEAN_BLUE
            }
        }
    }
    
    fun setColorScheme(scheme: ColorScheme) {
        _currentColorScheme.value = scheme
        prefs.edit().putString("color_scheme", scheme.name).apply()
    }
    
    private fun loadOledBlackSetting() {
        _useOledBlack.value = prefs.getBoolean("use_oled_black", true)
    }
    
    fun setOledBlack(enabled: Boolean) {
        _useOledBlack.value = enabled
        prefs.edit().putBoolean("use_oled_black", enabled).apply()
    }
    
    private fun loadLanguage() {
        val languageName = prefs.getString("app_language", null)
        _currentLanguage.value = if (languageName != null) {
            try {
                AppLanguage.valueOf(languageName)
            } catch (e: Exception) {
                AppLanguage.SYSTEM_DEFAULT
            }
        } else {
            AppLanguage.SYSTEM_DEFAULT
        }
    }
    
    fun setLanguage(language: AppLanguage) {
        _currentLanguage.value = language
        prefs.edit().putString("app_language", language.name).apply()
    }
    
    fun getCurrentLanguageDisplayName(): Int {
        return _currentLanguage.value.displayNameResId
    }

    /**
     * 配置WebDAV连接
     */
    fun configureWebDav(
        serverUrl: String,
        username: String,
        password: String,
        enableEncryption: Boolean = false,
        encryptionPassword: String = "",
        callback: (success: Boolean, error: String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Configuring WebDAV: $serverUrl")
                
                // 配置连接
                webDavHelper.configure(serverUrl, username, password)
                webDavHelper.configureEncryption(enableEncryption, encryptionPassword)
                
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
                    _syncState.value = SyncState.Error(getApplication<Application>().getString(R.string.webdav_test_failed))
                    Log.e(TAG, "WebDAV connection test failed")
                    callback(false, getApplication<Application>().getString(R.string.webdav_test_failed))
                }
            } catch (e: Exception) {
                webDavHelper.clearConfig()
                _isWebDavConfigured.value = false
                _syncState.value = SyncState.Error(getApplication<Application>().getString(R.string.webdav_config_failed) + ": ${e.message}")
                Log.e(TAG, "Error configuring WebDAV", e)
                callback(false, getApplication<Application>().getString(R.string.webdav_config_failed) + ": ${e.message}")
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
    fun syncNow(tempPassword: String? = null) {
        if (!webDavHelper.isConfigured()) {
            _syncState.value = SyncState.Error(getApplication<Application>().getString(R.string.sync_error_not_configured))
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
                
                // 使用新的 sync() 方法，包含下载和上传
                val result = webDavHelper.sync(tempPassword)
                
                if (result.isSuccess) {
                    val message = result.getOrDefault(getApplication<Application>().getString(R.string.sync_status_success))
                    _syncState.value = SyncState.Success(message)
                    loadLastSyncTime()
                    Log.d(TAG, "Manual sync completed: $message")
                    
                    // 3秒后恢复为Idle状态
                    kotlinx.coroutines.delay(3000)
                    if (_syncState.value is SyncState.Success) {
                        _syncState.value = SyncState.Idle
                    }
                } else {
                    val error = result.exceptionOrNull()
                    val errorMessage = error?.message ?: getApplication<Application>().getString(R.string.sync_error_network)
                    
                    if (errorMessage == "ENCRYPTION_PASSWORD_REQUIRED") {
                        _syncState.value = SyncState.PasswordRequired
                    } else {
                        _syncState.value = SyncState.Error(errorMessage)
                        Log.e(TAG, "Manual sync failed: ${error?.message}", error)
                    }
                }
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(getApplication<Application>().getString(R.string.sync_error_with_message, e.message ?: ""))
                Log.e(TAG, "Error during manual sync", e)
            }
        }
    }

    /**
     * 重置同步状态
     */
    fun resetSyncState() {
        if (_syncState.value is SyncState.PasswordRequired) {
            _syncState.value = SyncState.Idle
        }
    }
    
    /**
     * 检查是否需要自动同步
     * 自动同步只执行下载（恢复），不执行上传（备份）
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
        
        Log.d(TAG, "Auto sync conditions met, starting sync (download only)")
        
        viewModelScope.launch {
            try {
                _syncState.value = SyncState.Syncing
                
                // 自动同步只下载
                val result = webDavHelper.downloadAndImportLatestBackup()
                
                if (result.isSuccess) {
                    val message = result.getOrDefault(getApplication<Application>().getString(R.string.sync_status_success))
                    _syncState.value = SyncState.Success(message)
                    loadLastSyncTime()
                    Log.d(TAG, "Auto sync completed: $message")
                    
                    // 3秒后恢复为Idle状态
                    kotlinx.coroutines.delay(3000)
                    if (_syncState.value is SyncState.Success) {
                        _syncState.value = SyncState.Idle
                    }
                } else {
                    val error = result.exceptionOrNull()
                    val errorMessage = error?.message ?: getApplication<Application>().getString(R.string.sync_error_network)
                    _syncState.value = SyncState.Error(errorMessage)
                    Log.e(TAG, "Auto sync failed: ${error?.message}", error)
                }
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(getApplication<Application>().getString(R.string.sync_error_with_message, e.message ?: ""))
                Log.e(TAG, "Error during auto sync", e)
            }
        }
    }

    /**
     * 仅备份（上传）
     */
    fun backupNow(callback: (Boolean, String) -> Unit) {
        if (!webDavHelper.isConfigured()) {
            callback(false, getApplication<Application>().getString(R.string.sync_error_not_configured))
            return
        }
        
        viewModelScope.launch {
            try {
                _syncState.value = SyncState.Syncing
                val result = webDavHelper.exportAndUploadBackup()
                
                if (result.isSuccess) {
                    val message = result.getOrDefault("备份成功")
                    _syncState.value = SyncState.Success(message)
                    callback(true, message)
                    
                    kotlinx.coroutines.delay(3000)
                    if (_syncState.value is SyncState.Success) {
                        _syncState.value = SyncState.Idle
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "备份失败"
                    _syncState.value = SyncState.Error(error)
                    callback(false, error)
                }
            } catch (e: Exception) {
                val error = e.message ?: "未知错误"
                _syncState.value = SyncState.Error(error)
                callback(false, error)
            }
        }
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
                database.secureItemDao().deleteAll()
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

    /**
     * 添加新的 TOTP 验证器
     */
    fun addTotpItem(
        secret: String,
        issuer: String,
        accountName: String,
        callback: (success: Boolean, error: String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // 验证密钥格式
                if (secret.isBlank()) {
                    callback(false, getApplication<Application>().getString(R.string.dialog_totp_secret_error))
                    return@launch
                }

                // 创建 TOTP 数据
                val totpData = TotpData(
                    secret = secret.trim().replace(" ", "").uppercase(),
                    issuer = issuer.trim(),
                    accountName = accountName.trim(),
                    period = 30,
                    digits = 6,
                    algorithm = "SHA1",
                    otpType = OtpType.TOTP
                )

                // 创建 SecureItem
                val item = SecureItem(
                    id = 0, // Room 自动生成
                    title = if (issuer.isNotBlank()) issuer else accountName,
                    itemType = ItemType.TOTP,
                    itemData = json.encodeToString(TotpData.serializer(), totpData),
                    notes = "",
                    isFavorite = false,
                    sortOrder = 0,
                    createdAt = Date(System.currentTimeMillis()),
                    updatedAt = Date(System.currentTimeMillis())
                )

                // 插入数据库
                database.secureItemDao().insertItem(item)
                Log.d(TAG, "TOTP item added: $issuer")
                callback(true, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add TOTP item: ${e.message}")
                callback(false, getApplication<Application>().getString(R.string.totp_add_failed) + ": ${e.message}")
            }
        }
    }

    /**
     * 获取当前 WebDAV 配置
     */
    fun getWebDavConfig(): WebDavConfig {
        return WebDavConfig(
            serverUrl = webDavHelper.getServerUrl(),
            username = webDavHelper.getUsername(),
            password = webDavHelper.getPassword(),
            enableEncryption = webDavHelper.isEncryptionEnabled(),
            encryptionPassword = webDavHelper.getEncryptionPassword()
        )
    }
}

data class WebDavConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
    val enableEncryption: Boolean,
    val encryptionPassword: String
)
