package takagi.ru.monica.autofill

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

/**
 * 自动填充配置管理
 */
class AutofillPreferences(private val context: Context) {
    
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "autofill_settings")
        
        // 配置键
        private val KEY_AUTOFILL_ENABLED = booleanPreferencesKey("autofill_enabled")
        private val KEY_DOMAIN_MATCH_STRATEGY = stringPreferencesKey("domain_match_strategy")
        private val KEY_FILL_SUGGESTIONS_ENABLED = booleanPreferencesKey("fill_suggestions_enabled")
        private val KEY_MANUAL_SELECTION_ENABLED = booleanPreferencesKey("manual_selection_enabled")
        private val KEY_REQUEST_SAVE_DATA = booleanPreferencesKey("request_save_data")
        private val KEY_AUTO_SAVE_APP_INFO = booleanPreferencesKey("auto_save_app_info")
        private val KEY_AUTO_SAVE_WEBSITE_INFO = booleanPreferencesKey("auto_save_website_info")
        
        // Phase 8: 生物识别快速填充配置
        private val KEY_BIOMETRIC_QUICK_FILL_ENABLED = booleanPreferencesKey("biometric_quick_fill_enabled")
        
        // 新架构：使用增强匹配引擎
        private val KEY_USE_ENHANCED_MATCHING = booleanPreferencesKey("use_enhanced_matching")
        
        // 密码保存功能配置
        private val KEY_AUTO_UPDATE_DUPLICATE_PASSWORDS = booleanPreferencesKey("auto_update_duplicate_passwords")
        private val KEY_SHOW_SAVE_NOTIFICATION = booleanPreferencesKey("show_save_notification")
        private val KEY_SMART_TITLE_GENERATION = booleanPreferencesKey("smart_title_generation")
        
        // 黑名单配置
        private val KEY_BLACKLIST_ENABLED = booleanPreferencesKey("blacklist_enabled")
        private val KEY_BLACKLIST_PACKAGES = stringSetPreferencesKey("blacklist_packages")
        
        // 默认黑名单应用
        val DEFAULT_BLACKLIST_PACKAGES = setOf(
            "com.tencent.mm",           // 微信
            "com.eg.android.AlipayGphone", // 支付宝
            "com.unionpay"              // 云闪付
        )
    }
    
    /**
     * 是否启用自动填充服务
     */
    val isAutofillEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTOFILL_ENABLED] ?: true  // 默认启用
    }
    
    suspend fun setAutofillEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTOFILL_ENABLED] = enabled
        }
    }
    
    /**
     * 域名匹配策略
     */
    val domainMatchStrategy: Flow<DomainMatchStrategy> = context.dataStore.data.map { preferences ->
        val strategyName = preferences[KEY_DOMAIN_MATCH_STRATEGY] ?: DomainMatchStrategy.BASE_DOMAIN.name
        try {
            DomainMatchStrategy.valueOf(strategyName)
        } catch (e: Exception) {
            DomainMatchStrategy.BASE_DOMAIN
        }
    }
    
    suspend fun setDomainMatchStrategy(strategy: DomainMatchStrategy) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DOMAIN_MATCH_STRATEGY] = strategy.name
        }
    }
    
    /**
     * 是否启用填充建议
     */
    val isFillSuggestionsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_FILL_SUGGESTIONS_ENABLED] ?: true
    }
    
    suspend fun setFillSuggestionsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FILL_SUGGESTIONS_ENABLED] = enabled
        }
    }
    
    /**
     * 是否启用手动选择
     */
    val isManualSelectionEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_MANUAL_SELECTION_ENABLED] ?: true
    }
    
    suspend fun setManualSelectionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MANUAL_SELECTION_ENABLED] = enabled
        }
    }
    
    /**
     * 是否请求保存数据 (填写表单时询问是否更新密码库)
     */
    val isRequestSaveDataEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_REQUEST_SAVE_DATA] ?: true
    }
    
    suspend fun setRequestSaveDataEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_REQUEST_SAVE_DATA] = enabled
        }
    }
    
    /**
     * 是否自动保存应用信息
     */
    val isAutoSaveAppInfoEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_SAVE_APP_INFO] ?: true
    }
    
    suspend fun setAutoSaveAppInfoEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_SAVE_APP_INFO] = enabled
        }
    }
    
    /**
     * 是否自动保存网站信息
     */
    val isAutoSaveWebsiteInfoEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_SAVE_WEBSITE_INFO] ?: true
    }
    
    suspend fun setAutoSaveWebsiteInfoEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_SAVE_WEBSITE_INFO] = enabled
        }
    }
    
    /**
     * Phase 8: 是否启用生物识别快速填充
     * 启用后，用户选择密码时需要生物识别验证才能自动填充
     */
    val isBiometricQuickFillEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_BIOMETRIC_QUICK_FILL_ENABLED] ?: false
    }
    
    suspend fun setBiometricQuickFillEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BIOMETRIC_QUICK_FILL_ENABLED] = enabled
        }
    }
    
    /**
     * 是否使用增强匹配引擎（新架构）
     * 默认启用
     */
    val useEnhancedMatching: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_USE_ENHANCED_MATCHING] ?: true
    }
    
    suspend fun setUseEnhancedMatching(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USE_ENHANCED_MATCHING] = enabled
        }
    }
    
    /**
     * 是否自动更新重复密码
     * 启用后，保存已存在的用户名时自动更新密码而不提示用户
     */
    val isAutoUpdateDuplicatePasswordsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_UPDATE_DUPLICATE_PASSWORDS] ?: false
    }
    
    suspend fun setAutoUpdateDuplicatePasswordsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_UPDATE_DUPLICATE_PASSWORDS] = enabled
        }
    }
    
    /**
     * 保存密码时是否显示通知
     */
    val isShowSaveNotificationEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SHOW_SAVE_NOTIFICATION] ?: true
    }
    
    suspend fun setShowSaveNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SHOW_SAVE_NOTIFICATION] = enabled
        }
    }
    
    /**
     * 是否启用智能标题生成
     * 启用后，自动从应用名或域名生成有意义的标题
     */
    val isSmartTitleGenerationEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SMART_TITLE_GENERATION] ?: true
    }
    
    suspend fun setSmartTitleGenerationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SMART_TITLE_GENERATION] = enabled
        }
    }
    
    /**
     * 是否启用黑名单功能
     */
    val isBlacklistEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_BLACKLIST_ENABLED] ?: true  // 默认启用
    }
    
    suspend fun setBlacklistEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BLACKLIST_ENABLED] = enabled
        }
    }
    
    /**
     * 黑名单应用包名集合
     */
    val blacklistPackages: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[KEY_BLACKLIST_PACKAGES] ?: DEFAULT_BLACKLIST_PACKAGES
    }
    
    suspend fun setBlacklistPackages(packages: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BLACKLIST_PACKAGES] = packages
        }
    }
    
    /**
     * 添加应用到黑名单
     */
    suspend fun addToBlacklist(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_BLACKLIST_PACKAGES] ?: DEFAULT_BLACKLIST_PACKAGES
            preferences[KEY_BLACKLIST_PACKAGES] = current + packageName
        }
    }
    
    /**
     * 从黑名单移除应用
     */
    suspend fun removeFromBlacklist(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_BLACKLIST_PACKAGES] ?: DEFAULT_BLACKLIST_PACKAGES
            preferences[KEY_BLACKLIST_PACKAGES] = current - packageName
        }
    }
    
    /**
     * 检查应用是否在黑名单中
     */
    suspend fun isInBlacklist(packageName: String): Boolean {
        val enabled = isBlacklistEnabled.first()
        if (!enabled) return false
        
        val packages = blacklistPackages.first()
        return packages.contains(packageName)
    }
}
