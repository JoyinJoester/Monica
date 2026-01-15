package takagi.ru.monica.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.commonAccountDataStore: DataStore<Preferences> by preferencesDataStore(name = "common_account")

/**
 * 常用账号信息管理器
 * 
 * 存储用户常用的邮箱、手机号等信息，方便在新建条目时快速填入
 */
class CommonAccountPreferences(private val context: Context) {
    
    companion object {
        private val KEY_DEFAULT_EMAIL = stringPreferencesKey("default_email")
        private val KEY_DEFAULT_PHONE = stringPreferencesKey("default_phone")
        private val KEY_DEFAULT_USERNAME = stringPreferencesKey("default_username")
        private val KEY_AUTO_FILL_ENABLED = booleanPreferencesKey("auto_fill_enabled")
    }
    
    /**
     * 常用邮箱
     */
    val defaultEmail: Flow<String> = context.commonAccountDataStore.data.map { preferences ->
        preferences[KEY_DEFAULT_EMAIL] ?: ""
    }
    
    suspend fun setDefaultEmail(email: String) {
        context.commonAccountDataStore.edit { preferences ->
            preferences[KEY_DEFAULT_EMAIL] = email
        }
    }
    
    /**
     * 常用手机号
     */
    val defaultPhone: Flow<String> = context.commonAccountDataStore.data.map { preferences ->
        preferences[KEY_DEFAULT_PHONE] ?: ""
    }
    
    suspend fun setDefaultPhone(phone: String) {
        context.commonAccountDataStore.edit { preferences ->
            preferences[KEY_DEFAULT_PHONE] = phone
        }
    }
    
    /**
     * 常用用户名
     */
    val defaultUsername: Flow<String> = context.commonAccountDataStore.data.map { preferences ->
        preferences[KEY_DEFAULT_USERNAME] ?: ""
    }
    
    suspend fun setDefaultUsername(username: String) {
        context.commonAccountDataStore.edit { preferences ->
            preferences[KEY_DEFAULT_USERNAME] = username
        }
    }
    
    /**
     * 是否在新建条目时自动填入常用账号信息
     */
    val autoFillEnabled: Flow<Boolean> = context.commonAccountDataStore.data.map { preferences ->
        preferences[KEY_AUTO_FILL_ENABLED] ?: false
    }
    
    suspend fun setAutoFillEnabled(enabled: Boolean) {
        context.commonAccountDataStore.edit { preferences ->
            preferences[KEY_AUTO_FILL_ENABLED] = enabled
        }
    }
    
    /**
     * 获取所有常用账号信息
     */
    val commonAccountInfo: Flow<CommonAccountInfo> = context.commonAccountDataStore.data.map { preferences ->
        CommonAccountInfo(
            email = preferences[KEY_DEFAULT_EMAIL] ?: "",
            phone = preferences[KEY_DEFAULT_PHONE] ?: "",
            username = preferences[KEY_DEFAULT_USERNAME] ?: "",
            autoFillEnabled = preferences[KEY_AUTO_FILL_ENABLED] ?: false
        )
    }
}

/**
 * 常用账号信息数据类
 */
data class CommonAccountInfo(
    val email: String = "",
    val phone: String = "",
    val username: String = "",
    val autoFillEnabled: Boolean = false
) {
    fun hasAnyInfo(): Boolean = email.isNotEmpty() || phone.isNotEmpty() || username.isNotEmpty()
}
