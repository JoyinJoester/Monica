# 🎯 资产余额保存问题 - 快速修复总结

## 问题
用户在资产管理页面设置余额后，保存无效，余额始终显示为 0。

## 根本原因
**核心问题**：`recalculateAllAssetBalances()` 在页面加载时自动执行，将所有资产余额重置为 0，然后只根据账单记录重新计算。

**问题流程**：
1. 用户设置余额 → 2. 保存成功 → 3. 返回页面 → 4. **自动重新计算余额为 0** ❌

## 解决方案

### 1️⃣ 修复保存逻辑（AddEditAssetScreen.kt）
```kotlin
// 使用 copy() 保留原有字段
val asset = if (existingAsset != null) {
    existingAsset!!.copy(
        balanceInCents = balanceInCents,
        updatedAt = Date()
    )
} else {
    Asset(/* 新建 */)
}
```

### 2️⃣ 移除自动重新计算（关键修复）⭐
**AssetManagementScreen.kt** 和 **LedgerScreen.kt**：
```kotlin
// 注释掉这行，避免覆盖用户设置
// viewModel.recalculateAllAssetBalances()
```

## 修改的文件
1. ✅ `AddEditAssetScreen.kt` - 保存逻辑
2. ✅ `AssetManagementScreen.kt` - 移除自动重算
3. ✅ `LedgerScreen.kt` - 移除自动重算
4. ✅ `LedgerViewModel.kt` - 添加日志
5. ✅ `LedgerRepository.kt` - 添加日志

## 测试步骤
1. 打开资产管理
2. 编辑任意资产
3. 设置余额（如 1000 元）
4. 点击保存
5. 返回列表页面
6. ✅ 余额应该正确显示为 1000 元

## 部署状态
✅ 已编译并安装到测试设备，等待验证

---
**重要**：余额现在支持手动设置，不会被自动覆盖。账单记录仍会正常增减余额。
