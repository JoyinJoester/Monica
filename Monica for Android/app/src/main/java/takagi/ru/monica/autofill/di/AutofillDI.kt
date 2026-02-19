package takagi.ru.monica.autofill.di

import android.content.Context
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.getKoin
import takagi.ru.monica.autofill.data.*
import takagi.ru.monica.autofill.strategy.*
import takagi.ru.monica.autofill.engine.AutofillEngine
import takagi.ru.monica.autofill.engine.AutofillEngineBuilder
import takagi.ru.monica.autofill.core.AutofillLogger
import takagi.ru.monica.autofill.core.AutofillLogCategory
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.autofill.AutofillPreferences

/**
 * 自动填充依赖注入容器
 * 
 * ⚠️ 兼容层 - 渐进迁移到 Koin
 * 
 * 此对象现在作为 Koin 的兼容层，内部调用 Koin 获取依赖。
 * 新代码应直接使用 Koin 注入，例如:
 * ```kotlin
 * class MyService : KoinComponent {
 *     private val engine: AutofillEngine by inject()
 * }
 * ```
 * 
 * 或者在 Activity/Service 中:
 * ```kotlin
 * val engine: AutofillEngine by inject()
 * ```
 * 
 * @deprecated 优先使用 Koin 注入
 * @author Monica Team
 * @since 1.0
 */
object AutofillDI : KoinComponent {
    
    // ===== Koin 注入的依赖 =====
    
    private val koinEngine: AutofillEngine by inject()
    private val koinRepository: AutofillRepository by inject()
    private val koinCache: AutofillCache by inject()
    
    // ===== 公共API (兼容旧代码) =====

    /**
     * 提供自动填充引擎
     * 
     * @param context Android Context (现在被忽略，Koin 内部管理 Context)
     * @return AutofillEngine 单例
     * @deprecated 使用 Koin 注入: `val engine: AutofillEngine by inject()`
     */
    @Deprecated(
        message = "使用 Koin 注入替代",
        replaceWith = ReplaceWith(
            "inject<AutofillEngine>()",
            "org.koin.core.component.inject"
        )
    )
    fun provideEngine(context: Context): AutofillEngine {
        return try {
            koinEngine
        } catch (e: Exception) {
            // Koin 未初始化时的回退（应用启动早期）
            AutofillLogger.w(
                AutofillLogCategory.SERVICE,
                "Koin 未初始化，使用回退创建引擎",
                mapOf("error" to (e.message ?: "unknown"))
            )
            createEngineFallback(context)
        }
    }
    
    /**
     * 提供数据仓库
     * 
     * @param context Android Context
     * @return AutofillRepository 单例
     * @deprecated 使用 Koin 注入: `val repository: AutofillRepository by inject()`
     */
    @Deprecated(
        message = "使用 Koin 注入替代",
        replaceWith = ReplaceWith(
            "inject<AutofillRepository>()",
            "org.koin.core.component.inject"
        )
    )
    fun provideRepository(context: Context): AutofillRepository {
        return try {
            koinRepository
        } catch (e: Exception) {
            AutofillLogger.w(
                AutofillLogCategory.SERVICE,
                "Koin 未初始化，使用回退创建仓库"
            )
            createRepositoryFallback(context)
        }
    }
    
    /**
     * 提供缓存
     * 
     * @return AutofillCache 单例
     * @deprecated 使用 Koin 注入: `val cache: AutofillCache by inject()`
     */
    @Deprecated(
        message = "使用 Koin 注入替代",
        replaceWith = ReplaceWith(
            "inject<AutofillCache>()",
            "org.koin.core.component.inject"
        )
    )
    fun provideCache(): AutofillCache {
        return try {
            koinCache
        } catch (e: Exception) {
            AutofillLogger.w(
                AutofillLogCategory.SERVICE,
                "Koin 未初始化，使用回退创建缓存"
            )
            AutofillCache()
        }
    }
    
    /**
     * 重置所有单例
     * 
     * ⚠️ 注意: Koin 管理的依赖不会被重置
     * 如需重置，请使用 Koin 的 scope 机制
     */
    fun reset() {
        AutofillLogger.i(
            AutofillLogCategory.SERVICE,
            "AutofillDI.reset() 调用 - Koin 管理的依赖保持不变"
        )
    }
    
    /**
     * 清除缓存
     */
    fun clearCache() {
        try {
            koinRepository.clearCache()
        } catch (e: Exception) {
            // 忽略
        }
        AutofillLogger.i(
            AutofillLogCategory.SERVICE,
            "缓存已清除"
        )
    }
    
    // ===== 回退工厂方法 (Koin 未初始化时使用) =====
    
    private fun createEngineFallback(context: Context): AutofillEngine {
        val dataSource = createRepositoryFallback(context)
        val strategies = listOf(
            DomainMatchingStrategy(),
            PackageNameMatchingStrategy(),
            FuzzyMatchingStrategy()
        )
        val preferences = AutofillPreferencesData(
            enabled = true,
            requireBiometric = false,
            enableFuzzySearch = true,
            autoSubmit = false,
            maxSuggestions = 5,
            enableInlineSuggestions = true,
            enableLogging = true,
            timeoutMs = 5000
        )
        
        return AutofillEngineBuilder()
            .setDataSource(dataSource)
            .apply { strategies.forEach { addStrategy(it) } }
            .setPreferences(preferences)
            .build()
    }
    
    private fun createRepositoryFallback(context: Context): AutofillRepository {
        val database = PasswordDatabase.getDatabase(context.applicationContext)
        val passwordRepository = PasswordRepository(database.passwordEntryDao())
        return AutofillRepository(passwordRepository, AutofillCache())
    }
}

/**
 * 测试用的依赖注入容器
 * 
 * 允许注入 Mock 对象进行测试
 */
object TestAutofillDI {
    
    private var testEngine: AutofillEngine? = null
    private var testRepository: AutofillRepository? = null
    
    /**
     * 设置测试引擎
     */
    fun setTestEngine(engine: AutofillEngine) {
        testEngine = engine
    }
    
    /**
     * 设置测试仓库
     */
    fun setTestRepository(repository: AutofillRepository) {
        testRepository = repository
    }
    
    /**
     * 获取测试引擎
     */
    fun getTestEngine(): AutofillEngine? = testEngine
    
    /**
     * 获取测试仓库
     */
    fun getTestRepository(): AutofillRepository? = testRepository
    
    /**
     * 清除测试对象
     */
    fun clear() {
        testEngine = null
        testRepository = null
    }
}

/**
 * 延迟初始化助手
 * 
 * 提供 Kotlin lazy 委托,用于延迟初始化依赖
 */
class AutofillLazy<T>(private val initializer: () -> T) {
    
    @Volatile
    private var value: T? = null
    
    fun get(): T {
        return value ?: synchronized(this) {
            value ?: initializer().also { value = it }
        }
    }
    
    fun reset() {
        synchronized(this) {
            value = null
        }
    }
}

/**
 * Context 扩展函数
 * 
 * 提供便捷的依赖访问方式
 * 
 * @deprecated 优先在组件中使用 Koin 注入
 */

/**
 * 获取自动填充引擎
 * @deprecated 使用 Koin 注入: `val engine: AutofillEngine by inject()`
 */
@Deprecated("使用 Koin 注入替代")
fun Context.getAutofillEngine(): AutofillEngine {
    return try {
        getKoin().get()
    } catch (e: Exception) {
        AutofillDI.provideEngine(this)
    }
}

/**
 * 获取自动填充仓库
 * @deprecated 使用 Koin 注入: `val repository: AutofillRepository by inject()`
 */
@Deprecated("使用 Koin 注入替代")
fun Context.getAutofillRepository(): AutofillRepository {
    return try {
        getKoin().get()
    } catch (e: Exception) {
        AutofillDI.provideRepository(this)
    }
}

/**
 * 获取自动填充缓存
 * @deprecated 使用 Koin 注入: `val cache: AutofillCache by inject()`
 */
@Deprecated("使用 Koin 注入替代")
fun Context.getAutofillCache(): AutofillCache {
    return try {
        getKoin().get()
    } catch (e: Exception) {
        AutofillDI.provideCache()
    }
}

