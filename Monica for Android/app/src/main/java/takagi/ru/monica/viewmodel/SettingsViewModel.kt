package takagi.ru.monica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.BottomNavContentTab
import takagi.ru.monica.data.ColorScheme
import takagi.ru.monica.data.Language
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.utils.SettingsManager

/**
 * ViewModel for Settings screen
 */
class SettingsViewModel(
    private val settingsManager: SettingsManager,
    private val secureItemRepository: SecureItemRepository? = null
) : ViewModel() {
    
    val settings: StateFlow<AppSettings> = settingsManager.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    // 获取所有TOTP验证器
    val totpItems: StateFlow<List<SecureItem>> = secureItemRepository?.getItemsByType(ItemType.TOTP)
        ?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        ) ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    
    fun updateThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            settingsManager.updateThemeMode(themeMode)
        }
    }

    fun updateColorScheme(colorScheme: ColorScheme) {
        viewModelScope.launch {
            settingsManager.updateColorScheme(colorScheme)
        }
    }
    
    fun updateLanguage(language: Language) {
        viewModelScope.launch {
            settingsManager.updateLanguage(language)
        }
    }
    
    fun updateBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateBiometricEnabled(enabled)
        }
    }
    
    fun updateAutoLockMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsManager.updateAutoLockMinutes(minutes)
        }
    }
    
    fun updateScreenshotProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateScreenshotProtectionEnabled(enabled)
        }
    }

    fun updateDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateDynamicColorEnabled(enabled)
        }
    }

    fun updateBottomNavVisibility(tab: BottomNavContentTab, visible: Boolean) {
        viewModelScope.launch {
            settingsManager.updateBottomNavVisibility(tab, visible)
        }
    }

    fun updateBottomNavOrder(order: List<BottomNavContentTab>) {
        viewModelScope.launch {
            settingsManager.updateBottomNavOrder(order)
        }
    }

    fun updateCustomColors(primary: Long, secondary: Long, tertiary: Long) {
        viewModelScope.launch {
            settingsManager.updateCustomColors(primary, secondary, tertiary)
        }
    }

    fun updateStackCardMode(mode: String) {
        viewModelScope.launch {
            settingsManager.updateStackCardMode(mode)
        }
    }

    fun updatePasswordGroupMode(mode: String) {
        viewModelScope.launch {
            settingsManager.updatePasswordGroupMode(mode)
        }
    }

    fun updateDisablePasswordVerification(disabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateDisablePasswordVerification(disabled)
        }
    }

    fun updateValidatorProgressBarStyle(style: takagi.ru.monica.data.ProgressBarStyle) {
        android.util.Log.d("SettingsViewModel", "Updating progress bar style to: $style")
        viewModelScope.launch {
            settingsManager.updateValidatorProgressBarStyle(style)
            android.util.Log.d("SettingsViewModel", "Progress bar style updated successfully")
        }
    }

    fun updateValidatorUnifiedProgressBar(mode: takagi.ru.monica.data.UnifiedProgressBarMode) {
        viewModelScope.launch {
            settingsManager.updateValidatorUnifiedProgressBar(mode)
        }
    }

    fun updateValidatorSmoothProgress(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateValidatorSmoothProgress(enabled)
        }
    }

    fun updateValidatorVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateValidatorVibrationEnabled(enabled)
        }
    }

    fun updateCopyNextCodeWhenExpiring(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateCopyNextCodeWhenExpiring(enabled)
        }
    }

    fun updateNotificationValidatorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateNotificationValidatorEnabled(enabled)
        }
    }

    fun updateNotificationValidatorId(id: Long) {
        viewModelScope.launch {
            settingsManager.updateNotificationValidatorId(id)
        }
    }

    fun updateNotificationValidatorAutoMatch(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateNotificationValidatorAutoMatch(enabled)
        }
    }

    fun updatePlusActivated(activated: Boolean) {
        viewModelScope.launch {
            settingsManager.updatePlusActivated(activated)
        }
    }
    
    fun updateUseDraggableBottomNav(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateUseDraggableBottomNav(enabled)
        }
    }
    
    // 回收站设置
    fun updateTrashEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateTrashEnabled(enabled)
        }
    }
    
    fun updateTrashAutoDeleteDays(days: Int) {
        viewModelScope.launch {
            settingsManager.updateTrashAutoDeleteDays(days)
        }
    }
}