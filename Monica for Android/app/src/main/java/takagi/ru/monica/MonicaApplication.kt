package takagi.ru.monica

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import takagi.ru.monica.autofill.di.autofillModule
import takagi.ru.monica.autofill.di.autofillSessionModule
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
    }
    
    override fun onCreate() {
        super.onCreate()
        
        initKoin()
        refreshCredentialProviderService()
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
     * 刷新 Credential Provider Service 注册
     * 
     * 通过禁用再启用组件的方式，强制系统刷新对 CredentialProviderService 的识别
     * 这解决了安装新版本后需要手动切换通行密钥提供者的问题
     */
    private fun refreshCredentialProviderService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val componentName = ComponentName(this, MonicaCredentialProviderService::class.java)
                val pm = packageManager
                
                // 检查当前状态
                val currentState = pm.getComponentEnabledSetting(componentName)
                
                // 如果组件已启用，执行刷新
                if (currentState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ||
                    currentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    
                    // 先禁用
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    
                    // 再启用 - 这会触发系统重新识别服务
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    
                    Log.d(TAG, "CredentialProviderService refreshed")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to refresh CredentialProviderService", e)
            }
        }
    }
}