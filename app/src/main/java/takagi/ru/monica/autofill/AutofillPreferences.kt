package takagi.ru.monica.autofill

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    }
    
    /**
     * 是否启用自动填充服务
     */
    val isAutofillEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTOFILL_ENABLED] ?: false
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
}
