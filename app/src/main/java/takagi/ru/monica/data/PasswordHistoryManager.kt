package takagi.ru.monica.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.passwordHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "password_history")

/**
 * 密码生成历史管理器
 * 使用 DataStore 存储密码生成历史记录
 */
class PasswordHistoryManager(private val context: Context) {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private val HISTORY_KEY = stringPreferencesKey("password_generation_history")
        private const val MAX_HISTORY_SIZE = 50 // 最多保存50条历史记录
    }
    
    /**
     * 获取所有历史记录
     */
    val historyFlow: Flow<List<PasswordGenerationHistory>> = context.passwordHistoryDataStore.data
        .map { preferences ->
            val historyJson = preferences[HISTORY_KEY] ?: "[]"
            try {
                json.decodeFromString<List<PasswordGenerationHistory>>(historyJson)
            } catch (e: Exception) {
                emptyList()
            }
        }
    
    /**
     * 添加新的历史记录
     */
    suspend fun addHistory(
        password: String, 
        packageName: String = "", 
        domain: String = "",
        username: String = ""
    ) {
        context.passwordHistoryDataStore.edit { preferences ->
            val currentHistoryJson = preferences[HISTORY_KEY] ?: "[]"
            val currentHistory = try {
                json.decodeFromString<List<PasswordGenerationHistory>>(currentHistoryJson)
            } catch (e: Exception) {
                emptyList()
            }
            
            // 创建新记录
            val newRecord = PasswordGenerationHistory(
                password = password,
                timestamp = System.currentTimeMillis(),
                packageName = packageName,
                domain = domain,
                username = username
            )
            
            // 添加到列表头部，并限制数量
            val updatedHistory = (listOf(newRecord) + currentHistory).take(MAX_HISTORY_SIZE)
            
            // 保存
            preferences[HISTORY_KEY] = json.encodeToString(updatedHistory)
        }
    }
    
    /**
     * 清空所有历史记录
     */
    suspend fun clearHistory() {
        context.passwordHistoryDataStore.edit { preferences ->
            preferences[HISTORY_KEY] = "[]"
        }
    }
    
    /**
     * 删除指定的历史记录
     */
    suspend fun deleteHistory(timestamp: Long) {
        context.passwordHistoryDataStore.edit { preferences ->
            val currentHistoryJson = preferences[HISTORY_KEY] ?: "[]"
            val currentHistory = try {
                json.decodeFromString<List<PasswordGenerationHistory>>(currentHistoryJson)
            } catch (e: Exception) {
                emptyList()
            }
            
            val updatedHistory = currentHistory.filter { it.timestamp != timestamp }
            preferences[HISTORY_KEY] = json.encodeToString(updatedHistory)
        }
    }
}
