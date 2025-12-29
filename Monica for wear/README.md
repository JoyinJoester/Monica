# Monica Wear - Wear OS版本

## 快速开始

### 在Android Studio中运行

1. **打开项目**
   - 用Android Studio打开 `Monica for Android` 目录

2. **选择运行配置**
   - 在顶部工具栏的运行配置下拉菜单中选择 `Monica-wear`
   - 如果没有看到，重启Android Studio或点击 `File` → `Sync Project with Gradle Files`

3. **选择设备**
   - 在设备下拉菜单中选择：
     - Wear OS模拟器（推荐：Wear OS Small Round API 33+）
     - 或连接的Wear OS设备

4. **运行**
   - 点击绿色播放按钮 ▶️ 或按 `Shift+F10`
   - 应用会自动编译并安装到设备

### 命令行编译

```powershell
# 编译Debug版本
./gradlew :Monica-wear:assembleDebug

# 编译Release版本（需要签名配置）
./gradlew :Monica-wear:assembleRelease

# 编译并安装到设备
./gradlew :Monica-wear:installDebug

# 清理构建
./gradlew :Monica-wear:clean
```

## 配置说明

### 版本信息
- **应用ID**: `takagi.ru.monica.wear`
- **最低版本**: Android 8.0 (API 26) - Wear OS 2.0+
- **目标版本**: Android 14 (API 34)
- **版本号**: 1.0.0 (versionCode: 1)

### 签名配置（Release版本）

在项目根目录创建 `local.properties` 文件（如果没有），添加：

```properties
# 签名配置（可选，用于Release版本）
RELEASE_STORE_FILE=path/to/your/keystore.jks
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=your_key_alias
RELEASE_KEY_PASSWORD=your_key_password
```

如果不配置，将使用默认签名（仅用于测试）。

### 架构支持
- ARM 32位 (armeabi-v7a)
- ARM 64位 (arm64-v8a)

## 功能特性

### ✅ 已实现
- 6位PIN码锁定保护
- TOTP验证码生成和显示
- 倒计时震动提醒（≤5秒）
- WebDAV云端同步
- Material 3深色主题设计
- 原版Monica图标

### 🔒 安全特性
- EncryptedSharedPreferences存储
- PBKDF2密码哈希（100000次迭代）
- FLAG_SECURE防截屏
- 自动锁定保护

### ⚙️ 技术栈
- Kotlin + Jetpack Compose
- Wear Compose Material
- Room Database
- Coroutines + Flow
- DataStore Preferences
- Sardine Android (WebDAV)

## 故障排除

### 问题：Android Studio无法识别运行配置
**解决方案**：
1. 点击 `File` → `Invalidate Caches...` → `Invalidate and Restart`
2. 或手动创建运行配置（见上文"在Android Studio中运行"）

### 问题：编译失败 "Task 'wrapper' not found"
**解决方案**：
- 不要运行gradle任务，使用顶部的绿色播放按钮运行应用
- 或使用命令行：`./gradlew :Monica-wear:installDebug`

### 问题：无法连接Wear OS模拟器
**解决方案**：
1. 确保安装了Wear OS系统映像
2. 创建Wear OS模拟器（推荐：Wear OS Small Round API 33）
3. 启动模拟器后再运行应用

### 问题：应用安装后无法打开
**解决方案**：
1. 检查模拟器/设备是否支持Wear OS 2.0+（API 26+）
2. 查看Logcat日志排查错误
3. 尝试清理重新编译：`./gradlew clean :Monica-wear:installDebug`

## 开发说明

### 项目结构
```
Monica-wear/
├── src/main/
│   ├── java/takagi/ru/monica/wear/
│   │   ├── data/           # 数据层（数据库、实体）
│   │   ├── repository/     # 仓库层
│   │   ├── security/       # 安全管理（PIN码）
│   │   ├── ui/             # UI层（Compose界面）
│   │   ├── utils/          # 工具类（WebDAV、加密）
│   │   ├── viewmodel/      # ViewModel
│   │   └── MainActivity.kt # 主入口
│   └── res/                # 资源文件（图标、字符串）
├── build.gradle            # 模块配置
└── proguard-rules.pro      # 混淆规则
```

### 调试
- 使用Android Studio的Logcat查看日志
- 标签过滤：`Monica-wear`
- 启用详细日志：在Settings中打开详细日志

## 版本历史

### v1.0.0 (2025-11-18)
- ✅ 初始版本
- ✅ PIN码保护功能
- ✅ TOTP验证码显示
- ✅ WebDAV同步支持
- ✅ Material 3深色主题
- ✅ 原版Monica图标

## 许可证
遵循主项目的许可证
