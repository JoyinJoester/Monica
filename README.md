# Monica Password Manager 🔐

[中文](README_ZH.md) | **English**

<div align="center">

![Windows](https://img.shields.io/badge/Windows%2011-0078D6?style=for-the-badge&logo=windows&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Security](https://img.shields.io/badge/Security-AES--256--GCM-success?style=for-the-badge&logo=security&logoColor=white)
![License](https://img.shields.io/badge/License-GPLv3-blue.svg?style=for-the-badge)

**A unified, offline-first password management solution for Windows and Android.**
*Secure. Sovereign. Cross-Platform.*

</div>

---

## 📖 Overview

**Monica** is an enterprise-grade password manager engineered for absolute privacy and sovereignty over your digital credentials. By eschewing cloud dependencies in favor of local-only encrypted storage, Monica ensures that your sensitive data remains exclusively in your possession.

Whether you are on your **Browser (Chrome, Edge, Firefox)** or **Android** phone, Monica provides a seamless, consistent, and secure experience without monthly subscriptions or data tracking.

> **📦 Monica for Windows - Archived**  
> Due to significant feature overlap between Monica for Windows and the browser extension, we have discontinued Windows development.  
> Historical code has been migrated to: [Monica-for-Windows](https://github.com/JoyinJoester/Monica-for-Windows)

---

## ✨ Key Features

### 🔐 Multi-Platform Credential Management
*   **Unified Experience**: Feature parity between the modern **WinUI 3** desktop application and the **Jetpack Compose** Android app.
*   **Zero-Knowledge Encryption**: All data is encrypted locally using **AES-256-GCM**. Your master password is the only key, and it never leaves your device.
*   **Encrypted Vault**: Securely store logins, credit cards, identities, and secure notes.

### 🔄 Cross-Device Synchronization
*   **WebDAV Sync**: Synchronize your encrypted vault securely across Windows and Android using any WebDAV-compliant provider (Nextcloud, Synology, JianguoYun, etc.).
*   **Sovereignty**: YOU control the infrastructure. No vendor lock-in, no proprietary cloud servers.

### 🛡️ Built-in Authenticator (TOTP)
*   **Integrated 2FA**: Generate Time-based One-Time Passwords directly within Monica.
*   **Smart Scan (Android)**: Add accounts instantly by scanning QR codes.
*   **Steam Guard**: Native support for Steam's 2FA protocol.

### 📦 Advanced Data Features
*   **Secure Document Storage**: Encrypt and store sensitive files (ID scans, contracts) directly in the database.
*   **KeePass Compatibility**: Full interoperability with `.kdbx` files. Use Monica as a modern frontend for your KeePass databases.
*   **Breach Detection**: Proactive security analysis to check against known data breaches (Coming soon).

---

## 🛠️ Technical Architecture

Monica is built with modern, platform-native technologies to ensure performance and security.

### 🖥️ Windows Client
*   **Framework**: [WinUI 3 (Windows App SDK)](https://github.com/microsoft/WindowsAppSDK)
*   **Runtime**: .NET 8
*   **Data Access**: Entity Framework Core (SQLite)
*   **Design**: Native Fluent Design with Mica material support.

### 📱 Android Client
*   **Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
*   **Language**: Kotlin
*   **Design**: Material Design 3 (Material You) with dynamic theming.
*   **Security**: Android Keystore System for hardware-backed key protection.

---

## 🔒 Security Model

1.  **Encryption**: **AES-256** in **GCM** mode (Authenticated Encryption).
2.  **Key Derivation**: **PBKDF2-HMAC-SHA256** with high iteration counts (100,000+).
3.  **Local First**: No internet permissions required for core functionality. Network access is only used for WebDAV sync (user-controlled) and optionally checking favicons.

---

## 🚀 Installation

### Browser (Chrome / Edge)
1.  Download the latest source or build from the [**Monica for Browser**](Monica%20for%20Browser) directory.
2.  Enable **Developer Mode** in `chrome://extensions/`.
3.  Click **Load unpacked** and select the `dist` folder.
4.  *(Coming Soon: Installation from Chrome Web Store)*

### Android
1.  Download the latest `.apk` from the [**Releases**](https://github.com/JoyinJoester/Monica/releases) page.
2.  Install on **Android 11.0+** devices.

---

## 🤝 Support the Development

Monica is an open-source project driven by community support. If this tool adds value to your digital security workflow, consider supporting its continued development.

<div align="center">
<img src="image/support_author.jpg" alt="Support Author" width="300" style="border-radius: 10px"/>
<br/>
<sub>Scan using WeChat or Alipay</sub>
</div>

**Your support helps fund:**
*   Security audits
*   Multi-platform infrastructure
*   Continuous feature updates

---

## ⚖️ License

Copyright © 2025 JoyinJoester.
Distributed under the **GNU General Public License v3.0**. See `LICENSE` for more information.
