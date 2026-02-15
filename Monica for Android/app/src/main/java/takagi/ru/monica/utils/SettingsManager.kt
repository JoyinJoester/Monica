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
import takagi.ru.monica.data.PresetCustomField
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.data.AutofillSource

private val Context.dataStore by preferencesDataStore("settings")

data class RememberedStorageTarget(
    val categoryId: Long? = null,
    val keepassDatabaseId: Long? = null,
    val bitwardenVaultId: Long? = null,
    val bitwardenFolderId: String? = null
)

data class SavedCategoryFilterState(
    val type: String = "all",
    val primaryId: Long? = null,
    val secondaryId: Long? = null,
    val text: String? = null
)

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
        private val SHOW_SEND_TAB_KEY = booleanPreferencesKey("show_send_tab")
        private val SHOW_TIMELINE_TAB_KEY = booleanPreferencesKey("show_timeline_tab")  // 添加时间线标签键
        private val SHOW_PASSKEY_TAB_KEY = booleanPreferencesKey("show_passkey_tab")  // 添加 Passkey 标签键
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
        private val HIDE_FAB_ON_SCROLL_KEY = booleanPreferencesKey("hide_fab_on_scroll") // 滚动隐藏 FAB
        private val NOTE_GRID_LAYOUT_KEY = booleanPreferencesKey("note_grid_layout") // 笔记网格布局
        private val AUTOFILL_AUTH_REQUIRED_KEY = booleanPreferencesKey("autofill_auth_required") // 自动填充验证
        
        // 密码页面字段可见性
        private val FIELD_SECURITY_VERIFICATION_KEY = booleanPreferencesKey("field_security_verification")
        private val FIELD_CATEGORY_AND_NOTES_KEY = booleanPreferencesKey("field_category_and_notes")
        private val FIELD_APP_BINDING_KEY = booleanPreferencesKey("field_app_binding")
        private val FIELD_PERSONAL_INFO_KEY = booleanPreferencesKey("field_personal_info")
        private val FIELD_ADDRESS_INFO_KEY = booleanPreferencesKey("field_address_info")
        private val FIELD_PAYMENT_INFO_KEY = booleanPreferencesKey("field_payment_info")
        
        // 预设自定义字段 (JSON 格式存储)
        private val PRESET_CUSTOM_FIELDS_KEY = stringPreferencesKey("preset_custom_fields")
        
        // 减少动画 - 解决部分设备动画卡顿问题
        private val REDUCE_ANIMATIONS_KEY = booleanPreferencesKey("reduce_animations")

        // 智能去重
        private val SMART_DEDUPLICATION_ENABLED_KEY = booleanPreferencesKey("smart_deduplication_enabled")
        private val LAST_PASSWORD_CATEGORY_FILTER_TYPE_KEY = stringPreferencesKey("last_password_category_filter_type")
        private val LAST_PASSWORD_CATEGORY_FILTER_PRIMARY_ID_KEY = longPreferencesKey("last_password_category_filter_primary_id")
        private val LAST_PASSWORD_CATEGORY_FILTER_SECONDARY_ID_KEY = longPreferencesKey("last_password_category_filter_secondary_id")
        private val LAST_PASSWORD_CATEGORY_FILTER_TEXT_KEY = stringPreferencesKey("last_password_category_filter_text")

        // Bitwarden 同步范围
        private val BITWARDEN_UPLOAD_ALL_KEY = booleanPreferencesKey("bitwarden_upload_all")
        
        private val AUTOFILL_SOURCES_KEY = stringPreferencesKey("autofill_sources")
        private val AUTOFILL_PRIORITY_KEY = stringPreferencesKey("autofill_priority")

    }

    object StorageTargetScope {
        const val NOTE = "note"
        const val TOTP = "totp"
        const val BANK_CARD = "bank_card"
        const val PASSKEY = "passkey"
    }

    object CategoryFilterScope {
        const val NOTE = "note"
        const val TOTP = "totp"
        const val PASSKEY = "passkey"
    }

    private fun storageCategoryKey(scope: String) = longPreferencesKey("last_storage_${scope}_category_id")
    private fun storageKeePassKey(scope: String) = longPreferencesKey("last_storage_${scope}_keepass_database_id")
    private fun storageBitwardenVaultKey(scope: String) = longPreferencesKey("last_storage_${scope}_bitwarden_vault_id")
    private fun storageBitwardenFolderKey(scope: String) = stringPreferencesKey("last_storage_${scope}_bitwarden_folder_id")

    private fun categoryFilterTypeKey(scope: String) = stringPreferencesKey("last_category_filter_${scope}_type")
    private fun categoryFilterPrimaryKey(scope: String) = longPreferencesKey("last_category_filter_${scope}_primary_id")
    private fun categoryFilterSecondaryKey(scope: String) = longPreferencesKey("last_category_filter_${scope}_secondary_id")
    private fun categoryFilterTextKey(scope: String) = stringPreferencesKey("last_category_filter_${scope}_text")
    
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
            biometricEnabled = preferences[BIOMETRIC_ENABLED_KEY] ?: false,
            autoLockMinutes = preferences[AUTO_LOCK_MINUTES_KEY] ?: 5,
            screenshotProtectionEnabled = preferences[SCREENSHOT_PROTECTION_KEY] ?: true,
            dynamicColorEnabled = preferences[DYNAMIC_COLOR_ENABLED_KEY] ?: true, // 默认启用动态颜色
            bottomNavVisibility = BottomNavVisibility(
                // vault = preferences[SHOW_VAULT_TAB_KEY] ?: true,
                passwords = preferences[SHOW_PASSWORDS_TAB_KEY] ?: true,
                authenticator = preferences[SHOW_AUTHENTICATOR_TAB_KEY] ?: true,
                cardWallet = preferences[SHOW_CARD_WALLET_TAB_KEY] ?: true,
                generator = preferences[SHOW_GENERATOR_TAB_KEY] ?: false,
                notes = preferences[SHOW_NOTES_TAB_KEY] ?: false,
                send = preferences[SHOW_SEND_TAB_KEY] ?: false,
                timeline = preferences[SHOW_TIMELINE_TAB_KEY] ?: false,
                passkey = preferences[SHOW_PASSKEY_TAB_KEY] ?: true
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
            hideFabOnScroll = preferences[HIDE_FAB_ON_SCROLL_KEY] ?: false,
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
            }.getOrDefault(takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL),
            noteGridLayout = preferences[NOTE_GRID_LAYOUT_KEY] ?: true,
            autofillAuthRequired = preferences[AUTOFILL_AUTH_REQUIRED_KEY] ?: true, // 默认开启
            passwordFieldVisibility = takagi.ru.monica.data.PasswordFieldVisibility(
                securityVerification = preferences[FIELD_SECURITY_VERIFICATION_KEY] ?: true,
                categoryAndNotes = preferences[FIELD_CATEGORY_AND_NOTES_KEY] ?: true,
                appBinding = preferences[FIELD_APP_BINDING_KEY] ?: true,
                personalInfo = preferences[FIELD_PERSONAL_INFO_KEY] ?: true,
                addressInfo = preferences[FIELD_ADDRESS_INFO_KEY] ?: true,
                paymentInfo = preferences[FIELD_PAYMENT_INFO_KEY] ?: true
            ),
            reduceAnimations = preferences[REDUCE_ANIMATIONS_KEY] ?: false,
            smartDeduplicationEnabled = preferences[SMART_DEDUPLICATION_ENABLED_KEY] ?: true,
            lastPasswordCategoryFilterType = preferences[LAST_PASSWORD_CATEGORY_FILTER_TYPE_KEY] ?: "all",
            lastPasswordCategoryFilterPrimaryId = preferences[LAST_PASSWORD_CATEGORY_FILTER_PRIMARY_ID_KEY],
            lastPasswordCategoryFilterSecondaryId = preferences[LAST_PASSWORD_CATEGORY_FILTER_SECONDARY_ID_KEY],
            lastPasswordCategoryFilterText = preferences[LAST_PASSWORD_CATEGORY_FILTER_TEXT_KEY],
            bitwardenUploadAll = preferences[BITWARDEN_UPLOAD_ALL_KEY] ?: false,
            
            autofillSources = runCatching {
                val sourcesStr = preferences[AUTOFILL_SOURCES_KEY] ?: AutofillSource.V1_LOCAL.name
                sourcesStr.split(",").mapNotNull { 
                    runCatching { AutofillSource.valueOf(it.trim()) }.getOrNull() 
                }.toSet()
            }.getOrDefault(setOf(AutofillSource.V1_LOCAL)),
            autofillPriority = runCatching {
                val priorityStr = preferences[AUTOFILL_PRIORITY_KEY] ?: AutofillSource.V1_LOCAL.name
                priorityStr.split(",").mapNotNull { 
                    runCatching { AutofillSource.valueOf(it.trim()) }.getOrNull() 
                }
            }.getOrDefault(listOf(AutofillSource.V1_LOCAL))
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

    suspend fun updateBitwardenUploadAll(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BITWARDEN_UPLOAD_ALL_KEY] = enabled
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
                // BottomNavContentTab.VAULT -> preferences[SHOW_VAULT_TAB_KEY] = visible
                BottomNavContentTab.PASSWORDS -> preferences[SHOW_PASSWORDS_TAB_KEY] = visible
                BottomNavContentTab.AUTHENTICATOR -> preferences[SHOW_AUTHENTICATOR_TAB_KEY] = visible
                BottomNavContentTab.CARD_WALLET -> preferences[SHOW_CARD_WALLET_TAB_KEY] = visible
                BottomNavContentTab.GENERATOR -> preferences[SHOW_GENERATOR_TAB_KEY] = visible
                BottomNavContentTab.NOTES -> preferences[SHOW_NOTES_TAB_KEY] = visible
                BottomNavContentTab.SEND -> preferences[SHOW_SEND_TAB_KEY] = visible
                BottomNavContentTab.TIMELINE -> preferences[SHOW_TIMELINE_TAB_KEY] = visible
                BottomNavContentTab.PASSKEY -> preferences[SHOW_PASSKEY_TAB_KEY] = visible
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

    suspend fun updateHideFabOnScroll(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[HIDE_FAB_ON_SCROLL_KEY] = enabled
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

    suspend fun updateNoteGridLayout(isGrid: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTE_GRID_LAYOUT_KEY] = isGrid
        }
    }

    suspend fun updateAutofillAuthRequired(required: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTOFILL_AUTH_REQUIRED_KEY] = required
        }
    }

    suspend fun updatePasswordFieldVisibility(field: String, visible: Boolean) {
        dataStore.edit { preferences ->
            when (field) {
                "securityVerification" -> preferences[FIELD_SECURITY_VERIFICATION_KEY] = visible
                "categoryAndNotes" -> preferences[FIELD_CATEGORY_AND_NOTES_KEY] = visible
                "appBinding" -> preferences[FIELD_APP_BINDING_KEY] = visible
                "personalInfo" -> preferences[FIELD_PERSONAL_INFO_KEY] = visible
                "addressInfo" -> preferences[FIELD_ADDRESS_INFO_KEY] = visible
                "paymentInfo" -> preferences[FIELD_PAYMENT_INFO_KEY] = visible
            }
        }
    }
    
    // ==================== 预设自定义字段管理 ====================
    
    /**
     * 获取预设自定义字段列表 Flow
     */
    val presetCustomFieldsFlow: Flow<List<PresetCustomField>> = dataStore.data.map { preferences ->
        val json = preferences[PRESET_CUSTOM_FIELDS_KEY] ?: "[]"
        PresetCustomField.listFromJson(json)
    }
    
    /**
     * 添加预设自定义字段
     */
    suspend fun addPresetCustomField(field: PresetCustomField) {
        dataStore.edit { preferences ->
            val currentJson = preferences[PRESET_CUSTOM_FIELDS_KEY] ?: "[]"
            val currentList = PresetCustomField.listFromJson(currentJson).toMutableList()
            // 设置新字段的排序为最后
            val maxOrder = currentList.maxOfOrNull { it.order } ?: -1
            val newField = field.copy(order = maxOrder + 1)
            currentList.add(newField)
            preferences[PRESET_CUSTOM_FIELDS_KEY] = PresetCustomField.listToJson(currentList)
        }
    }
    
    /**
     * 更新预设自定义字段
     */
    suspend fun updatePresetCustomField(field: PresetCustomField) {
        dataStore.edit { preferences ->
            val currentJson = preferences[PRESET_CUSTOM_FIELDS_KEY] ?: "[]"
            val currentList = PresetCustomField.listFromJson(currentJson).toMutableList()
            val index = currentList.indexOfFirst { it.id == field.id }
            if (index >= 0) {
                currentList[index] = field
                preferences[PRESET_CUSTOM_FIELDS_KEY] = PresetCustomField.listToJson(currentList)
            }
        }
    }
    
    /**
     * 删除预设自定义字段
     */
    suspend fun deletePresetCustomField(fieldId: String) {
        dataStore.edit { preferences ->
            val currentJson = preferences[PRESET_CUSTOM_FIELDS_KEY] ?: "[]"
            val currentList = PresetCustomField.listFromJson(currentJson).toMutableList()
            currentList.removeAll { it.id == fieldId }
            // 重新排序
            val reordered = currentList.mapIndexed { index, field -> field.copy(order = index) }
            preferences[PRESET_CUSTOM_FIELDS_KEY] = PresetCustomField.listToJson(reordered)
        }
    }
    
    /**
     * 重新排序预设自定义字段
     */
    suspend fun reorderPresetCustomFields(fieldIds: List<String>) {
        dataStore.edit { preferences ->
            val currentJson = preferences[PRESET_CUSTOM_FIELDS_KEY] ?: "[]"
            val currentList = PresetCustomField.listFromJson(currentJson)
            val fieldMap = currentList.associateBy { it.id }
            val reorderedList = fieldIds.mapIndexedNotNull { index, id ->
                fieldMap[id]?.copy(order = index)
            }
            preferences[PRESET_CUSTOM_FIELDS_KEY] = PresetCustomField.listToJson(reorderedList)
        }
    }
    
    /**
     * 清空所有预设自定义字段
     */
    suspend fun clearAllPresetCustomFields() {
        dataStore.edit { preferences ->
            preferences[PRESET_CUSTOM_FIELDS_KEY] = "[]"
        }
    }
    
    /**
     * 更新减少动画设置
     * 开启后将禁用共享元素动画，改为简单的淡入淡出效果
     * 主要用于解决 HyperOS 2 / Android 15 等设备上的动画卡顿问题
     */
    suspend fun updateReduceAnimations(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[REDUCE_ANIMATIONS_KEY] = enabled
        }
    }

    suspend fun updateSmartDeduplicationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SMART_DEDUPLICATION_ENABLED_KEY] = enabled
        }
    }

    suspend fun updateLastPasswordCategoryFilter(
        type: String,
        primaryId: Long? = null,
        secondaryId: Long? = null,
        text: String? = null
    ) {
        dataStore.edit { preferences ->
            preferences[LAST_PASSWORD_CATEGORY_FILTER_TYPE_KEY] = type
            if (primaryId != null) {
                preferences[LAST_PASSWORD_CATEGORY_FILTER_PRIMARY_ID_KEY] = primaryId
            } else {
                preferences.remove(LAST_PASSWORD_CATEGORY_FILTER_PRIMARY_ID_KEY)
            }
            if (secondaryId != null) {
                preferences[LAST_PASSWORD_CATEGORY_FILTER_SECONDARY_ID_KEY] = secondaryId
            } else {
                preferences.remove(LAST_PASSWORD_CATEGORY_FILTER_SECONDARY_ID_KEY)
            }
            if (text.isNullOrBlank()) {
                preferences.remove(LAST_PASSWORD_CATEGORY_FILTER_TEXT_KEY)
            } else {
                preferences[LAST_PASSWORD_CATEGORY_FILTER_TEXT_KEY] = text
            }
        }
    }

    fun rememberedStorageTargetFlow(scope: String): Flow<RememberedStorageTarget> = dataStore.data.map { preferences ->
        RememberedStorageTarget(
            categoryId = preferences[storageCategoryKey(scope)],
            keepassDatabaseId = preferences[storageKeePassKey(scope)],
            bitwardenVaultId = preferences[storageBitwardenVaultKey(scope)],
            bitwardenFolderId = preferences[storageBitwardenFolderKey(scope)]
        )
    }

    suspend fun updateRememberedStorageTarget(scope: String, target: RememberedStorageTarget) {
        dataStore.edit { preferences ->
            val categoryKey = storageCategoryKey(scope)
            val keepassKey = storageKeePassKey(scope)
            val bitwardenVaultKey = storageBitwardenVaultKey(scope)
            val bitwardenFolderKey = storageBitwardenFolderKey(scope)

            if (target.categoryId != null) preferences[categoryKey] = target.categoryId else preferences.remove(categoryKey)
            if (target.keepassDatabaseId != null) preferences[keepassKey] = target.keepassDatabaseId else preferences.remove(keepassKey)
            if (target.bitwardenVaultId != null) preferences[bitwardenVaultKey] = target.bitwardenVaultId else preferences.remove(bitwardenVaultKey)
            if (target.bitwardenFolderId.isNullOrBlank()) preferences.remove(bitwardenFolderKey) else preferences[bitwardenFolderKey] = target.bitwardenFolderId
        }
    }

    fun categoryFilterStateFlow(scope: String): Flow<SavedCategoryFilterState> = dataStore.data.map { preferences ->
        SavedCategoryFilterState(
            type = preferences[categoryFilterTypeKey(scope)] ?: "all",
            primaryId = preferences[categoryFilterPrimaryKey(scope)],
            secondaryId = preferences[categoryFilterSecondaryKey(scope)],
            text = preferences[categoryFilterTextKey(scope)]
        )
    }

    suspend fun updateCategoryFilterState(scope: String, state: SavedCategoryFilterState) {
        dataStore.edit { preferences ->
            val typeKey = categoryFilterTypeKey(scope)
            val primaryKey = categoryFilterPrimaryKey(scope)
            val secondaryKey = categoryFilterSecondaryKey(scope)
            val textKey = categoryFilterTextKey(scope)

            preferences[typeKey] = state.type
            if (state.primaryId != null) preferences[primaryKey] = state.primaryId else preferences.remove(primaryKey)
            if (state.secondaryId != null) preferences[secondaryKey] = state.secondaryId else preferences.remove(secondaryKey)
            if (state.text.isNullOrBlank()) preferences.remove(textKey) else preferences[textKey] = state.text
        }
    }
    
    /**
     * 更新自动填充数据源
     */
    suspend fun updateAutofillSources(sources: Set<AutofillSource>) {
        dataStore.edit { preferences ->
            preferences[AUTOFILL_SOURCES_KEY] = sources.joinToString(",") { it.name }
        }
    }
    
    /**
     * 更新自动填充优先级
     */
    suspend fun updateAutofillPriority(priority: List<AutofillSource>) {
        dataStore.edit { preferences ->
            preferences[AUTOFILL_PRIORITY_KEY] = priority.joinToString(",") { it.name }
        }
    }
    
}
