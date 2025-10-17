# 自动填充新架构集成指南

## 📋 概述

本文档指导如何将新的自动填充架构集成到现有的 `MonicaAutofillService` 中。

---

## 🎯 集成目标

- ✅ 保持向后兼容,不破坏现有功能
- ✅ 逐步替换现有实现
- ✅ 提升性能和准确率
- ✅ 增强日志和监控能力

---

## 📦 新增文件清单

### 核心组件
1. `autofill/core/AutofillLogger.kt` - 日志系统
2. `autofill/core/MetricsCollector.kt` - 性能监控
3. `autofill/core/ErrorRecoveryManager.kt` - 错误处理

### 数据层
4. `autofill/data/AutofillDataModels.kt` - 数据模型
5. `autofill/data/AutofillRepository.kt` - 数据仓库

### 策略层
6. `autofill/strategy/MatchingStrategy.kt` - 匹配策略

### 业务层
7. `autofill/engine/AutofillEngine.kt` - 核心引擎

### 依赖注入
8. `autofill/di/AutofillDI.kt` - 依赖容器

---

## 🔧 集成步骤

### 步骤 1: 在 MonicaAutofillService 中初始化引擎

**位置**: `MonicaAutofillService.kt` 的 `onCreate()` 方法

**修改前**:
```kotlin
class MonicaAutofillService : AutofillService() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var passwordRepository: PasswordRepository
    private lateinit var autofillPreferences: AutofillPreferences
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            val database = PasswordDatabase.getDatabase(applicationContext)
            passwordRepository = PasswordRepository(database.passwordEntryDao())
            autofillPreferences = AutofillPreferences(applicationContext)
            // ...
        } catch (e: Exception) {
            android.util.Log.e("MonicaAutofill", "Error initializing service", e)
        }
    }
}
```

**修改后**:
```kotlin
import takagi.ru.monica.autofill.di.AutofillDI
import takagi.ru.monica.autofill.engine.AutofillEngine
import takagi.ru.monica.autofill.core.AutofillLogger
import takagi.ru.monica.autofill.core.AutofillLogCategory

class MonicaAutofillService : AutofillService() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 保留旧的实例作为备用
    private lateinit var passwordRepository: PasswordRepository
    private lateinit var autofillPreferences: AutofillPreferences
    
    // ✨ 新增: 自动填充引擎
    private lateinit var autofillEngine: AutofillEngine
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            // 初始化旧的组件(暂时保留)
            val database = PasswordDatabase.getDatabase(applicationContext)
            passwordRepository = PasswordRepository(database.passwordEntryDao())
            autofillPreferences = AutofillPreferences(applicationContext)
            
            // ✨ 初始化新的引擎
            autofillEngine = AutofillDI.provideEngine(applicationContext)
            
            AutofillLogger.i(
                AutofillLogCategory.SERVICE,
                "自动填充服务已启动",
                mapOf("version" to "2.0")
            )
            
        } catch (e: Exception) {
            AutofillLogger.e(
                AutofillLogCategory.ERROR,
                "服务初始化失败",
                error = e
            )
        }
    }
}
```

---

### 步骤 2: 重构 onFillRequest 方法

**位置**: `MonicaAutofillService.kt` 的 `onFillRequest()` 方法

#### 2.1 创建辅助方法: 从 FillRequest 构建 AutofillContext

在类中添加新方法:

```kotlin
import takagi.ru.monica.autofill.data.AutofillContext
import android.service.autofill.FillRequest

/**
 * 从 FillRequest 构建 AutofillContext
 */
private fun buildAutofillContext(request: FillRequest): AutofillContext? {
    try {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return null
        val parsedStructure = enhancedParserV2.parse(structure)
        
        return AutofillContext(
            packageName = parsedStructure.packageName,
            domain = parsedStructure.webDomain,
            webUrl = parsedStructure.webUrl,
            appLabel = getAppLabel(parsedStructure.packageName),
            isWebView = parsedStructure.isWebView,
            detectedFields = parsedStructure.items.map { it.hint.name },
            metadata = mapOf(
                "requestId" to request.id,
                "confidence" to parsedStructure.confidence
            )
        )
    } catch (e: Exception) {
        AutofillLogger.e(
            AutofillLogCategory.PARSING,
            "构建上下文失败",
            error = e
        )
        return null
    }
}

/**
 * 获取应用标签
 */
private fun getAppLabel(packageName: String): String {
    return appInfoCache.getOrPut(packageName) {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
```

#### 2.2 重构主要填充逻辑

**修改前** (简化版):
```kotlin
override fun onFillRequest(
    request: FillRequest,
    cancellationSignal: CancellationSignal,
    callback: FillCallback
) {
    serviceScope.launch {
        try {
            val structure = request.fillContexts.lastOrNull()?.structure
            val parsedStructure = enhancedParserV2.parse(structure)
            
            // 搜索密码
            val passwords = searchPasswords(parsedStructure.webDomain, parsedStructure.packageName)
            
            // 构建响应
            val response = buildFillResponse(...)
            callback.onSuccess(response)
            
        } catch (e: Exception) {
            Log.e("MonicaAutofill", "Fill failed", e)
            callback.onFailure(e.message)
        }
    }
}
```

**修改后**:
```kotlin
import takagi.ru.monica.autofill.core.PerformanceTracker
import takagi.ru.monica.autofill.core.MetricsCollector
import takagi.ru.monica.autofill.data.PasswordMatch

override fun onFillRequest(
    request: FillRequest,
    cancellationSignal: CancellationSignal,
    callback: FillCallback
) {
    serviceScope.launch {
        val requestTracker = PerformanceTracker("onFillRequest")
        
        try {
            AutofillLogger.i(
                AutofillLogCategory.REQUEST,
                "收到填充请求",
                mapOf("requestId" to request.id)
            )
            
            // 1. 构建上下文
            val context = buildAutofillContext(request)
            if (context == null) {
                AutofillLogger.w(AutofillLogCategory.REQUEST, "无法构建上下文")
                callback.onFailure("无法解析请求")
                return@launch
            }
            
            // 2. 使用新引擎处理请求
            val result = autofillEngine.processRequest(context)
            
            if (!result.isSuccess || !result.hasMatches()) {
                AutofillLogger.w(
                    AutofillLogCategory.REQUEST,
                    "未找到匹配的密码",
                    mapOf("context" to context.getDisplayName())
                )
                callback.onSuccess(null) // 返回空响应
                return@launch
            }
            
            // 3. 构建填充响应
            val structure = request.fillContexts.lastOrNull()?.structure!!
            val parsedStructure = enhancedParserV2.parse(structure)
            val response = buildFillResponseFromMatches(
                result.matches,
                parsedStructure,
                request
            )
            
            // 4. 记录成功
            val duration = requestTracker.finish()
            MetricsCollector.recordSuccess(duration)
            
            AutofillLogger.i(
                AutofillLogCategory.REQUEST,
                "填充响应已发送",
                mapOf(
                    "matchCount" to result.matches.size,
                    "duration_ms" to duration
                )
            )
            
            callback.onSuccess(response)
            
        } catch (e: Exception) {
            AutofillLogger.e(
                AutofillLogCategory.ERROR,
                "填充请求处理失败",
                error = e,
                metadata = mapOf("requestId" to request.id)
            )
            
            MetricsCollector.recordFailure(e::class.simpleName ?: "Unknown")
            callback.onFailure(e.message)
        }
    }
}
```

---

### 步骤 3: 添加新的响应构建方法

```kotlin
import android.service.autofill.FillResponse
import android.service.autofill.Dataset
import android.view.autofill.AutofillValue

/**
 * 从匹配结果构建填充响应
 */
private fun buildFillResponseFromMatches(
    matches: List<PasswordMatch>,
    parsedStructure: ParsedStructure,
    request: FillRequest
): FillResponse {
    val responseBuilder = FillResponse.Builder()
    
    // 为每个匹配创建一个 Dataset
    matches.forEach { match ->
        val dataset = buildDatasetFromMatch(match, parsedStructure)
        responseBuilder.addDataset(dataset)
    }
    
    // 添加认证Intent (如果需要生物识别)
    if (autofillPreferences.isRequireBiometricAuth()) {
        val authIntent = createBiometricAuthIntent()
        responseBuilder.setAuthentication(
            parsedStructure.items.map { it.autofillId }.toTypedArray(),
            authIntent,
            createAuthPresentation(matches.size)
        )
    }
    
    return responseBuilder.build()
}

/**
 * 从单个匹配创建 Dataset
 */
private fun buildDatasetFromMatch(
    match: PasswordMatch,
    parsedStructure: ParsedStructure
): Dataset {
    val datasetBuilder = Dataset.Builder()
    
    val entry = match.entry
    
    // 设置用户名字段
    parsedStructure.items
        .filter { it.hint == FieldHint.USERNAME || it.hint == FieldHint.EMAIL }
        .forEach { item ->
            datasetBuilder.setValue(
                item.autofillId,
                AutofillValue.forText(entry.username)
            )
        }
    
    // 设置密码字段
    parsedStructure.items
        .filter { it.hint == FieldHint.PASSWORD }
        .forEach { item ->
            datasetBuilder.setValue(
                item.autofillId,
                AutofillValue.forText(entry.password)
            )
        }
    
    // 设置展示
    val presentation = createPresentation(entry, match)
    datasetBuilder.setPresentation(presentation)
    
    // 设置内联建议 (Android 11+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val inlinePresentation = createInlinePresentation(entry, match, request)
        inlinePresentation?.let { datasetBuilder.setInlinePresentation(it) }
    }
    
    return datasetBuilder.build()
}

/**
 * 创建展示视图
 */
private fun createPresentation(entry: PasswordEntry, match: PasswordMatch): RemoteViews {
    val presentation = RemoteViews(packageName, R.layout.autofill_item)
    
    // 标题: 显示分数指示器
    val title = when {
        match.score >= 90 -> "⭐ ${entry.title}"
        match.score >= 75 -> "✓ ${entry.title}"
        else -> entry.title
    }
    presentation.setTextViewText(R.id.autofill_title, title)
    
    // 用户名
    presentation.setTextViewText(R.id.autofill_username, entry.username)
    
    return presentation
}
```

---

### 步骤 4: 添加日志埋点到关键路径

在现有代码的关键位置添加日志:

```kotlin
// 解析结构时
val parsedStructure = try {
    val result = enhancedParserV2.parse(structure)
    AutofillLogger.d(
        AutofillLogCategory.PARSING,
        "结构解析成功",
        mapOf(
            "packageName" to result.packageName,
            "fieldCount" to result.items.size,
            "confidence" to result.confidence
        )
    )
    result
} catch (e: Exception) {
    AutofillLogger.e(
        AutofillLogCategory.PARSING,
        "结构解析失败",
        error = e
    )
    throw e
}

// 生物识别验证时
AutofillLogger.d(
    AutofillLogCategory.USER_ACTION,
    "请求生物识别验证"
)

// 用户选择密码时
AutofillLogger.i(
    AutofillLogCategory.USER_ACTION,
    "用户选择了密码",
    mapOf("entryId" to entry.id)
)
```

---

### 步骤 5: 在 onDestroy 中清理资源

```kotlin
override fun onDestroy() {
    super.onDestroy()
    
    // 清理协程
    serviceScope.cancel()
    
    // 清理缓存
    appInfoCache.clear()
    
    // 停止SMS Retriever
    smsRetrieverHelper?.stopSmsRetriever()
    smsRetrieverHelper = null
    
    // ✨ 新增: 导出日志统计
    AutofillLogger.i(
        AutofillLogCategory.SERVICE,
        "自动填充服务已停止",
        mapOf("stats" to MetricsCollector.getFormattedStats())
    )
}
```

---

## 🧪 测试建议

### 单元测试

创建 `MonicaAutofillServiceTest.kt`:

```kotlin
import org.junit.Test
import org.junit.Before
import kotlinx.coroutines.runBlocking

class MonicaAutofillServiceTest {
    
    private lateinit var service: MonicaAutofillService
    
    @Before
    fun setup() {
        service = MonicaAutofillService()
        // 注入 Mock 对象
    }
    
    @Test
    fun testBuildAutofillContext() = runBlocking {
        // 测试上下文构建
    }
    
    @Test
    fun testProcessRequest() = runBlocking {
        // 测试请求处理
    }
}
```

### 集成测试

1. **手动测试常用应用**
   - 微信
   - 支付宝
   - 淘宝
   - Chrome 浏览器

2. **性能测试**
   - 记录响应时间
   - 检查内存占用
   - 验证缓存效果

3. **边界测试**
   - 无网络环境
   - 无匹配密码
   - 多个匹配结果
   - WebView vs 原生应用

---

## 📊 验证清单

集成完成后,验证以下功能:

- [ ] 服务启动正常,无崩溃
- [ ] 日志正常输出到 Logcat
- [ ] 能够正确匹配密码
- [ ] RemoteViews 正常显示
- [ ] 内联建议正常工作 (Android 11+)
- [ ] 生物识别验证正常
- [ ] 性能无明显下降
- [ ] 导出日志功能正常
- [ ] 统计数据准确

---

## 🔄 回滚策略

如果出现严重问题,可以快速回滚:

### 方法 1: 注释掉新代码

```kotlin
// 暂时禁用新引擎
// autofillEngine = AutofillDI.provideEngine(applicationContext)

// 继续使用旧的逻辑
```

### 方法 2: 特性开关

添加配置开关:

```kotlin
private val useNewEngine = false // 设置为 false 禁用新架构

override fun onFillRequest(...) {
    if (useNewEngine) {
        // 新逻辑
        processRequestWithNewEngine(...)
    } else {
        // 旧逻辑
        processRequestLegacy(...)
    }
}
```

---

## 📝 注意事项

1. **逐步迁移**: 不要一次性替换所有代码
2. **充分测试**: 每个步骤都要充分测试
3. **保留日志**: 保留足够的日志方便排查问题
4. **监控性能**: 持续关注性能指标
5. **用户反馈**: 收集用户反馈及时调整

---

## 📞 支持

遇到问题请参考:
- [自动填充优化计划](./AUTOFILL_ENHANCEMENT_PLAN.md)
- [实施进度报告](./AUTOFILL_OPTIMIZATION_PROGRESS.md)
- [错误修复指南](./AUTOFILL_ERROR_FIX_GUIDE.md)

---

**更新日期**: 2025-10-17  
**版本**: 1.0
