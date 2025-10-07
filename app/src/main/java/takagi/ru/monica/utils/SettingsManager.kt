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
            language = Language.valueOf(
                preferences[LANGUAGE_KEY] ?: Language.SYSTEM.name
            ),
            biometricEnabled = preferences[BIOMETRIC_ENABLED_KEY] ?: false,
            autoLockMinutes = preferences[AUTO_LOCK_MINUTES_KEY] ?: 5,
            screenshotProtectionEnabled = preferences[SCREENSHOT_PROTECTION_KEY] ?: true,
            bottomNavVisibility = BottomNavVisibility(
                passwords = preferences[SHOW_PASSWORDS_TAB_KEY] ?: true,
                authenticator = preferences[SHOW_AUTHENTICATOR_TAB_KEY] ?: true,
                documents = preferences[SHOW_DOCUMENTS_TAB_KEY] ?: true,
                bankCards = preferences[SHOW_BANK_CARDS_TAB_KEY] ?: true,
                ledger = preferences[SHOW_LEDGER_TAB_KEY] ?: true
            ),
            bottomNavOrder = sanitizedOrder
        )
    }
    
    suspend fun updateThemeMode(themeMode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = themeMode.name
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

    suspend fun updateBottomNavVisibility(tab: BottomNavContentTab, visible: Boolean) {
        dataStore.edit { preferences ->
            when (tab) {
                BottomNavContentTab.PASSWORDS -> preferences[SHOW_PASSWORDS_TAB_KEY] = visible
                BottomNavContentTab.AUTHENTICATOR -> preferences[SHOW_AUTHENTICATOR_TAB_KEY] = visible
                BottomNavContentTab.DOCUMENTS -> preferences[SHOW_DOCUMENTS_TAB_KEY] = visible
                BottomNavContentTab.BANK_CARDS -> preferences[SHOW_BANK_CARDS_TAB_KEY] = visible
                BottomNavContentTab.LEDGER -> preferences[SHOW_LEDGER_TAB_KEY] = visible
            }
        }
    }

    suspend fun updateBottomNavOrder(order: List<BottomNavContentTab>) {
        val sanitizedOrder = BottomNavContentTab.sanitizeOrder(order)
        dataStore.edit { preferences ->
            preferences[BOTTOM_NAV_ORDER_KEY] = sanitizedOrder.joinToString(",") { it.name }
        }
    }
}