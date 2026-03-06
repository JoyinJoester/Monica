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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

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
        private val KEY_TEMPLATES_JSON = stringPreferencesKey("templates_json")
        private val KEY_LEGACY_DEFAULTS_MIGRATED = booleanPreferencesKey("legacy_defaults_migrated")
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

    /**
     * 常用账号模板列表（支持多个“类型/标题/内容”）
     */
    val templatesFlow: Flow<List<CommonAccountTemplate>> = context.commonAccountDataStore.data.map { preferences ->
        decodeTemplates(preferences[KEY_TEMPLATES_JSON])
    }

    suspend fun addTemplate(type: String, title: String, content: String): CommonAccountTemplate {
        val template = CommonAccountTemplate(
            id = UUID.randomUUID().toString(),
            type = type.trim(),
            title = title.trim(),
            content = content.trim()
        )
        context.commonAccountDataStore.edit { preferences ->
            val current = decodeTemplates(preferences[KEY_TEMPLATES_JSON])
            preferences[KEY_TEMPLATES_JSON] = encodeTemplates(current + template)
        }
        return template
    }

    suspend fun updateTemplate(template: CommonAccountTemplate) {
        val normalizedTemplate = template.normalized()
        context.commonAccountDataStore.edit { preferences ->
            val current = decodeTemplates(preferences[KEY_TEMPLATES_JSON])
            val updated = current.map { existing ->
                if (existing.id == normalizedTemplate.id) normalizedTemplate else existing
            }
            preferences[KEY_TEMPLATES_JSON] = encodeTemplates(updated)
        }
    }

    suspend fun upsertTemplate(template: CommonAccountTemplate) {
        val normalizedTemplate = template.normalized()
        context.commonAccountDataStore.edit { preferences ->
            val current = decodeTemplates(preferences[KEY_TEMPLATES_JSON])
            val index = current.indexOfFirst { it.id == normalizedTemplate.id }
            val updated = if (index >= 0) {
                current.toMutableList().apply { this[index] = normalizedTemplate }
            } else {
                current + normalizedTemplate
            }
            preferences[KEY_TEMPLATES_JSON] = encodeTemplates(updated)
        }
    }

    suspend fun deleteTemplate(templateId: String) {
        context.commonAccountDataStore.edit { preferences ->
            val current = decodeTemplates(preferences[KEY_TEMPLATES_JSON])
            preferences[KEY_TEMPLATES_JSON] = encodeTemplates(
                current.filterNot { it.id == templateId }
            )
        }
    }

    suspend fun setTemplates(templates: List<CommonAccountTemplate>) {
        context.commonAccountDataStore.edit { preferences ->
            preferences[KEY_TEMPLATES_JSON] = encodeTemplates(templates)
        }
    }

    /**
     * 将旧版单值默认项迁移为模板，迁移后清空旧字段，避免在新页面重复显示。
     */
    suspend fun migrateLegacyDefaultsToTemplatesIfNeeded(
        accountType: String,
        emailType: String,
        phoneType: String,
        accountTitle: String,
        emailTitle: String,
        phoneTitle: String
    ) {
        context.commonAccountDataStore.edit { preferences ->
            if (preferences[KEY_LEGACY_DEFAULTS_MIGRATED] == true) {
                return@edit
            }

            val username = (preferences[KEY_DEFAULT_USERNAME] ?: "").trim()
            val email = (preferences[KEY_DEFAULT_EMAIL] ?: "").trim()
            val phone = (preferences[KEY_DEFAULT_PHONE] ?: "").trim()
            val currentTemplates = decodeTemplates(preferences[KEY_TEMPLATES_JSON]).toMutableList()

            fun hasTemplate(type: String, content: String): Boolean {
                val normalizedType = type.trim().lowercase()
                val normalizedContent = content.trim().lowercase()
                return currentTemplates.any { template ->
                    template.type.trim().lowercase() == normalizedType &&
                        template.content.trim().lowercase() == normalizedContent
                }
            }

            if (username.isNotEmpty() && !hasTemplate(accountType, username)) {
                currentTemplates += CommonAccountTemplate(
                    id = UUID.randomUUID().toString(),
                    type = accountType.trim(),
                    title = accountTitle.trim(),
                    content = username
                )
            }

            if (email.isNotEmpty() && !hasTemplate(emailType, email)) {
                currentTemplates += CommonAccountTemplate(
                    id = UUID.randomUUID().toString(),
                    type = emailType.trim(),
                    title = emailTitle.trim(),
                    content = email
                )
            }

            if (phone.isNotEmpty() && !hasTemplate(phoneType, phone)) {
                currentTemplates += CommonAccountTemplate(
                    id = UUID.randomUUID().toString(),
                    type = phoneType.trim(),
                    title = phoneTitle.trim(),
                    content = phone
                )
            }

            preferences[KEY_TEMPLATES_JSON] = encodeTemplates(currentTemplates)
            preferences[KEY_DEFAULT_USERNAME] = ""
            preferences[KEY_DEFAULT_EMAIL] = ""
            preferences[KEY_DEFAULT_PHONE] = ""
            preferences[KEY_AUTO_FILL_ENABLED] = false
            preferences[KEY_LEGACY_DEFAULTS_MIGRATED] = true
        }
    }

    private fun decodeTemplates(raw: String?): List<CommonAccountTemplate> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    if (id.isEmpty()) continue
                    add(
                        CommonAccountTemplate(
                            id = id,
                            type = item.optString("type").trim(),
                            title = item.optString("title").trim(),
                            content = item.optString("content").trim()
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeTemplates(templates: List<CommonAccountTemplate>): String {
        val array = JSONArray()
        templates
            .map { it.normalized() }
            .filter { it.id.isNotEmpty() }
            .forEach { template ->
                array.put(
                    JSONObject().apply {
                        put("id", template.id)
                        put("type", template.type)
                        put("title", template.title)
                        put("content", template.content)
                    }
                )
            }
        return array.toString()
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

data class CommonAccountTemplate(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val content: String = ""
) {
    fun normalized(): CommonAccountTemplate {
        return copy(
            id = id.trim(),
            type = type.trim(),
            title = title.trim(),
            content = content.trim()
        )
    }
}
