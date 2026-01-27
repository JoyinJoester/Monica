package takagi.ru.monica.autofill.di

import android.content.Context
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
 * 提供单例模式的依赖管理,避免重复创建对象
 * 
 * 使用示例:
 * ```kotlin
 * val engine = AutofillDI.provideEngine(context)
 * ```
 * 
 * @author Monica Team
 * @since 1.0
 */
object AutofillDI {
    
    // ===== 单例实例 =====
    
    @Volatile
    private var engine: AutofillEngine? = null
    
    @Volatile
    private var repository: AutofillRepository? = null
    
    @Volatile
    private var cache: AutofillCache? = null
    
    // ===== 公共API =====
    
    /**
     * 提供自动填充引擎
     * 
     * @param context Android Context
     * @return AutofillEngine 单例
     */
    fun provideEngine(context: Context): AutofillEngine {
        return engine ?: synchronized(this) {
            engine ?: createEngine(context).also { 
                engine = it 
                AutofillLogger.i(
                    AutofillLogCategory.SERVICE,
                    "自动填充引擎已初始化"
                )
            }
        }
    }
    
    /**
     * 提供数据仓库
     * 
     * @param context Android Context
     * @return AutofillRepository 单例
     */
    fun provideRepository(context: Context): AutofillRepository {
        return repository ?: synchronized(this) {
            repository ?: createRepository(context).also { 
                repository = it 
                AutofillLogger.d(
                    AutofillLogCategory.SERVICE,
                    "数据仓库已初始化"
                )
            }
        }
    }
    
    /**
     * 提供缓存
     * 
     * @return AutofillCache 单例
     */
    fun provideCache(): AutofillCache {
        return cache ?: synchronized(this) {
            cache ?: AutofillCache().also { 
                cache = it 
                AutofillLogger.d(
                    AutofillLogCategory.SERVICE,
                    "缓存已初始化"
                )
            }
        }
    }
    
    /**
     * 重置所有单例
     * 
     * 用于测试或重新初始化
     */
    fun reset() {
        synchronized(this) {
            engine = null
            repository = null
            cache = null
            AutofillLogger.i(
                AutofillLogCategory.SERVICE,
                "依赖注入容器已重置"
            )
        }
    }
    
    /**
     * 清除缓存
     */
    fun clearCache() {
        repository?.clearCache()
        AutofillLogger.i(
            AutofillLogCategory.SERVICE,
            "缓存已清除"
        )
    }
    
    // ===== 私有工厂方法 =====
    
    /**
     * 创建自动填充引擎
     */
    private fun createEngine(context: Context): AutofillEngine {
        val dataSource = provideRepository(context)
        val strategies = createStrategies()
        val preferences = loadPreferences(context)
        
        return AutofillEngineBuilder()
            .setDataSource(dataSource)
            .apply {
                strategies.forEach { addStrategy(it) }
            }
            .setPreferences(preferences)
            .build()
    }
    
    /**
     * 创建数据仓库
     */
    private fun createRepository(context: Context): AutofillRepository {
        val database = PasswordDatabase.getDatabase(context.applicationContext)
        val passwordRepository = PasswordRepository(database.passwordEntryDao())
        val cache = provideCache()
        
        return AutofillRepository(passwordRepository, cache)
    }
    
    /**
     * 创建匹配策略列表
     */
    private fun createStrategies(): List<MatchingStrategy> {
        return listOf(
            DomainMatchingStrategy(),          // 优先级 100
            PackageNameMatchingStrategy(),     // 优先级 90
            FuzzyMatchingStrategy()            // 优先级 50
        )
    }
    
    /**
     * 加载用户偏好设置
     */
    private fun loadPreferences(context: Context): AutofillPreferencesData {
        // val prefs = AutofillPreferences(context)
        
        return AutofillPreferencesData(
            enabled = true,
            requireBiometric = false, // TODO: 从 SharedPreferences 读取
            enableFuzzySearch = true,
            autoSubmit = false,
            maxSuggestions = 5,
            enableInlineSuggestions = true, // TODO: 从 SharedPreferences 读取
            enableLogging = true,
            timeoutMs = 5000
        )
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
 */

/**
 * 获取自动填充引擎
 */
fun Context.getAutofillEngine(): AutofillEngine {
    return AutofillDI.provideEngine(this)
}

/**
 * 获取自动填充仓库
 */
fun Context.getAutofillRepository(): AutofillRepository {
    return AutofillDI.provideRepository(this)
}

/**
 * 获取自动填充缓存
 */
fun Context.getAutofillCache(): AutofillCache {
    return AutofillDI.provideCache()
}
