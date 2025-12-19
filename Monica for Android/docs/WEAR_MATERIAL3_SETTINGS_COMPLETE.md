# Monica Wear 设置界面 - Material 3 UI 完成

## 📋 已完成功能

### 1. Material 3 设计的设置界面 ✅
- **现代化UI设计**
  - 使用 Material 3 组件 (ElevatedCard, TopAppBar, AlertDialog)
  - 动态配色方案和主题适配
  - 流畅的动画效果 (fadeIn/fadeOut, scaleIn/scaleOut)
  - 圆角卡片和优雅的阴影效果

- **同步状态卡片**
  - 实时显示同步状态（空闲/同步中/成功/失败）
  - 动态颜色变化（primaryContainer/errorContainer/secondaryContainer）
  - 显示最后同步时间
  - 带图标的视觉反馈
  - 点击立即同步功能

- **设置分组**
  - 云端同步：WebDAV配置、加密密码
  - 安全：生物识别开关
  - 数据管理：清除所有数据
  - 关于信息：版本号和应用描述

### 2. WebDAV 配置对话框 ✅
- Material 3 AlertDialog 设计
- 输入字段：
  - 服务器地址 (带Link图标)
  - 用户名 (带Person图标)
  - 密码 (带Lock图标)
- 实时连接测试
- 错误提示显示
- 加载动画

### 3. 加密密码配置对话框 ✅
- 密码输入和确认
- 密码匹配验证
- 用于解密WebDAV加密备份

### 4. 清除数据确认对话框 ✅
- 警告图标显示
- 红色主题强调危险性
- 二次确认机制

### 5. SettingsViewModel 完整实现 ✅
- **状态管理**
  - `syncState`: 同步状态 (Idle/Syncing/Success/Error)
  - `lastSyncTime`: 最后同步时间
  - `isWebDavConfigured`: WebDAV配置状态
  - `biometricEnabled`: 生物识别开关状态

- **功能实现**
  - `configureWebDav()`: 配置WebDAV并测试连接
  - `configureEncryptionPassword()`: 配置加密密码
  - `syncNow()`: 立即同步（下载最新备份）
  - `checkAutoSync()`: 检查是否需要自动同步
  - `toggleBiometric()`: 切换生物识别
  - `clearAllData()`: 清除所有数据

- **集成WearWebDavHelper**
  - 完全集成已创建的WebDAV同步引擎
  - 调用 `downloadAndImportLatestBackup()` 执行同步
  - 使用 `shouldAutoSync()` 检查同步时机
  - 管理同步时间和配置状态

## 🎨 UI 特性

### Material 3 组件使用
```kotlin
- ElevatedCard: 带阴影的卡片容器
- TopAppBar: 顶部导航栏
- OutlinedTextField: 输入框
- Button/TextButton: 按钮
- Icon: Material Icons 图标
- HorizontalDivider: 分割线
- AlertDialog: 对话框
- CircularProgressIndicator: 加载指示器
```

### 动画效果
```kotlin
- AnimatedVisibility: 组件显示/隐藏动画
- fadeIn/fadeOut: 淡入淡出
- scaleIn/scaleOut: 缩放动画
```

### 配色方案
```kotlin
- MaterialTheme.colorScheme.primary: 主色
- MaterialTheme.colorScheme.primaryContainer: 主容器色
- MaterialTheme.colorScheme.error: 错误色
- MaterialTheme.colorScheme.surface: 表面色
- MaterialTheme.colorScheme.onSurface: 表面上的文字色
```

## 📱 界面布局

```
┌─────────────────────────┐
│   ← 设置              │  <- TopAppBar
├─────────────────────────┤
│                         │
│  ┌─────────────────┐   │
│  │ 🔄 云端同步     │   │  <- SyncStatusCard
│  │ 上次: 12:30     │   │     (动态颜色)
│  │ 点击立即同步    │   │
│  └─────────────────┘   │
│                         │
│  云端同步               │  <- SettingsSection
│  ┌─────────────────┐   │
│  │ ☁ WebDAV 配置  →│   │
│  ├─────────────────┤   │
│  │ 🔑 加密密码     →│   │
│  └─────────────────┘   │
│                         │
│  安全                   │
│  ┌─────────────────┐   │
│  │ 👆 生物识别     →│   │
│  └─────────────────┘   │
│                         │
│  数据管理               │
│  ┌─────────────────┐   │
│  │ 🗑 清除所有数据 →│   │  (红色)
│  └─────────────────┘   │
│                         │
│  ┌─────────────────┐   │
│  │ ℹ Monica Wear   │   │  <- AboutSection
│  │   版本 1.0.0    │   │
│  └─────────────────┘   │
│                         │
└─────────────────────────┘
```

## 🔄 同步流程

### 用户手动同步
1. 点击同步状态卡片
2. ViewModel 调用 `syncNow()`
3. 检查 WebDAV 配置状态
4. 设置状态为 `Syncing`
5. 调用 `WearWebDavHelper.downloadAndImportLatestBackup()`
6. 更新状态为 `Success` 或 `Error`
7. 3秒后恢复为 `Idle`

### 自动同步（待集成）
1. 应用启动时调用 `checkAutoSync()`
2. 检查是否满足条件（每天首次 + 12小时间隔）
3. 满足条件则自动触发 `syncNow()`

## 📋 待完成工作

### 1. 创建 EncryptionHelper
从原项目复制解密功能，用于解密加密的备份文件

### 2. 创建 SyncWorker
使用 WorkManager 实现后台定期同步

### 3. 应用启动集成
在 MainActivity 中调用 `viewModel.checkAutoSync()`

### 4. 清除数据功能
实现删除所有 TOTP 数据的功能

### 5. 生物识别功能
实现生物识别锁定/解锁功能

## 🎯 使用说明

### 配置 WebDAV
1. 打开设置页面
2. 点击 "WebDAV 配置"
3. 输入服务器地址、用户名、密码
4. 点击保存（会自动测试连接）
5. 如果连接成功，配置会被保存

### 配置加密密码
1. 点击 "加密密码"
2. 输入用于解密备份的密码
3. 确认密码
4. 点击保存

### 执行同步
1. 确保 WebDAV 已配置
2. 点击同步状态卡片
3. 等待同步完成
4. 查看同步结果（成功/失败）

### 清除数据
1. 点击 "清除所有数据"
2. 阅读警告信息
3. 点击 "确认删除"
4. 所有本地数据将被清除

## 🔧 技术细节

### 依赖项
```groovy
// 已包含在 build.gradle 中
implementation libs.androidx.material3
implementation 'androidx.wear.compose:compose-material:1.2.1'
implementation 'com.github.thegrizzlylabs:sardine-android:0.8'
```

### ViewModel 架构
- 继承 `AndroidViewModel` 以访问 Application Context
- 使用 `StateFlow` 管理状态
- `viewModelScope` 执行异步操作
- 完全集成 `WearWebDavHelper`

### 错误处理
- 所有操作都包含 try-catch
- 详细的日志记录 (Log.d/Log.e)
- 用户友好的错误提示
- 自动清除失败配置

## 🎉 完成状态

✅ Material 3 设计的设置界面  
✅ WebDAV 配置对话框  
✅ 加密密码配置对话框  
✅ 清除数据确认对话框  
✅ SettingsViewModel 完整实现  
✅ 同步状态管理  
✅ WebDavHelper 集成  

现在可以继续实现剩余的功能（EncryptionHelper、SyncWorker、应用启动集成）！
