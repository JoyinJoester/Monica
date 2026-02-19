package takagi.ru.monica.autofill.di

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import takagi.ru.monica.autofill.EnhancedAutofillStructureParserV2
import takagi.ru.monica.autofill.builder.AutofillDatasetBuilder
import takagi.ru.monica.autofill.data.AutofillCache
import takagi.ru.monica.autofill.data.AutofillPreferencesData
import takagi.ru.monica.autofill.data.AutofillRepository
import takagi.ru.monica.autofill.engine.AutofillEngine
import takagi.ru.monica.autofill.engine.AutofillEngineBuilder
import takagi.ru.monica.autofill.strategy.DomainMatchingStrategy
import takagi.ru.monica.autofill.strategy.FuzzyMatchingStrategy
import takagi.ru.monica.autofill.strategy.MatchingStrategy
import takagi.ru.monica.autofill.strategy.PackageNameMatchingStrategy
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.repository.PasswordRepository

/**
 * Autofill 模块的 Koin 依赖注入配置
 * 
 * 使用 Koin 的模块化设计，将自动填充相关的依赖集中管理。
 * 安全考量:
 * - 使用 single 作用域，确保敏感数据只有一个实例
 * - 懒加载设计，减少内存占用
 * - 便于测试时替换 mock 实现
 * 
 * 渐进迁移策略:
 * - 保留 AutofillDI 作为兼容层
 * - 新代码优先使用 Koin 注入
 * - AutofillDI 内部调用 Koin 获取依赖
 */
val autofillModule = module {
    
    // ==================== 基础设施层 ====================
    
    /**
     * 密码数据库
     */
    single {
        PasswordDatabase.getDatabase(androidContext())
    }
    
    /**
     * 密码仓库 (Monica 核心)
     */
    single {
        PasswordRepository(get<PasswordDatabase>().passwordEntryDao())
    }
    
    // ==================== 自动填充核心组件 ====================
    
    /**
     * 自动填充缓存
     * 用于缓存密码搜索结果，提升性能
     */
    single {
        AutofillCache()
    }
    
    /**
     * 自动填充数据仓库
     * 集成 PasswordRepository 和 AutofillCache
     */
    single {
        AutofillRepository(
            passwordRepository = get(),
            cache = get()
        )
    }
    
    /**
     * 匹配策略列表
     * 按优先级排序: Domain(100) > Package(90) > Fuzzy(50)
     */
    single<List<MatchingStrategy>> {
        listOf(
            DomainMatchingStrategy(),
            PackageNameMatchingStrategy(),
            FuzzyMatchingStrategy()
        )
    }
    
    /**
     * 用户偏好设置
     * TODO: 后续从 SharedPreferences/DataStore 读取
     */
    single {
        AutofillPreferencesData(
            enabled = true,
            requireBiometric = false,
            enableFuzzySearch = true,
            autoSubmit = false,
            maxSuggestions = 5,
            enableInlineSuggestions = true,
            enableLogging = true,
            timeoutMs = 5000
        )
    }
    
    /**
     * 自动填充引擎
     * 核心业务逻辑处理器
     */
    single {
        AutofillEngineBuilder()
            .setDataSource(get<AutofillRepository>())
            .apply {
                get<List<MatchingStrategy>>().forEach { addStrategy(it) }
            }
            .setPreferences(get())
            .build()
    }
    
    /**
     * 自动填充结构解析器
     * 负责从 AssistStructure 中提取字段信息
     */
    single { 
        EnhancedAutofillStructureParserV2() 
    }
    
    // ==================== 工具类 ====================
    
    /**
     * Dataset 构建器工厂
     * 统一处理不同 Android 版本的 Dataset 构建逻辑
     */
    single {
        AutofillDatasetBuilder
    }
}

/**
 * 自动填充会话作用域模块
 * 用于每次自动填充请求的临时依赖
 * 
 * 这些依赖在请求处理完成后自动释放，
 * 确保敏感数据不会长期驻留内存。
 */
val autofillSessionModule = module {
    // 未来可添加会话级别的依赖
    // 例如: 当前请求的解析结果缓存等
}

