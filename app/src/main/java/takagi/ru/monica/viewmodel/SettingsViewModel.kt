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
import takagi.ru.monica.utils.SettingsManager

/**
 * ViewModel for Settings screen
 */
class SettingsViewModel(
    private val settingsManager: SettingsManager
) : ViewModel() {
    
    val settings: StateFlow<AppSettings> = settingsManager.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )
    
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
}