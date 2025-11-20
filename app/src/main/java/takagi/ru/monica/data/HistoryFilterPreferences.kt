package takagi.ru.monica.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.historyFilterDataStore: DataStore<Preferences> by preferencesDataStore(name = "history_filter")

/**
 * 历史记录过滤偏好管理器
 */
class HistoryFilterPreferences(private val context: Context) {
    
    companion object {
        private val SHOW_SYMBOL = booleanPreferencesKey("show_symbol")
        private val SHOW_PASSWORD = booleanPreferencesKey("show_password")
        private val SHOW_PASSPHRASE = booleanPreferencesKey("show_passphrase")
        private val SHOW_PIN = booleanPreferencesKey("show_pin")
        private val SHOW_AUTOFILL = booleanPreferencesKey("show_autofill")
    }
    
    /**
     * 获取过滤设置
     */
    val filterSettings: Flow<HistoryFilterSettings> = context.historyFilterDataStore.data
        .map { preferences ->
            HistoryFilterSettings(
                showSymbol = preferences[SHOW_SYMBOL] ?: true,
                showPassword = preferences[SHOW_PASSWORD] ?: true,
                showPassphrase = preferences[SHOW_PASSPHRASE] ?: true,
                showPin = preferences[SHOW_PIN] ?: true,
                showAutofill = preferences[SHOW_AUTOFILL] ?: true
            )
        }
    
    /**
     * 更新过滤设置
     */
    suspend fun updateFilterSettings(settings: HistoryFilterSettings) {
        context.historyFilterDataStore.edit { preferences ->
            preferences[SHOW_SYMBOL] = settings.showSymbol
            preferences[SHOW_PASSWORD] = settings.showPassword
            preferences[SHOW_PASSPHRASE] = settings.showPassphrase
            preferences[SHOW_PIN] = settings.showPin
            preferences[SHOW_AUTOFILL] = settings.showAutofill
        }
    }
}

/**
 * 历史记录过滤设置
 */
data class HistoryFilterSettings(
    val showSymbol: Boolean = true,
    val showPassword: Boolean = true,
    val showPassphrase: Boolean = true,
    val showPin: Boolean = true,
    val showAutofill: Boolean = true
) {
    /**
     * 检查是否应该显示指定类型的历史记录
     */
    fun shouldShow(type: String): Boolean {
        return when (type) {
            "SYMBOL" -> showSymbol
            "PASSWORD" -> showPassword
            "PASSPHRASE" -> showPassphrase
            "PIN" -> showPin
            "AUTOFILL" -> showAutofill
            else -> true
        }
    }
}
