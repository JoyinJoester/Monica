# Bitwarden 集成 - Phase 2 完成报告

## 概述
Phase 2 实现了 Bitwarden 的服务层、数据仓库、ViewModel 和 UI 界面。

## 完成的组件

### 1. BitwardenRepository (数据仓库)
**文件**: `bitwarden/repository/BitwardenRepository.kt`

核心职责:
- 管理 Bitwarden Vault 生命周期（登录、登出、Token 刷新）
- 协调认证服务和同步服务
- 提供统一的数据访问接口
- 管理加密存储（使用 EncryptedSharedPreferences）

主要方法:
- `login()` / `loginWithTwoFactor()` - 登录认证
- `unlock()` / `lock()` / `lockAll()` - 解锁/锁定 Vault
- `logout()` - 注销并清理数据
- `sync()` - 同步数据
- `getPasswordEntries()` / `getFolders()` / `searchEntries()` - 数据查询
- `resolveConflictWithLocal()` / `resolveConflictWithServer()` - 冲突解决

### 2. BitwardenViewModel (UI 状态管理)
**文件**: `bitwarden/viewmodel/BitwardenViewModel.kt`

核心职责:
- 管理 UI 状态（登录状态、解锁状态、同步状态）
- 提供 StateFlow 供 Compose UI 观察
- 处理用户操作并与 Repository 交互

状态管理:
- `loginState` - 登录状态 (Idle, Loading, Success, Error, TwoFactorRequired)
- `vaults` - Vault 列表
- `activeVault` - 当前活动的 Vault
- `unlockState` - 解锁状态
- `syncState` - 同步状态
- `entries` / `folders` - 密码条目和文件夹

### 3. BitwardenLoginScreen (登录界面)
**文件**: `bitwarden/ui/BitwardenLoginScreen.kt`

功能:
- 服务器 URL 输入（支持自托管服务器）
- 邮箱/密码认证
- 高级选项（自定义 API 和 Identity URL）
- 双因素认证对话框
- 加载状态和错误提示

### 4. BitwardenSettingsScreen (设置/管理界面)
**文件**: `bitwarden/ui/BitwardenSettingsScreen.kt`

功能:
- Vault 列表展示（卡片样式）
- Vault 状态显示（已连接、已锁定、同步状态）
- 快捷操作（锁定、解锁、同步、登出）
- 同步设置（自动同步、仅 WiFi 同步）
- 解锁对话框
- 登出确认对话框

### 5. PasswordEntryDao 扩展
**文件**: `data/PasswordEntryDao.kt`

新增方法:
- `getByBitwardenCipherId()` - 通过 Cipher ID 查询
- `getByBitwardenVaultId()` - 获取 Vault 的所有条目
- `getByBitwardenFolderId()` - 获取文件夹的条目
- `insert()` - 插入新条目（兼容 SyncService）
- `getEntriesWithPendingBitwardenSync()` - 获取待同步条目
- `searchBitwardenEntries()` - 搜索 Bitwarden 条目
- `deleteAllByBitwardenVaultId()` - 删除 Vault 的所有条目
- `updateBitwardenFields()` - 更新 Bitwarden 字段
- `markBitwardenEntryAsModified()` - 标记为本地修改
- `countBitwardenEntries()` - 统计条目数量

### 6. 导航集成
**修改的文件**:
- `navigation/Screens.kt` - 添加 BitwardenLogin 和 BitwardenSettings 路由
- `MainActivity.kt` - 添加 Bitwarden 页面的 composable 路由
- `ui/screens/SyncBackupScreen.kt` - 添加 Bitwarden 入口

## 用户访问路径
设置 → 同步与备份 → Bitwarden 同步 → BitwardenSettingsScreen

## 下一步工作 (Phase 3)
1. 实现密码条目的完整展示和编辑
2. 添加 Bitwarden 条目到主密码列表中
3. 实现双向同步（本地修改推送到服务器）
4. 添加自动同步触发器
5. 完善冲突解决 UI
6. 添加离线模式支持
7. 实现 Bitwarden 搜索集成

## 测试要点
1. 登录官方 Bitwarden 服务器
2. 登录自托管 Vaultwarden 服务器
3. 双因素认证流程
4. Vault 解锁/锁定
5. 数据同步
6. 登出清理

## 安全注意事项
- 所有敏感数据使用 EncryptedSharedPreferences 存储
- 主密码永不持久化
- 对称密钥仅在内存中保持解锁状态
- Token 过期后自动刷新
- 登出时清除所有本地数据
