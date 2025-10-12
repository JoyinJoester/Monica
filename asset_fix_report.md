# 资产管理金额保存问题修复报告

## 🐛 问题描述
**症状**: 在资产管理页面编辑资产金额后，保存无效，余额始终显示为 0

## 🔍 问题分析

### 根本原因（两个关键问题）

#### 问题 1：数据保存逻辑错误 ✅ 已修复
在 `AddEditAssetScreen.kt` 中保存资产时，代码存在以下问题：

1. **创建了全新的 Asset 对象**：即使在编辑现有资产时，也使用构造函数创建了一个全新的对象
2. **丢失了重要字段**：`createdAt` 被设置为当前时间（`Date()`），导致原有的创建时间丢失
3. **可能的其他字段丢失**：`isActive`、`sortOrder` 等字段可能未正确保留

#### 问题 2：自动重新计算余额覆盖用户设置 ⚠️ 核心问题
**这是导致余额始终为 0 的主要原因！**

在以下页面中，`LaunchedEffect(Unit)` 会自动调用 `recalculateAllAssetBalances()`：
- `AssetManagementScreen.kt` - 资产管理页面
- `LedgerScreen.kt` - 账本页面

`recalculateAllAssetBalances()` 方法的行为：
```kotlin
suspend fun recalculateAllAssetBalances() {
    // 1. 重置所有资产余额为 0 ❌
    allAssets.forEach { asset ->
        ledgerDao.updateAssetBalance(asset.id, -asset.balanceInCents)
    }
    
    // 2. 只根据账单条目重新计算余额
    allEntries.forEach { entryWithRelations ->
        updateAssetBalanceForEntry(entryWithRelations.entry, revert = false)
    }
}
```

**问题流程**：
1. 用户设置资产余额为 1000 元
2. 保存成功，余额写入数据库
3. 返回资产管理页面
4. `LaunchedEffect(Unit)` 触发
5. `recalculateAllAssetBalances()` 被调用
6. **所有资产余额被重置为 0** ❌
7. 只根据账单记录重新计算（如果没有账单，余额就是 0）

### 原始代码问题
```kotlin
// 问题代码：总是创建新对象，丢失了原有的字段
val asset = Asset(
    id = assetId ?: 0,
    name = displayName,
    assetType = assetType,
    balanceInCents = balanceInCents,
    currencyCode = currencyCode,
    iconKey = iconKey,
    colorHex = color,
    createdAt = Date(),  // ❌ 总是设置为当前时间
    updatedAt = Date()
)
```

## ✅ 解决方案

### 修复 1：使用 copy() 方法保存资产数据

1. **添加状态变量保存完整的资产对象**
```kotlin
var existingAsset by remember { mutableStateOf<Asset?>(null) }
```

2. **在 LaunchedEffect 中保存完整对象**
```kotlin
LaunchedEffect(assetId) {
    if (assetId != null && assetId > 0) {
        viewModel.getAssetById(assetId)?.let { asset ->
            existingAsset = asset // 保存完整对象
            name = asset.name
            assetType = asset.assetType
            balance = (asset.balanceInCents / 100.0).toString()
            currencyCode = asset.currencyCode
        }
    }
}
```

3. **使用 copy() 方法更新现有资产**
```kotlin
val asset = if (existingAsset != null) {
    // 编辑现有资产，使用 copy() 保留所有原有字段
    existingAsset!!.copy(
        name = displayName,
        assetType = assetType,
        balanceInCents = balanceInCents,
        currencyCode = currencyCode,
        iconKey = iconKey,
        colorHex = color,
        updatedAt = Date()  // 只更新 updatedAt
    )
} else {
    // 创建新资产
    Asset(/* ... */)
}
```

### 修复 2：移除自动重新计算余额（核心修复）⭐

**AssetManagementScreen.kt**
```kotlin
// 移除自动重新计算，避免覆盖用户手动设置的余额
// LaunchedEffect(Unit) {
//     viewModel.recalculateAllAssetBalances()
// }
```

**LedgerScreen.kt**
```kotlin
LaunchedEffect(Unit) {
    viewModel.initializeDefaultAssets()
    // 移除自动重新计算，避免覆盖用户手动设置的余额
    // viewModel.recalculateAllAssetBalances()
}
```

### 修复的优势

✅ **保留所有原有字段**：使用 `copy()` 方法确保所有未显式修改的字段保持不变  
✅ **正确的时间戳**：`createdAt` 保持原值，只更新 `updatedAt`  
✅ **更清晰的逻辑**：区分"创建"和"编辑"两种情况  
✅ **尊重用户设置**：不再自动覆盖用户手动设置的余额  
✅ **保持数据一致性**：余额在保存后能够正确保持  
✅ **添加调试日志**：便于追踪问题  

## 🧪 测试验证

### 测试步骤
1. ✅ 打开 Monica 应用
2. ✅ 进入资产管理页面
3. ✅ 点击现有资产进行编辑
4. ✅ 修改余额金额
5. ✅ 点击保存
6. ✅ 返回资产管理页面验证金额是否更新

### 预期结果
- ✅ 金额应该正确保存并显示
- ✅ 其他字段（名称、类型、货币等）保持不变
- ✅ 创建时间不变，更新时间更新为当前时间

## 📝 相关文件

### 修改的文件
1. `app/src/main/java/takagi/ru/monica/ui/screens/AddEditAssetScreen.kt` - 保存逻辑修复
2. `app/src/main/java/takagi/ru/monica/ui/screens/AssetManagementScreen.kt` - 移除自动重新计算
3. `app/src/main/java/takagi/ru/monica/ui/screens/LedgerScreen.kt` - 移除自动重新计算
4. `app/src/main/java/takagi/ru/monica/viewmodel/LedgerViewModel.kt` - 添加调试日志
5. `app/src/main/java/takagi/ru/monica/repository/LedgerRepository.kt` - 添加调试日志

### 关键代码位置
- **AddEditAssetScreen 状态变量**: 第 48 行
- **AddEditAssetScreen 加载逻辑**: 第 50-63 行
- **AddEditAssetScreen 保存逻辑**: 第 89-140 行
- **AssetManagementScreen**: 第 43-47 行（注释掉的代码）
- **LedgerScreen**: 第 82-86 行（注释掉的代码）

## 🔧 技术细节

### Kotlin Data Class 的 copy() 方法
```kotlin
data class Asset(
    val id: Long,
    val name: String,
    // ... 其他字段
)

// copy() 方法的优势：
// 1. 只修改指定的字段
// 2. 其他字段自动复制原值
// 3. 类型安全
val updated = original.copy(name = "新名称")
```

### Room 数据库更新
```kotlin
// Repository 层
suspend fun upsertAsset(asset: Asset): Long {
    return if (asset.id == 0L) {
        ledgerDao.insertAsset(asset)  // 新建
    } else {
        ledgerDao.updateAsset(asset)  // 更新
        asset.id
    }
}

// DAO 层
@Update
suspend fun updateAsset(asset: Asset)
```

## 🎯 最佳实践建议

1. **编辑实体时使用 copy()**：对于 data class，始终使用 `copy()` 方法而不是构造函数
2. **保存完整对象**：在编辑场景中，保存原始对象的引用，而不只是单独的字段
3. **区分创建和编辑**：用明确的逻辑分支处理两种情况
4. **谨慎使用自动重新计算**：避免在页面加载时自动重新计算，这可能覆盖用户的手动设置
5. **明确余额更新时机**：
   - ✅ 添加/删除账单时自动更新资产余额
   - ✅ 用户手动编辑资产余额时保存
   - ❌ 不在页面加载时自动重新计算
6. **添加调试日志**：在关键位置添加日志，便于排查问题

## 💡 设计思考

### 资产余额的两种更新方式

#### 方式 1：基于账单自动计算（当前使用）
- **优点**：余额自动反映所有交易记录，保证数据一致性
- **缺点**：无法设置初始余额，必须通过账单记录

#### 方式 2：允许手动设置 + 账单增量更新（本次修复采用）
- **优点**：可以设置初始余额，灵活性更高
- **缺点**：需要防止自动重新计算覆盖用户设置

### 推荐的余额管理策略

1. **初始设置**：用户可以手动设置资产的初始余额
2. **增量更新**：添加账单时，使用 `updateAssetBalance(assetId, amountInCents)` 进行增量更新
3. **避免全量重算**：不要频繁调用 `recalculateAllAssetBalances()`
4. **提供重算选项**：在设置中提供"重新计算所有资产余额"选项，供用户手动触发

## 📊 修复前后对比

### 修复前 ❌
```kotlin
// 问题：总是创建新对象
val asset = Asset(
    id = assetId ?: 0,
    // ... 所有字段都重新设置
    createdAt = Date()  // ❌ 丢失原始创建时间
)
```

### 修复后 ✅
```kotlin
// 解决：区分创建和编辑
val asset = if (existingAsset != null) {
    existingAsset!!.copy(
        balanceInCents = balanceInCents,
        // 只修改需要更新的字段
        updatedAt = Date()
    )
} else {
    Asset(/* 创建新资产 */)
}
```

## 🚀 部署状态
- ✅ 代码已修复（问题 1 和问题 2）
- ✅ 编译成功
- ✅ APK 已安装到测试设备
- ✅ 添加详细调试日志
- ⏳ 等待用户验证

## ⚠️ 注意事项

1. **余额计算逻辑变更**：移除了页面加载时的自动重新计算，资产余额现在支持手动设置
2. **数据迁移**：如果之前有通过账单记录计算的余额，建议手动检查并调整
3. **功能建议**：可以考虑在设置中添加"重新计算所有资产余额"按钮，供用户手动触发

---
**修复时间**: 2025-10-12  
**修复人员**: AI Assistant  
**问题级别**: 🔴 高优先级（核心功能缺陷）  
**影响范围**: 资产管理模块  
**修复版本**: v2 - 完整修复（包含自动重新计算问题）
