package takagi.ru.monica

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import takagi.ru.monica.autofill.di.autofillModule
import takagi.ru.monica.autofill.di.autofillSessionModule
import takagi.ru.monica.bitwarden.sync.NetworkMonitor
import takagi.ru.monica.bitwarden.sync.SyncQueueManager
import takagi.ru.monica.bitwarden.sync.SyncQueueManagerHolder
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.passkey.MonicaCredentialProviderService

/**
 * Monica 应用程序入口
 * 
 * 负责初始化全局依赖注入容器（Koin）
 * 
 * 安全设计考量:
 * - Koin 在进程级别初始化，生命周期与应用一致
 * - 敏感依赖使用 single 作用域，避免多实例
 * - 模块化设计便于测试时替换 mock 实现
 */
class MonicaApplication : Application() {
    
    companion object {
        private const val TAG = "MonicaApplication"
        private const val CREDENTIAL_REFRESH_DELAY_MS = 1500L
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        
        initKoin()
        initBitwardenSyncInfrastructure()
        scheduleCredentialProviderRefresh()
    }
    
    /**
     * 初始化 Koin 依赖注入框架
     * 
     * 按模块加载依赖:
     * - autofillModule: 自动填充核心组件
     * - autofillSessionModule: 会话级临时依赖
     */
    private fun initKoin() {
        startKoin {
            // 关闭日志以提高性能和安全性
            androidLogger(Level.NONE)
            
            // 提供 Android Context
            androidContext(this@MonicaApplication)
            
            // 加载模块
            modules(
                autofillModule,
                autofillSessionModule
            )
        }
    }

    /**
     * Initialize lightweight Bitwarden sync infrastructure so WorkManager
     * can resolve a queue manager instance instead of retry looping.
     */
    private fun initBitwardenSyncInfrastructure() {
        runCatching {
            val database = PasswordDatabase.getDatabase(this)
            val queueManager = SyncQueueManager(
                context = this,
                pendingOperationDao = database.bitwardenPendingOperationDao(),
                networkMonitor = NetworkMonitor(this)
            )
            SyncQueueManagerHolder.instance = queueManager
        }.onFailure { error ->
            Log.w(TAG, "Failed to init Bitwarden sync infrastructure", error)
        }
    }
    
    /**
     * 启动后做一次软刷新，不进入 DISABLED 状态，尽量避免体验抖动。
     */
    private fun maybeSoftRefreshCredentialProviderServiceState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        try {
            val componentName = ComponentName(this, MonicaCredentialProviderService::class.java)
            val currentState = packageManager.getComponentEnabledSetting(componentName)
            when (currentState) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> {
                    return
                }

                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> {
                    packageManager.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    packageManager.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                        PackageManager.DONT_KILL_APP
                    )
                }

                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> {
                    packageManager.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
            Log.d(TAG, "CredentialProviderService state soft-refreshed")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to soft-refresh CredentialProviderService state", e)
        }
    }

    private fun scheduleCredentialProviderRefresh() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        appScope.launch {
            delay(CREDENTIAL_REFRESH_DELAY_MS)
            maybeSoftRefreshCredentialProviderServiceState()
        }
    }
}

