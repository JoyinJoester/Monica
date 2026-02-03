package takagi.ru.monica.ui.base

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.Language
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.utils.LocaleHelper
import takagi.ru.monica.utils.ScreenshotProtectionUtil
import takagi.ru.monica.utils.SettingsManager

/**
 * Monica 应用的统一基类 Activity
 * 
 * 功能：
 * - attachBaseContext：统一处理语言上下文（LocaleHelper），带超时保护
 * - Theme：统一监听 SettingsManager 并应用主题（深色模式、动态取色）
 * - ScreenshotProtection：统一处理防截屏逻辑
 * - SessionManager：统一管理会话状态和自动锁定
 * 
 * 继承此基类的 Activity：
 * - MainActivity
 * - AutofillPickerActivityV2
 */
abstract class BaseMonicaActivity : FragmentActivity() {
    
    protected lateinit var settingsManager: SettingsManager
    
    // 缓存的设置，供子类使用
    protected var cachedSettings: AppSettings? = null
    
    override fun attachBaseContext(newBase: Context?) {
        if (newBase != null) {
            val tempSettingsManager = SettingsManager(newBase)
            // 使用超时保护，防止 ANR
            val language = try {
                runBlocking {
                    withTimeout(200) {
                        try {
                            tempSettingsManager.settingsFlow.first().language
                        } catch (e: Exception) {
                            Language.SYSTEM
                        }
                    }
                }
            } catch (e: Exception) {
                // 超时或出错，回退到默认
                Language.SYSTEM
            }
            super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
        } else {
            super.attachBaseContext(newBase)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        settingsManager = SettingsManager(applicationContext)
        
        // 监听设置变化，更新截图保护和自动锁定配置
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsManager.settingsFlow.collect { settings ->
                    cachedSettings = settings
                    
                    // 更新截图保护
                    applyScreenshotProtection(settings.screenshotProtectionEnabled)
                    
                    // 更新 SessionManager 的自动锁定超时
                    SessionManager.updateAutoLockTimeout(settings.autoLockMinutes)
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // 检查会话是否过期
        if (SessionManager.isSessionExpired()) {
            SessionManager.markLocked()
            onSessionExpired()
        } else {
            // 刷新会话时间戳
            SessionManager.refreshSession()
        }
    }
    
    /**
     * 应用截图保护设置
     */
    protected fun applyScreenshotProtection(enabled: Boolean) {
        if (enabled) {
            ScreenshotProtectionUtil.enableScreenshotProtection(this)
        } else {
            ScreenshotProtectionUtil.disableScreenshotProtection(this)
        }
    }
    
    /**
     * 会话过期回调，子类可覆写以处理锁定逻辑
     */
    protected open fun onSessionExpired() {
        // 默认空实现，子类可覆写
        android.util.Log.d("BaseMonicaActivity", "Session expired")
    }
    
    /**
     * 检查是否可以跳过验证（基于 SessionManager 的安全窗规则）
     */
    protected fun canSkipVerification(): Boolean {
        return SessionManager.canSkipVerification(this)
    }
    
    /**
     * 标记验证成功，更新会话状态
     */
    protected fun markAuthenticationSuccess() {
        SessionManager.markUnlocked()
    }
}
