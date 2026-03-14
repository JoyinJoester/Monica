# Monica 本地密码库

**中文** | [English](README_EN.md) | [日本語](README_JA.md) | [Tiếng Việt](README_VI.md) 

[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Browser-3DDC84)
![Security](https://img.shields.io/badge/Security-AES--256--GCM-success)
[![Website](https://img.shields.io/badge/Website-Monica-0A66C2)](https://joyinjoester.github.io/Monica/)
[![Release](https://img.shields.io/github/v/release/JoyinJoester/Monica?style=flat-square)](https://github.com/JoyinJoester/Monica/releases)
[![Stars](https://img.shields.io/github/stars/JoyinJoester/Monica?style=flat-square)](https://github.com/JoyinJoester/Monica/stargazers)
[![Forks](https://img.shields.io/github/forks/JoyinJoester/Monica?style=flat-square)](https://github.com/JoyinJoester/Monica/network/members)
![Repo Size](https://img.shields.io/github/repo-size/JoyinJoester/Monica?style=flat-square)
[![Top Language](https://img.shields.io/github/languages/top/JoyinJoester/Monica?style=flat-square)](https://github.com/JoyinJoester/Monica)
![Language Count](https://img.shields.io/github/languages/count/JoyinJoester/Monica?style=flat-square)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9.3-3178C6?style=flat-square&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![State-of-the-art Shitcode](https://img.shields.io/static/v1?label=State-of-the-art&message=Shitcode&color=7B5804)](https://github.com/trekhleb/state-of-the-art-shitcode)
[![爱发电](https://img.shields.io/badge/爱发电-JoyinJoester-ea4aaa?style=flat-square)](https://afdian.com/a/JoyinJoester)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-joyinjoester-29ABE0?style=flat-square&logo=kofi&logoColor=white)](https://ko-fi.com/joyinjoester)
[![QQ群](https://img.shields.io/badge/QQ群-1087865010-12B7F5?style=flat-square&logo=tencentqq&logoColor=white)](https://qm.qq.com/q/2vTdTkHV3u)

Monica 是一个聚合 **Bitwarden** 与 **KeePass** 的本地密码库（Local Vault）。
它以本地存储优先为核心，帮助你在 Android 与浏览器端统一管理账号密码、2FA、私密笔记与敏感附件。

官网入口: https://joyinjoester.github.io/Monica/

> Monica for Windows 已归档。历史代码见: [Monica-for-Windows](https://github.com/JoyinJoester/Monica-for-Windows)

---

## 用户先看

### Monica 适合谁
- 需要本地优先密码管理，不希望账号数据托管到第三方云。
- 既使用 Bitwarden，也维护 KeePass (`.kdbx`) 数据。
- 需要 Android 日常使用，同时在浏览器里完成自动填充。

### 你能得到什么
- 本地加密保险箱: 登录信息、银行卡、身份信息、私密笔记、附件。
- 双生态聚合: Android 端包含 Bitwarden API/同步能力与 KeePass (`.kdbx`) 读写能力。
- 可选同步与备份: 通过自有 WebDAV 基础设施实现跨设备数据流转。
- 内置 TOTP: 在同一应用内完成密码与二次验证码管理。

### 快速安装

Android:
1. 从 [Releases](https://github.com/JoyinJoester/Monica/releases) 下载最新 APK。
2. 在 Android 8.0+ 设备安装并初始化主密码。

浏览器插件 (Chrome / Edge):
1. 在 `Monica for Browser` 目录构建插件。
2. 打开 `chrome://extensions/` 并启用开发者模式。
3. 选择“加载已解压的扩展程序”，导入 `dist` 目录。

---

## Android 版本重点

### 核心功能
- 本地 Vault: 所有核心凭据本地加密存储。
- 聚合导入: 支持 KeePass 数据迁移与 Bitwarden 兼容接入。
- 智能检索: 按标题、域名、标签快速定位凭据。
- 生物识别解锁: 使用系统级生物识别能力提升安全与可用性。
- TOTP 管理: 统一存储并生成动态验证码。

### 实现说明（专业版）
- UI 层: Jetpack Compose + Material 3 + Navigation Compose。
- 数据层: Room（`PasswordDatabase`）+ DAO + Repository。
- 并发模型: Kotlin Coroutines + Flow。
- 依赖注入: Koin（应用启动于 `MonicaApplication`）。
- 安全能力: Android Keystore、EncryptedSharedPreferences、BiometricPrompt。
- 同步任务: WorkManager（`AutoBackupWorker`）用于自动 WebDAV 备份。
- 协议与集成: Retrofit + OkHttp（Bitwarden API）、kotpass（KeePass）、sardine-android（WebDAV）。

### 安全模型
- 加密算法: AES-256-GCM（认证加密）。
- 密钥派生: PBKDF2-HMAC-SHA256（高迭代参数）。
- 本地保护: 主密码哈希与安全配置由本地安全组件管理。
- 网络边界: 应用声明网络权限，主要用于 Bitwarden 联动与 WebDAV 备份/同步等在线能力。

---

## 赞助支持

如果 Monica 对你有帮助，欢迎支持持续开发与安全投入。

<div align="center">
<img src="image/support_author.jpg" alt="Support Monica" width="320"/>
<br/>
<sub>微信 / 支付宝扫码支持</sub>
</div>

你的支持将优先用于:
- 安全审计与加密方案强化。
- Android 体验优化与稳定性改进。
- 跨端功能统一与文档维护。

---

## 开发者信息

### 项目分层（代码现状）
- `takagi/ru/monica/ui`: Compose 页面与组件。
- `takagi/ru/monica/data`: Room 实体、DAO、数据库迁移。
- `takagi/ru/monica/repository`: 数据访问封装。
- `takagi/ru/monica/security`: 加密、密钥与鉴权相关实现。
- `takagi/ru/monica/bitwarden`: API、加密、映射、同步与视图模型。
- `takagi/ru/monica/autofill`: 自动填充服务与流程。
- `takagi/ru/monica/passkey`: Android 14+ Credential Provider 相关实现。
- `takagi/ru/monica/workers`: 后台任务（如自动 WebDAV 备份）。

### 当前已使用的成熟组件（仓库可验证）
- Android UI: Jetpack Compose, Material 3, Navigation Compose。
- 数据与状态: Room, DataStore Preferences, ViewModel。
- 安全: Android Keystore, EncryptedSharedPreferences, BiometricPrompt。
- 网络与协议: Retrofit, OkHttp, Kotlinx Serialization。
- 同步与生态: sardine-android(WebDAV), kotpass(KeePass), Bitwarden API 对接。
- 异步与任务: Coroutines, Flow, WorkManager。
- 其他能力: CameraX + ML Kit（二维码扫描）, Credentials API（Passkey）。

### 构建与贡献
- Android Studio: 最新稳定版。
- JDK: 17+。
- Android 配置: `compileSdk 35`，`targetSdk 34`，`minSdk 26`（见 `Monica for Android/app/build.gradle`）。
- Android 构建基线: AGP `8.6.0`，Kotlin `1.9.10`，Compose BOM `2024.10.01`（Material3 跟随 BOM）。
- 版本信息以 `Monica for Android/gradle/libs.versions.toml` 与 `Monica for Android/app/build.gradle` 为准。
- 浏览器端技术栈: React + TypeScript + Vite（见 `Monica for Browser/package.json`）。
- 欢迎通过 Issue / PR 参与功能和安全改进。

---

## 许可证

Copyright (c) 2025 JoyinJoester

Monica 基于 [GNU General Public License v3.0](LICENSE) 开源发布。

## 第三方图标标注

- 本项目本地打包了来自 [Stratum Auth app](https://github.com/stratumauth/app) 的图标资源（版本 [v1.4.0](https://github.com/stratumauth/app/releases/tag/v1.4.0)，目录 [icons](https://github.com/stratumauth/app/tree/v1.4.0/icons) / [extraicons](https://github.com/stratumauth/app/tree/v1.4.0/extraicons)，GPL-3.0）。
- 品牌名称与 Logo 的商标权归各自权利人所有。
