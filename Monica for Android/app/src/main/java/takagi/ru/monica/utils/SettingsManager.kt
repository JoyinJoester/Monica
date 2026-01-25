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
        private val SHOW_CARD_WALLET_TAB_KEY = booleanPreferencesKey("show_card_wallet_tab")
        private val SHOW_NOTES_TAB_KEY = booleanPreferencesKey("show_notes_tab")
        private val SHOW_LEDGER_TAB_KEY = booleanPreferencesKey("show_ledger_tab")
        private val SHOW_GENERATOR_TAB_KEY = booleanPreferencesKey("show_generator_tab")  // 添加生成器标签键
        private val SHOW_TIMELINE_TAB_KEY = booleanPreferencesKey("show_timeline_tab")  // 添加时间线标签键
        private val DYNAMIC_COLOR_ENABLED_KEY = booleanPreferencesKey("dynamic_color_enabled")
        private val BOTTOM_NAV_ORDER_KEY = stringPreferencesKey("bottom_nav_order")
        private val USE_DRAGGABLE_BOTTOM_NAV_KEY = booleanPreferencesKey("use_draggable_bottom_nav")
        private val DISABLE_PASSWORD_VERIFICATION_KEY = booleanPreferencesKey("disable_password_verification")
        private val VALIDATOR_PROGRESS_BAR_STYLE_KEY = stringPreferencesKey("validator_progress_bar_style")
        private val VALIDATOR_UNIFIED_PROGRESS_BAR_KEY = stringPreferencesKey("validator_unified_progress_bar")
        private val VALIDATOR_SMOOTH_PROGRESS_KEY = booleanPreferencesKey("validator_smooth_progress")
        private val VALIDATOR_VIBRATION_ENABLED_KEY = booleanPreferencesKey("validator_vibration_enabled")
        private val COPY_NEXT_CODE_WHEN_EXPIRING_KEY = booleanPreferencesKey("copy_next_code_when_expiring")
        private val NOTIFICATION_VALIDATOR_ENABLED_KEY = booleanPreferencesKey("notification_validator_enabled")
        private val NOTIFICATION_VALIDATOR_AUTO_MATCH_KEY = booleanPreferencesKey("notification_validator_auto_match")
        private val NOTIFICATION_VALIDATOR_ID_KEY = longPreferencesKey("notification_validator_id")
        private val IS_PLUS_ACTIVATED_KEY = booleanPreferencesKey("is_plus_activated")
        private val STACK_CARD_MODE_KEY = stringPreferencesKey("stack_card_mode")
        private val PASSWORD_GROUP_MODE_KEY = stringPreferencesKey("password_group_mode")
        private val TOTP_TIME_OFFSET_KEY = intPreferencesKey("totp_time_offset") // TOTP时间偏移（秒）
        private val TRASH_ENABLED_KEY = booleanPreferencesKey("trash_enabled") // 回收站功能开关
        private val TRASH_AUTO_DELETE_DAYS_KEY = intPreferencesKey("trash_auto_delete_days") // 回收站自动清空天数
        private val ICON_CARDS_ENABLED_KEY = booleanPreferencesKey("icon_cards_enabled") // 带图标卡片开关
        private val PASSWORD_CARD_DISPLAY_MODE_KEY = stringPreferencesKey("password_card_display_mode") // 密码卡片显示模式
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
            colorScheme = runCatching {
                ColorScheme.valueOf(
                    preferences[COLOR_SCHEME_KEY] ?: ColorScheme.DEFAULT.name
                )
            }.getOrDefault(ColorScheme.DEFAULT),
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
            bottomNavVisibility = BottomNavVisibility(
                passwords = preferences[SHOW_PASSWORDS_TAB_KEY] ?: true,
                authenticator = preferences[SHOW_AUTHENTICATOR_TAB_KEY] ?: true,
                cardWallet = preferences[SHOW_CARD_WALLET_TAB_KEY] ?: true,
                generator = preferences[SHOW_GENERATOR_TAB_KEY] ?: false,
                notes = preferences[SHOW_NOTES_TAB_KEY] ?: false,
                timeline = preferences[SHOW_TIMELINE_TAB_KEY] ?: false
            ),
            bottomNavOrder = sanitizedOrder,
            useDraggableBottomNav = preferences[USE_DRAGGABLE_BOTTOM_NAV_KEY] ?: false,
            disablePasswordVerification = preferences[DISABLE_PASSWORD_VERIFICATION_KEY] ?: false,
            validatorProgressBarStyle = runCatching {
                val styleString = preferences[VALIDATOR_PROGRESS_BAR_STYLE_KEY] ?: takagi.ru.monica.data.ProgressBarStyle.LINEAR.name
                android.util.Log.d("SettingsManager", "Loading progress bar style from DataStore: $styleString")
                val style = takagi.ru.monica.data.ProgressBarStyle.valueOf(styleString)
                android.util.Log.d("SettingsManager", "Parsed progress bar style: $style")
                style
            }.getOrDefault(takagi.ru.monica.data.ProgressBarStyle.LINEAR),
            validatorUnifiedProgressBar = runCatching {
                val modeString = preferences[VALIDATOR_UNIFIED_PROGRESS_BAR_KEY] ?: takagi.ru.monica.data.UnifiedProgressBarMode.ENABLED.name
                takagi.ru.monica.data.UnifiedProgressBarMode.valueOf(modeString)
            }.getOrDefault(takagi.ru.monica.data.UnifiedProgressBarMode.ENABLED),
            validatorSmoothProgress = preferences[VALIDATOR_SMOOTH_PROGRESS_KEY] ?: true,
            validatorVibrationEnabled = preferences[VALIDATOR_VIBRATION_ENABLED_KEY] ?: true,
            copyNextCodeWhenExpiring = preferences[COPY_NEXT_CODE_WHEN_EXPIRING_KEY] ?: false,
            notificationValidatorEnabled = preferences[NOTIFICATION_VALIDATOR_ENABLED_KEY] ?: false,
            notificationValidatorAutoMatch = preferences[NOTIFICATION_VALIDATOR_AUTO_MATCH_KEY] ?: false,
            notificationValidatorId = preferences[NOTIFICATION_VALIDATOR_ID_KEY] ?: -1L,
            isPlusActivated = preferences[IS_PLUS_ACTIVATED_KEY] ?: false,
            stackCardMode = preferences[STACK_CARD_MODE_KEY] ?: "AUTO",
            passwordGroupMode = preferences[PASSWORD_GROUP_MODE_KEY] ?: "smart",
            totpTimeOffset = preferences[TOTP_TIME_OFFSET_KEY] ?: 0,
            trashEnabled = preferences[TRASH_ENABLED_KEY] ?: true,
            trashAutoDeleteDays = preferences[TRASH_AUTO_DELETE_DAYS_KEY] ?: 30,
            iconCardsEnabled = preferences[ICON_CARDS_ENABLED_KEY] ?: false,
            passwordCardDisplayMode = runCatching {
                val modeString = preferences[PASSWORD_CARD_DISPLAY_MODE_KEY] ?: takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL.name
                takagi.ru.monica.data.PasswordCardDisplayMode.valueOf(modeString)
            }.getOrDefault(takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL)
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

    suspend fun updateBottomNavVisibility(tab: BottomNavContentTab, visible: Boolean) {
        dataStore.edit { preferences ->
            when (tab) {
                BottomNavContentTab.PASSWORDS -> preferences[SHOW_PASSWORDS_TAB_KEY] = visible
                BottomNavContentTab.AUTHENTICATOR -> preferences[SHOW_AUTHENTICATOR_TAB_KEY] = visible
                BottomNavContentTab.CARD_WALLET -> preferences[SHOW_CARD_WALLET_TAB_KEY] = visible
                BottomNavContentTab.GENERATOR -> preferences[SHOW_GENERATOR_TAB_KEY] = visible  // 添加生成器分支
                BottomNavContentTab.NOTES -> preferences[SHOW_NOTES_TAB_KEY] = visible
                BottomNavContentTab.TIMELINE -> preferences[SHOW_TIMELINE_TAB_KEY] = visible
            }
        }
    }

    suspend fun updateBottomNavOrder(order: List<BottomNavContentTab>) {
        val sanitizedOrder = BottomNavContentTab.sanitizeOrder(order)
        dataStore.edit { preferences ->
            preferences[BOTTOM_NAV_ORDER_KEY] = sanitizedOrder.joinToString(",") { it.name }
        }
    }

    suspend fun updateUseDraggableBottomNav(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[USE_DRAGGABLE_BOTTOM_NAV_KEY] = enabled
        }
    }

    suspend fun updateCustomColors(primary: Long, secondary: Long, tertiary: Long) {
        dataStore.edit { preferences ->
            preferences[CUSTOM_PRIMARY_COLOR_KEY] = primary
            preferences[CUSTOM_SECONDARY_COLOR_KEY] = secondary
            preferences[CUSTOM_TERTIARY_COLOR_KEY] = tertiary
        }
    }

    suspend fun updateDisablePasswordVerification(disabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DISABLE_PASSWORD_VERIFICATION_KEY] = disabled
        }
    }

    suspend fun updateValidatorProgressBarStyle(style: takagi.ru.monica.data.ProgressBarStyle) {
        android.util.Log.d("SettingsManager", "Saving progress bar style: ${style.name}")
        dataStore.edit { preferences ->
            preferences[VALIDATOR_PROGRESS_BAR_STYLE_KEY] = style.name
        }
        android.util.Log.d("SettingsManager", "Progress bar style saved to DataStore")
    }

    suspend fun updateValidatorVibrationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[VALIDATOR_VIBRATION_ENABLED_KEY] = enabled
        }
    }

    suspend fun updateCopyNextCodeWhenExpiring(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[COPY_NEXT_CODE_WHEN_EXPIRING_KEY] = enabled
        }
    }

    suspend fun updateNotificationValidatorEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATION_VALIDATOR_ENABLED_KEY] = enabled
        }
    }

    suspend fun updateNotificationValidatorAutoMatch(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATION_VALIDATOR_AUTO_MATCH_KEY] = enabled
        }
    }

    suspend fun updateNotificationValidatorId(id: Long) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATION_VALIDATOR_ID_KEY] = id
        }
    }

    suspend fun updatePlusActivated(activated: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_PLUS_ACTIVATED_KEY] = activated
        }
    }

    suspend fun updateStackCardMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[STACK_CARD_MODE_KEY] = mode
        }
    }

    suspend fun updatePasswordGroupMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[PASSWORD_GROUP_MODE_KEY] = mode
        }
    }

    suspend fun updateTotpTimeOffset(offset: Int) {
        dataStore.edit { preferences ->
            preferences[TOTP_TIME_OFFSET_KEY] = offset
        }
    }

    suspend fun updateTrashEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[TRASH_ENABLED_KEY] = enabled
        }
    }

    suspend fun updateTrashAutoDeleteDays(days: Int) {
        dataStore.edit { preferences ->
            preferences[TRASH_AUTO_DELETE_DAYS_KEY] = days
        }
    }

    suspend fun updateValidatorUnifiedProgressBar(mode: takagi.ru.monica.data.UnifiedProgressBarMode) {
        dataStore.edit { preferences ->
            preferences[VALIDATOR_UNIFIED_PROGRESS_BAR_KEY] = mode.name
        }
    }

    suspend fun updateValidatorSmoothProgress(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[VALIDATOR_SMOOTH_PROGRESS_KEY] = enabled
        }
    }

    suspend fun updateIconCardsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[ICON_CARDS_ENABLED_KEY] = enabled
        }
    }

    suspend fun updatePasswordCardDisplayMode(mode: takagi.ru.monica.data.PasswordCardDisplayMode) {
        dataStore.edit { preferences ->
            preferences[PASSWORD_CARD_DISPLAY_MODE_KEY] = mode.name
        }
    }
}