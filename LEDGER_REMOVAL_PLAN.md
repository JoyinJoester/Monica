# 🗑️ 记账功能删除计划

## 📋 需要删除的功能模块

### 1. 数据层 (Data Layer)
- [ ] `app/src/main/java/takagi/ru/monica/data/ledger/` 整个文件夹
  - `Asset.kt` - 资产实体
  - `AssetType.kt` - 资产类型枚举
  - `LedgerEntry.kt` - 账单条目
  - `LedgerCategory.kt` - 账单分类
  - `LedgerTag.kt` - 账单标签
  - `LedgerRelations.kt` - 关系定义
  - `LedgerEntryTagCrossRef.kt` - 交叉引用
  - `LedgerDao.kt` - 数据访问对象

### 2. Repository 层
- [ ] `app/src/main/java/takagi/ru/monica/repository/LedgerRepository.kt`

### 3. ViewModel 层
- [ ] `app/src/main/java/takagi/ru/monica/viewmodel/LedgerViewModel.kt`

### 4. UI 层 - Screens
- [ ] `app/src/main/java/takagi/ru/monica/ui/screens/LedgerScreen.kt`
- [ ] `app/src/main/java/takagi/ru/monica/ui/screens/AddEditLedgerEntryScreen.kt`
- [ ] `app/src/main/java/takagi/ru/monica/ui/screens/LedgerEntryDetailScreen.kt`
- [ ] `app/src/main/java/takagi/ru/monica/ui/screens/AssetManagementScreen.kt`
- [ ] `app/src/main/java/takagi/ru/monica/ui/screens/AddEditAssetScreen.kt`

### 5. 导航路由
- [ ] `Screens.kt` 中删除：
  - `AddEditLedgerEntry`
  - `LedgerEntryDetail`
  - `AssetManagement`
  - `AddEditAsset`

### 6. MainActivity 修改
- [ ] 移除 `LedgerRepository` 初始化
- [ ] 移除 `LedgerViewModel` 初始化
- [ ] 移除记账相关的导航路由
- [ ] 移除记账相关的参数传递

### 7. SimpleMainScreen 修改
- [ ] 移除账本/记账相关的标签页或入口

### 8. 数据库修改
- [ ] `PasswordDatabase.kt` 中移除：
  - `LedgerDao` 引用
  - 记账相关的实体类
  - 记账相关的数据库迁移

### 9. 导入导出功能修改
- [ ] `DataExportImportViewModel.kt`:
  - 保留密码等数据的导入导出
  - 删除支付宝账单导入相关代码
  - 删除记账数据的导出功能

### 10. WebDAV 备份功能修改
- [ ] 保留密码等数据的 WebDAV 备份
- [ ] 删除记账数据的 WebDAV 备份

### 11. 资源文件
- [ ] 删除 `strings.xml` 中记账相关的字符串
- [ ] 删除记账相关的图标和布局文件

### 12. 文档和报告
- [ ] 删除或更新：
  - `asset_fix_report.md`
  - `asset_fix_summary.md`
  - `LEDGER_REMOVAL_PROGRESS.md`
  - `ledger_removal_summary.md`

## ⚠️ 需要保留的功能

### 导入功能（非记账）
- ✅ 密码数据导入
- ✅ TOTP 数据导入
- ✅ 银行卡数据导入
- ✅ 文档数据导入

### WebDAV 功能（非记账）
- ✅ 密码数据备份
- ✅ 其他敏感数据备份

### 银行卡功能
- ✅ 银行卡管理（独立于记账系统）
- ✅ 银行卡添加/编辑/删除

## 🔍 需要检查的依赖关系

1. **银行卡与资产的关联**
   - 检查银行卡是否依赖资产管理
   - 如果有关联，需要解耦

2. **导入功能中的记账部分**
   - 支付宝账单导入 → 删除
   - 其他数据导入 → 保留

3. **WebDAV 备份**
   - 记账数据备份 → 删除
   - 密码等数据备份 → 保留

4. **主界面标签**
   - 记账/账本标签 → 删除
   - 其他标签 → 保留

## 📝 执行步骤

### 第一步：备份
- [x] 创建 Git 提交点（已有 git reset --hard HEAD）

### 第二步：删除数据层
- [ ] 删除整个 `data/ledger/` 文件夹

### 第三步：删除 Repository 和 ViewModel
- [ ] 删除 `LedgerRepository.kt`
- [ ] 删除 `LedgerViewModel.kt`

### 第四步：删除 UI 层
- [ ] 删除记账相关的 Screen 文件

### 第五步：修改集成点
- [ ] 修改 `MainActivity.kt`
- [ ] 修改 `Screens.kt`
- [ ] 修改 `SimpleMainScreen.kt`
- [ ] 修改 `PasswordDatabase.kt`

### 第六步：清理导入导出
- [ ] 修改 `DataExportImportViewModel.kt`
- [ ] 删除支付宝导入相关代码

### 第七步：清理资源文件
- [ ] 清理 `strings.xml`
- [ ] 删除无用的布局和图标

### 第八步：测试编译
- [ ] 编译检查
- [ ] 修复编译错误
- [ ] 功能测试

## 🎯 预期结果

删除后，Monica 应该是一个纯粹的密码管理器，包含：
- ✅ 密码管理
- ✅ TOTP 两步验证
- ✅ 银行卡管理（独立功能）
- ✅ 文档管理
- ✅ 数据导入导出（非记账）
- ✅ WebDAV 备份（非记账）
- ✅ 自动填充
- ✅ 安全分析
- ❌ 记账功能（已删除）
- ❌ 资产管理（已删除）
