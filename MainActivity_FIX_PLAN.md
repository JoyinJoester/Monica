# 🔧 MainActivity.kt 修复详细计划

## 当前状态
MainActivity.kt 仍有 50+ 处引用已删除的 Ledger 相关类，导致编译失败。

## 需要修复的位置

### 1. 函数参数 (第 244, 250 行)
```kotlin
// 需要删除这两个参数：
ledgerViewModel: takagi.ru.monica.viewmodel.LedgerViewModel,
ledgerRepository: LedgerRepository,
```

### 2. SimpleMainScreen 调用 (第 221, 227, 291 行)
```kotlin
// 删除这些参数传递：
ledgerViewModel = ledgerViewModel,
ledgerRepository = ledgerRepository,
```

### 3. 导航回调 (第 307-311 行)
```kotlin
// 删除这两个导航回调：
onNavigateToAddLedgerEntry = { entryId ->
    navController.navigate(Screen.AddEditLedgerEntry.createRoute(entryId))
},
onNavigateToLedgerEntryDetail = { entryId ->
    navController.navigate(Screen.LedgerEntryDetail.createRoute(entryId))
},
```

### 4. clearLedger 参数 (第 346, 376-380, 635, 665-669 行)
```kotlin
// 修改 onClearAllData 回调：
// 从：onClearAllData = { clearPasswords: Boolean, clearTotp: Boolean, clearLedger: Boolean, clearDocuments: Boolean, clearBankCards: Boolean ->
// 改为：onClearAllData = { clearPasswords: Boolean, clearTotp: Boolean, clearDocuments: Boolean, clearBankCards: Boolean ->

// 删除这些代码块：
if (clearLedger) {
    val entries = ledgerRepository.observeEntries().first()
    entries.forEach { entryWithRelations ->
        ledgerRepository.deleteEntry(entryWithRelations.entry)
    }
}
```

### 5. importAlipayLedgerData 调用 (第 568 行)
```kotlin
// 删除此调用，或将整个 Alipay 导入块删除
dataExportImportViewModel.importAlipayLedgerData(uri)
```

### 6. WebDavBackupScreen 调用 (第 791 行)
```kotlin
// 删除参数传递：
ledgerRepository = ledgerRepository,
```

## 建议的修复策略

由于 MainActivity.kt 很大且修改较多，建议：

### 方案一：逐步修复（推荐）
1. 先找到所有 `ledgerViewModel` 和 `ledgerRepository` 作为参数的函数
2. 删除这些参数
3. 删除所有传递这些参数的地方
4. 删除所有使用这些参数的代码块

### 方案二：使用文本编辑器批量替换
使用 VSCode 的查找替换功能：
1. 查找所有包含 `ledgerViewModel` 的行并删除
2. 查找所有包含 `ledgerRepository` 的行并删除
3. 查找所有包含 `clearLedger` 的行并修改

## 具体修复步骤（逐行）

由于token限制，建议手动修复或让AI分多次完成。以下是关键位置：

1. **第 244 行左右** - MonicaApp 函数签名
   - 删除 `ledgerViewModel` 参数
   - 删除 `ledgerRepository` 参数

2. **第 221, 227, 291 行** - SimpleMainScreen 调用
   - 删除 `ledgerViewModel = ledgerViewModel,`
   - 删除 `ledgerRepository = ledgerRepository,`

3. **第 307-311 行** - 导航回调
   - 删除整个 `onNavigateToAddLedgerEntry` 回调
   - 删除整个 `onNavigateToLedgerEntryDetail` 回调

4. **第 346-382 行** - 第一个 onClearAllData
   - 修改参数列表，删除 `clearLedger`
   - 删除 `if (clearLedger) { ... }` 代码块

5. **第 568 行** - Alipay导入
   - 删除或注释掉整个 Alipay 导入分支

6. **第 635-671 行** - 第二个 onClearAllData
   - 修改参数列表，删除 `clearLedger`
   - 删除 `if (clearLedger) { ... }` 代码块

7. **第 791 行** - WebDavBackupScreen 调用
   - 删除 `ledgerRepository = ledgerRepository,`

## ⚠️ 注意事项

1. **不要删除密码相关功能**
   - `clearPasswords` 要保留
   - `passwordRepository` 要保留
   - 所有 PasswordEntry 相关代码要保留

2. **不要删除 SecureItem 相关功能**
   - `clearTotp`, `clearDocuments`, `clearBankCards` 要保留
   - `secureItemRepository` 要保留

3. **保留所有导航逻辑**
   - 只删除记账相关的导航
   - 密码、TOTP、文档、设置的导航都要保留

## 编译验证

修改后运行：
```powershell
.\gradlew clean
.\gradlew compileDebugKotlin
```

如果还有错误，继续修复。

## 预计修改量

- 删除约 15-20 行代码
- 修改约 10-15 行代码
- 涉及 8-10 个不同位置

## 建议工具

使用 VSCode 的多光标编辑功能可以加快修复速度。
