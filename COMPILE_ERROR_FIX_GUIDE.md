# 🚨 记账功能删除 - 编译错误修复指南

## 当前状态 

编译失败原因：**许多文件仍然引用已删除的记账类**

错误：`Could not load module <Error module>` - 这通常意味着有类找不到

## ✅ 已删除的文件 
- ❌ `app/src/main/java/takagi/ru/monica/repository/LedgerRepository.kt`
- ❌ `app/src/main/java/takagi/ru/monica/viewmodel/LedgerViewModel.kt`
- ❌ `app/src/main/java/takagi/ru/monica/ui/screens/LedgerScreen.kt`
- ❌ `app/src/main/java/takagi/ru/monica/ui/screens/AddEditAssetScreen.kt`
- ❌ `app/src/main/java/takagi/ru/monica/ui/screens/AssetManagementScreen.kt`
- ❌ `app/src/main/java/takagi/ru/monica/data/ledger/*` (整个文件夹)

## 🔴 仍然引用已删除类的文件 (需要修复)

### 1. MainActivity.kt - 🔴 严重
**引用问题**:
- ❌ 第 221, 291 行: `ledgerViewModel = ledgerViewModel`
- ❌ 第 227, 791 行: `ledgerRepository = ledgerRepository`  
- ❌ 第 244 行: `ledgerViewModel: takagi.ru.monica.viewmodel.LedgerViewModel`
- ❌ 第 250 行: `ledgerRepository: LedgerRepository`
- ❌ 第 378, 380, 667, 669 行: `ledgerRepository.observeEntries()` 和 `ledgerRepository.deleteEntry()`
- ❌ 第 568 行: `dataExportImportViewModel.importAlipayLedgerData(uri)` (此方法已删除)

**需要做的修改**:
1. 完全移除 SimpleMainScreen 的 ledgerViewModel 参数传递
2. 完全移除 MonicaApp 的 ledgerRepository 参数
3. 删除所有使用 ledgerRepository 的代码块（数据备份相关）
4. 删除 importAlipayLedgerData 调用

### 2. SimpleMainScreen.kt - 🔴 严重
**引用问题**:
- ❌ 第 52 行: `import takagi.ru.monica.ui.screens.LedgerScreen`
- ❌ 第 69 行: `ledgerViewModel: takagi.ru.monica.viewmodel.LedgerViewModel`
- ❌ 第 351-352 行: `LedgerScreen(viewModel = ledgerViewModel, ...)`
- ❌ 底部导航栏包含 Ledger 标签

**需要做的修改**:
1. 删除 LedgerScreen 导入
2. 移除 ledgerViewModel 参数
3. 删除 Ledger 标签页定义 (BottomNavItem.Ledger)
4. 删除 when 分支中的 LedgerScreen 内容渲染
5. 更新 Tab 索引（从 0-4 变为 0-3）

### 3. WebDavBackupScreen.kt - 🔴 严重
**引用问题**:
- ❌ 第 25 行: `import takagi.ru.monica.repository.LedgerRepository`
- ❌ 第 26 行: `import takagi.ru.monica.data.ledger.LedgerCategory`
- ❌ 第 39, 432 行: `ledgerRepository: LedgerRepository` 参数
- ❌ 第 302, 405, 590, 591, 619, 627 行: 调用 ledgerRepository 方法

**需要做的修改**:
1. 删除 LedgerRepository 和 LedgerCategory 导入
2. 移除所有函数的 ledgerRepository 参数
3. 删除所有记账相关的备份和恢复代码
4. **保留** SecureItem、PasswordEntry 的备份功能

### 4. WebDavHelper.kt - 🔴 需要修复
**引用问题**:
- ❌ 第 14-16 行: 导入 LedgerEntry, LedgerEntryType, LedgerEntryWithRelations
- ❌ 第 306 行: `importLedgerFromCSV(tempFile)`
- ❌ 第 519 行: `importLedgerFromCSV` 函数定义

**需要做的修改**:
1. 删除 ledger 相关导入
2. 删除 importLedgerFromCSV 函数
3. 删除调用 importLedgerFromCSV 的代码
4. **保留** 密码和 SecureItem 的 CSV 导入功能

### 5. DataExportImportManager.kt - ⚠️ 可能需要修复
**引用问题**:
- ❌ 第 409 行: `importAlipayLedgerData` 函数

**需要做的修改**:
1. 删除 importAlipayLedgerData 函数（整个函数）
2. 删除相关的 AlipayLedgerItem 数据类（如果有）

### 6. Converters.kt - ⚠️ 可能需要修复
**引用问题**:
- ❌ 第 5 行: `import takagi.ru.monica.data.ledger.LedgerEntryType`

**需要做的修改**:
1. 删除 LedgerEntryType 导入
2. 删除 LedgerEntryType 的类型转换器（如果有）

## 📋 修复步骤建议

### Step 1: 修复 MainActivity.kt
```kotlin
// 需要删除的部分：
- ledgerViewModel 参数和初始化
- ledgerRepository 参数传递
- MonicaApp 中的 ledgerRepository 参数
- 所有 ledgerRepository 使用（备份相关）
- importAlipayLedgerData 调用
```

### Step 2: 修复 SimpleMainScreen.kt (最大的改动)
```kotlin
// 需要删除：
1. import LedgerScreen
2. ledgerViewModel 参数
3. BottomNavItem.Ledger 定义
4. when (currentScreen) { Ledger -> LedgerScreen(...) }
5. 底部导航栏的 Ledger 标签项

// 需要更新：
- Tab 数量从 5 个改为 4 个
- selectedTabIndex 范围从 0..4 改为 0..3
```

### Step 3: 修复 WebDavBackupScreen.kt
```kotlin
// 需要删除：
- ledgerRepository 参数（所有函数）
- 所有 ledgerRepository 调用
- ledger 相关的备份/恢复逻辑

// 保留：
- SecureItem 备份
- PasswordEntry 备份
- 设置备份
```

### Step 4: 修复 WebDavHelper.kt
```kotlin
// 需要删除：
- LedgerEntry 相关导入
- importLedgerFromCSV 函数
- 调用 importLedgerFromCSV 的代码
```

### Step 5: 修复 DataExportImportManager.kt
```kotlin
// 需要删除：
- importAlipayLedgerData 函数
- AlipayLedgerItem 数据类（如果存在）
```

### Step 6: 修复 Converters.kt
```kotlin
// 需要删除：
- LedgerEntryType 导入
- LedgerEntryType 转换器
```

### Step 7: 清理并重新编译
```powershell
.\gradlew clean
.\gradlew assembleDebug
```

## ⚠️ 重要提醒

1. **不要删除密码管理相关的 WebDAV 功能**
   - ✅ 保留 SecureItem 的 WebDAV 备份
   - ✅ 保留 PasswordEntry 的 WebDAV 备份
   - ❌ 删除 LedgerEntry 的 WebDAV 备份

2. **不要删除银行卡管理**
   - 银行卡 (BankCard) 是独立于记账的功能
   - 银行卡属于 SecureItem 类型
   - 不需要修改银行卡相关代码

3. **保留数据导出导入的非记账功能**
   - ✅ 保留密码导出导入
   - ✅ 保留 TOTP 导出导入
   - ✅ 保留文档导出导入
   - ❌ 删除账本导出导入

## 🎯 预期结果

修复后的应用：
- ✅ 只有 4 个底部导航标签（密码、数据、生成器、设置）
- ✅ WebDAV 仍然可以备份密码和 TOTP
- ✅ 数据导出导入仍然可以导出密码
- ❌ 没有记账功能
- ❌ 没有资产管理
- ❌ 没有支付宝导入

## 📄 修复时的注意事项

1. MainActivity.kt 很大（可能 800+ 行），修改时要小心
2. SimpleMainScreen.kt 很大（可能 2700+ 行），建议分段修改
3. WebDavBackupScreen.kt 需要仔细区分密码备份和记账备份的代码
4. 每次修改后建议 git commit，方便回滚

## 下一步

**建议一个一个文件修复**，每修复一个文件就编译一次，确保没有引入新的错误。

修复顺序：
1. Converters.kt (最简单)
2. DataExportImportManager.kt (中等)
3. WebDavHelper.kt (中等)
4. WebDavBackupScreen.kt (复杂)
5. MainActivity.kt (复杂)
6. SimpleMainScreen.kt (最复杂)

每个文件修复完后运行：
```powershell
.\gradlew compileDebugKotlin
```
