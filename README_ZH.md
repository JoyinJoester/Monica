# Monica 密码管理器

[![License](https://img.shields.io/badge/license-GPL%20v3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Windows%20%7C%20Android-lightgrey.svg)]()
![Security](https://img.shields.io/badge/Security-AES--256--GCM-success)

**Monica** 是一款企业级的、离线优先的密码管理解决方案，专为极致的隐私保护和数据主权而设计。通过摒弃云端依赖，采用本地加密存储，Monica 确保您的敏感数据完全掌握在您自己手中。

完美支持 **Windows 11** (WinUI 3) 及 **Android** (Jetpack Compose) 平台。

---

## 🏛️ 架构与安全

Monica 基于“隐私设计 (Privacy by Design)”理念构建，采用行业标准的加密原语，以确保数据的机密性和完整性。

- **零知识架构**: 您的主密码从未离开设备，也从未被存储。
- **高强度加密**: 密码库采用 **AES-256-GCM** 认证加密算法进行保护。
- **密钥派生**: 主密钥通过高迭代次数的 **PBKDF2-HMAC-SHA256** 算法派生，有效抵御暴力破解攻击。
- **数据主权**: 数据本地存储于加密的 SQLite/Room 数据库中。通过您自有的 **WebDAV** 服务器实现可选同步，让您完全掌控基础设施。

---

## ⚡ 核心能力

### 🔐 凭证管理
*   **加密保险箱**:以此安全地存储密码、银行卡信息及私密笔记。
*   **TOTP 验证器**: 内置基于时间的一次性密码生成器，实现无缝的 2FA 双因素认证。
*   **泄露检测**: 使用 *k-Anonymity* 技术进行主动安全分析，在不泄露密码的前提下检测凭证是否遭受已知数据泄露。

### 🔄 跨平台同步
*   **WebDAV 同步**: 支持通过任意 WebDAV 兼容提供商（如 Nextcloud, 群晖, 私有云等）在设备间进行加密同步。
*   **统一体验**: 现代化的 Windows 桌面应用与原生的 Android 分在移动应用之间保持功能高度一致。

### 🛡️ 高级特性
*   **安全文档存储**: 支持附件及敏感文件的加密存储。
*   **生物识别解锁**: 支持 Windows Hello 及 Android 指纹/面部识别，通过快速认证访问。
*   **数据可移植性**: 完整的导入/导出支持，拒绝厂商锁定。

---

## 🛠️ 技术规格

### Windows 客户端
*   **框架**: WinUI 3 (Windows App SDK)
*   **运行时**: .NET 8
*   **数据访问**: Entity Framework Core

### Android 客户端
*   **UI 工具包**: Jetpack Compose (Material Design 3)
*   **语言**: Kotlin
*   **架构**: MVVM / Clean Architecture

---

## 📥 安装指南

### Windows
1. 前往 [Releases](https://github.com/JoyinJoester/Monica/releases) 页面下载最新的安装程序 (`.exe`)。
2. 运行安装程序即可完成部署。

### Android
1. 前往 [Releases](https://github.com/JoyinJoester/Monica/releases) 页面下载最新的 APK 文件。
2. 在您的 Android 设备上安装（需 Android 8.0+）。

---

## 🤝 支持开发

Monica 是一个由社区驱动的开源项目。如果这个工具为您带来了价值，欢迎支持项目的持续开发。

<div align="center">
<img src="image/support_author.jpg" alt="Support" width="280"/>
<br/>
<sub>微信 / 支付宝扫码支持</sub>
</div>

---

## ⚖️ 许可证

Copyright © 2025 JoyinJoester.
本项目基于 **GNU General Public License v3.0** 许可证分发。详情请参阅 `LICENSE` 文件。
