---
title: Android 版 介绍
date: 2025-06-01 23:00:00
permalink: /ecosystem/AndroidReadme
---

# Monica for Android

> Monica 的本地密码库 Android 客户端  
> Android 8.0+ · Jetpack Compose · MDBX / Bitwarden / KeePass · AutoFill · TOTP · WebDAV

[![Release](https://img.shields.io/github/v/release/Monica-Pass/Monica-for-Android?style=flat-square)](https://github.com/Monica-Pass/Monica-for-Android/releases)

[![Downloads](https://img.shields.io/github/downloads/Monica-Pass/Monica-for-Android/total?style=flat-square)](https://github.com/Monica-Pass/Monica-for-Android/releases)

[![Last Commit](https://img.shields.io/github/last-commit/Monica-Pass/Monica-for-Android?style=flat-square)](https://github.com/Monica-Pass/Monica-for-Android/commits)

[![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](LICENSE)

::: note 摘要
Monica for Android 是 Monica 密码库生态中的原生 Android 客户端，采用 <mark>本地优先</mark> 的设计理念，专注于在 Android 手机上提供安全、可控、可恢复的密码管理体验。 ✨

主要特性：`本地 Vault`、`Bitwarden 聚合`、`KeePass 兼容`、`AutoFill`、`TOTP`、`WebDAV`。
:::

Monica for Android 是聚合 **Bitwarden** 与 **KeePass** 的本地密码库（Local Vault）。它以本地存储优先为核心，帮助你在 Android 手机上统一管理账号密码、2FA、私密笔记与敏感附件，同时支持可选的 WebDAV 备份恢复与跨平台数据同步。

项目当前以 **本地优先、Bitwarden 兼容、KeePass 聚合、MDBX 全面化、Android 8.0+** 为核心路线，重点围绕本地加密保险库、原生 AutoFill、TOTP、生物识别、Passkey、WebDAV 备份恢复以及多类型条目管理展开。

---

## 适合谁使用 ✅

Monica for Android 适合以下用户：

- 希望使用**本地优先**方案管理密码，不愿将核心凭据托管给第三方云服务。
- 既使用 Bitwarden，也维护 KeePass (`.kdbx`) 数据，需要统一入口管理。
- 需要在 Android 日常使用中完成 AutoFill 自动填充、生物识别解锁、TOTP 管理。
- 希望通过自有 WebDAV 基础设施实现离线或低延迟的跨设备数据备份与恢复。


## 当前功能 🎯

### 本地 Vault

- 支持创建、打开、锁定本地加密保险库
- 采用本地优先策略，降低对外部服务的依赖
- 支持 MDBX 与传统加密数据库格式

### 多生态聚合

- **Bitwarden 兼容**：支持 Bitwarden API 同步、导入导出、账户关联
- **KeePass 兼容**：原生支持 `.kdbx` 文件读写与导入迁移
- **MDBX 本地格式**：Monica 自研的嵌套加密数据库格式，支持版本历史与冲突检测

### 多类型条目管理

- 支持登录项、安全笔记、银行卡、身份证件、TOTP 等基础 CRUD
- 每条目支持附件存储
- 支持收藏、标签、文件夹分类

### 收藏与搜索

- 支持收藏优先视图
- 支持按标题、域名、标签快速检索
- 支持全文搜索

### TOTP 与二次验证

- 支持 `otpauth://` URI 导入
- 支持二维码导入（通过相机或图库）
- 支持验证码生成与实时刷新

### Android 原生集成

- **自动填充（AutoFill）**：系统级密码填充能力，兼容大多数应用与浏览器
- **生物识别**：Face ID / 指纹解锁，基于 BiometricPrompt 集成
- **Passkey / Credential Provider**：Android 14+ 通行密钥支持
- **后台备份**：WorkManager 定时 WebDAV 自动备份

### WebDAV 备份恢复

- 支持上传、下载、完整性校验（SHA-256）
- 支持恢复预览与版本选择
- 支持恢复前验证


## 当前状态 ⚡

- 项目处于活跃开发与持续维护阶段
- Android 8.0 - 15 设备广泛支持；某些新特性（如 Passkey）需要 Android 14+


## 开发信息 👨‍💻

### 技术架构

```
UI 层 (Compose)
    ↓
ViewModel / ViewModel State
    ↓
Repository Pattern
    ↓
Room Database + Keystore + Biometric
    ↓
Bitwarden API / KeePass Library / MDBX FFI
    ↓
WebDAV / Encryption / Security
```

### 核心技术栈 ⚙️

- **UI 框架**：<mark>Jetpack Compose</mark> + Material 3 + Navigation Compose
- **数据层**：Room（SQLite ORM）+ DAO + Repository
- **状态管理**：ViewModel + Kotlin Flow + DataStore Preferences
- **依赖注入**：Koin（应用启动于 `MonicaApplication`）
- **安全能力**：Android Keystore、EncryptedSharedPreferences、BiometricPrompt
- **后台任务**：WorkManager（`AutoBackupWorker` 用于自动 WebDAV 备份）
- **网络与协议**：Retrofit + OkHttp（Bitwarden API）、kotpass（KeePass）、sardine-android（WebDAV）
- **异步框架**：Coroutines + Flow
- **扫描与识别**：CameraX + ML Kit（二维码）、Credentials API（Passkey）

### 项目分层（代码结构） 📁

- `takagi/ru/monica/ui`：Compose 页面与组件
- `takagi/ru/monica/data`：Room 实体、DAO、数据库迁移
- `takagi/ru/monica/repository`：数据访问封装与聚合
- `takagi/ru/monica/security`：加密、密钥与鉴权
- `takagi/ru/monica/bitwarden`：Bitwarden API、加密、同步、ViewModel
- `takagi/ru/monica/autofill`：AutoFill 服务与流程
- `takagi/ru/monica/passkey`：Android 14+ Credential Provider 集成
- `takagi/ru/monica/workers`：后台任务（如自动 WebDAV 备份）

### 安全模型 🔐

- **加密算法**：AES-256-GCM（认证加密）
- **密钥派生**：PBKDF2-HMAC-SHA256（高迭代参数）
- **本地保护**：主密码哈希与安全配置由 Android Keystore 管理
- **网络边界**：仅用于 Bitwarden API 同步、WebDAV 备份/恢复及二维码更新

### 构建与依赖 📦

- **JDK**：17+
- **Android Studio**：最新稳定版
- **编译配置**：`compileSdk 35`，`targetSdk 34`，`minSdk 26`
- **构建工具**：AGP `8.6.0`，Kotlin `2.0.21`，Compose BOM `2026.03.00`
- **版本管理**：见 `gradle/libs.versions.toml` 与 `app/build.gradle`

### 快速构建命令

构建调试 APK：

```bash
./gradlew :app:assembleDebug
```

运行单元测试：

```bash
./gradlew test
```

运行 Android 仪表测试（需连接设备或启动模拟器）：

```bash
./gradlew connectedAndroidTest
```

---

## 路线图 🚦

### 稳定版重点

- MDBX 本地 Vault 核心能力
- Bitwarden 兼容导入/同步
- KeePass `.kdbx` 读写
- 多类型条目管理
- Android AutoFill 支持
- TOTP 与二维码
- WebDAV 备份恢复

### 持续推进

- 生物识别体验深化（Face / 指纹）
- Passkey 真机验收（Android 14+）
- 离线搜索性能优化
- MDBX 全面迁移与兼容

### 后续能力

- 更多云服务同步（OneDrive、Google Drive 等）
- 浏览器插件联动
- iOS 版本数据同步
- 高级加密与安全审计
- Widget 与快速访问

---

## 致谢 🙏

Monica 的设计、兼容性适配与部分功能方向，受到了以下优秀开源项目和软件的启发与帮助：

- [Keyguard](https://github.com/AChep/keyguard-app) - Android 端密码管理器的交互设计与体验参考
- [Bitwarden](https://bitwarden.com/) - 开源密码管理生态、Vault 模型与同步能力的重要参考
- [KeePass](https://keepass.info/) - 本地密码库理念与 `.kdbx` 生态兼容的重要基础
- [Stratum Auth](https://github.com/stratumauth/app) - 身份验证器体验、图标资源与相关兼容支持参考

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Monica-Pass/Monica-for-Android&type=Date)](https://star-history.com/#Monica-Pass/Monica-for-Android&Date)

---

## 许可证

Copyright (c) 2025 JoyinJoester

Monica for Android 基于 [GNU General Public License v3.0](LICENSE) 开源发布。

## 第三方图标标注

- 本项目本地打包了来自 [Stratum Auth app](https://github.com/stratumauth/app) 的图标资源（版本 [v1.4.0](https://github.com/stratumauth/app/releases/tag/v1.4.0)，目录 [icons](https://github.com/stratumauth/app/tree/v1.4.0/icons) / [extraicons](https://github.com/stratumauth/app/tree/v1.4.0/extraicons)，GPL-3.0）
- 品牌名称与 Logo 的商标权归各自权利人所有
