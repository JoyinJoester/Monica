package takagi.ru.monica

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import takagi.ru.monica.autofill.di.autofillModule
import takagi.ru.monica.autofill.di.autofillSessionModule

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
    
    override fun onCreate() {
        super.onCreate()
        
        initKoin()
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
}
