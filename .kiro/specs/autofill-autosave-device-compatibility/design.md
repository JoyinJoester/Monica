# 自动保存设备兼容性设计文档

## 概述

本文档描述了 Monica 密码管理器自动保存功能的设计，重点解决在不同 Android 设备（特别是国产 ROM）上的兼容性问题。当前代码已经实现了基础的 `onSaveRequest` 功能，但在某些设备上无法正常触发，需要针对性优化。

## 架构

### 当前架构分析

```
用户提交表单
    ↓
系统检测到表单提交
    ↓
检查 FillResponse 中是否有 SaveInfo ← 关键点1
    ↓
如果有 SaveInfo，触发 onSaveRequest
    ↓
MonicaAutofillService.onSaveRequest()
    ↓
processSaveRequest() 提取数据
    ↓
启动 AutofillSaveTransparentActivity
    ↓
用户确认保存
```

### 问题根源分析

根据代码审查，发现以下潜在问题：

1. **SaveInfo 配置问题**
   - 当前代码在 `buildStandardResponse()` 中配置了 SaveInfo
   - 使用了 `FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE` 标志
   - 但在 `buildFillResponseEnhanced()` 中没有明确看到 SaveInfo 配置

2. **设备特定标志问题**
   - 不同 ROM 对 `FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE` 的支持不同
   - 某些设备可能需要不同的标志组合

3. **字段 ID 收集问题**
   - SaveInfo 需要正确的字段 ID 数组
   - 如果字段 ID 为空，SaveInfo 不会生效

## 组件和接口

### 1. SaveInfoBuilder (新增)

负责根据设备类型构建适配的 SaveInfo 配置。

```kotlin
/**
 * SaveInfo 构建器
 * 根据设备类型和 ROM 版本构建适配的 SaveInfo
 */
object SaveInfoBuilder {
    
    /**
     * 构建 SaveInfo
     * 
     * @param parsedStructure 解析的表单结构
     * @param deviceInfo 设备信息
     * @return SaveInfo 或 null（如果不应该保存）
     */
    fun build(
        parsedStructure: ParsedStructure,
        deviceInfo: DeviceInfo
    ): SaveInfo?
    
    /**
     * 获取设备特定的 SaveInfo 标志
     */
    private fun getDeviceSpecificFlags(deviceInfo: DeviceInfo): Int
    
    /**
     * 收集需要保存的字段 ID
     */
    private fun collectSaveFieldIds(parsedStructure: ParsedStructure): Array<AutofillId>
    
    /**
     * 验证 SaveInfo 配置是否有效
     */
    private fun validateSaveInfo(saveInfo: SaveInfo): Boolean
}
```

### 2. DeviceInfo (扩展现有 DeviceUtils)

封装设备信息，用于决策。

```kotlin
data class DeviceInfo(
    val manufacturer: DeviceUtils.Manufacturer,
    val romType: DeviceUtils.ROMType,
    val romVersion: String,
    val androidVersion: Int,
    val supportsInlineSuggestions: Boolean
) {
    /**
     * 是否支持延迟保存提示
     */
    fun supportsDelayedSavePrompt(): Boolean
    
    /**
     * 推荐的 SaveInfo 标志
     */
    fun getRecommendedSaveFlags(): Int
    
    /**
     * 是否需要自定义保存 UI
     */
    fun needsCustomSaveUI(): Boolean
}
```

### 3. SaveRequestProcessor (重构现有逻辑)

处理保存请求的核心逻辑，从 `MonicaAutofillService` 中提取。

```kotlin
/**
 * 保存请求处理器
 * 负责处理 onSaveRequest 的核心逻辑
 */
class SaveRequestProcessor(
    private val context: Context,
    private val passwordRepository: PasswordRepository,
    private val preferences: AutofillPreferences,
    private val diagnostics: AutofillDiagnostics
) {
    
    /**
     * 处理保存请求
     * 
     * @param request SaveRequest
     * @return ProcessResult 处理结果
     */
    suspend fun process(request: SaveRequest): ProcessResult
    
    /**
     * 提取表单数据
     */
    private suspend fun extractFormData(request: SaveRequest): SaveData?
    
    /**
     * 验证数据
     */
    private fun validateData(saveData: SaveData): ValidationResult
    
    /**
     * 检查重复
     */
    private suspend fun checkDuplicate(saveData: SaveData): DuplicateCheckResult
    
    /**
     * 创建保存 Intent
     */
    private fun createSaveIntent(saveData: SaveData, duplicateResult: DuplicateCheckResult): Intent
    
    sealed class ProcessResult {
        data class Success(val intent: Intent) : ProcessResult()
        data class Skip(val reason: String) : ProcessResult()
        data class Error(val message: String, val exception: Exception?) : ProcessResult()
    }
}
```

### 4. AutofillDiagnostics (扩展)

添加保存功能的诊断能力。

```kotlin
// 扩展现有的 AutofillDiagnostics 类

/**
 * 记录保存请求
 */
fun logSaveRequest(
    packageName: String,
    domain: String?,
    hasUsername: Boolean,
    hasPassword: Boolean,
    isNewPasswordScenario: Boolean
)

/**
 * 记录保存结果
 */
fun logSaveResult(
    success: Boolean,
    action: String, // "created", "updated", "skipped"
    reason: String?,
    processingTimeMs: Long
)

/**
 * 记录 SaveInfo 配置
 */
fun logSaveInfoConfig(
    deviceInfo: DeviceInfo,
    flags: Int,
    fieldCount: Int,
    saveType: Int
)

/**
 * 获取保存功能统计
 */
fun getSaveStatistics(): SaveStatistics

data class SaveStatistics(
    val totalSaveRequests: Int,
    val successfulSaves: Int,
    val skippedSaves: Int,
    val failedSaves: Int,
    val averageProcessingTime: Long,
    val deviceSpecificIssues: Map<String, Int>
)
```

## 数据模型

### SaveInfo 标志配置

不同设备的推荐标志配置：

```kotlin
object SaveInfoFlags {
    // 标准配置（原生 Android）
    const val STANDARD = SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE
    
    // 小米 MIUI/HyperOS
    const val XIAOMI = 0 // 不使用任何标志
    
    // 华为 EMUI/HarmonyOS
    const val HUAWEI = SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE
    
    // OPPO ColorOS
    const val OPPO = SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE
    
    // Vivo OriginOS
    const val VIVO = 0 // 不使用任何标志
    
    // 三星 One UI
    const val SAMSUNG = SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE
}
```

### 设备兼容性矩阵

| 设备品牌 | ROM 类型 | Android 版本 | SaveInfo 标志 | 是否支持 | 备注 |
|---------|---------|-------------|--------------|---------|------|
| 小米 | MIUI 12+ | 11+ | 无标志 | ✅ | 需要移除 FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE |
| 小米 | HyperOS | 14+ | 无标志 | ✅ | 同 MIUI |
| 华为 | EMUI 10+ | 10+ | STANDARD | ⚠️ | 部分设备需要用户手动启用 |
| 华为 | HarmonyOS | 10+ | STANDARD | ⚠️ | 兼容性有限 |
| OPPO | ColorOS 11+ | 11+ | STANDARD | ✅ | 支持良好 |
| Vivo | OriginOS | 11+ | 无标志 | ⚠️ | 需要特殊处理 |
| 三星 | One UI 3+ | 11+ | STANDARD | ✅ | 完美支持 |
| Google | Stock Android | 8+ | STANDARD | ✅ | 完美支持 |

## 错误处理

### 错误类型

1. **SaveInfo 未触发**
   - 原因：标志配置不当
   - 解决：根据设备类型调整标志

2. **字段 ID 无效**
   - 原因：字段解析失败或 ID 为空
   - 解决：增强字段解析，添加验证

3. **Activity 启动失败**
   - 原因：Intent 标志不正确
   - 解决：确保使用 FLAG_ACTIVITY_NEW_TASK

4. **数据提取失败**
   - 原因：AssistStructure 中没有 AutofillValue
   - 解决：添加后备提取方法

### 错误恢复策略

```kotlin
/**
 * 错误恢复策略
 */
sealed class RecoveryStrategy {
    /**
     * 重试：使用不同的标志配置
     */
    data class Retry(val alternativeFlags: Int) : RecoveryStrategy()
    
    /**
     * 降级：使用简化的保存流程
     */
    object Fallback : RecoveryStrategy()
    
    /**
     * 跳过：记录错误但不影响用户
     */
    data class Skip(val reason: String) : RecoveryStrategy()
    
    /**
     * 通知：显示错误信息给用户
     */
    data class Notify(val message: String) : RecoveryStrategy()
}
```

## 测试策略

### 单元测试

1. **SaveInfoBuilder 测试**
   ```kotlin
   @Test
   fun `test SaveInfo flags for MIUI devices`()
   
   @Test
   fun `test SaveInfo flags for HarmonyOS devices`()
   
   @Test
   fun `test field ID collection`()
   
   @Test
   fun `test SaveInfo validation`()
   ```

2. **SaveRequestProcessor 测试**
   ```kotlin
   @Test
   fun `test extract form data with valid structure`()
   
   @Test
   fun `test extract form data with missing fields`()
   
   @Test
   fun `test duplicate detection`()
   
   @Test
   fun `test save intent creation`()
   ```

3. **DeviceInfo 测试**
   ```kotlin
   @Test
   fun `test device detection for various manufacturers`()
   
   @Test
   fun `test recommended save flags`()
   
   @Test
   fun `test delayed save prompt support`()
   ```

### 集成测试

1. **端到端保存流程测试**
   - 在测试应用中填写表单
   - 提交表单
   - 验证 onSaveRequest 被触发
   - 验证保存 UI 显示
   - 验证数据正确保存

2. **设备特定测试**
   - 在小米设备上测试（MIUI/HyperOS）
   - 在华为设备上测试（EMUI/HarmonyOS）
   - 在 OPPO 设备上测试（ColorOS）
   - 在 Vivo 设备上测试（OriginOS）
   - 在三星设备上测试（One UI）
   - 在原生 Android 设备上测试

### 手动测试清单

- [ ] 在原生 Android 虚拟机上测试保存功能
- [ ] 在小米真机上测试保存功能
- [ ] 在华为真机上测试保存功能
- [ ] 在 OPPO 真机上测试保存功能
- [ ] 在 Vivo 真机上测试保存功能
- [ ] 测试新密码场景（注册）
- [ ] 测试密码更新场景
- [ ] 测试重复密码检测
- [ ] 测试保存 UI 的取消操作
- [ ] 测试保存 UI 的确认操作
- [ ] 验证诊断日志记录

## 性能考虑

### 优化点

1. **SaveInfo 构建缓存**
   - 缓存设备信息，避免重复检测
   - 缓存标志配置

2. **字段 ID 收集优化**
   - 使用增强解析器的结果，避免重复解析
   - 提前过滤无效字段

3. **异步处理**
   - 保存请求处理使用协程
   - 避免阻塞主线程

4. **内存管理**
   - 及时释放 AssistStructure 引用
   - 避免内存泄漏

### 性能指标

- SaveInfo 构建时间：< 10ms
- 保存请求处理时间：< 100ms
- Activity 启动时间：< 200ms
- 总体保存流程：< 500ms

## 安全考虑

### 数据保护

1. **敏感数据处理**
   - 密码在内存中的时间最小化
   - 使用加密存储

2. **Intent 安全**
   - 使用显式 Intent
   - 验证 Intent 来源

3. **日志安全**
   - 不记录完整密码
   - 只记录密码长度和掩码

### 权限验证

1. **自动填充服务权限**
   - 验证服务已启用
   - 验证服务有权限访问表单数据

2. **存储权限**
   - 验证有权限写入数据库

## 诊断和调试

### 日志级别

```kotlin
enum class SaveLogLevel {
    VERBOSE,  // 详细日志，包括所有步骤
    DEBUG,    // 调试日志，包括关键决策点
    INFO,     // 信息日志，包括成功/失败结果
    WARNING,  // 警告日志，包括兼容性问题
    ERROR     // 错误日志，包括异常
}
```

### 诊断信息

在诊断报告中添加保存功能部分：

```
=== 自动保存功能 ===
状态: 已启用
SaveInfo 配置:
  - 标志: FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE
  - 字段数量: 2
  - 保存类型: USERNAME | PASSWORD

统计信息:
  - 总保存请求: 15
  - 成功保存: 12
  - 跳过保存: 2
  - 失败保存: 1
  - 平均处理时间: 85ms

设备兼容性:
  - 制造商: Xiaomi
  - ROM: MIUI 14
  - 推荐标志: 无标志
  - 当前标志: FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE ⚠️
  - 建议: 移除 FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE 以提高兼容性

最近的保存请求:
  1. [2024-01-15 10:30:25] 成功 - com.example.app (85ms)
  2. [2024-01-15 10:25:10] 跳过 - 完全重复
  3. [2024-01-15 10:20:05] 失败 - 字段提取失败
```

## 实现优先级

### Phase 1: 核心修复（高优先级）

1. 创建 SaveInfoBuilder
2. 实现设备特定标志配置
3. 修复 buildFillResponseEnhanced 中的 SaveInfo 配置
4. 添加字段 ID 验证

### Phase 2: 增强功能（中优先级）

1. 重构 SaveRequestProcessor
2. 增强错误处理和恢复
3. 添加诊断日志
4. 实现设备兼容性检测

### Phase 3: 优化和测试（低优先级）

1. 性能优化
2. 添加单元测试
3. 设备特定测试
4. 文档更新

## 向后兼容性

### 现有功能保护

1. **不影响现有填充功能**
   - SaveInfo 配置独立
   - 填充逻辑不变

2. **渐进式启用**
   - 通过偏好设置控制
   - 默认启用，可关闭

3. **数据迁移**
   - 无需数据迁移
   - 新功能不影响现有数据

## 未来扩展

### 可能的增强

1. **智能保存建议**
   - 基于使用频率推荐保存
   - 检测密码强度并建议改进

2. **批量保存**
   - 支持一次保存多个账号
   - 导入功能集成

3. **云同步**
   - 保存后自动同步
   - 冲突解决

4. **生物识别集成**
   - 保存前验证身份
   - 增强安全性
