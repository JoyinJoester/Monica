package takagi.ru.monica.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.BottomNavContentTab
import takagi.ru.monica.data.BottomNavVisibility
import takagi.ru.monica.data.ColorScheme
import takagi.ru.monica.data.Language
import takagi.ru.monica.data.ThemeMode

private val Context.dataStore by preferencesDataStore("settings")

/**
 * Settings manager using DataStore
 */
class SettingsManager(private val context: Context) {
    
    private val dataStore: DataStore<Preferences> = context.dataStore
    
    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val COLOR_SCHEME_KEY = stringPreferencesKey("color_scheme")
        private val CUSTOM_PRIMARY_COLOR_KEY = longPreferencesKey("custom_primary_color")
        private val CUSTOM_SECONDARY_COLOR_KEY = longPreferencesKey("custom_secondary_color")
        private val CUSTOM_TERTIARY_COLOR_KEY = longPreferencesKey("custom_tertiary_color")
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
        private val AUTO_LOCK_MINUTES_KEY = intPreferencesKey("auto_lock_minutes")
        private val SCREENSHOT_PROTECTION_KEY = booleanPreferencesKey("screenshot_protection_enabled")
        private val SHOW_PASSWORDS_TAB_KEY = booleanPreferencesKey("show_passwords_tab")
        private val SHOW_AUTHENTICATOR_TAB_KEY = booleanPreferencesKey("show_authenticator_tab")
        private val SHOW_DOCUMENTS_TAB_KEY = booleanPreferencesKey("show_documents_tab")
        private val SHOW_BANK_CARDS_TAB_KEY = booleanPreferencesKey("show_bank_cards_tab")
        private val SHOW_NOTES_TAB_KEY = booleanPreferencesKey("show_notes_tab")
        private val SHOW_LEDGER_TAB_KEY = booleanPreferencesKey("show_ledger_tab")
        private val SHOW_GENERATOR_TAB_KEY = booleanPreferencesKey("show_generator_tab")  // 添加生成器标签键
        private val DYNAMIC_COLOR_ENABLED_KEY = booleanPreferencesKey("dynamic_color_enabled")
        private val BOTTOM_SHEET_ENABLED_KEY = booleanPreferencesKey("bottom_sheet_enabled")
        private val BOTTOM_NAV_ORDER_KEY = stringPreferencesKey("bottom_nav_order")
    }
    
    val settingsFlow: Flow<AppSettings> = dataStore.data.map { preferences ->
        val storedOrder = preferences[BOTTOM_NAV_ORDER_KEY]
        val parsedOrder = storedOrder
            ?.split(",")
            ?.mapNotNull { value ->
                runCatching { BottomNavContentTab.valueOf(value) }.getOrNull()
            }
            ?: BottomNavContentTab.DEFAULT_ORDER
        val sanitizedOrder = BottomNavContentTab.sanitizeOrder(parsedOrder)

        AppSettings(
            themeMode = ThemeMode.valueOf(
                preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
            ),
            colorScheme = ColorScheme.valueOf(
                preferences[COLOR_SCHEME_KEY] ?: ColorScheme.DEFAULT.name
            ),
            customPrimaryColor = preferences[CUSTOM_PRIMARY_COLOR_KEY] ?: 0xFF6650a4,
            customSecondaryColor = preferences[CUSTOM_SECONDARY_COLOR_KEY] ?: 0xFF625b71,
            customTertiaryColor = preferences[CUSTOM_TERTIARY_COLOR_KEY] ?: 0xFF7D5260,
            language = Language.valueOf(
                preferences[LANGUAGE_KEY] ?: Language.SYSTEM.name
            ),
            biometricEnabled = preferences[BIOMETRIC_ENABLED_KEY] ?: true, // 默认启用指纹验证
            autoLockMinutes = preferences[AUTO_LOCK_MINUTES_KEY] ?: 5,
            screenshotProtectionEnabled = preferences[SCREENSHOT_PROTECTION_KEY] ?: true,
            dynamicColorEnabled = preferences[DYNAMIC_COLOR_ENABLED_KEY] ?: true, // 默认启用动态颜色
            bottomSheetEnabled = preferences[BOTTOM_SHEET_ENABLED_KEY] ?: false, // 默认关闭 Bottom Sheet 模式
            bottomNavVisibility = BottomNavVisibility(
                passwords = preferences[SHOW_PASSWORDS_TAB_KEY] ?: true,
                authenticator = preferences[SHOW_AUTHENTICATOR_TAB_KEY] ?: true,
                documents = preferences[SHOW_DOCUMENTS_TAB_KEY] ?: true,
                bankCards = preferences[SHOW_BANK_CARDS_TAB_KEY] ?: false,  // 银行卡功能默认关闭
                generator = preferences[SHOW_GENERATOR_TAB_KEY] ?: false    // 生成器功能默认关闭
            ),
            bottomNavOrder = sanitizedOrder
        )
    }
    
    suspend fun updateThemeMode(themeMode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = themeMode.name
        }
    }

    suspend fun updateColorScheme(colorScheme: ColorScheme) {
        dataStore.edit { preferences ->
            preferences[COLOR_SCHEME_KEY] = colorScheme.name
        }
    }
    
    suspend fun updateLanguage(language: Language) {
        dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language.name
        }
    }
    
    suspend fun updateBiometricEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED_KEY] = enabled
        }
    }
    
    suspend fun updateAutoLockMinutes(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[AUTO_LOCK_MINUTES_KEY] = minutes
        }
    }
    
    suspend fun updateScreenshotProtectionEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SCREENSHOT_PROTECTION_KEY] = enabled
        }
    }

    suspend fun updateDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_ENABLED_KEY] = enabled
        }
    }

    suspend fun updateBottomSheetEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BOTTOM_SHEET_ENABLED_KEY] = enabled
        }
    }

    suspend fun updateBottomNavVisibility(tab: BottomNavContentTab, visible: Boolean) {
        dataStore.edit { preferences ->
            when (tab) {
                BottomNavContentTab.PASSWORDS -> preferences[SHOW_PASSWORDS_TAB_KEY] = visible
                BottomNavContentTab.AUTHENTICATOR -> preferences[SHOW_AUTHENTICATOR_TAB_KEY] = visible
                BottomNavContentTab.DOCUMENTS -> preferences[SHOW_DOCUMENTS_TAB_KEY] = visible
                BottomNavContentTab.BANK_CARDS -> preferences[SHOW_BANK_CARDS_TAB_KEY] = visible
                BottomNavContentTab.GENERATOR -> preferences[SHOW_GENERATOR_TAB_KEY] = visible  // 添加生成器分支
            }
        }
    }

    suspend fun updateBottomNavOrder(order: List<BottomNavContentTab>) {
        val sanitizedOrder = BottomNavContentTab.sanitizeOrder(order)
        dataStore.edit { preferences ->
            preferences[BOTTOM_NAV_ORDER_KEY] = sanitizedOrder.joinToString(",") { it.name }
        }
    }

    suspend fun updateCustomColors(primary: Long, secondary: Long, tertiary: Long) {
        dataStore.edit { preferences ->
            preferences[CUSTOM_PRIMARY_COLOR_KEY] = primary
            preferences[CUSTOM_SECONDARY_COLOR_KEY] = secondary
            preferences[CUSTOM_TERTIARY_COLOR_KEY] = tertiary
        }
    }
}