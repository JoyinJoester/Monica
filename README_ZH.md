# Monica 密码管理器

[![License](https://img.shields.io/badge/license-GPL%20v3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Browser-lightgrey.svg)]()
![Security](https://img.shields.io/badge/Security-AES--256--GCM-success)

**Monica** 是一款企业级的、离线优先的密码管理解决方案，专为极致的隐私保护和数据主权而设计。通过摒弃云端依赖，采用本地加密存储，Monica 确保您的敏感数据完全掌握在您自己手中。

完美支持 **Android** (Jetpack Compose) 平台及 **Chrome / Edge / 浏览器插件**。

---

## 🏛️ 架构与安全

Monica 基于“隐私设计 (Privacy by Design)”理念构建，采用行业标准的加密原语，以确保数据的机密性和完整性。

- **零知识架构**: 您的主密码从未离开设备，也从未被存储。
- **高强度加密**: 密码库采用 **AES-256-GCM** 认证加密算法进行保护。
- **密钥派生**: 主密钥通过高迭代次数的 **PBKDF2-HMAC-SHA256** 算法派生，有效抵御暴力破解攻击。
- **数据主权**: 数据本地存储。通过您自有的 **WebDAV** 服务器实现可选同步，让您完全掌控基础设施。

---

## ⚡ 核心能力

### 🔐 凭证管理
*   **加密保险箱**:以此安全地存储密码、银行卡信息及私密笔记。
*   **TOTP 验证器**: 内置基于时间的一次性密码生成器，实现无缝的 2FA 双因素认证。
*   **自动填充**: 浏览器插件支持一键填充登录表单及 2FA 验证码。

### 🔄 跨平台同步
*   **WebDAV 同步**: 支持通过任意 WebDAV 兼容提供商（如 Nextcloud, 群晖, 私有云等）在设备间进行加密同步。
*   **统一体验**: React 开发的 **浏览器插件** 与原生的 **Android 应用** 之间保持功能高度一致。

### 🛡️ 高级特性
*   **安全文档存储**: 支持附件及敏感文件的加密存储（Android版支持）。
*   **隐私保护**: 无网络权限要求，数据完全本地化。
*   **数据可移植性**: 完整的导入/导出支持，拒绝厂商锁定。

---

## 🛠️ 技术规格

### 浏览器插件
*   **框架**: React + Vite
*   **存储**: 浏览器本地存储 (加密)
*   **兼容性**: Chrome, Edge, Brave 等 Chromium 内核浏览器

### Android 客户端
*   **UI 工具包**: Jetpack Compose (Material Design 3)
*   **语言**: Kotlin
*   **安全性**: Android Keystore 硬件保护

---

## 📥 安装指南

### 浏览器插件 (Chrome / Edge)
1. 下载源码或从 [**Monica for Browser**](Monica%20for%20Browser) 目录构建。
2. 在浏览器中打开 `chrome://extensions/` 并开启 **开发者模式**。
3. 点击 **加载已解压的扩展程序**，选择 `dist` 目录。

### Android
1. 前往 [Releases](https://github.com/JoyinJoester/Monica/releases) 页面下载最新的 APK 文件。
2. 在您的 Android 设备上安装（需 Android 11+）。

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
